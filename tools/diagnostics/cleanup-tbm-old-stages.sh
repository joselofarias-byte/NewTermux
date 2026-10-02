#!/data/data/com.termux/files/usr/bin/bash
# Remove only obsolete TBM direct-cutover stages, never the current stage.
# Default: dry-run. Use --apply to delete non-current direct-cutover-stage-* trees.
set -u
export LC_ALL=C

MODE="${1:-}"
TBM="$HOME/.tbm"
MARKER="$TBM/direct-cutover-current-stage.txt"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$HOME/storage/downloads/NT-TBM-OLD-STAGES-$STAMP.txt"
MANIFEST="$HOME/storage/downloads/NT-TBM-OLD-STAGES-MANIFEST-$STAMP.txt"

status() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*" | tee -a "$OUT"; }
fmt() {
  awk -v b="$1" 'BEGIN {
    if (b >= 1099511627776) printf "%.2f TB", b/1099511627776;
    else if (b >= 1073741824) printf "%.2f GB", b/1073741824;
    else if (b >= 1048576) printf "%.2f MB", b/1048576;
    else if (b >= 1024) printf "%.2f KB", b/1024;
    else printf "%.0f B", b;
  }'
}
disk_bytes() { [ -e "$1" ] || { echo 0; return; }; du -s -B1 -- "$1" 2>/dev/null | awk 'NR==1 {printf "%.0f\n",$1+0}'; }

if ! du --version >/dev/null 2>&1; then
  echo "ERROR: falta GNU du/coreutils." | tee "$OUT"
  exit 2
fi
if [ ! -d "$TBM" ]; then
  echo "ERROR: no existe $TBM" | tee "$OUT"
  exit 3
fi

MARKER_TEXT=""
CURRENT_STAGE_NAME=""
CURRENT_STAGE_PATH=""
if [ -f "$MARKER" ]; then
  MARKER_TEXT="$(tr -d '\r\000' < "$MARKER" 2>/dev/null || true)"
  CURRENT_STAGE_NAME="$(printf '%s\n' "$MARKER_TEXT" | grep -oE 'direct-cutover-stage-[0-9]{8}-[0-9]{6}' | tail -n 1 || true)"
  [ -n "$CURRENT_STAGE_NAME" ] && CURRENT_STAGE_PATH="$TBM/$CURRENT_STAGE_NAME"
fi

if [ -z "$CURRENT_STAGE_PATH" ] || [ ! -d "$CURRENT_STAGE_PATH" ]; then
  echo "ERROR: no se pudo resolver una etapa actual valida desde $MARKER" | tee "$OUT"
  exit 10
fi

ACTIVE=()
SELF="$$"; PARENT="$PPID"
for proc in /proc/[0-9]*; do
  [ -r "$proc/cmdline" ] || continue
  pid="$(basename "$proc")"
  [ "$pid" = "$SELF" ] && continue
  [ "$pid" = "$PARENT" ] && continue
  cmd="$(tr '\0' ' ' < "$proc/cmdline" 2>/dev/null || true)"
  [ -n "$cmd" ] || continue
  case "$cmd" in
    *NT-TBM-OLD-STAGES*|*"tee "*NT-TBM-*) continue ;;
  esac
  case "$cmd" in
    *"/.tbm/"*|*"TBM-Recovery-Master"*|*"tbm-home-"*|*"tbm-restore-"*|*"tbm_migration"*) ACTIVE+=("PID=$pid CMD=$cmd") ;;
  esac
done

CANDIDATES=()
shopt -s nullglob
for p in "$TBM"/direct-cutover-stage-*; do
  [ -d "$p" ] || continue
  [ "$p" = "$CURRENT_STAGE_PATH" ] && continue
  CANDIDATES+=("$p")
done
shopt -u nullglob

