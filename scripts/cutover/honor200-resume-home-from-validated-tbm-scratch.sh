#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

KNOWN_MIG="$HOME/.tbm/tmp/tbm-migration-restore-staging-1059385793"
TARGET="$HOME/.tbm/direct-cutover-stage-20260926-124519"
HANDOFF="/sdcard/Download/TBM-NEWTERMUX-HANDOFF"
REPORT="/sdcard/Download/NEWTERMUX-TBM-HOME-FOREGROUND-$(date +%Y%m%d-%H%M%S).txt"
RESCUE="$HOME/.tbm/tmp/tbm-home-foreground-$(date +%Y%m%d-%H%M%S)-$$"
ARCHIVE="$KNOWN_MIG/home.tar.gz"

WAKE=0
cleanup_wake() {
  if [ "$WAKE" -eq 1 ] && command -v termux-wake-unlock >/dev/null 2>&1; then
    termux-wake-unlock >/dev/null 2>&1 || true
  fi
}
trap cleanup_wake EXIT

fail() {
  printf 'FALLO: %s\n' "$*" | tee -a "$REPORT"
  exit 1
}

{
  echo "=== NEWTERMUX - HOME DESDE SCRATCH TBM VALIDADO ==="
  date
  echo "HOME=$HOME"
  echo "TARGET=$TARGET"
  echo "ARCHIVE=$ARCHIVE"
  echo
} | tee "$REPORT"

[ "${PREFIX:-}" = "/data/data/com.termux/files/usr" ] || fail "PREFIX inesperado."
[ -d "$KNOWN_MIG" ] || fail "Falta scratch de migracion: $KNOWN_MIG"
[ -f "$ARCHIVE" ] || fail "Falta home.tar.gz."
[ -f "$KNOWN_MIG/manifest.json" ] || fail "Falta manifest.json."
grep -q 'tbm-migration-backup-v1' "$KNOWN_MIG/manifest.json" || fail "Manifest inesperado."

if ps -A -o ARGS 2>/dev/null | grep -Eq '[t]bm restore|[t]ar .*home\.tar\.gz'; then
  fail "Ya hay una restauracion/extraccion activa."
fi

mkdir -p "$TARGET"
if find "$TARGET" -mindepth 1 -print -quit 2>/dev/null | grep -q .; then
  fail "El stage final ya contiene entradas; no se sobrescribe."
fi

if command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1; then
  WAKE=1
  echo "WAKE_LOCK=ACTIVO" | tee -a "$REPORT"
else
  echo "WAKE_LOCK=NO_DISPONIBLE" | tee -a "$REPORT"
fi

mkdir -p "$RESCUE"
echo "RESCUE=$RESCUE" | tee -a "$REPORT"
echo "ARCHIVE_SIZE=$(du -sh "$ARCHIVE" 2>/dev/null | awk '{print $1}')" | tee -a "$REPORT"
echo | tee -a "$REPORT"

echo "[1/5] Extrayendo HOME en primer plano." | tee -a "$REPORT"
echo "      El archivo historico contiene arboles .tbm/rehearse* gigantes." | tee -a "$REPORT"
echo "      Se omiten, pero gzip debe recorrer igualmente sus bytes." | tee -a "$REPORT"
echo "      Cada mensaje PROGRESO_TAR confirma que sigue avanzando." | tee -a "$REPORT"
echo | tee -a "$REPORT"

set +e
tar --preserve-permissions -xzf "$ARCHIVE" -C "$RESCUE" \
  --exclude='./.tbm/rehearse*' \
  --exclude='.tbm/rehearse*' \
  --exclude='./.tbm/reports' \
  --exclude='.tbm/reports' \
  --exclude='./.tbm/reports/*' \
  --exclude='.tbm/reports/*' \
  --exclude='./.codex/tmp/arg0/*' \
  --exclude='.codex/tmp/arg0/*' \
  --exclude='*/.codex/tmp/arg0/*' \
  --checkpoint=500000 \
  "--checkpoint-action=echo=PROGRESO_TAR: home.tar.gz sigue recorriendose" \
  -- 2>&1 | tee -a "$REPORT"
