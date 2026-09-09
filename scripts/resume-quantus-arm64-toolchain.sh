#!/usr/bin/env bash
set -uo pipefail

STAMP="$(date +%Y%m%d-%H%M%S)"
STATE="$HOME/.local/state/opportunity-fabric"
LOGDIR="$STATE/logs"
BACKUPDIR="$STATE/backups"
mkdir -p "$LOGDIR" "$BACKUPDIR"
LOG="$LOGDIR/quantus-arm64-resume-$STAMP.log"

exec > >(tee -a "$LOG") 2>&1

OFFICIAL_KEY="${PREFIX}/share/termux-keyring/termux-autobuilds.gpg"
OFFICIAL_MAIN="https://packages-cf.termux.dev/apt/termux-main/"

echo "==> Quantus ARM64 resume: normalize Termux main -> toolchain -> preflight -> build -> benchmark"
echo "==> Log: $LOG"

if ! command -v pkg >/dev/null 2>&1 || ! command -v of >/dev/null 2>&1; then
  echo "ERROR: native Termux + Opportunity Fabric are required." >&2
  exit 2
fi
if [ ! -f "$OFFICIAL_KEY" ]; then
  echo "ERROR: verified Termux autobuild key is missing; run the main Opportunity Fabric bootstrap first." >&2
  exit 3
fi

backup_file() {
  local f="$1" tag out
  tag="$(printf '%s' "$f" | python -c 'import hashlib,sys; print(hashlib.sha1(sys.stdin.buffer.read()).hexdigest()[:12])')"
  out="$BACKUPDIR/$(basename "$f").$tag.$STAMP.bak"
  cp -a "$f" "$out"
  echo "$out"
}

normalize_main_file() {
  local f="$1"
  python - "$f" "$OFFICIAL_KEY" "$OFFICIAL_MAIN" <<'PY'
from pathlib import Path
import re, sys
p=Path(sys.argv[1]); key=sys.argv[2]; main=sys.argv[3]
txt=p.read_text(encoding="utf-8")
changed=False

if p.suffix == ".sources":
    parts=re.split(r"(\n\s*\n)", txt)
    for i in range(0,len(parts),2):
        s=parts[i]
        if "termux-glibc" in s:
            continue
        if not ("termux.net" in s or "apt/termux-main" in s or "termux-main" in s):
            continue
        lines=s.splitlines()
        wanted={
            "types":"Types: deb",
            "uris":f"URIs: {main}",
            "suites":"Suites: stable",
            "components":"Components: main",
            "signed-by":f"Signed-By: {key}",
        }
        seen=set(); out=[]
        for line in lines:
            if ":" in line and not line[:1].isspace():
                k=line.split(":",1)[0].strip().lower()
                if k in wanted:
                    nl=wanted[k]
                    changed |= (line != nl)
                    out.append(nl); seen.add(k); continue
            out.append(line)
        for k,nl in wanted.items():
            if k not in seen:
                out.append(nl); changed=True
        parts[i]="\n".join(out)
    if changed: p.write_text("".join(parts),encoding="utf-8")
else:
    out=[]
    for line in txt.splitlines(True):
        raw=line.rstrip("\n"); nl="\n" if line.endswith("\n") else ""
        s=raw.lstrip(); indent=raw[:len(raw)-len(s)]
        if s.startswith("deb ") and "termux-glibc" not in s and ("termux.net" in s or "apt/termux-main" in s):
            new=f"deb [signed-by={key}] {main} stable main"
            changed |= (s != new)
            raw=indent+new
        out.append(raw+nl)
    if changed: p.write_text("".join(out),encoding="utf-8")
print("changed" if changed else "unchanged")
PY
}

found=0
changed=0
while IFS= read -r f; do
  [ -f "$f" ] || continue
  if ! grep -Eq 'termux\.net|apt/termux-main|termux-main' "$f"; then
    continue
  fi
  if grep -q 'termux-glibc' "$f" && ! grep -Eq 'termux\.net|apt/termux-main' "$f"; then
    continue
  fi
  found=1
  b="$(backup_file "$f")"
  echo "==> Backup main source: $b"
  r="$(normalize_main_file "$f")"
  echo "==> Main source patch ($f): $r"
  [ "$r" = changed ] && changed=1
