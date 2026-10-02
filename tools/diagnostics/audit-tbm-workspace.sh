#!/data/data/com.termux/files/usr/bin/bash
# TBM workspace audit for NewTermux HOME.
# Read-only: does not delete, move, compress, or modify anything.

set -u
export LC_ALL=C

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$HOME/storage/downloads/NT-TBM-DESGLOSE-$STAMP.txt"
TBM="$HOME/.tbm"

if ! du --version >/dev/null 2>&1 || ! du -s -B1 -- "$HOME" >/dev/null 2>&1; then
  echo "ERROR: se necesita GNU du con -B1 (paquete coreutils)." | tee "$OUT"
  echo "Ejecuta: pkg install coreutils" | tee -a "$OUT"
  exit 2
fi

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

sum_glob_dirs() {
  local pattern="$1"
  local total=0 p b
  shopt -s nullglob
  for p in $pattern; do
    [ -e "$p" ] || continue
    b="$(disk_bytes "$p")"
    total="$(awk -v a="$total" -v b="$b" 'BEGIN {printf "%.0f\n",a+b}')"
  done
  shopt -u nullglob
  echo "$total"
}

if [ ! -d "$TBM" ]; then
  {
    echo "===== TBM WORKSPACE AUDIT ====="
    echo "No existe $TBM"
    echo "REPORT=$OUT"
  } | tee "$OUT"
  exit 0
fi

TBM_TOTAL="$(disk_bytes "$TBM")"
TBM_TMP="$(disk_bytes "$TBM/tmp")"
TBM_CUTOVER_SOURCE="$(disk_bytes "$TBM/cutover-source")"
TBM_BACKUPS="$(disk_bytes "$TBM/backups")"
TBM_STAGES="$(sum_glob_dirs "$TBM/direct-cutover-stage-*")"

KNOWN="$(printf '%s\n' "$TBM_TMP" "$TBM_CUTOVER_SOURCE" "$TBM_BACKUPS" "$TBM_STAGES" | awk '{s+=$1} END{printf "%.0f\n",s+0}')"
TBM_OTHER="$(awk -v a="$TBM_TOTAL" -v b="$KNOWN" 'BEGIN{x=a-b;if(x<0)x=0;printf "%.0f\n",x}')"

HOME_TOTAL="$(disk_bytes "$HOME")"
HOME_WITHOUT_TBM="$(awk -v a="$HOME_TOTAL" -v b="$TBM_TOTAL" 'BEGIN{x=a-b;if(x<0)x=0;printf "%.0f\n",x}')"

