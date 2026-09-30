#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ACTION="${1:-}"
PREPARED=0

say() {
  printf '\n==> %s\n' "$*"
}

die() {
  printf '\nERROR: %s\n' "$*" >&2
  exit 1
}

ensure_host() {
  command -v pkg >/dev/null 2>&1 || die "Este instalador debe ejecutarse dentro de NewTermux."
  say "Comprobando proot-distro"
  pkg install -y proot-distro

  if ! proot-distro login debian -- /bin/true >/dev/null 2>&1; then
    say "Debian no está instalado; instalándolo"
    proot-distro install debian
  fi
}

guest() {
  proot-distro login --shared-tmp debian -- /bin/bash -lc "$1"
}

prepare_guest() {
  if [ "$PREPARED" -eq 1 ]; then
    return 0
  fi

  ensure_host
  say "Preparando Debian para coding harnesses"
  guest 'set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git jq nodejs npm
mkdir -p "$HOME/.local/bin" "$HOME/.opencode/bin"
touch "$HOME/.profile"
PATH_LINE='''export PATH="$HOME/.local/bin:$HOME/.opencode/bin:$PATH"'''
grep -Fqx "$PATH_LINE" "$HOME/.profile" || printf "\n%s\n" "$PATH_LINE" >> "$HOME/.profile"
npm config set prefix "$HOME/.local"
printf "Node: "; node --version
printf "npm: "; npm --version
'
  PREPARED=1
}

install_antigravity() {
  prepare_guest
  say "Instalando/actualizando Antigravity CLI"
  guest 'set -euo pipefail
tmp="$(mktemp)"
trap '''rm -f "$tmp"''' EXIT
curl -fsSL https://antigravity.google/cli/install.sh -o "$tmp"
bash "$tmp"
export PATH="$HOME/.local/bin:$HOME/.opencode/bin:$PATH"
command -v agy >/dev/null
agy --version || true
'
}

install_codex() {
  prepare_guest
  say "Instalando/actualizando Codex CLI"
  guest 'set -euo pipefail
export PATH="$HOME/.local/bin:$HOME/.opencode/bin:$PATH"
npm config set prefix "$HOME/.local"
npm install -g @openai/codex@latest
command -v codex >/dev/null
codex --version
'
}

install_opencode() {
  prepare_guest
  say "Instalando/actualizando OpenCode"
  guest 'set -euo pipefail
tmp="$(mktemp)"
trap '''rm -f "$tmp"''' EXIT
curl -fsSL https://opencode.ai/v2/install -o "$tmp"
bash "$tmp"
export PATH="$HOME/.local/bin:$HOME/.opencode/bin:$PATH"
if command -v opencode >/dev/null 2>&1; then
  opencode --version
elif command -v opencode2 >/dev/null 2>&1; then
  opencode2 --version
else
  echo "OpenCode terminó el instalador pero no quedó visible en PATH." >&2
  exit 1
fi
'
}

configure_opencode_router() {
  ensure_host
  if ! proot-distro login debian -- /bin/true >/dev/null 2>&1; then
    die "Debian PRoot no está instalado. Ejecutá primero Preparar Debian."
  fi

  say "Configurando OpenCode para 9router-go / free-best"
  guest 'set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
if ! command -v jq >/dev/null 2>&1; then
  apt-get update
  apt-get install -y jq
fi
cfg="$HOME/.config/opencode/opencode.json"
mkdir -p "$(dirname "$cfg")"

if [ -s "$cfg" ] && ! jq -e . "$cfg" >/dev/null 2>&1; then
  backup="$cfg.bak-$(date +%Y%m%d-%H%M%S)"
  cp "$cfg" "$backup"
  echo "Configuración previa inválida preservada en: $backup"
  printf "{}\n" > "$cfg"
fi

[ -s "$cfg" ] || printf "{}\n" > "$cfg"
tmp="$(mktemp)"
jq '''
  .["$schema"] = (.["$schema"] // "https://opencode.ai/config.json")
  | .provider = (.provider // {})
  | (.provider["9router"] // {}) as $old
  | .provider["9router"] = ($old * {
      "npm": "@ai-sdk/openai-compatible",
      "name": "9router-go Fabric",
      "options": (($old.options // {}) * {
        "baseURL": "http://127.0.0.1:20128/v1"
      }),
      "models": (($old.models // {}) * {
        "free-best": {"name":"Best currently discovered free model"},
        "free": {"name":"Discovered free and free-tier pool"}
      })
    })
  | .model = "9router/free-best"
''' "$cfg" > "$tmp"
chmod 600 "$tmp"
mv "$tmp" "$cfg"
echo "OpenCode configurado: $cfg"
echo "Modelo predeterminado: 9router/free-best"
'
}

show_status() {
  ensure_host
  say "Estado de coding harnesses en Debian"
  guest 'export PATH="$HOME/.local/bin:$HOME/.opencode/bin:$PATH"
for cmd in agy codex opencode opencode2; do
  if command -v "$cmd" >/dev/null 2>&1; then
    printf "%-12s " "$cmd"
    "$cmd" --version 2>/dev/null || echo "instalado"
  else
    printf "%-12s no instalado\n" "$cmd"
  fi
done
'
}

case "$ACTION" in
  prepare)
    prepare_guest
    say "Entorno preparado"
    ;;
  antigravity)
    install_antigravity
    ;;
  codex)
    install_codex
    ;;
  opencode)
    install_opencode
    ;;
  opencode-router)
    configure_opencode_router
    ;;
  all)
    install_antigravity
    install_codex
    install_opencode
    show_status
    ;;
  status)
    show_status
    ;;
  *)
    cat <<'EOF'
Uso:
  ai-harnesses.sh prepare
  ai-harnesses.sh antigravity
  ai-harnesses.sh codex
  ai-harnesses.sh opencode
  ai-harnesses.sh opencode-router
  ai-harnesses.sh all
  ai-harnesses.sh status
EOF
    exit 2
    ;;
esac

say "Listo. La autenticación se realiza al iniciar cada herramienta; NewTermux no incluye credenciales."
