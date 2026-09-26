#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

EXPECTED_PREFIX="/data/data/com.termux/files/usr"
HANDOFF="/sdcard/Download/TBM-NEWTERMUX-HANDOFF"
READY="/sdcard/Download/NEWTERMUX-DIRECT-CUTOVER-READY.txt"
STAMP="$(date +%Y%m%d-%H%M%S)"
BIN_DIR="$HOME/.tbm/bin"
STAGE="$HOME/.tbm/direct-cutover-stage-$STAMP"
REPORT="/sdcard/Download/NEWTERMUX-DIRECT-POSTINSTALL-$STAMP.txt"

fail(){ printf 'FALLO: %s\n' "$*" | tee -a "$REPORT"; exit 1; }
ok(){ printf 'PASS  %s\n' "$*" | tee -a "$REPORT"; }

{
  echo "=== NEWTERMUX com.termux - VALIDACION POSTINSTALACION ==="
  date
  echo "PREFIX=${PREFIX:-}"
  echo "HOME=${HOME:-}"
  echo
} | tee "$REPORT"

[ "${PREFIX:-}" = "$EXPECTED_PREFIX" ] || fail "PREFIX inesperado."
[ -f "$READY" ] || fail "Falta NEWTERMUX-DIRECT-CUTOVER-READY.txt."

for bin in sh bash login dpkg apt; do
  [ -x "$PREFIX/bin/$bin" ] || fail "Falta $PREFIX/bin/$bin"
done

"$PREFIX/bin/sh" -c 'printf NEWTERMUX_SH_OK' | grep -q '^NEWTERMUX_SH_OK$' || fail "sh no ejecuta"
ok "SH_EJECUTA"

"$PREFIX/bin/bash" --version >/dev/null 2>&1 || fail "bash no ejecuta"
ok "BASH_EJECUTA"

LOGIN_OUT="$(timeout 5 "$PREFIX/bin/login" --help 2>&1 || true)"
if printf '%s' "$LOGIN_OUT" | grep -qiE 'permission denied|cannot execute|segmentation fault|not found'; then
  fail "login no ejecuta: $(printf '%s' "$LOGIN_OUT" | head -c 180)"
fi
ok "LOGIN_EJECUTA"

"$PREFIX/bin/dpkg" --version >/dev/null 2>&1 || fail "dpkg no ejecuta"
ok "DPKG_EJECUTA"

"$PREFIX/bin/apt" --version >/dev/null 2>&1 || fail "apt no ejecuta"
ok "APT_EJECUTA"

command -v pkg >/dev/null 2>&1 || fail "pkg ausente"
ok "PKG_DISPONIBLE"

mkdir -p "$BIN_DIR" "$STAGE"
cp -f "$HANDOFF/tbm" "$BIN_DIR/tbm"
chmod 700 "$BIN_DIR/tbm"
(
  cd "$HANDOFF"
  sha256sum -c tbm.sha256
) 2>&1 | tee -a "$REPORT"

TBM="$BIN_DIR/tbm"
case "$("$TBM" version 2>&1 | head -n1)" in *1.06*) ;; *) fail "TBM handoff no es 1.06" ;; esac
ok "TBM_1_06"

BUNDLE="$(cat "$HANDOFF/BUNDLE_PATH.txt")"
[ -f "$BUNDLE" ] || fail "Bundle no visible: $BUNDLE"

echo "[1/3] Verificando bundle..." | tee -a "$REPORT"
"$TBM" verify "$BUNDLE" 2>&1 | tee -a "$REPORT"

echo "[2/3] Restaurando SOLO home,info a staging..." | tee -a "$REPORT"
"$TBM" restore "$BUNDLE" "$STAGE" --yes --components home,info 2>&1 | tee -a "$REPORT"

[ -d "$STAGE/migration_info" ] || fail "Falta migration_info"
[ ! -d "$STAGE/prefix_snapshot" ] || fail "SEGURIDAD: se restauro prefix_snapshot"
if find "$STAGE" -maxdepth 1 -type d -name 'proot_*' | grep -q .; then
  fail "SEGURIDAD: se restauro proot raw"
fi

echo "[3/3] Control final del staging..." | tee -a "$REPORT"
printf 'STAGE=%s\n' "$STAGE" | tee -a "$REPORT"
printf 'COMPONENTS=home,info\nPREFIX_SNAPSHOT_RESTORED=NO\nPROOT_RAW_RESTORED=NO\n' | tee -a "$REPORT"

if grep -RIl --binary-files=without-match --exclude-dir='.git' '/data/data/com.newtermux.dev/files' "$STAGE" 2>/dev/null | head -n1 | grep -q .; then
  fail "El HOME restaurado contiene referencias al paquete coexist com.newtermux.dev"
fi
ok "SIN_REFERENCIAS_COEXIST"

echo "NEWTERMUX_DIRECT_POSTINSTALL_STAGE=PASS" | tee -a "$REPORT"
echo "INFORME=$REPORT"
