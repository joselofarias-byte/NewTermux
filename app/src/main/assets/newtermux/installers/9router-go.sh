#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ACTION="${1:-}"
REPO_URL="https://github.com/joselofarias-byte/9router-go.git"
SRC_DIR="$HOME/.newtermux/sources/9router-go"
STATE_DIR="$HOME/.config/9router-go"
PID_FILE="$STATE_DIR/newtermux.pid"
LOG_FILE="$STATE_DIR/newtermux-router.log"
SAVER_FILE="$STATE_DIR/token-saver.env"
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
  # Start/Status must not depend on curl/libcurl or package-manager state.
  # Use Bash's own TCP socket support and read only the HTTP status line.
  local status=""
  if ! exec 3<>"/dev/tcp/127.0.0.1/$PORT" 2>/dev/null; then
    return 1
  fi
  printf 'GET /health HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n' >&3
  IFS= read -r -t 3 status <&3 || {
    exec 3<&- 3>&-
    return 1
  }
  exec 3<&- 3>&-
  case "$status" in
    "HTTP/1.1 200 "*|"HTTP/1.0 200 "*) return 0 ;;
    *) return 1 ;;
  esac
}

load_token_saver() {
  RTK_ENABLED=true
  CAVEMAN_ENABLED=false
  PONYTAIL_ENABLED=false
  SAVER_PROFILE="recommended"

  if [ -r "$SAVER_FILE" ]; then
    while IFS='=' read -r key value; do
      case "$key" in
        SAVER_PROFILE) SAVER_PROFILE="$value" ;;
        RTK_ENABLED) RTK_ENABLED="$value" ;;
        CAVEMAN_ENABLED) CAVEMAN_ENABLED="$value" ;;
        PONYTAIL_ENABLED) PONYTAIL_ENABLED="$value" ;;
      esac
    done <"$SAVER_FILE"
  fi

  case "$SAVER_PROFILE" in
    recommended|medium|maximum|off) ;;
    *) SAVER_PROFILE="recommended" ;;
  esac
  case "$RTK_ENABLED" in true|false) ;; *) RTK_ENABLED=true ;; esac
  case "$CAVEMAN_ENABLED" in true|false) ;; *) CAVEMAN_ENABLED=false ;; esac
  case "$PONYTAIL_ENABLED" in true|false) ;; *) PONYTAIL_ENABLED=false ;; esac

  export RTK_ENABLED CAVEMAN_ENABLED PONYTAIL_ENABLED SAVER_PROFILE
}

write_token_saver() {
  local profile="$1"
  local rtk="$2"
  local caveman="$3"
  local ponytail="$4"

  mkdir -p "$STATE_DIR"
  cat >"$SAVER_FILE" <<EOF
# Managed by NewTermux. Safe to edit while 9router-go is stopped.
SAVER_PROFILE=$profile
RTK_ENABLED=$rtk
CAVEMAN_ENABLED=$caveman
PONYTAIL_ENABLED=$ponytail
EOF
  chmod 600 "$SAVER_FILE" 2>/dev/null || true
}

resolve_router_bin() {
  local candidate
  candidate="$(command -v 9router-go 2>/dev/null || true)"
  if [ -n "$candidate" ] && [ -x "$candidate" ]; then
    printf '%s\n' "$candidate"
    return 0
  fi
  if [ -x "$SRC_DIR/9router-go" ]; then
    printf '%s\n' "$SRC_DIR/9router-go"
    return 0
  fi
  return 1
}

managed_router_running() {
  [ -r "$PID_FILE" ] || return 1
  local pid
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  [ -n "$pid" ] && kill -0 "$pid" >/dev/null 2>&1
}

show_token_saver() {
  load_token_saver
  say "Perfil de ahorro: $SAVER_PROFILE"
  say "RTK (comprime tool_result): $RTK_ENABLED"
  say "Respuestas breves / Caveman: $CAVEMAN_ENABLED"
  say "Código mínimo / Ponytail: $PONYTAIL_ENABLED"
  say "Configuración: $SAVER_FILE"
}

apply_token_saver() {
  local profile="$1"
  case "$profile" in
    recommended)
      write_token_saver recommended true false false
      ;;
    medium)
      write_token_saver medium true true false
      ;;
    maximum)
      write_token_saver maximum true true true
      ;;
    off)
      write_token_saver off false false false
      ;;
    *)
      die "Perfil de ahorro desconocido: $profile"
      ;;
  esac

  show_token_saver

  if managed_router_running; then
    say "Reiniciando 9router-go administrado para aplicar el perfil"
    stop_router
    start_router
  elif health; then
    say "9router-go está activo pero no fue iniciado por NewTermux."
    say "Perfil guardado; se aplicará en el próximo inicio administrado."
  else
    say "Perfil guardado; se aplicará al iniciar 9router-go."
  fi
}

install_router() {
  need_termux
  say "Instalando dependencias de compilación"
  pkg install -y git golang make

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
  local router_bin
  router_bin="$(resolve_router_bin || true)"
  [ -n "$router_bin" ] || die "9router-go no está instalado. Elegí 'Instalar / actualizar 9router-go' primero."
  mkdir -p "$STATE_DIR" "$DATA_DIR"
  if health; then
    say "9router-go ya está activo en http://127.0.0.1:$PORT"
    return 0
  fi

  load_token_saver
  if [ ! -r "$SAVER_FILE" ]; then
    write_token_saver recommended true false false
    load_token_saver
  fi
  say "Iniciando 9router-go · ahorro=$SAVER_PROFILE"
  say "Binario: $router_bin"
  nohup env PORT="$PORT" DATA_DIR="$DATA_DIR" \
    RTK_ENABLED="$RTK_ENABLED" \
    CAVEMAN_ENABLED="$CAVEMAN_ENABLED" \
    PONYTAIL_ENABLED="$PONYTAIL_ENABLED" \
    "$router_bin" >"$LOG_FILE" 2>&1 &
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
  local router_bin
  router_bin="$(resolve_router_bin || true)"
  if [ -n "$router_bin" ]; then
    say "Binario: $router_bin"
    "$router_bin" version 2>/dev/null || true
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
  show_token_saver
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
  saver-status) show_token_saver ;;
  saver-safe) apply_token_saver recommended ;;
  saver-medium) apply_token_saver medium ;;
  saver-max) apply_token_saver maximum ;;
  saver-off) apply_token_saver off ;;
  *) die "Uso: 9router-go.sh install|start|status|stop|saver-status|saver-safe|saver-medium|saver-max|saver-off" ;;
esac
