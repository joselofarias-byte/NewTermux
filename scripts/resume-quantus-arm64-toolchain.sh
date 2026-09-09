#!/usr/bin/env bash
set -uo pipefail

STAMP="$(date +%Y%m%d-%H%M%S)"
STATE="$HOME/.local/state/opportunity-fabric"
LOGDIR="$STATE/logs"
BACKUPDIR="$STATE/backups"
mkdir -p "$LOGDIR" "$BACKUPDIR"
LOG="$LOGDIR/quantus-arm64-resume-$STAMP.log"
SUMMARY="$STATE/quantus-arm64-safe-benchmark-$STAMP.json"

exec > >(tee -a "$LOG") 2>&1

OFFICIAL_KEY="${PREFIX}/share/termux-keyring/termux-autobuilds.gpg"
OFFICIAL_MAIN="https://packages-cf.termux.dev/apt/termux-main/"
SRC="$HOME/.local/src/quantus-miner"
BIN="$SRC/target/release/quantus-miner"

CPU_SECONDS="${QUANTUS_CPU_BENCH_SECONDS:-10}"
GPU_SECONDS="${QUANTUS_GPU_BENCH_SECONDS:-10}"
CPU_WORKERS="${QUANTUS_CPU_WORKERS:-4}"
GPU_BATCHES=(25000 50000 100000 250000)

printf '%s\n' "==> Quantus ARM64 safe resume: toolchain -> build recovery -> CPU baseline -> adaptive Adreno/Vulkan probe"
printf '%s\n' "==> Log: $LOG"
printf '%s\n' "==> This script never starts network mining and never reads/imports a wallet secret."

if ! command -v pkg >/dev/null 2>&1 || ! command -v of >/dev/null 2>&1; then
  echo "ERROR: native Termux + Opportunity Fabric are required." >&2
  exit 2
fi
if [ ! -f "$OFFICIAL_KEY" ]; then
  echo "ERROR: verified Termux autobuild key is missing; run the main Opportunity Fabric bootstrap first." >&2
  exit 3
fi

backup_file() {
  local f="$1" tag out
  tag="$(printf '%s' "$f" | python -c 'import hashlib,sys; print(hashlib.sha1(sys.stdin.buffer.read()).hexdigest()[:12])')"
  out="$BACKUPDIR/$(basename "$f").$tag.$STAMP.bak"
  cp -a "$f" "$out"
  echo "$out"
}

normalize_main_file() {
  local f="$1"
  python - "$f" "$OFFICIAL_KEY" "$OFFICIAL_MAIN" <<'PY'
from pathlib import Path
import re, sys
p=Path(sys.argv[1]); key=sys.argv[2]; main=sys.argv[3]
txt=p.read_text(encoding="utf-8")
changed=False

if p.suffix == ".sources":
    parts=re.split(r"(\n\s*\n)", txt)
    for i in range(0,len(parts),2):
        s=parts[i]
        if "termux-glibc" in s:
            continue
        if not ("termux.net" in s or "apt/termux-main" in s or "termux-main" in s):
            continue
        lines=s.splitlines()
        wanted={
            "types":"Types: deb",
            "uris":f"URIs: {main}",
            "suites":"Suites: stable",
            "components":"Components: main",
            "signed-by":f"Signed-By: {key}",
        }
        seen=set(); out=[]
        for line in lines:
            if ":" in line and not line[:1].isspace():
                k=line.split(":",1)[0].strip().lower()
                if k in wanted:
                    nl=wanted[k]
                    changed |= (line != nl)
                    out.append(nl); seen.add(k); continue
            out.append(line)
        for k,nl in wanted.items():
            if k not in seen:
                out.append(nl); changed=True
        parts[i]="\n".join(out)
    if changed: p.write_text("".join(parts),encoding="utf-8")
else:
    out=[]
    for line in txt.splitlines(True):
        raw=line.rstrip("\n"); nl="\n" if line.endswith("\n") else ""
        s=raw.lstrip(); indent=raw[:len(raw)-len(s)]
        if s.startswith("deb ") and "termux-glibc" not in s and ("termux.net" in s or "apt/termux-main" in s):
            new=f"deb [signed-by={key}] {main} stable main"
            changed |= (s != new)
            raw=indent+new
        out.append(raw+nl)
    if changed: p.write_text("".join(out),encoding="utf-8")
print("changed" if changed else "unchanged")
PY
}

found=0
changed=0
while IFS= read -r f; do
  [ -f "$f" ] || continue
  if ! grep -Eq 'termux\.net|apt/termux-main|termux-main' "$f"; then
    continue
  fi
  if grep -q 'termux-glibc' "$f" && ! grep -Eq 'termux\.net|apt/termux-main' "$f"; then
    continue
  fi
  found=1
  b="$(backup_file "$f")"
  echo "==> Backup main source: $b"
  r="$(normalize_main_file "$f")"
  echo "==> Main source patch ($f): $r"
  [ "$r" = changed ] && changed=1
