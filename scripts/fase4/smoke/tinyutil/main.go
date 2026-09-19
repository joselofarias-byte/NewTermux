package main

import (
	"fmt"
	"os"
)

func main() {
	if len(os.Args) != 2 || os.Args[1] != "ok" {
		fmt.Fprintf(os.Stderr, "FAIL tinyutil argv=%q\n", os.Args)
		os.Exit(1)
	}
	fmt.Println("tinyutil ok")
}
