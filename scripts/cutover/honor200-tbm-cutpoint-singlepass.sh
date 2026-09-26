#!/data/data/com.termux/files/usr/bin/bash
set -u

ARCHIVE="$HOME/.tbm/tmp/tbm-migration-restore-staging-1059385793/home.tar.gz"
TARGET="./.tbm/native-acceptance-bin/tbm"
OUT="/sdcard/Download/TBM-CUTPOINT-SINGLEPASS-$(date +%Y%m%d-%H%M%S).txt"

echo "=== TBM - PUNTO DE CORTE, UNA SOLA PASADA ===" | tee "$OUT"
echo "Fecha: $(date)" | tee -a "$OUT"
echo "ARCHIVE=$ARCHIVE" | tee -a "$OUT"
echo "TARGET=$TARGET" | tee -a "$OUT"
echo | tee -a "$OUT"

[ -f "$ARCHIVE" ] || {
  echo "FALLO: no existe $ARCHIVE" | tee -a "$OUT"
  exit 1
}

echo "[1/2] Buscando objetivo y contexto inmediato..." | tee -a "$OUT"

set +e
tar -tvzf "$ARCHIVE" 2>/dev/null | awk -v target="$TARGET" '
  BEGIN {
    before=12
    after=20
    n=0
    found=0
    left=0
  }
  {
    ring[n % before] = $0
    n++

    # En salida verbose, el nombre es el ultimo campo para rutas sin espacios.
    # Nuestro objetivo no contiene espacios.
    name=$NF

    if (!found && name == target) {
      print "--- 12 ANTERIORES ---"
      start=n-before-1
      if (start < 0) start=0
      for (i=start; i<n-1; i++) {
        idx=i % before
        if (ring[idx] != "") print ring[idx]
      }
      print "--- OBJETIVO ---"
      print $0
      print "--- 20 POSTERIORES ---"
      found=1
      left=after
      next
    }

    if (found && left > 0) {
      print $0
      left--
      if (left == 0) exit
    }
  }
  END {
    if (!found) {
      print "OBJETIVO_NO_ENCONTRADO"
    }
  }
' | tee -a "$OUT"
PIPE_RC=${PIPESTATUS[0]}
set -e

echo | tee -a "$OUT"
echo "[2/2] Resultado" | tee -a "$OUT"
echo "TAR_PIPE_EXIT=$PIPE_RC" | tee -a "$OUT"
echo "Nota: un codigo no-cero aqui puede ser normal porque awk cierra el pipe al reunir 20 entradas posteriores." | tee -a "$OUT"
echo "INFORME=$OUT" | tee -a "$OUT"
