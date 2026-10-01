#!/usr/bin/env bash
# Cloud/host Fase 5 checks. Do not claim PRoot/Debian works on HONOR 200.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "== product tree must not implement PRoot =="
if git grep -nE 'proot-distro|PRoot' -- '*.java' '*.kt' '*.xml' '*.gradle'; then
  echo "FAIL: PRoot leaked into app sources"
  exit 1
fi
echo "PASS: PRoot remains post-bootstrap (scripts/docs only)"

echo "== smoke refuses Play PREFIX =="
grep -q 'com.termux' scripts/fase5/proot-debian-smoke.sh
grep -q 'com.newtermux.dev' scripts/fase5/proot-debian-smoke.sh
grep -q 'AndroidSelfExecutable\|Play/playcompat PREFIX' scripts/fase5/proot-debian-smoke.sh \
  || grep -q 'Play/playcompat PREFIX' scripts/fase5/proot-debian-smoke.sh
echo "PASS: Play PREFIX refused in smoke"

echo "== no PREFIX wholesale copy =="
if grep -nE 'cp -a .*/data/data/com.termux|rsync .*com.termux/files' scripts/fase5/proot-debian-smoke.sh; then
  echo "FAIL: smoke copies Play PREFIX"
  exit 1
fi
echo "PASS: no PREFIX copy in Fase 5 scripts"

echo "== smoke reports PENDIENTE-HARDWARE here =="
set +e
bash scripts/fase5/proot-debian-smoke.sh
rc=$?
set -e
if [[ "$rc" -eq 2 ]]; then
  echo "PASS: proot-debian-smoke exit 2 on this host"
else
  echo "FAIL: proot-debian-smoke exit $rc (expected 2)"
  exit 1
fi

echo "== host git / gh (agent, NOT Termux Debian) =="
git --version
command -v gh >/dev/null
gh --version >/dev/null
echo "PASS host git/gh (does not prove guest tools)"

echo "== host TLS to official repos (no --insecure) =="
curl -fsSI --max-time 30 https://packages.termux.dev >/dev/null
curl -fsSI --max-time 30 https://deb.debian.org >/dev/null
echo "PASS host TLS to packages.termux.dev and deb.debian.org"

echo "== required smoke coverage strings =="
for needle in '/bin/bash' 'ld-linux' 'shebang' 'deb.debian.org' 'HOME=' 'SIGTERM' 'storage/shared' 'codex' 'nodejs'; do
  grep -q "$needle" scripts/fase5/proot-debian-smoke.sh || {
    echo "FAIL: smoke missing $needle"
    exit 1
  }
done
echo "PASS smoke covers documented checks"

echo "PASS Fase 5 host/cloud checks"
