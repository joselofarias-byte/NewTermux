#!/usr/bin/env bash
# Verify CI outputs are coexist debug (com.newtermux.dev), not playcompat/release.
set -euo pipefail

META="${1:-./app/build/outputs/apk/coexist/debug/output-metadata.json}"
APK_DIR="$(dirname "$META")"

fail() { echo "FAIL: $*"; exit 1; }

[[ -f "$META" ]] || fail "missing $META"
python3 - "$META" <<'PY'
import json, sys
meta = json.load(open(sys.argv[1], encoding="utf-8"))
aid = meta.get("applicationId")
var = meta.get("variantName")
print(f"applicationId={aid}")
print(f"variantName={var}")
if aid != "com.newtermux.dev":
    raise SystemExit(f"FAIL applicationId {aid} != com.newtermux.dev")
if var != "coexistDebug":
    raise SystemExit(f"FAIL variantName {var} != coexistDebug")
print("PASS metadata coexistDebug / com.newtermux.dev")
PY

if [[ -d ./app/build/outputs/apk/playcompat ]]; then
  fail "playcompat outputs exist; CI must not assemble playcompatDebug"
fi
if [[ -d ./app/build/outputs/apk/release ]] || [[ -d ./app/build/outputs/apk/coexist/release ]]; then
  fail "release APK outputs exist; this job is debug-only"
fi

shopt -s nullglob
apks=("$APK_DIR"/*arm64-v8a*.apk)
[[ ${#apks[@]} -ge 1 ]] || fail "no arm64-v8a APK in $APK_DIR"
APK="${apks[0]}"
echo "arm64_apk=$APK"

# Binary XML still stores UTF-16 package strings.
python3 - "$APK" <<'PY'
import sys, zipfile
apk = sys.argv[1]
with zipfile.ZipFile(apk) as z:
    data = z.read("AndroidManifest.xml")
    names = z.namelist()
if b"CERT.RSA" not in "\n".join(names).encode() and not any(n.upper().endswith(".RSA") or n.upper().endswith(".DSA") for n in names):
    raise SystemExit("FAIL APK has no signature block in META-INF")
u16 = data.decode("utf-16le", "replace")
if "com.newtermux.dev" not in u16:
    raise SystemExit("FAIL manifest strings lack com.newtermux.dev")
print("PASS signed APK mentions com.newtermux.dev")
PY

echo "DEBUG-NOT-RELEASE: do not attach this APK to a GitHub Release"
