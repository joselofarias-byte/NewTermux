#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

HANDOFF="/sdcard/Download/TBM-NEWTERMUX-HANDOFF"
STAMP="$(date +%Y%m%d-%H%M%S)"
REPORT="/sdcard/Download/NEWTERMUX-TBM-HOME-FOREGROUND-$STAMP.txt"
SOURCE_ROOT="$HOME/.tbm/cutover-source"
SOURCE_HOME="$SOURCE_ROOT/home.tar.gz"
SOURCE_MANIFEST="$SOURCE_ROOT/manifest.json"
TARGET="$HOME/.tbm/direct-cutover-stage-$STAMP"
STAGE_POINTER="$HOME/.tbm/direct-cutover-current-stage.txt"
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
  echo "=== NEWTERMUX - RECUPERACION HOME TBM ROBUSTA ==="
  echo "Fecha: $(date)"
  echo "HOME=$HOME"
  echo "TARGET=$TARGET"
  echo "HANDOFF=$HANDOFF"
  echo
} | tee "$REPORT"

[ "${PREFIX:-}" = "/data/data/com.termux/files/usr" ] || fail "PREFIX inesperado."
[ -d "$HANDOFF" ] || fail "Falta handoff: $HANDOFF"
[ -f "$HANDOFF/BUNDLE_PATH.txt" ] || fail "Falta BUNDLE_PATH.txt."
[ -f "$HANDOFF/tbm" ] || fail "Falta binario TBM del handoff."
[ -f "$HANDOFF/tbm.sha256" ] || fail "Falta tbm.sha256."

BUNDLE="$(cat "$HANDOFF/BUNDLE_PATH.txt")"
[ -f "$BUNDLE" ] || fail "Bundle no visible: $BUNDLE"

if ps -A -o ARGS 2>/dev/null | grep -Eq '[t]bm restore|[t]ar .*home\.tar\.gz'; then
  fail "Ya hay una restauracion/extraccion HOME activa."
fi

if command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1; then
  WAKE=1
  echo "WAKE_LOCK=ACTIVO" | tee -a "$REPORT"
else
  echo "WAKE_LOCK=NO_DISPONIBLE" | tee -a "$REPORT"
fi

mkdir -p "$HOME/.tbm" "$SOURCE_ROOT" "$TARGET"

echo "[1/7] Validando binario TBM del handoff..." | tee -a "$REPORT"
(
  cd "$HANDOFF"
  sha256sum -c tbm.sha256
) 2>&1 | tee -a "$REPORT"
chmod 700 "$HANDOFF/tbm"
case "$("$HANDOFF/tbm" version 2>&1 | head -n1)" in
  *1.06*) echo "TBM_VERSION=1.06" | tee -a "$REPORT" ;;
  *) fail "Binario TBM del handoff no es 1.06." ;;
esac

echo "[2/7] Preparando home.tar.gz persistente..." | tee -a "$REPORT"
REUSE=0
if [ -s "$SOURCE_HOME" ] && [ -s "$SOURCE_MANIFEST" ] && grep -q 'tbm-migration-backup-v1' "$SOURCE_MANIFEST"; then
  REUSE=1
  echo "HOME_ARCHIVE=REUTILIZADO" | tee -a "$REPORT"
else
  rm -f "$SOURCE_HOME" "$SOURCE_MANIFEST"
  echo "No hay copia persistente util; se recupera desde el bundle verificado." | tee -a "$REPORT"
  echo "BUNDLE=$BUNDLE" | tee -a "$REPORT"

  MEMBER_HOME="$(tar -tf "$BUNDLE" -- 2>/dev/null | awk '
    {
      n=$0
      sub(/^\.\//,"",n)
      if (n=="home.tar.gz") { print $0; exit }
    }'
  )"
  MEMBER_MANIFEST="$(tar -tf "$BUNDLE" -- 2>/dev/null | awk '
    {
      n=$0
      sub(/^\.\//,"",n)
      if (n=="manifest.json") { print $0; exit }
    }'
  )"
  [ -n "$MEMBER_HOME" ] || fail "El bundle no contiene home.tar.gz."
  [ -n "$MEMBER_MANIFEST" ] || fail "El bundle no contiene manifest.json."

  echo "Extrayendo manifest.json..." | tee -a "$REPORT"
  tar --preserve-permissions -xf "$BUNDLE" -C "$SOURCE_ROOT" -- "$MEMBER_MANIFEST" 2>&1 | tee -a "$REPORT"

  if [ "$MEMBER_MANIFEST" != "manifest.json" ]; then
    FOUND_MANIFEST="$SOURCE_ROOT/$MEMBER_MANIFEST"
    [ -f "$FOUND_MANIFEST" ] || fail "manifest.json no aparecio tras extraer."
    mv -f "$FOUND_MANIFEST" "$SOURCE_MANIFEST"
  fi
  grep -q 'tbm-migration-backup-v1' "$SOURCE_MANIFEST" || fail "Manifest de migracion inesperado."

  echo "Extrayendo home.tar.gz desde el bundle." | tee -a "$REPORT"
  echo "Puede demorar; se mostrara un latido cada 30 segundos." | tee -a "$REPORT"

  (
    while :; do
      sleep 30
      if [ -e "$SOURCE_ROOT/$MEMBER_HOME" ]; then
        SIZE="$(du -sh "$SOURCE_ROOT/$MEMBER_HOME" 2>/dev/null | awk '{print $1}')"
      else
        SIZE="0"
      fi
      echo "PROGRESO_BUNDLE: $(date +%H:%M:%S) home.tar.gz=$SIZE" | tee -a "$REPORT"
    done
  ) &
  HEART_PID=$!

  set +e
  tar --preserve-permissions -xf "$BUNDLE" -C "$SOURCE_ROOT" -- "$MEMBER_HOME" 2>&1 | tee -a "$REPORT"
  OUTER_RC=${PIPESTATUS[0]}
  set -e
  kill "$HEART_PID" 2>/dev/null || true
  wait "$HEART_PID" 2>/dev/null || true

  [ "$OUTER_RC" -eq 0 ] || fail "No se pudo recuperar home.tar.gz desde el bundle (tar=$OUTER_RC)."

  if [ "$MEMBER_HOME" != "home.tar.gz" ]; then
    FOUND_HOME="$SOURCE_ROOT/$MEMBER_HOME"
    [ -f "$FOUND_HOME" ] || fail "home.tar.gz no aparecio tras extraer."
    mv -f "$FOUND_HOME" "$SOURCE_HOME"
  fi
  [ -s "$SOURCE_HOME" ] || fail "home.tar.gz recuperado esta vacio."
  echo "HOME_ARCHIVE=RECUPERADO" | tee -a "$REPORT"
