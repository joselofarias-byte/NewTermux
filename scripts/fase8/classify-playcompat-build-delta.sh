#!/usr/bin/env bash
# Decide whether this GitHub event must run the ARM64 playcompat assemble.
#
# pull_request.paths is the accumulated PR diff, so a docs-only synchronize
# still starts the workflow once any build input is already on the PR.
# This classifier looks at the newly pushed delta instead:
#   synchronize -> git diff <github.event.before> <github.event.after>
#   opened      -> git diff <base>...<head> (the PR as first introduced)
#   reopened    -> no new delta; do not rebuild
#   workflow_dispatch -> explicit force-build
#
# Commit messages are ignored. An unresolvable synchronize range fails open
# (build) so a missing SHA cannot hide an APK change.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"

exec python3 - "$@" <<'PY'
import argparse
import os
import subprocess
import sys

# Files that change the APK or the proof that the APK is the stock
# com.termux bootstrap. Gate scripts are intentionally absent: editing the
# classifier must not assemble another APK.
BUILD_INPUTS = (
    ".github/workflows/playcompat_migration_build.yml",
    "app/build.gradle",
    "app/src/main/java/com/termux/app/TermuxInstaller.java",
    "termux-shared/src/main/java/com/termux/shared/termux/shell/**",
    "scripts/fase8/assert-playcompat-stock-bootstrap.sh",
)

ZERO_SHA = "0" * 40


def matches(path, pattern):
    if pattern.endswith("/**"):
        prefix = pattern[:-3]
        return path == prefix or path.startswith(prefix + "/")
    return path == pattern


def is_build_input(path):
    return any(matches(path, pattern) for pattern in BUILD_INPUTS)


def blank_sha(value):
    if value is None:
        return True
    text = str(value).strip()
    return text == "" or set(text) <= {"0"}


def have_commit(sha):
    found = subprocess.call(
        ["git", "cat-file", "-e", f"{sha}^{{commit}}"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    if found == 0:
        return True
    subprocess.call(
        ["git", "fetch", "--no-tags", "origin", sha],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    found = subprocess.call(
        ["git", "cat-file", "-e", f"{sha}^{{commit}}"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return found == 0


def diff_names(left, right, three_dot):
    # --no-renames lists both the old and new path so a deleted build
    # input is still visible.
    spec = ["git", "diff", "--name-only", "--no-renames", "-z"]
    if three_dot:
        spec.append(f"{left}...{right}")
    else:
        spec.extend([left, right])
    out = subprocess.check_output(spec)
    return [name.decode("utf-8") for name in out.split(b"\0") if name]


def resolve_files(event, action, before, after, base, head):
    """Return (files, resolved). resolved False means the delta is unknown."""
    if event == "workflow_dispatch":
        return [], True
    if event == "pull_request" and action == "reopened":
        return [], True
    if event == "pull_request" and action == "synchronize":
        if blank_sha(before) or blank_sha(after):
            return [], False
        if not have_commit(before) or not have_commit(after):
            return [], False
        return diff_names(before, after, three_dot=False), True
    if event == "pull_request" and action == "opened":
        if blank_sha(base) or blank_sha(head):
            return [], False
        if not have_commit(base) or not have_commit(head):
            return [], False
        return diff_names(base, head, three_dot=True), True
    return [], True


def classify(event, action, files, resolved):
    if event == "workflow_dispatch":
        return True, "workflow_dispatch"
    if event != "pull_request":
        return True, "unclassified_event"
    if action == "reopened":
        return False, "reopened_no_new_delta"
    if action == "synchronize":
        if not resolved:
            return True, "unresolved_delta"
        if any(is_build_input(path) for path in files):
            return True, "synchronize_build_inputs"
        return False, "synchronize_no_build_inputs"
    if action == "opened":
        if not resolved:
            return True, "unresolved_delta"
        if any(is_build_input(path) for path in files):
            return True, "opened_build_inputs"
        return False, "opened_no_build_inputs"
    return True, "unclassified_event"


def emit(required, reason, files):
    required_text = "true" if required else "false"
    print(f"build_required={required_text}")
    print(f"reason={reason}")
    for path in files:
        print(f"changed={path}")
    decision = "run build-arm64" if required else "skip build-arm64"
    print(f"decision={decision}")
    output_path = os.environ.get("GITHUB_OUTPUT")
    if output_path:
        with open(output_path, "a", encoding="utf-8") as stream:
            stream.write(f"build_required={required_text}\n")
            stream.write(f"reason={reason}\n")


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--from-git", action="store_true")
    parser.add_argument("--event", default=os.environ.get("CLASSIFY_EVENT_NAME", ""))
    parser.add_argument("--action", default=os.environ.get("CLASSIFY_EVENT_ACTION", ""))
    parser.add_argument("--before", default=os.environ.get("CLASSIFY_BEFORE", ""))
    parser.add_argument("--after", default=os.environ.get("CLASSIFY_AFTER", ""))
    parser.add_argument("--base", default=os.environ.get("CLASSIFY_BASE_SHA", ""))
    parser.add_argument("--head", default=os.environ.get("CLASSIFY_HEAD_SHA", ""))
    parser.add_argument("--file", action="append", default=[])
    parser.add_argument(
        "--commit-message",
        default="",
        help="Accepted and ignored. The gate never reads commit messages.",
    )
    args = parser.parse_args(argv)
    del args.commit_message

    event = args.event.strip()
    action = args.action.strip()
    if not event:
        print("FAIL: missing --event", file=sys.stderr)
        return 2

    if args.from_git:
        files, resolved = resolve_files(
            event, action, args.before.strip(), args.after.strip(),
            args.base.strip(), args.head.strip(),
        )
    else:
        files, resolved = list(args.file), True

    required, reason = classify(event, action, files, resolved)
    emit(required, reason, files)
    return 0


sys.exit(main(sys.argv[1:]))
PY
