#!/data/data/com.termux/files/usr/bin/bash
# Read-only comparison of ~/.tbm/cutover-source/home.tar.gz against the current TBM stage.
# It does NOT delete, move, or extract the archive.
set -u
export LC_ALL=C

TBM="$HOME/.tbm"
CUTOVER="$TBM/cutover-source"
ARCHIVE="$CUTOVER/home.tar.gz"
MANIFEST="$CUTOVER/manifest.json"
MARKER="$TBM/direct-cutover-current-stage.txt"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$HOME/storage/downloads/NT-TBM-CUTOVER-VS-STAGE-$STAMP.txt"
PYFILE="$HOME/.cache/nt-tbm-cutover-vs-stage-$STAMP.py"
RESULT="$HOME/.cache/nt-tbm-cutover-vs-stage-$STAMP.result"
trap 'rm -f -- "$PYFILE" "$RESULT"' EXIT

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

if [ ! -f "$ARCHIVE" ] || [ ! -f "$MANIFEST" ]; then
  echo "ERROR: faltan $ARCHIVE o $MANIFEST" | tee "$OUT"; exit 2
fi
PY="$(command -v python3 || command -v python || true)"
if [ -z "$PY" ]; then
  echo "ERROR: se necesita python/python3 para auditar el tar de forma segura." | tee "$OUT"; exit 3
fi

MARKER_TEXT="$(tr -d '\r\000' < "$MARKER" 2>/dev/null || true)"
CURRENT_STAGE_NAME="$(printf '%s\n' "$MARKER_TEXT" | grep -oE 'direct-cutover-stage-[0-9]{8}-[0-9]{6}' | tail -n 1 || true)"
CURRENT_STAGE="$TBM/$CURRENT_STAGE_NAME"
if [ -z "$CURRENT_STAGE_NAME" ] || [ ! -d "$CURRENT_STAGE" ]; then
  echo "ERROR: current stage no valido." | tee "$OUT"; exit 4
fi

cat > "$PYFILE" <<'PY'
import os, sys, tarfile, time, stat

archive, stage, result_path = sys.argv[1:4]

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

c = {
    "members": 0, "regular": 0, "regular_bytes": 0,
    "regular_match": 0, "regular_match_bytes": 0,
    "missing_regular": 0, "missing_regular_bytes": 0,
    "size_mismatch": 0, "size_mismatch_archive_bytes": 0,
    "type_mismatch": 0, "symlink": 0, "symlink_match": 0, "symlink_mismatch": 0,
    "hardlink": 0, "hardlink_match": 0, "hardlink_mismatch": 0,
    "dirs": 0, "dirs_match": 0, "special": 0, "unsafe": 0
}
examples = {k: [] for k in ("missing_regular","size_mismatch","type_mismatch","symlink_mismatch","hardlink_mismatch","special","unsafe")}
max_examples = 30
last_report = time.time()
next_bytes = 2 * 1024**3

def ex(kind, text):
    if len(examples[kind]) < max_examples:
        examples[kind].append(text)

