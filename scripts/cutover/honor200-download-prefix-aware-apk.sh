#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

REPO="joselofarias-byte/NewTermux"
RUN_ID="36212880258"
ARTIFACT="newtermux-prefix-aware-coexist-arm64"
DEST="$HOME/storage/downloads/NewTermux-HONOR200-22c2b0e"

echo "=== NEWTERMUX - DESCARGA APK HONOR 200 ==="
echo "Run: $RUN_ID (PR #23, build prefix-aware reproducible)"
mkdir -p "$DEST"
rm -f "$DEST"/*.apk "$DEST"/*.sha256 2>/dev/null || true

if ! gh run download "$RUN_ID" -R "$REPO" -n "$ARTIFACT" -D "$DEST"; then
  echo
  echo "EL_BUILD_TODAVIA_NO_ESTA_LISTO"
  echo "No se instaló ni modificó ninguna aplicación."
  exit 2
fi

APK="$(find "$DEST" -maxdepth 1 -type f -name '*arm64-v8a.apk' -print -quit)"
SUMFILE="$(find "$DEST" -maxdepth 1 -type f -name '*.sha256' -print -quit)"

[ -n "$APK" ] && [ -s "$APK" ] || { echo "FATAL: APK ARM64 no encontrado."; exit 1; }
[ -n "$SUMFILE" ] && [ -s "$SUMFILE" ] || { echo "FATAL: archivo SHA-256 no encontrado."; exit 1; }

EXPECTED="$(awk 'NF{print $1; exit}' "$SUMFILE")"
ACTUAL="$(sha256sum "$APK" | awk '{print $1}')"

echo
echo "APK=$APK"
echo "SHA_ESPERADO=$EXPECTED"
echo "SHA_ACTUAL=$ACTUAL"
[ "$EXPECTED" = "$ACTUAL" ] || { echo "FATAL: SHA-256 no coincide."; exit 1; }

echo
echo "NEWTERMUX_APK_READY"
echo "Paquete esperado por CI: com.newtermux.dev"
echo "No se tocó Termux Play."
