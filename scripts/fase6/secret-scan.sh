#!/usr/bin/env bash
# Fail on unexpected private keys / cloud tokens. Debug testkey is allowlisted.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

fail() { echo "FAIL: $*"; exit 1; }
ALLOW_JKS='app/testkey_untrusted.jks'

echo "== unexpected keystores / PEM private keys =="
while IFS= read -r f; do
  [[ "$f" == "$ALLOW_JKS" ]] && continue
  fail "unexpected key material: $f"
done < <(git ls-files '*.jks' '*.keystore' '*.p12' '*.pfx' '*.pem' '*.key')

if git grep -nI --fixed-string 'BEGIN RSA PRIVATE KEY' -- ':!docs/' ':!scripts/fase6/' \
  || git grep -nI --fixed-string 'BEGIN OPENSSH PRIVATE KEY' -- ':!docs/' ':!scripts/fase6/' \
  || git grep -nI --fixed-string 'BEGIN PRIVATE KEY' -- ':!docs/' ':!scripts/fase6/'; then
  fail "PEM private key material in git"
fi

echo "== unexpected cloud tokens (high-confidence prefixes) =="
# Do not print matches beyond the file:line that git grep already shows.
if git grep -nI -E 'ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|xox[baprs]-[A-Za-z0-9-]{10,}' \
  -- ':!docs/' ':!scripts/fase6/'; then
  fail "cloud token prefix found"
fi

echo "== debug signing is the untrusted test key only =="
git ls-files --error-unmatch "$ALLOW_JKS" >/dev/null
grep -q "storeFile file('testkey_untrusted.jks')" app/build.gradle \
  || fail "debug signingConfig no longer points at testkey_untrusted.jks"
echo "PASS: only allowlisted debug testkey; no extra private keys"
echo "NOTE: debug store passwords live in app/build.gradle (known untrusted test key, not Play)."