with tarfile.open(archive, mode="r|gz") as tf:
    for m in tf:
        c["members"] += 1
        rel = norm(m.name)
        if rel is None:
            c["unsafe"] += 1; ex("unsafe", m.name); continue
        if rel == "":
            continue
        dst = os.path.join(stage, *rel.split("/"))

        if m.isfile():
            c["regular"] += 1
            c["regular_bytes"] += m.size
            try:
                st = os.lstat(dst)
            except FileNotFoundError:
                c["missing_regular"] += 1
                c["missing_regular_bytes"] += m.size
                ex("missing_regular", f"{m.size}\t{rel}")
                continue
            if not stat.S_ISREG(st.st_mode):
                c["type_mismatch"] += 1
                ex("type_mismatch", f"archive=file stage=other\t{rel}")
                continue
            if st.st_size != m.size:
                c["size_mismatch"] += 1
                c["size_mismatch_archive_bytes"] += m.size
                ex("size_mismatch", f"archive={m.size} stage={st.st_size}\t{rel}")
                continue
            c["regular_match"] += 1
            c["regular_match_bytes"] += m.size

        elif m.issym():
            c["symlink"] += 1
            try:
                if os.path.islink(dst) and os.readlink(dst) == m.linkname:
                    c["symlink_match"] += 1
                else:
                    c["symlink_mismatch"] += 1
                    got = os.readlink(dst) if os.path.islink(dst) else "<not-symlink>"
                    ex("symlink_mismatch", f"archive={m.linkname!r} stage={got!r}\t{rel}")
            except OSError as e:
                c["symlink_mismatch"] += 1
                ex("symlink_mismatch", f"{e}\t{rel}")

        elif m.islnk():
            c["hardlink"] += 1
            target_rel = norm(m.linkname)
            target = os.path.join(stage, *(target_rel or "").split("/")) if target_rel is not None else ""
            try:
                a = os.stat(dst)
                b = os.stat(target)
                if a.st_ino == b.st_ino and a.st_dev == b.st_dev:
                    c["hardlink_match"] += 1
                else:
                    c["hardlink_mismatch"] += 1
                    ex("hardlink_mismatch", f"archive_target={m.linkname}\t{rel}")
            except OSError as e:
                c["hardlink_mismatch"] += 1
                ex("hardlink_mismatch", f"{e}\t{rel}")

        elif m.isdir():
            c["dirs"] += 1
            if os.path.isdir(dst): c["dirs_match"] += 1

        else:
            c["special"] += 1
            ex("special", f"type={m.type!r}\t{rel}")

        now = time.time()
        if c["regular_bytes"] >= next_bytes or now - last_report >= 30:
            gib = c["regular_bytes"] / 1024**3
            miss = c["missing_regular_bytes"] / 1024**3
            print(f"[{time.strftime('%H:%M:%S')}] miembros={c['members']} archivos={c['regular']} datos_tar={gib:.2f} GiB faltantes={miss:.2f} GiB", flush=True)
            last_report = now
            while c["regular_bytes"] >= next_bytes:
                next_bytes += 2 * 1024**3

covered = 100.0 * c["regular_match_bytes"] / c["regular_bytes"] if c["regular_bytes"] else 100.0
safe_meta = (c["missing_regular"] == 0 and c["size_mismatch"] == 0 and c["type_mismatch"] == 0 and c["symlink_mismatch"] == 0 and c["hardlink_mismatch"] == 0 and c["unsafe"] == 0 and c["special"] == 0)

with open(result_path, "w", encoding="utf-8") as out:
    for k, v in c.items():
        out.write(f"{k}={v}\n")
    out.write(f"regular_byte_coverage={covered:.4f}\n")
    out.write(f"metadata_redundant={'YES' if safe_meta else 'NO'}\n")
    for kind, rows in examples.items():
        out.write(f"\n[{kind}_examples]\n")
        for row in rows:
            out.write(row + "\n")
PY

{
  echo "===== TBM CUTOVER VS CURRENT STAGE ====="
  echo "Fecha=$(date)"
  echo "Metodo=tarfile streaming + lstat; SOLO LECTURA"
  echo "Archive=$ARCHIVE"
  echo "ArchiveBytes=$(stat -c %s "$ARCHIVE" 2>/dev/null || wc -c < "$ARCHIVE")"
  echo "CurrentStage=$CURRENT_STAGE"
  echo
} | tee "$OUT"

status "Leyendo home.tar.gz completo y comparando su inventario con el stage..."
status "No se extrae el archivo. Esto puede demorar bastante."
if ! "$PY" "$PYFILE" "$ARCHIVE" "$CURRENT_STAGE" "$RESULT" 2>&1 | tee -a "$OUT"; then
  echo "ERROR: fallo la lectura/comparacion del tar." | tee -a "$OUT"; exit 5
fi

{
  echo
  echo "===== RESULTADO ====="
  cat "$RESULT"
  echo
  echo "INTERPRETACION:"
  echo "- metadata_redundant=YES solo significa que todas las entradas del tar estan representadas por ruta/tamano/tipo en el stage."
  echo "- Si da NO, cutover-source NO debe borrarse: contiene datos o estructura no cubierta por el stage."
  echo "- Este auditor no compara contenido criptograficamente; eso solo se haria si la metadata da 100% cubierta."
  echo "REPORT=$OUT"
} | tee -a "$OUT"

status "Auditoria terminada. No se borro nada."
