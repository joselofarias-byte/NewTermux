#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROUTER="$ROOT/app/src/main/assets/newtermux/installers/9router-go.sh"
HARNESS="$ROOT/app/src/main/assets/newtermux/installers/ai-harnesses.sh"
ONE_TOUCH="$ROOT/app/src/main/assets/newtermux/installers/one-touch-ai-stack.sh"
WORKSPACE="$ROOT/app/src/main/assets/newtermux/installers/open-ai-workspace.sh"
ACTIVITY="$ROOT/app/src/main/java/com/termux/app/TermuxActivity.java"
CLOCK="$ROOT/app/src/main/assets/newtermux/installers/prompt-clock.sh"
WORKFLOW="$ROOT/.github/workflows/tmp_ime_opencode_honor200.yml"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

pass() {
  echo "PASS: $*"
}

bash -n "$ROUTER"
bash -n "$HARNESS"
bash -n "$ONE_TOUCH"
bash -n "$WORKSPACE"
bash -n "$CLOCK"
pass "bundled installer scripts parse with bash -n"

if grep -Eq '^[[:space:]]*pkg[[:space:]]+install' "$ROUTER" "$HARNESS"; then
  fail "AI installers must not call pkg install; apt-get is the recovery-safe path"
fi
pass "AI installers bypass pkg wrapper"

if grep -Eq '"pkg install ' "$ACTIVITY"; then
  fail "component menu still exposes pkg install commands"
fi
pass "component menu avoids broken pkg wrapper too"

grep -Fq 'install_router_from_ci' "$ROUTER" ||
  fail "router installer does not prefer validated CI artifact"
grep -Fq '9router-go-termux-arm64' "$ROUTER" ||
  fail "router installer does not search for ARM64 CI artifact"
grep -Fq 'host_install git golang make curl' "$ROUTER" ||
  fail "router dependencies are not installed through host_install"
grep -Fq 'host_install proot-distro' "$HARNESS" ||
  fail "proot-distro is not installed through host_install"
pass "host dependencies use apt-get-backed host_install"

grep -Fq 'apt-get -o Dpkg::Options::="--force-confold" -y full-upgrade' "$ROUTER" ||
  fail "router installer lacks broken-curl repair"
grep -Fq 'apt-get -o Dpkg::Options::="--force-confold" -y full-upgrade' "$HARNESS" ||
  fail "AI installer lacks broken-curl repair"
pass "broken curl self-heal is present"

grep -Fq 'OpenCode · Abrir' "$ACTIVITY" ||
  fail "OpenCode is not promoted to primary menu"
grep -Fq 'OpenCode · Preparar' "$ACTIVITY" ||
  fail "OpenCode self-healing setup state is not promoted to primary menu"
grep -Fq 'runOpenCodeWorkspace' "$ACTIVITY" ||
  fail "primary OpenCode action does not use workspace launcher"
grep -Fq '9router-go · router local  ›' "$ACTIVITY" ||
  fail "9router-go is not visible in primary menu"
grep -Fq 'Ahorro de tokens · RTK  ›' "$ACTIVITY" ||
  fail "RTK menu is not visible in primary menu"
grep -Fq '.opencode/bin/' "$ACTIVITY" ||
  fail "OpenCode detector does not scan ~/.opencode/bin"
pass "primary AI menu and OpenCode discovery are wired"

grep -Fq '.model = "9router/free-best"' "$HARNESS" ||
  fail "OpenCode is not configured to use 9router/free-best"
grep -Fq '"baseURL": "http://127.0.0.1:20128/v1"' "$HARNESS" ||
  fail "OpenCode 9router baseURL is missing"
pass "OpenCode -> 9router configuration is embedded"

grep -Fq 'bash "$ROUTER" start' "$WORKSPACE" ||
  fail "workspace launcher does not ensure 9router is running"
grep -Fq 'bash "$HARNESS" opencode' "$WORKSPACE" ||
  fail "workspace launcher does not install missing OpenCode"
grep -Fq 'bash "$HARNESS" opencode-router' "$WORKSPACE" ||
  fail "workspace launcher does not repair missing OpenCode->9router config"
grep -Fq 'exec proot-distro login debian' "$WORKSPACE" ||
  fail "workspace launcher does not enter Debian/OpenCode"
pass "primary OpenCode launcher is self-healing end-to-end"

grep -Fq 'bash "$ROUTER" install' "$ONE_TOUCH" ||
  fail "one-touch does not install router"
grep -Fq 'bash "$ROUTER" start' "$ONE_TOUCH" ||
  fail "one-touch does not start router"
grep -Fq 'bash "$HARNESS" all' "$ONE_TOUCH" ||
  fail "one-touch does not install AI harnesses"
grep -Fq 'bash "$HARNESS" opencode-router' "$ONE_TOUCH" ||
  fail "one-touch does not connect OpenCode to router"
pass "one-touch chain contains router + harness + config stages"

grep -Fq './gradlew assembleDirectDebug' "$WORKFLOW" ||
  fail "physical APK workflow is not building directDebug"
grep -Fq "NEWTERMUX_VERSION_CODE: '202610050'" "$WORKFLOW" ||
  fail "physical APK workflow is not pinned above Play version range"
pass "physical APK is standalone direct build with independent version range"

if grep -Fq "%F{8}" "$CLOCK"; then
  fail "prompt clock still uses unreadable ANSI color 8"
fi
grep -Fq "PROMPT='%B[%D{%H:%M}]%b '" "$CLOCK" ||
  fail "prompt clock does not use readable default foreground"
pass "prompt clock is readable on dark themes"

