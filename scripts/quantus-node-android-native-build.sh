#!/usr/bin/env bash
set -uo pipefail

STAMP="$(date +%Y%m%d-%H%M%S)"
STATE="$HOME/.local/state/opportunity-fabric"
LOGDIR="$STATE/logs"
LOG="$LOGDIR/quantus-node-android-native-build-$STAMP.log"
SUMMARY="$STATE/quantus-node-android-native-build-$STAMP.json"
mkdir -p "$LOGDIR"
exec > >(tee -a "$LOG") 2>&1

echo "==> Quantus native Android/Termux build gate"
echo "==> Decision: NO_GO"
echo "==> Mode: no-go-android-termux"
echo "==> Build disabled: true"
echo "==> No clone, dependency installation, Cargo build, node, sync, wallet, validator or mining process will be started."
echo "==> Reason 1: the controlled Quantus v1.0.1 node probe reached rustix 1.1.2 and hit the known Android linux_raw_sys compilation failure."
echo "==> Reason 2: the same probe exposed a local protoc/Abseil ABI mismatch."
echo "==> Reason 3: removing those build blockers would not establish supported Android mining or economics worth further phone resource consumption."
echo "==> Reopen only after a materially changed upstream condition and a new Opportunity Fabric evaluation."

python - "$SUMMARY" "$LOG" <<'PY'
import json
import sys
from datetime import datetime, timezone

summary, log = sys.argv[1:]
with open(summary, "w", encoding="utf-8") as fh:
    json.dump(
        {
            "ok": True,
            "decision": "NO_GO",
            "mode": "no-go-android-termux",
            "build_disabled": True,
            "stage": "policy_gate",
            "executed": False,
            "clone_started": False,
            "dependency_install_started": False,
            "cargo_build_started": False,
            "node_started": False,
            "sync_started": False,
            "wallet_started": False,
            "validation_started": False,
            "mining_started": False,
            "recorded_at_utc": datetime.now(timezone.utc).isoformat(),
            "log": log,
            "reopen_condition": "Material upstream Android/ARM64 support change plus economics worth benchmarking",
        },
        fh,
        indent=2,
    )
PY

echo "==> Summary: $SUMMARY"
echo "==> Log: $LOG"
echo "=== QUANTUS ANDROID/TERMUX: NO_GO — NOTHING EXECUTED ==="
exit 0
