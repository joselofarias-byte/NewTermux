#!/data/data/com.termux/files/usr/bin/bash
# Read-only classifier for data that exists in TBM home.tar.gz but is not covered by current stage.
set -u
export LC_ALL=C

TBM="$HOME/.tbm"
ARCHIVE="$TBM/cutover-source/home.tar.gz"
MARKER="$TBM/direct-cutover-current-stage.txt"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$HOME/storage/downloads/NT-TBM-CUTOVER-UNIQUE-MAP-$STAMP.txt"
PYFILE="$HOME/.cache/nt-tbm-cutover-unique-map-$STAMP.py"
trap 'rm -f -- "$PYFILE"' EXIT

status() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*" | tee -a "$OUT"; }

PY="$(command -v python3 || command -v python || true)"
if [ -z "$PY" ]; then echo "ERROR: se necesita python/python3." | tee "$OUT"; exit 2; fi
if [ ! -f "$ARCHIVE" ]; then echo "ERROR: no existe $ARCHIVE" | tee "$OUT"; exit 3; fi

MARKER_TEXT="$(tr -d '\r\000' < "$MARKER" 2>/dev/null || true)"
CURRENT_STAGE_NAME="$(printf '%s\n' "$MARKER_TEXT" | grep -oE 'direct-cutover-stage-[0-9]{8}-[0-9]{6}' | tail -n 1 || true)"
CURRENT_STAGE="$TBM/$CURRENT_STAGE_NAME"
if [ -z "$CURRENT_STAGE_NAME" ] || [ ! -d "$CURRENT_STAGE" ]; then
  echo "ERROR: current stage no valido." | tee "$OUT"; exit 4
fi

cat > "$PYFILE" <<'PY'
import os, sys, tarfile, time, stat, heapq
from collections import defaultdict

archive, stage = sys.argv[1:3]

def norm(name):
    while name.startswith("./"):
        name = name[2:]
    known = "data/data/com.termux/files/home/"
    if name.startswith("/" + known):
        name = name[len(known)+1:]
    elif name.startswith(known):
        name = name[len(known):]
    name = name.lstrip("/")
    parts = [p for p in name.split("/") if p not in ("", ".")]
    if any(p == ".." for p in parts):
        return None
    return "/".join(parts)

def bucket(rel, depth):
    parts = rel.split("/") if rel else ["<root>"]
    return "/".join(parts[:depth]) if parts else "<root>"

top = defaultdict(lambda: [0,0])
two = defaultdict(lambda: [0,0])
kind = defaultdict(lambda: [0,0])
largest = []
stats = defaultdict(int)
last_report = time.time()
next_bytes = 2 * 1024**3

def add_group(rel, size, reason):
    b1 = bucket(rel, 1)
    b2 = bucket(rel, 2)
    top[b1][0] += 1; top[b1][1] += size
    two[b2][0] += 1; two[b2][1] += size
    kind[reason][0] += 1; kind[reason][1] += size
    item = (size, rel, reason)
    if len(largest) < 100:
        heapq.heappush(largest, item)
    elif size > largest[0][0]:
        heapq.heapreplace(largest, item)

with tarfile.open(archive, mode="r|gz") as tf:
    for m in tf:
        stats["members"] += 1
        rel = norm(m.name)
        if rel is None or rel == "":
            continue
        dst = os.path.join(stage, *rel.split("/"))

        if m.isfile():
            stats["regular"] += 1
            stats["regular_bytes"] += m.size
            try:
                st = os.lstat(dst)
            except FileNotFoundError:
                stats["missing_files"] += 1
                stats["missing_bytes"] += m.size
                add_group(rel, m.size, "missing_regular")
                continue
            if not stat.S_ISREG(st.st_mode):
                stats["type_mismatch"] += 1
                stats["type_mismatch_bytes"] += m.size
                add_group(rel, m.size, "type_mismatch")
                continue
            if st.st_size != m.size:
                stats["size_mismatch"] += 1
                stats["size_mismatch_bytes"] += m.size
                add_group(rel, m.size, "size_mismatch")
                continue

        elif m.issym():
            stats["symlink"] += 1
            try:
                if not (os.path.islink(dst) and os.readlink(dst) == m.linkname):
                    stats["symlink_mismatch"] += 1
                    add_group(rel, 0, "symlink_mismatch")
            except OSError:
                stats["symlink_mismatch"] += 1
                add_group(rel, 0, "symlink_mismatch")

        now = time.time()
        if stats["regular_bytes"] >= next_bytes or now - last_report >= 30:
            print(f"[{time.strftime('%H:%M:%S')}] leidos={stats['regular_bytes']/1024**3:.2f} GiB unicos={stats['missing_bytes']/1024**3:.2f} GiB", flush=True)
            last_report = now
            while stats["regular_bytes"] >= next_bytes:
                next_bytes += 2 * 1024**3

def print_table(title, mapping, limit=None):
    print()
    print(title)
    print("BYTES\tGiB\tFILES\tPATH")
    rows = sorted(((v[1], v[0], k) for k,v in mapping.items()), reverse=True)
    if limit is not None:
        rows = rows[:limit]
    for b,c,k in rows:
        print(f"{b}\t{b/1024**3:.3f}\t{c}\t{k}")

print("===== TBM CUTOVER UNIQUE MAP =====")
for k in ("members","regular","regular_bytes","missing_files","missing_bytes","size_mismatch","size_mismatch_bytes","type_mismatch","type_mismatch_bytes","symlink","symlink_mismatch"):
    print(f"{k}={stats[k]}")

print_table("===== UNICOS/MISMATCH POR TOP-LEVEL =====", top)
print_table("===== UNICOS/MISMATCH POR 2 NIVELES (TOP 100) =====", two, 100)
print_table("===== POR MOTIVO =====", kind)

print()
print("===== 100 ARCHIVOS UNICOS/MISMATCH MAS GRANDES =====")
print("BYTES\tGiB\tREASON\tPATH")
for size, rel, reason in sorted(largest, reverse=True):
    print(f"{size}\t{size/1024**3:.3f}\t{reason}\t{rel}")
PY

{
  echo "===== TBM CUTOVER UNIQUE MAP ====="
  echo "Fecha=$(date)"
  echo "Archive=$ARCHIVE"
  echo "CurrentStage=$CURRENT_STAGE"
  echo "Metodo=tarfile streaming + lstat; SOLO LECTURA"
  echo
} | tee "$OUT"

status "Clasificando los datos del TAR que no estan cubiertos por el stage..."
if ! "$PY" "$PYFILE" "$ARCHIVE" "$CURRENT_STAGE" 2>&1 | tee -a "$OUT"; then
  echo "ERROR: fallo el clasificador." | tee -a "$OUT"; exit 5
fi
status "Clasificacion terminada. No se borro nada."
echo "REPORT=$OUT" | tee -a "$OUT"
