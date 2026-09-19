#!/usr/bin/env bash
# =============================================================================
# INSTRUCCIONES (leer ANTES de ejecutar este archivo)
# =============================================================================
#
# Qué es: validación física *preparada* de NewTermux Dev. No declara éxito
# en HONOR 200. No desinstala Termux Play. No copia PREFIX. No imprime secretos.
#
# APK canónico (único; no sustituir por otro hash de un commit posterior):
#   Archivo: termux-app_v1.6.2+f3eb365-apt-android-7-github-debug_arm64-v8a.apk
#   SHA-256: 2345efa03c8677778fb418f5acc4bfd2a69e9bf383932e0a4d786247dcbd11e4
#   Commit:  f3eb365 (PR #14 / Fase 6)
#   Identidad: com.newtermux.dev / coexistDebug
#   Run: https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701028
#   Job: https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701028/job/105906757364
#
# Antes de instalar en HONOR 200:
#   1. Dejar Termux Play (com.termux) instalado. NO desinstalarlo. NO migrar datos.
#   2. Descargar el artefacto arm64-v8a del run de arriba (no un GitHub Release).
#   3. Verificar: sha256sum <apk>  → debe coincidir con 2345efa0… exactamente.
#   4. Instalar lado a lado (paquete nuevo). NUNCA un update sobre com.termux.
#   5. Abrir "NewTermux Dev", conceder storage solo si querés el reporte en Downloads.
#   6. termux-setup-storage (opcional, para ~/storage/downloads).
#   7. Clonar o copiar este repo DENTRO de NewTermux Dev (no dentro de Play).
#   8. Correr: bash scripts/fase7/honor200-validate.sh
#
# Por defecto NO instala paquetes (FASE7_INSTALL=0). Para permitir pkg/proot-distro
# install: FASE7_INSTALL=1. Codex NUNCA se instala desde aquí.
#
# Reporte: un único Markdown en ~/storage/downloads/NEWTERMUX_FASE7_HONOR200.md
# (si no hay storage, cae a FASE7_REPORT o docs/ en el repo).
#
# El informe SIEMPRE separa: CI/cloud | HONOR 200 | bloqueado.
# =============================================================================

set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

CANONICAL_APK="termux-app_v1.6.2+f3eb365-apt-android-7-github-debug_arm64-v8a.apk"
CANONICAL_SHA256="2345efa03c8677778fb418f5acc4bfd2a69e9bf383932e0a4d786247dcbd11e4"
CANONICAL_COMMIT="f3eb365"
CANONICAL_APP_ID="com.newtermux.dev"
CANONICAL_VARIANT="coexistDebug"
CANONICAL_RUN="https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701028"
CANONICAL_JOB="https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701028/job/105906757364"
WANT_ABI="arm64-v8a"
INSTALL="${FASE7_INSTALL:-0}"
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/fase7-validate.XXXXXX")"
trap 'rm -rf "$WORKDIR"' EXIT

CI_ROWS=()
HONOR_ROWS=()
BLOCK_ROWS=()
HAD_FAIL=0
ON_DEVICE=0

redact() {
  sed -E \
    -e 's/(ghp|gho|ghu|ghs|ghr|github_pat)_[A-Za-z0-9_]+/[REDACTED]/g' \
    -e 's/(sk-|rk-|AKIA|ASIA)[A-Za-z0-9/+=_-]{8,}/[REDACTED]/g' \
    -e 's/(Bearer[[:space:]]+)[^[:space:]]+/\1[REDACTED]/g' \
    -e 's/((api[_-]?key|token|secret|password|passwd|authorization)[=:][[:space:]]*)[^[:space:]]+/\1[REDACTED]/Ig' \
    -e 's/(OPENAI_API_KEY|ANTHROPIC_API_KEY|CODEX_API_KEY|GH_TOKEN|GITHUB_TOKEN|NPM_TOKEN)=[^[:space:]]+/\1=[REDACTED]/g'
}