make_fake_env() {
  local base="$1"
  local bin="$base/bin"
  mkdir -p "$bin" "$base/home" "$base/prefix/bin"
  : > "$base/log"

  cat > "$bin/pkg" <<'EOF'
#!/usr/bin/env bash
echo "PKG_CALLED $*" >> "$FAKE_LOG"
exit 91
EOF

  cat > "$bin/apt-get" <<'EOF'
#!/usr/bin/env bash
echo "APT_GET $*" >> "$FAKE_LOG"
for arg in "$@"; do
  if [[ "$arg" == "full-upgrade" ]]; then
    : > "$FAKE_REPAIRED"
  fi
done
exit 0
EOF

  cat > "$bin/curl" <<'EOF'
#!/usr/bin/env bash
if [[ "${1:-}" == "--version" ]]; then
  [[ -f "$FAKE_REPAIRED" ]] && { echo "curl fake repaired"; exit 0; }
  echo 'CANNOT LINK EXECUTABLE "curl": simulated ngtcp2 mismatch' >&2
  exit 127
fi
exit 1
EOF

  cat > "$bin/git" <<'EOF'
#!/usr/bin/env bash
echo "GIT $*" >> "$FAKE_LOG"
if [[ "${1:-}" == "clone" ]]; then
  target="${@: -1}"
  mkdir -p "$target/.git"
fi
exit 0
EOF

  cat > "$bin/make" <<'EOF'
#!/usr/bin/env bash
echo "MAKE $*" >> "$FAKE_LOG"
cat > "$PWD/9router-go" <<'INNER'
#!/usr/bin/env bash
if [[ "${1:-}" == "version" ]]; then echo "9router-go fake"; fi
INNER
chmod +x "$PWD/9router-go"
EOF

  cat > "$bin/proot-distro" <<'EOF'
#!/usr/bin/env bash
echo "PROOT $*" >> "$FAKE_LOG"
if [[ " $* " == *" /bin/bash -s "* ]]; then
  cat >/dev/null
fi
exit 0
EOF

  chmod +x "$bin/"*
}

run_router_repair_test() {
  local base
  base="$(mktemp -d)"
  make_fake_env "$base"

  FAKE_LOG="$base/log" \
  FAKE_REPAIRED="$base/repaired" \
  HOME="$base/home" \
  PREFIX="$base/prefix" \
  PATH="$base/bin:/usr/bin:/bin" \
    bash "$ROUTER" install >/tmp/newtermux-router-test.out 2>&1 ||
      { cat /tmp/newtermux-router-test.out; fail "router self-heal smoke failed"; }

  [[ -f "$base/repaired" ]] || fail "router did not run full-upgrade after broken curl"
  [[ -x "$base/prefix/bin/9router-go" ]] || fail "router was not installed after repair"
  ! grep -Fq 'PKG_CALLED' "$base/log" || fail "router invoked pkg wrapper"
  grep -Fq 'full-upgrade' "$base/log" || fail "router repair did not full-upgrade"
  grep -Fq 'install -y git golang make curl' "$base/log" ||
    fail "router dependencies were not installed with apt-get"
  pass "router recovers simulated broken curl and installs without pkg"
  rm -rf "$base"
}

run_harness_repair_test() {
  local base
  base="$(mktemp -d)"
  make_fake_env "$base"

  FAKE_LOG="$base/log" \
  FAKE_REPAIRED="$base/repaired" \
  HOME="$base/home" \
  PREFIX="$base/prefix" \
  PATH="$base/bin:/usr/bin:/bin" \
    bash "$HARNESS" prepare >/tmp/newtermux-harness-test.out 2>&1 ||
      { cat /tmp/newtermux-harness-test.out; fail "AI harness self-heal smoke failed"; }

  [[ -f "$base/repaired" ]] || fail "AI harness did not repair broken curl"
  ! grep -Fq 'PKG_CALLED' "$base/log" || fail "AI harness invoked pkg wrapper"
  grep -Fq 'install -y proot-distro' "$base/log" ||
    fail "AI harness did not install proot-distro through apt-get"
  pass "AI harness recovers simulated broken curl and reaches Debian preparation"
  rm -rf "$base"
}

run_rtk_default_test() {
  local base
  base="$(mktemp -d)"
  make_fake_env "$base"

  # saver-safe does not need a working network; curl health is allowed to fail.
  : > "$base/repaired"
  FAKE_LOG="$base/log" \
  FAKE_REPAIRED="$base/repaired" \
  HOME="$base/home" \
  PREFIX="$base/prefix" \
  PATH="$base/bin:/usr/bin:/bin" \
    bash "$ROUTER" saver-safe >/tmp/newtermux-rtk-test.out 2>&1 ||
      { cat /tmp/newtermux-rtk-test.out; fail "RTK default profile smoke failed"; }

  local cfg="$base/home/.config/9router-go/token-saver.env"
  [[ -f "$cfg" ]] || fail "RTK profile file was not created"
  grep -Fxq 'RTK_ENABLED=true' "$cfg" || fail "RTK is not enabled"
  grep -Fxq 'CAVEMAN_ENABLED=false' "$cfg" || fail "Caveman should stay opt-in"
  grep -Fxq 'PONYTAIL_ENABLED=false' "$cfg" || fail "Ponytail should stay opt-in"
  pass "RTK is default-on while aggressive modes stay opt-in"
  rm -rf "$base"
}

run_router_repair_test
run_harness_repair_test
run_rtk_default_test

echo
echo "ALL_NEWTERMUX_AI_STACK_TESTS=PASS"
