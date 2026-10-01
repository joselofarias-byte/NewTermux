# NewTermux — Fase 4: Go, goargs y repositorios

- **Fecha:** 2026-09-19
- **Rama:** `cursor/fase4-go-repos-2026-09-19-0b82`
- **Base:** punta Fase 3 `1f8b73cd614fc86204c67dec74b65d2e404f1568` (PR #11), **no** `main` @ `94c5e7f`
- **Regla:** sin binarios opacos, sin ELF `fil`/`file`, sin wrappers permanentes de argv, sin HONOR 200

---

## 1. Dónde vive Go (y dónde no)

| Superficie | ¿Go / goargs? | Evidencia |
| --- | --- | --- |
| Árbol de esta app (`rg goargs` fuera de docs/scripts Fase 4) | **No** | 0 hits de producto. NewTermux **no hereda** el parche |
| Zip bootstrap `termux-packages` `2026.02.12-r1+apt.android-7` | **No golang, no goargs** | 3648 entradas; 0 paths `go`/`gofmt`/`golang`; 0 bytes `goargs` |
| `pkg install golang` desde APT oficial | **Sí, runtime sano** | Receta `termux/termux-packages` `packages/golang` @ master = **3:1.27.1** **sin** `src-runtime-runtime1.go.patch` |
| `pkg install golang` desde Termux Play | **Sí, runtime defectuoso** | Receta `termux-play-store/termux-packages` `packages/golang` = **3:1.26.4** **con** `src-runtime-runtime1.go.patch` |

Go **no entra por el APK**. Entra después, cuando el usuario (o un script) hace `pkg install golang` contra el APT que haya en `$PREFIX/etc/apt/sources.list`.

El zip bootstrap pinneado trae APT oficial:

```
deb https://packages-cf.termux.dev/apt/termux-main/ stable main
# deb https://packages.termux.dev/apt/termux-main/ stable main
```

Ese es el repo que instala paquetes sanos. **No** es el canal Play.

---

## 2. El parche defectuoso `goargs`

Origen (sigue en `main` de Play packages al auditar):

https://github.com/termux-play-store/termux-packages/blob/main/packages/golang/src-runtime-runtime1.go.patch

Hace `adjust = 1` en `runtime.goargs()` cuando `GOOS==android && !CGO`. Construye `os.Args` saltando `argv[1]`. El kernel/`execve` están bien; el runtime miente.

Efectos (issue `termux/termux-packages#29385`, etiqueta `google-play`):

- `go version` imprime help / exit 2
- `go env GOROOT` → `unknown command`
- `gofmt` y cualquier binario `CGO_ENABLED=0` comen el primer argumento
- Wrappers que anteponen un dummy arg **esconden** el runtime roto; **prohibidos** como mitigación permanente

La mención de misión “Play 2026.06.21 / Go 1.27.1” describe ese canal Play + termux-exec. En GitHub **hoy**:

- Play recipe = 1.26.4 **con** goargs
- Oficial = **1.27.1 sin** goargs (DNS/pidfd/futex/netlink only)

**No copiar debs ni patches desde `termux-play-store`.** Un `pkg upgrade` contra repos Play reintroduce el parche aunque el APK NewTermux esté limpio.

### Cómo se reintroduciría (y cómo se corta)

| Vector | ¿Bloqueado en este PR? |
| --- | --- |
| Código Java/Gradle de la app | N/A — nunca tuvo goargs |
| `scripts/patch-bootstrap.sh` | **sí** — `exit 2` inmediato |
| `build-bootstrap.yml` (`releases/latest` + rename a `com.newtermux.app`) | **sí** — job aborta |
| `build-bootstrap-source.yml` (`TERMUX_APP_PACKAGE=com.newtermux.app`) | **sí** — job aborta |
| Instalar golang desde Play en HONOR 200 | **PENDIENTE-HARDWARE** — `scripts/fase4/go-smoke.sh` falla si ve `AndroidSelfExecutable` o PREFIX `com.termux` |
| Wrappers argv “temporales” | no se añadieron |

---

## 3. PREFIX: zip oficial vs coexist `com.newtermux.dev`

Inventario del zip aarch64 (2026-09-19):

| Needle | Count |
| --- | --- |
| `/data/data/com.termux/files/usr` | **6539** |
| `/data/data/com.newtermux.dev/files/usr` | 0 |
| `/data/data/com.newtermux.app/files/usr` | 0 |
| `goargs` / `AndroidSelfExecutable` | 0 |

El APK coexist extrae este zip en `/data/data/com.newtermux.dev/files/usr`. Shebang/RPATH/interpreter **siguen** apuntando a Play. Eso no se “arregla” con un wrapper Go.

### Camino PREFIX-aware (viable, no opaco)

`generate-bootstraps.sh` reconstruye PREFIX desde `TERMUX_APP_PACKAGE`; **no** hace falta igualar la longitud de `com.termux` (10) ni usar el hack ELF. `com.newtermux.dev` tiene 17 caracteres (igual que el experimento abandonado `com.newtermux.app`). Receta:

```text
TERMUX_APP_PACKAGE=com.newtermux.dev
termux-packages/scripts/generate-bootstraps.sh --architectures aarch64
```

dentro de Docker (`run-docker.sh`), con `termux-packages` **pinneado a un commit SHA**.

**No** se ejecutó ese rebuild aquí: tardaría horas, emitiría un zip que habría que publicar con origen+hash, y no hay runner Docker de paquetes en este agente.

Script de receta (no genera binario): `scripts/fase4/prepare-prefix-aware-bootstrap.sh`.

**Detenido a propósito:**

- No se reactivó `com.newtermux.app`
- No se aplicó `patchelf` / `fil`→`files/usr`
- No se cambió el SHA esperado de Gradle al hash observado (ver §4)

---

## 4. Hash del zip: no adivinar

`app/build.gradle` registra `f73ee7d55630ae710c977691cd5157f3e27ac6563cc233298781cad0754acadd` para aarch64, pero la verificación está **comentada**.

Descarga del mismo URL de tag el 2026-09-19:

`ea2aeba8819e517db711f8c32369e89e7c52cee73e07930ff91185e1ab93f4f3` (30 MiB, zip válido).

**Decisión:** no reactivar el compare con el hash viejo (rompería CI). No sustituir el hash en Gradle sin una decisión humana de retarget. `apt-android-5` además usa **el mismo URL android-7** con checksums de `2022.04.28-r6` — inconsistencia preexistente; no se inventó otra URL.

`downloadBootstrap` siempre apunta al tag `2026.02.12-r1+apt.android-7`, **no** a `/releases/latest`.

---

## 5. Pruebas

| Prueba | Dónde | Estado |
| --- | --- | --- |
| Inventario zip (no golang, no goargs, APT oficial, PREFIX `com.termux`) | cloud / `scripts/fase4/inspect-bootstrap.sh` | **PASS** (ejecutado 2026-09-19; CI `Fase4 bootstrap inventory`) |
| `goargs` ausente en producto | cloud / `host-checks.sh` | **PASS** |
| `execve()` argv | host gcc (linux/amd64) | **PASS** host; Termux **PENDIENTE-HARDWARE** |
| `go version/env/help`, hello, `go test`, `gofmt`, `go install` | host Go 1.22 linux/amd64 | **PASS** host-only (no es golang android) |
| Misma suite dentro de NewTermux coexist | HONOR 200 + `scripts/fase4/go-smoke.sh` | **PENDIENTE-HARDWARE** |
| Rebuild PREFIX-aware | Docker termux-packages | **PENDIENTE** (receta lista, no corrida) |

Por qué no se corre Go de Termux en este VM: `uname=Linux`, `PREFIX` vacío, no hay `/system/bin/linker64` ni `pkg`. El `go` del host es `go1.22.2 linux/amd64`, ABI distinto.

Plan reproducible en dispositivo (coexist, **sin** desinstalar Play):

```text
# APK: assembleCoexistDebug (com.newtermux.dev)
# Dentro de NewTermux Dev:
bash scripts/fase4/go-smoke.sh
```

El smoke **aborta** si `PREFIX` es `com.termux` o si `go` contiene `AndroidSelfExecutable`.

---

## 6. Reversión

No mergear. Quitar la rama no toca Play ni datos. Los workflows abandonados quedan a prueba de un `workflow_dispatch` accidental.

---

## 7. Tabla PASS / FAIL / PENDIENTE

| Ítem | Ámbito | Estado |
| --- | --- | --- |
| `goargs` no está en el árbol de app | git | PASS |
| Bootstrap oficial sin golang / sin goargs | cloud (zip) | PASS |
| APT pin = `packages.termux.dev` / `packages-cf` | zip | PASS |
| Play golang trae `runtime1.go` goargs | API GitHub | PASS (documentado; no vendoreado) |
| Oficial golang 1.27.1 sin goargs | API GitHub | PASS (documentado) |
| Hash Gradle vs GitHub | cloud | **FAIL** de pin (drift; verify off; no se adivinó) |
| ELF `fil` / `latest` / `com.newtermux.app` | scripts/CI | PASS (rechazados) |
| Camino PREFIX-aware oficial | receta | PASS (no ejecutado) |
| Wrappers argv permanentes | proceso | PASS (no añadidos) |
| Suite Go Termux | HONOR 200 | **PENDIENTE-HARDWARE** |
| Rebuild zip `com.newtermux.dev` | Docker | **PENDIENTE** |
| PRoot / Debian / Codex | Fase 5 | **PENDIENTE** |