fi

echo "HOME_ARCHIVE_SIZE=$(du -sh "$SOURCE_HOME" 2>/dev/null | awk '{print $1}')" | tee -a "$REPORT"

echo "[3/7] Extrayendo HOME a stage aislado..." | tee -a "$REPORT"
echo "Los arboles .tbm/rehearse* y .tbm/reports/** se omiten." | tee -a "$REPORT"
echo "gzip debe recorrer igualmente esos bytes historicos." | tee -a "$REPORT"

RESCUE="$HOME/.tbm/home-extract-$STAMP-$$"
mkdir -p "$RESCUE"

set +e
tar --preserve-permissions -xzf "$SOURCE_HOME" -C "$RESCUE" \
  --exclude='./.tbm/rehearse*' \
  --exclude='.tbm/rehearse*' \
  --exclude='./.tbm/reports' \
  --exclude='.tbm/reports' \
  --exclude='./.tbm/reports/*' \
  --exclude='.tbm/reports/*' \
  --exclude='./.codex/tmp/arg0/*' \
  --exclude='.codex/tmp/arg0/*' \
  --exclude='*/.codex/tmp/arg0/*' \
  --checkpoint=50000 \
  "--checkpoint-action=echo=PROGRESO_TAR: home.tar.gz sigue recorriendose" \
  -- 2>&1 | tee -a "$REPORT"
INNER_RC=${PIPESTATUS[0]}
set -e
echo "TAR_EXIT=$INNER_RC" | tee -a "$REPORT"
[ "$INNER_RC" -eq 0 ] || fail "Extraccion HOME termino con codigo $INNER_RC; no se publica nada."

echo "[4/7] Verificando exclusiones..." | tee -a "$REPORT"
if find "$RESCUE/.tbm" -mindepth 1 \( -path "$RESCUE/.tbm/reports" -o -path "$RESCUE/.tbm/reports/*" -o -name 'rehearse*' \) -print -quit 2>/dev/null | grep -q .; then
  fail "Aparecio contenido TBM transitorio excluido."
fi
if find "$RESCUE" -path '*/.codex/tmp/arg0/*' -print -quit 2>/dev/null | grep -q .; then
  fail "Aparecio un shim Codex arg0 excluido."
fi
echo "EXCLUSIONES=OK" | tee -a "$REPORT"

echo "[5/7] Publicando al stage nuevo..." | tee -a "$REPORT"
if find "$TARGET" -mindepth 1 -print -quit 2>/dev/null | grep -q .; then
  fail "El stage nuevo no esta vacio: $TARGET"
fi

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
    fail "Colision inesperada: $TARGET/$name"
  fi
  if ! mv -- "$entry" "$TARGET/"; then
    rollback
    fail "Fallo publicando $name; se intento rollback."
  fi
  MOVED+=("$name")
done < <(find "$RESCUE" -mindepth 1 -maxdepth 1 -print0)

[ "${#MOVED[@]}" -gt 0 ] || fail "La extraccion HOME quedo vacia."
rmdir "$RESCUE" 2>/dev/null || true
echo "HOME_STAGE_PUBLICADO=OK" | tee -a "$REPORT"

echo "[6/7] Restaurando solo migration_info..." | tee -a "$REPORT"
"$HANDOFF/tbm" restore "$BUNDLE" "$TARGET" --yes --components info 2>&1 | tee -a "$REPORT"

[ -d "$TARGET/migration_info" ] || fail "No se creo migration_info."
[ ! -d "$TARGET/prefix_snapshot" ] || fail "SEGURIDAD: aparecio prefix_snapshot."
if find "$TARGET" -maxdepth 1 -type d -name 'proot_*' | grep -q .; then
  fail "SEGURIDAD: aparecio proot raw."
fi

echo "[7/7] Control final..." | tee -a "$REPORT"
printf '%s\n' "$TARGET" > "$STAGE_POINTER"

{
  echo "TARGET=$TARGET"
  echo "STAGE_POINTER=$STAGE_POINTER"
  echo "COMPONENTS=home,info"
  echo "PREFIX_SNAPSHOT_RESTORED=NO"
  echo "PROOT_RAW_RESTORED=NO"
  echo "HOME_STAGE_SIZE=$(du -sh "$TARGET" 2>/dev/null | awk '{print $1}')"
  echo "HOME_ARCHIVE_SOURCE=$SOURCE_HOME"
  echo "NEWTERMUX_HOME_FOREGROUND_STAGE=PASS"
  echo "INFORME=$REPORT"
  echo
  echo "El HOME real no fue modificado."
  echo "No borrar el stage ni cutover-source hasta el merge controlado."
} | tee -a "$REPORT"