done < <(
  {
    [ -f "$PREFIX/etc/apt/sources.list" ] && printf '%s\n' "$PREFIX/etc/apt/sources.list"
    find "$PREFIX/etc/apt/sources.list.d" -maxdepth 1 -type f \( -name '*.list' -o -name '*.sources' \) -print 2>/dev/null || true
  } | sort -u
)

if [ "$found" -eq 0 ]; then
  target="$PREFIX/etc/apt/sources.list.d/opportunity-fabric-termux-main.sources"
  cat >"$target" <<EOF
Types: deb
URIs: $OFFICIAL_MAIN
Suites: stable
Components: main
Signed-By: $OFFICIAL_KEY
EOF
  echo "==> Created current official Termux main source: $target"
  changed=1
fi

if [ "$changed" -eq 1 ]; then
  echo "==> Termux main normalized to current official repository: $OFFICIAL_MAIN"
else
  echo "==> Termux main already normalized."
fi

echo "==> Refreshing APT metadata..."
if ! apt-get update; then
  echo "ERROR: apt-get update failed after main-source normalization." >&2
  exit 20
fi

echo "==> Checking native build toolchain..."
missing_pkgs=()
for p in rust cmake; do
  apt-cache show "$p" >/dev/null 2>&1 || missing_pkgs+=("$p")
done
if [ "${#missing_pkgs[@]}" -gt 0 ]; then
  echo "ERROR: packages still not visible: ${missing_pkgs[*]}" >&2
  exit 24
fi

install=()
command -v rustc >/dev/null 2>&1 || install+=(rust)
command -v cargo >/dev/null 2>&1 || install+=(rust)
command -v cmake >/dev/null 2>&1 || install+=(cmake)
if [ "${#install[@]}" -gt 0 ]; then
  uniq=()
  for p in "${install[@]}"; do
    case " ${uniq[*]:-} " in *" $p "*) ;; *) uniq+=("$p");; esac
  done
  echo "==> Installing: ${uniq[*]}"
  apt-get install -y "${uniq[@]}" || exit 24
else
  echo "==> Rust/Cargo/CMake already installed."
fi

echo "==> Quantus preflight"
PREFLIGHT="$(of quantus-preflight)"
printf '%s\n' "$PREFLIGHT"
if ! grep -q '"ready_to_attempt_build": true' <<<"$PREFLIGHT"; then
  echo "ERROR: Quantus toolchain preflight is incomplete." >&2
  exit 25
fi

BUILD_STATE="reused"
BUILD_RESULT=""
if [ -x "$BIN" ]; then
  echo "==> Existing release binary found; reusing it without recompiling: $BIN"
else
  echo "==> No release binary found. Starting official Quantus source build (2 Cargo jobs)."
  BUILD_RESULT="$STATE/quantus-arm64-build-result-$STAMP.json"
  set +o pipefail
  of quantus-build --execute --jobs 2 | tee "$BUILD_RESULT"
  BUILD_PIPE_RC=${PIPESTATUS[0]}
  set -o pipefail
  BUILD_STATE="attempted"
  echo "==> quantus-build command return code: $BUILD_PIPE_RC"

  # quantus-build may report stage=benchmark after a successful compile if WGPU
  # crashes on Android. The existence of an executable release binary is the
  # authoritative build-success signal for this resume path.
  if [ ! -x "$BIN" ]; then
    echo "ERROR: official source build did not produce an executable release binary." >&2
    [ -f "$BUILD_RESULT" ] && cat "$BUILD_RESULT"
    exit 30
  fi
  echo "==> Release binary exists despite any embedded benchmark failure: $BIN"
fi

# Cap requested CPU workers to online processors.
ONLINE_CPUS="$(getconf _NPROCESSORS_ONLN 2>/dev/null || nproc 2>/dev/null || echo 1)"
case "$ONLINE_CPUS" in ''|*[!0-9]*) ONLINE_CPUS=1;; esac
case "$CPU_WORKERS" in ''|*[!0-9]*) CPU_WORKERS=4;; esac
[ "$CPU_WORKERS" -gt "$ONLINE_CPUS" ] && CPU_WORKERS="$ONLINE_CPUS"
[ "$CPU_WORKERS" -lt 1 ] && CPU_WORKERS=1

