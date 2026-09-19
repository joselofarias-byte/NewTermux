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
OLD_ROOT="/data/data/com.termux"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

unzip -q "$ZIP" -d "$TMP"

fail=0

is_elf() {
  local f="$1"
  [[ -f "$f" && ! -L "$f" ]] || return 1
  [[ "$(od -An -N4 -tx1 "$f" 2>/dev/null | tr -d ' \n')" == "7f454c46" ]]
}

echo "== bootstrap identity check =="
echo "zip=$ZIP"
echo "expected_package=$EXPECTED_PACKAGE"
echo "expected_prefix=$EXPECTED_PREFIX"

echo "-- text / shebang / SYMLINKS (must not contain stock PREFIX) --"
text_hits="$(mktemp)"
while IFS= read -r -d '' f; do
  if is_elf "$f"; then
    continue
  fi
  if grep -aFq "$OLD_PREFIX" "$f" 2>/dev/null || grep -aFq "$OLD_ROOT/files" "$f" 2>/dev/null; then
    echo "${f#"$TMP/"}" >> "$text_hits"
  fi
done < <(find "$TMP" -type f -print0)

if [[ -s "$text_hits" ]]; then
  echo "FAIL: stock Termux prefix is still embedded in text/shebang files:"
  head -100 "$text_hits"
  fail=1
else
  echo "PASS: no $OLD_PREFIX references in text/shebang/SYMLINKS"
fi
rm -f "$text_hits"

if grep -aRIl -- "$EXPECTED_PREFIX" "$TMP" >/tmp/newtermux-expected-prefix-hits.$$ 2>/dev/null; then
  echo "PASS: expected NewTermux prefix is embedded"
  # Do not use `sed | head` under pipefail: head closes the pipe and sed
  # exits 141 / "Broken pipe", which is what failed CI 35475342734.
  n=0
  while IFS= read -r line && (( n < 40 )); do
    echo "${line#"$TMP/"}"
    n=$((n + 1))
  done < /tmp/newtermux-expected-prefix-hits.$$
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
  if grep -aFq "$OLD_PREFIX" "$LOGIN" || grep -aFq "$OLD_ROOT/files" "$LOGIN"; then
    echo "FAIL: login body still references stock Termux paths"
    fail=1
  else
    echo "PASS: login body has no stock Termux paths"
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
  bash_dyn="$(readelf -d "$BASH" 2>/dev/null || true)"
  if [[ "$bash_dyn" == *libandroid-support.so* ]]; then
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
  if command -v patchelf >/dev/null 2>&1; then
    bash_rpath="$(patchelf --print-rpath "$BASH" 2>/dev/null || true)"
    echo "bash_rpath=$bash_rpath"
    if [[ "$bash_rpath" == *"$OLD_PREFIX"* ]]; then
      echo "FAIL: bash RUNPATH still targets stock Termux lib dir"
      fail=1
    elif [[ "$bash_rpath" == *"$EXPECTED_PREFIX"* || "$bash_rpath" == *'$ORIGIN'* ]]; then
      echo "PASS: bash RUNPATH is prefix-aware"
    elif [[ -n "$bash_rpath" ]]; then
      echo "FAIL: bash RUNPATH is neither $EXPECTED_PREFIX nor \$ORIGIN: $bash_rpath"
      fail=1
    fi
  elif [[ -n "$bash_dyn" ]]; then
    if [[ "$bash_dyn" == *"$OLD_PREFIX"* ]]; then
      echo "FAIL: bash RUNPATH still targets stock Termux lib dir"
      fail=1
    elif [[ "$bash_dyn" == *"$EXPECTED_PREFIX"* ]]; then
      echo "PASS: bash RUNPATH is prefix-aware"
    fi
  fi
else
  echo "FAIL: bash not found"
  fail=1
fi

echo "-- ELF load paths (interpreter / RUNPATH) --"
if command -v patchelf >/dev/null 2>&1; then
  while IFS= read -r -d '' f; do
    is_elf "$f" || continue
    rel="${f#"$TMP/"}"
    if rpath="$(patchelf --print-rpath "$f" 2>/dev/null)"; then
      if [[ "$rpath" == *"$OLD_PREFIX"* ]]; then
        echo "FAIL: $rel RUNPATH still has $OLD_PREFIX"
        fail=1
      fi
    fi
    if interp="$(patchelf --print-interpreter "$f" 2>/dev/null)"; then
      if [[ "$interp" == *"$OLD_PREFIX"* ]]; then
        echo "FAIL: $rel interpreter still has $OLD_PREFIX"
        fail=1
      fi
    fi
  done < <(find "$TMP" -type f -print0)
  echo "PASS: no ELF interpreter/RUNPATH uses stock PREFIX"
else
  echo "WARN: patchelf missing; skipped ELF load-path sweep"
fi

echo "-- ELF .rodata compile-time leftovers (cannot grow in-place) --"
rodata_hits=0
while IFS= read -r -d '' f; do
  is_elf "$f" || continue
  if grep -aFq "$OLD_PREFIX" "$f" 2>/dev/null; then
    rodata_hits=$((rodata_hits + 1))
  fi
done < <(find "$TMP" -type f -print0)
echo "elf_rodata_old_prefix_files=$rodata_hits"
if [[ "$rodata_hits" -gt 0 ]]; then
  echo "WARN: official-deb compile-time PREFIX strings remain in ELF .rodata."
  echo "WARN: runtime contract is shebang + login body + RUNPATH + LD_LIBRARY_PATH."
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
