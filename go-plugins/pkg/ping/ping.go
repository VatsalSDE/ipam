package ping

import (
	"context"

	"encoding/json"

	"fmt"

	"io"

	"os"

	"os/exec"

	"runtime"

	"strconv"

	"strings"

	"time"
)

// PingConfig represents ping execution configuration
type PingConfig struct {
	IPAddresses string `json:"ip-addresses"`

	IPList []string `json:"ip_list,omitempty"`

	RetryCount int `json:"max-ping-check-retry-count"`

	TimeoutMs int `json:"max-ping-check-timeout"`

	MaxConcurrent int `json:"max-concurrent-ping"`
}

// PingResult represents the output JSON contract
type PingResult struct {
	Up []string `json:"up"`

	Down []string `json:"down"`

	ErrorCode string `json:"error-code,omitempty"`
}

// Run is the entry point for the ping subcommand
func Run(args []string) {

	var inputData []byte

	var err error

	if len(args) > 0 {

		firstArg := strings.TrimSpace(args[0])

//         If Java passed a filename (e.g. cache.json)

		if strings.HasSuffix(firstArg, ".txt") || strings.HasSuffix(firstArg, ".json") {

			inputData, err = os.ReadFile(firstArg)

			if err != nil {

				printResult(PingResult{ErrorCode: "FILE_READ_ERROR: " + err.Error()})

				return

			}

		} else {
            // If Java passed the JSON string directly as an argument

			inputData = []byte(firstArg)

		}

	} else {

		stat, statErr := os.Stdin.Stat()

		if statErr == nil && (stat.Mode()&os.ModeCharDevice) == 0 {

            // Fallback: If Java piped JSON via STDIN pipe
			inputData, err = io.ReadAll(os.Stdin)

		}

		if err != nil || len(inputData) == 0 {

			printResult(PingResult{ErrorCode: "MISSING_INPUT"})

			return

		}

	}

	result := ExecutePing(inputData)

	printResult(result)

}

// ExecutePing performs concurrent ping against IP addresses
func ExecutePing(inputData []byte) PingResult {

    // 1. Parse JSON options

    // 2. Extract comma-separated IPs into slice []string
	var rawMap map[string]interface{}

	if err := json.Unmarshal(inputData, &rawMap); err != nil {

		return PingResult{ErrorCode: "INVALID_JSON: " + err.Error()}

	}

	retryCount := 2

	timeoutMs := 1000

	var ips []string

	if val, ok := rawMap["max-ping-check-retry-count"]; ok {

		retryCount = parseInt(val, 2)

	}

	if val, ok := rawMap["max-ping-check-timeout"]; ok {

		timeoutMs = parseInt(val, 1000)

	}

	if val, ok := rawMap["max-concurrent-ping"]; ok {

		_ = parseInt(val, 500)

	}

	if val, ok := rawMap["ip-addresses"]; ok {

		if str, ok := val.(string); ok { // this validates that the actually sent is a i mean the string only in ythe input suppsoe the ip ok so yess liek the "192.12.18.1" not lie 123

			for _, ip := range strings.Split(str, ",") {

				trimmed := strings.TrimSpace(ip)

				if trimmed != "" {

					ips = append(ips, trimmed)

				}

			}

		}

	} else if val, ok := rawMap["ip_list"]; ok {

		if list, ok := val.([]interface{}); ok {

			for _, item := range list {

				if str, ok := item.(string); ok {

					ips = append(ips, strings.TrimSpace(str))

				}

			}

		}

	}

	if len(ips) == 0 {

		return PingResult{Up: []string{}, Down: []string{}}

	}

	// High-speed industrial fping execution
	// -a: print alive only
	// -q: quiet (suppress headers and per-target verbose logs)
	// -t: timeout per target in ms
	// -r: retry count
	cmd := exec.Command("fping",
		"-a",
		"-q",
		"-t", strconv.Itoa(timeoutMs),
		"-r", strconv.Itoa(retryCount),
	)

	// Stream all chunk IPs into fping's standard input pipe separated by newline
	cmd.Stdin = strings.NewReader(strings.Join(ips, "\n"))

	// Execute fping.
	// Note: fping exits with code 1 when some hosts are unreachable.
	// We read output regardless of exit code.
	output, _ := cmd.CombinedOutput()

	// Build alive IP fast-lookup set
	aliveMap := make(map[string]bool)

	lines := strings.Split(string(output), "\n")

	for _, line := range lines {

		trimmed := strings.TrimSpace(line)

		if trimmed != "" {

			aliveMap[trimmed] = true

		}

	}

	// Partition original chunk IPs into up and down lists
	var upList []string

	var downList []string

	for _, ip := range ips {

		if aliveMap[ip] {

			upList = append(upList, ip)

		} else {

			downList = append(downList, ip)

		}

	}

	if upList == nil {

		upList = []string{}

	}

	if downList == nil {

		downList = []string{}

	}

	return PingResult{
		Up:   upList,
		Down: downList,
	}

}

func pingSingleHost(ip string, retryCount, timeoutMs int) bool {

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutMs*retryCount+1000)*time.Millisecond)

	defer cancel()

	var cmd *exec.Cmd

	if runtime.GOOS == "windows" {

		cmd = exec.CommandContext(ctx, "ping", "-n", strconv.Itoa(retryCount), "-w", strconv.Itoa(timeoutMs), ip)

	} else {

		timeoutSec := (timeoutMs + 999) / 1000

		cmd = exec.CommandContext(ctx, "ping", "-c", strconv.Itoa(retryCount), "-W", strconv.Itoa(timeoutSec), ip)

	}

	output, err := cmd.CombinedOutput()

	if err != nil {

		return false

	}

	outStr := strings.ToLower(string(output))

	if strings.Contains(outStr, "100% packet loss") || strings.Contains(outStr, "100% loss") || strings.Contains(outStr, "unreachable") {

		return false

	}

	return strings.Contains(outStr, "bytes from") || strings.Contains(outStr, "reply from") || strings.Contains(outStr, "ttl=")

}

func parseInt(val interface{}, fallback int) int {

	switch v := val.(type) {

	case float64:

		return int(v)

	case int:

		return v

	case string:

		if parsed, err := strconv.Atoi(strings.TrimSpace(v)); err == nil {

			return parsed

		}

	}

	return fallback

}

func printResult(res PingResult) {

	bytes, _ := json.Marshal(res)

	fmt.Println(string(bytes))

}