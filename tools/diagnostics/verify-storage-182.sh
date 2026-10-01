#!/data/data/com.termux/files/usr/bin/bash
# Independent cross-check for NewTermux native storage accounting.
# Uses GNU du/df instead of NativeStorageManager's Java lstat scanner.

set -u
export LC_ALL=C

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$HOME/storage/downloads/NT-VERIFY-182-$STAMP.txt"

PREFIX_DIR="${PREFIX:-/data/data/com.termux/files/usr}"
HOME_DIR="$HOME"
APP_ROOT="$(dirname "$(dirname "$HOME_DIR")")"
APP_CACHE="$APP_ROOT/cache"
PROOT_BASE="$PREFIX_DIR/var/lib/proot-distro"

SHARED="$(readlink -f "$HOME_DIR/storage/shared" 2>/dev/null || true)"
if [ -z "$SHARED" ] || [ ! -d "$SHARED" ]; then
  SHARED="/storage/emulated/0"
fi
DOWNLOADS="$SHARED/Download"

have_gnu_du=0
if du --version >/dev/null 2>&1 && du -s -B1 -- "$HOME_DIR" >/dev/null 2>&1; then
  have_gnu_du=1
fi

if [ "$have_gnu_du" -ne 1 ]; then
  echo "ERROR: se necesita GNU du con -B1 (paquete coreutils)." | tee "$OUT"
  echo "Ejecuta: pkg install coreutils" | tee -a "$OUT"
  exit 2
fi

disk_bytes_many() {
  local paths=()
  local p
  for p in "$@"; do
    [ -e "$p" ] && paths+=("$p")
  done
  if [ "${#paths[@]}" -eq 0 ]; then
    echo 0
    return
  fi
  du -s -B1 -- "${paths[@]}" 2>/dev/null | awk '{s += $1} END {printf "%.0f\n", s+0}'
}

sub_nonneg() {
  awk -v a="$1" -v b="$2" 'BEGIN {x=a-b; if (x<0) x=0; printf "%.0f\n", x}'
}

sum_nums() {
  awk 'BEGIN{s=0} {s+=$1} END{printf "%.0f\n",s}'
}

fmt() {
  awk -v b="$1" 'BEGIN {
    if (b >= 1099511627776) printf "%.2f TB", b/1099511627776;
    else if (b >= 1073741824) printf "%.2f GB", b/1073741824;
    else if (b >= 1048576) printf "%.2f MB", b/1048576;
    else if (b >= 1024) printf "%.2f KB", b/1024;
    else printf "%.0f B", b;
  }'
}

# ---- Discover roots exactly like NativeStorageManager ----
MODEL_ROOTS=(
  "$HOME_DIR/llm-models"
  "$HOME_DIR/models"
  "$HOME_DIR/.ollama/models"
  "$HOME_DIR/.cache/huggingface/hub"
  "$HOME_DIR/.cache/llama.cpp"
)

BACKUP_ROOTS=(
  "$HOME_DIR/tbm_backups"
  "$HOME_DIR/.tbm/backups"
  "$DOWNLOADS/NewTermux/Backups"
  "$SHARED/Download-Folders/tbm_backups"
)

TBM_STAGE_ROOTS=()
if [ -d "$HOME_DIR/.tbm" ]; then
  while IFS= read -r -d '' p; do
    TBM_STAGE_ROOTS+=("$p")
  done < <(find "$HOME_DIR/.tbm" -mindepth 1 -maxdepth 1 -type d -name 'direct-cutover-stage-*' -print0 2>/dev/null)
fi

PROOT_ROOTS=()
declare -A PROOT_NAMES=()
if [ -d "$PROOT_BASE/containers" ]; then
  while IFS= read -r -d '' c; do
    if [ -d "$c/rootfs" ]; then
      n="$(basename "$c")"
      PROOT_NAMES["$n"]=1
      PROOT_ROOTS+=("$c")
    fi
  done < <(find "$PROOT_BASE/containers" -mindepth 1 -maxdepth 1 -type d -print0 2>/dev/null)
fi
if [ -d "$PROOT_BASE/installed-rootfs" ]; then
  while IFS= read -r -d '' r; do
    n="$(basename "$r")"
    if [ -z "${PROOT_NAMES[$n]+x}" ]; then
      PROOT_NAMES["$n"]=1
      PROOT_ROOTS+=("$r")
    fi
  done < <(find "$PROOT_BASE/installed-rootfs" -mindepth 1 -maxdepth 1 -type d -print0 2>/dev/null)
