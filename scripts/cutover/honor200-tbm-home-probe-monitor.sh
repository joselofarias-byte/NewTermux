#!/data/data/com.termux/files/usr/bin/bash
set -u

INTERVAL="${1:-10}"

show_once() {
  local rows count line pid probe base rest stamp files err size last errtail

  rows="$(ps -A -o PID=,ELAPSED=,PCPU=,PMEM=,ARGS= 2>/dev/null |     grep -E '[t]ar --preserve-permissions -xzvf .*home\.tar\.gz.* -C .*/tbm-home-probe-' || true)"

  if [ -z "$rows" ]; then
    echo "SONDAS_ACTIVAS=0"
    echo
    echo "Ultima sonda registrada:"
    files="$(ls -1t /sdcard/Download/TBM-HOME-TAR-PROBE-*.files.txt 2>/dev/null | head -n1 || true)"
    if [ -n "$files" ]; then
      stamp="${files##*/TBM-HOME-TAR-PROBE-}"
      stamp="${stamp%.files.txt}"
      err="/sdcard/Download/TBM-HOME-TAR-PROBE-$stamp.stderr.txt"
      echo "FILES=$files"
      echo "ULTIMA_RUTA=$(tail -n1 "$files" 2>/dev/null || true)"
      echo "STDERR:"
      tail -n 12 "$err" 2>/dev/null || true
    else
      echo "NO_ENCONTRADA"
    fi
    return 1
  fi

  count="$(printf '%s\n' "$rows" | grep -c . || true)"
  echo "SONDAS_ACTIVAS=$count"
  if [ "$count" -gt 1 ]; then
    echo "ADVERTENCIA: hay $count extracciones de diagnostico simultaneas."
  fi
  echo

  printf '%s\n' "$rows" | while IFS= read -r line; do
    [ -n "$line" ] || continue
    pid="$(printf '%s\n' "$line" | awk '{print $1}')"
    probe="$(printf '%s\n' "$line" | sed -n 's#.* -C \([^ ]*tbm-home-probe-[^ ]*\).*#\1#p')"
    base="${probe##*/}"
    rest="${base#tbm-home-probe-}"
    stamp="${rest%-*}"
    files="/sdcard/Download/TBM-HOME-TAR-PROBE-$stamp.files.txt"
    err="/sdcard/Download/TBM-HOME-TAR-PROBE-$stamp.stderr.txt"
    size="$(du -sh "$probe" 2>/dev/null | awk '{print $1}')"
    last="$(tail -n1 "$files" 2>/dev/null || true)"
    errtail="$(tail -n3 "$err" 2>/dev/null || true)"

    echo "PID=$pid"
    echo "ESTADO=$(printf '%s\n' "$line" | sed 's/^[[:space:]]*//')"
    echo "PROBE=$probe"
    echo "TAMANO=${size:-0}"
    echo "ULTIMA_RUTA=${last:-SIN_DATO}"
    if [ -n "$errtail" ]; then
      echo "STDERR_ULTIMO:"
      printf '%s\n' "$errtail"
    else
      echo "STDERR_ULTIMO=(vacio)"
    fi
    echo
  done
  return 0
}

echo "=== TBM HOME TAR - MONITOR EN VIVO ==="
echo "Intervalo: ${INTERVAL}s"
echo "Ctrl+C solo detiene ESTE monitor; no toca las extracciones."
echo

while true; do
  echo "----- $(date '+%H:%M:%S') -----"
  if ! show_once; then
    exit 0
  fi
  sleep "$INTERVAL"
done