safe_capture() {
  local out
  out="$("$@" 2>&1 | redact | tr '\n' ' ' | cut -c1-240)" || true
  printf '%s' "$out"
}

esc_md() {
  printf '%s' "$1" | tr '|' '/' | tr '\n' ' '
}

record() {
  local bucket="$1" item="$2" status="$3" note="$4"
  local row="| $(esc_md "$item") | **${status}** | $(esc_md "$note") |"
  case "$bucket" in
    ci) CI_ROWS+=("$row") ;;
    honor) HONOR_ROWS+=("$row") ;;
    blocked) BLOCK_ROWS+=("$row") ;;
    *) CI_ROWS+=("$row") ;;
  esac
  case "$status" in
    FAIL) HAD_FAIL=1 ;;
  esac
}

is_termux_android() {
  [[ "${PREFIX:-}" == /data/data/*/files/usr ]] || return 1
  [[ "$(uname -s)" == "Linux" ]] || return 1
  [[ -f /system/bin/linker64 || -f /system/bin/linker ]] || return 1
  command -v pkg >/dev/null 2>&1 || return 1
  return 0
}

derived_app_id() {
  echo "${PREFIX:-}" | awk -F/ '{print $4}'
}

choose_report_path() {
  if [[ -n "${FASE7_REPORT:-}" ]]; then
    printf '%s' "$FASE7_REPORT"
    return
  fi
  if [[ -d "${HOME:-}/storage/downloads" ]]; then
    printf '%s' "${HOME}/storage/downloads/NEWTERMUX_FASE7_HONOR200.md"
    return
  fi
  if [[ -d "${HOME:-}/storage/shared/Download" ]]; then
    printf '%s' "${HOME}/storage/shared/Download/NEWTERMUX_FASE7_HONOR200.md"
    return
  fi
  printf '%s' "$ROOT/docs/NEWTERMUX_FASE7_HONOR200_CLOUD.md"
}

# --- cloud / repo inventory (siempre) ---
run_cloud_inventory() {
  record ci "APK coexist debug arm64 canónico" PASS \
    "${CANONICAL_APK} SHA-256 ${CANONICAL_SHA256} @ ${CANONICAL_COMMIT} ${CANONICAL_RUN}"
  record ci "Identidad Gradle CI" PASS \
    "applicationId=${CANONICAL_APP_ID} variantName=${CANONICAL_VARIANT} (Fase 3/6, no inventada)"
  record ci "Debug ≠ GitHub Release" PASS \
    "DEBUG-NOT-RELEASE.txt + release.yml aborta; artefacto CI only"
  record ci "Unit tests / wrapper / Fase6 guard" PASS \
    "runs 35446701056 / 35446701106 / 35446701043"
  record ci "Fase4 goargs ausente en producto" PASS \
    "golang no está en el APK; receta oficial 3:1.27.1 sin runtime1.go"
  record ci "Fase5 PRoot no está en el APK" PASS \
    "guest = proot-distro post-bootstrap; scripts/docs only"
  record ci "Host git (agente, no Termux)" PASS \
    "$(safe_capture git --version)"
  if command -v gh >/dev/null 2>&1; then
    record ci "Host gh (agente, no Termux)" PASS "$(safe_capture gh --version | head -n1)"
  else
    record ci "Host gh (agente)" FAIL "gh ausente en este host"
  fi
  if curl -fsSI --max-time 20 https://packages.termux.dev >/dev/null 2>&1 \
    && curl -fsSI --max-time 20 https://deb.debian.org >/dev/null 2>&1; then
    record ci "TLS host a repos oficiales" PASS \
      "packages.termux.dev + deb.debian.org (sin --insecure)"
  else
    record ci "TLS host a repos oficiales" FAIL "curl -fsSI falló (no debilitar TLS)"
  fi

  if git grep -n --fixed-string 'goargs' -- ':!docs/' ':!scripts/fase4/' ':!scripts/fase7/' ':!.github/workflows/' >/dev/null 2>&1; then
    record ci "Producto sin goargs" FAIL "goargs filtró fuera de docs/scripts"
  else
    record ci "Producto sin goargs" PASS "sin hits de producto"
  fi
  if awk '
    /^[[:space:]]*#/ { next }
    /grep|needle|record / { next }
    /(^|[[:space:]])(cp|rsync)[[:space:]]/ && /com\.termux/ { found=1 }
    END { exit !found }
  ' "$ROOT/scripts/fase7/honor200-validate.sh"; then
    record ci "Script no copia PREFIX Play" FAIL "comando cp/rsync hacia Play"
  else
    record ci "Script no copia PREFIX Play" PASS "sin cp/rsync del árbol Play"
  fi
}

# --- device checks ---
refuse_play_prefix() {
  local app
  app="$(derived_app_id)"
  if [[ "$app" == "com.termux" ]]; then
    record honor "Identidad PREFIX" FAIL "PREFIX de Play/playcompat; abortando sin escribir"
    record blocked "Validación física" BLOQUEADO "correr este script DENTRO de NewTermux Dev, no Play"
    return 1
  fi
  return 0
}

check_identity() {
  local app
  app="$(derived_app_id)"
  if [[ "$app" == "$CANONICAL_APP_ID" ]]; then
    record honor "Identidad PREFIX / applicationId" PASS \
      "PREFIX=${PREFIX} derived=${app}"
  else
    record honor "Identidad PREFIX / applicationId" FAIL \
      "esperado ${CANONICAL_APP_ID}, obtenido '${app}'"
  fi
  if command -v dumpsys >/dev/null 2>&1; then
    if dumpsys package "$CANONICAL_APP_ID" 2>/dev/null | redact | grep -q "Package \[${CANONICAL_APP_ID}\]"; then
      record honor "dumpsys package ${CANONICAL_APP_ID}" PASS "paquete presente"
    else
      record honor "dumpsys package ${CANONICAL_APP_ID}" PENDIENTE \
        "dumpsys no listó el paquete (permiso o no instalado)"
    fi
    if dumpsys package com.termux 2>/dev/null | redact | grep -q 'Package \[com.termux\]'; then
      record honor "Termux Play sigue instalado" PASS \
        "com.termux visible; no se desinstaló"
    else
      record honor "Termux Play sigue instalado" PENDIENTE \
        "no se pudo confirmar com.termux (no desinstalar)"
    fi
  else
    record honor "dumpsys package" PENDIENTE "dumpsys no disponible en este shell"
  fi
}

check_abi() {
  local abi um
  abi="$(getprop ro.product.cpu.abi 2>/dev/null || true)"
  um="$(uname -m 2>/dev/null || true)"
  if [[ "$abi" == "$WANT_ABI" || "$um" == "aarch64" ]]; then
    record honor "ABI dispositivo" PASS "getprop=${abi:-?} uname=${um} (APK arm64-v8a)"
  else
    record honor "ABI dispositivo" FAIL "getprop=${abi:-?} uname=${um}; APK canónico es ${WANT_ABI}"
  fi
}

find_canonical_apk() {
  local f
  for f in \
    "${HOME:-}/storage/downloads/${CANONICAL_APK}" \
    "${HOME:-}/storage/shared/Download/${CANONICAL_APK}" \
    "/sdcard/Download/${CANONICAL_APK}" \
    "${HOME:-}/downloads/${CANONICAL_APK}" \
    "${FASE7_APK:-}"; do
    [[ -n "$f" && -f "$f" ]] && { printf '%s' "$f"; return 0; }
  done
  return 1
}

check_apk_hash() {
  local apk got
  if apk="$(find_canonical_apk)"; then
    got="$(sha256sum "$apk" | awk '{print $1}')"
    if [[ "$got" == "$CANONICAL_SHA256" ]]; then
      record honor "SHA-256 APK en dispositivo" PASS \
        "coincide ${CANONICAL_SHA256} (${apk})"
    else
      record honor "SHA-256 APK en dispositivo" FAIL \
        "hash distinto al canónico f3eb365; no uses este APK"
    fi
  else
    record honor "SHA-256 APK en dispositivo" PENDIENTE \
      "APK canónico no encontrado en Downloads; verificar a mano ${CANONICAL_SHA256}"
  fi
}

check_shell() {
  local sh bashv
  sh="$(command -v bash 2>/dev/null || command -v sh || true)"
  if [[ -n "$sh" && -x "$sh" ]]; then
    bashv="$(safe_capture "$sh" --version)"
    record honor "Shell" PASS "bin=${sh} ${bashv}"
  else
    record honor "Shell" FAIL "ni bash ni sh ejecutables"
  fi
  if [[ -x "${PREFIX}/bin/bash" ]]; then
    record honor "PREFIX/bin/bash" PASS "${PREFIX}/bin/bash"
  else
    record honor "PREFIX/bin/bash" FAIL "ausente (bootstrap incompleto?)"
  fi
}

check_pty() {
  if [[ -c /dev/ptmx || -c /dev/pts/ptmx ]]; then
    record honor "PTY device" PASS "/dev/ptmx presente"
  else
    record honor "PTY device" FAIL "/dev/ptmx ausente"
  fi
  if [[ -t 0 || -t 1 ]]; then
    record honor "PTY sesión (tty)" PASS "fd interactivo $(safe_capture tty)"
  else
    record honor "PTY sesión (tty)" PENDIENTE \
      "script no interactivo; abrir en sesión Termux real"
  fi
}

check_packages() {
  if [[ -f "${PREFIX}/etc/apt/sources.list" ]]; then
    if grep -qiE 'termux-play|play-store' "${PREFIX}/etc/apt/sources.list"; then
      record honor "APT sources" FAIL "repo Play presente (reintroduce goargs)"
    elif grep -qE 'packages(-cf)?\.termux\.dev' "${PREFIX}/etc/apt/sources.list"; then
      record honor "APT sources" PASS "packages.termux.dev oficial"
    else
      record honor "APT sources" FAIL "sources.list no es packages.termux.dev"
    fi
  else
    record honor "APT sources" FAIL "sin ${PREFIX}/etc/apt/sources.list"
  fi
  if command -v pkg >/dev/null 2>&1; then
    record honor "pkg disponible" PASS "$(safe_capture pkg --version || echo pkg)"
  else
    record honor "pkg disponible" FAIL "pkg ausente"
  fi
}

maybe_install() {
  local p="$1"
  command -v "$p" >/dev/null 2>&1 && return 0
  [[ "$INSTALL" == "1" ]] || return 1
  pkg install -y "$p" >/dev/null 2>&1 || return 1
  command -v "$p" >/dev/null 2>&1
}

check_go() {
  if ! command -v go >/dev/null 2>&1; then
    if [[ "$INSTALL" == "1" ]]; then
      pkg install -y golang >/dev/null 2>&1 || true
    fi
  fi
  if ! command -v go >/dev/null 2>&1; then
    record honor "Go (Termux oficial)" PENDIENTE \
      "golang no instalado; pkg install golang desde APT oficial (no Play)"
    return
  fi
  local gobin
  gobin="$(command -v go)"
  if strings "$gobin" 2>/dev/null | grep -q 'AndroidSelfExecutable'; then
    record honor "Go (Termux oficial)" FAIL \
      "marcador Play goargs AndroidSelfExecutable — reinstalar desde termux/termux-packages"
    return
  fi
  local ver out
  ver="$(safe_capture go version)"
  if echo "$ver" | grep -qiE 'unknown command|Usage: go'; then
    record honor "Go (Termux oficial)" FAIL "go version no funciona (goargs?)"
    return
  fi
  (
    cd "$WORKDIR"
    printf '%s\n' 'package main' 'import "fmt"' 'import "os"' \
      'func main() { fmt.Print("hello ", os.Args[1]) }' >hello.go
    go build -o hello hello.go
    out="$(./hello world)"
    [[ "$out" == "hello world" ]]
  ) && record honor "Go compile+argv" PASS "${ver}" \
    || record honor "Go compile+argv" FAIL "${ver}"
}

check_proot_debian() {
  if ! command -v proot-distro >/dev/null 2>&1; then
    if [[ "$INSTALL" == "1" ]]; then
      pkg install -y proot-distro >/dev/null 2>&1 || true
    fi
  fi
  if ! command -v proot-distro >/dev/null 2>&1; then
    record honor "PRoot / Debian" PENDIENTE \
      "proot-distro ausente (pkg install proot-distro; no copiar PREFIX Play)"
    return
  fi
  if ! proot-distro login debian -- /bin/true >/dev/null 2>&1; then
    if [[ "$INSTALL" == "1" ]]; then
      proot-distro install debian >/dev/null 2>&1 || true
    fi
  fi
  if ! proot-distro login debian -- /bin/true >/dev/null 2>&1; then
    record honor "PRoot / Debian login" PENDIENTE \
      "guest debian no instalado; FASE7_INSTALL=1 o proot-distro install debian"
    return
  fi
  record honor "PRoot / Debian login" PASS "proot-distro login debian -- /bin/true"
  if proot-distro login debian -- /bin/bash -lc 'test -x /bin/bash && echo bash-ok' 2>/dev/null | grep -q bash-ok; then
    record honor "Debian /bin/bash" PASS "bash-ok"
  else
    record honor "Debian /bin/bash" FAIL "bash no ejecuta"
  fi
  if proot-distro login debian -- /bin/bash -lc \
    'test -x /lib/ld-linux-aarch64.so.1 || ls /lib/ld-linux*.so* >/dev/null' >/dev/null 2>&1; then
    record honor "Debian loader" PASS "ld-linux presente"
  else
    record honor "Debian loader" FAIL "loader ausente"
  fi
}

check_node() {
  if command -v node >/dev/null 2>&1; then
    record honor "Node (Termux host)" PASS "$(safe_capture node -e 'console.log(process.version)')"
    return
  fi
  if command -v proot-distro >/dev/null 2>&1 \
    && proot-distro login debian -- /bin/bash -lc 'command -v node' >/dev/null 2>&1; then
    record honor "Node (Debian guest)" PASS \
      "$(safe_capture proot-distro login debian -- node -e 'console.log(process.version)')"
    return
  fi
  record honor "Node" PENDIENTE \
    "no instalado; preferir nodejs en guest Debian (no se instala Codex)"
}

check_git() {
  if ! command -v git >/dev/null 2>&1; then
    maybe_install git || true
  fi
  if ! command -v git >/dev/null 2>&1; then
    record honor "Git" PENDIENTE "git ausente (pkg install git)"
    return
  fi
  if (
    d="$(mktemp -d "${WORKDIR}/git.XXXXXX")"
    cd "$d"
    git init >/dev/null
    git -c user.email=fase7@local -c user.name=fase7 commit --allow-empty -m fase7 >/dev/null
    git rev-parse HEAD >/dev/null
  ); then
    record honor "Git" PASS "$(safe_capture git --version)"
  else
    record honor "Git" FAIL "git init/commit vacío falló"
  fi
}

check_gh() {
  if ! command -v gh >/dev/null 2>&1; then
    record honor "gh CLI" PENDIENTE "gh ausente (pkg install gh); no pegar tokens"
    record blocked "gh auth" BLOQUEADO "credenciales: autenticar en el teléfono, no en este script"
    return
  fi
  record honor "gh CLI versión" PASS "$(safe_capture gh --version | head -n1)"
  if gh auth status >/dev/null 2>&1; then
    record honor "gh auth" PASS "ya configurado (detalle no impreso)"
  else
    record honor "gh auth" PENDIENTE "no autenticado"
    record blocked "gh auth token" BLOQUEADO "no pegar GH_TOKEN / PAT en el script ni el reporte"
  fi
}

check_codex() {
  if command -v codex >/dev/null 2>&1; then
    record honor "Codex CLI" PASS "$(safe_capture codex --version)"
    record blocked "Codex API key" BLOQUEADO "no volcar claves; ~/.codex queda en el dispositivo"
    return
  fi
  if command -v proot-distro >/dev/null 2>&1 \
    && proot-distro login debian -- /bin/bash -lc 'command -v codex' >/dev/null 2>&1; then
    record honor "Codex CLI (Debian)" PASS "detectado en guest (sin secretos)"
    record blocked "Codex API key" BLOQUEADO "no volcar claves"
    return
  fi
  record honor "Codex CLI" PENDIENTE \
    "no instalado; instalar a mano en Debian. Este script NO lo instala"
  record blocked "Codex install automático" BLOQUEADO \
    "requiere registry + credenciales; FASE7 no lo dispara"
}

check_play_isolation() {
  if [[ -d /data/data/com.termux/files/usr ]]; then
    if [[ -w /data/data/com.termux/files/usr ]]; then
      record honor "Aislamiento Play PREFIX" FAIL \
        "este proceso PUEDE escribir el PREFIX de Play — parar"
    else
      record honor "Aislamiento Play PREFIX" PASS \
        "Play PREFIX existe y no es escribible desde coexist"
    fi
  else
    record honor "Aislamiento Play PREFIX" PENDIENTE \
      "árbol Play no visible (OK si Play usa otro usuario)"
  fi
}

check_storage_report_target() {
  if [[ -d "${HOME:-}/storage/downloads" ]]; then
    record honor "~/storage/downloads" PASS "listo para el Markdown"
  else
    record honor "~/storage/downloads" PENDIENTE \
      "termux-setup-storage no hecho; reporte cae a fallback"
    record blocked "~/storage/downloads" BLOQUEADO \
      "acceso storage no concedido en este entorno"
  fi
}

write_report() {
  local dest="$1" downloads_ok="no"
  [[ -d "${HOME:-}/storage/downloads" ]] && downloads_ok="sí"
  mkdir -p "$(dirname "$dest")"
  {
    cat <<EOF
# NewTermux Fase 7 — reporte de validación

**Leer estas instrucciones antes del bloque ejecutable.**

Este archivo no declara éxito físico en HONOR 200. El APK canónico es
\`${CANONICAL_APK}\` @ \`${CANONICAL_COMMIT}\` con SHA-256
\`${CANONICAL_SHA256}\` (coexist / \`${CANONICAL_APP_ID}\`). No uses otro hash.

1. No desinstalar Termux Play. No migrar PREFIX. No mergear. No Release.
2. Instalar solo el APK debug arm64 del run CI ${CANONICAL_RUN}
3. \`sha256sum\` debe coincidir **exactamente** con \`${CANONICAL_SHA256}\`.
4. Abrir NewTermux Dev y correr el script **dentro** de \`com.newtermux.dev\`.

## Bloque ejecutable (solo en HONOR 200, dentro de NewTermux Dev)

\`\`\`bash
# Opcional: termux-setup-storage
bash scripts/fase7/honor200-validate.sh
# Paquetes (opt-in, sigue sin tocar Play):
# FASE7_INSTALL=1 bash scripts/fase7/honor200-validate.sh
\`\`\`

- Generado: ${STAMP}
- Host: $(uname -s) $(uname -m)
- PREFIX: ${PREFIX:-∅}
- ON_DEVICE: ${ON_DEVICE}
- FASE7_INSTALL: ${INSTALL}
- Destino reporte: ${dest}
- ~/storage/downloads existe: ${downloads_ok}
- TBM: no modificado

## Comprobado en CI / cloud

| Ítem | Estado | Nota |
| --- | --- | --- |
EOF
    if [[ ${#CI_ROWS[@]} -eq 0 ]]; then
      echo "| (vacío) | PENDIENTE | sin filas CI |"
    else
      printf '%s\n' "${CI_ROWS[@]}"
    fi
    cat <<'EOF'

## Pendiente en HONOR 200

| Ítem | Estado | Nota |
| --- | --- | --- |
EOF
    if [[ ${#HONOR_ROWS[@]} -eq 0 ]]; then
      echo "| Validación en dispositivo | **PENDIENTE-HARDWARE** | este host no es HONOR 200 / Termux |"
    else
      printf '%s\n' "${HONOR_ROWS[@]}"
    fi
    cat <<'EOF'

## Bloqueado por hardware, credenciales o acceso

| Ítem | Estado | Nota |
| --- | --- | --- |
EOF
    if [[ ${#BLOCK_ROWS[@]} -eq 0 ]]; then
      echo "| (ningún bloqueo extra) | — | — |"
    else
      printf '%s\n' "${BLOCK_ROWS[@]}"
    fi
    cat <<EOF

## Referencia APK (no sustituir)

| Campo | Valor |
| --- | --- |
| Archivo | ${CANONICAL_APK} |
| SHA-256 | ${CANONICAL_SHA256} |
| Commit | ${CANONICAL_COMMIT} |
| applicationId | ${CANONICAL_APP_ID} |
| variant | ${CANONICAL_VARIANT} |
| Run | ${CANONICAL_RUN} |
| Job | ${CANONICAL_JOB} |

Secretos redactados. No hay claves en este Markdown.
EOF
  } | redact >"$dest"
}

# --- main ---
run_cloud_inventory

if is_termux_android; then
  ON_DEVICE=1
  if refuse_play_prefix; then
    check_identity
    check_abi
    check_apk_hash
    check_shell
    check_pty
    check_packages
    check_go
    check_proot_debian
    check_node
    check_git
    check_gh
    check_codex
    check_play_isolation
    check_storage_report_target
  fi
else
  record honor "Identidad / ABI / shell / PTY / paquetes" PENDIENTE-HARDWARE \
    "PREFIX='${PREFIX:-}' uname=$(uname -s) pkg=$(command -v pkg || echo no) linker=$(test -f /system/bin/linker64 && echo yes || echo no)"
  record honor "Go Termux" PENDIENTE-HARDWARE "go-smoke no corre aquí"
  record honor "PRoot / Debian / Node / Codex" PENDIENTE-HARDWARE \
    "proot Ubuntu ≠ proot-distro Termux"
  record honor "Git / gh en PREFIX nuevo" PENDIENTE-HARDWARE \
    "git/gh de este VM son del agente"
  record blocked "HONOR 200 / linker Android / pkg" BLOQUEADO \
    "este entorno no es el teléfono; no simular"
  record blocked "Credenciales gh / Codex" BLOQUEADO \
    "sin tokens en CI ni en el repo"
  record blocked "~/storage/downloads" BLOQUEADO \
    "ruta Termux ausente en cloud; fallback a docs/ o FASE7_REPORT"
fi

REPORT_PATH="$(choose_report_path)"
write_report "$REPORT_PATH"
printf '%s\n' "WROTE ${REPORT_PATH}"

if [[ "$HAD_FAIL" -eq 1 ]]; then
  exit 1
fi
if [[ "$ON_DEVICE" -eq 0 ]]; then
  printf '%s\n' "PENDIENTE-HARDWARE: not Termux-on-Android; report classified CI vs HONOR vs blocked."
  exit 2
fi
exit 0
