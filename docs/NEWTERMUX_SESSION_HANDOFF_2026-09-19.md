# NewTermux — Session handoff 2026-09-19

Handoff **completo** de Fase 1–7 (misión). Validación física **preparada**, no ejecutada.  
Repo: `https://github.com/joselofarias-byte/NewTermux`  
Mantenedor de esta sesión: agente Cursor sobre el fork JoseloFarias.  
**No hay éxito físico en HONOR 200.** `main` intacto. TBM no se tocó.

---

## PRs y ramas

| Pieza | URL / ref | Estado |
| --- | --- | --- |
| Fase 1 auditoría | https://github.com/joselofarias-byte/NewTermux/pull/9 | DRAFT, rama `cursor/audit-fase1-2026-09-19-0b82` |
| Fase 2 port 1.6.2 | https://github.com/joselofarias-byte/NewTermux/pull/10 | DRAFT, rama `cursor/port-banner-1.6.2-conservador-2026-09-19-0b82` @ `2493641` |
| Fase 3 coexistencia | https://github.com/joselofarias-byte/NewTermux/pull/11 | DRAFT @ `1f8b73c` (APK CI `6c31c7c`), **no mergear** |
| Fase 4 Go / repos | https://github.com/joselofarias-byte/NewTermux/pull/12 | DRAFT, rama `cursor/fase4-go-repos-2026-09-19-0b82`, **no mergear** |
| Fase 5 PRoot / Debian / Codex | https://github.com/joselofarias-byte/NewTermux/pull/13 | DRAFT, rama `cursor/fase5-proot-debian-codex-2026-09-19-0b82`, **no mergear** |
| Fase 6 Build / CI | https://github.com/joselofarias-byte/NewTermux/pull/14 | DRAFT, rama `cursor/fase6-build-ci-2026-09-19-0b82`, **no mergear** |
| Fase 7 validación física | https://github.com/joselofarias-byte/NewTermux/pull/15 | DRAFT, rama `cursor/fase7-validacion-fisica-2026-09-19-0b82`, **no mergear** |
| Port previo Banner | https://github.com/joselofarias-byte/NewTermux/pull/8 | OPEN; usado como *fuente* del merge, no cerrado ni mergeado |
| `main` | `94c5e7fbd9b945c1e952104a89b4936810c02026` | intacto |

Informes:

- `docs/NEWTERMUX_AUDIT_FASE1_2026-09-19.md`
- `docs/NEWTERMUX_FASE2_PORT_2026-09-19.md`
- `docs/NEWTERMUX_FASE3_COEXIST_2026-09-19.md`
- `docs/NEWTERMUX_FASE4_GO_REPOS_2026-09-19.md`
- `docs/NEWTERMUX_FASE5_PROOT_DEBIAN_CODEX_2026-09-19.md`
- `docs/NEWTERMUX_FASE6_BUILD_CI_2026-09-19.md`
- `docs/NEWTERMUX_FASE7_VALIDACION_FISICA_2026-09-19.md`
- `docs/NEWTERMUX_FASE7_HONOR200_CLOUD.md` (clasificación cloud; no es prueba de teléfono)

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

## 3. Cambios (Fase 2 → 7)

**Fase 2** — merge (`0938ec9`) de PR #8 sobre `main`: Compose, FGS keep-alive, session metadata persist, version 28 / 1.6.2. Extra: security RUN_COMMAND file-result; terminal render/compat. Preservado: branding, termcap kP/kN, BigText null, Opportunity Fabric, NO_GO `94c5e7f`. `applicationId` **no** cambiado en ese PR.

**Fase 3** — flavors `coexist` / `playcompat`. Debug CI = `com.newtermux.dev`. CI no publica playcompat.

**Fase 4** — inventario Go/goargs; golang es APT post-bootstrap; guardas contra zip Play / ELF `fil`.

**Fase 5** — scripts PRoot/Debian/Codex; PRoot no está en el APK; migración documentada sin copiar PREFIX.

