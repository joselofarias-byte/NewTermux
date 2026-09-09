#!/usr/bin/env bash
set -uo pipefail

STAMP="$(date +%Y%m%d-%H%M%S)"
STATE="$HOME/.local/state/opportunity-fabric"
LOGDIR="$STATE/logs"
mkdir -p "$LOGDIR"
LOG="$LOGDIR/quantus-arm64-experiment-$STAMP.log"
APT_LOG="$LOGDIR/apt-repair-$STAMP.log"
SAFE_LIST="$STATE/termux-main-only-$STAMP.list"

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

cleanup_safe_list() {
  rm -f "$SAFE_LIST" 2>/dev/null || true
}
trap cleanup_safe_list EXIT

repair_termux_keyring_if_needed() {
  echo "==> Checking Termux package repository signatures..."
  : >"$APT_LOG"
  if apt-get update >"$APT_LOG" 2>&1; then
    echo "==> Repository signatures OK."
    return 0
  fi

  if grep -q 'NO_PUBKEY 5A897D96E57CF20C' "$APT_LOG"; then
    echo "==> Detected missing official Termux autobuild signing key 5A897D96E57CF20C."
    echo "==> Refreshing termux-keyring using ONLY the official Termux main repository."

    # Do not mutate the user's configured repository files. Instead, use an
    # isolated one-off source list so the broken glibc repo cannot block the
    # keyring refresh.
    printf '%s\n' 'deb https://packages.termux.dev/apt/termux-main stable main' >"$SAFE_LIST"

    if ! apt-get \
      -o "Dir::Etc::sourcelist=$SAFE_LIST" \
      -o 'Dir::Etc::sourceparts=-' \
      -o 'APT::Get::List-Cleanup=0' \
      update >>"$APT_LOG" 2>&1; then
      echo "ERROR: could not refresh package metadata from the official Termux main repository." >&2
      tail -n 100 "$APT_LOG" >&2
      return 20
    fi

    if ! apt-get \
      -o "Dir::Etc::sourcelist=$SAFE_LIST" \
      -o 'Dir::Etc::sourceparts=-' \
      -o 'APT::Get::List-Cleanup=0' \
      install -y --reinstall termux-keyring >>"$APT_LOG" 2>&1; then
      echo "ERROR: could not reinstall the official termux-keyring package." >&2
      tail -n 100 "$APT_LOG" >&2
      return 21
    fi

    echo "==> Keyring refreshed. Re-checking all configured repositories..."
    if ! apt-get update >>"$APT_LOG" 2>&1; then
      echo "ERROR: repository signatures are still invalid after keyring refresh." >&2
      tail -n 120 "$APT_LOG" >&2
      return 22
    fi

    echo "==> Termux keyring repaired; configured repositories now verify."
    return 0
  fi

  echo "ERROR: package repository update failed, but not because of the known Termux autobuild key." >&2
  tail -n 100 "$APT_LOG" >&2
  return 23
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
