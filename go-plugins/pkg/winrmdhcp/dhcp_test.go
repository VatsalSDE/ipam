package winrmdhcp

import (
	"testing"
)

func TestEscapeXML(t *testing.T) {

	input := `Get-DhcpServerv4Scope | Where-Object { $_.ScopeId -eq "192.168.1.0" & $_.State -eq 'Active' }`

	got := EscapeXML(input)

	if got == input {

		t.Errorf("expected string to be escaped")

	}

}
