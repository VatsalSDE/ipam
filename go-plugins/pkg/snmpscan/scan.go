package snmpscan

import (
	"encoding/json"

	"fmt"

	"io"

	"net"

	"os"

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

	TimeoutSeconds int `json:"timeout"`

	Retries int `json:"retries"`
}

// OutputResponse represents the JSON output sent to stdout
type OutputResponse struct {
	Result map[string]string `json:"result"`

	ErrorCode string `json:"error-code,omitempty"`
}

// Run is the entry point for snmp-scan subcommand
func Run(args []string) {

	var inputData []byte

	var err error

	if len(args) > 0 && len(strings.TrimSpace(args[0])) > 0 {

		inputData = []byte(args[0])

	} else {

		stat, statErr := os.Stdin.Stat()

		if statErr == nil && (stat.Mode()&os.ModeCharDevice) == 0 {

			inputData, err = io.ReadAll(os.Stdin)

		}

		if err != nil || len(inputData) == 0 {

			printError("MISSING_CREDENTIALS")

			return

		}

	}

	var cred SNMPCredential

	if err := json.Unmarshal(inputData, &cred); err != nil {

		printError("INVALID_JSON: " + err.Error())

		return

	}

	if cred.Gateway == "" {

		printError("GATEWAY_IP_REQUIRED")

		return

	}

	snmpClient, err := BuildSNMPClient(&cred)

	if err != nil {

		printError("SNMP_CONFIG_ERROR: " + err.Error())

		return

	}

	if err := snmpClient.Connect(); err != nil {

		printError("CONNECT_FAILED: " + err.Error())

		return

	}

	defer snmpClient.Conn.Close()

	targetOIDs := []string{
		"1.3.6.1.2.1.4.21.1.11",
		"1.3.6.1.4.1.9.2.4.2.1.1",
	}

	results := make(map[string]string)

	for _, rootOID := range targetOIDs {

		_ = snmpClient.BulkWalk(rootOID, func(pdu gosnmp.SnmpPDU) error {

			oidParts := strings.Split(strings.TrimPrefix(pdu.Name, "."), ".")

			if len(oidParts) < 4 {

				return nil

			}

			subnetIP := strings.Join(oidParts[len(oidParts)-4:], ".")

			subnetMask := formatPDUValue(pdu)

			if subnetMask != "" && IsValidSubnet(subnetIP, subnetMask) {

				results[subnetIP] = subnetMask

			}

			return nil

		})

	}

	output := OutputResponse{
		Result: results,
	}

	jsonBytes, _ := json.Marshal(output)

	fmt.Println(string(jsonBytes))

}

// BuildSNMPClient configures Gosnmp based on version (v1, v2c, v3)
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

func formatPDUValue(pdu gosnmp.SnmpPDU) string {

	switch pdu.Type {

	case gosnmp.IPAddress:

		if str, ok := pdu.Value.(string); ok {

			return str

		}

	case gosnmp.OctetString:

		if bytes, ok := pdu.Value.([]byte); ok {

			if len(bytes) == 4 {

				return fmt.Sprintf("%d.%d.%d.%d", bytes[0], bytes[1], bytes[2], bytes[3])

			}

			return string(bytes)

		}

	}

	return fmt.Sprintf("%v", pdu.Value)

}

// IsValidSubnet checks if the subnet + mask form a valid network start address and not /32
func IsValidSubnet(subnetStr, maskStr string) bool {

	ip := net.ParseIP(subnetStr).To4()

	if ip == nil {

		return false

	}

	maskIP := net.ParseIP(maskStr).To4()

	if maskIP == nil {

		return false

	}

	mask := net.IPv4Mask(maskIP[0], maskIP[1], maskIP[2], maskIP[3])

	ones, bits := mask.Size()

	if ones == 32 || ones == 0 || bits != 32 {

		return false

	}

	calcNetwork := ip.Mask(mask)

	return ip.Equal(calcNetwork)

}

func printError(code string) {

	resp := OutputResponse{
		ErrorCode: code,
	}

	bytes, _ := json.Marshal(resp)

	fmt.Println(string(bytes))

}
