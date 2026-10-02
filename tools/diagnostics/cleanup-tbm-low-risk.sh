#!/data/data/com.termux/files/usr/bin/bash
# Conservative TBM cleanup gate.
# Default mode is read-only. Use --apply to remove ONLY low-risk temporary probe/smoke/payload trees.
# It NEVER removes:
#   - ~/.tbm/cutover-source
#   - ~/.tbm/direct-cutover-stage-*
#   - ~/.tbm/tmp/tbm-home-resume-*
#   - rollback/bin/config/metadata

set -u
export LC_ALL=C

MODE="${1:-}"
TBM="$HOME/.tbm"
TMP="$TBM/tmp"
MARKER="$TBM/direct-cutover-current-stage.txt"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$HOME/storage/downloads/NT-TBM-CLEANUP-GATE-$STAMP.txt"
MANIFEST="$HOME/storage/downloads/NT-TBM-CLEANUP-MANIFEST-$STAMP.txt"

fmt() {
  awk -v b="$1" 'BEGIN {
    if (b >= 1099511627776) printf "%.2f TB", b/1099511627776;
    else if (b >= 1073741824) printf "%.2f GB", b/1073741824;
    else if (b >= 1048576) printf "%.2f MB", b/1048576;
    else if (b >= 1024) printf "%.2f KB", b/1024;
    else printf "%.0f B", b;
  }'
}

disk_bytes() {
  [ -e "$1" ] || { echo 0; return; }
  du -s -B1 -- "$1" 2>/dev/null | awk 'NR==1 {printf "%.0f\n",$1+0}'
}

sum_bytes() {
  local total=0 p b
  for p in "$@"; do
    [ -e "$p" ] || continue
    b="$(disk_bytes "$p")"
    total="$(awk -v a="$total" -v b="$b" 'BEGIN{printf "%.0f\n",a+b}')"
  done
  echo "$total"
}

if ! du --version >/dev/null 2>&1 || ! du -s -B1 -- "$HOME" >/dev/null 2>&1; then
  echo "ERROR: falta GNU du/coreutils." | tee "$OUT"
  echo "Ejecuta: pkg install coreutils" | tee -a "$OUT"
  exit 2
fi

if [ ! -d "$TBM" ]; then
  echo "ERROR: no existe $TBM" | tee "$OUT"
  exit 3
fi

# Resolve current stage from the marker without assuming its exact text format.
MARKER_TEXT=""
CURRENT_STAGE_NAME=""
CURRENT_STAGE_PATH=""
if [ -f "$MARKER" ]; then
  MARKER_TEXT="$(tr -d '\r\000' < "$MARKER" 2>/dev/null || true)"
  CURRENT_STAGE_NAME="$(printf '%s\n' "$MARKER_TEXT" | grep -oE 'direct-cutover-stage-[0-9]{8}-[0-9]{6}' | tail -n 1 || true)"
  if [ -n "$CURRENT_STAGE_NAME" ]; then
    CURRENT_STAGE_PATH="$TBM/$CURRENT_STAGE_NAME"
  fi
fi

# Detect a live TBM workload, excluding this cleanup gate and trivial readers.
ACTIVE_TBM=()
SELF="$$"
PARENT="$PPID"
for proc in /proc/[0-9]*; do
  [ -r "$proc/cmdline" ] || continue
  pid="$(basename "$proc")"
  [ "$pid" = "$SELF" ] && continue
  [ "$pid" = "$PARENT" ] && continue
  cmd="$(tr '\0' ' ' < "$proc/cmdline" 2>/dev/null || true)"
  [ -n "$cmd" ] || continue
  case "$cmd" in
    *NT-TBM-CLEANUP-GATE*|*NT-TBM-AUDIT*|*"tee "*NT-TBM-*|*"grep "*)
      continue
      ;;
  esac
  case "$cmd" in
    *"/.tbm/"*|*"TBM-Recovery-Master"*|*"tbm-home-"*|*"tbm-restore-"*|*"tbm_migration"*)
      ACTIVE_TBM+=("PID=$pid CMD=$cmd")
      ;;
  esac
done

# Low-risk candidates only. Keep resume and all direct-cutover snapshots.
CANDIDATES=()
shopt -s nullglob
for p in "$TMP"/tbm-home-probe-*; do [ -e "$p" ] && CANDIDATES+=("$p"); done
for p in "$TMP"/tbm-panel-smoke-*; do [ -e "$p" ] && CANDIDATES+=("$p"); done
for p in "$TMP"/tbm-restore-payload-staging-*; do [ -e "$p" ] && CANDIDATES+=("$p"); done
shopt -u nullglob

CANDIDATE_BYTES="$(sum_bytes "${CANDIDATES[@]}")"
TBM_BEFORE="$(disk_bytes "$TBM")"
HOME_BEFORE="$(disk_bytes "$HOME")"

