#!/usr/bin/env bash
# Official PREFIX-aware bootstrap rebuild path. Refuses ELF fil/file hacks.
set -euo pipefail

PACKAGE="${TERMUX_APP_PACKAGE:-com.newtermux.dev}"
PACKAGES_REF="${TERMUX_PACKAGES_REF:-}"

refuse() { echo "REFUSED: $*"; exit 2; }

[[ "$PACKAGE" == "com.newtermux.dev" ]] || refuse "only com.newtermux.dev is allowed (got $PACKAGE). Abandoned com.newtermux.app and Play com.termux are not rebuild targets here."
# Official generate-bootstraps.sh rewrites PREFIX from TERMUX_APP_PACKAGE.
# Equal-length ELF surgery is the abandoned fil/file hack — not required here.

if [[ "${PREFIX_AWARE_I_UNDERSTAND:-}" != "1" ]]; then
  cat <<'EOF'
PREFIX-aware rebuild is the official termux-packages generate-bootstraps.sh path:

  git clone https://github.com/termux/termux-packages.git
  git -C termux-packages checkout <pinned-commit-sha>
  export TERMUX_APP_PACKAGE=com.newtermux.dev
  ./scripts/run-docker.sh ./scripts/generate-bootstraps.sh --architectures aarch64

Do NOT:
  - run scripts/patch-bootstrap.sh (fil/file ELF rewrite, abandoned 2026-03-02)
  - download bootstrap from .../releases/latest
  - set TERMUX_APP_PACKAGE=com.newtermux.app
  - copy golang from Termux Play / termux-play-store/termux-packages

Re-run with PREFIX_AWARE_I_UNDERSTAND=1 TERMUX_PACKAGES_REF=<sha> only when
you intend to spend hours in Docker and will publish origin+hash of the zip.
This script does not download or emit an opaque bootstrap binary.
EOF
  exit 3
fi

[[ "$PACKAGES_REF" =~ ^[0-9a-f]{40}$ ]] || refuse "TERMUX_PACKAGES_REF must be a full 40-char commit SHA (got '${PACKAGES_REF:-empty}')"

echo "Ready recipe (not executed here): TERMUX_APP_PACKAGE=$PACKAGE TERMUX_PACKAGES_REF=$PACKAGES_REF"
echo "Next human/CI step is generate-bootstraps.sh inside termux-packages @$PACKAGES_REF"
echo "This does not emit a bootstrap zip."
exit 0
