#!/data/data/com.termux/files/usr/bin/bash
# Remove TBM home-resume trees only when every resume file is represented in the current stage
# with the same relative path and size. Default is dry-run; use --apply to delete.
set -u
export LC_ALL=C

MODE="${1:-}"
TBM="$HOME/.tbm"
MARKER="$TBM/direct-cutover-current-stage.txt"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$HOME/storage/downloads/NT-TBM-RESUME-CLEANUP-$STAMP.txt"
MANIFEST="$HOME/storage/downloads/NT-TBM-RESUME-CLEANUP-MANIFEST-$STAMP.txt"
WORK="$HOME/.cache/nt-tbm-resume-gate-$STAMP"
mkdir -p "$WORK"
trap 'rm -rf -- "$WORK"' EXIT

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
manifest_tree() { local root="$1" out="$2"; (cd "$root" && find . -xdev -type f -printf '%P\t%s\n' 2>/dev/null | sort) > "$out"; }

if ! du --version >/dev/null 2>&1; then echo "ERROR: falta GNU coreutils" | tee "$OUT"; exit 2; fi
if [ ! -d "$TBM" ]; then echo "ERROR: no existe $TBM" | tee "$OUT"; exit 3; fi

MARKER_TEXT=""
CURRENT_STAGE_NAME=""
CURRENT_STAGE=""
if [ -f "$MARKER" ]; then
  MARKER_TEXT="$(tr -d '\r\000' < "$MARKER" 2>/dev/null || true)"
  CURRENT_STAGE_NAME="$(printf '%s\n' "$MARKER_TEXT" | grep -oE 'direct-cutover-stage-[0-9]{8}-[0-9]{6}' | tail -n 1 || true)"
  [ -n "$CURRENT_STAGE_NAME" ] && CURRENT_STAGE="$TBM/$CURRENT_STAGE_NAME"
fi
if [ -z "$CURRENT_STAGE" ] || [ ! -d "$CURRENT_STAGE" ]; then
  echo "ERROR: no se pudo resolver current stage valido" | tee "$OUT"; exit 10
fi

RESUMES=()
shopt -s nullglob
for p in "$TBM"/tmp/tbm-home-resume-*; do [ -d "$p" ] && RESUMES+=("$p"); done
shopt -u nullglob
if [ "${#RESUMES[@]}" -eq 0 ]; then echo "No hay tbm-home-resume-* para limpiar." | tee "$OUT"; exit 0; fi

ACTIVE=()
SELF="$$"; PARENT="$PPID"
for proc in /proc/[0-9]*; do
  [ -r "$proc/cmdline" ] || continue
  pid="$(basename "$proc")"
  [ "$pid" = "$SELF" ] && continue
  [ "$pid" = "$PARENT" ] && continue
  cmd="$(tr '\0' ' ' < "$proc/cmdline" 2>/dev/null || true)"
  [ -n "$cmd" ] || continue
  case "$cmd" in *NT-TBM-RESUME-CLEANUP*|*"tee "*NT-TBM-*) continue ;; esac
  case "$cmd" in
    *"/.tbm/"*|*"TBM-Recovery-Master"*|*"tbm-home-"*|*"tbm-restore-"*|*"tbm_migration"*) ACTIVE+=("PID=$pid CMD=$cmd") ;;
  esac
done

status "Construyendo manifiesto de current stage..."
manifest_tree "$CURRENT_STAGE" "$WORK/current.tsv"
CURRENT_FILES="$(wc -l < "$WORK/current.tsv" | tr -d ' ')"

TOTAL_BYTES=0
SAFE=1
: > "$WORK/resume-summary.tsv"
i=0
for p in "${RESUMES[@]}"; do
  i=$((i+1))
  status "Verificando resume #$i: $p"
  manifest_tree "$p" "$WORK/resume-$i.tsv"
  RESUME_FILES="$(wc -l < "$WORK/resume-$i.tsv" | tr -d ' ')"
  ONLY_RESUME="$WORK/only-resume-$i.tsv"
  comm -23 "$WORK/resume-$i.tsv" "$WORK/current.tsv" > "$ONLY_RESUME"
  UNIQUE_COUNT="$(wc -l < "$ONLY_RESUME" | tr -d ' ')"
  BYTES="$(disk_bytes "$p")"
  TOTAL_BYTES="$(awk -v a="$TOTAL_BYTES" -v b="$BYTES" 'BEGIN{printf "%.0f\n",a+b}')"
  [ "$UNIQUE_COUNT" -eq 0 ] || SAFE=0
  printf '%s\t%s\t%s\t%s\t%s\n' "$BYTES" "$RESUME_FILES" "$UNIQUE_COUNT" "$p" "$(fmt "$BYTES")" >> "$WORK/resume-summary.tsv"
done

{
  echo "===== TBM RESUME CLEANUP GATE ====="
  echo "Fecha=$(date)"
  echo "Modo=$([ "$MODE" = "--apply" ] && echo APPLY || echo DRY-RUN)"
  echo "CurrentStage=$CURRENT_STAGE"
  echo "CurrentStageFiles=$CURRENT_FILES"
  echo "ActiveProcesses=${#ACTIVE[@]}"
  if [ "${#ACTIVE[@]}" -gt 0 ]; then printf '%s\n' "${ACTIVE[@]}"; fi
  echo
  echo "BYTES  FILES  UNIQUE_VS_STAGE  PATH  SIZE"
  cat "$WORK/resume-summary.tsv"
  echo
  echo "ResumeTotalBytes=$TOTAL_BYTES"
  echo "ResumeTotalSize=$(fmt "$TOTAL_BYTES")"
  echo "AllResumeFilesCoveredByCurrentStage=$([ "$SAFE" -eq 1 ] && echo YES || echo NO)"
} | tee "$OUT"

if [ "$MODE" != "--apply" ]; then
  echo "DRY_RUN=YES; no se borro nada." | tee -a "$OUT"
  exit 0
fi
if [ "${#ACTIVE[@]}" -ne 0 ]; then echo "APPLY_REFUSED=YES RAZON=hay procesos TBM activos" | tee -a "$OUT"; exit 11; fi
if [ "$SAFE" -ne 1 ]; then echo "APPLY_REFUSED=YES RAZON=resume contiene archivos unicos" | tee -a "$OUT"; exit 12; fi

{
  echo "===== TBM RESUME CLEANUP MANIFEST ====="
  echo "Fecha=$(date)"
  echo "CurrentStage=$CURRENT_STAGE"
  echo "Marker=$MARKER_TEXT"
  echo "DeletedResumes:"
  cat "$WORK/resume-summary.tsv"
} > "$MANIFEST"

BEFORE="$(disk_bytes "$TBM")"
for p in "${RESUMES[@]}"; do
  case "$p" in
    "$TBM"/tmp/tbm-home-resume-*) status "Eliminando resume redundante: $p"; rm -rf -- "$p" ;;
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