{
  echo "===== TBM CLEANUP GATE ====="
  echo "Fecha=$(date)"
  echo "Modo=$([ "$MODE" = "--apply" ] && echo APPLY || echo DRY-RUN)"
  echo "TBM=$TBM"
  echo
  echo "===== MARCADOR DE ETAPA ====="
  echo "MARKER=$MARKER"
  if [ -f "$MARKER" ]; then
    echo "MARKER_TEXT_BEGIN"
    printf '%s\n' "$MARKER_TEXT"
    echo "MARKER_TEXT_END"
  else
    echo "MARKER_AUSENTE=1"
  fi
  echo "CURRENT_STAGE_NAME=${CURRENT_STAGE_NAME:-NO_RESUELTO}"
  echo "CURRENT_STAGE_PATH=${CURRENT_STAGE_PATH:-NO_RESUELTO}"
  if [ -n "$CURRENT_STAGE_PATH" ] && [ -d "$CURRENT_STAGE_PATH" ]; then
    echo "CURRENT_STAGE_EXISTS=YES"
    echo "CURRENT_STAGE_BYTES=$(disk_bytes "$CURRENT_STAGE_PATH")"
    echo "CURRENT_STAGE_SIZE=$(fmt "$(disk_bytes "$CURRENT_STAGE_PATH")")"
  else
    echo "CURRENT_STAGE_EXISTS=NO"
  fi
  echo
  echo "===== PROCESOS TBM ACTIVOS ====="
  if [ "${#ACTIVE_TBM[@]}" -eq 0 ]; then
    echo "(ninguno detectado)"
  else
    printf '%s\n' "${ACTIVE_TBM[@]}"
  fi
  echo
  echo "===== CANDIDATOS CONSERVADORES ====="
  if [ "${#CANDIDATES[@]}" -eq 0 ]; then
    echo "(ninguno)"
  else
    for p in "${CANDIDATES[@]}"; do
      b="$(disk_bytes "$p")"
      printf '%18s  %10s  %s\n' "$b" "$(fmt "$b")" "$p"
    done
  fi
  echo
  echo "CANDIDATE_TOTAL_BYTES=$CANDIDATE_BYTES"
  echo "CANDIDATE_TOTAL_SIZE=$(fmt "$CANDIDATE_BYTES")"
  echo "TBM_BEFORE_BYTES=$TBM_BEFORE"
  echo "TBM_BEFORE_SIZE=$(fmt "$TBM_BEFORE")"
  echo "HOME_BEFORE_BYTES=$HOME_BEFORE"
  echo "HOME_BEFORE_SIZE=$(fmt "$HOME_BEFORE")"
  echo
  echo "===== PRESERVADO EXPLICITAMENTE ====="
  echo "$TBM/cutover-source"
  echo "$TBM/direct-cutover-stage-*"
  echo "$TMP/tbm-home-resume-*"
  echo "$TBM/rollback"
  echo "$TBM/bin"
  echo "$TBM/config"
  echo "$MARKER"
} | tee "$OUT"

if [ "$MODE" != "--apply" ]; then
  {
    echo
    echo "DRY_RUN=YES"
    echo "No se borro nada."
    echo "Para aplicar esta poda conservadora, vuelve a ejecutar este mismo script con --apply."
    echo "REPORT=$OUT"
  } | tee -a "$OUT"
  exit 0
fi

# Apply gate: require a valid current stage and zero active TBM workloads.
if [ -z "$CURRENT_STAGE_NAME" ] || [ -z "$CURRENT_STAGE_PATH" ] || [ ! -d "$CURRENT_STAGE_PATH" ]; then
  {
    echo
    echo "APPLY_REFUSED=YES"
    echo "RAZON=no se pudo resolver una etapa actual valida desde $MARKER"
    echo "No se borro nada."
    echo "REPORT=$OUT"
  } | tee -a "$OUT"
  exit 10
fi

if [ "${#ACTIVE_TBM[@]}" -ne 0 ]; then
  {
    echo
    echo "APPLY_REFUSED=YES"
    echo "RAZON=hay procesos TBM activos"
    echo "No se borro nada."
    echo "REPORT=$OUT"
  } | tee -a "$OUT"
  exit 11
fi

# Write exact pre-delete manifest.
{
  echo "===== TBM CLEANUP MANIFEST ====="
  echo "Fecha=$(date)"
  echo "Current stage=$CURRENT_STAGE_PATH"
  echo "Marker:"
  printf '%s\n' "$MARKER_TEXT"
  echo
  echo "Deleted candidates:"
  for p in "${CANDIDATES[@]}"; do
    b="$(disk_bytes "$p")"
    printf '%s\t%s\n' "$b" "$p"
  done
} > "$MANIFEST"

for p in "${CANDIDATES[@]}"; do
  case "$p" in
    "$TMP"/tbm-home-probe-*|"$TMP"/tbm-panel-smoke-*|"$TMP"/tbm-restore-payload-staging-*)
      rm -rf -- "$p"
      ;;
    *)
      echo "SAFETY_REFUSAL unexpected candidate: $p" | tee -a "$OUT"
      exit 12
      ;;
  esac
done

TBM_AFTER="$(disk_bytes "$TBM")"
HOME_AFTER="$(disk_bytes "$HOME")"
FREED="$(awk -v a="$TBM_BEFORE" -v b="$TBM_AFTER" 'BEGIN{x=a-b;if(x<0)x=0;printf "%.0f\n",x}')"

{
  echo
  echo "===== RESULTADO ====="
  echo "APPLY_DONE=YES"
  echo "FREED_BYTES=$FREED"
  echo "FREED_SIZE=$(fmt "$FREED")"
  echo "TBM_AFTER_BYTES=$TBM_AFTER"
  echo "TBM_AFTER_SIZE=$(fmt "$TBM_AFTER")"
  echo "HOME_AFTER_BYTES=$HOME_AFTER"
  echo "HOME_AFTER_SIZE=$(fmt "$HOME_AFTER")"
  echo "MANIFEST=$MANIFEST"
  echo "REPORT=$OUT"
} | tee -a "$OUT"

echo
echo "LISTO=$OUT"
