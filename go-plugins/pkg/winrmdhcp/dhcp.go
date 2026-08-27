package winrmdhcp

import (
	"bytes"

	"crypto/tls"

	"encoding/base64"

	"encoding/json"

	"fmt"

	"io"

	"net/http"

	"os"

	"strconv"

	"strings"

	"time"
)

// WinRMContext represents the input payload passed by Java / Vert.x
type WinRMContext struct {
	Host string `json:"host"`

	Port interface{} `json:"port"`

	Username string `json:"username"`

	Password string `json:"password"`

	Timeout interface{} `json:"timeout"`

	ScopeIDs string `json:"scope-ids,omitempty"`

	Action string `json:"action,omitempty"`

	Endpoint string `json:"endpoint,omitempty"`

	Result map[string]interface{} `json:"result,omitempty"`

	ErrorCode string `json:"error-code,omitempty"`
}

// Run is the entry point for windows-dhcp subcommand
func Run(args []string) {

	var action string

	var contextRaw []byte

	if len(args) >= 2 {

		action = strings.ToLower(strings.TrimSpace(args[0]))

		decoded, err := base64.StdEncoding.DecodeString(args[1])

		if err != nil {

			contextRaw = []byte(args[1])

		} else {

			contextRaw = decoded

		}

	} else if len(args) == 1 {

		contextRaw = []byte(args[0])

	} else {

		stat, statErr := os.Stdin.Stat()

		if statErr == nil && (stat.Mode()&os.ModeCharDevice) == 0 {

			var err error

			contextRaw, err = io.ReadAll(os.Stdin)

			if err != nil || len(contextRaw) == 0 {

				emitErrorResponse("MISSING_ARGUMENTS", nil)

				return

			}

		} else {

			emitErrorResponse("MISSING_ARGUMENTS", nil)

			return

		}

	}

	var ctx WinRMContext

	if err := json.Unmarshal(contextRaw, &ctx); err != nil {

		emitErrorResponse("INVALID_JSON: "+err.Error(), nil)

		return

	}

	if action == "" {

		if ctx.Action != "" {

			action = strings.ToLower(ctx.Action)

		} else {

			action = "collector"

		}

	}

	if ctx.Host == "" {

		emitErrorResponse("HOST_REQUIRED", &ctx)

		return

	}

	portNum := 5985

	if ctx.Port != nil {

		switch v := ctx.Port.(type) {

		case float64:

			portNum = int(v)

		case string:

			if p, err := strconv.Atoi(v); err == nil {

				portNum = p

			}

		}

	}

	scheme := "http"

	if portNum == 5986 {

		scheme = "https"

	}

	ctx.Endpoint = fmt.Sprintf("%s://%s:%d/wsman", scheme, ctx.Host, portNum)

	timeoutSec := 30

	if ctx.Timeout != nil {

		switch v := ctx.Timeout.(type) {

		case float64:

			timeoutSec = int(v)

		case string:

			if t, err := strconv.Atoi(v); err == nil {

				timeoutSec = t

			}

		}

	}

	client := NewWinRMClient(ctx.Endpoint, ctx.Username, ctx.Password, timeoutSec)

	var execResult map[string]interface{}

	var execErr error

	if action == "discovery" {

		execResult, execErr = executeDiscovery(client)

	} else {

		execResult, execErr = executeCollector(client, ctx.ScopeIDs)

	}

	ctx.Password = ""

	ctx.Username = ""

	if execErr != nil {

		ctx.ErrorCode = execErr.Error()

	} else {

		ctx.Result = execResult

	}

	emitSuccessResponse(&ctx)

}

func executeDiscovery(client *WinRMClient) (map[string]interface{}, error) {

	status, err := client.ExecuteCommand("Get-Service -Name DHCPServer | Select-Object -ExpandProperty Status")

	if err != nil {

		return nil, fmt.Errorf("CONNECTION_FAILED: %v", err)

	}

	if !strings.Contains(strings.ToLower(status), "running") {

		return nil, fmt.Errorf("DHCP Server Service Stopped")

	}

	output, err := client.ExecuteCommand("Get-DhcpServerv4Statistics | Select-Object ServerStartTime | Format-List")

	if err != nil {

		return nil, err

	}

	return map[string]interface{}{
		"result": output,
	}, nil

}