echo
echo "===== QUANTUS CPU-ONLY BASELINE ====="
CPU_LOG="$LOGDIR/quantus-cpu-benchmark-$STAMP.log"
set +e
"$BIN" benchmark \
  --cpu-workers "$CPU_WORKERS" \
  --gpu-devices 0 \
  --duration "$CPU_SECONDS" 2>&1 | tee "$CPU_LOG"
CPU_RC=${PIPESTATUS[0]}
set -e 2>/dev/null || true
set +e
if [ "$CPU_RC" -ne 0 ]; then
  echo "ERROR: CPU-only benchmark failed (rc=$CPU_RC). GPU testing is skipped." >&2
  python - "$SUMMARY" "$BIN" "$BUILD_STATE" "$CPU_RC" <<'PY'
import json,sys
p,binary,build_state,cpu_rc=sys.argv[1:]
json.dump({"ok":False,"stage":"cpu_benchmark","binary":binary,"build_state":build_state,"cpu_returncode":int(cpu_rc),"gpu_tests":[]},open(p,"w"),indent=2)
PY
  echo "==> Summary: $SUMMARY"
  echo "==> Full log: $LOG"
  exit 40
fi

echo "==> CPU-only benchmark passed. GPU probing may proceed."

BEST_BATCH=0
GPU_TEST_RECORDS="$STATE/quantus-gpu-tests-$STAMP.tsv"
: > "$GPU_TEST_RECORDS"

for batch in "${GPU_BATCHES[@]}"; do
  echo
  echo "===== QUANTUS ADRENO/VULKAN PROBE: batch=$batch ====="
  GLOG="$LOGDIR/quantus-gpu-${batch}-$STAMP.log"

  # Each probe runs in a fresh process. timeout is only a final safety belt;
  # the miner currently has its own 30 s WGPU mapping timeout.
  set +e
  if command -v timeout >/dev/null 2>&1; then
    RUST_BACKTRACE=1 timeout 50s "$BIN" benchmark \
      --cpu-workers 0 \
      --gpu-devices 1 \
      --gpu-batch-size "$batch" \
      --duration "$GPU_SECONDS" 2>&1 | tee "$GLOG"
  else
    RUST_BACKTRACE=1 "$BIN" benchmark \
      --cpu-workers 0 \
      --gpu-devices 1 \
      --gpu-batch-size "$batch" \
      --duration "$GPU_SECONDS" 2>&1 | tee "$GLOG"
  fi
  GRC=${PIPESTATUS[0]}
  set -e 2>/dev/null || true
  set +e

  if [ "$GRC" -eq 0 ] && \
     ! grep -Eqi 'device lost|unresponsive|mapping timed out|panicked at|timed out while waiting' "$GLOG"; then
    echo "==> GPU probe stable at batch=$batch (rc=0)."
    printf '%s\t%s\t%s\n' "$batch" "$GRC" stable >> "$GPU_TEST_RECORDS"
    BEST_BATCH="$batch"
    sleep 3
    continue
  fi

  echo "==> GPU probe unstable at batch=$batch (rc=$GRC). Stopping escalation to protect the Android GPU driver."
  printf '%s\t%s\t%s\n' "$batch" "$GRC" unstable >> "$GPU_TEST_RECORDS"
  break
done

python - "$SUMMARY" "$BIN" "$BUILD_STATE" "$CPU_RC" "$BEST_BATCH" "$GPU_TEST_RECORDS" <<'PY'
import json,sys
p,binary,build_state,cpu_rc,best,records=sys.argv[1:]
tests=[]
try:
    for line in open(records,encoding="utf-8"):
        batch,rc,status=line.rstrip("\n").split("\t")
        tests.append({"batch_size":int(batch),"returncode":int(rc),"status":status})
except FileNotFoundError:
    pass
best=int(best)
d={
    "ok": True,
    "stage": "safe_benchmark_complete",
    "binary": binary,
    "build_state": build_state,
    "cpu_returncode": int(cpu_rc),
    "gpu_best_stable_batch": best if best else None,
    "gpu_tests": tests,
    "network_mining_started": False,
    "wallet_secret_touched": False,
}
json.dump(d,open(p,"w",encoding="utf-8"),indent=2)
PY

echo
echo "=== QUANTUS ARM64 SAFE RESUME COMPLETE ==="
echo "Binary: $BIN"
echo "CPU benchmark: PASS"
if [ "$BEST_BATCH" -gt 0 ]; then
  echo "GPU: stable through batch size $BEST_BATCH"
  echo "Next phase: use this as the conservative starting point for a throttled serve configuration."
else
  echo "GPU: no stable conservative batch found."
  echo "Next phase: patch the Adreno 720 dispatch profile/workgroup count before any further GPU stress test."
fi
echo "No network mining was started and no wallet secret was touched."
echo "==> Summary: $SUMMARY"
echo "==> Full log: $LOG"
exit 0
