package snmparp

import (
	"encoding/hex"

	"encoding/json"

	"fmt"

	"io"

	"net"

	"os"

	"strconv"

	"strings"

	"time"

	"github.com/gosnmp/gosnmp"
)

// SNMPCredential represents the input JSON payload passed by Java/Vert.x
type SNMPCredential struct {
	Gateway string `json:"gateway"`

	Port int `json:"port"`

	Version string `json:"version"` // "v1", "v2c", "v3"

	Community string `json:"community"` // e.g. "public"

	UserName string `json:"user-name"` // SNMP v3 user

	AuthPassword string `json:"auth-password"` // SNMP v3 auth key

	AuthProtocol string `json:"auth-protocol"` // "MD5", "SHA", "SHA256", etc.

	PrivatePassword string `json:"private-password"` // SNMP v3 privacy key

	PrivacyProtocol string `json:"privacy-protocol"` // "DES", "AES", "AES128", etc.

	SecurityLevel string `json:"security-level"` // "noAuthNoPriv", "authNoPriv", "authPriv"

	SubnetIP string `json:"subnet_ip,omitempty"`

	CIDR string `json:"cidr,omitempty"`

	TimeoutSeconds int `json:"timeout"`

	Retries int `json:"retries"`
}

// OutputResponse represents the JSON output format expected by TraceOrgSubnetUtil.java
type OutputResponse struct {
	Result []map[string]string `json:"result"`

	ErrorCode string `json:"error-code,omitempty"`
}

// Run is the entry point for snmp-arp subcommand
func Run(args []string) {

	var inputData []byte

	var subnetIP string

	var cidrStr string

	if len(args) >= 3 {

		inputData = []byte(args[0])

		subnetIP = strings.TrimSpace(args[1])

		cidrStr = strings.TrimSpace(args[2])

	} else if len(args) > 0 && strings.TrimSpace(args[0]) != "" {

		inputData = []byte(args[0])

	} else {

		stat, statErr := os.Stdin.Stat()

		if statErr == nil && (stat.Mode()&os.ModeCharDevice) == 0 {

			var err error

			inputData, err = io.ReadAll(os.Stdin)

			if err != nil || len(inputData) == 0 {

				printErrorResult("MISSING_CREDENTIALS")

				return

			}

		} else {

			printErrorResult("MISSING_CREDENTIALS")

			return

		}

	}

	var cred SNMPCredential

	if err := json.Unmarshal(inputData, &cred); err != nil {

		printErrorResult("INVALID_JSON: " + err.Error())

		return

	}

	if subnetIP == "" {

		subnetIP = cred.SubnetIP

	}

	if cidrStr == "" {

		cidrStr = cred.CIDR

	}

	if cred.Gateway == "" {

		printErrorResult("GATEWAY_REQUIRED")

		return

	}

	var ipNet *net.IPNet

	if subnetIP != "" && cidrStr != "" {

		cidrInt, err := strconv.Atoi(cidrStr)

		if err == nil && cidrInt >= 0 && cidrInt <= 32 {

			_, parsedNet, parseErr := net.ParseCIDR(fmt.Sprintf("%s/%d", subnetIP, cidrInt))

			if parseErr == nil {

				ipNet = parsedNet

			}

		}

	}

	snmpClient, err := BuildSNMPClient(&cred)

	if err != nil {

		printErrorResult("SNMP_CONFIG_ERROR: " + err.Error())

		return

	}

	if err := snmpClient.Connect(); err != nil {

		printErrorResult("CONNECT_FAILED: " + err.Error())

		return

	}

	defer snmpClient.Conn.Close()

	targetOIDs := []string{
		"1.3.6.1.2.1.4.22.1.2",
		"1.3.6.1.2.1.4.35.1.4",
	}

	seenIPs := make(map[string]bool)

	records := make([]map[string]string, 0)

	for _, rootOID := range targetOIDs {

		_ = snmpClient.BulkWalk(rootOID, func(pdu gosnmp.SnmpPDU) error {

			ipStr := ExtractIPFromOID(pdu.Name, rootOID)

			if ipStr == "" {

				return nil

			}

			ip := net.ParseIP(ipStr)

			if ip == nil {

				return nil

			}

			if ipNet != nil && !ipNet.Contains(ip) {

				return nil

			}

			if seenIPs[ipStr] {

				return nil

			}

			macStr := FormatMACAddress(pdu)

			if macStr == "" || macStr == "00:00:00:00:00:00" {

				return nil

			}

			seenIPs[ipStr] = true

			records = append(records, map[string]string{
				ipStr: macStr,
			})

			return nil

		})

	}

	output := OutputResponse{
		Result: records,
	}

	outBytes, _ := json.Marshal(output)

	fmt.Println(string(outBytes))

}

