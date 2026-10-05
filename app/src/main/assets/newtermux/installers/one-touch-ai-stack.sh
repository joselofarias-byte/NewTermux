#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DOCTOR="$DIR/environment-doctor.sh"
ROUTER="$DIR/9router-go.sh"
HARNESS="$DIR/ai-harnesses.sh"
TBM="$DIR/tbm-integration.sh"

step() {
  printf '\n\n========== [%s/8] %s ==========\n' "$1" "$2"
}

fail() {
  rc=$?
  printf '\n\nUN_TOQUE_FAIL exit=%s\n' "$rc" >&2
  printf 'Podés ejecutar nuevamente “Un toque”; los instaladores son re-ejecutables.\n' >&2
  exit "$rc"
}
trap fail ERR

test -f "$DOCTOR"
test -f "$ROUTER"
test -f "$HARNESS"
test -f "$TBM"

step 1 "Diagnóstico y autorreparación del entorno NewTermux"
bash "$DOCTOR" repair

step 2 "Instalar / actualizar 9router-go"
bash "$ROUTER" install

step 3 "Iniciar 9router-go"
bash "$ROUTER" start

step 4 "Preparar Debian PRoot"
bash "$HARNESS" prepare

step 5 "Instalar / actualizar Antigravity, Codex, OpenCode y GitHub CLI"
bash "$HARNESS" all

step 6 "Conectar OpenCode con 9router-go"
bash "$HARNESS" opencode-router

step 7 "TBM si ya pasó el gate de integración"
bash "$TBM" install-if-ready

step 8 "Verificación final"
bash "$ROUTER" status
bash "$HARNESS" status
bash "$TBM" status

printf '\n\nUN_TOQUE_OK\n'
printf '9router-go: http://127.0.0.1:20130\n'
printf 'OpenCode: coding-auto si el router lo publica; coding-best-free como fallback compatible\n'
printf 'TBM: instalado sólo si existe release estable con NEWTERMUX_READY=1\n'
printf 'Las autenticaciones de cada proveedor/harness siguen siendo interactivas y no se guardan en estos scripts.\n'