CURRENT_BYTES="$(disk_bytes "$CURRENT_STAGE_PATH")"
TOTAL_CANDIDATE_BYTES=0
for p in "${CANDIDATES[@]}"; do
  b="$(disk_bytes "$p")"
  TOTAL_CANDIDATE_BYTES="$(awk -v a="$TOTAL_CANDIDATE_BYTES" -v b="$b" 'BEGIN{printf "%.0f\n",a+b}')"
done

{
  echo "===== TBM OLD STAGES GATE ====="
  echo "Fecha=$(date)"
  echo "Modo=$([ "$MODE" = "--apply" ] && echo APPLY || echo DRY-RUN)"
  echo "Marker=$MARKER"
  echo "MarkerText=$MARKER_TEXT"
  echo "CurrentStage=$CURRENT_STAGE_PATH"
  echo "CurrentStageBytes=$CURRENT_BYTES"
  echo "CurrentStageSize=$(fmt "$CURRENT_BYTES")"
  echo
  echo "Procesos TBM activos:"
  if [ "${#ACTIVE[@]}" -eq 0 ]; then echo "(ninguno detectado)"; else printf '%s\n' "${ACTIVE[@]}"; fi
  echo
  echo "Etapas NO actuales candidatas:"
  if [ "${#CANDIDATES[@]}" -eq 0 ]; then
    echo "(ninguna)"
  else
    for p in "${CANDIDATES[@]}"; do
      b="$(disk_bytes "$p")"
      printf '%18s  %10s  %s\n' "$b" "$(fmt "$b")" "$p"
    done
  fi
  echo "CandidateTotalBytes=$TOTAL_CANDIDATE_BYTES"
  echo "CandidateTotalSize=$(fmt "$TOTAL_CANDIDATE_BYTES")"
  echo
  echo "PRESERVADO:"
  echo "$CURRENT_STAGE_PATH"
  echo "$TBM/cutover-source"
  echo "$TBM/tmp/tbm-home-resume-*"
  echo "$TBM/rollback"
  echo "$TBM/bin"
  echo "$TBM/config"
} | tee "$OUT"

if [ "$MODE" != "--apply" ]; then
  echo "DRY_RUN=YES; no se borro nada." | tee -a "$OUT"
  exit 0
fi

if [ "${#ACTIVE[@]}" -ne 0 ]; then
  echo "APPLY_REFUSED=YES RAZON=hay procesos TBM activos" | tee -a "$OUT"
  exit 11
fi

{
  echo "===== TBM OLD STAGES MANIFEST ====="
  echo "Fecha=$(date)"
  echo "CurrentStage=$CURRENT_STAGE_PATH"
  echo "Marker=$MARKER_TEXT"
  echo "DeletedCandidates:"
  for p in "${CANDIDATES[@]}"; do printf '%s\t%s\n' "$(disk_bytes "$p")" "$p"; done
} > "$MANIFEST"

BEFORE="$(disk_bytes "$TBM")"
for p in "${CANDIDATES[@]}"; do
  case "$p" in
    "$TBM"/direct-cutover-stage-*)
      if [ "$p" = "$CURRENT_STAGE_PATH" ]; then
        echo "SAFETY_REFUSAL=current stage" | tee -a "$OUT"; exit 12
      fi
      status "Eliminando etapa obsoleta: $p"
      rm -rf -- "$p"
      ;;
    *) echo "SAFETY_REFUSAL ruta inesperada: $p" | tee -a "$OUT"; exit 13 ;;
  esac
done
AFTER="$(disk_bytes "$TBM")"
FREED="$(awk -v a="$BEFORE" -v b="$AFTER" 'BEGIN{x=a-b;if(x<0)x=0;printf "%.0f\n",x}')"

{
  echo "APPLY_DONE=YES"
  echo "FreedBytes=$FREED"
  echo "FreedSize=$(fmt "$FREED")"
  echo "TBMAfterBytes=$AFTER"
  echo "TBMAfterSize=$(fmt "$AFTER")"
  echo "MANIFEST=$MANIFEST"
  echo "REPORT=$OUT"
} | tee -a "$OUT"
