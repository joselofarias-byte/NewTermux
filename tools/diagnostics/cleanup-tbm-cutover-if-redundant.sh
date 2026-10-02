#!/data/data/com.termux/files/usr/bin/bash
# Delete internal ~/.tbm/cutover-source only if a verified equivalent full migration archive
# exists on shared storage and the current stage carries the same migration manifest.
# Default: dry-run. Use --apply to verify and delete if every gate passes.
set -u
export LC_ALL=C

MODE="${1:-}"
TBM="$HOME/.tbm"
CUTOVER="$TBM/cutover-source"
INTERNAL_MANIFEST="$CUTOVER/manifest.json"
INTERNAL_HOME="$CUTOVER/home.tar.gz"
MARKER="$TBM/direct-cutover-current-stage.txt"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$HOME/storage/downloads/NT-TBM-CUTOVER-GATE-$STAMP.txt"
MANIFEST_OUT="$HOME/storage/downloads/NT-TBM-CUTOVER-CLEANUP-MANIFEST-$STAMP.txt"
WORK="$HOME/.cache/nt-tbm-cutover-gate-$STAMP"
mkdir -p "$WORK"
trap 'rm -rf -- "$WORK"' EXIT

status() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*" | tee -a "$OUT"; }
fmt() {
  awk -v b="$1" 'BEGIN {
    if (b >= 1099511627776) printf "%.2f TB", b/1099511627776;
    else if (b >= 1073741824) printf "%.2f GB", b/1073741824;
    else if (b >= 1048576) printf "%.2f MB", b/1048576;
    else if (b >= 1024) printf "%.2f KB", b/1024;
    else printf "%.0f B", b;
  }'
}
disk_bytes() { [ -e "$1" ] || { echo 0; return; }; du -s -B1 -- "$1" 2>/dev/null | awk 'NR==1 {printf "%.0f\n",$1+0}'; }

if ! command -v tar >/dev/null 2>&1 || ! command -v sha256sum >/dev/null 2>&1; then
  echo "ERROR: se necesitan tar y sha256sum/coreutils." | tee "$OUT"; exit 2
fi
if [ ! -f "$INTERNAL_MANIFEST" ] || [ ! -f "$INTERNAL_HOME" ]; then
  echo "ERROR: cutover-source interno incompleto o ausente." | tee "$OUT"; exit 3
fi

# Resolve current stage.
MARKER_TEXT=""
CURRENT_STAGE_NAME=""
CURRENT_STAGE=""
if [ -f "$MARKER" ]; then
  MARKER_TEXT="$(tr -d '\r\000' < "$MARKER" 2>/dev/null || true)"
  CURRENT_STAGE_NAME="$(printf '%s\n' "$MARKER_TEXT" | grep -oE 'direct-cutover-stage-[0-9]{8}-[0-9]{6}' | tail -n 1 || true)"
  [ -n "$CURRENT_STAGE_NAME" ] && CURRENT_STAGE="$TBM/$CURRENT_STAGE_NAME"
fi
if [ -z "$CURRENT_STAGE" ] || [ ! -d "$CURRENT_STAGE" ]; then
  echo "ERROR: current stage no valido." | tee "$OUT"; exit 10
fi
STAGE_MANIFEST="$CURRENT_STAGE/migration_info/manifest.json"
if [ ! -f "$STAGE_MANIFEST" ]; then
  echo "ERROR: el current stage no contiene migration_info/manifest.json." | tee "$OUT"; exit 11
fi

# Reject while TBM is active.
ACTIVE=()
SELF="$$"; PARENT="$PPID"
for proc in /proc/[0-9]*; do
  [ -r "$proc/cmdline" ] || continue
  pid="$(basename "$proc")"
  [ "$pid" = "$SELF" ] && continue
  [ "$pid" = "$PARENT" ] && continue
  cmd="$(tr '\0' ' ' < "$proc/cmdline" 2>/dev/null || true)"
  [ -n "$cmd" ] || continue
  case "$cmd" in *NT-TBM-CUTOVER-GATE*|*"tee "*NT-TBM-*) continue ;; esac
  case "$cmd" in
    *"/.tbm/"*|*"TBM-Recovery-Master"*|*"tbm-home-"*|*"tbm-restore-"*|*"tbm_migration"*) ACTIVE+=("PID=$pid CMD=$cmd") ;;
  esac
done

# Find candidate full migration tar archives on shared storage.
# Fast paths first, then a bounded discovery pass so moved archives are still found.
ARCHIVES=()
add_archive() {
  local p="$1" x
  [ -f "$p" ] || return 0
  for x in "${ARCHIVES[@]}"; do [ "$x" = "$p" ] && return 0; done
  ARCHIVES+=("$p")
}
shopt -s nullglob
for p in /storage/emulated/0/Download-Folders/tbm_backups/tbm_migration_*.tar; do add_archive "$p"; done
for p in "$HOME"/storage/downloads/tbm_backups/tbm_migration_*.tar; do add_archive "$p"; done
for p in "$HOME"/storage/downloads/tbm_migration_*.tar; do add_archive "$p"; done
shopt -u nullglob

