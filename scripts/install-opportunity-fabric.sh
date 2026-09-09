#!/usr/bin/env bash
set -euo pipefail

APP_NAME="opportunity-fabric"
VERSION="0.2.1"
REPO="https://github.com/joselofarias-byte/NewTermux.git"
SOURCE_SUBDIR="tools/opportunity-fabric"

if [ -n "${PREFIX:-}" ] && printf '%s' "$PREFIX" | grep -q 'com.termux'; then
  BIN_DIR="$PREFIX/bin"
else
  BIN_DIR="$HOME/.local/bin"
fi

APP_ROOT="$HOME/.local/share/$APP_NAME"
SRC_DIR="$APP_ROOT/current"
BACKUP_ROOT="$APP_ROOT/backups"
TMP_ROOT="${TMPDIR:-$HOME/.cache}"
STAMP="$(date +%Y%m%d-%H%M%S)"
TMP_DIR="$TMP_ROOT/${APP_NAME}-install-$STAMP"
CANDIDATE="$APP_ROOT/.candidate-$STAMP"

cleanup() {
  rm -rf "$TMP_DIR" "$CANDIDATE" 2>/dev/null || true
}
trap cleanup EXIT

echo "==> Opportunity Fabric $VERSION — actualización automática"

if ! command -v python >/dev/null 2>&1 || ! command -v git >/dev/null 2>&1; then
  if command -v pkg >/dev/null 2>&1; then
    echo "==> Instalando dependencias faltantes..."
    pkg install -y python git
  else
    echo "ERROR: faltan Python/Git y no existe el gestor pkg." >&2
    exit 1
  fi
fi

mkdir -p "$TMP_DIR" "$APP_ROOT" "$BACKUP_ROOT" "$BIN_DIR"

ok=0
for attempt in 1 2 3; do
  rm -rf "$TMP_DIR/repo"
  if git clone --depth 1 --filter=blob:none --sparse "$REPO" "$TMP_DIR/repo"; then
    ok=1
    break
  fi
  echo "==> Reintento Git $attempt/3..."
  sleep $((attempt * 2))
done
[ "$ok" -eq 1 ] || { echo "ERROR: no se pudo obtener NewTermux desde GitHub." >&2; exit 2; }

git -C "$TMP_DIR/repo" sparse-checkout set "$SOURCE_SUBDIR"
NEW_SRC="$TMP_DIR/repo/$SOURCE_SUBDIR"
[ -f "$NEW_SRC/opportunity_fabric/__main__.py" ] || { echo "ERROR: source tree incompleto." >&2; exit 3; }

# Stage first, test before replacing a working installation.
rm -rf "$CANDIDATE"
mkdir -p "$CANDIDATE"
cp -a "$NEW_SRC"/. "$CANDIDATE"/
find "$CANDIDATE" -type d -name '__pycache__' -prune -exec rm -rf {} + 2>/dev/null || true

echo "==> Autopruebas sobre candidato..."
PYTHONPATH="$CANDIDATE${PYTHONPATH:+:$PYTHONPATH}" python -m opportunity_fabric status >/dev/null
PYTHONPATH="$CANDIDATE${PYTHONPATH:+:$PYTHONPATH}" python -m opportunity_fabric inventory >/dev/null
PYTHONPATH="$CANDIDATE${PYTHONPATH:+:$PYTHONPATH}" python -m opportunity_fabric evaluate quantus >/dev/null
PYTHONPATH="$CANDIDATE${PYTHONPATH:+:$PYTHONPATH}" python -m opportunity_fabric policies >/dev/null
PYTHONPATH="$CANDIDATE${PYTHONPATH:+:$PYTHONPATH}" python -m opportunity_fabric quantus-preflight >/dev/null

if [ -d "$SRC_DIR" ]; then
  echo "==> Respaldando instalación anterior..."
  mv "$SRC_DIR" "$BACKUP_ROOT/$STAMP"
fi
mv "$CANDIDATE" "$SRC_DIR"

cat > "$BIN_DIR/of" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
SRC_DIR="$HOME/.local/share/opportunity-fabric/current"
if [ -n "${PYTHONPATH:-}" ]; then
  export PYTHONPATH="$SRC_DIR:$PYTHONPATH"
else
  export PYTHONPATH="$SRC_DIR"
fi
exec python -m opportunity_fabric "$@"
EOF
chmod 700 "$BIN_DIR/of"

if [ "$BIN_DIR" = "$HOME/.local/bin" ]; then
  case ":${PATH:-}:" in *":$BIN_DIR:"*) ;; *) export PATH="$BIN_DIR:$PATH" ;; esac
fi

# Final installed-path smoke test.
"$BIN_DIR/of" status >/dev/null

echo
echo "OK — Opportunity Fabric $VERSION instalado y probado."
echo "==> Estado Quantus en este dispositivo:"
"$BIN_DIR/of" evaluate quantus
echo "==> Preflight Quantus ARM64/source build:"
"$BIN_DIR/of" quantus-preflight
