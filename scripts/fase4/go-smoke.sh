#!/usr/bin/env bash
# Termux Go smoke suite. Refuse Play goargs. No permanent argv wrappers.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SMOKE="$ROOT/scripts/fase4/smoke"
REPORT="${FASE4_REPORT:-}"

log() { printf '%s\n' "$*"; }
fail() { log "FAIL: $*"; exit 1; }
skip_hw() {
  log "PENDIENTE-HARDWARE: $*"
  exit 2
}

is_termux_android() {
  [[ "${PREFIX:-}" == /data/data/*/files/usr ]] || return 1
  [[ "$(uname -s)" == "Linux" ]] || return 1
  [[ -f /system/bin/linker64 || -f /system/bin/linker ]] || return 1
  command -v pkg >/dev/null 2>&1 || return 1
  return 0
}

if ! is_termux_android; then
  skip_hw "this VM is not Termux-on-Android (PREFIX='${PREFIX:-}' uname=$(uname -s) pkg=$(command -v pkg || true)). Host Go/execve checks are separate."
fi

APP_ID="$(echo "$PREFIX" | awk -F/ '{print $4}')"
log "PREFIX=$PREFIX"
log "derived_applicationId=$APP_ID"

if [[ "$APP_ID" == "com.termux" ]]; then
  fail "running inside Play/playcompat PREFIX; refuse to install or test Go here"
fi
if [[ "$APP_ID" != "com.newtermux.dev" ]]; then
  fail "unexpected PREFIX package $APP_ID (want com.newtermux.dev)"
fi

if [[ -f "$PREFIX/etc/apt/sources.list" ]]; then
  log "--- sources.list ---"
  cat "$PREFIX/etc/apt/sources.list"
  if grep -qiE 'termux-play|play-store' "$PREFIX/etc/apt/sources.list"; then
    fail "Play APT repo present; would reintroduce goargs golang"
  fi
  if ! grep -qE 'packages(-cf)?\.termux\.dev' "$PREFIX/etc/apt/sources.list"; then
    fail "sources.list is not official packages.termux.dev"
  fi
fi

if ! command -v go >/dev/null 2>&1; then
  log "golang not installed; installing from current official APT (not Play)"
  pkg install -y golang
fi

GO_BIN="$(command -v go)"
log "go_bin=$GO_BIN"
file "$GO_BIN" || true

if strings "$GO_BIN" | grep -q 'AndroidSelfExecutable'; then
  fail "Play goargs marker AndroidSelfExecutable in $GO_BIN — do not wrap, reinstall from official termux/termux-packages"
fi
if strings "$GO_BIN" | grep -q 'func goargs'; then
  log "note: goargs symbol name may appear in any Go runtime; marker used is AndroidSelfExecutable"
fi

log "=== go version ==="
go version
log "=== go env ==="
go env
log "=== go help ==="
go help >/dev/null
log "go help: ok"

WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/fase4-go-smoke.XXXXXX")"
trap 'rm -rf "$WORKDIR"' EXIT
cp -a "$SMOKE/." "$WORKDIR/"
cd "$WORKDIR"
# tinyutil is a second main module
mkdir -p "$WORKDIR/gopath/bin"
export GOPATH="$WORKDIR/gopath"
export GOBIN="$WORKDIR/gopath/bin"

log "=== hello compile+run ==="
go build -o hello ./hello.go
OUT="$(./hello world)"
[[ "$OUT" == "hello world" ]] || fail "hello output: $OUT"
echo "$OUT"

log "=== go test ==="
go test .

log "=== gofmt ==="
fmt_out="$(gofmt hello.go)"
[[ -n "$fmt_out" ]] || fail "gofmt produced empty output"
gofmt -e hello.go >/dev/null

log "=== go install tinyutil ==="
go install ./tinyutil
"$GOBIN/tinyutil" ok

log "=== execve argv (C) ==="
if command -v clang >/dev/null 2>&1; then
  CC=clang
elif command -v gcc >/dev/null 2>&1; then
  CC=gcc
else
  fail "need clang/gcc to build execve-argv.c (pkg install clang)"
fi
"$CC" -O2 -o execve-argv "$ROOT/scripts/fase4/execve-argv.c"
./execve-argv

log "=== from shell script ==="
cat > run-from-sh.sh <<'EOS'
#!/usr/bin/env sh
set -eu
./hello script
EOS
chmod +x run-from-sh.sh
OUT="$(./run-from-sh.sh)"
[[ "$OUT" == "hello script" ]] || fail "script hello: $OUT"

log "PASS Termux Go smoke (no Play goargs, no argv wrapper)"
if [[ -n "$REPORT" ]]; then
  {
    echo "applicationId=$APP_ID"
    echo "PREFIX=$PREFIX"
    go version
  } >"$REPORT"
fi