done < <(
  {
    [ -f "$PREFIX/etc/apt/sources.list" ] && printf '%s\n' "$PREFIX/etc/apt/sources.list"
    find "$PREFIX/etc/apt/sources.list.d" -maxdepth 1 -type f \( -name '*.list' -o -name '*.sources' \) -print 2>/dev/null || true
  } | sort -u
)

if [ "$found" -eq 0 ]; then
  target="$PREFIX/etc/apt/sources.list.d/opportunity-fabric-termux-main.sources"
  cat >"$target" <<EOF
Types: deb
URIs: $OFFICIAL_MAIN
Suites: stable
Components: main
Signed-By: $OFFICIAL_KEY
EOF
  echo "==> Created current official Termux main source: $target"
  changed=1
fi

if [ "$changed" -eq 1 ]; then
  echo "==> Termux main normalized to current official repository: $OFFICIAL_MAIN"
else
  echo "==> Termux main already normalized."
fi

echo "==> Refreshing APT metadata..."
if ! apt-get update; then
  echo "ERROR: apt-get update failed after main-source normalization." >&2
  exit 20
fi

echo "==> Checking rust/cmake visibility..."
missing_pkgs=()
for p in rust cmake; do
  if ! apt-cache show "$p" >/dev/null 2>&1; then
    missing_pkgs+=("$p")
  fi
done

if [ "${#missing_pkgs[@]}" -gt 0 ]; then
  echo "ERROR: packages still not visible: ${missing_pkgs[*]}" >&2
  for p in "${missing_pkgs[@]}"; do apt-cache policy "$p" || true; done
  echo "==> Active source diagnostics:"
  grep -RInE 'termux\.net|termux-main|termux-glibc|Signed-By|URIs:' "$PREFIX/etc/apt" 2>/dev/null | head -n 120 || true
  exit 24
fi

install=()
command -v rustc >/dev/null 2>&1 || install+=(rust)
command -v cargo >/dev/null 2>&1 || install+=(rust)
command -v cmake >/dev/null 2>&1 || install+=(cmake)
if [ "${#install[@]}" -gt 0 ]; then
  uniq=()
  for p in "${install[@]}"; do
    case " ${uniq[*]:-} " in *" $p "*) ;; *) uniq+=("$p");; esac
  done
  echo "==> Installing: ${uniq[*]}"
  apt-get install -y "${uniq[@]}" || exit 24
else
  echo "==> Rust/Cargo/CMake already installed."
fi

echo "==> Quantus preflight"
of quantus-preflight
if ! of quantus-preflight | grep -q '"ready_to_attempt_build": true'; then
  echo "ERROR: Quantus toolchain preflight is still incomplete." >&2
  exit 25
fi

echo "==> Starting official Quantus source build (2 Cargo jobs)."
RESULT="$STATE/quantus-arm64-result-$STAMP.json"
of quantus-build --execute --jobs 2 | tee "$RESULT"

python - "$RESULT" <<'PY'
import json,sys
p=sys.argv[1]
try:
    d=json.load(open(p,encoding="utf-8"))
except Exception as e:
    print("RESULT_PARSE_ERROR:",e); raise SystemExit(3)
if d.get("ok"):
    print("\n=== QUANTUS ARM64 EXPERIMENT: SUCCESS ===")
    print("Binary:",d.get("binary"))
    if d.get("benchmark_output"):
        print("\nBenchmark output:\n",d["benchmark_output"])
    raise SystemExit(0)
print("\n=== QUANTUS ARM64 EXPERIMENT: BUILD/BENCHMARK NOT YET VIABLE ===")
print("Stage:",d.get("stage"))
if d.get("log_tail"): print("\nBuild log tail:\n",d["log_tail"])
print("No mining was started and no wallet secret was touched.")
raise SystemExit(4)
PY
rc=$?
echo "==> Resume return code: $rc"
echo "==> Full log: $LOG"
exit "$rc"