func executeCollector(client *WinRMClient, scopeIDs string) (map[string]interface{}, error) {

	result := make(map[string]interface{})

	if scopeIDs != "" {

		scopes := strings.Split(scopeIDs, ",")

		var resOut strings.Builder

		for _, s := range scopes {

			s = strings.TrimSpace(s)

			if s == "" {

				continue

			}

			out, err := client.ExecuteCommand(fmt.Sprintf("Get-DhcpServerv4Reservation -ScopeId %s | Format-List", s))

			if err == nil && len(out) > 0 {

				resOut.WriteString(out)

			}

		}

		if resOut.Len() > 0 {

			result["dhcp-scope-reservations"] = resOut.String()

		}

		var leaseOut strings.Builder

		for _, s := range scopes {

			s = strings.TrimSpace(s)

			if s == "" {

				continue

			}

			out, err := client.ExecuteCommand(fmt.Sprintf("Get-DhcpServerv4Lease -ScopeId %s | Format-List", s))

			if err == nil && len(out) > 0 {

				leaseOut.WriteString(out)

			}

		}

		if leaseOut.Len() > 0 {

			result["dhcp-server-lease"] = leaseOut.String()

		}

		var policyOut strings.Builder

		for _, s := range scopes {

			s = strings.TrimSpace(s)

			if s == "" {

				continue

			}

			out, err := client.ExecuteCommand(fmt.Sprintf("Get-DhcpServerv4PolicyIPRange -ScopeId %s | Format-List", s))

			if err == nil && len(out) > 0 {

				policyOut.WriteString(out)

			}

		}

		if policyOut.Len() > 0 {

			result["dhcp-scope-range-policies"] = policyOut.String()

		}

	} else {

		stats, err := client.ExecuteCommand("Get-DhcpServerv4Statistics | Format-List")

		if err == nil && len(stats) > 0 {

			result["dhcp-server-statistics"] = stats

		}

		scopes, err := client.ExecuteCommand("Get-DhcpServerv4Scope | Format-List")

		if err == nil && len(scopes) > 0 {

			result["dhcp-scopes"] = scopes

		}

		scopeStats, err := client.ExecuteCommand("Get-DhcpServerv4ScopeStatistics | Format-List")

		if err == nil && len(scopeStats) > 0 {

			result["dhcp-scope-statistics"] = scopeStats

		}

	}

	return result, nil

}

type WinRMClient struct {
	Endpoint string

	Username string

	Password string

	TimeoutSec int

	HTTPClient *http.Client
}

func NewWinRMClient(endpoint, username, password string, timeoutSec int) *WinRMClient {

	transport := &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}

	return &WinRMClient{
		Endpoint:   endpoint,
		Username:   username,
		Password:   password,
		TimeoutSec: timeoutSec,
		HTTPClient: &http.Client{
			Transport: transport,
			Timeout:   time.Duration(timeoutSec) * time.Second,
		},
	}

}

func (c *WinRMClient) ExecuteCommand(psCommand string) (string, error) {

	fullCommand := fmt.Sprintf("$Host.UI.RawUI.BufferSize = New-Object Management.Automation.Host.Size (512,25); %s", psCommand)

	soapPayload := fmt.Sprintf(`<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:wsman="http://schemas.dmtf.org/wbem/wsman/1/wsman.xsd">
  <s:Header>
    <wsa:To>%s</wsa:To>
    <wsman:ResourceURI>http://schemas.microsoft.com/powershell/Microsoft.PowerShell</wsman:ResourceURI>
    <wsa:Action>http://schemas.xmlsoap.org/ws/2004/09/transfer/Create</wsa:Action>
    <wsa:MessageID>uuid:%d</wsa:MessageID>
  </s:Header>
  <s:Body>
    <Command>%s</Command>
  </s:Body>
</s:Envelope>`, c.Endpoint, time.Now().UnixNano(), EscapeXML(fullCommand))

	req, err := http.NewRequest("POST", c.Endpoint, bytes.NewBufferString(soapPayload))

	if err != nil {

		return "", err

	}

	req.Header.Set("Content-Type", "application/soap+xml;charset=UTF-8")

	if c.Username != "" && c.Password != "" {

		req.SetBasicAuth(c.Username, c.Password)

	}

	resp, err := c.HTTPClient.Do(req)

	if err != nil {

		return "", err

	}

	defer resp.Body.Close()

	bodyBytes, err := io.ReadAll(resp.Body)

	if err != nil {

		return "", err

	}

	return string(bodyBytes), nil

}

func EscapeXML(s string) string {

	s = strings.ReplaceAll(s, "&", "&amp;")

	s = strings.ReplaceAll(s, "<", "&lt;")

	s = strings.ReplaceAll(s, ">", "&gt;")

	s = strings.ReplaceAll(s, "\"", "&quot;")

	s = strings.ReplaceAll(s, "'", "&apos;")

	return s

}

func emitSuccessResponse(ctx *WinRMContext) {

	out := map[string]interface{}{
		"host":     ctx.Host,
		"port":     ctx.Port,
		"endpoint": ctx.Endpoint,
	}

	if ctx.Timeout != nil {

		out["timeout"] = ctx.Timeout

	}

	if ctx.Result != nil {

		out["result"] = ctx.Result

	}

	if ctx.ErrorCode != "" {

		out["error-code"] = ctx.ErrorCode

	}

	bytes, _ := json.Marshal(out)

	fmt.Println(string(bytes))

}

func emitErrorResponse(code string, ctx *WinRMContext) {

	out := map[string]interface{}{
		"error-code": code,
	}

	if ctx != nil {

		out["host"] = ctx.Host

		out["endpoint"] = ctx.Endpoint

	}

	bytes, _ := json.Marshal(out)

	fmt.Println(string(bytes))

}
