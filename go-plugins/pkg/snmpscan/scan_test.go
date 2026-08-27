package snmpscan

import (
	"testing"
)

func TestIsValidSubnet(t *testing.T) {

	tests := []struct {
		subnet string

		mask string

		valid bool
	}{
		{"192.168.1.0", "255.255.255.0", true},

		{"10.0.0.0", "255.0.0.0", true},

		{"172.16.0.0", "255.240.0.0", true},

		{"192.168.1.1", "255.255.255.0", false},

		{"192.168.1.100", "255.255.255.255", false},

		{"0.0.0.0", "0.0.0.0", false},
	}

	for _, tt := range tests {

		got := IsValidSubnet(tt.subnet, tt.mask)

		if got != tt.valid {

			t.Errorf("IsValidSubnet(%q, %q) = %v; want %v", tt.subnet, tt.mask, got, tt.valid)

		}

	}

}

func TestBuildSNMPClient(t *testing.T) {

	cred := &SNMPCredential{
		Gateway: "192.168.1.1",

		Port: 161,

		Version: "v2c",

		Community: "public",
	}

	client, err := BuildSNMPClient(cred)

	if err != nil {

		t.Fatalf("BuildSNMPClient failed: %v", err)

	}

	if client.Target != "192.168.1.1" {

		t.Errorf("expected Target 192.168.1.1, got %s", client.Target)

	}

}
