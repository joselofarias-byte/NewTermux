#!/data/data/com.newtermux.dev/files/usr/bin/bash
set -Eeuo pipefail

EXPECTED_PREFIX="/data/data/com.newtermux.dev/files/usr"
OLD_PREFIX="/data/data/com.termux/files/usr"
OLD_HOME="/data/data/com.termux/files/home"
BUNDLE="/storage/emulated/0/Download-Folders/tbm_backups/tbm_migration_20260923-001255.tar"
BUNDLE_SHA="e4f77f060e0d39bb63a240821741c4ee3e4c21c8e345ed6ba68efe31582f91b3"
TBM_SHA="1e64da52db7f2628abd2a7224197dce15f509fbf730800f482f1aebde9a8b67e"
STAMP="$(date +%Y%m%d-%H%M%S)"
FILES_ROOT="$(dirname "$HOME")"
FRESH_BACKUP="$FILES_ROOT/newtermux-fresh-home-$STAMP"
FAILED_ROOT="$FILES_ROOT/newtermux-failed-restore-$STAMP"

die(){ printf '\nFATAL: %s\n' "$*" >&2; exit 1; }

move_home_contents() {
  local dst="$1"
  mkdir -p "$dst"
  shopt -s dotglob nullglob
  local items=("$HOME"/*)
  if [ "${#items[@]}" -gt 0 ]; then
    mv -- "${items[@]}" "$dst"/
  fi
  shopt -u dotglob nullglob
}

restore_fresh_home() {
  echo
  echo "Intentando rollback del HOME fresco..."
  mkdir -p "$FAILED_ROOT"
  shopt -s dotglob nullglob
  local current=("$HOME"/*)
  if [ "${#current[@]}" -gt 0 ]; then
    mv -- "${current[@]}" "$FAILED_ROOT"/ 2>/dev/null || true
  fi
  local original=("$FRESH_BACKUP"/*)
  if [ "${#original[@]}" -gt 0 ]; then
    mv -- "${original[@]}" "$HOME"/ 2>/dev/null || true
  fi
  shopt -u dotglob nullglob
  echo "ROLLBACK_HOME_FRESCO=INTENTADO"
  echo "RESTORE_FALLIDO_GUARDADO=$FAILED_ROOT"
}

rewrite_symlinks() {
  local count=0 link target new
  while IFS= read -r -d '' link; do
    target="$(readlink "$link" 2>/dev/null || true)"
    new=""
    case "$target" in
      "$OLD_PREFIX"/*)
        new="$PREFIX/${target#"$OLD_PREFIX"/}"
        ;;
      "$OLD_HOME"/*)
        new="$HOME/${target#"$OLD_HOME"/}"
        ;;
    esac
    if [ -n "$new" ] && [ "$new" != "$target" ]; then
      ln -sfn -- "$new" "$link"
      count=$((count+1))
    fi
  done < <(find "$HOME" -type l -print0 2>/dev/null)
  echo "$count"
}

rewrite_text_file() {
  local f="$1"
  [ -f "$f" ] || return 0
  [ -L "$f" ] && return 0
  grep -Iq . "$f" 2>/dev/null || return 0
  if grep -Fq "$OLD_PREFIX" "$f" 2>/dev/null || grep -Fq "$OLD_HOME" "$f" 2>/dev/null; then
    sed -i \
      -e "s#${OLD_PREFIX}#${PREFIX}#g" \
      -e "s#${OLD_HOME}#${HOME}#g" \
      "$f"
    return 10
  fi
  return 0
}

echo "=== NEWTERMUX - CORTE HOME HONOR 200 ==="
echo "Fecha: $(date)"
echo "HOME=$HOME"
echo "PREFIX=$PREFIX"

[ "$PREFIX" = "$EXPECTED_PREFIX" ] || die "Debe ejecutarse dentro de NewTermux com.newtermux.dev."
[ -x "$PREFIX/bin/tbm" ] || die "TBM no está instalado en el PREFIX nuevo."
[ "$(sha256sum "$PREFIX/bin/tbm" | awk '{print $1}')" = "$TBM_SHA" ] || die "TBM no es el binario final validado."
[ -r "$BUNDLE" ] || die "No se puede leer el paquete de migración."
[ "$(awk 'NF{print $1; exit}' "$BUNDLE.sha256")" = "$BUNDLE_SHA" ] || die "El sidecar SHA del paquete no coincide."

echo
echo "[1/6] Espacio y estado previo"
df -h "$HOME" /storage/emulated/0 2>/dev/null || true

echo
echo "[2/6] Guardando HOME fresco de NewTermux"
move_home_contents "$FRESH_BACKUP"
echo "HOME_FRESCO_GUARDADO=$FRESH_BACKUP"

echo
echo "[3/6] Restaurando HOME + información"
set +e
"$PREFIX/bin/tbm" restore "$BUNDLE" "$HOME" --yes --components home,info
RC=$?
set -e
if [ "$RC" -ne 0 ]; then
  echo "RESTORE_EXIT=$RC"
  restore_fresh_home
  exit "$RC"
fi
echo "RESTORE_EXIT=0"

echo
echo "[4/6] Reubicando enlaces absolutos del Termux anterior"
REWRITTEN_LINKS="$(rewrite_symlinks)"
echo "SYMLINKS_REUBICADOS=$REWRITTEN_LINKS"

LEFT_LINKS=0
while IFS= read -r -d '' link; do
  target="$(readlink "$link" 2>/dev/null || true)"
  case "$target" in
    "$OLD_PREFIX"/*|"$OLD_HOME"/*)
      echo "PENDIENTE_SYMLINK=$link -> $target"
      LEFT_LINKS=$((LEFT_LINKS+1))
      ;;
  esac
done < <(find "$HOME" -type l -print0 2>/dev/null)
[ "$LEFT_LINKS" -eq 0 ] || die "Quedaron symlinks apuntando al Termux anterior."

echo
echo "[5/6] Reubicando rutas en lanzadores/configuración personal"
TEXT_CHANGED=0
for f in "$HOME/.bashrc" "$HOME/.bash_profile" "$HOME/.profile" "$HOME/.zshrc" "$HOME/.zprofile"; do
  set +e
  rewrite_text_file "$f"
  rc=$?
  set -e
  [ "$rc" -eq 10 ] && TEXT_CHANGED=$((TEXT_CHANGED+1))
done
for d in "$HOME/bin" "$HOME/.local/bin" "$HOME/.termux"; do
  [ -d "$d" ] || continue
  while IFS= read -r -d '' f; do
    set +e
    rewrite_text_file "$f"
    rc=$?
    set -e
    [ "$rc" -eq 10 ] && TEXT_CHANGED=$((TEXT_CHANGED+1))
  done < <(find "$d" -type f -size -2M -print0 2>/dev/null)
done
echo "ARCHIVOS_TEXTO_REUBICADOS=$TEXT_CHANGED"

echo
echo "[6/6] Comprobación final"
[ -d "$HOME/migration_info" ] || die "Falta migration_info."
[ -d "$HOME/.tbm" ] || die "No se restauró .tbm."
echo "ARCHIVOS_HOME=$(find "$HOME" -type f 2>/dev/null | wc -l)"
echo "DIRECTORIOS_HOME=$(find "$HOME" -type d 2>/dev/null | wc -l)"
echo "SYMLINKS_HOME=$(find "$HOME" -type l 2>/dev/null | wc -l)"
"$PREFIX/bin/bash" --version | head -n 1
"$PREFIX/bin/sh" -c 'echo NEWTERMUX_SH_POST_RESTORE_OK'
"$PREFIX/bin/pkg" --help >/dev/null 2>&1 || die "pkg dejó de funcionar tras el restore."

echo
echo "NEWTERMUX_HOME_RESTORE_OK"
echo "HOME_FRESCO_RESPALDO=$FRESH_BACKUP"
echo "Termux Play sigue instalado y sin tocar."
