#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ACTION="${1:-check}"

say() {
  printf '\n==> %s\n' "$*"
}

warn() {
  printf '\nAVISO: %s\n' "$*" >&2
}

die() {
  printf '\nERROR: %s\n' "$*" >&2
  exit 1
}

have() {
  command -v "$1" >/dev/null 2>&1
}

run_ok() {
  "$@" >/dev/null 2>&1
}

check_core() {
  local failed=0

  for cmd in bash apt-get dpkg dpkg-query; do
    if ! have "$cmd" || ! run_ok "$cmd" --version; then
      warn "$cmd no funciona correctamente"
      failed=1
    fi
  done

  if ! have pkg; then
    warn "pkg no está disponible"
    failed=1
  fi

  if ! have curl || ! run_ok curl --version; then
    warn "curl no funciona (posible mezcla libcurl/libngtcp2)"
    failed=1
  fi

  if ! have git || ! run_ok git --version; then
    warn "git no funciona"
    failed=1
  fi

  if have proot-distro && ! run_ok proot-distro --version; then
    warn "proot-distro está instalado pero no ejecuta correctamente"
    failed=1
  fi

  if [ -n "$(dpkg --audit 2>/dev/null || true)" ]; then
    warn "dpkg informa paquetes pendientes o inconsistentes"
    failed=1
  fi

  return "$failed"
}

repair_core() {
  have apt-get || die "apt-get no está disponible; no puedo autorreparar el prefijo."
  have dpkg || die "dpkg no está disponible; no puedo autorreparar el prefijo."

  say "Reparando estado de dpkg"
  dpkg --configure -a || true
  apt-get -f install -y

  say "Actualizando índices de paquetes"
  apt-get update

  # Reinstalar como conjunto evita exactamente el caso donde libcurl fue
  # actualizado pero libngtcp2/libnghttp3 quedó en una versión anterior.
  say "Reinstalando pila de red coherente"
  apt-get install -y --reinstall \
    ca-certificates \
    curl \
    libcurl \
    libngtcp2 \
    libnghttp3 \
    git

  hash -r

  say "Verificando binarios reparados"
  curl --version | head -n 3
  git --version

  if ! check_core; then
    die "La reparación terminó pero el entorno todavía presenta inconsistencias."
  fi
}

case "$ACTION" in
  check)
    say "Diagnóstico de entorno NewTermux"
    if check_core; then
      echo "ENTORNO_OK"
      exit 0
    fi
    echo "ENTORNO_REQUIERE_REPARACION"
    exit 2
    ;;
  repair)
    say "Diagnóstico de entorno NewTermux"
    if check_core; then
      echo "ENTORNO_OK · no se modificó nada"
      exit 0
    fi
    repair_core
    echo "ENTORNO_REPARADO"
    ;;
  *)
    die "Uso: environment-doctor.sh check|repair"
    ;;
esac
