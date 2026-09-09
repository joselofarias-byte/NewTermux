#!/usr/bin/env bash
set -u

API_BASE="https://api.github.com/repos/joselofarias-byte/NewTermux/contents/scripts"
TMPROOT="${TMPDIR:-$HOME/.cache}"
STAMP="$(date +%Y%m%d-%H%M%S)"
TMP="$TMPROOT/quantus-arm64-bootstrap-$STAMP"
mkdir -p "$TMP"
cleanup() { rm -rf "$TMP" 2>/dev/null || true; }
trap cleanup EXIT

fetch_script() {
  local name="$1" out="$2"
  curl -fL --retry 8 --retry-delay 2 --retry-all-errors --connect-timeout 15 \
    -H 'Accept: application/vnd.github.raw+json' \
    "$API_BASE/$name?ref=main" -o "$out"
  bash -n "$out"
}

if command -v of >/dev/null 2>&1 && of status 2>/dev/null | grep -q '"version": "0.2.1"'; then
  echo "==> Opportunity Fabric 0.2.1 already installed; skipping redundant reinstall."
else
  echo "==> Updating Opportunity Fabric first..."
  if fetch_script install-opportunity-fabric.sh "$TMP/install.sh"; then
    bash "$TMP/install.sh"
    rc=$?
    if [ "$rc" -ne 0 ]; then
      echo "ERROR: Opportunity Fabric installer returned $rc." >&2
      exit "$rc"
    fi
  else
    echo "ERROR: could not fetch/validate Opportunity Fabric installer." >&2
    exit 10
  fi
fi

echo
echo "==> Launching controlled Quantus ARM64 source-build experiment..."
if fetch_script run-quantus-arm64-experiment.sh "$TMP/experiment.sh"; then
  bash "$TMP/experiment.sh"
  rc=$?
else
  echo "ERROR: could not fetch/validate Quantus experiment runner." >&2
  exit 11
fi

echo
echo "==> Bootstrap finished with experiment code $rc. Your interactive Termux shell remains open."
exit "$rc"