# Historical exact name seen during this migration, if still present.
add_archive "/storage/emulated/0/Download-Folders/tbm_backups/tbm_migration_20260923-001255.tar"

# Broader read-only discovery. Limit depth to avoid crawling Android internals forever.
DISCOVERY_ROOTS=("/storage/emulated/0" "$HOME/storage/shared" "$HOME/storage/downloads")
for root in "${DISCOVERY_ROOTS[@]}"; do
  [ -d "$root" ] || continue
  while IFS= read -r p; do add_archive "$p"; done < <(
    find "$root" -maxdepth 7 -type f \( -name "tbm_migration_*.tar" -o -name "tbm-migration-*.tar" -o -name "*tbm*migration*.tar" \) -print 2>/dev/null
  )
done

# Fallback promised by the diagnostic flow: locate large files by SIZE, not name.
# The historical migration tar was ~46 GiB, so inspect 30-70 GiB files even if moved/renamed.
status "Buscando también archivos grandes por tamaño (30-70 GiB)..."
for root in "${DISCOVERY_ROOTS[@]}"; do
  [ -d "$root" ] || continue
  while IFS= read -r p; do add_archive "$p"; done < <(
    find "$root" -maxdepth 8 -type f -size +32212254720c -size -75161927680c -print 2>/dev/null
  )
done

status "Candidatos de backup externo encontrados: ${#ARCHIVES[@]}"
for p in "${ARCHIVES[@]}"; do status "Candidato: $p ($(fmt "$(disk_bytes "$p")"))"; done

INTERNAL_MANIFEST_SHA="$(sha256sum "$INTERNAL_MANIFEST" | awk '{print $1}')"
STAGE_MANIFEST_SHA="$(sha256sum "$STAGE_MANIFEST" | awk '{print $1}')"
EXPECTED_HOME_SHA="$(grep -A5 '"name"[[:space:]]*:[[:space:]]*"home.tar.gz"' "$INTERNAL_MANIFEST" | grep -m1 '"sha256"' | sed -E 's/.*"sha256"[[:space:]]*:[[:space:]]*"([0-9a-fA-F]+)".*/\1/' | tr 'A-F' 'a-f')"

status "Buscando un backup externo completo con el mismo manifest..."
MATCH_ARCHIVE=""
MATCH_MANIFEST_MEMBER=""
MATCH_HOME_MEMBER=""
EXTERNAL_MANIFEST_SHA=""

for archive in "${ARCHIVES[@]}"; do
  status "Inspeccionando: $archive"
  LIST="$WORK/list-$(printf '%s' "$archive" | sha256sum | cut -c1-12).txt"
  if ! tar -tf "$archive" > "$LIST" 2>/dev/null; then
    status "No se pudo listar; se omite."
    continue
  fi
  manifest_member="$(grep -E '(^|/)manifest\.json$' "$LIST" | head -n 1 || true)"
  home_member="$(grep -E '(^|/)home\.tar\.gz$' "$LIST" | head -n 1 || true)"
  [ -n "$manifest_member" ] || continue
  [ -n "$home_member" ] || continue
  if ! tar -xOf "$archive" "$manifest_member" > "$WORK/external-manifest.json" 2>/dev/null; then continue; fi
  ext_sha="$(sha256sum "$WORK/external-manifest.json" | awk '{print $1}')"
  if [ "$ext_sha" = "$INTERNAL_MANIFEST_SHA" ]; then
    MATCH_ARCHIVE="$archive"
    MATCH_MANIFEST_MEMBER="$manifest_member"
    MATCH_HOME_MEMBER="$home_member"
    EXTERNAL_MANIFEST_SHA="$ext_sha"
    break
  fi
done

GATE_MANIFESTS=NO
[ "$INTERNAL_MANIFEST_SHA" = "$STAGE_MANIFEST_SHA" ] && [ -n "$MATCH_ARCHIVE" ] && GATE_MANIFESTS=YES

{
  echo "===== TBM CUTOVER REDUNDANCY GATE ====="
  echo "Fecha=$(date)"
  echo "Modo=$([ "$MODE" = "--apply" ] && echo APPLY || echo DRY-RUN)"
  echo "CurrentStage=$CURRENT_STAGE"
  echo "ActiveProcesses=${#ACTIVE[@]}"
  if [ "${#ACTIVE[@]}" -gt 0 ]; then printf '%s\n' "${ACTIVE[@]}"; fi
  echo "InternalManifestSha256=$INTERNAL_MANIFEST_SHA"
  echo "StageManifestSha256=$STAGE_MANIFEST_SHA"
  echo "ManifestMatchInternalVsStage=$([ "$INTERNAL_MANIFEST_SHA" = "$STAGE_MANIFEST_SHA" ] && echo YES || echo NO)"
  echo "ExpectedHomeTarSha256=${EXPECTED_HOME_SHA:-NO_RESUELTO}"
  echo "ExternalArchive=${MATCH_ARCHIVE:-NO_RESUELTO}"
  echo "ExternalManifestMember=${MATCH_MANIFEST_MEMBER:-NO_RESUELTO}"
  echo "ExternalHomeMember=${MATCH_HOME_MEMBER:-NO_RESUELTO}"
  echo "ExternalManifestSha256=${EXTERNAL_MANIFEST_SHA:-NO_RESUELTO}"
  echo "ManifestTripleMatch=$GATE_MANIFESTS"
  echo "CutoverBytes=$(disk_bytes "$CUTOVER")"
  echo "CutoverSize=$(fmt "$(disk_bytes "$CUTOVER")")"
} | tee "$OUT"

