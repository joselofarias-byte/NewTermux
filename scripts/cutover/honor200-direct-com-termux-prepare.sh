#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

REPO="joselofarias-byte/NewTermux"
RUN_ID="36177823591"
ARTIFACT="NewTermux-playcompat-ARM64-verified"
EXPECTED_APK_SHA="b5975090cf5fcea1062c87a568e7840adb1410432707d891a4e82347a25edb56"
EXPECTED_BUNDLE_SHA="e4f77f060e0d39bb63a240821741c4ee3e4c21c8e345ed6ba68efe31582f91b3"
HANDOFF="/sdcard/Download/TBM-NEWTERMUX-HANDOFF"
OUT="/sdcard/Download/NewTermux-Direct-Cutover"
APK_OUT="/sdcard/Download/NewTermux-1.6.2-playcompat-arm64.apk"
REPORT="/sdcard/Download/NEWTERMUX-DIRECT-CUTOVER-PREPARE.txt"

fail(){ printf 'FALLO: %s\n' "$*" | tee -a "$REPORT"; exit 1; }

{
  echo "=== NEWTERMUX DIRECT CUTOVER - PREPARACION ==="
  date
  echo
} | tee "$REPORT"

[ "${PREFIX:-}" = "/data/data/com.termux/files/usr" ] || fail "Ejecutar dentro del Termux Play actual."
command -v tbm >/dev/null 2>&1 || fail "Falta tbm."
case "$(tbm version 2>&1 | head -n1)" in *1.06*) ;; *) fail "Se requiere TBM 1.06." ;; esac

[ -f "$HANDOFF/tbm" ] || fail "Falta handoff TBM."
[ -f "$HANDOFF/tbm.sha256" ] || fail "Falta tbm.sha256."
[ -f "$HANDOFF/BUNDLE_PATH.txt" ] || fail "Falta BUNDLE_PATH.txt."

BUNDLE="$(cat "$HANDOFF/BUNDLE_PATH.txt")"
[ -f "$BUNDLE" ] || fail "Bundle no visible: $BUNDLE"
GOT_BUNDLE="$(sha256sum "$BUNDLE" | awk '{print $1}')"
[ "$GOT_BUNDLE" = "$EXPECTED_BUNDLE_SHA" ] || fail "SHA bundle distinto: $GOT_BUNDLE"

echo "[1/4] Verificando bundle con TBM..." | tee -a "$REPORT"
tbm verify "$BUNDLE" 2>&1 | tee -a "$REPORT"

echo "[2/4] Descargando playcompat desde GitHub Actions..." | tee -a "$REPORT"
rm -rf "$OUT"
mkdir -p "$OUT"
gh run download "$RUN_ID" -R "$REPO" -n "$ARTIFACT" -D "$OUT"

SRC="$(find "$OUT" -type f -name '*arm64-v8a.apk' -print -quit)"
[ -n "$SRC" ] && [ -f "$SRC" ] || fail "No encontre APK arm64-v8a en el artifact."
cp -f "$SRC" "$APK_OUT"

echo "[3/4] Verificando APK..." | tee -a "$REPORT"
GOT_APK="$(sha256sum "$APK_OUT" | awk '{print $1}')"
[ "$GOT_APK" = "$EXPECTED_APK_SHA" ] || fail "SHA APK distinto: $GOT_APK"

echo "[4/4] Guardando estado de corte..." | tee -a "$REPORT"
cat > /sdcard/Download/NEWTERMUX-DIRECT-CUTOVER-READY.txt <<EOF
APK=$APK_OUT
APK_SHA256=$GOT_APK
BUNDLE=$BUNDLE
BUNDLE_SHA256=$GOT_BUNDLE
TBM=1.06
PACKAGE=com.termux
VERSION=1.6.2-playcompat
READY=YES
EOF

{
  echo "APK=$APK_OUT"
  echo "APK_SHA256=$GOT_APK"
  echo "BUNDLE_SHA256=$GOT_BUNDLE"
  echo "NEWTERMUX_DIRECT_CUTOVER_READY=PASS"
  echo
  echo "NO se desinstalo ni modifico Termux Play."
} | tee -a "$REPORT"