**Fase 6** — CI `assembleCoexistDebug` + SHA-256 + secret-scan; tests/wrapper escuchan `main`; debug ≠ Release.

**Fase 7** — un script físico + handoff final. Sin prueba en el teléfono.

## 4. Android 16 (revisión estática)

Ver tabla en el informe Fase 2. Resumen:

- **Portado:** FGS `specialUse` (API 34+), keep-alive, persistencia de tabs (sin revivir PTY).
- **No resuelto:** `targetSdk=28`; PendingIntent flags `0`; 16 KiB ELF no medido; storage scoped no revalidado.
- JNI/PTY/teclado/clipboard/wake-lock: heredados; termcap propio conservado.
- Humo de PTY/teclado en HONOR 200: **PENDIENTE-HARDWARE** (script Fase 7).

## 5. Coexistencia (Fase 3)

Base: punta PR #10 `2493641`, no `main` viejo.

- **Debug CI:** `assembleCoexistDebug` → `applicationId` / `sharedUserId` / PREFIX / authorities = **`com.newtermux.dev`** (no Play).
- **Playcompat:** sigue `com.termux` (sigue chocando con Play; CI **no** lo publica).
- **Demo:** `assemblePlaycompatDemo` → `com.termux.demo` (shell falso). `coexistDemo` filtrado.
- AGP: `applicationId` no va en `buildTypes` (CI `35445634939` FAIL). Flavors `identity`.
- `com.joselofarias.newtermux.debug` **rechazado** (PREFIX ELF 31 chars).
- Guardas: si el proceso no es `com.termux` y PREFIX apunta a Play, se aborta.
- CI Build Fase 3: [35445851912](https://github.com/joselofarias-byte/NewTermux/actions/runs/35445851912) **success** @ `6c31c7c`.
- SHA-256 histórico Fase 3 arm64-7: `e644c96a0bcd6024539c36088b50dec90a631be43d9cbc3a525099d398a527e4` (referencia; **no** es el APK de Fase 7).
- HONOR 200: **PENDIENTE**. Instalar solo el APK Fase 6 canónico.

## 6. Go / goargs (Fase 4)

Informe: `docs/NEWTERMUX_FASE4_GO_REPOS_2026-09-19.md`.

- `goargs` **no** está en el source de la app.
- Zip `bootstrap-2026.02.12-r1+apt.android-7` aarch64: **sin** `go`/`gofmt`; 6539× PREFIX `/data/data/com.termux/files/usr`; APT `packages-cf.termux.dev`. SHA zip observado `ea2aeba8…` ≠ hash Gradle `f73ee7d5…` (verify sigue off).
- Runtime Go = `pkg install golang` **después** del bootstrap, canal `termux/termux-packages` **3:1.27.1** sin `runtime1.go`.
- Canal roto: Play `3:1.26.4` + `src-runtime-runtime1.go.patch` (`AndroidSelfExecutable`).
- Guardas: `patch-bootstrap.sh` y workflows bootstrap abortan.
- Suite: `scripts/fase4/go-smoke.sh` + check Go en `scripts/fase7/honor200-validate.sh` — **PENDIENTE-HARDWARE**.

## 7. PRoot / Debian (Fase 5)

Informe: `docs/NEWTERMUX_FASE5_PROOT_DEBIAN_CODEX_2026-09-19.md`.

- PRoot **no** está en el APK. Guest = `proot-distro` + APT `deb.debian.org`.
- Scripts Fase 5 + cobertura en el script Fase 7 (login, bash, loader).
- Migración futura: limpia → paquetes → HOME selectivo → contenedor nuevo → validar → Play solo si el usuario decide. **Prohibido copiar PREFIX viejo.**
- Este VM: `PENDIENTE-HARDWARE`.

## 8. Node / Codex / Git / gh

- Git y gh: paquetes post-bootstrap (`pkg install git gh`) o Debian guest. El `git`/`gh` del agente cloud **no** prueban el PREFIX nuevo.
- Node: preferir `nodejs` en el guest Debian (Codex aislado del PREFIX Termux).
- Codex CLI: **solo detección**. No se instala desde scripts (registry + credenciales). No commitear `~/.codex` ni API keys.
- `gh auth`: interactivo en el teléfono. El script oculta tokens y marca credenciales como **BLOQUEADO**.

## 9. CI, artefactos y SHA-256

Informe: `docs/NEWTERMUX_FASE6_BUILD_CI_2026-09-19.md`.

- `Build` produce **solo** `assembleCoexistDebug` + SHA-256 + `DEBUG-NOT-RELEASE.txt`. No playcompat. No Release.
- Tests/wrapper: trigger `master`/`android-10` → **`main`**.
- **APK canónico HONOR (Fase 6/7):**

| Campo | Valor |
| --- | --- |
| Archivo | `termux-app_v1.6.2+f3eb365-apt-android-7-github-debug_arm64-v8a.apk` |
| SHA-256 | `2345efa03c8677778fb418f5acc4bfd2a69e9bf383932e0a4d786247dcbd11e4` |
| Commit | `f3eb365` |
| Identidad | `com.newtermux.dev` / `coexistDebug` |
| Job | [105906757364](https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701028/job/105906757364) en run [35446701028](https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701028) |

No inventar otro hash. Docs posteriores (incl. este PR) no sustituyen `f3eb365`.

- `apt-android-5` @ `f3eb365`: FAIL Maven 403 (flake). Matriz completa verde: [35446689522](https://github.com/joselofarias-byte/NewTermux/actions/runs/35446689522) @ `50942b8`.
- Guard [35446701043](https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701043), tests [35446701056](https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701056), wrapper [35446701106](https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701106): success.

## 10. Pruebas

| Ámbito | Estado |
| --- | --- |
| Comprobado en git/API | genealogía, merge limpio, IDs, guardas |
| Comprobado en CI cloud | Fases 2–6 verdes en artefactos coexist; Fase 7 clasifica PENDIENTE-HARDWARE |
| CI Fase 6 arm64-7 | PASS job `105906757364` @ `f3eb365` hash `2345efa0…` |
| HONOR 200 | **PENDIENTE** — no declarar éxito |
| Bloqueado | hardware (este VM), credenciales (gh/Codex), storage Downloads |

## 11. Riesgos, reversión y plan TBM

**Riesgos**

- Instalar un APK `com.termux` (playcompat o Release) sobre Termux Play = firma/datos. Solo coexist debug.
- Bootstrap zip sigue compilado para PREFIX `com.termux` (6539 hits). Shebang/RPATH pueden apuntar a Play hasta un rebuild PREFIX-aware (no hecho).
- `targetSdk=28`, PendingIntent `0`, 16 KiB ELF no medido.
- Compose/UI grande sin humo físico.
- `apt-android-5` flaky (Maven 403); no es el artefacto HONOR.
- Quantus NO_GO: no revertir.

**Reversión**

- No mergear ningún PR de esta misión. `main` = `94c5e7f`.
- Quitar ramas no toca Play ni datos.
- El script Fase 7 no borra distros `proot-distro` ni paquetes salvo que el usuario ponga `FASE7_INSTALL=1` (solo instala, no desinstala).

**Plan TBM (sin modificar TBM)**

TBM-Recovery-Master **no se editó**. Migración selectiva (si alguna vez): NewTermux Dev limpio → paquetes oficiales → HOME allowlist sin credenciales → Debian nuevo → validar con el script Fase 7 → Play se retira solo si el usuario lo decide. **Prohibido** `cp -a` / rsync del PREFIX Play. Esta misión no ejecuta ese plan.

## 12. Archivos y commits (Fase 7)

Archivos de esta fase (más el handoff):

| Archivo | Rol |
| --- | --- |
| `scripts/fase7/honor200-validate.sh` | script único idempotente |
| `.github/workflows/fase7_validate.yml` | CI cloud: exit 2 + tres secciones + hash canónico |
| `docs/NEWTERMUX_FASE7_VALIDACION_FISICA_2026-09-19.md` | informe Fase 7 |
| `docs/NEWTERMUX_FASE7_HONOR200_CLOUD.md` | reporte generado en cloud |
| `docs/NEWTERMUX_SESSION_HANDOFF_2026-09-19.md` | este handoff |

Cadena de commits propios recientes (ramas de misión; no `main`):

- Fase 6 código: `50942b8` / docs+firma: `f3eb365` / evidencia CI: `6f83bcc`
- Fase 7: commits de esta rama (script + docs + PR)

Lista completa de producto tocado en Fases 3–6: `app/build.gradle` (flavors), `CoexistIdentity.java`, workflows `debug_build.yml` / `run_tests.yml` / `gradle-wrapper-validation.yml` / `fase6_guard.yml` / release abort, `scripts/fase4/*`, `scripts/fase5/*`, `scripts/fase6/*`.

## 13. Tabla PASS / FAIL / PENDIENTE-HARDWARE (sesión)

| Ítem | Fase | Estado |
| --- | --- | --- |
| Auditoría verificable + PR #9 draft | 1 | PASS |
| Upstream = Banner, no Termux oficial | 1 | PASS |
| Port 1.6.2 desde `main` + PR #8 | 2 | PASS |
| Personalizaciones + NO_GO | 2 | PASS |
| applicationId intacto en el port | 2 | PASS |
| Android 16 review documentada | 2 | PASS (estática) |
| PendingIntent IMMUTABLE | 2 | FAIL (documentado) |
| Debug `com.newtermux.dev` ≠ Play | 3 | PASS CI / PENDIENTE HONOR |
| Inventario goargs / zip / repos | 4 | PASS cloud |
| Go Termux + PREFIX rebuild | 4 | PENDIENTE-HARDWARE / Docker |
| PRoot/Debian/Codex scripts | 5 | PASS cloud / PENDIENTE-HARDWARE guest |
| CI coexist debug + guards + SHA `2345efa0…` | 6 | PASS android-7; FAIL android-5 Maven 403 |
| Unit tests CI en `main` | 6 | PASS `35446701056` |
| Script físico + reporte 3 bloques | 7 | PASS cloud (exit 2); workflow [35447245754](https://github.com/joselofarias-byte/NewTermux/actions/runs/35447245754) |
| Fase4 inventory allowlist `scripts/fase7` | 7 | FAIL inicial `35447245674` (mención `goargs`); corregido |
| Identidad / ABI / shell / PTY en teléfono | 7 | PENDIENTE-HARDWARE |
| Paquetes / Go / PRoot / Node / Git / gh / Codex en teléfono | 7 | PENDIENTE-HARDWARE |
| gh auth / Codex keys | 7 | BLOQUEADO (credenciales) |
| HONOR 200 éxito físico | 7 | **no declarado** |
| TBM modificado | — | PASS (no tocado) |
| `main` intacto | — | PASS `94c5e7f` |

## 14. Recomendación y próximos pasos

**Preparación para pruebas físicas: SÍ (fundada).** Hay APK coexist debug arm64 con hash, identidad distinta de Play, CI que no publica Release, y un script no destructivo que escribe un Markdown clasificado. El usuario puede, cuando quiera:

1. Descargar el APK canónico del run `35446701028` (no un Release).
2. Verificar SHA-256 `2345efa03c8677778fb418f5acc4bfd2a69e9bf383932e0a4d786247dcbd11e4`.
3. Instalar **lado a lado** con Termux Play. No desinstalar Play. No migrar datos.
4. En NewTermux Dev: `termux-setup-storage` (opcional) y `bash scripts/fase7/honor200-validate.sh`.
5. Leer `~/storage/downloads/NEWTERMUX_FASE7_HONOR200.md`.

**No** mergear. **No** Release. **No** force-push. **No** declarar éxito hasta que ese reporte (en el teléfono) lo diga. Rebuild PREFIX-aware y 16 KiB/PendingIntent siguen siendo trabajo futuro, fuera de esta misión.
