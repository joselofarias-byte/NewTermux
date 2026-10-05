#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ROUTER="$DIR/9router-go.sh"
HARNESS="$DIR/ai-harnesses.sh"

say() {
  printf '\n==> %s\n' "$*"
}

die() {
  printf '\nERROR: %s\n' "$*" >&2
  exit 1
}

test -f "$ROUTER" || die "Falta $ROUTER"
test -f "$HARNESS" || die "Falta $HARNESS"

# 1) Router: install only when missing, then always ensure it is running.
if ! command -v 9router-go >/dev/null 2>&1; then
  say "9router-go no está instalado; preparando"
  bash "$ROUTER" install
fi

# The first managed start creates the recommended profile with RTK enabled.
bash "$ROUTER" start

# 2) Debian: create only when it does not already exist.
if ! command -v proot-distro >/dev/null 2>&1 ||
   ! proot-distro login debian -- /bin/true >/dev/null 2>&1; then
  say "Debian PRoot no está listo; preparando"
  bash "$HARNESS" prepare
fi

# 3) OpenCode: install only when absent.
if ! proot-distro login debian -- sh -lc '
  export PATH="$HOME/.local/bin:$HOME/.opencode/bin:$PATH"
  command -v opencode >/dev/null 2>&1 || command -v opencode2 >/dev/null 2>&1
'; then
  say "OpenCode no está instalado; instalando"
  bash "$HARNESS" opencode
fi

# 4) 9router provider: repair/create config only when missing.
if ! proot-distro login debian -- sh -lc '
  cfg="$HOME/.config/opencode/opencode.json"
  [ -s "$cfg" ] &&
  grep -Fq "127.0.0.1:20128/v1" "$cfg" &&
  grep -Fq "9router/free-best" "$cfg"
'; then
  say "Conectando OpenCode con 9router-go"
  bash "$HARNESS" opencode-router
fi

say "Abriendo OpenCode · 9router/free-best"

exec proot-distro login debian -- sh -lc '
  export PATH="$HOME/.local/bin:$HOME/.opencode/bin:$PATH"
  if command -v opencode >/dev/null 2>&1; then
    exec opencode
  elif command -v opencode2 >/dev/null 2>&1; then
    exec opencode2
  else
    echo "ERROR: OpenCode no quedó disponible después de la preparación." >&2
    exit 1
  fi
'
