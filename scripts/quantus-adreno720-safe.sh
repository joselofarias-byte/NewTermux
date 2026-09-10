#!/usr/bin/env bash
set -uo pipefail

BIN="${QUANTUS_BIN:-$HOME/.local/src/quantus-miner/target/release/quantus-miner}"
MODE="${1:-check}"
GPU_BATCH="${QUANTUS_GPU_BATCH:-100000}"
GPU_THROTTLE_MS="${QUANTUS_GPU_THROTTLE_MS:-50}"
CPU_WORKERS="${QUANTUS_CPU_WORKERS:-0}"
BURNIN_SECONDS="${QUANTUS_BURNIN_SECONDS:-20}"
NODE_ADDR="${MINER_NODE_ADDR:-127.0.0.1:9833}"
AUTH_FILE="${MINER_AUTH_TOKEN_FILE:-}"
TLS_FILE="${MINER_TLS_CERT_SHA256_FILE:-}"
STATE="$HOME/.local/state/opportunity-fabric"
LOGDIR="$STATE/logs"
mkdir -p "$LOGDIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
LOG="$LOGDIR/quantus-adreno720-safe-$STAMP.log"

exec > >(tee -a "$LOG") 2>&1

usage() {
  cat <<'EOF'
Usage:
  quantus-adreno720-safe.sh check
  quantus-adreno720-safe.sh serve

Environment overrides:
  QUANTUS_BIN=/path/to/quantus-miner
  QUANTUS_GPU_BATCH=100000          # validated ceiling on Adreno 720
  QUANTUS_GPU_THROTTLE_MS=50        # used by serve only
  QUANTUS_CPU_WORKERS=0             # keep GPU isolated by default
  QUANTUS_BURNIN_SECONDS=20
  MINER_NODE_ADDR=127.0.0.1:9833
  MINER_AUTH_TOKEN_FILE=/path/to/miner-auth-token
  MINER_TLS_CERT_SHA256_FILE=/path/to/miner-tls-cert-sha256

Notes:
- This profile is based on native Termux/aarch64 testing on Adreno (TM) 720.
- 25k, 50k and 100k GPU batches were stable at ~86 KH/s.
- 250k caused a WGPU buffer mapping timeout/device loss.
- Values above 100000 are blocked unless QUANTUS_UNSAFE_OVERRIDE=1.
EOF
}

if [ "$MODE" != "check" ] && [ "$MODE" != "serve" ]; then
  usage
  exit 2
fi

if [ ! -x "$BIN" ]; then
  echo "ERROR: quantus-miner binary not found/executable: $BIN" >&2
  exit 3
fi

case "$GPU_BATCH" in ''|*[!0-9]*) echo "ERROR: QUANTUS_GPU_BATCH must be numeric." >&2; exit 4;; esac
if [ "$GPU_BATCH" -gt 100000 ] && [ "${QUANTUS_UNSAFE_OVERRIDE:-0}" != "1" ]; then
  echo "ERROR: Refusing GPU batch $GPU_BATCH on Adreno 720; validated stable ceiling is 100000." >&2
  echo "Set QUANTUS_UNSAFE_OVERRIDE=1 only if you intentionally want an experimental stress test." >&2
  exit 5
fi

case "$CPU_WORKERS" in ''|*[!0-9]*) echo "ERROR: QUANTUS_CPU_WORKERS must be numeric." >&2; exit 6;; esac
case "$GPU_THROTTLE_MS" in ''|*[!0-9]*) echo "ERROR: QUANTUS_GPU_THROTTLE_MS must be numeric." >&2; exit 7;; esac
case "$BURNIN_SECONDS" in ''|*[!0-9]*) echo "ERROR: QUANTUS_BURNIN_SECONDS must be numeric." >&2; exit 8;; esac

run_check() {
  local tmp rc
  tmp="$LOGDIR/quantus-adreno720-burnin-$STAMP.log"
  echo "==> Adreno 720 safe-profile check"
  echo "==> Binary: $BIN"
  echo "==> GPU batch: $GPU_BATCH"
  echo "==> Burn-in: ${BURNIN_SECONDS}s"
  echo "==> CPU workers during check: 0"

  set +e
  if command -v timeout >/dev/null 2>&1; then
    RUST_BACKTRACE=1 timeout "$((BURNIN_SECONDS + 45))s" "$BIN" benchmark \
      --cpu-workers 0 \
      --gpu-devices 1 \
      --gpu-batch-size "$GPU_BATCH" \
      --duration "$BURNIN_SECONDS" 2>&1 | tee "$tmp"
  else
    RUST_BACKTRACE=1 "$BIN" benchmark \
      --cpu-workers 0 \
      --gpu-devices 1 \
      --gpu-batch-size "$GPU_BATCH" \
      --duration "$BURNIN_SECONDS" 2>&1 | tee "$tmp"
  fi
  rc=${PIPESTATUS[0]}
  set -e 2>/dev/null || true
  set +e

  if [ "$rc" -ne 0 ] || grep -Eqi 'device lost|unresponsive|mapping timed out|panicked at|timed out while waiting' "$tmp"; then
    echo "ERROR: Adreno 720 check failed/unstable (rc=$rc)." >&2
    echo "==> Log: $tmp" >&2
    return 20
  fi

  echo "==> Adreno 720 check PASSED at batch=$GPU_BATCH."
  return 0
}

run_serve() {
  if [ -z "$AUTH_FILE" ] || [ -z "$TLS_FILE" ]; then
    echo "ERROR: serve requires file-based node credentials." >&2
    echo "Set MINER_AUTH_TOKEN_FILE and MINER_TLS_CERT_SHA256_FILE." >&2
    echo "Their contents are not printed by this script." >&2
    return 30
  fi
  if [ ! -r "$AUTH_FILE" ]; then
    echo "ERROR: auth-token file is not readable: $AUTH_FILE" >&2
    return 31
  fi
  if [ ! -r "$TLS_FILE" ]; then
    echo "ERROR: TLS fingerprint file is not readable: $TLS_FILE" >&2
    return 32
  fi

  echo "==> Starting Quantus with validated Adreno 720 profile"
  echo "==> Node: $NODE_ADDR"
  echo "==> GPU batch: $GPU_BATCH"
  echo "==> GPU throttle: ${GPU_THROTTLE_MS}ms"
  echo "==> CPU workers: $CPU_WORKERS"
  echo "==> Metrics: http://127.0.0.1:9900/metrics"
  echo "==> Credential contents will not be printed."

  exec "$BIN" serve \
    --node-addr "$NODE_ADDR" \
    --auth-token-file "$AUTH_FILE" \
    --tls-cert-sha256-file "$TLS_FILE" \
    --cpu-workers "$CPU_WORKERS" \
    --gpu-devices 1 \
    --gpu-batch-size "$GPU_BATCH" \
    --gpu-throttle-ms "$GPU_THROTTLE_MS" \
    --metrics-port 9900
}

case "$MODE" in
  check)
    run_check
    rc=$?
    echo "==> Full log: $LOG"
    exit "$rc"
    ;;
  serve)
    run_check || exit $?
    echo "==> Burn-in passed; entering serve mode."
    run_serve
    ;;
esac
