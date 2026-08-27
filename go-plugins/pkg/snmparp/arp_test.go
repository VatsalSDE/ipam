package snmparp

import (
	"testing"
)

func TestExtractIPFromOID(t *testing.T) {

	rootOID := "1.3.6.1.2.1.4.22.1.2"

	sampleOID := ".1.3.6.1.2.1.4.22.1.2.2.192.168.1.100"

	got := ExtractIPFromOID(sampleOID, rootOID)

	if got != "192.168.1.100" {

		t.Errorf("expected 192.168.1.100, got %s", got)

	}

}
