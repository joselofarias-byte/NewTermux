# NewTermux — Session handoff 2026-09-19

Handoff parcial de **Fase 1–5**. Fases 6–7 no implementadas.  
Repo: `https://github.com/joselofarias-byte/NewTermux`  
Mantenedor de esta sesión: agente Cursor sobre el fork JoseloFarias.  
**No hay éxito físico en HONOR 200.**

---

## PRs y ramas

| Pieza | URL / ref | Estado |
| --- | --- | --- |
| Fase 1 auditoría | https://github.com/joselofarias-byte/NewTermux/pull/9 | DRAFT, rama `cursor/audit-fase1-2026-09-19-0b82` |
| Fase 2 port 1.6.2 | https://github.com/joselofarias-byte/NewTermux/pull/10 | DRAFT, rama `cursor/port-banner-1.6.2-conservador-2026-09-19-0b82` @ `2493641` |
| Fase 3 coexistencia | https://github.com/joselofarias-byte/NewTermux/pull/11 | DRAFT @ `1f8b73c` (APK CI `6c31c7c`), **no mergear** |
| Fase 4 Go / repos | https://github.com/joselofarias-byte/NewTermux/pull/12 | DRAFT, rama `cursor/fase4-go-repos-2026-09-19-0b82`, **no mergear** |
| Fase 5 PRoot / Debian / Codex | (este PR) rama `cursor/fase5-proot-debian-codex-2026-09-19-0b82` | DRAFT, **no mergear** |
| Port previo Banner | https://github.com/joselofarias-byte/NewTermux/pull/8 | OPEN; usado como *fuente* del merge, no cerrado ni mergeado |
| `main` | `94c5e7fbd9b945c1e952104a89b4936810c02026` | intacto |

Informes:

- `docs/NEWTERMUX_AUDIT_FASE1_2026-09-19.md`
- `docs/NEWTERMUX_FASE2_PORT_2026-09-19.md`
- `docs/NEWTERMUX_FASE3_COEXIST_2026-09-19.md`
- `docs/NEWTERMUX_FASE4_GO_REPOS_2026-09-19.md`
- `docs/NEWTERMUX_FASE5_PROOT_DEBIAN_CODEX_2026-09-19.md`

---

## 1. Upstream y divergencia (Fase 1)

- Parent GitHub: **`The412Banner/NewTermux`** (`fork=false`). No es `termux/termux-app`.
- Linaje Termux: reimport GPLv3 (primer commit `a0001f1`; ese SHA no existe en Termux oficial).
- Merge-base histórico: `13d57e9`.
- Antes del port: **46 ahead / 25 behind** parent `v1.6.2` `6001da84`.
- Después del port: parent `6001da84` y PR #8 tip `ac4a643` son ancestros de esta rama; `94c5e7f` también.

## 2. Estado inicial (`main`)

- App 1.5.5 (`versionCode` 18), Views, `applicationId=com.termux`.
- CI Build verde en `94c5e7f` (run `34510906662`) con APK `arm64-v8a`.
- Unit tests CI muertos (`master`/`android-10`).
- Quantus native Android: **NO_GO** (script hard-disable). El APK Gradle **no** estaba deshabilitado.

## 3. Cambios Fase 2

Un merge (`0938ec9`) de PR #8 sobre `main`:

- Upstream 1.6.2: Compose, FGS keep-alive, session metadata persist, version 28 / 1.6.2.
- Extra PR #8: security RUN_COMMAND file-result; terminal render/compat.
- Preservado: branding, termcap kP/kN, BigText null, Opportunity Fabric, NO_GO `94c5e7f` (diff vacío vs ese script).
- `applicationId` **no** cambiado.

Commits propios de documentación en esta rama (después del merge):

- cherry-pick auditoría Fase 1
- este handoff + `docs/NEWTERMUX_FASE2_PORT_2026-09-19.md`

## 4. Android 16 (revisión estática)

Ver tabla en el informe Fase 2. Resumen:

- **Portado:** FGS `specialUse` (API 34+), keep-alive, persistencia de tabs (sin revivir PTY).
- **No resuelto:** `targetSdk=28`; PendingIntent flags `0`; 16 KiB ELF no medido; storage scoped no revalidado.
- JNI/PTY/teclado/clipboard/wake-lock: heredados; termcap propio conservado.

