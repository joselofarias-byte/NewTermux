#!/usr/bin/env bash
# Cloud/host checks that do NOT claim Termux Go works on HONOR 200.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "== repo: goargs must stay absent =="
if git grep -n --fixed-string 'goargs' -- ':!docs/' ':!scripts/fase4/' ':!scripts/fase7/' ':!.github/workflows/fase4_bootstrap_inventory.yml' ':!.github/workflows/fase7_validate.yml'; then
  echo "FAIL: goargs leaked outside Fase 4/7 docs/scripts"
  exit 1
fi
echo "PASS: no product goargs"

echo "== refuse Play golang patch filename in product trees =="
if git grep -n 'src-runtime-runtime1.go.patch' -- ':!docs/' ':!scripts/fase4/' ':!scripts/fase7/' ':!.github/workflows/fase4_bootstrap_inventory.yml' ':!.github/workflows/fase7_validate.yml'; then
  echo "FAIL: Play runtime1 patch referenced in product code"
  exit 1
fi
echo "PASS: Play runtime1 patch not vendored"

echo "== inspect official bootstrap zip =="
FASE4_WORKDIR="${TMPDIR:-/tmp}/fase4-host-inspect" bash scripts/fase4/inspect-bootstrap.sh

echo "== host execve argv =="
HOST_BIN="${TMPDIR:-/tmp}/fase4-execve-argv"
gcc -O2 -o "$HOST_BIN" scripts/fase4/execve-argv.c
"$HOST_BIN"

echo "== host Go compile of smoke sources (linux/amd64, NOT Termux) =="
HOSTDIR="${TMPDIR:-/tmp}/fase4-host-go"
rm -rf "$HOSTDIR"
mkdir -p "$HOSTDIR"
cp -a scripts/fase4/smoke/. "$HOSTDIR/"
(
  cd "$HOSTDIR"
  go test .
  go build -o hello ./hello.go
  test "$(./hello world)" = "hello world"
  gofmt -e hello.go >/dev/null
  GOPATH="$HOSTDIR/gopath" GOBIN="$HOSTDIR/gopath/bin" go install ./tinyutil
  test "$("$HOSTDIR/gopath/bin/tinyutil" ok)" = "tinyutil ok"
)
echo "PASS host Go smoke (does not prove Android golang)"

echo "== Termux Go smoke is hardware-only =="
set +e
bash scripts/fase4/go-smoke.sh
rc=$?
set -e
if [[ "$rc" -eq 2 ]]; then
  echo "PASS: go-smoke correctly reported PENDIENTE-HARDWARE on this host"
else
  echo "FAIL: go-smoke exit $rc (expected 2 on non-Termux host)"
  exit 1
fi

echo "PASS Fase 4 host/cloud checks"
