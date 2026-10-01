#!/usr/bin/env bash
# Rewrite a Termux bootstrap zip so scripts, shebangs, SYMLINKS, and ELF
# RUNPATH/interpreter target TERMUX_APP_PACKAGE (default com.newtermux.dev).
#
# This is NOT the abandoned equal-length ELF fil/file hack. New PREFIX is
# longer than /data/data/com.termux/files/usr; text is rewritten at any
# length and ELF load paths are updated with patchelf.
#
# Official APT debs still embed compile-time PREFIX strings in ELF .rodata.
# Those leftovers cannot be grown in-place; verify-bootstrap-prefix.sh treats
# them as WARN after RUNPATH/shebang checks pass.
set -euo pipefail

ZIP="${1:-}"
EXPECTED_PACKAGE="${2:-com.newtermux.dev}"

if [[ -z "$ZIP" || ! -f "$ZIP" ]]; then
  echo "usage: $0 <bootstrap.zip> [expected.package]" >&2
  exit 64
fi

if [[ "$EXPECTED_PACKAGE" != "com.newtermux.dev" ]]; then
  echo "REFUSED: only com.newtermux.dev is allowed (got $EXPECTED_PACKAGE)" >&2
  exit 2
fi

OLD_ROOT="/data/data/com.termux"
NEW_ROOT="/data/data/${EXPECTED_PACKAGE}"
OLD_PREFIX="${OLD_ROOT}/files/usr"
NEW_PREFIX="${NEW_ROOT}/files/usr"

if ! command -v patchelf >/dev/null 2>&1; then
  echo "FAIL: patchelf is required" >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "== rewrite bootstrap prefix =="
echo "zip=$ZIP"
echo "old_prefix=$OLD_PREFIX"
echo "new_prefix=$NEW_PREFIX"

unzip -q "$ZIP" -d "$TMP"

is_elf() {
  local f="$1"
  [[ -f "$f" && ! -L "$f" ]] || return 1
  [[ "$(od -An -N4 -tx1 "$f" 2>/dev/null | tr -d ' \n')" == "7f454c46" ]]
}

rewrite_text_file() {
  local f="$1"
  # grep -I skips binary files (NUL bytes). Do not sed ELF/.so/.gz.
  if grep -Iq "$OLD_ROOT" "$f" 2>/dev/null; then
    # Prefer full PREFIX first so we do not create a half-rewritten path.
    sed -i \
      -e "s|${OLD_PREFIX}|${NEW_PREFIX}|g" \
      -e "s|${OLD_ROOT}/|${NEW_ROOT}/|g" \
      "$f"
  fi
}

echo "[*] Rewriting shebangs and text files"
while IFS= read -r -d '' f; do
  if is_elf "$f"; then
    continue
  fi
  rewrite_text_file "$f"
done < <(find "$TMP" -type f -print0)

echo "[*] Updating ELF interpreter and RUNPATH with patchelf"
elf_count=0
elf_patched=0
while IFS= read -r -d '' f; do
  is_elf "$f" || continue
  elf_count=$((elf_count + 1))
  changed=0
  if rpath="$(patchelf --print-rpath "$f" 2>/dev/null)"; then
    if [[ "$rpath" == *"$OLD_ROOT"* ]]; then
      new_rpath="${rpath//$OLD_PREFIX/$NEW_PREFIX}"
      new_rpath="${new_rpath//$OLD_ROOT/$NEW_ROOT}"
      patchelf --set-rpath "$new_rpath" "$f"
      changed=1
    fi
  fi
  if interp="$(patchelf --print-interpreter "$f" 2>/dev/null)"; then
    if [[ "$interp" == *"$OLD_ROOT"* ]]; then
      new_interp="${interp//$OLD_PREFIX/$NEW_PREFIX}"
      new_interp="${new_interp//$OLD_ROOT/$NEW_ROOT}"
      patchelf --set-interpreter "$new_interp" "$f"
      changed=1
    fi
  fi
  if [[ "$changed" -eq 1 ]]; then
    elf_patched=$((elf_patched + 1))
  fi
done < <(find "$TMP" -type f -print0)

echo "elf_files=$elf_count elf_load_path_patched=$elf_patched"

login=""
for candidate in "$TMP/bin/login" "$TMP/usr/bin/login"; do
  if [[ -f "$candidate" ]]; then
    login="$candidate"
    break
  fi
done
if [[ -z "$login" ]]; then
  echo "FAIL: login missing after rewrite" >&2
  exit 1
fi
shebang="$(head -n 1 "$login")"
echo "login_shebang=$shebang"
if [[ "$shebang" != "#!${NEW_PREFIX}/"* ]]; then
  echo "FAIL: login shebang was not rewritten to $NEW_PREFIX" >&2
  exit 1
fi
if grep -aFq "$OLD_ROOT" "$login"; then
  echo "FAIL: login still references $OLD_ROOT" >&2
  exit 1
fi

work="$(mktemp -d)/bootstrap-rewritten.zip"
(cd "$TMP" && zip -q -r9 "$work" .)
mv -f "$work" "$ZIP"
echo "RESULT=REWRITTEN $ZIP"