## 5. Coexistencia (Fase 3)

Base: punta PR #10 `2493641`, no `main` viejo.

- **Debug CI:** `assembleCoexistDebug` → `applicationId` / `sharedUserId` / PREFIX / authorities = **`com.newtermux.dev`** (no Play).
- **Playcompat:** sigue `com.termux` (sigue chocando con Play; CI **no** lo publica).
- **Demo:** `assemblePlaycompatDemo` → `com.termux.demo` (shell falso). `coexistDemo` filtrado.
- AGP: `applicationId` no va en `buildTypes` (CI `35445634939` FAIL). Flavors `identity`.
- `com.joselofarias.newtermux.debug` **rechazado** (PREFIX ELF 31 chars).
- No se parcheó el zip bootstrap (Fase 4). Guardas abortan si PREFIX apunta a Play.
- CI Build Fase 3: [35445851912](https://github.com/joselofarias-byte/NewTermux/actions/runs/35445851912) **success** @ `6c31c7c`.
- SHA-256 arm64 apt-android-7: `e644c96a0bcd6024539c36088b50dec90a631be43d9cbc3a525099d398a527e4` (`termux-app_v1.6.2+6c31c7c-apt-android-7-github-debug_arm64-v8a.apk`).
- `output-metadata.json`: `applicationId=com.newtermux.dev`, `variantName=coexistDebug`.
- Matriz: `docs/NEWTERMUX_FASE3_COEXIST_2026-09-19.md`.
- HONOR 200: **PENDIENTE**. Fase 4 dejó `scripts/fase4/go-smoke.sh` para el dispositivo.

## 6. Go / goargs (Fase 4)

Informe: `docs/NEWTERMUX_FASE4_GO_REPOS_2026-09-19.md`.

- `goargs` **no** está en el source de la app. NewTermux no hereda el parche.
- Zip `bootstrap-2026.02.12-r1+apt.android-7` aarch64: **sin** `go`/`gofmt`; 6539× PREFIX `/data/data/com.termux/files/usr`; APT `packages-cf.termux.dev`. SHA observado `ea2aeba8…` ≠ hash Gradle `f73ee7d5…` (verify sigue off; no se retargeteó).
- Runtime Go = `pkg install golang` **después** del bootstrap.
- Canal sano: `termux/termux-packages` golang **3:1.27.1** sin `runtime1.go` goargs.
- Canal roto: `termux-play-store/termux-packages` golang **3:1.26.4** + `src-runtime-runtime1.go.patch`.
- Guardas: `patch-bootstrap.sh`, `build-bootstrap.yml`, `build-bootstrap-source.yml` abortan. Receta PREFIX-aware: `scripts/fase4/prepare-prefix-aware-bootstrap.sh` (`com.newtermux.dev`, sin ELF `fil`).
- Checks cloud: `scripts/fase4/host-checks.sh` + workflow `Fase4 bootstrap inventory`.
- Suite Termux: `scripts/fase4/go-smoke.sh` — **PENDIENTE-HARDWARE** (este VM no es Android/Termux).
- Sin wrappers argv permanentes. Listo para Fase 5 (PRoot/Debian/Codex) sin mezclar golang Play.

## 7. PRoot / Debian / Node / Codex (Fase 5)

Informe: `docs/NEWTERMUX_FASE5_PROOT_DEBIAN_CODEX_2026-09-19.md`.

- PRoot **no** está en el APK. Guest Debian = `proot-distro` post-bootstrap + APT `deb.debian.org`.
- Scripts: `scripts/fase5/proot-debian-smoke.sh` (dispositivo), `host-checks.sh` (cloud), `reproducible-packages.txt`.
- Smoke cubre bash, loader, shebang/execve, permisos (sin escribir Play PREFIX), DNS/TLS, git/gh, Node opcional, Codex solo detectado, persistencia HOME, PATH/TMPDIR, SIGTERM, `~/storage/shared`.
- Codex **no** se instala automáticamente (sin secretos).
- Migración futura documentada: limpia → paquetes → HOME selectivo → contenedor nuevo → validar → Play solo si el usuario decide. **Prohibido copiar PREFIX viejo.**
- Este VM: `PENDIENTE-HARDWARE` (`PREFIX` vacío, sin linker Android / `pkg` / `proot-distro`).
- Listo para Fase 6 (CI/build endurecido).

## 8. CI / artefactos (Fase 6 — parcial)

- Workflow `Build` Fase 2: run [35445138266](https://github.com/joselofarias-byte/NewTermux/actions/runs/35445138266) — **success** (`de24c28`).
- Fase 3 intento 1 (`b0540c7`, `applicationId` en BuildType): run [35445634939](https://github.com/joselofarias-byte/NewTermux/actions/runs/35445634939) — **FAIL** AGP. Corregido con flavors + `assembleCoexistDebug`.
- Fase 3 retry (`6c31c7c`): run [35445851912](https://github.com/joselofarias-byte/NewTermux/actions/runs/35445851912) — **success** (apt-android-5 y 7). Artefactos en `apk/coexist/debug`.
- SHA-256 arm64-v8a apt-android-7: `e644c96a0bcd6024539c36088b50dec90a631be43d9cbc3a525099d398a527e4`.
- SHA-256 arm64-v8a apt-android-5: `948937c9086b26b8f5d1e543609619ce459449a3e4800ebd4bb05f20c8052d3f`.
- Unit tests / wrapper validation siguen sin trigger útil.

## 9. Pruebas

| Ámbito | Estado |
| --- | --- |
| Comprobado en git/API | genealogía, merge limpio, preservaciones, IDs |
| Comprobado en CI cloud (Fase 1 `main`) | Build `34510906662` success |
| CI de esta rama Fase 2 | PASS run `35445138266` |
| CI Fase 3 `assembleCoexistDebug` | PASS run `35445851912` @ `6c31c7c` |
| HONOR 200 | PENDIENTE — no declarar éxito |

## 10. Riesgos y reversión

- Instalar APK `com.termux` sobre Termux Play (o al revés) = firma/datos. No hacerlo.
- No revertir NO_GO Quantus (batería/CPU/toolchain).
- PR #8 + Compose es un cambio de UI grande; humo físico pendiente.
- Reversión: no mergear este PR. `main` permanece en `94c5e7f`.

Plan TBM: **no se modificó TBM**. Migración selectiva sigue siendo decisión posterior.

## 11. Recomendación

El APK debug coexistente tiene identidad distinta de Play **en CI**. Go sano depende del APT oficial, no del zip. No mergear. No sustituir Play. No hay prueba física.  
Siguiente: Fase 6 (build/CI endurecido) en PR nuevo. En HONOR 200: `bash scripts/fase5/proot-debian-smoke.sh` dentro de NewTermux Dev. No copiar PREFIX de Play. No desinstalar Play desde esta misión.

## 12. Tabla PASS / FAIL / PENDIENTE (sesión)

| Ítem | Fase | Estado |
| --- | --- | --- |
| Auditoría verificable + PR #9 draft | 1 | PASS |
| Upstream = Banner, no Termux oficial | 1 | PASS |
| Port 1.6.2 desde `main` + PR #8 | 2 | PASS |
| Personalizaciones + NO_GO | 2 | PASS |
| applicationId intacto en el port | 2 | PASS |
| Debug `com.newtermux.dev` ≠ Play | 3 | PASS (código + CI `35445851912`; metadata `com.newtermux.dev`) |
| Inventario goargs / zip / repos | 4 | PASS cloud |
| Go Termux + PREFIX rebuild | 4 | PENDIENTE-HARDWARE / Docker |
| Android 16 review documentada | 2 | PASS (estática) |
| PendingIntent IMMUTABLE | 2 | FAIL (documentado) |
| Unit tests CI | 1–2 | FAIL |
| CI Build esta rama | 2 | PASS (`35445138266`) |
| HONOR 200 | 7 | PENDIENTE |
| Coexistencia identidad debug | 3 | PASS CI / PENDIENTE HONOR 200 |
| Shell/Go sobre PREFIX nuevo | 4 | PENDIENTE-HARDWARE |
| goargs / bootstrap zip | 4 | PASS inventario; rebuild PREFIX **PENDIENTE** |
| PRoot/Debian/Codex scripts + inventario | 5 | PASS cloud / PENDIENTE-HARDWARE guest |
