#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ACTION="${1:-}"
REPO="joselofarias-byte/TBM-Recovery-Master"
READY_MARKER="NEWTERMUX_READY=1"
STATE_DIR="$HOME/.config/tbm"
INSTALLED_RELEASE_FILE="$STATE_DIR/newtermux-installed-release"
READY_TAG=""

say() {
  printf '\n==> %s\n' "$*"
}

die() {
  printf '\nERROR: %s\n' "$*" >&2
  exit 1
}

need_termux() {
  command -v pkg >/dev/null 2>&1 || die "Este instalador debe ejecutarse en NewTermux."
}

ensure_tools() {
  need_termux
  pkg install -y gh coreutils
}

probe_ready_release() {
  READY_TAG=""

  if ! gh auth status -h github.com >/dev/null 2>&1; then
    return 10
  fi

  READY_TAG="$(gh api "repos/$REPO/releases/latest"     --jq 'select(.draft == false and .prerelease == false and ((.body // "") | contains("NEWTERMUX_READY=1"))) | .tag_name'     2>/dev/null || true)"

  [ -n "$READY_TAG" ]
}

print_gate() {
  ensure_tools

  if ! gh auth status -h github.com >/dev/null 2>&1; then
    say "TBM_GATE=PENDING_GITHUB_AUTH"
    printf 'El repositorio TBM es privado. Ejecutá gh auth login antes de instalarlo desde NewTermux.\n'
    return 0
  fi

  if probe_ready_release; then
    say "TBM_GATE=READY"
    printf 'Release validada: %s\n' "$READY_TAG"
    return 0
  fi

  say "TBM_GATE=PENDING_VALIDATED_RELEASE"
  printf 'Todavía no existe una release estable con el marcador %s.\n' "$READY_MARKER"
  printf 'Las prereleases de aceptación física se ignoran deliberadamente.\n'
}

install_ready() {
  local soft="${1:-0}"
  ensure_tools

  if ! gh auth status -h github.com >/dev/null 2>&1; then
    if [ "$soft" = "1" ]; then
      say "TBM_PENDING: falta gh auth para el repositorio privado; Un toque continúa."
      return 0
    fi
    die "TBM es privado y gh no está autenticado. Ejecutá gh auth login."
  fi

  if ! probe_ready_release; then
    if [ "$soft" = "1" ]; then
      say "TBM_PENDING: aún no hay release estable con $READY_MARKER; Un toque continúa."
      return 0
    fi
    die "TBM todavía no pasó el gate de integración con NewTermux."
  fi

  local tmp
  tmp="$(mktemp -d)"
  say "Descargando TBM validado: $READY_TAG"

  if ! gh release download "$READY_TAG"       --repo "$REPO"       --pattern 'tbm'       --pattern 'tbm-panel'       --pattern 'SHA256SUMS.txt'       --dir "$tmp"; then
    rm -rf "$tmp"
    die "No se pudieron descargar los assets de TBM."
  fi

  test -f "$tmp/tbm"
  test -f "$tmp/tbm-panel"
  test -f "$tmp/SHA256SUMS.txt"

  grep -E '[[:space:]](tbm|tbm-panel)$' "$tmp/SHA256SUMS.txt" > "$tmp/selected.sha256"
  if [ "$(wc -l < "$tmp/selected.sha256" | tr -d ' ')" -ne 2 ]; then
    rm -rf "$tmp"
    die "SHA256SUMS.txt no contiene exactamente tbm y tbm-panel."
  fi

  (
    cd "$tmp"
    sha256sum -c selected.sha256
  )

  install -m 700 "$tmp/tbm" "$PREFIX/bin/tbm"
  install -m 700 "$tmp/tbm-panel" "$PREFIX/bin/tbm-panel"
  mkdir -p "$STATE_DIR"
  printf '%s\n' "$READY_TAG" > "$INSTALLED_RELEASE_FILE"
  chmod 600 "$INSTALLED_RELEASE_FILE"
  rm -rf "$tmp"

  say "TBM instalado desde release validada $READY_TAG"
  "$PREFIX/bin/tbm" --version 2>/dev/null || true
}

verify_installation() {
  need_termux

  local ok=1
  if [ -x "$PREFIX/bin/tbm" ]; then
    say "tbm: $PREFIX/bin/tbm"
    "$PREFIX/bin/tbm" --version 2>/dev/null || true
  else
    printf 'tbm: no instalado\n'
    ok=0
  fi

  if [ -x "$PREFIX/bin/tbm-panel" ]; then
    say "tbm-panel: $PREFIX/bin/tbm-panel"
  else
    printf 'tbm-panel: no instalado\n'
    ok=0
  fi

  if [ -r "$INSTALLED_RELEASE_FILE" ]; then
    printf 'Release registrada: %s\n' "$(cat "$INSTALLED_RELEASE_FILE")"
  else
    printf 'Release registrada: ninguna\n'
  fi

  if [ "$ok" -eq 1 ]; then
    say "TBM_VERIFY=OK"
  else
    say "TBM_VERIFY=PENDING"
  fi
}

open_panel() {
  need_termux
  [ -x "$PREFIX/bin/tbm-panel" ] || die "tbm-panel no está instalado. Usá primero Instalar / actualizar TBM validado."
  say "Abriendo panel TBM. Backup y restore siguen siendo acciones explícitas dentro del panel."
  exec "$PREFIX/bin/tbm-panel"
}

case "$ACTION" in
  status)
    print_gate
    verify_installation
    ;;
  install)
    install_ready 0
    ;;
  install-if-ready)
    install_ready 1
    ;;
  panel)
    open_panel
    ;;
  verify)
    verify_installation
    ;;
  *)
    die "Uso: tbm-integration.sh status|install|install-if-ready|panel|verify"
    ;;
esac
