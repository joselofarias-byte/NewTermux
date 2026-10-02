#!/data/data/com.termux/files/usr/bin/bash
# Read-only audit of the three large TBM survivors:
# current direct-cutover stage, tbm-home-resume, and cutover-source.
set -u
export LC_ALL=C

TBM="$HOME/.tbm"
MARKER="$TBM/direct-cutover-current-stage.txt"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$HOME/storage/downloads/NT-TBM-FINAL-3-AUDIT-$STAMP.txt"
WORK="$HOME/.cache/nt-tbm-final3-$STAMP"
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

manifest_tree() {
  local root="$1" out="$2"
  if [ ! -d "$root" ]; then : > "$out"; return; fi
  (cd "$root" && find . -xdev -type f -printf '%P\t%s\n' 2>/dev/null | sort) > "$out"
}

compare_manifests() {
  local a="$1" b="$2" label="$3"
  local common="$WORK/common.txt" onlya="$WORK/onlya.txt" onlyb="$WORK/onlyb.txt"
  comm -12 "$a" "$b" > "$common"
  comm -23 "$a" "$b" > "$onlya"
  comm -13 "$a" "$b" > "$onlyb"
  local ca cb cc ba bb bc
  ca="$(wc -l < "$a" | tr -d ' ')"
  cb="$(wc -l < "$b" | tr -d ' ')"
  cc="$(wc -l < "$common" | tr -d ' ')"
  ba="$(awk -F '\t' '{s+=$2} END{printf "%.0f\n",s+0}' "$a")"
  bb="$(awk -F '\t' '{s+=$2} END{printf "%.0f\n",s+0}' "$b")"
  bc="$(awk -F '\t' '{s+=$2} END{printf "%.0f\n",s+0}' "$common")"
  {
    echo "COMPARE=$label"
    echo "A_FILES=$ca"
    echo "B_FILES=$cb"
    echo "COMMON_SAME_PATH_SIZE_FILES=$cc"
    echo "A_LOGICAL_BYTES=$ba"
    echo "B_LOGICAL_BYTES=$bb"
    echo "COMMON_LOGICAL_BYTES=$bc"
    if [ "$ca" -gt 0 ]; then awk -v c="$cc" -v a="$ca" 'BEGIN{printf "COMMON_FILE_PERCENT=%.2f%%\n",100*c/a}'; fi
    if [ "$ba" -gt 0 ]; then awk -v c="$bc" -v a="$ba" 'BEGIN{printf "COMMON_BYTE_PERCENT=%.2f%%\n",100*c/a}'; fi
    echo "ONLY_A_SAMPLE:"
    head -n 20 "$onlya"
    echo "ONLY_B_SAMPLE:"
    head -n 20 "$onlyb"
    echo
  } >> "$OUT"
}

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

RESUMES=()
shopt -s nullglob
for p in "$TBM"/tmp/tbm-home-resume-*; do [ -d "$p" ] && RESUMES+=("$p"); done
shopt -u nullglob

CURRENT_BYTES="$(disk_bytes "$CURRENT_STAGE")"
CUTOVER_BYTES="$(disk_bytes "$TBM/cutover-source")"
TBM_BYTES="$(disk_bytes "$TBM")"

{
  echo "===== TBM FINAL 3 AUDIT ====="
  echo "Fecha=$(date)"
  echo "Metodo=find path+size + du; SOLO LECTURA"
  echo "MarkerText=$MARKER_TEXT"
  echo "CurrentStage=${CURRENT_STAGE:-NO_RESUELTO}"
  echo "CurrentStageBytes=$CURRENT_BYTES"
  echo "CurrentStageSize=$(fmt "$CURRENT_BYTES")"
  echo "CutoverSourceBytes=$CUTOVER_BYTES"
  echo "CutoverSourceSize=$(fmt "$CUTOVER_BYTES")"
  echo "ResumeCount=${#RESUMES[@]}"
  for p in "${RESUMES[@]}"; do echo "Resume=$(disk_bytes "$p") $(fmt "$(disk_bytes "$p")") $p"; done
  echo "TBMTotalBytes=$TBM_BYTES"
  echo "TBMTotalSize=$(fmt "$TBM_BYTES")"
  echo
} | tee "$OUT"

status "Construyendo manifiesto de la etapa actual..."
manifest_tree "$CURRENT_STAGE" "$WORK/current.tsv"

if [ "${#RESUMES[@]}" -gt 0 ]; then
  i=0
  for p in "${RESUMES[@]}"; do
    i=$((i+1))
    status "Comparando resume #$i con la etapa actual..."
    manifest_tree "$p" "$WORK/resume-$i.tsv"
    compare_manifests "$WORK/current.tsv" "$WORK/resume-$i.tsv" "CURRENT_STAGE_vs_RESUME_$i"
  done
fi

status "Construyendo manifiesto del HOME vivo sin .tbm..."
(cd "$HOME" && find . -xdev \( -path './.tbm' -o -path './.tbm/*' \) -prune -o -type f -printf '%P\t%s\n' 2>/dev/null | sort) > "$WORK/live.tsv"
compare_manifests "$WORK/current.tsv" "$WORK/live.tsv" "CURRENT_STAGE_vs_LIVE_HOME"

{
  echo "===== CUTOVER-SOURCE ====="
  if [ -d "$TBM/cutover-source" ]; then
    du -x -B1 -d2 "$TBM/cutover-source" 2>/dev/null | sort -nr
    echo
    find "$TBM/cutover-source" -maxdepth 2 -type f -printf '%s\t%TY-%Tm-%Td %TH:%TM\t%p\n' 2>/dev/null | sort -nr
    echo
    if [ -f "$TBM/cutover-source/manifest.json" ]; then
      echo "----- manifest.json -----"
      sed -n '1,160p' "$TBM/cutover-source/manifest.json"
      echo "----- end manifest.json -----"
    fi
  else
    echo "(sin cutover-source)"
  fi
  echo
  echo "===== PROCESOS TBM ACTIVOS ====="
  found=0
  for proc in /proc/[0-9]*; do
    [ -r "$proc/cmdline" ] || continue
    cmd="$(tr '\0' ' ' < "$proc/cmdline" 2>/dev/null || true)"
    case "$cmd" in
      *NT-TBM-FINAL-3-AUDIT*) continue ;;
      *"/.tbm/"*|*"TBM-Recovery-Master"*|*"tbm-home-"*|*"tbm-restore-"*|*"tbm_migration"*)
        echo "PID=$(basename "$proc") CMD=$cmd"; found=1 ;;
    esac
  done
  [ "$found" -eq 1 ] || echo "(ninguno detectado)"
  echo
  echo "NOTA: este informe NO borra nada."
  echo "REPORT=$OUT"
} | tee -a "$OUT"

status "Auditoria final terminada."
