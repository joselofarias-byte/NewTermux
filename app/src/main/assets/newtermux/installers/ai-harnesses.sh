#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ACTION="${1:-}"
PREPARED=0

DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DOCTOR="$DIR/environment-doctor.sh"
ROUTER_INSTALLER="$DIR/9router-go.sh"
STATE_DIR="$HOME/.config/9router-go"
ROUTER_ENV="$STATE_DIR/router.env"
API_KEY_FILE="$STATE_DIR/opencode-api-key"

if [ -z "${ROUTER_PORT:-}" ] && [ -r "$ROUTER_ENV" ]; then
  while IFS='=' read -r key value; do
    case "$key" in
      ROUTER_PORT) ROUTER_PORT="$value" ;;
    esac
  done <"$ROUTER_ENV"
fi
ROUTER_PORT="${ROUTER_PORT:-20130}"
ROUTER_BASE="http://127.0.0.1:$ROUTER_PORT"

say() {
  printf '\n==> %s\n' "$*"
}

die() {
  printf '\nERROR: %s\n' "$*" >&2
  exit 1
}

router_health() {
  local status=""
  if ! exec 3<>"/dev/tcp/127.0.0.1/$ROUTER_PORT" 2>/dev/null; then
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

ensure_host() {
  command -v pkg >/dev/null 2>&1 || die "Este instalador debe ejecutarse dentro de NewTermux."

  if [ -x "$DOCTOR" ]; then
    bash "$DOCTOR" repair
  fi

  local missing=()
  command -v proot-distro >/dev/null 2>&1 || missing+=(proot-distro)
  command -v jq >/dev/null 2>&1 || missing+=(jq)
  command -v curl >/dev/null 2>&1 || missing+=(curl)
  command -v sha256sum >/dev/null 2>&1 || missing+=(coreutils)

  if [ "${#missing[@]}" -gt 0 ]; then
    say "Instalando dependencias NewTermux: ${missing[*]}"
    pkg install -y "${missing[@]}"
  fi

  if ! proot-distro login debian -- /bin/true >/dev/null 2>&1; then
    say "Debian no está instalado; instalándolo"
    proot-distro install debian
  fi
}

guest_script() {
  proot-distro login --shared-tmp debian -- /bin/bash -s
}

guest_script_with_router_key() {
  local api_key="$1"
  proot-distro login --shared-tmp debian -- \
    /usr/bin/env \
      NINE_ROUTER_API_KEY="$api_key" \
      NINE_ROUTER_PORT="$ROUTER_PORT" \
      /bin/bash -s
}

ensure_router_running() {
  if router_health; then
    return 0
  fi
  [ -x "$ROUTER_INSTALLER" ] || die "No está disponible el instalador integrado de 9router-go."
  say "9router-go no está activo; iniciándolo"
  ROUTER_PORT="$ROUTER_PORT" bash "$ROUTER_INSTALLER" start
  router_health || die "9router-go no respondió en $ROUTER_BASE"
}

derive_cli_token() {
  local secret_file="$HOME/.9router/auth/cli-secret"
  local machine_file="$HOME/.9router/machine-id"
  local machine secret

  [ -s "$secret_file" ] || return 1
  [ -s "$machine_file" ] || return 1

  machine="$(tr -d '\r\n ' <"$machine_file")"
  secret="$(tr -d '\r\n ' <"$secret_file")"
  printf '%s' "${machine}9r-cli-auth${secret}" |
    sha256sum |
    awk '{print substr($1,1,16)}'
}

ensure_router_api_key() {
  mkdir -p "$STATE_DIR"

  if [ -s "$API_KEY_FILE" ]; then
    local existing
    existing="$(cat "$API_KEY_FILE")"
    if curl -fsS --max-time 5 \
      -H "Authorization: Bearer $existing" \
      "$ROUTER_BASE/v1/models" >/dev/null 2>&1; then
      printf '%s\n' "$existing"
      return 0
    fi
  fi

  local cli_token keys api_key created
  cli_token="$(derive_cli_token || true)"
  [ -n "$cli_token" ] || die "9router-go no publicó todavía su token CLI local."

  keys="$(curl -fsS --max-time 8 \
    -H "x-9r-cli-token: $cli_token" \
    "$ROUTER_BASE/api/keys")"

  api_key="$(printf '%s' "$keys" | jq -r '
    [
      .[]
      | select(
          (.isActive == 1 or .isActive == true)
          and ((.name // "") == "NewTermux OpenCode" or (.name // "") == "OpenCode Launcher")
          and ((.key // "") | startswith("sk-"))
        )
    ][0].key // empty
  ')"

  if [ -z "$api_key" ]; then
    created="$(curl -fsS --max-time 8 \
      -X POST \
      -H "x-9r-cli-token: $cli_token" \
      -H 'Content-Type: application/json' \
      --data '{"name":"NewTermux OpenCode"}' \
      "$ROUTER_BASE/api/keys")"
    api_key="$(printf '%s' "$created" | jq -r '.key // empty')"
  fi

  [ -n "$api_key" ] || die "No se pudo crear una API key local para OpenCode."
  printf '%s' "$api_key" >"$API_KEY_FILE"
  chmod 600 "$API_KEY_FILE"
  printf '%s\n' "$api_key"
}

prepare_guest() {
  if [ "$PREPARED" -eq 1 ]; then
    return 0
  fi

  ensure_host
  say "Preparando Debian para coding harnesses"
  guest_script <<'GUEST'
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git gh jq nodejs npm
mkdir -p "$HOME/.local/bin" "$HOME/.opencode/bin"
touch "$HOME/.profile"
PATH_LINE='export PATH="$HOME/.local/bin:$HOME/.opencode/bin:$PATH"'
grep -Fqx "$PATH_LINE" "$HOME/.profile" || printf "\n%s\n" "$PATH_LINE" >> "$HOME/.profile"
printf "Node: "; node --version
printf "npm: "; npm --version
printf "gh: "; gh --version | head -n1
GUEST
  PREPARED=1
}

install_antigravity() {
  prepare_guest
  say "Instalando/actualizando Antigravity CLI"
  guest_script <<'GUEST'
set -euo pipefail
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
curl -fsSL https://antigravity.google/cli/install.sh -o "$tmp"
bash "$tmp"
export PATH="$HOME/.local/bin:$HOME/.opencode/bin:$PATH"
command -v agy >/dev/null
agy --version || true
GUEST
}

install_codex() {
  prepare_guest
  say "Instalando/actualizando Codex CLI"
  guest_script <<'GUEST'
set -euo pipefail
export PATH="$HOME/.local/bin:$HOME/.opencode/bin:$PATH"
npm config set prefix "$HOME/.local"
npm install -g @openai/codex@latest
command -v codex >/dev/null
codex --version
GUEST
}

install_opencode() {
  prepare_guest
  say "Instalando/actualizando OpenCode"
  guest_script <<'GUEST'
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl jq
mkdir -p "$HOME/.local/bin" "$HOME/.opencode/bin"
touch "$HOME/.profile"
PATH_LINE='export PATH="$HOME/.local/bin:$HOME/.opencode/bin:$PATH"'
grep -Fqx "$PATH_LINE" "$HOME/.profile" || printf "\n%s\n" "$PATH_LINE" >> "$HOME/.profile"

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
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
GUEST
}

configure_opencode_router() {
  ensure_host
  ensure_router_running

  local api_key
  api_key="$(ensure_router_api_key)"

  say "Configurando OpenCode para 9router-go"
  guest_script_with_router_key "$api_key" <<'GUEST'
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl jq

cfg="$HOME/.config/opencode/opencode.json"
mkdir -p "$(dirname "$cfg")"

if [ -s "$cfg" ] && ! jq -e . "$cfg" >/dev/null 2>&1; then
  backup="$cfg.bak-$(date +%Y%m%d-%H%M%S)"
  cp "$cfg" "$backup"
  echo "Configuración previa inválida preservada en: $backup"
  printf "{}\n" > "$cfg"
fi
[ -s "$cfg" ] || printf "{}\n" > "$cfg"

base="http://127.0.0.1:${NINE_ROUTER_PORT}/v1"
models_json="$(curl -fsS --max-time 12 \
  -H "Authorization: Bearer $NINE_ROUTER_API_KEY" \
  "$base/models")"

advertises_coding_auto="$(
  printf '%s' "$models_json" |
  jq -r 'any((.data // [])[]?; .id == "coding-auto")'
)"

model_map="$(
  printf '%s' "$models_json" |
  jq -c '
    [(.data // [])[]?.id | select(type=="string" and length>0)]
    | unique
    | map({key:., value:{name:(. + " via 9router")}})
    | from_entries
    + {
        "free-best":{"name":"Mejor gratuito via 9router"},
        "coding-best-free":{"name":"Mejor gratuito para programar via 9router"}
      }
  '
)"

if [ "$advertises_coding_auto" = "true" ]; then
  model_map="$(
    printf '%s' "$model_map" |
    jq -c '. + {"coding-auto":{"name":"Programación automática con continuidad via 9router"}}'
  )"
fi

preferred="$(
  printf '%s' "$model_map" |
  jq -r --arg auto "$advertises_coding_auto" '
    keys as $k |
    ((if $auto == "true" and ($k|index("coding-auto")) then "coding-auto" else empty end) //
     (if ($k|index("coding-best-free")) then "coding-best-free" else empty end) //
     (if ($k|index("free-best")) then "free-best" else empty end) //
     $k[0] // empty)
  '
)"
[ -n "$preferred" ] || {
  echo "9router-go no publicó ningún modelo utilizable." >&2
  exit 1
}

provider="$(
  jq -n -c \
    --arg base "$base" \
    --arg key "$NINE_ROUTER_API_KEY" \
    --argjson models "$model_map" \
    '{
      name:"9router-go",
      package:"@opencode/ai/providers/openai-compatible",
      settings:{baseURL:$base,apiKey:$key},
      models:$models
    }'
)"

tmp="$(mktemp)"
jq \
  --argjson provider "$provider" \
  --arg preferred "$preferred" '
    .["$schema"] = (.["$schema"] // "https://opencode.ai/config.json")
    | .providers = (.providers // {})
    | .providers["9router"] = $provider
    | if ((.provider // null) | type) == "object"
      then .provider |= del(.["9router"])
      else .
      end
    | .model = "9router/" + $preferred
  ' "$cfg" >"$tmp"

chmod 600 "$tmp"
mv "$tmp" "$cfg"

echo "OpenCode configurado: $cfg"
echo "Endpoint: $base"
echo "Modelo predeterminado: 9router/$preferred"
echo "Modelos visibles via 9router: $(printf '%s' "$model_map" | jq 'length')"
GUEST
}

show_status() {
  ensure_host
  say "Estado de coding harnesses en Debian"
  guest_script <<'GUEST'
export PATH="$HOME/.local/bin:$HOME/.opencode/bin:$PATH"
for cmd in agy codex opencode opencode2 gh; do
  if command -v "$cmd" >/dev/null 2>&1; then
    printf "%-12s " "$cmd"
    "$cmd" --version 2>/dev/null | head -n1 || echo "instalado"
  else
    printf "%-12s no instalado\n" "$cmd"
  fi
done

cfg="$HOME/.config/opencode/opencode.json"
if [ -s "$cfg" ] && command -v jq >/dev/null 2>&1; then
  printf "OpenCode model: "
  jq -r '.model // "sin configurar"' "$cfg"
fi
GUEST

  if router_health; then
    say "9router-go: ACTIVO en $ROUTER_BASE"
  else
    say "9router-go: detenido o sin respuesta en $ROUTER_BASE"
  fi
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
    configure_opencode_router
    ;;
  opencode-router)
    configure_opencode_router
    ;;
  all)
    install_antigravity
    install_codex
    install_opencode
    configure_opencode_router
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

say "Listo. La autenticación de proveedores sigue siendo interactiva; NewTermux sólo guarda la API key local de su propio 9router-go."
