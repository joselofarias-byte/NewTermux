#!/usr/bin/env bash
set -uo pipefail

STAMP="$(date +%Y%m%d-%H%M%S)"
STATE="$HOME/.local/state/opportunity-fabric"
LOGDIR="$STATE/logs"
mkdir -p "$LOGDIR"
LOG="$LOGDIR/quantus-arm64-experiment-$STAMP.log"
APT_LOG="$LOGDIR/apt-repair-$STAMP.log"
KEY_TMP="$STATE/termux-autobuilds-$STAMP.gpg"

exec > >(tee -a "$LOG") 2>&1

echo "==> Quantus ARM64 experiment"
echo "==> Log: $LOG"
echo "==> This performs only a source build + short benchmark; it does NOT start mining or create/import a wallet."

if ! command -v pkg >/dev/null 2>&1; then
  echo "ERROR: this experiment is intended for native Termux." >&2
  exit 1
fi
if ! command -v of >/dev/null 2>&1; then
  echo "ERROR: Opportunity Fabric is not installed." >&2
  exit 2
fi

cleanup() { rm -f "$KEY_TMP" 2>/dev/null || true; }
trap cleanup EXIT

bootstrap_official_termux_key() {
  local api expected actual share_dir trust_dir
  api="https://api.github.com/repos/termux/termux-packages/contents/packages/termux-keyring/termux-autobuilds.gpg?ref=master"
  expected="c5ed76a1b9a1f2bc2e296bdd5ca50cf1f1f12706"

  echo "==> Bootstrapping the missing Termux autobuild key from the official termux/termux-packages repository."
  if ! curl -fL --retry 8 --retry-delay 2 --retry-all-errors --connect-timeout 15 \
      -H 'Accept: application/vnd.github.raw+json' "$api" -o "$KEY_TMP"; then
    echo "ERROR: could not download the official Termux autobuild key from GitHub." >&2
    return 30
  fi

  actual="$(python - "$KEY_TMP" <<'PY'
import hashlib, pathlib, sys
p = pathlib.Path(sys.argv[1])
b = p.read_bytes()
print(hashlib.sha1(b"blob " + str(len(b)).encode() + b"\0" + b).hexdigest())
PY
)"
  if [ "$actual" != "$expected" ]; then
    echo "ERROR: official key integrity check failed." >&2
    echo "Expected Git blob: $expected" >&2
    echo "Received Git blob: $actual" >&2
    return 31
  fi

  share_dir="${PREFIX}/share/termux-keyring"
  trust_dir="${PREFIX}/etc/apt/trusted.gpg.d"
  mkdir -p "$share_dir" "$trust_dir"
  install -m 600 "$KEY_TMP" "$share_dir/termux-autobuilds.gpg"
  ln -sfn "$share_dir/termux-autobuilds.gpg" "$trust_dir/termux-autobuilds.gpg"
  echo "==> Official key installed after exact Git-blob verification."
  return 0
}

repair_termux_keyring_if_needed() {
  echo "==> Checking Termux package repository signatures..."
  : >"$APT_LOG"
  if apt-get update >"$APT_LOG" 2>&1; then
    echo "==> Repository signatures OK."
    return 0
  fi

  if ! grep -q 'NO_PUBKEY 5A897D96E57CF20C' "$APT_LOG"; then
    echo "ERROR: package repository update failed for an unrelated reason." >&2
    tail -n 100 "$APT_LOG" >&2
    return 23
  fi

  echo "==> Missing official Termux autobuild signing key 5A897D96E57CF20C detected."
  bootstrap_official_termux_key || return $?

  echo "==> Re-checking configured repositories with the verified official key..."
  if ! apt-get update >>"$APT_LOG" 2>&1; then
    echo "ERROR: repository signatures still fail after installing the verified official key." >&2
    tail -n 120 "$APT_LOG" >&2
    return 32
  fi

  echo "==> Package metadata verifies. Reinstalling termux-keyring to restore the package-managed keyring."
  if ! apt-get install -y --reinstall termux-keyring >>"$APT_LOG" 2>&1; then
    echo "ERROR: termux-keyring reinstall failed after trust bootstrap." >&2
    tail -n 120 "$APT_LOG" >&2
    return 33
  fi

  if ! apt-get update >>"$APT_LOG" 2>&1; then
    echo "ERROR: final repository verification failed after termux-keyring reinstall." >&2
    tail -n 120 "$APT_LOG" >&2
    return 34
  fi
  echo "==> Termux keyring repaired; configured repositories verify."
  return 0
}

repair_termux_keyring_if_needed
rc=$?
if [ "$rc" -ne 0 ]; then
  echo "==> Repository repair failed with code $rc."
  exit "$rc"
fi

echo "==> Installing only missing native build dependencies..."
missing=()
for spec in "git:git" "rustc:rust" "cargo:rust" "clang:clang" "cmake:cmake" "pkg-config:pkg-config" "make:make" "openssl:openssl"; do
  cmd="${spec%%:*}"; pkgname="${spec#*:}"
  command -v "$cmd" >/dev/null 2>&1 || missing+=("$pkgname")
done

if [ "${#missing[@]}" -gt 0 ]; then
  uniq_pkgs=()
  for p in "${missing[@]}"; do
    seen=0
    for q in "${uniq_pkgs[@]:-}"; do [ "$q" = "$p" ] && seen=1; done
    [ "$seen" -eq 0 ] && uniq_pkgs+=("$p")
  done
  echo "==> Installing: ${uniq_pkgs[*]}"
  if ! apt-get install -y "${uniq_pkgs[@]}"; then
    echo "ERROR: dependency installation failed." >&2
    exit 24
  fi
else
  echo "==> Toolchain already present."
fi

echo "==> Preflight after dependency setup"
of quantus-preflight
if ! of quantus-preflight | grep -q '"ready_to_attempt_build": true'; then
  echo "ERROR: build toolchain is still incomplete after dependency setup." >&2
  exit 25
fi

echo "==> Starting controlled official-source build with 2 Cargo jobs."
echo "==> The phone may get warm; compilation is intentionally limited to 2 parallel jobs."
RESULT="$STATE/quantus-arm64-result-$STAMP.json"
of quantus-build --execute --jobs 2 | tee "$RESULT"

python - "$RESULT" <<'PY'
import json, sys
p=sys.argv[1]
try:
    d=json.load(open(p, encoding='utf-8'))
except Exception as e:
    print(f"RESULT_PARSE_ERROR: {e}")
    raise SystemExit(3)
if d.get('ok'):
    print("\n=== QUANTUS ARM64 EXPERIMENT: SUCCESS ===")
    print("Official-source miner compiled and the short CPU benchmark returned success.")
    print("Binary:", d.get('binary'))
    if d.get('benchmark_output'):
        print("\nBenchmark output:\n", d.get('benchmark_output'))
    raise SystemExit(0)
print("\n=== QUANTUS ARM64 EXPERIMENT: BUILD/BENCHMARK NOT YET VIABLE ===")
print("Stage:", d.get('stage'))
if d.get('log_tail'):
    print("\nBuild log tail:\n", d.get('log_tail'))
print("No mining was started and no wallet secret was touched.")
raise SystemExit(4)
PY
rc=$?
echo "==> Experiment return code: $rc"
echo "==> Full log: $LOG"
exit "$rc"
