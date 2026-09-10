#!/usr/bin/env bash
set -uo pipefail

STATE="$HOME/.local/state/opportunity-fabric"
ROOT="$HOME/.local/opt/quantus-node"
mkdir -p "$STATE" "$ROOT"
STAMP="$(date +%Y%m%d-%H%M%S)"
LOG="$STATE/logs/quantus-node-arm64-glibc-preflight-$STAMP.log"
mkdir -p "$(dirname "$LOG")"
exec > >(tee -a "$LOG") 2>&1

API="https://api.github.com/repos/Quantus-Network/chain/releases/latest"

echo "==> Quantus node ARM64/glibc preflight for native Termux"
echo "==> This only downloads/verifies the official node binary and runs --version."
echo "==> It does NOT generate keys, create a wallet/preimage, start a validator, sync, or mine."
echo "==> Log: $LOG"

if ! command -v pkg >/dev/null 2>&1; then
  echo "ERROR: native Termux required." >&2
  exit 2
fi
for c in curl python tar sha256sum; do
  command -v "$c" >/dev/null 2>&1 || { echo "ERROR: missing command: $c" >&2; exit 3; }
done

META="$STATE/quantus-chain-latest-$STAMP.json"
if ! curl -fsSL --retry 6 --retry-delay 2 --retry-all-errors \
  -H 'Accept: application/vnd.github+json' \
  -H 'User-Agent: NewTermux-Quantus-Preflight' \
  "$API" -o "$META"; then
  echo "ERROR: failed to fetch latest Quantus release metadata." >&2
  exit 10
fi

readarray -t FIELDS < <(python - "$META" <<'PY'
import json,sys
j=json.load(open(sys.argv[1],encoding='utf-8'))
tag=j.get('tag_name') or ''
asset=None
for a in j.get('assets',[]):
    if a.get('name','').endswith('aarch64-unknown-linux-gnu.tar.gz'):
        asset=a; break
if not tag or not asset:
    raise SystemExit('latest release has no aarch64-unknown-linux-gnu asset')
print(tag)
print(asset['name'])
print(asset['browser_download_url'])
print((asset.get('digest') or '').removeprefix('sha256:'))
PY
)
TAG="${FIELDS[0]:-}"
ASSET="${FIELDS[1]:-}"
URL="${FIELDS[2]:-}"
EXPECTED="${FIELDS[3]:-}"

if [ -z "$TAG" ] || [ -z "$ASSET" ] || [ -z "$URL" ]; then
  echo "ERROR: could not resolve latest ARM64 Linux release asset." >&2
  exit 11
fi

echo "==> Latest release: $TAG"
echo "==> Asset: $ASSET"

DEST="$ROOT/$TAG"
ARCHIVE="$STATE/$ASSET"
mkdir -p "$DEST"

if [ ! -f "$ARCHIVE" ]; then
  echo "==> Downloading official ARM64 Linux binary archive..."
  curl -fL --retry 8 --retry-delay 2 --retry-all-errors "$URL" -o "$ARCHIVE" || exit 12
else
  echo "==> Reusing downloaded archive: $ARCHIVE"
fi

ACTUAL="$(sha256sum "$ARCHIVE" | awk '{print $1}')"
if [ -n "$EXPECTED" ]; then
  echo "==> Expected SHA-256: $EXPECTED"
  echo "==> Actual   SHA-256: $ACTUAL"
  if [ "$ACTUAL" != "$EXPECTED" ]; then
    echo "ERROR: release archive digest mismatch." >&2
    exit 13
  fi
  echo "==> Release archive SHA-256 verified."
else
  echo "WARNING: GitHub release metadata did not expose a digest; refusing unverified execution." >&2
  exit 14
fi

rm -rf "$DEST/extracted"
mkdir -p "$DEST/extracted"
tar -xzf "$ARCHIVE" -C "$DEST/extracted"
BIN="$(find "$DEST/extracted" -type f -name 'quantus-node' -print -quit)"
if [ -z "$BIN" ]; then
  BIN="$(find "$DEST/extracted" -type f -name 'quantus-node*' -perm -u+x -print -quit)"
fi
if [ -z "$BIN" ] || [ ! -f "$BIN" ]; then
  echo "ERROR: quantus-node executable not found in archive." >&2
  find "$DEST/extracted" -maxdepth 3 -type f -print
  exit 15
fi
chmod 700 "$BIN"
echo "==> Extracted node: $BIN"

RUNNER=""
if command -v grun >/dev/null 2>&1; then
  RUNNER="grun"
elif command -v glibc-runner >/dev/null 2>&1; then
  RUNNER="glibc-runner"
else
  echo "==> glibc-runner not found; installing from the Termux glibc repository."
  if ! apt-cache show glibc-runner >/dev/null 2>&1; then
    pkg install -y glibc-repo || exit 20
    pkg update -y || exit 20
  fi
  pkg install -y glibc-runner || exit 21
  if command -v grun >/dev/null 2>&1; then RUNNER="grun"; fi
  if [ -z "$RUNNER" ] && command -v glibc-runner >/dev/null 2>&1; then RUNNER="glibc-runner"; fi
fi

if [ -z "$RUNNER" ]; then
  echo "ERROR: glibc-runner installed but no runner command was found." >&2
  exit 22
fi

echo "==> Runner: $RUNNER"
echo "==> Executing only: quantus-node --version"
set +e
VERSION_OUT="$($RUNNER "$BIN" --version 2>&1)"
RC=$?
set -e 2>/dev/null || true
set +e
printf '%s\n' "$VERSION_OUT"

SUMMARY="$STATE/quantus-node-arm64-glibc-preflight-$STAMP.json"
python - "$SUMMARY" "$TAG" "$ASSET" "$ACTUAL" "$BIN" "$RUNNER" "$RC" "$VERSION_OUT" <<'PY'
import json,sys
p,tag,asset,sha,binary,runner,rc,out=sys.argv[1:]
d={
  'ok': int(rc)==0,
  'release': tag,
  'asset': asset,
  'sha256': sha,
  'binary': binary,
  'runner': runner,
  'version_returncode': int(rc),
  'version_output': out,
  'node_started': False,
  'sync_started': False,
  'keys_generated': False,
  'wallet_or_preimage_generated': False,
  'mining_started': False,
}
json.dump(d,open(p,'w',encoding='utf-8'),indent=2)
PY

if [ "$RC" -eq 0 ]; then
  echo
  echo "=== QUANTUS NODE ARM64/GLIBC PREFLIGHT: PASS ==="
  echo "Official $TAG aarch64 Linux node executes under native Termux via $RUNNER."
  echo "No node, sync, wallet/preimage, or mining was started."
  echo "==> Summary: $SUMMARY"
  echo "==> Full log: $LOG"
  exit 0
fi

echo
echo "=== QUANTUS NODE ARM64/GLIBC PREFLIGHT: NOT COMPATIBLE YET ==="
echo "The official aarch64 Linux binary did not execute successfully via $RUNNER (rc=$RC)."
echo "No node, sync, wallet/preimage, or mining was started."
echo "==> Summary: $SUMMARY"
echo "==> Full log: $LOG"
exit 30
