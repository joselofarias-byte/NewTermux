#!/usr/bin/env bash
# Deterministic checks for the playcompat ARM64 synchronize gate.
# No Gradle, no device, no workflow_dispatch.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"

classify="$root/scripts/fase8/classify-playcompat-build-delta.sh"
workflow="$root/.github/workflows/playcompat_migration_build.yml"
failures=0

check_case() {
  local name="$1" want_required="$2" want_reason="$3"
  shift 3
  local out got_required got_reason
  if ! out="$("$classify" "$@")"; then
    echo "FAIL: $name (classifier exited nonzero)"
    failures=$((failures + 1))
    return
  fi
  got_required="$(printf '%s\n' "$out" | sed -n 's/^build_required=//p' | tail -1)"
  got_reason="$(printf '%s\n' "$out" | sed -n 's/^reason=//p' | tail -1)"
  if [[ "$got_required" == "$want_required" && "$got_reason" == "$want_reason" ]]; then
    echo "PASS: $name"
  else
    echo "FAIL: $name expected build_required=$want_required reason=$want_reason"
    echo "      got build_required=${got_required:-<missing>} reason=${got_reason:-<missing>}"
    printf '%s\n' "$out" | sed 's/^/      /'
    failures=$((failures + 1))
  fi
}

check_case \
  "docs-only synchronize skips the expensive build" \
  false synchronize_no_build_inputs \
  --event pull_request --action synchronize \
  --commit-message "fix: rebuild the playcompat APK" \
  --file docs/NEWTERMUX_PLAYCOMPAT_CI_STATUS_2026-09-25.md

check_case \
  "irrelevant synchronize skips the expensive build" \
  false synchronize_no_build_inputs \
  --event pull_request --action synchronize \
  --file README.md

check_case \
  "workflow delta requires the build" \
  true synchronize_build_inputs \
  --event pull_request --action synchronize \
  --commit-message "docs: comment only" \
  --file .github/workflows/playcompat_migration_build.yml

check_case \
  "assert-script delta requires the build" \
  true synchronize_build_inputs \
  --event pull_request --action synchronize \
  --file scripts/fase8/assert-playcompat-stock-bootstrap.sh

check_case \
  "app gradle delta requires the build" \
  true synchronize_build_inputs \
  --event pull_request --action synchronize \
  --file app/build.gradle

check_case \
  "TermuxInstaller delta requires the build" \
  true synchronize_build_inputs \
  --event pull_request --action synchronize \
  --file app/src/main/java/com/termux/app/TermuxInstaller.java

check_case \
  "shell tree delta requires the build" \
  true synchronize_build_inputs \
  --event pull_request --action synchronize \
  --file termux-shared/src/main/java/com/termux/shared/termux/shell/command/environment/TermuxShellEnvironment.java

check_case \
  "docs mixed with an app input still requires the build" \
  true synchronize_build_inputs \
  --event pull_request --action synchronize \
  --file docs/NEWTERMUX_PLAYCOMPAT_CI_STATUS_2026-09-25.md \
  --file app/build.gradle

check_case \
  "gate-script-only delta does not assemble" \
  false synchronize_no_build_inputs \
  --event pull_request --action synchronize \
  --file scripts/fase8/classify-playcompat-build-delta.sh \
  --file scripts/fase8/test-playcompat-build-gate.sh \
  --file docs/NEWTERMUX_PLAYCOMPAT_CI_STATUS_2026-09-25.md

check_case \
  "workflow_dispatch is allowed to build" \
  true workflow_dispatch \
  --event workflow_dispatch \
  --file docs/NEWTERMUX_PLAYCOMPAT_CI_STATUS_2026-09-25.md

check_case \
  "reopened does not force a rebuild" \
  false reopened_no_new_delta \
  --event pull_request --action reopened \
  --file app/build.gradle

check_case \
  "unresolved synchronize range fails open" \
  true unresolved_delta \
  --from-git --event pull_request --action synchronize \
  --before 0000000000000000000000000000000000000000 \
  --after 29ec6692bba816fffc4d0a80de97f4a793de39c5

# The push that rebuilt anyway: 112e1d4 -> 29ec669 is docs-only.
docs_before=112e1d47c5f6d07a1cc74fc29d8f41bf99251789
docs_after=29ec6692bba816fffc4d0a80de97f4a793de39c5
build_before=22c2b0ef91c5121de99deb2205b7e69841aa3028
build_after=112e1d47c5f6d07a1cc74fc29d8f41bf99251789

if git cat-file -e "${docs_before}^{commit}" && git cat-file -e "${docs_after}^{commit}" \
  && git cat-file -e "${build_before}^{commit}"; then
  check_case \
    "real docs synchronize 112e1d4..29ec669 skips the build" \
    false synchronize_no_build_inputs \
    --from-git --event pull_request --action synchronize \
    --before "$docs_before" --after "$docs_after"

  check_case \
    "real workflow/script delta 22c2b0e..112e1d4 requires the build" \
    true synchronize_build_inputs \
    --from-git --event pull_request --action synchronize \
    --before "$build_before" --after "$build_after"
else
  echo "FAIL: regression SHAs are not in this clone (need fetch-depth 0)"
  failures=$((failures + 1))
fi

python3 - "$workflow" <<'PY'
import pathlib, sys
workflow = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
failures = []

def check(ok, message):
    print(("PASS: " if ok else "FAIL: ") + message)
    if not ok:
        failures.append(message)

build_inputs = [
    ".github/workflows/playcompat_migration_build.yml",
    "app/build.gradle",
    "app/src/main/java/com/termux/app/TermuxInstaller.java",
    "termux-shared/src/main/java/com/termux/shared/termux/shell/**",
    "scripts/fase8/assert-playcompat-stock-bootstrap.sh",
]
for path in build_inputs:
    check(path in workflow, f"workflow trigger paths include {path}")

check(
    "scripts/fase8/classify-playcompat-build-delta.sh" in workflow,
    "workflow runs the delta classifier",
)
check(
    "scripts/fase8/test-playcompat-build-gate.sh" in workflow,
    "workflow runs this gate regression in preflight",
)
check("github.event.before" in workflow, "workflow reads synchronize before SHA")
check("github.event.after" in workflow, "workflow reads synchronize after SHA")
check(
    "needs.preflight.outputs.build_required == 'true'" in workflow,
    "build-arm64 is conditional on the delta gate",
)
check("workflow_dispatch:" in workflow, "workflow_dispatch remains a force-build trigger")
check("assemblePlaycompatDebug" in workflow, "workflow still assembles playcompatDebug")
check("TERMUX_ABIS: arm64-v8a" in workflow, "workflow stays ARM64 only")
check("docs/" not in workflow.split("paths:")[1].split("workflow_dispatch:")[0],
      "docs paths are not a workflow trigger by themselves")

if failures:
    sys.exit(1)
PY

if [[ "$failures" -ne 0 ]]; then
  echo "$failures playcompat build-gate check(s) failed" >&2
  exit 1
fi
echo "PASS: playcompat synchronize gate"
