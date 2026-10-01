#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

EXPECTED_PREFIX="/data/data/com.newtermux.dev/files/usr"
HANDOFF="/sdcard/Download/TBM-NEWTERMUX-HANDOFF"
STAMP="$(date +%Y%m%d-%H%M%S)"
STAGE="$HOME/.tbm/cutover-stage-$STAMP"
BIN_DIR="$HOME/.tbm/bin"
REPORT="/sdcard/Download/NEWTERMUX-CUTOVER-STAGE-$STAMP.txt"

fail() { printf 'FALLO: %s\n' "$*" | tee -a "$REPORT"; exit 1; }

{
  echo "=== NEWTERMUX CUTOVER - STAGING HOME+INFO ==="
  date
  echo "PREFIX=${PREFIX:-}"
  echo "HOME=${HOME:-}"
  echo
} | tee "$REPORT"

[ "${PREFIX:-}" = "$EXPECTED_PREFIX" ] || fail "Este script solo corre dentro de NewTermux Dev (com.newtermux.dev)."

VALID_REPORT="$(
  ls -1t /sdcard/Download/NEWTERMUX-PR23-HONOR200-*.txt 2>/dev/null | head -n1 || true
)"
[ -n "$VALID_REPORT" ] || fail "No encuentro el informe fisico NEWTERMUX-PR23-HONOR200-*.txt en Descargas."
grep -q '^NEWTERMUX_PR23_PHYSICAL=PASS$' "$VALID_REPORT" || fail "La validacion fisica de NewTermux no dio PASS."

[ -f "$HANDOFF/tbm" ] || fail "Falta $HANDOFF/tbm. Ejecuta primero el preparador desde Termux Play."
[ -f "$HANDOFF/tbm.sha256" ] || fail "Falta tbm.sha256."
[ -f "$HANDOFF/BUNDLE_PATH.txt" ] || fail "Falta BUNDLE_PATH.txt."

mkdir -p "$BIN_DIR" "$STAGE"
cp -f "$HANDOFF/tbm" "$BIN_DIR/tbm"
chmod 700 "$BIN_DIR/tbm"
(
  cd "$HANDOFF"
  sha256sum -c tbm.sha256
) 2>&1 | tee -a "$REPORT"

TBM="$BIN_DIR/tbm"
VER="$("$TBM" version 2>&1 | head -n1)"
printf 'TBM=%s\n' "$VER" | tee -a "$REPORT"
case "$VER" in *1.06*) ;; *) fail "El binario del handoff no es TBM 1.06." ;; esac

BUNDLE="$(cat "$HANDOFF/BUNDLE_PATH.txt")"
[ -f "$BUNDLE" ] || fail "El bundle no es visible desde NewTermux: $BUNDLE"

case "$BUNDLE" in
  *.tbmprot) fail "Bundle protegido: este staging no solicita contrasena." ;;
esac

echo "[1/4] Verificando bundle..." | tee -a "$REPORT"
"$TBM" verify "$BUNDLE" 2>&1 | tee -a "$REPORT"

echo "[2/4] Restaurando SOLO home,info a staging..." | tee -a "$REPORT"
"$TBM" restore "$BUNDLE" "$STAGE" --yes --components home,info 2>&1 | tee -a "$REPORT"

[ -d "$STAGE/migration_info" ] || fail "No se creo migration_info en staging."
if [ -d "$STAGE/prefix_snapshot" ]; then fail "SEGURIDAD: aparecio prefix_snapshot aunque no fue seleccionado."; fi
if find "$STAGE" -maxdepth 1 -type d -name 'proot_*' | grep -q .; then
  fail "SEGURIDAD: aparecio un proot raw aunque no fue seleccionado."
fi

echo "[3/4] Buscando referencias al PREFIX viejo..." | tee -a "$REPORT"
REFS="$STAGE/OLD_PREFIX_REFERENCES.txt"
(
  grep -RIl --binary-files=without-match \
    --exclude='OLD_PREFIX_REFERENCES.txt' \
    --exclude-dir='.git' \
    '/data/data/com\.termux/files' "$STAGE" 2>/dev/null || true
) | sort -u > "$REFS"
REF_COUNT="$(wc -l < "$REFS" | tr -d ' ')"

echo "[4/4] Resumen..." | tee -a "$REPORT"
{
  echo "STAGE=$STAGE"
  echo "OLD_PREFIX_REFERENCES=$REF_COUNT"
  echo "PREFIX_SNAPSHOT_RESTORED=NO"
  echo "PROOT_RAW_RESTORED=NO"
  echo "COMPONENTS=home,info"
  if [ "$REF_COUNT" -eq 0 ]; then
    echo "MERGE_HOME=READY_FOR_REVIEW"
  else
    echo "MERGE_HOME=BLOCKED_OLD_PREFIX_REFERENCES"
    echo "REFERENCIAS=$REFS"
  fi
  echo "NEWTERMUX_HOME_STAGE=PASS"
} | tee -a "$REPORT"

echo "INFORME=$REPORT"
