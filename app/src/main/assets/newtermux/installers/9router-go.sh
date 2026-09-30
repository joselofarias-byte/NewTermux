#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ACTION="${1:-}"
REPO_URL="https://github.com/joselofarias-byte/9router-go.git"
SRC_DIR="$HOME/.newtermux/sources/9router-go"
STATE_DIR="$HOME/.config/9router-go"
PID_FILE="$STATE_DIR/newtermux.pid"
LOG_FILE="$STATE_DIR/newtermux-router.log"
PORT="${ROUTER_PORT:-20128}"
DATA_DIR="${ROUTER_DATA_DIR:-$HOME/.9router}"

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

health() {
  command -v curl >/dev/null 2>&1 || return 1
  curl -fsS --max-time 3 "http://127.0.0.1:$PORT/health" >/dev/null 2>&1
}

install_router() {
  need_termux
  say "Instalando dependencias de compilación"
  pkg install -y git golang make curl

  mkdir -p "$(dirname "$SRC_DIR")" "$STATE_DIR"
  if [ -d "$SRC_DIR/.git" ]; then
    say "Actualizando copia administrada de nuestro fork"
    git -C "$SRC_DIR" fetch --prune origin main
    git -C "$SRC_DIR" checkout -B main origin/main
  else
    say "Clonando joselofarias-byte/9router-go"
    git clone --depth 1 --branch main "$REPO_URL" "$SRC_DIR"
  fi

  say "Compilando 9router-go"
  (
    cd "$SRC_DIR"
    make build
  )

  test -x "$SRC_DIR/9router-go" || die "No se generó el binario 9router-go."
  install -m 700 "$SRC_DIR/9router-go" "$PREFIX/bin/9router-go"
  say "Instalado en $PREFIX/bin/9router-go"
  "$PREFIX/bin/9router-go" version 2>/dev/null || true
}

start_router() {
  need_termux
  pkg install -y curl
  command -v 9router-go >/dev/null 2>&1 || install_router

  mkdir -p "$STATE_DIR" "$DATA_DIR"
  if health; then
    say "9router-go ya está activo en http://127.0.0.1:$PORT"
    return 0
  fi

  say "Iniciando 9router-go"
  nohup env PORT="$PORT" DATA_DIR="$DATA_DIR" RTK_ENABLED=true     "$PREFIX/bin/9router-go" >"$LOG_FILE" 2>&1 &
  echo "$!" >"$PID_FILE"

  for _ in 1 2 3 4 5 6 7 8 9 10; do
    sleep 1
    if health; then
      say "9router-go listo en http://127.0.0.1:$PORT"
      say "Rutas virtuales gratuitas: free-best / free"
      return 0
    fi
  done

  tail -n 30 "$LOG_FILE" 2>/dev/null || true
  die "9router-go no respondió al health check."
}

status_router() {
  need_termux
  pkg install -y curl
  if command -v 9router-go >/dev/null 2>&1; then
    say "Binario: $(command -v 9router-go)"
    9router-go version 2>/dev/null || true
  else
    say "Binario: no instalado"
  fi

  if health; then
    say "Servicio: ACTIVO en http://127.0.0.1:$PORT"
  else
    say "Servicio: detenido o sin respuesta"
  fi

  if [ -r "$PID_FILE" ]; then
    say "PID administrado: $(cat "$PID_FILE" 2>/dev/null || true)"
  fi
  say "Log: $LOG_FILE"
}

stop_router() {
  if [ ! -r "$PID_FILE" ]; then
    say "No hay PID administrado por NewTermux; no se mata ningún proceso por nombre."
    exit 0
  fi

  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [ -n "$pid" ] && kill -0 "$pid" >/dev/null 2>&1; then
    say "Deteniendo 9router-go PID $pid"
    kill "$pid"
  else
    say "El PID guardado ya no está activo."
  fi
  rm -f "$PID_FILE"
}

case "$ACTION" in
  install) install_router ;;
  start) start_router ;;
  status) status_router ;;
  stop) stop_router ;;
  *) die "Uso: 9router-go.sh install|start|status|stop" ;;
esac
