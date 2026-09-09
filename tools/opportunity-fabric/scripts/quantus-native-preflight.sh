#!/usr/bin/env bash
set -euo pipefail
arch="$(uname -m)"; os="$(uname -s)"
echo "OS=$os ARCH=$arch"
if [ "$os" = Linux ] && [ "$arch" = x86_64 ]; then
  echo 'OK: ruta nativa documentada para Quantus.'
elif [ "$os" = Darwin ] && { [ "$arch" = arm64 ] || [ "$arch" = x86_64 ]; }; then
  echo 'OK: ruta nativa documentada para Quantus.'
else
  echo 'NO SOPORTADO oficialmente por la guía de minería actual.' >&2
  exit 2
fi
command -v curl >/dev/null || { echo 'Falta curl'; exit 3; }
echo 'Antes de minar mainnet, confirmar la guía oficial actualizada: https://docs.quantus.com/guides/mining/'
echo 'La guía publicada todavía contiene ejemplos de Planck testnet; no se ejecuta ningún instalador automáticamente.'
