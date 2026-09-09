#!/usr/bin/env bash
set -uo pipefail

STAMP="$(date +%Y%m%d-%H%M%S)"
STATE="$HOME/.local/state/opportunity-fabric"
LOGDIR="$STATE/logs"
BACKUPDIR="$STATE/backups"
mkdir -p "$LOGDIR" "$BACKUPDIR"
LOG="$LOGDIR/quantus-arm64-experiment-$STAMP.log"
APT_LOG="$LOGDIR/apt-repair-$STAMP.log"
KEY_TMP="$STATE/termux-autobuilds-$STAMP.gpg"
SAFE_LIST="$STATE/termux-main-signed-$STAMP.list"
OFFICIAL_KEY="$PREFIX/share/termux-keyring/termux-autobuilds.gpg"

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

cleanup() { rm -f "$KEY_TMP" "$SAFE_LIST" 2>/dev/null || true; }
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

  share_dir="$PREFIX/share/termux-keyring"
  trust_dir="$PREFIX/etc/apt/trusted.gpg.d"
  mkdir -p "$share_dir" "$trust_dir"
  install -m 600 "$KEY_TMP" "$OFFICIAL_KEY"
  install -m 644 "$KEY_TMP" "$trust_dir/termux-autobuilds.gpg"
  echo "==> Official key installed after exact Git-blob verification."
  return 0
}

write_safe_main_source() {
  [ -f "$OFFICIAL_KEY" ] || return 1
  printf '%s\n' "deb [signed-by=$OFFICIAL_KEY] https://packages.termux.dev/apt/termux-main stable main" >"$SAFE_LIST"
}

isolated_main_apt() {
  write_safe_main_source || return 1
  apt-get \
    -o "Dir::Etc::sourcelist=$SAFE_LIST" \
    -o 'Dir::Etc::sourceparts=-' \
    -o 'APT::Get::List-Cleanup=0' \
    "$@"
}

bind_glibc_sources_to_official_key() {
  local found=0 changed=0 file backup tag result

  while IFS= read -r file; do
    [ -f "$file" ] || continue
    grep -q 'termux-glibc' "$file" || continue
    found=1

    tag="$(printf '%s' "$file" | python -c 'import hashlib,sys; print(hashlib.sha1(sys.stdin.buffer.read()).hexdigest()[:12])')"
    backup="$BACKUPDIR/$(basename "$file").$tag.$STAMP.bak"
    cp -a "$file" "$backup"
    echo "==> Backed up glibc source: $backup"

    result="$(python - "$file" "$OFFICIAL_KEY" <<'PY'
from pathlib import Path
import re, sys
p = Path(sys.argv[1])
key = sys.argv[2]
text = p.read_text(encoding='utf-8')
changed = False

if p.suffix == '.sources':
    # Deb822 source format. Patch only stanzas that reference termux-glibc.
    parts = re.split(r'(\n\s*\n)', text)
    for i in range(0, len(parts), 2):
        stanza = parts[i]
        if 'termux-glibc' not in stanza:
            continue
        lines = stanza.splitlines()
        out = []
        seen = False
        for line in lines:
            if re.match(r'^\s*Signed-By\s*:', line, flags=re.I):
                new = f'Signed-By: {key}'
                if line != new:
                    changed = True
                out.append(new)
                seen = True
            else:
                out.append(line)
        if not seen:
            out.append(f'Signed-By: {key}')
            changed = True
        parts[i] = '\n'.join(out)
    if changed:
        p.write_text(''.join(parts), encoding='utf-8')
else:
    # Traditional one-line .list format (including sources.list).
    out = []
    for line in text.splitlines(True):
        raw = line.rstrip('\n')
        suffix = '\n' if line.endswith('\n') else ''
        stripped = raw.lstrip()
        if stripped.startswith('deb ') and 'termux-glibc' in stripped:
            indent = raw[:len(raw)-len(stripped)]
            if stripped.startswith('deb ['):
                head, rest = stripped.split(']', 1)
                if 'signed-by=' in head:
                    new_head = re.sub(r'signed-by=[^\s\]]+', f'signed-by={key}', head)
                else:
                    new_head = head + f' signed-by={key}'
                new = new_head + ']' + rest
            else:
                new = f'deb [signed-by={key}] ' + stripped[4:]
            if new != stripped:
                changed = True
            raw = indent + new
        out.append(raw + suffix)
    if changed:
        p.write_text(''.join(out), encoding='utf-8')

print('changed' if changed else 'unchanged')
PY
)"
    echo "==> Source trust patch ($file): $result"
    [ "$result" = "changed" ] && changed=1
  done < <(
    {
      [ -f "$PREFIX/etc/apt/sources.list" ] && printf '%s\n' "$PREFIX/etc/apt/sources.list"
      find "$PREFIX/etc/apt/sources.list.d" -maxdepth 1 -type f \( -name '*.list' -o -name '*.sources' \) -print 2>/dev/null || true
      grep -RIl --include='*.list' --include='*.sources' 'termux-glibc' "$PREFIX/etc/apt" 2>/dev/null || true
    } | sort -u
  )

  if [ "$found" -eq 0 ]; then
    echo "==> No .list/.sources file containing termux-glibc was found."
    echo "==> APT source diagnostics:"
    grep -RIn 'termux-glibc' "$PREFIX/etc/apt" 2>/dev/null || true
    apt-config dump 2>/dev/null | grep -Ei 'Dir::Etc|source' | head -n 80 || true
    return 1
  fi

  if [ "$changed" -eq 1 ]; then
    echo "==> termux-glibc source is now explicitly bound to the verified official signing key."
  else
    echo "==> termux-glibc source already had an explicit signing-key binding."
  fi
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
    echo "==> Configured APT has an unrelated failure. The Quantus experiment will use only an isolated signed official Termux-main source."
    tail -n 80 "$APT_LOG" >&2
    return 0
  fi

  echo "==> Missing official Termux autobuild signing key 5A897D96E57CF20C detected."
  bootstrap_official_termux_key || return $?

  echo "==> Verifying the official Termux main repository with explicit signed-by."
  if ! isolated_main_apt update >>"$APT_LOG" 2>&1; then
    echo "ERROR: isolated signed official-main update failed; cannot safely install build dependencies." >&2
    tail -n 120 "$APT_LOG" >&2
    return 32
  fi

  echo "==> Repairing configured termux-glibc source trust without disabling signature checks."
  bind_glibc_sources_to_official_key || true

  echo "==> Re-checking all configured repositories..."
  if apt-get update >>"$APT_LOG" 2>&1; then
    echo "==> Configured repositories verify with the official key."
  else
    echo "==> Warning: a configured repository still fails, but the verified isolated Termux-main source is healthy."
    echo "==> This will NOT disable signature checks or use the failing repository; continuing only with signed official Termux-main for build dependencies."
    tail -n 100 "$APT_LOG" >&2
  fi

  # Best effort: package refresh is not required once the exact official key is verified.
  isolated_main_apt install -y termux-keyring >>"$APT_LOG" 2>&1 || true
  return 0
}

repair_termux_keyring_if_needed
rc=$?
if [ "$rc" -ne 0 ]; then
  echo "==> Repository bootstrap failed with code $rc."
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
  echo "==> Installing from verified official Termux main: ${uniq_pkgs[*]}"
  if ! isolated_main_apt install -y "${uniq_pkgs[@]}"; then
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
