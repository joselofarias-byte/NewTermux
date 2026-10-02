#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
javac -d "$test_dir" \
  "$project_root/app/src/main/java/com/newtermux/features/BackupArchivePolicy.java" \
  "$project_root/scripts/tests/BackupArchivePolicyTest.java"
java -cp "$test_dir" BackupArchivePolicyTest
