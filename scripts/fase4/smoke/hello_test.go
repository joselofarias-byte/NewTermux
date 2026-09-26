package main

import "testing"

func TestGreeting(t *testing.T) {
	if got := greeting("world"); got != "hello world" {
		t.Fatalf("greeting: got %q", got)
	}
}
