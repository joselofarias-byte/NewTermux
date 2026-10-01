#!/usr/bin/env bash
# Inventory the pinned official bootstrap zip. No ELF rewrite. No Go install.
set -euo pipefail

PIN_TAG='bootstrap-2026.02.12-r1+apt.android-7'
PIN_URL='https://github.com/termux/termux-packages/releases/download/bootstrap-2026.02.12-r1%2Bapt.android-7/bootstrap-aarch64.zip'
# Checksum recorded in app/build.gradle (verify currently commented out).
GRADLE_RECORDED_SHA256='f73ee7d55630ae710c977691cd5157f3e27ac6563cc233298781cad0754acadd'
# Observed 2026-09-19 from the same GitHub tag URL (asset may have been rebuilt).
OBSERVED_2026_09_19_SHA256='ea2aeba8819e517db711f8c32369e89e7c52cee73e07930ff91185e1ab93f4f3'

WORKDIR="${FASE4_WORKDIR:-${TMPDIR:-/tmp}/fase4-bootstrap-inspect}"
mkdir -p "$WORKDIR"
ZIP="$WORKDIR/bootstrap-aarch64.zip"

echo "== Fase 4 bootstrap inventory =="
echo "tag: $PIN_TAG"
echo "url: $PIN_URL"

if [[ ! -f "$ZIP" ]]; then
  curl -fsSL -o "$ZIP" "$PIN_URL"
fi

ACTUAL="$(sha256sum "$ZIP" | awk '{print $1}')"
SIZE="$(wc -c <"$ZIP" | tr -d ' ')"
echo "size_bytes: $SIZE"
echo "sha256_actual: $ACTUAL"
echo "sha256_gradle_recorded: $GRADLE_RECORDED_SHA256"
echo "sha256_observed_2026_09_19: $OBSERVED_2026_09_19_SHA256"

if [[ "$ACTUAL" != "$GRADLE_RECORDED_SHA256" ]]; then
  echo "WARN: GitHub asset hash != app/build.gradle recorded hash."
  echo "      Checksum verify in Gradle is commented out; do not silently retarget."
fi
if [[ "$ACTUAL" != "$OBSERVED_2026_09_19_SHA256" ]]; then
  echo "WARN: hash drifted again since 2026-09-19 observation. Re-document before pinning."
fi

python3 - "$ZIP" <<'PY'
import sys, zipfile, re
zip_path = sys.argv[1]
z = zipfile.ZipFile(zip_path)
names = z.namelist()
print(f"entries: {len(names)}")

go_like = [n for n in names if re.search(r'(^|/)(go|gofmt)(/|$)|golang', n, re.I)]
print("golang_paths:")
if go_like:
    for n in go_like:
        print("  " + n)
    raise SystemExit("FAIL: bootstrap must not ship golang (post-install package only)")
print("  (none)")

needles = {
    b"/data/data/com.termux/files/usr": "prefix_playcompat",
    b"/data/data/com.newtermux.dev/files/usr": "prefix_coexist",
    b"/data/data/com.newtermux.app/files/usr": "prefix_abandoned",
    b"goargs": "goargs_string",
    b"AndroidSelfExecutable": "play_goargs_marker",
    b"packages.termux.dev": "repo_official",
    b"packages-cf.termux.dev": "repo_official_cf",
}
counts = {k: 0 for k in needles}
for info in z.infolist():
    if info.is_dir():
        continue
    data = z.read(info.filename)
    for k in needles:
        counts[k] += data.count(k)

print("needle_counts:")
for k, label in needles.items():
    print(f"  {label}: {counts[k]}")

if counts[b"goargs"] or counts[b"AndroidSelfExecutable"]:
    raise SystemExit("FAIL: goargs / Play runtime marker found inside bootstrap zip")
if counts[b"/data/data/com.newtermux.app/files/usr"]:
    raise SystemExit("FAIL: abandoned com.newtermux.app PREFIX inside official zip")
if counts[b"/data/data/com.newtermux.dev/files/usr"]:
    raise SystemExit("FAIL: unexpected coexist PREFIX inside official zip (should still be com.termux)")
if counts[b"/data/data/com.termux/files/usr"] < 1:
    raise SystemExit("FAIL: official PREFIX string missing; zip is not the Termux bootstrap we expect")

src = z.read("etc/apt/sources.list").decode("utf-8", "replace")
print("sources.list:")
print(src)
if "packages.termux.dev" not in src and "packages-cf.termux.dev" not in src:
    raise SystemExit("FAIL: sources.list is not official packages.termux.dev")
if "play" in src.lower() and "termux-play" in src.lower():
    raise SystemExit("FAIL: Play APT repo in bootstrap sources.list")
print("PASS: official bootstrap, no golang, no goargs, PREFIX=com.termux, APT=termux.dev")
PY
