package ping

import (
	"testing"
)

func TestParseInt(t *testing.T) {

	if parseInt(10, 5) != 10 {

		t.Errorf("expected 10, got %d", parseInt(10, 5))

	}

	if parseInt("25", 5) != 25 {

		t.Errorf("expected 25, got %d", parseInt("25", 5))

	}

	if parseInt(float64(50), 5) != 50 {

		t.Errorf("expected 50, got %d", parseInt(float64(50), 5))

	}

	if parseInt("invalid", 5) != 5 {

		t.Errorf("expected fallback 5, got %d", parseInt("invalid", 5))

	}

}

func TestExecutePing_Localhost(t *testing.T) {

	inputJSON := []byte(`{
		"ip-addresses": "127.0.0.1",
		"max-concurrent-ping": 1,
		"max-ping-check-timeout": 1000,
		"max-ping-check-retry-count": 1
	}`)

	result := ExecutePing(inputJSON)

	if len(result.Up) == 0 && len(result.Down) == 0 {

		t.Errorf("expected ping result to have at least one entry")

	}

}
