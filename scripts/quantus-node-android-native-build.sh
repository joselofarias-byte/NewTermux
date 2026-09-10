#!/usr/bin/env bash
set -uo pipefail

STAMP="$(date +%Y%m%d-%H%M%S)"
STATE="$HOME/.local/state/opportunity-fabric"
LOGDIR="$STATE/logs"
mkdir -p "$LOGDIR"
LOG="$LOGDIR/quantus-node-android-native-build-$STAMP.log"
SUMMARY="$STATE/quantus-node-android-native-build-$STAMP.json"
SRC_BASE="$HOME/.local/src"
SRC="$SRC_BASE/quantus-chain-v1.0.1-android"
TARGET_DIR="$SRC/target-termux-android"
BIN="$TARGET_DIR/release/quantus-node"
REPO="https://github.com/Quantus-Network/chain.git"
REF="v1.0.1"
JOBS="${QUANTUS_NODE_BUILD_JOBS:-2}"

mkdir -p "$SRC_BASE"
exec > >(tee -a "$LOG") 2>&1

echo "==> Quantus node native Android/Termux build probe"
echo "==> Source: $REPO @ $REF"
echo "==> Build jobs: $JOBS"
echo "==> Log: $LOG"
echo "==> This only builds the node and runs --version."
echo "==> It does NOT generate keys, create a wallet/preimage, sync, validate, or mine."

if ! command -v pkg >/dev/null 2>&1 || [ -z "${PREFIX:-}" ]; then
  echo "ERROR: native Termux is required." >&2
  exit 2
fi

HOST="$(rustc -vV 2>/dev/null | sed -n 's/^host: //p' || true)"
echo "==> Rust host: ${HOST:-unknown}"
if [ "$HOST" != "aarch64-linux-android" ]; then
  echo "ERROR: expected native Termux Rust host aarch64-linux-android; got '${HOST:-unknown}'." >&2
  exit 3
fi

missing_pkgs=()
for spec in "git:git" "rustc:rust" "cargo:rust" "clang:clang" "clang++:clang" "cmake:cmake" "make:make" "pkg-config:pkg-config" "protoc:protobuf"; do
  cmd="${spec%%:*}"; pkgname="${spec#*:}"
  command -v "$cmd" >/dev/null 2>&1 || missing_pkgs+=("$pkgname")
done
if [ "${#missing_pkgs[@]}" -gt 0 ]; then
  uniq=()
  for p in "${missing_pkgs[@]}"; do
    case " ${uniq[*]:-} " in *" $p "*) ;; *) uniq+=("$p");; esac
  done
  echo "==> Installing missing native build packages: ${uniq[*]}"
  pkg install -y "${uniq[@]}" || exit 10
else
  echo "==> Native build dependencies already present."
fi

# Bindgen consumers generally find Termux libclang here. Export it explicitly
# when present so build scripts do not guess Debian/Ubuntu paths.
LIBCLANG_SO="$(find "$PREFIX/lib" -maxdepth 2 -type f -name 'libclang.so*' -print -quit 2>/dev/null || true)"
if [ -n "$LIBCLANG_SO" ]; then
  export LIBCLANG_PATH="$(dirname "$LIBCLANG_SO")"
  echo "==> LIBCLANG_PATH=$LIBCLANG_PATH"
else
  echo "==> Warning: libclang.so not found under $PREFIX/lib; bindgen may become the first blocker."
fi

export CC=clang
export CXX=clang++
export AR=llvm-ar
export RANLIB=llvm-ranlib
export CARGO_BUILD_JOBS="$JOBS"
export CARGO_TARGET_DIR="$TARGET_DIR"
export PROTOC="$(command -v protoc)"

if [ ! -d "$SRC/.git" ]; then
  echo "==> Cloning exact Quantus release tag $REF..."
  git clone --depth 1 --branch "$REF" --single-branch "$REPO" "$SRC" || exit 11