if [ -z "$EXPECTED_HOME_SHA" ] || [ "$GATE_MANIFESTS" != "YES" ]; then
  echo "GATE_RESULT=REFUSED; faltan coincidencias de manifest/backup externo." | tee -a "$OUT"
  exit 12
fi
if [ "${#ACTIVE[@]}" -ne 0 ]; then
  echo "GATE_RESULT=REFUSED; hay procesos TBM activos." | tee -a "$OUT"
  exit 13
fi

# Strong check: hash both the internal home.tar.gz and the member embedded in the external full backup.
status "Calculando SHA-256 del home.tar.gz interno ($(fmt "$(disk_bytes "$INTERNAL_HOME")")). Puede demorar..."
INTERNAL_HOME_SHA="$(dd if="$INTERNAL_HOME" bs=16M status=progress 2> >(tee -a "$OUT" >&2) | sha256sum | awk '{print $1}')"
status "SHA interno terminado: $INTERNAL_HOME_SHA"

status "Calculando SHA-256 del home.tar.gz dentro del backup externo. Puede demorar..."
EXTERNAL_HOME_SHA="$(tar -xOf "$MATCH_ARCHIVE" "$MATCH_HOME_MEMBER" 2>>"$OUT" | dd bs=16M status=progress 2> >(tee -a "$OUT" >&2) | sha256sum | awk '{print $1}')"
status "SHA externo terminado: $EXTERNAL_HOME_SHA"

ALL_HASHES=NO
if [ "$INTERNAL_HOME_SHA" = "$EXPECTED_HOME_SHA" ] && [ "$EXTERNAL_HOME_SHA" = "$EXPECTED_HOME_SHA" ]; then ALL_HASHES=YES; fi

{
  echo "InternalHomeSha256=$INTERNAL_HOME_SHA"
  echo "ExternalHomeSha256=$EXTERNAL_HOME_SHA"
  echo "ExpectedHomeSha256=$EXPECTED_HOME_SHA"
  echo "HomeTarTripleHashMatch=$ALL_HASHES"
} | tee -a "$OUT"

if [ "$ALL_HASHES" != "YES" ]; then
  echo "GATE_RESULT=REFUSED; home.tar.gz no coincide por SHA-256." | tee -a "$OUT"
  exit 14
fi

if [ "$MODE" != "--apply" ]; then
  echo "GATE_RESULT=PASS; DRY_RUN=YES; no se borro nada." | tee -a "$OUT"
  exit 0
fi

CUTOVER_BEFORE="$(disk_bytes "$CUTOVER")"
{
  echo "===== TBM CUTOVER CLEANUP MANIFEST ====="
  echo "Fecha=$(date)"
  echo "CurrentStage=$CURRENT_STAGE"
  echo "ExternalArchive=$MATCH_ARCHIVE"
  echo "InternalManifestSha256=$INTERNAL_MANIFEST_SHA"
  echo "ExpectedHomeSha256=$EXPECTED_HOME_SHA"
  echo "InternalHomeSha256=$INTERNAL_HOME_SHA"
  echo "ExternalHomeSha256=$EXTERNAL_HOME_SHA"
  echo "Deleted=$CUTOVER"
  echo "DeletedBytes=$CUTOVER_BEFORE"
} > "$MANIFEST_OUT"

status "Todas las compuertas pasaron. Eliminando SOLO $CUTOVER"
rm -rf -- "$CUTOVER"
if [ -e "$CUTOVER" ]; then
  echo "APPLY_FAILED=YES; cutover-source sigue existiendo." | tee -a "$OUT"; exit 15
fi
TBM_AFTER="$(disk_bytes "$TBM")"
{
  echo "APPLY_DONE=YES"
  echo "FreedBytes=$CUTOVER_BEFORE"
  echo "FreedSize=$(fmt "$CUTOVER_BEFORE")"
  echo "TBMAfterBytes=$TBM_AFTER"
  echo "TBMAfterSize=$(fmt "$TBM_AFTER")"
  echo "ExternalArchivePreserved=$MATCH_ARCHIVE"
  echo "CurrentStagePreserved=$CURRENT_STAGE"
  echo "MANIFEST=$MANIFEST_OUT"
  echo "REPORT=$OUT"
} | tee -a "$OUT"
