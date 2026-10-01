#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

OUT="/sdcard/Download/TBM-NEWTERMUX-HANDOFF"
REPORT="/sdcard/Download/NEWTERMUX-CUTOVER-HANDOFF.txt"
mkdir -p "$OUT"

fail() { printf 'FALLO: %s\n' "$*" | tee -a "$REPORT"; exit 1; }

{
  echo "=== NEWTERMUX CUTOVER - PREPARAR HANDOFF DESDE TERMUX PLAY ==="
  date
  echo
} | tee "$REPORT"

[ "${PREFIX:-}" = "/data/data/com.termux/files/usr" ] || fail "Este script debe ejecutarse dentro de Termux Play (com.termux)."
command -v tbm >/dev/null 2>&1 || fail "No se encontro tbm instalado."

VER="$(tbm version 2>&1 | head -n1)"
printf 'TBM=%s\n' "$VER" | tee -a "$REPORT"
case "$VER" in *1.06*) ;; *) fail "Se requiere TBM 1.06." ;; esac

TBM_SRC="$(command -v tbm)"
cp -f "$TBM_SRC" "$OUT/tbm"
chmod 700 "$OUT/tbm"
sha256sum "$OUT/tbm" > "$OUT/tbm.sha256"

KNOWN_BUNDLE="/storage/emulated/0/Download-Folders/tbm_backups/tbm_migration_20260923-001255.tar"
KNOWN_SHA256="e4f77f060e0d39bb63a240821741c4ee3e4c21c8e345ed6ba68efe31582f91b3"

if [ -f "$KNOWN_BUNDLE" ]; then
  BUNDLE="$KNOWN_BUNDLE"
  echo "Usando bundle fisicamente validado: $BUNDLE" | tee -a "$REPORT"
  GOT_KNOWN="$(sha256sum "$BUNDLE" | awk '{print $1}')"
  [ "$GOT_KNOWN" = "$KNOWN_SHA256" ] || fail "El bundle conocido existe pero su SHA-256 cambio: $GOT_KNOWN"
else
  CANDIDATES="$(
    find \
      /storage/emulated/0/Download-Folders/tbm_backups \
      /sdcard/Download-Folders/tbm_backups \
      /sdcard/Download/tbm_backups \
      /sdcard/Download/TBM-MIGRATION/handoff \
      -maxdepth 2 -type f \( -name 'tbm_migration_*.tar' -o -name 'tbm_migration_*.tbmprot' \) \
      -printf '%T@ %p\n' 2>/dev/null | sort -nr || true
  )"
  [ -n "$CANDIDATES" ] || fail "No encontre un backup de migracion TBM en Download-Folders/tbm_backups, Download/tbm_backups ni TBM-MIGRATION/handoff."
  BUNDLE="$(printf '%s\n' "$CANDIDATES" | head -n1 | cut -d' ' -f2-)"
fi

[ -f "$BUNDLE" ] || fail "El candidato seleccionado no existe: $BUNDLE"

case "$BUNDLE" in
  *.tbmprot) fail "El backup seleccionado esta protegido (.tbmprot). Este flujo no pedira contrasenas automaticamente." ;;
esac

SIDECAR="$BUNDLE.sha256"
[ -f "$SIDECAR" ] || fail "Falta sidecar SHA-256: $SIDECAR"

echo "[1/3] Verificando sidecar..." | tee -a "$REPORT"
(
  cd "$(dirname "$BUNDLE")"
  sha256sum -c "$(basename "$SIDECAR")"
) 2>&1 | tee -a "$REPORT"

echo "[2/3] Verificando bundle con TBM..." | tee -a "$REPORT"
tbm verify "$BUNDLE" 2>&1 | tee -a "$REPORT"

echo "[3/3] Preparando handoff compartido..." | tee -a "$REPORT"
printf '%s\n' "$BUNDLE" > "$OUT/BUNDLE_PATH.txt"
printf '%s\n' "$(basename "$BUNDLE")" > "$OUT/BUNDLE_NAME.txt"
cp -f "$SIDECAR" "$OUT/$(basename "$SIDECAR")"

{
  echo "TBM_BINARY_SHA256=$(sha256sum "$OUT/tbm" | awk '{print $1}')"
  echo "BUNDLE=$BUNDLE"
  echo "BUNDLE_SHA256=$(sha256sum "$BUNDLE" | awk '{print $1}')"
  echo "HANDOFF=$OUT"
  echo
  echo "NEWTERMUX_HANDOFF=PASS"
} | tee -a "$REPORT"
