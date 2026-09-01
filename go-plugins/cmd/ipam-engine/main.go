package main

import (
	"encoding/json"

	"fmt"

	"os"

	"strings"

	"ipam/go-plugins/pkg/ping"

	"ipam/go-plugins/pkg/snmparp"

	"ipam/go-plugins/pkg/snmpscan"

	"ipam/go-plugins/pkg/winrmdhcp"
)

func main() {

	if len(os.Args) < 2 {

		printUsage()

		return

	}

	command := strings.ToLower(strings.TrimSpace(os.Args[1]))

	args := os.Args[2:]

	switch command {

	case "ping", "fping":

		ping.Run(args)

	case "snmp-scan":

		snmpscan.Run(args)

	case "snmp-arp":

		snmparp.Run(args)

	case "windows-dhcp":

		winrmdhcp.Run(args)

	case "help", "--help", "-h":

		printUsage()

	default:

		errResp := map[string]string{
			"error-code": fmt.Sprintf("UNKNOWN_COMMAND: %s", command),
		}

		out, _ := json.Marshal(errResp)

		fmt.Println(string(out))

	}

}

func printUsage() {

	fmt.Println("Motadata IPAM Unified Plugin Engine")

	fmt.Println("Usage: ipam-engine <command> [arguments...]")

	fmt.Println("")

	fmt.Println("Available Commands:")

	fmt.Println("  ping          Concurrent ICMP bulk ping (input: .txt cache file or JSON)")

	fmt.Println("  snmp-scan     Gateway SNMP route table discovery (input: JSON credentials)")

	fmt.Println("  snmp-arp      Gateway SNMP ARP table IP-to-MAC walk (input: JSON credentials, Subnet, CIDR)")

	fmt.Println("  windows-dhcp  Windows Server DHCP scope & lease collector (input: Base64 JSON context)")

	fmt.Println("")

}