func ExtractIPFromOID(oidName string, rootOID string) string {

	oidClean := strings.TrimPrefix(oidName, ".")

	parts := strings.Split(oidClean, ".")

	if strings.HasPrefix(rootOID, "1.3.6.1.2.1.4.22.1.2") {

		if len(parts) >= 4 {

			ipParts := parts[len(parts)-4:]

			ip := strings.Join(ipParts, ".")

			if net.ParseIP(ip) != nil {

				return ip

			}

		}

	} else if strings.HasPrefix(rootOID, "1.3.6.1.2.1.4.35.1.4") {

		if len(parts) >= 4 {

			ipParts := parts[len(parts)-4:]

			ip := strings.Join(ipParts, ".")

			if net.ParseIP(ip) != nil {

				return ip

			}

		}

	}

	return ""

}

func FormatMACAddress(pdu gosnmp.SnmpPDU) string {

	switch pdu.Type {

	case gosnmp.OctetString:

		if bytes, ok := pdu.Value.([]byte); ok {

			if len(bytes) == 6 {

				return fmt.Sprintf("%02X:%02X:%02X:%02X:%02X:%02X",
					bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5])

			}

			hexStr := strings.TrimSpace(string(bytes))

			hexStr = strings.ReplaceAll(hexStr, "0x", "")

			hexStr = strings.ReplaceAll(hexStr, "0X", "")

			hexStr = strings.ReplaceAll(hexStr, ":", "")

			hexStr = strings.ReplaceAll(hexStr, "-", "")

			if len(hexStr) == 12 {

				decoded, err := hex.DecodeString(hexStr)

				if err == nil && len(decoded) == 6 {

					return fmt.Sprintf("%02X:%02X:%02X:%02X:%02X:%02X",
						decoded[0], decoded[1], decoded[2], decoded[3], decoded[4], decoded[5])

				}

			}

		}

	}

	return ""

}

func BuildSNMPClient(cred *SNMPCredential) (*gosnmp.GoSNMP, error) {

	port := uint16(161)

	if cred.Port > 0 {

		port = uint16(cred.Port)

	}

	timeout := 5 * time.Second

	if cred.TimeoutSeconds > 0 {

		timeout = time.Duration(cred.TimeoutSeconds) * time.Second

	}

	retries := 1

	if cred.Retries > 0 {

		retries = cred.Retries

	}

	community := "public"

	if cred.Community != "" {

		community = cred.Community

	}

	client := &gosnmp.GoSNMP{
		Target:    cred.Gateway,
		Port:      port,
		Community: community,
		Timeout:   timeout,
		Retries:   retries,
		MaxOids:   50,
	}

	switch strings.ToLower(cred.Version) {

	case "v1":

		client.Version = gosnmp.Version1

	case "v3":

		client.Version = gosnmp.Version3

		client.SecurityModel = gosnmp.UserSecurityModel

		secParams := &gosnmp.UsmSecurityParameters{
			UserName: cred.UserName,
		}

		secLevel := strings.ToLower(cred.SecurityLevel)

		switch secLevel {

		case "authnopriv", "authpriv":

			client.MsgFlags = gosnmp.AuthNoPriv

			secParams.AuthenticationPassphrase = cred.AuthPassword

			secParams.AuthenticationProtocol = mapAuthProtocol(cred.AuthProtocol)

			if secLevel == "authpriv" {

				client.MsgFlags = gosnmp.AuthPriv

				secParams.PrivacyPassphrase = cred.PrivatePassword

				secParams.PrivacyProtocol = mapPrivProtocol(cred.PrivacyProtocol)

			}

		default:

			client.MsgFlags = gosnmp.NoAuthNoPriv

		}

		client.SecurityParameters = secParams

	default:

		client.Version = gosnmp.Version2c

	}

	return client, nil

}

func mapAuthProtocol(proto string) gosnmp.SnmpV3AuthProtocol {

	switch strings.ToUpper(proto) {

	case "SHA", "SHA1":

		return gosnmp.SHA

	case "SHA224":

		return gosnmp.SHA224

	case "SHA256":

		return gosnmp.SHA256

	case "SHA384":

		return gosnmp.SHA384

	case "SHA512":

		return gosnmp.SHA512

	default:

		return gosnmp.MD5

	}

}

func mapPrivProtocol(proto string) gosnmp.SnmpV3PrivProtocol {

	switch strings.ToUpper(proto) {

	case "AES", "AES128":

		return gosnmp.AES

	case "AES192":

		return gosnmp.AES192

	case "AES256", "AES256C":

		return gosnmp.AES256

	default:

		return gosnmp.DES

	}

}

func printErrorResult(code string) {

	resp := OutputResponse{
		Result:    make([]map[string]string, 0),
		ErrorCode: code,
	}

	bytes, _ := json.Marshal(resp)

	fmt.Println(string(bytes))

}
