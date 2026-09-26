#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

KNOWN_MIG="$HOME/.tbm/tmp/tbm-migration-restore-staging-1059385793"
KNOWN_PAYLOAD="$HOME/.tbm/tmp/tbm-restore-payload-staging-798187682"
TARGET="$HOME/.tbm/direct-cutover-stage-20260926-124519"
HANDOFF="/sdcard/Download/TBM-NEWTERMUX-HANDOFF"
REPORT="/sdcard/Download/NEWTERMUX-TBM-HOME-RESUME-$(date +%Y%m%d-%H%M%S).txt"
RESCUE="$HOME/.tbm/tmp/tbm-home-resume-$(date +%Y%m%d-%H%M%S)-$$"

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
  echo "=== NEWTERMUX - REANUDAR HOME DESDE SCRATCH TBM VALIDADO ==="
  date
  echo "HOME=$HOME"
  echo "TARGET=$TARGET"
  echo
} | tee "$REPORT"

[ "${PREFIX:-}" = "/data/data/com.termux/files/usr" ] || fail "PREFIX inesperado."

if ps -A -o ARGS 2>/dev/null | grep -Eq '[t]bm restore|[t]ar .*home\.tar\.gz'; then
  fail "Hay una restauracion/extraccion activa. No iniciar otra."
fi

[ -d "$KNOWN_MIG" ] || fail "Falta scratch de migracion validado: $KNOWN_MIG"
[ -f "$KNOWN_MIG/home.tar.gz" ] || fail "Falta home.tar.gz validado."
[ -f "$KNOWN_MIG/manifest.json" ] || fail "Falta manifest.json."
grep -q 'tbm-migration-backup-v1' "$KNOWN_MIG/manifest.json" || fail "Manifest de migracion inesperado."

# La existencia de este staging parcial demuestra que TBM ya completo
# preflightValidatedArchive() + el control exacto de espacio antes de crear
# el staging de payload y lanzar tar.
[ -d "$KNOWN_PAYLOAD" ] || fail "Falta evidencia del staging de payload ya validado."

mkdir -p "$TARGET"
if find "$TARGET" -mindepth 1 -print -quit 2>/dev/null | grep -q .; then
  fail "El stage final ya contiene entradas; no se sobrescribe nada."
fi

if command -v termux-wake-lock >/dev/null 2>&1; then
  if termux-wake-lock >/dev/null 2>&1; then
    WAKE=1
    echo "WAKE_LOCK=ACTIVO" | tee -a "$REPORT"
  else
    echo "WAKE_LOCK=NO_CONCEDIDO" | tee -a "$REPORT"
  fi
else
  echo "WAKE_LOCK=NO_DISPONIBLE" | tee -a "$REPORT"
fi

mkdir -p "$RESCUE"
ARCHIVE="$KNOWN_MIG/home.tar.gz"
echo "ARCHIVE=$ARCHIVE" | tee -a "$REPORT"
echo "ARCHIVE_SIZE=$(du -sh "$ARCHIVE" 2>/dev/null | awk '{print $1}')" | tee -a "$REPORT"

echo "[1/4] Reanudando extraccion desde home.tar.gz ya validado..." | tee -a "$REPORT"

tar --preserve-permissions -xzf "$ARCHIVE" -C "$RESCUE" \
  --exclude='./.tbm/rehearse*' \
  --exclude='.tbm/rehearse*' \
  --exclude='./.tbm/reports' \
  --exclude='.tbm/reports' \
  --exclude='./.codex/tmp/arg0/*' \
  --exclude='.codex/tmp/arg0/*' \
  --exclude='*/.codex/tmp/arg0/*' \
  -- &
TAR_PID=$!

while kill -0 "$TAR_PID" 2>/dev/null; do
  LINE="$(ps -A -o PID,ELAPSED,PCPU,PMEM,ARGS 2>/dev/null | awk -v p="$TAR_PID" '$1==p {print; exit}')"
  printf '... tar activo: %s\n' "${LINE:-PID=$TAR_PID}" | tee -a "$REPORT"
  sleep 30
done

set +e
wait "$TAR_PID"
TAR_RC=$?
set -e
[ "$TAR_RC" -eq 0 ] || fail "tar termino con codigo $TAR_RC; el stage final sigue intacto."

echo "EXTRACCION=OK" | tee -a "$REPORT"

echo "[2/4] Publicando staging completo en destino aislado..." | tee -a "$REPORT"
mapfile -d '' ENTRIES < <(find "$RESCUE" -mindepth 1 -maxdepth 1 -print0)
[ "${#ENTRIES[@]}" -gt 0 ] || fail "La extraccion termino pero el staging esta vacio."

MOVED=()
rollback() {
  local name
  for name in "${MOVED[@]}"; do
    if [ -e "$TARGET/$name" ] || [ -L "$TARGET/$name" ]; then
      mv -- "$TARGET/$name" "$RESCUE/" 2>/dev/null || true
    fi
  done
}

for entry in "${ENTRIES[@]}"; do
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
done

rmdir "$RESCUE" 2>/dev/null || true
echo "HOME_STAGE_PUBLICADO=OK" | tee -a "$REPORT"

echo "[3/4] Restaurando solo migration_info con TBM 1.06..." | tee -a "$REPORT"
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
  *) fail "Binario TBM de handoff no es 1.06." ;;
esac

BUNDLE="$(cat "$HANDOFF/BUNDLE_PATH.txt")"
[ -f "$BUNDLE" ] || fail "Bundle no visible: $BUNDLE"
"$TBM" restore "$BUNDLE" "$TARGET" --yes --components info 2>&1 | tee -a "$REPORT"

[ -d "$TARGET/migration_info" ] || fail "No se creo migration_info."
[ ! -d "$TARGET/prefix_snapshot" ] || fail "SEGURIDAD: aparecio prefix_snapshot."
if find "$TARGET" -maxdepth 1 -type d -name 'proot_*' | grep -q .; then
  fail "SEGURIDAD: aparecio proot raw."
fi

echo "[4/4] Control final..." | tee -a "$REPORT"
{
  echo "TARGET=$TARGET"
  echo "COMPONENTS=home,info"
  echo "PREFIX_SNAPSHOT_RESTORED=NO"
  echo "PROOT_RAW_RESTORED=NO"
  echo "HOME_STAGE_SIZE=$(du -sh "$TARGET" 2>/dev/null | awk '{print $1}')"
  echo "NEWTERMUX_HOME_RESUME_STAGE=PASS"
} | tee -a "$REPORT"

echo "INFORME=$REPORT"
echo
echo "No se modifico el HOME real. No borrar los scratch viejos hasta revisar este PASS."