{
  echo "===== TBM WORKSPACE AUDIT ====="
  echo "Fecha=$(date)"
  echo "HOME=$HOME"
  echo "TBM=$TBM"
  echo "Metodo=GNU du -B1 + find/stat; SOLO LECTURA"
  echo

  echo "===== RESUMEN ====="
  printf '%-34s %18s %18s\n' "CATEGORIA" "BYTES" "FORMATEADO"
  printf '%-34s %18s %18s\n' "----------------------------------" "------------------" "------------------"
  printf '%-34s %18s %18s\n' ".tbm TOTAL" "$TBM_TOTAL" "$(fmt "$TBM_TOTAL")"
  printf '%-34s %18s %18s\n' ".tbm/tmp" "$TBM_TMP" "$(fmt "$TBM_TMP")"
  printf '%-34s %18s %18s\n' ".tbm/cutover-source" "$TBM_CUTOVER_SOURCE" "$(fmt "$TBM_CUTOVER_SOURCE")"
  printf '%-34s %18s %18s\n' ".tbm/direct-cutover-stage-*" "$TBM_STAGES" "$(fmt "$TBM_STAGES")"
  printf '%-34s %18s %18s\n' ".tbm/backups" "$TBM_BACKUPS" "$(fmt "$TBM_BACKUPS")"
  printf '%-34s %18s %18s\n' ".tbm/otros" "$TBM_OTHER" "$(fmt "$TBM_OTHER")"
  echo
  printf '%-34s %18s %18s\n' "HOME total bruto" "$HOME_TOTAL" "$(fmt "$HOME_TOTAL")"
  printf '%-34s %18s %18s\n' "HOME sin .tbm" "$HOME_WITHOUT_TBM" "$(fmt "$HOME_WITHOUT_TBM")"
  echo

  echo "===== .TBM NIVEL 1 ====="
  du -x -B1 -d1 "$TBM" 2>/dev/null | sort -nr
  echo

  echo "===== .TBM NIVEL 2 (TOP 80) ====="
  du -x -B1 -d2 "$TBM" 2>/dev/null | sort -nr | head -n 80
  echo

  echo "===== ARCHIVOS >= 500 MB ====="
  find "$TBM" -xdev -type f -size +500M -printf '%s\t%TY-%Tm-%Td %TH:%TM\t%p\n' 2>/dev/null | sort -nr
  echo

  echo "===== CANDIDATOS DUPLICADOS POR TAMANO >= 500 MB ====="
  # Same size is only a candidate signal; no expensive hashing is performed here.
  find "$TBM" -xdev -type f -size +500M -printf '%s\t%p\n' 2>/dev/null |
    sort -n |
    awk -F '\t' '
      prev==$1 { if (!shown) print prev_line; print; shown=1; next }
      { prev=$1; prev_line=$0; shown=0 }
    '
  echo

  echo "===== DIRECT-CUTOVER STAGES ====="
  shopt -s nullglob
  for p in "$TBM"/direct-cutover-stage-*; do
    [ -d "$p" ] || continue
    b="$(disk_bytes "$p")"
    printf '%18s  %10s  %s\n' "$b" "$(fmt "$b")" "$p"
  done
  shopt -u nullglob
  echo

  echo "===== TMP TBM ====="
  if [ -d "$TBM/tmp" ]; then
    du -x -B1 -d1 "$TBM/tmp" 2>/dev/null | sort -nr
  else
    echo "(sin .tbm/tmp)"
  fi
  echo

  echo "===== CUTOVER-SOURCE ====="
  if [ -d "$TBM/cutover-source" ]; then
    du -x -B1 -d2 "$TBM/cutover-source" 2>/dev/null | sort -nr
    echo
    find "$TBM/cutover-source" -maxdepth 2 -type f -printf '%s\t%TY-%Tm-%Td %TH:%TM\t%p\n' 2>/dev/null | sort -nr
  else
    echo "(sin .tbm/cutover-source)"
  fi
  echo

  echo "===== MARCADORES / ESTADO TBM ====="
  find "$TBM" -maxdepth 2 -type f \
    \( -iname '*stage*' -o -iname '*state*' -o -iname '*current*' -o -iname '*resume*' -o -iname '*ready*' -o -iname '*complete*' -o -iname '*lock*' \) \
    -printf '%TY-%Tm-%Td %TH:%TM:%TS\t%s\t%p\n' 2>/dev/null | sort
  echo

  echo "===== PROCESOS TBM ACTIVOS ====="
  found=0
  for proc in /proc/[0-9]*; do
    [ -r "$proc/cmdline" ] || continue
    cmd="$(tr '\0' ' ' < "$proc/cmdline" 2>/dev/null || true)"
    case "$cmd" in
      *TBM*|*tbm*|*.tbm*)
        echo "PID=$(basename "$proc") CMD=$cmd"
        found=1
        ;;
    esac
  done
  [ "$found" -eq 1 ] || echo "(ninguno detectado)"
  echo

  echo "===== INTERPRETACION AUTOMATICA ====="
  echo "- .tbm/tmp: area temporal/probes/resume; revisar antes de borrar."
  echo "- direct-cutover-stage-*: snapshots de staging; revisar cual es el vigente."
  echo "- cutover-source: fuente/archivo de migracion; home.tar.gz puede ser enorme."
  echo "- El informe NO declara nada seguro para borrar por si solo."
  echo "- No se ha modificado ningun archivo."
  echo
  echo "REPORT=$OUT"
} | tee "$OUT"

echo
echo "LISTO=$OUT"