TAR_RC=${PIPESTATUS[0]}
set -e

echo "TAR_EXIT=$TAR_RC" | tee -a "$REPORT"
[ "$TAR_RC" -eq 0 ] || fail "tar termino con codigo $TAR_RC; TARGET sigue intacto."

echo "[2/5] Controlando exclusiones..." | tee -a "$REPORT"
if find "$RESCUE/.tbm" -mindepth 1 \( -path "$RESCUE/.tbm/reports" -o -path "$RESCUE/.tbm/reports/*" -o -name 'rehearse*' \) -print -quit 2>/dev/null | grep -q .; then
  fail "Aparecio contenido TBM transitorio que debia quedar excluido."
fi
if find "$RESCUE" -path '*/.codex/tmp/arg0/*' -print -quit 2>/dev/null | grep -q .; then
  fail "Aparecio un shim Codex arg0 que debia quedar excluido."
fi
echo "EXCLUSIONES=OK" | tee -a "$REPORT"

echo "[3/5] Publicando HOME completo al stage aislado..." | tee -a "$REPORT"
MOVED=()
rollback() {
  local name
  for name in "${MOVED[@]}"; do
    if [ -e "$TARGET/$name" ] || [ -L "$TARGET/$name" ]; then
      mv -- "$TARGET/$name" "$RESCUE/" 2>/dev/null || true
    fi
  done
}

while IFS= read -r -d '' entry; do
  name="${entry##*/}"
  if [ -e "$TARGET/$name" ] || [ -L "$TARGET/$name" ]; then
    rollback
    fail "Colision inesperada al publicar: $TARGET/$name"
  fi
  if ! mv -- "$entry" "$TARGET/"; then
    rollback
    fail "Fallo publicando $name; se intento rollback."
  fi
  MOVED+=("$name")
done < <(find "$RESCUE" -mindepth 1 -maxdepth 1 -print0)

[ "${#MOVED[@]}" -gt 0 ] || fail "Extraccion vacia."
rmdir "$RESCUE" 2>/dev/null || true
echo "HOME_STAGE_PUBLICADO=OK" | tee -a "$REPORT"

echo "[4/5] Restaurando migration_info con TBM 1.06..." | tee -a "$REPORT"
mkdir -p "$HOME/.tbm/bin"
cp -f "$HANDOFF/tbm" "$HOME/.tbm/bin/tbm"
chmod 700 "$HOME/.tbm/bin/tbm"
(
  cd "$HANDOFF"
  sha256sum -c tbm.sha256
) 2>&1 | tee -a "$REPORT"

TBM="$HOME/.tbm/bin/tbm"
case "$("$TBM" version 2>&1 | head -n1)" in
  *1.06*) ;;
  *) fail "Binario TBM del handoff no es 1.06." ;;
esac

BUNDLE="$(cat "$HANDOFF/BUNDLE_PATH.txt")"
[ -f "$BUNDLE" ] || fail "Bundle no visible: $BUNDLE"
"$TBM" restore "$BUNDLE" "$TARGET" --yes --components info 2>&1 | tee -a "$REPORT"

[ -d "$TARGET/migration_info" ] || fail "No se creo migration_info."
[ ! -d "$TARGET/prefix_snapshot" ] || fail "SEGURIDAD: aparecio prefix_snapshot."
if find "$TARGET" -maxdepth 1 -type d -name 'proot_*' | grep -q .; then
  fail "SEGURIDAD: aparecio proot raw."
fi

echo "[5/5] Control final..." | tee -a "$REPORT"
{
  echo "TARGET=$TARGET"
  echo "COMPONENTS=home,info"
  echo "PREFIX_SNAPSHOT_RESTORED=NO"
  echo "PROOT_RAW_RESTORED=NO"
  echo "HOME_STAGE_SIZE=$(du -sh "$TARGET" 2>/dev/null | awk '{print $1}')"
  echo "NEWTERMUX_HOME_FOREGROUND_STAGE=PASS"
  echo "INFORME=$REPORT"
  echo
  echo "No se modifico el HOME real."
  echo "No borrar scratch viejos hasta revisar este PASS."
} | tee -a "$REPORT"
