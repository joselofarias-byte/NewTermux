#!/usr/bin/env bash
set -euo pipefail

ZIP="${1:-}"
EXPECTED_PACKAGE="${2:-com.newtermux.dev}"

if [[ -z "$ZIP" || ! -f "$ZIP" ]]; then
  echo "usage: $0 <bootstrap.zip> [expected.package]" >&2
  exit 64
fi

EXPECTED_PREFIX="/data/data/$EXPECTED_PACKAGE/files/usr"
OLD_PREFIX="/data/data/com.termux/files/usr"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

unzip -q "$ZIP" -d "$TMP"

fail=0

echo "== bootstrap identity check =="
echo "zip=$ZIP"
echo "expected_package=$EXPECTED_PACKAGE"
echo "expected_prefix=$EXPECTED_PREFIX"

if grep -aRIl -- "$OLD_PREFIX" "$TMP" >/tmp/newtermux-old-prefix-hits.$$ 2>/dev/null; then
  echo "FAIL: stock Termux prefix is still embedded:"
  sed "s#^$TMP/##" /tmp/newtermux-old-prefix-hits.$$ | head -100
  fail=1
else
  echo "PASS: no $OLD_PREFIX references found"
fi
rm -f /tmp/newtermux-old-prefix-hits.$$

if grep -aRIl -- "$EXPECTED_PREFIX" "$TMP" >/tmp/newtermux-expected-prefix-hits.$$ 2>/dev/null; then
  echo "PASS: expected NewTermux prefix is embedded"
  sed "s#^$TMP/##" /tmp/newtermux-expected-prefix-hits.$$ | head -40
else
  echo "FAIL: expected prefix $EXPECTED_PREFIX was not found anywhere in bootstrap"
  fail=1
fi
rm -f /tmp/newtermux-expected-prefix-hits.$$

LOGIN=""
for candidate in "$TMP/bin/login" "$TMP/usr/bin/login"; do
  if [[ -f "$candidate" ]]; then LOGIN="$candidate"; break; fi
done

if [[ -n "$LOGIN" ]]; then
  first="$(head -n 1 "$LOGIN" 2>/dev/null || true)"
  echo "login_shebang=$first"
  if [[ "$first" == "#!$EXPECTED_PREFIX/"* ]]; then
    echo "PASS: login shebang targets NewTermux"
  else
    echo "FAIL: login shebang does not target $EXPECTED_PREFIX"
    fail=1
  fi
else
  echo "WARN: login not present in bootstrap"
fi

BASH=""
for candidate in "$TMP/bin/bash" "$TMP/usr/bin/bash"; do
  if [[ -f "$candidate" ]]; then BASH="$candidate"; break; fi
done

SUPPORT_LIB=""
for candidate in   "$TMP/lib/libandroid-support.so"   "$TMP/usr/lib/libandroid-support.so"; do
  if [[ -f "$candidate" ]]; then SUPPORT_LIB="$candidate"; break; fi
done

if [[ -n "$BASH" ]]; then
  echo "bash=$(realpath --relative-to="$TMP" "$BASH")"
  if command -v readelf >/dev/null 2>&1 && readelf -d "$BASH" 2>/dev/null | grep -q 'libandroid-support\.so'; then
    echo "bash_needs_libandroid_support=yes"
    if [[ -n "$SUPPORT_LIB" ]]; then
      echo "PASS: libandroid-support.so is packaged"
    else
      echo "FAIL: bash needs libandroid-support.so but bootstrap does not contain it"
      fail=1
    fi
  else
    echo "bash_needs_libandroid_support=unknown_or_no"
  fi
else
  echo "FAIL: bash not found"
  fail=1
fi

if [[ -f "$TMP/SYMLINKS.txt" ]]; then
  if grep -aFq "$OLD_PREFIX" "$TMP/SYMLINKS.txt"; then
    echo "FAIL: SYMLINKS.txt still contains stock Termux prefix"
    fail=1
  fi
fi

if [[ "$fail" -ne 0 ]]; then
  echo "RESULT=FAIL"
  exit 1
fi

echo "RESULT=PASS"