fi

# ---- Independent measurements with du ----
HOME_TOTAL="$(disk_bytes_many "$HOME_DIR")"

# Non-overlapping roots removed from HOME by the app.
HOME_EXCLUDES=(
  "$HOME_DIR/llm-models"
  "$HOME_DIR/models"
  "$HOME_DIR/.ollama/models"
  "$HOME_DIR/.cache"
  "$HOME_DIR/tbm_backups"
  "$HOME_DIR/.tbm/backups"
)
for p in "${TBM_STAGE_ROOTS[@]}"; do HOME_EXCLUDES+=("$p"); done
HOME_EXCLUDED="$(disk_bytes_many "${HOME_EXCLUDES[@]}")"
HOME_ACTIVE="$(sub_nonneg "$HOME_TOTAL" "$HOME_EXCLUDED")"

PREFIX_TOTAL="$(disk_bytes_many "$PREFIX_DIR")"
PROOT_BASE_TOTAL="$(disk_bytes_many "$PROOT_BASE")"
PREFIX_TMP="$(disk_bytes_many "$PREFIX_DIR/tmp")"
PREFIX_ACTIVE="$(sub_nonneg "$PREFIX_TOTAL" "$(printf '%s\n%s\n' "$PROOT_BASE_TOTAL" "$PREFIX_TMP" | sum_nums)")"

PROOT_BYTES="$(disk_bytes_many "${PROOT_ROOTS[@]}")"
PROOT_UNCLASSIFIED="$(sub_nonneg "$PROOT_BASE_TOTAL" "$PROOT_BYTES")"

MODEL_BYTES="$(disk_bytes_many "${MODEL_ROOTS[@]}")"

CACHE_RAW="$(disk_bytes_many "$APP_CACHE" "$HOME_DIR/.cache" "$PREFIX_DIR/tmp")"
CACHE_MODEL_BYTES="$(disk_bytes_many "$HOME_DIR/.cache/huggingface/hub" "$HOME_DIR/.cache/llama.cpp")"
CACHE_BYTES="$(sub_nonneg "$CACHE_RAW" "$CACHE_MODEL_BYTES")"

TBM_STAGE_BYTES="$(disk_bytes_many "${TBM_STAGE_ROOTS[@]}")"
BACKUP_BYTES="$(disk_bytes_many "${BACKUP_ROOTS[@]}")"

OUTPUTS_RAW="$(disk_bytes_many "$DOWNLOADS/NewTermux")"
OUTPUT_BACKUPS="$(disk_bytes_many "$DOWNLOADS/NewTermux/Backups")"
OUTPUT_BYTES="$(sub_nonneg "$OUTPUTS_RAW" "$OUTPUT_BACKUPS")"

CLASSIFIED="$(printf '%s\n' "$HOME_ACTIVE" "$PREFIX_ACTIVE" "$PROOT_BYTES" "$MODEL_BYTES" "$CACHE_BYTES" "$TBM_STAGE_BYTES" "$BACKUP_BYTES" "$OUTPUT_BYTES" | sum_nums)"

# Device filesystem usage, same partition basis as StatFs(context.getFilesDir()).
read -r FS_TOTAL FS_USED FS_AVAIL < <(
  df -B1 --output=size,used,avail "$HOME_DIR" 2>/dev/null | awk 'NR==2 {print $1, $2, $3}'
)
FS_TOTAL="${FS_TOTAL:-0}"
FS_USED="${FS_USED:-0}"
FS_AVAIL="${FS_AVAIL:-0}"
RESIDUAL="$(sub_nonneg "$FS_USED" "$CLASSIFIED")"

