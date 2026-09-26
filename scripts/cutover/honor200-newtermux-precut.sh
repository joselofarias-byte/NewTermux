#!/data/data/com.newtermux.dev/files/usr/bin/bash
set -Eeuo pipefail

EXPECTED_PREFIX="/data/data/com.newtermux.dev/files/usr"
OLD_PREFIX="/data/data/com.termux/files/usr"
TBM_SRC="/storage/emulated/0/Download/TBM-NATIVE-1c7341e/tbm"
TBM_SHA="1e64da52db7f2628abd2a7224197dce15f509fbf730800f482f1aebde9a8b67e"
BUNDLE="/storage/emulated/0/Download-Folders/tbm_backups/tbm_migration_20260923-001255.tar"
BUNDLE_SHA="e4f77f060e0d39bb63a240821741c4ee3e4c21c8e345ed6ba68efe31582f91b3"

die(){ printf '\nFATAL: %s\n' "$*" >&2; exit 1; }

echo "=== NEWTERMUX - PRECORTE HONOR 200 ==="
echo "Fecha: $(date)"
echo "PREFIX=$PREFIX"
echo "HOME=$HOME"

[ "$PREFIX" = "$EXPECTED_PREFIX" ] || die "Este script debe ejecutarse dentro de NewTermux com.newtermux.dev."

echo
echo "[1/6] Bootstrap y shell"
LOGIN_HEAD="$(head -n 1 "$PREFIX/bin/login" 2>/dev/null || true)"
echo "LOGIN_SHEBANG=$LOGIN_HEAD"
[ "$LOGIN_HEAD" = "#!$EXPECTED_PREFIX/bin/sh" ] || die "login todavía apunta a un PREFIX incorrecto."

"$PREFIX/bin/bash" --version | head -n 1
"$PREFIX/bin/sh" -c 'echo NEWTERMUX_SH_OK'
"$PREFIX/bin/dpkg" --version | head -n 1
"$PREFIX/bin/apt" --version | head -n 1
"$PREFIX/bin/pkg" --help >/dev/null 2>&1 || die "pkg no funciona."
echo "LOGIN_BASH_SH_PKG=OK"

echo
echo "[2/6] Identidad binaria del PREFIX"
grep -aFq "$EXPECTED_PREFIX/etc/dpkg" "$PREFIX/bin/dpkg" || die "dpkg no contiene el PREFIX de NewTermux."
if grep -aFq "$OLD_PREFIX/etc/dpkg" "$PREFIX/bin/dpkg"; then
  die "dpkg todavía contiene el PREFIX de Termux Play."
fi
echo "PREFIX_BINARIO=OK"

echo
echo "[3/6] Acceso al almacenamiento compartido"
[ -r "$TBM_SRC" ] || die "No se puede leer $TBM_SRC. Concedé acceso a archivos/almacenamiento a NewTermux."
[ -r "$BUNDLE" ] || die "No se puede leer el paquete TBM."
[ -r "$BUNDLE.meta.json" ] || die "Falta .meta.json."
[ -r "$BUNDLE.sha256" ] || die "Falta .sha256."
echo "ALMACENAMIENTO=OK"

echo
echo "[4/6] Instalando TBM estable en el PREFIX nuevo"
ACTUAL_TBM_SHA="$(sha256sum "$TBM_SRC" | awk '{print $1}')"
echo "TBM_ORIGEN_SHA=$ACTUAL_TBM_SHA"
[ "$ACTUAL_TBM_SHA" = "$TBM_SHA" ] || die "El binario TBM compartido no es el final validado."
install -m 0755 "$TBM_SRC" "$PREFIX/bin/tbm"
hash -r
echo "TBM_VERSION=$(tbm version)"
[ "$(sha256sum "$PREFIX/bin/tbm" | awk '{print $1}')" = "$TBM_SHA" ] || die "TBM cambió al copiarlo."
echo "TBM_NUEVO_PREFIX=OK"

echo
echo "[5/6] Verificando evidencia del paquete sin recalcular 46 GiB"
RECORDED="$(awk 'NF{print $1; exit}' "$BUNDLE.sha256")"
echo "BUNDLE_SHA_REGISTRADO=$RECORDED"
[ "$RECORDED" = "$BUNDLE_SHA" ] || die "El sidecar SHA no coincide con el paquete ya validado."
echo "BUNDLE_SIDECARS=OK"

echo
echo "[6/6] Estado previo al corte"
df -h "$HOME" /storage/emulated/0 2>/dev/null || true
echo
echo "Contenido actual del HOME fresco:"
find "$HOME" -mindepth 1 -maxdepth 1 -printf '%f\n' 2>/dev/null | sort | head -n 80 || true

echo
echo "NEWTERMUX_PRE_CUTOVER_OK"
echo "No se restauró HOME todavía y Termux Play no fue modificado."
