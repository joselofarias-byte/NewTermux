# NewTermux — Fase 5: PRoot, Debian y Codex

- **Fecha:** 2026-09-19
- **Rama:** `cursor/fase5-proot-debian-codex-2026-09-19-0b82`
- **Base:** punta Fase 4 `7bf66dd934bcb44acef6479cb07afca67732383b` (PR #12), **no** `main` @ `94c5e7f`
- **Regla:** sin secretos, sin TBM, sin desinstalar Play, sin copiar PREFIX viejo, sin éxito HONOR 200

---

## 1. Inventario (esta app)

`rg proot|PRoot|proot-distro` en Java/Gradle/XML de producto: **sin implementación**.

PRoot/Debian/Node/Codex/Git/gh son **paquetes post-bootstrap**, igual que golang en Fase 4. Entran por APT oficial (`packages.termux.dev`) y, para el guest, por `proot-distro` + `deb.debian.org`.

El APK coexist (`com.newtermux.dev`) solo aporta el PREFIX aislado. No hay runtime Debian embebido.

---

## 2. Por qué el VM cloud no corre esto

Evidencia en este agente (2026-09-19):

| Señal | Valor |
| --- | --- |
| `uname` | Linux (Ubuntu host, no Android) |
| `PREFIX` | vacío |
| `/system/bin/linker64` | ausente |
| `pkg` / `proot-distro` | ausentes |
| `go-smoke` / `proot-debian-smoke` | exit 2 `PENDIENTE-HARDWARE` |

`proot` de Ubuntu **no** es `proot-distro` de Termux (ABI, bionic, `/data/data/...`). No se simula.

---

## 3. Plan reproducible (HONOR 200, coexist)

Dentro de **NewTermux Dev** (`assembleCoexistDebug`), APT oficial, **sin** tocar Play:

```text
# 1) Paquetes Termux (lista: scripts/fase5/reproducible-packages.txt)
pkg update
pkg install -y proot-distro git gh

# 2) Guest Debian (instalación + inicio)
proot-distro install debian
proot-distro login debian -- /bin/true

# 3) Validación idempotente
bash scripts/fase5/proot-debian-smoke.sh
# Node opcional:
FASE5_INSTALL_NODE=1 bash scripts/fase5/proot-debian-smoke.sh
```

El smoke cubre:

| Check | Cómo |
| --- | --- |
| install/start Debian | `proot-distro install/login` |
| `/bin/bash` | guest `test -x` + echo |
| execve / shebang | script `#!/bin/bash` |
| loader | `ld-linux*.so*` |
| permisos | tmp escribible; **no** escribir `/data/data/com.termux/files/usr` |
| DNS / TLS / repos | `getent` + `curl -fsSI https://deb.debian.org` (sin `--insecure`) |
| Node | `node -e` si está; si no, `PENDIENTE` o `FASE5_INSTALL_NODE=1` |
| Codex CLI | detecta `codex`; **no** instala ni pide tokens |
| Git | `git init` + commit vacío en tmp |
| `gh` | versión en guest o Termux; `gh auth` es PENDIENTE sin secretos |
| persistencia | marker en `$HOME` guest entre dos `login` |
| HOME / PATH / TMPDIR | no vacíos; PATH incluye `/bin` o `/usr/bin` |
| señales | `SIGTERM` a `sleep` |
| storage | `~/storage/shared` si `termux-setup-storage` ya corrió |

`FASE5_INSTALL_CODEX=1` **falla a propósito**: Codex no es paquete APT y arrastraría registry/credenciales.

---

## 4. Migración futura (documentada, NO ejecutada)

Orden obligatorio. **No copiar el PREFIX de Play completo.**

1. **Instalación limpia** de NewTermux Dev (`com.newtermux.dev`) junto a Play. No “update” de `com.termux`.
2. **Paquetes reproducibles** desde `packages.termux.dev` / `proot-distro` (`scripts/fase5/reproducible-packages.txt`). Anotar versiones (`pkg list-installed`, `proot-distro list`).
3. **HOME / proyectos / config selectivos** (allowlist): p. ej. `~/projects`, `~/.gitconfig` (sin credenciales), snippets de shell. **Prohibido:** `cp -a /data/data/com.termux/files` , rsync de `usr/`, `var/lib/dpkg`, golang Play, `goargs`.
4. **Contenedores controlados:** un guest Debian nuevo (`proot-distro install debian`), no un tarball del chroot de Play.
5. **Validación:** `scripts/fase5/proot-debian-smoke.sh` + Fase 4 `go-smoke.sh`.
6. **Retiro de Play** solo si el usuario lo decide **después** de que esos checks pasen en HONOR 200. Esta fase no lo hace.

---

## 5. Codex / Node

- Preferir **Node + Codex dentro del guest Debian** (aislado del PREFIX Termux).
- Instalar Codex a mano en el dispositivo (docs upstream del CLI). **No** commitear tokens ni `~/.codex`.
- GitHub CLI: `pkg install gh` en Termux o el binario Debian; autenticación interactiva en el teléfono.

---

## 6. Reversión

No mergear. Quitar la rama no toca Play ni guests existentes. Los scripts no borran `proot-distro` distros.

---

## 7. Tabla PASS / FAIL / PENDIENTE

| Ítem | Ámbito | Estado |
| --- | --- | --- |
| PRoot no está en el APK | git | PASS |
| Scripts + lista de paquetes | repo | PASS |
| Smoke aborta en host cloud | cloud | PASS (`exit 2`) |
| TLS host a `packages.termux.dev` / `deb.debian.org` | cloud | PASS (no prueba guest) |
| git/gh del agente | cloud | PASS host-only |
| Debian install/start en Termux | HONOR 200 | **PENDIENTE-HARDWARE** |
| bash / loader / shebang / execve / señales | HONOR 200 | **PENDIENTE-HARDWARE** |
| DNS/TLS/repos del guest | HONOR 200 | **PENDIENTE-HARDWARE** |
| Node / Codex / gh auth | HONOR 200 | **PENDIENTE-HARDWARE** |
| persistencia HOME / storage | HONOR 200 | **PENDIENTE-HARDWARE** |
| Migración PREFIX Play | proceso | **no ejecutada** (solo documentada) |