{
  echo "===== NEWTERMUX STORAGE CROSS-CHECK ====="
  echo "Fecha=$(date)"
  echo "Metodo=GNU du -s -B1 + df -B1 (independiente del scanner Java/lstat)"
  echo "HOME=$HOME_DIR"
  echo "PREFIX=$PREFIX_DIR"
  echo "SHARED=$SHARED"
  echo
  printf '%-34s %18s %18s\n' "COMPONENTE" "BYTES" "FORMATEADO"
  printf '%-34s %18s %18s\n' "----------------------------------" "------------------" "------------------"
  printf '%-34s %18s %18s\n' "HOME activo" "$HOME_ACTIVE" "$(fmt "$HOME_ACTIVE")"
  printf '%-34s %18s %18s\n' "PREFIX activo" "$PREFIX_ACTIVE" "$(fmt "$PREFIX_ACTIVE")"
  printf '%-34s %18s %18s\n' "PRoot detectado" "$PROOT_BYTES" "$(fmt "$PROOT_BYTES")"
  printf '%-34s %18s %18s\n' "Modelos LLM" "$MODEL_BYTES" "$(fmt "$MODEL_BYTES")"
  printf '%-34s %18s %18s\n' "Caches/temporales" "$CACHE_BYTES" "$(fmt "$CACHE_BYTES")"
  printf '%-34s %18s %18s\n' "TBM staging/recuperacion" "$TBM_STAGE_BYTES" "$(fmt "$TBM_STAGE_BYTES")"
  printf '%-34s %18s %18s\n' "Respaldos detectados" "$BACKUP_BYTES" "$(fmt "$BACKUP_BYTES")"
  printf '%-34s %18s %18s\n' "Salidas/registros NewTermux" "$OUTPUT_BYTES" "$(fmt "$OUTPUT_BYTES")"
  echo
  printf '%-34s %18s %18s\n' "TOTAL NEWTERMUX CLASIFICADO" "$CLASSIFIED" "$(fmt "$CLASSIFIED")"
  echo
  printf '%-34s %18s %18s\n' "Dispositivo usado (df)" "$FS_USED" "$(fmt "$FS_USED")"
  printf '%-34s %18s %18s\n' "Dispositivo libre (df)" "$FS_AVAIL" "$(fmt "$FS_AVAIL")"
  printf '%-34s %18s %18s\n' "Dispositivo total (df)" "$FS_TOTAL" "$(fmt "$FS_TOTAL")"
  printf '%-34s %18s %18s\n' "Residual no clasificado" "$RESIDUAL" "$(fmt "$RESIDUAL")"
  echo
  echo "===== CONTROLES DE CONSISTENCIA ====="
  printf '%-34s %18s %18s\n' "HOME total bruto" "$HOME_TOTAL" "$(fmt "$HOME_TOTAL")"
  printf '%-34s %18s %18s\n' "HOME excluido por categorias" "$HOME_EXCLUDED" "$(fmt "$HOME_EXCLUDED")"
  printf '%-34s %18s %18s\n' "PREFIX total bruto" "$PREFIX_TOTAL" "$(fmt "$PREFIX_TOTAL")"
  printf '%-34s %18s %18s\n' "PRoot base bruto" "$PROOT_BASE_TOTAL" "$(fmt "$PROOT_BASE_TOTAL")"
  printf '%-34s %18s %18s\n' "PRoot base no clasificado" "$PROOT_UNCLASSIFIED" "$(fmt "$PROOT_UNCLASSIFIED")"
  printf '%-34s %18s %18s\n' "Cache bruto" "$CACHE_RAW" "$(fmt "$CACHE_RAW")"
  printf '%-34s %18s %18s\n' "Modelos dentro de cache" "$CACHE_MODEL_BYTES" "$(fmt "$CACHE_MODEL_BYTES")"
  echo
  echo "===== ROOTS DETECTADOS ====="
  printf 'PROOT_ROOTS=%s\n' "${#PROOT_ROOTS[@]}"
  for p in "${PROOT_ROOTS[@]}"; do echo "  PROOT: $p"; done
  printf 'TBM_STAGE_ROOTS=%s\n' "${#TBM_STAGE_ROOTS[@]}"
  for p in "${TBM_STAGE_ROOTS[@]}"; do echo "  TBM_STAGE: $p"; done
  echo
  echo "NOTA:"
  echo "- Esta verificacion usa du/df, no el codigo Java de NativeStorageManager."
  echo "- Una diferencia pequena puede deberse a redondeo/metadata y hardlinks entre categorias."
  echo "- Una diferencia grande indica que hay que revisar clasificacion o conteo."
  echo
  echo "REPORT=$OUT"
} | tee "$OUT"

echo
echo "LISTO=$OUT"
