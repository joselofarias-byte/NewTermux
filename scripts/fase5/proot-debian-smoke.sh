#!/usr/bin/env bash
# Idempotent PRoot/Debian/Node/Codex/Git/gh validation for NewTermux Dev.
# Refuses Play PREFIX. Does not copy old PREFIX. No secrets.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DISTRO="${FASE5_DISTRO:-debian}"
INSTALL="${FASE5_INSTALL:-1}"
INSTALL_NODE="${FASE5_INSTALL_NODE:-0}"
INSTALL_CODEX="${FASE5_INSTALL_CODEX:-0}"

log() { printf '%s\n' "$*"; }
fail() { log "FAIL: $*"; exit 1; }
skip_hw() { log "PENDIENTE-HARDWARE: $*"; exit 2; }
pend() { log "PENDIENTE: $*"; }

is_termux_android() {
  [[ "${PREFIX:-}" == /data/data/*/files/usr ]] || return 1
  [[ "$(uname -s)" == "Linux" ]] || return 1
  [[ -f /system/bin/linker64 || -f /system/bin/linker ]] || return 1
  command -v pkg >/dev/null 2>&1 || return 1
  return 0
}

if ! is_termux_android; then
  skip_hw "this VM is not Termux-on-Android (PREFIX='${PREFIX:-}' uname=$(uname -s) pkg=$(command -v pkg || true) proot-distro=$(command -v proot-distro || true) linker=$(test -f /system/bin/linker64 && echo yes || echo no))."
fi

APP_ID="$(echo "$PREFIX" | awk -F/ '{print $4}')"
log "PREFIX=$PREFIX"
log "derived_applicationId=$APP_ID"
[[ "$APP_ID" != "com.termux" ]] || fail "Play/playcompat PREFIX; refuse to install PRoot here"
[[ "$APP_ID" == "com.newtermux.dev" ]] || fail "unexpected PREFIX package $APP_ID (want com.newtermux.dev)"

if [[ -f "$PREFIX/etc/apt/sources.list" ]]; then
  grep -qE 'packages(-cf)?\.termux\.dev' "$PREFIX/etc/apt/sources.list" \
    || fail "Termux APT is not official packages.termux.dev"
  grep -qiE 'termux-play|play-store' "$PREFIX/etc/apt/sources.list" \
    && fail "Play APT repo present"
fi

# --- Termux-side packages ---
need_pkg() {
  local p="$1"
  command -v "$p" >/dev/null 2>&1 && return 0
  [[ "$INSTALL" == "1" ]] || fail "missing $p and FASE5_INSTALL=0"
  pkg install -y "$p"
}

need_pkg proot-distro
need_pkg git
if ! command -v gh >/dev/null 2>&1; then
  if [[ "$INSTALL" == "1" ]]; then
    pkg install -y gh || pend "Termux gh package install failed; debian gh still checked later"
  else
    pend "Termux gh not installed"
  fi
fi

log "=== proot-distro list ==="
proot-distro list || true

if ! proot-distro list 2>/dev/null | grep -qE "^[[:space:]]*${DISTRO}[[:space:]]|installed.*${DISTRO}|${DISTRO}.*installed"; then
  # Fallback: login fails if missing
  if ! proot-distro login "$DISTRO" -- /bin/true >/dev/null 2>&1; then
    [[ "$INSTALL" == "1" ]] || fail "debian guest missing and FASE5_INSTALL=0"
    log "installing $DISTRO (official proot-distro; not a PREFIX copy)"
    proot-distro install "$DISTRO"
  fi
fi

log "=== start $DISTRO ==="
proot-distro login "$DISTRO" -- /bin/true || fail "proot-distro login $DISTRO"

guest() {
  proot-distro login "$DISTRO" -- /bin/bash -lc "$*"
}

# --- /bin/bash ---
guest 'test -x /bin/bash && /bin/bash -c "echo bash-ok"' | grep -q bash-ok \
  || fail "/bin/bash inside $DISTRO"

# --- loader ---
guest 'test -x /lib/ld-linux-aarch64.so.1 || test -x /lib64/ld-linux-x86-64.so.2 || test -x /lib/ld-linux-armhf.so.3 || ls /lib/ld-linux*.so* >/dev/null' \
  || fail "dynamic loader missing inside $DISTRO"

# --- execve / shebang / permissions ---
guest 'printf "%s\n" "#!/bin/bash" "echo shebang-ok" > /tmp/fase5-shebang.sh && chmod 755 /tmp/fase5-shebang.sh && /tmp/fase5-shebang.sh' \
  | grep -q shebang-ok || fail "shebang /bin/bash failed"
guest 'umask 022; touch /tmp/fase5-perm && test -w /tmp/fase5-perm && test ! -w /data/data/com.termux/files/usr' \
  || fail "guest must not write Play PREFIX; tmp must be writable"

# --- DNS / TLS / repos (no TLS weakening) ---
guest 'getent hosts deb.debian.org >/dev/null || getent ahosts deb.debian.org >/dev/null' \
  || fail "DNS lookup for deb.debian.org failed"
guest 'command -v curl >/dev/null || (apt-get update && apt-get install -y ca-certificates curl)'
guest 'curl -fsSI --max-time 30 https://deb.debian.org >/dev/null' \
  || fail "TLS to deb.debian.org failed (do not add --insecure)"
guest 'grep -RqsE "debian.org|deb.debian.org" /etc/apt/sources.list /etc/apt/sources.list.d 2>/dev/null' \
  || fail "Debian APT sources missing debian.org"

# --- HOME / PATH / TMPDIR ---
guest 'echo "HOME=$HOME"; echo "PATH=$PATH"; echo "TMPDIR=${TMPDIR:-}"; test -n "$HOME"; test -d "$HOME"; test -n "$PATH"; case ":$PATH:" in *:/bin:*|*:/usr/bin:*) ;; *) exit 1 ;; esac'
log "env HOME/PATH/TMPDIR: ok"

# --- persistencia ---
MARKER=".fase5-newtermux-persist"
guest "echo persist-ok > \$HOME/$MARKER"
guest "test \"\$(cat \$HOME/$MARKER)\" = persist-ok" || fail "HOME persistence across login failed"

# --- señales ---
guest 'bash -c "sleep 30 & p=\$!; kill -TERM \$p; wait \$p; e=\$?; test \$e -eq 143 -o \$e -eq 137 -o \$e -eq 1 -o \$e -eq 143"' \
  || fail "SIGTERM not delivered inside guest"

# --- Git ---
guest 'command -v git >/dev/null || (apt-get update && apt-get install -y git)'
guest 'git --version'
guest 'd=$(mktemp -d); cd "$d"; git init; git -c user.email=fase5@local -c user.name=fase5 commit --allow-empty -m fase5; git rev-parse HEAD >/dev/null'

# --- GitHub CLI (guest or Termux host) ---
if guest 'command -v gh >/dev/null'; then
  guest 'gh --version'
else
  if command -v gh >/dev/null 2>&1; then
    gh --version
    log "gh present on Termux host (not inside debian)"
  else
    pend "gh not installed in debian or Termux (pkg install gh)"
  fi
fi
if command -v gh >/dev/null 2>&1; then
  if gh auth status >/dev/null 2>&1; then
    log "gh auth: already configured (not printed)"
  else
    pend "gh auth not configured — do not paste tokens into this script"
  fi
fi

# --- Node.js ---
if ! guest 'command -v node >/dev/null'; then
  if [[ "$INSTALL_NODE" == "1" ]]; then
    guest 'apt-get update && apt-get install -y nodejs'
  else
    pend "node not installed; re-run with FASE5_INSTALL_NODE=1 to apt-get install nodejs"
  fi
fi
if guest 'command -v node >/dev/null'; then
  guest 'node -e "console.log(\"node-ok\", process.version)"'
fi

# --- Codex CLI (no secrets, no default install) ---
if guest 'command -v codex >/dev/null'; then
  guest 'codex --version || true'
else
  if [[ "$INSTALL_CODEX" == "1" ]]; then
    fail "refusing automatic Codex install (needs registry + credentials). Install manually inside debian after node, then re-run."
  fi
  pend "codex CLI not installed — install manually in debian; do not store API keys in this repo"
fi

# --- almacenamiento compartido (Termux host) ---
SHARED="${HOME:-}/storage/shared"
if [[ -d "$SHARED" ]]; then
  echo fase5-storage >"$SHARED/.fase5-newtermux-storage"
  test -f "$SHARED/.fase5-newtermux-storage" || fail "cannot write ~/storage/shared"
  rm -f "$SHARED/.fase5-newtermux-storage"
  log "shared storage: ok"
else
  pend "termux-setup-storage not done (~/storage/shared missing). Do not copy Play PREFIX."
fi

# Isolation from Play PREFIX
if [[ -d /data/data/com.termux/files/usr ]]; then
  if [[ -w /data/data/com.termux/files/usr ]]; then
    fail "this process can write Play PREFIX — stop"
  fi
  log "Play PREFIX exists but is not writable from coexist (expected)"
fi

log "PASS Fase 5 Termux PRoot/Debian smoke (hardware). Codex/gh-auth/storage may still be PENDIENTE above."