else
  echo "==> Existing source checkout found: $SRC"
  if [ -n "$(git -C "$SRC" status --porcelain 2>/dev/null)" ]; then
    echo "ERROR: source checkout has local modifications; refusing to overwrite them." >&2
    exit 12
  fi
  git -C "$SRC" fetch --depth 1 origin "refs/tags/$REF:refs/tags/$REF" || exit 13
  git -C "$SRC" checkout --detach "$REF" || exit 13
fi

COMMIT="$(git -C "$SRC" rev-parse HEAD)"
echo "==> Source commit: $COMMIT"
echo "==> Cargo: $(cargo -V)"
echo "==> Rust: $(rustc -V)"
echo "==> Clang: $(clang --version | head -n1)"
echo "==> CMake: $(cmake --version | head -n1)"
echo "==> Protoc: $(protoc --version)"

echo "==> Running reproducible native build: cargo build --release --locked -p quantus-node"
BUILD_LOG="$LOGDIR/quantus-node-android-cargo-$STAMP.log"
set +e
(
  cd "$SRC" &&
  cargo build --release --locked -p quantus-node -j "$JOBS"
) 2>&1 | tee "$BUILD_LOG"
BUILD_RC=${PIPESTATUS[0]}
set -e 2>/dev/null || true
set +e

if [ "$BUILD_RC" -ne 0 ] || [ ! -x "$BIN" ]; then
  echo
  echo "=== QUANTUS NODE NATIVE ANDROID BUILD: BLOCKED ==="
  echo "Cargo return code: $BUILD_RC"
  echo "No runnable node binary was produced."
  echo "The last build output is preserved for diagnosis; no node/sync/mining was started."
  python - "$SUMMARY" "$SRC" "$COMMIT" "$HOST" "$BUILD_RC" "$BUILD_LOG" <<'PY'
import json,sys
p,src,commit,host,rc,log=sys.argv[1:]
json.dump({
  "ok":False,
  "stage":"cargo_build",
  "source":src,
  "commit":commit,
  "rust_host":host,
  "cargo_returncode":int(rc),
  "build_log":log,
  "node_started":False,
  "sync_started":False,
  "mining_started":False
},open(p,"w",encoding="utf-8"),indent=2)
PY
  echo "==> Summary: $SUMMARY"
  echo "==> Build log: $BUILD_LOG"
  echo "==> Full log: $LOG"
  exit 30
fi

echo
 echo "==> Native Android binary produced: $BIN"
file "$BIN" || true

echo "==> Executing only: quantus-node --version"
VERSION_OUT="$($BIN --version 2>&1)"
VERSION_RC=$?
printf '%s\n' "$VERSION_OUT"
if [ "$VERSION_RC" -ne 0 ]; then
  echo "=== QUANTUS NODE NATIVE ANDROID BUILD: BINARY DID NOT RUN ==="
  python - "$SUMMARY" "$SRC" "$COMMIT" "$HOST" "$VERSION_RC" "$BIN" <<'PY'
import json,sys
p,src,commit,host,rc,binary=sys.argv[1:]
json.dump({"ok":False,"stage":"version_exec","source":src,"commit":commit,"rust_host":host,"version_returncode":int(rc),"binary":binary,"node_started":False,"sync_started":False,"mining_started":False},open(p,"w",encoding="utf-8"),indent=2)
PY
  echo "==> Summary: $SUMMARY"
  exit 31
fi

python - "$SUMMARY" "$SRC" "$COMMIT" "$HOST" "$BIN" "$VERSION_OUT" <<'PY'
import json,sys
p,src,commit,host,binary,version=sys.argv[1:]
json.dump({
  "ok":True,
  "stage":"native_android_version_pass",
  "source":src,
  "commit":commit,
  "rust_host":host,
  "binary":binary,
  "version":version,
  "node_started":False,
  "sync_started":False,
  "mining_started":False
},open(p,"w",encoding="utf-8"),indent=2)
PY

echo
echo "=== QUANTUS NODE NATIVE ANDROID/TERMUX: PASS ==="
echo "The official v1.0.1 source built for Android/bionic and the resulting node answered --version."
echo "No node, sync, validator, wallet/preimage, or mining was started."
echo "==> Summary: $SUMMARY"
echo "==> Build log: $BUILD_LOG"
echo "==> Full log: $LOG"
exit 0
