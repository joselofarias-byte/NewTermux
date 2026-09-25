#!/usr/bin/env bash
# Lock the playcompat migration invariant without assembling an APK.
# assemblePlaycompatDebug must keep the stock com.termux bootstrap so an
# in-place Play install can see the existing PREFIX. Coexist builds are the
# only ones that refuse that bootstrap and rewrite stock paths.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"

python3 - <<'PY'
import pathlib
import re
import sys

gradle = pathlib.Path("app/build.gradle").read_text(encoding="utf-8")
workflow = pathlib.Path(".github/workflows/playcompat_migration_build.yml").read_text(encoding="utf-8")
installer = pathlib.Path("app/src/main/java/com/termux/app/TermuxInstaller.java").read_text(encoding="utf-8")
shell_utils = pathlib.Path(
    "termux-shared/src/main/java/com/termux/shared/termux/shell/TermuxShellUtils.java"
).read_text(encoding="utf-8")
shell_env = pathlib.Path(
    "termux-shared/src/main/java/com/termux/shared/termux/shell/command/environment/TermuxShellEnvironment.java"
).read_text(encoding="utf-8")

failures = []

def check(ok, message):
    print(("PASS: " if ok else "FAIL: ") + message)
    if not ok:
        failures.append(message)

def flavor_id(name):
    match = re.search(
        rf"{name}\s*\{{[^}}]*applicationId\s+\"([^\"]+)\"",
        gradle,
        re.S,
    )
    return match.group(1) if match else None

check(flavor_id("playcompat") == "com.termux", "playcompat applicationId is com.termux")
check(flavor_id("coexist") == "com.newtermux.dev", "coexist applicationId is com.newtermux.dev")

guard = re.search(
    r"def coexistRequested = gradle\.startParameter\.taskNames\.any \{ (.+) \}",
    gradle,
)
predicate = guard.group(1).strip() if guard else ""
check(predicate == 'it.contains("Coexist")', f"stock-bootstrap refusal is Coexist-only ({predicate or 'missing'})")

# Mirror the Groovy predicate exactly. Playcompat task names must stay false.
def is_coexist_task(task):
    return "Coexist" in task

task_cases = {
    "assemblePlaycompatDebug": False,
    ":app:assemblePlaycompatDebug": False,
    "assemblePlaycompatDemo": False,
    "assemblePlaycompatRelease": False,
    "assembleDebug": False,
    "assembleCoexistDebug": True,
    ":app:assembleCoexistDebug": True,
}
for task, expected in task_cases.items():
    check(
        is_coexist_task(task) is expected,
        f"task {task} coexist-refusal={expected}",
    )

check("NEWTERMUX_BOOTSTRAP_DIR" in gradle, "coexist path still requires NEWTERMUX_BOOTSTRAP_DIR")
check(
    "Refusing coexist build with stock Termux bootstrap." in gradle,
    "coexist builds still refuse the stock bootstrap",
)

# Runtime repairs are no-ops for the Play package. They must not rewrite
# /data/data/com.termux when the installed identity is com.termux.
repair = re.search(
    r"void repairStockPrefixReferencesIfNeeded\(\) \{(.*?)\n    \}",
    installer,
    re.S,
)
repair_body = repair.group(1) if repair else ""
check(bool(repair), "repairStockPrefixReferencesIfNeeded exists")
check(
    'if ("com.termux".equals(TermuxConstants.TERMUX_PACKAGE_NAME))' in repair_body
    and "return;" in repair_body,
    "prefix repair returns immediately for com.termux",
)
check(
    '!"com.termux".equals(TermuxConstants.TERMUX_PACKAGE_NAME)' in shell_utils,
    "login shebang rewrite is skipped for com.termux",
)
check(
    '!"com.termux".equals(TermuxConstants.TERMUX_PACKAGE_NAME)' in shell_env,
    "LD_LIBRARY_PATH override is skipped for com.termux",
)

check("assemblePlaycompatDebug" in workflow, "workflow assembles playcompatDebug")
check("assembleCoexist" not in workflow, "workflow does not assemble coexist")
check("assemblePlaycompatRelease" not in workflow, "workflow does not assemble playcompatRelease")
check("assembleRelease" not in workflow, "workflow does not assemble Release")
check("NEWTERMUX_BOOTSTRAP_DIR" not in workflow, "workflow does not inject a coexist bootstrap")
check('TERMUX_ABIS: arm64-v8a' in workflow, "workflow builds ARM64 only")
check('applicationId") == "com.termux"' in workflow, "workflow asserts applicationId com.termux")
check('variantName") == "playcompatDebug"' in workflow, "workflow asserts variant playcompatDebug")
check(
    '#!/data/data/com.termux/files/usr/bin/sh' in workflow,
    "workflow asserts the stock Play login shebang",
)
check("lib/libandroid-support.so" in workflow, "workflow asserts libandroid-support.so")
check("com.newtermux.dev" not in workflow, "workflow does not require the coexist package id")

if failures:
    print(f"\n{len(failures)} playcompat invariant(s) failed", file=sys.stderr)
    sys.exit(1)
print("PASS: playcompat stays on the stock com.termux bootstrap")
PY
