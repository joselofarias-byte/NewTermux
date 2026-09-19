# NewTermux — Auditoría Fase 1 (verificable)

- **Fecha de captura:** 2026-09-19T13:09:26Z
- **Alcance:** solo `https://github.com/joselofarias-byte/NewTermux`
- **Rama de este informe:** `cursor/audit-fase1-2026-09-19-0b82`
- **HEAD auditado (main al inicio):** `94c5e7fbd9b945c1e952104a89b4936810c02026`
- **Regla:** cero invención de estado. Si un dato no se midió, queda `PENDIENTE`.
- **Fuera de alcance (no tocados):** PR Terminal, `pr-android16-test`, TBM-Recovery-Master, Termux Play, merge, release, force-push, visibilidad, secrets.

Este documento no declara éxito físico en HONOR 200. No se compiló APK en este entorno; el estado de build se toma de CI de GitHub ya ejecutada.

---

## 1. Método y comandos usados

Entorno local:

```bash
pwd
git rev-parse --is-inside-work-tree
git remote -v
git status -sb
git log -1 --format=fuller
git symbolic-ref refs/remotes/origin/HEAD
git checkout -b cursor/audit-fase1-2026-09-19-0b82
```

GitHub (API / `gh`, solo lectura):

```bash
gh repo view joselofarias-byte/NewTermux --json name,nameWithOwner,description,isFork,isPrivate,parent,defaultBranchRef,url,createdAt,updatedAt,pushedAt,licenseInfo,visibility
gh api repos/joselofarias-byte/NewTermux --jq '{full_name,fork,parent:.parent.full_name,source:.source.full_name,default_branch,license:.license.spdx_id}'
gh api repos/joselofarias-byte/NewTermux/branches --paginate
gh api repos/joselofarias-byte/NewTermux/tags --paginate
gh api repos/joselofarias-byte/NewTermux/releases --paginate
gh pr list --repo joselofarias-byte/NewTermux --state all --limit 50
gh api repos/The412Banner/NewTermux --jq '{full_name,fork,parent:.parent.full_name,source:.source.full_name,default_branch}'
gh api repos/The412Banner/NewTermux/commits/main
gh api repos/The412Banner/NewTermux/compare/6001da84bb2775508e3e3346084958eb7b143480...joselofarias-byte:main
gh api repos/joselofarias-byte/NewTermux/compare/main...The412Banner:main
gh api repos/termux/termux-app --jq '{full_name,default_branch,license:.license.spdx_id}'
gh api repos/termux/termux-app/commits/a0001f10c57266ba3b0d973a9daa6357c05dbe24
gh run list --repo joselofarias-byte/NewTermux --limit 30
gh run view 34510906662 --repo joselofarias-byte/NewTermux
gh api repos/joselofarias-byte/NewTermux/actions/runs/34510906662/artifacts
```

Historia y divergencia local:

```bash
git log --oneline -40
git rev-list --max-parents=0 HEAD
git log -1 --format=fuller a0001f10c57266ba3b0d973a9daa6357c05dbe24
git log -1 --format=fuller 13d57e9fe19cc2a4c7498084887123140cd5750a
git merge-base --is-ancestor 13d57e9fe19cc2a4c7498084887123140cd5750a HEAD
git log --oneline 13d57e9fe19cc2a4c7498084887123140cd5750a..HEAD
git log --oneline 13d57e9fe19cc2a4c7498084887123140cd5750a..6001da84bb2775508e3e3346084958eb7b143480
git rev-list --count 13d57e9fe19cc2a4c7498084887123140cd5750a..HEAD
git rev-list --count 13d57e9fe19cc2a4c7498084887123140cd5750a..6001da84bb2775508e3e3346084958eb7b143480
git diff --name-status 13d57e9fe19cc2a4c7498084887123140cd5750a HEAD
```

Búsquedas de inventario (sin implementación):

```bash
rg -n 'applicationId|namespace|com\.newtermux|com\.termux' --glob '*.{gradle,kts,xml,properties,md}'
rg -n 'goargs' .
rg -n 'proot|PRoot|proot-distro' --glob '*.{md,java,sh,yml,xml}'
rg -n 'TERMUX_PACKAGE_NAME|TERMUX_PREFIX_DIR_PATH' --glob '*.{java,xml}'
```

---

## 2. Identidad del repositorio

| Campo | Resultado medido | Fuente |
| --- | --- | --- |
| `full_name` | `joselofarias-byte/NewTermux` | `gh api repos/joselofarias-byte/NewTermux` |
| URL | https://github.com/joselofarias-byte/NewTermux | idem |
| Público | `visibility=PUBLIC`, `isPrivate=false` | idem |
| Fork de GitHub | `fork=true` | idem |
| Parent GitHub | `The412Banner/NewTermux` | `parent.full_name` |
| Source GitHub | `The412Banner/NewTermux` | `source.full_name` |
| Creado | `2026-07-25T01:19:32Z` | `created_at` |
| Default branch | `main` | `default_branch` + `refs/remotes/origin/HEAD` |
| HEAD `main` | `94c5e7fbd9b945c1e952104a89b4936810c02026` | `git log -1`, `gh` branches |
| Mensaje HEAD | `Quantus: hard-disable native Android build after NO_GO decision` | idem |
| Autor HEAD | `joselofarias-byte` `2026-09-10 14:52:52 -0300` | `git log -1 --format=fuller` |
| Descripción | `Termux fork with modernized UI: accent colors, terminal themes, Oh My Zsh, text expansion, SSH manager, custom drawer buttons, file manager, and more.` | `gh repo view` |
| Topics | `null` | `gh repo view` |
| Stars / forks | `0` / `0` | `gh repo view` |
| Licencia GitHub | `NOASSERTION` / `licenseInfo.key=other` | `gh api` / `gh repo view` |
| Tags en este fork | **ninguno** | `gh api .../tags` y `git tag -l` vacíos |
| Releases en este fork | **ninguno** | `gh api .../releases` vacío |
| Submódulos | **no hay** `.gitmodules` ni `.git/modules` | `cat .gitmodules`; `git submodule status` |

Confirmación de no-confusión: el remote `origin` apunta solo a `joselofarias-byte/NewTermux`. No se añadió remote de PR Terminal ni de TBM.

---

## 3. Ramas remotas (SHA de punta)

Medido con `gh api repos/joselofarias-byte/NewTermux/branches` y `git log -1 origin/<rama>`:

| Rama | SHA | Tip message | Fecha commit |
| --- | --- | --- | --- |
| `main` | `94c5e7fbd9b945c1e952104a89b4936810c02026` | Quantus: hard-disable native Android build after NO_GO decision | 2026-09-10 |
| `agent/upstream-port-20260813` | `a1991220500da3985b008127af577564a0a159f9` | fix(ci): align NewTermux Java and Kotlin JVM targets | 2026-08-15 |
| `automation/build-20260820-2103` | `61559d747677cdadd96bf553b13d2da941d57abb` | ci: trigger fresh automation build | 2026-08-20 |
| `automation/build-20260820-2126` | `bc4bf60298ac3651861b916d05337817971565fd` | ci: trigger fresh build 20260820-2202 | 2026-08-20 |
| `ci-full-build-20260821-2029` | `668255d64e4f9206f50fdc362f66d3aae7914581` | ci: trigger fresh full build 2026-08-21 | 2026-08-21 |
| `ci-full-build-20260821-200435` | `daf31240e6b4bbe1a07a385a929ca945909e3b8b` | ci: trigger full build batch 20260821-200435 | 2026-08-21 |
| `feature/ci-build-20260814` | `dccf326fbd2fe7f388debea94cf4e8cd72676933` | CI: compile latest NewTermux branch | 2026-08-14 |
| `fix/github-build-20260815` | `4aafd3c013c52f8885bbaaa82035fcd81496a6a2` | chore(ci): remove temporary build verification workflow | 2026-08-15 |
| `tmp-check-noop` | `4aafd3c013c52f8885bbaaa82035fcd81496a6a2` | (misma punta que `fix/github-build-20260815`) | 2026-08-15 |
| `fix/notification-bigtext-null-20260821` | `23df9f89a321d2b7d5ce97172a37585ed487eebd` | fix(notification): guard null big text on Android 6 | 2026-08-21 |
| `fix/termcap-page-keys-20260821` | `aac94fabc743bce35afb5e2e635dc0535e06753a` | fix(terminal): correct PageUp/PageDown termcap mappings | 2026-08-21 |
| `opportunity-fabric-quantus-nogo-20260910` | `07e6b48c9c0758af350c8eaaecd2576608aaaad8` | Opportunity Fabric CLI: honor Quantus build-disabled hint | 2026-09-10 |
| `port/upstream-1.6.2-20260905` | `ac4a643c0d2bb9a79e5078f20ca46a46e25603b8` | Port focused terminal compatibility and rendering fixes | 2026-09-12 |

`main` está 1 commit por delante de `opportunity-fabric-quantus-nogo-20260910` (el commit NO_GO `94c5e7f`).

---

## 4. PRs existentes (estado al 2026-09-19)

`gh pr list --state all`:

| # | Título | Rama | Estado | Fecha |
| --- | --- | --- | --- | --- |
| 8 | Update NewTermux fork to upstream 1.6.2 | `port/upstream-1.6.2-20260905` | OPEN (no draft) | 2026-09-05 |
| 7 | CI: full build batch 2026-08-21 | `ci-full-build-20260821-2029` | DRAFT | 2026-08-21 |
| 6 | CI: full build batch 2026-08-21 | `ci-full-build-20260821-200435` | DRAFT | 2026-08-21 |
| 5 | Fix PageUp/PageDown termcap mappings | `fix/termcap-page-keys-20260821` | MERGED | 2026-08-21 |
| 4 | fix: evitar NPE de BigTextStyle con texto nulo | `fix/notification-bigtext-null-20260821` | MERGED | 2026-08-21 |
| 3 | Automation: fresh scheduled Android build | `automation/build-20260820-2126` | DRAFT | 2026-08-21 |
| 2 | Automation: fresh APK build | `automation/build-20260820-2103` | DRAFT | 2026-08-21 |
| 1 | Fix GitHub build JVM target mismatch | `fix/github-build-20260815` | MERGED | 2026-08-15 |

PR #8 (`MERGEABLE` según `gh pr view 8`) es el intento de poner este fork al día con parent `v1.6.2`. Su CI `Build` más reciente observado: run `34711279460`, `success`, SHA `ac4a643c0d2bb9a79e5078f20ca46a46e25603b8`. **No se fusionó en esta fase.**

---

## 5. Upstream real (no se asume Termux oficial)

### 5.1 Parent de GitHub (hecho)

```text
joselofarias-byte/NewTermux.fork = true
joselofarias-byte/NewTermux.parent = The412Banner/NewTermux
joselofarias-byte/NewTermux.source = The412Banner/NewTermux
The412Banner/NewTermux.fork = false
The412Banner/NewTermux.parent = null
The412Banner/NewTermux.source = null
```

El parent **no** es un fork de GitHub de `termux/termux-app`. `The412Banner/NewTermux` se presenta a sí mismo como proyecto independiente (`fork=false`).

Parent `main` al momento de la captura:

| Campo | Valor |
| --- | --- |
| SHA | `6001da84bb2775508e3e3346084958eb7b143480` |
| Mensaje | `Merge pull request #17 ... Background keep-alive + session persistence (1.6.2-pre.1)` |
| Fecha | `2026-08-21T18:42:00Z` |
| Tag `v1.6.2` | mismo SHA `6001da84...` |
| `versionCode` / `versionName` en ese SHA | `28` / `"1.6.2"` (`gh api .../contents/app/build.gradle?ref=6001da84...`) |

### 5.2 Relación con `termux/termux-app` (evidencia, no parent git)

Prueba negativa de SHA compartido:

```text
gh api repos/termux/termux-app/commits/a0001f10c57266ba3b0d973a9daa6357c05dbe24
→ HTTP 422  No commit found for SHA: a0001f10c57266ba3b0d973a9daa6357c05dbe24
```

Primer commit de **este** historial:

```text
a0001f10c57266ba3b0d973a9daa6357c05dbe24
Author: the412banner
Date:   2026-03-01 14:19:19 -0500
feat: initial NewTermux fork (com.newtermux.app)
```

Árbol de ese commit (módulos clásicos de Termux): `.github`, `LICENSE.md`, `app`, `terminal-emulator`, `terminal-view`, `termux-shared`, `gradle`, `docs`, `site`, `fastlane`.

`LICENSE.md` (primer commit, parent actual y `termux/termux-app`) empieza igual:

```text
The `termux/termux-app` repository is released under GPLv3 only
```

README del parent (`The412Banner/NewTermux`):

```text
A personal fork of Termux ... installs under the com.termux package,
so it's a drop-in replacement for stock Termux
```

`termux/termux-app` `master` al capturar: `084d709fbf23ea83b5cb85fd3d795c775be06676` (`2026-09-16T16:16:49Z`). Ese SHA **no** se usó como base de este fork.

**Conclusión de upstream (verificada):**

1. **Upstream git correcto para divergencia de este fork:** `The412Banner/NewTermux`, merge-base `13d57e9fe19cc2a4c7498084887123140cd5750a`.
2. **Linaje de código:** NewTermux (The412Banner) es un *reimport/squash* de la app Termux (GPLv3 + excepciones), no un fork de GitHub con historia compartida de `termux/termux-app`.
3. **No** se debe tratar `termux/termux-app` como parent git de `joselofarias-byte/NewTermux`.

### 5.3 Merge-base

```text
13d57e9fe19cc2a4c7498084887123140cd5750a
Author: The412Banner
Date:   2026-05-11 05:41:33 -0400
docs: add Discord invite link to README
```

Ese commit **es ancestro** de `HEAD` local (`git merge-base --is-ancestor ... && echo yes`).

En el merge-base, `app/build.gradle` ya tenía:

- `applicationId "com.termux"`
- `versionCode 18` / `versionName "1.5.5"`
- variante `demo` con `applicationIdSuffix ".demo"`

---

## 6. Divergencia vs upstream correcto

Compare GitHub (parent `main` `6001da84` vs `joselofarias-byte:main`):

```text
status=diverged
ahead_by=46          # commits en ESTE fork que parent no tiene
behind_by=25         # commits en parent main que ESTE fork no tiene
merge_base=13d57e9fe19cc2a4c7498084887123140cd5750a
```

Inverso (`joselofarias-byte/main` vs `The412Banner:main`): `ahead_by=25`, `behind_by=46`.

Cuentas locales coinciden: `git rev-list --count` → 46 propios, 25 del parent.

### 6.1 Commits propios (46; `13d57e9..HEAD`)

Clasificación por grupo (todos los SHA están en `git log 13d57e9..HEAD`):

| Grupo | SHAs / rango | Conservar en Fase 2 | Notas |
| --- | --- | --- | --- |
| Branding JoseloFarias | `4d791aa` `ecfb401` `e68e33d` `bc59dec` `a361933` `3ea2fde` `9f01eef` | **Sí** | `BRANDING.md`, `FORK_IDENTITY.md`, README, estilos, About/support |
| Fix BigTextStyle NPE | `23df9f8` + merge `cf8c534` (PR #4) | **Sí** | Android 6; 4 líneas en `NotificationUtils.java` |
| Fix termcap PageUp/PageDown | `aac94fa` + merge `d7dd19a` (PR #5) | **Sí** | `kP`/`kN` invertidos; `KeyHandler.java` |
| Opportunity Fabric + Quantus | `d1eb760` … `07e6b48` (34 commits) | **Sí, como política NO_GO** | scripts + `tools/opportunity-fabric/` |
| Hard-disable Quantus Android | `94c5e7f` | **Sí; no revertir** | no deshabilita el APK Gradle; ver §8 |

Archivos **añadidos** vs merge-base (producto/docs/tooling de este fork):

- `BRANDING.md`, `FORK_IDENTITY.md`
- `docs/quantus-adreno720-termux.md`
- `scripts/bootstrap-quantus-arm64.sh`, `install-opportunity-fabric.sh`, `quantus-adreno720-safe.sh`, `quantus-node-android-native-build.sh`, `quantus-node-arm64-glibc-preflight.sh`, `resume-quantus-arm64-toolchain.sh`, `run-quantus-arm64-experiment.sh`
- árbol `tools/opportunity-fabric/**`

Archivos **modificados** vs merge-base además de los anteriores:

- `README.md`
- `app/src/main/res/values/styles.xml`
- `terminal-emulator/src/main/java/com/termux/terminal/KeyHandler.java`
- `termux-shared/src/main/java/com/termux/shared/notification/NotificationUtils.java`

### 6.2 Commits del parent que este `main` no tiene (25; `13d57e9..6001da84`)

Resumen factual (mensajes de `git log`):

- toolchain Kotlin + Jetpack Compose (Phase 0–5)
- migración Compose de SSH / File / Package / Theme / Settings
- restyle Bannerlator (menús, drawer, themes)
- bump a `1.6.0` / `1.6.1` / `1.6.2` (`versionCode` 24 → 25 → 28)
- background keep-alive FGS + persistencia de sesiones
- `foregroundServiceType=specialUse`
- bumps de GitHub Actions (checkout v6, setup-java v5, etc.; parte ya está en este `main` vía workflow files coincidentes)

Esos 25 commits son el contenido de la PR #8 abierta. **No se aplicaron en esta fase.**

### 6.3 Autores del historial completo

`git shortlog -sn --all | head`:

```text
133  the412banner
 71  joselofarias-byte
 31  The412Banner
```

El grueso de la app (UI moderna, Oh My Zsh, managers) es **heredado del parent**, no inventado en este fork.

---

## 7. Build, módulos, SDK, firma, variantes

### 7.1 Módulos Gradle

`settings.gradle`:

```text
include ':app', ':termux-shared', ':terminal-emulator', ':terminal-view'
```

No hay Kotlin en `main` actual (las Activities Compose viven solo en parent 1.6.x / PR #8).

### 7.2 Versiones pinneadas (`gradle.properties` + `build.gradle` + wrapper)

| Ítem | Valor en `94c5e7f` | Archivo |
| --- | --- | --- |
| AGP | `8.13.2` | `build.gradle` `classpath` |
| Gradle | `9.2.1` | `gradle/wrapper/gradle-wrapper.properties` |
| `minSdkVersion` | `21` | `gradle.properties` |
| `targetSdkVersion` | `28` | `gradle.properties` |
| `compileSdkVersion` | `36` | `gradle.properties` |
| `ndkVersion` | `29.0.14206865` | `gradle.properties` |
| Java compile | `VERSION_1_8` | `app/build.gradle` |
| `versionCode` / `versionName` | `18` / `"1.5.5"` | `app/build.gradle` |
| `packageVariant` default | `apt-android-7` | `app/build.gradle` |
| `markwonVersion` | `4.6.2` | `gradle.properties` |
| Parent latest (no en main) | `28` / `"1.6.2"` | parent `app/build.gradle` @ `6001da84` |

Parent `gradle.properties` @ `6001da84` midió los **mismos** min/target/compile/NDK/markwon.

### 7.3 applicationId, namespace, sharedUserId

`app/build.gradle` @ `94c5e7f`:

```text
namespace "com.termux"
applicationId "com.termux"
manifestPlaceholders.TERMUX_PACKAGE_NAME = "com.termux"
manifestPlaceholders.TERMUX_APP_NAME = "NewTermux"
```

`AndroidManifest.xml`: `android:sharedUserId="${TERMUX_PACKAGE_NAME}"` → `com.termux` en debug/release.

`TermuxConstants.java`:

```text
TERMUX_APP_NAME = "NewTermux"
TERMUX_PACKAGE_NAME = "com.termux"
TERMUX_PREFIX_DIR_PATH = "/data/data/com.termux/files/usr"
```

Comentario en `TermuxActivity.java` L269: falla si `TERMUX_PACKAGE_NAME` ≠ `applicationId`.

**Historial del package (parent, no este fork):**

1. `8c080e647e108efe09b51d3bbefe311083dc54ea` (2026-03-01) — `applicationId "com.newtermux.app"`
2. `94bbbc111d21d0e74ba40adb00e173417b95dba2` (2026-03-02) — `revert: package name to com.termux for 100% stability (Nuclear Option)` y bootstrap remoto pasa a `termux/termux-packages` `bootstrap-2026.02.12-r1+apt.android-7`

### 7.4 Contradicción residual `com.newtermux.app`

Sigue en XML (no en Java de runtime):

| Archivo | Entidad |
| --- | --- |
| `app/src/main/res/values/strings.xml` | `<!ENTITY TERMUX_PACKAGE_NAME "com.newtermux.app">` |
| `termux-shared/src/main/res/values/strings.xml` | idem + `TERMUX_PREFIX_DIR_PATH "/data/data/com.newtermux.app/files/usr"` |

El PREFIX **efectivo** de shell/instalación lo define `TermuxConstants` (`com.termux`). El entity XML se usa en textos de error (`&TERMUX_PREFIX_DIR_PATH;`), así que la UI puede **mostrar** una ruta que no es la real.

Workflows de bootstrap **heredados** siguen parcheando hacia `com.newtermux.app`:

- `.github/workflows/build-bootstrap.yml` — `OLD_ID=com.termux` → `NEW_ID=com.newtermux.app`
- `.github/workflows/build-bootstrap-source.yml` — `TERMUX_APP_PACKAGE=com.newtermux.app`
- `scripts/patch-bootstrap.sh` — mismo rename + recorte de paths ELF a 31/32 chars (`.../com.newtermux.app/fil`)

`FORK_IDENTITY.md` documenta `com.termux` como excepción de compatibilidad. `BRANDING.md` pide identidad de paquete **única** respecto del upstream. Las dos políticas **chocan**.

### 7.5 Variante `demo` (coexistencia)

`app/build.gradle` buildType `demo`:

- `applicationIdSuffix ".demo"` → `com.termux.demo`
- `IS_DEMO=true`
- comentario: instala junto a Termux real y NewTermux; shell falso
- APK name: `newtermux-test-coexist_...apk`

`TermuxInstaller.java` tiene rama para saltar bootstrap real en demo.

### 7.6 Firma

`signingConfigs.debug` usa `app/testkey_untrusted.jks` ( commiteado: `git ls-files '*.jks'` → `app/testkey_untrusted.jks` ). Alias/passwords están en claro en `app/build.gradle`. `release` no define `signingConfig` propio en el archivo leído.

No se inspeccionó el keystore más allá de su presencia (no se extrajeron claves).

### 7.7 Bootstrap embebido

`app/src/main/cpp/`:

- `termux-bootstrap.c` — JNI `Java_com_termux_app_TermuxInstaller_getZip`
- `termux-bootstrap-zip.S` — `.incbin bootstrap-{i686,x86_64,aarch64,arm}.zip`
- `downloadBootstraps` descarga  
  `https://github.com/termux/termux-packages/releases/download/bootstrap-2026.02.12-r1%2Bapt.android-7/bootstrap-<arch>.zip`  
  con SHA-256 listados, **pero la verificación de checksum está comentada** (`app/build.gradle` L201–209 y L223–230).

No hay `bootstrap-*.zip` versionados en git (`git ls-files '**/bootstrap*.zip'` vacío). CI los descarga al compilar.

### 7.8 ABI

Splits debug: `x86`, `x86_64`, `armeabi-v7a`, `arm64-v8a` + `universalApk true`.

---

## 8. Estado real de build / CI (qué compila)

### 8.1 El commit NO_GO **no** apaga el APK Android

`94c5e7f` toca **solo** `scripts/quantus-node-android-native-build.sh` (`git show --stat`: 1 file, +43/−162).

El script ahora imprime `Decision: NO_GO`, escribe un JSON de política y `exit 0` **sin** clonar, instalar deps, ni `cargo build`. Motivos citados en el propio script: fallo `rustix 1.1.2` / `linux_raw_sys` en Android y mismatch `protoc`/Abseil.

CI **Build** (`debug_build.yml` → `./gradlew assembleDebug`) en ese mismo SHA:

| Campo | Valor |
| --- | --- |
| Run | `34510906662` |
| URL | https://github.com/joselofarias-byte/NewTermux/actions/runs/34510906662 |
| Evento | `push` / `main` |
| Conclusión | `success` |
| Artefactos | 12 (apt-android-5 y apt-android-7 × universal/arm64-v8a/armeabi-v7a/x86_64/x86 + sha256sums) |
| Nombre típico | `termux-app_v1.5.5+94c5e7f-apt-android-7-github-debug_arm64-v8a` |

**Hecho:** el APK debug de NewTermux **sí compiló en CI** después del NO_GO. El NO_GO bloquea el *nodo nativo Quantus en Termux*, no `assembleDebug`.

Esta auditoría **no** re-ejecutó Gradle localmente y **no** instaló nada en HONOR 200.

### 8.2 Workflows en `main` (9 archivos)

| Workflow | Path | Trigger real | Runs observados |
| --- | --- | --- | --- |
| Build | `debug_build.yml` | push `main` / `github-releases/**` / `claude-integration` / `feature/**`; PR a `main` | Varios `success` recientes, incl. `94c5e7f` |
| Unit tests | `run_tests.yml` | push/PR a `master` y `android-10` **(este repo no tiene esas ramas)** | `gh run list --workflow 'Unit tests'` vacío |
| Validate Gradle Wrapper | `gradle-wrapper-validation.yml` | mismas ramas `master`/`android-10` | no se listó ejecución reciente en el sample |
| Automatic Dependency Submission | `dependency-submission.yml` | push `master` + `workflow_dispatch` | no medido más allá del trigger muerto |
| Build NewTermux Bootstrap | `build-bootstrap.yml` | `workflow_dispatch` | `gh run list` vacío |
| Build NewTermux Bootstrap from Source | `build-bootstrap-source.yml` | `workflow_dispatch` | vacío |
| NewTermux Full Release | `release.yml` | tags `v*` / dispatch | vacío (no hay tags) |
| Attach Debug APKs To Release | `attach_debug_apks_to_release.yml` | `release.published` | no aplicable (0 releases) |
| Trigger Termux Library Builds on Jitpack | `trigger_library_builds_on_jitpack.yml` | `release.published` | no aplicable |

`gh api .../actions/workflows` también lista como `active` (registro de Actions, **archivo 404 en `main`**):

- `CI One-shot Build` → `.github/workflows/ci-build-20260814-234030-newtermux.yml`
- `Verify GitHub build fix` → `.github/workflows/verify-build-fix.yml`

Un run fallido observado: `31860322951` `CI One-shot Build` en rama `ci/build-20260814-234030-newtermux` (`2026-08-15`, esa rama ya no existe en el listado de branches).

### 8.3 Tests locales existentes (no ejecutados aquí)

Directorios presentes: `app/src/test`, `terminal-emulator/src/test`, `termux-shared/src/androidTest`, `tools/opportunity-fabric/tests/test_quantus_nogo.py`.

Estado de ejecución en CI de la app: **no corre** por triggers heredados de Termux oficial (`master`/`android-10`).

### 8.4 Qué está incompleto / abandonado / heredado

| Ítem | Clasificación | Evidencia |
| --- | --- | --- |
| UI moderna (temas, SSH, files, packages, STT, root toggle, Oh My Zsh) | **Heredada** del parent; presente en `main` 1.5.5 (Views, no Compose) | primer commit + `app/src/main/java/com/newtermux/features/*`, `app/src/main/assets/zsh-plugins.zip` |
| Compose 1.6.x + keep-alive FGS | **Pendiente de port** (parent ahead 25) | PR #8 abierta |
| `com.newtermux.app` como applicationId | **Abandonado** (revert 2026-03-02) | `94bbbc1` |
| Workflows/scripts que aún parchean a `com.newtermux.app` | **Heredado / inconsistente** | `build-bootstrap.yml`, `patch-bootstrap.sh`, entities XML |
| Logs CI bootstrap `logs_58977516357/` | **Heredado / artefacto** | commit `ec5ff04` 2026-03-01; parche a `TERMUX_APP_PACKAGE=com.newtermux.app` |
| `PROGRESS_LOG.md` | **Heredado** (The412Banner; p.ej. session pip 2026-04-25 “CI green, NOT merged”) | archivo en raíz |
| `docs/en/index.md` | **Heredado**; sigue linkeando `termux/termux-app` | L13 |
| Unit tests / wrapper validation | **Roto de disparo** (ramas incorrectas) | YAML |
| Releases/tags de este fork | **Inexistentes** | API vacía |
| Quantus native Android node | **Cerrado NO_GO** | `94c5e7f`, `tools/opportunity-fabric/README.md` |
| Checksum bootstrap | **Debilitado** (bloques de verify comentados) | `app/build.gradle` |
| `targetSdk 28` en compileSdk 36 | **Heredado** (también en parent 1.6.2) | `gradle.properties` |

---

## 9. Licencias y avisos a conservar

Archivos de licencia en el árbol:

- `LICENSE.md` — GPLv3 only para el linaje `termux/termux-app`; excepción Apache 2.0 para código de [Android Terminal Emulator](https://github.com/jackpal/Android-Terminal-Emulator) en `terminal-view` / `terminal-emulator`; apunta a `termux-shared/LICENSE.md`.
- `termux-shared/LICENSE.md` — MIT con excepciones GPLv3 (`com.termux.shared.termux.*` salvo `TermuxConstants` / `TermuxPropertyConstants` en MIT), GPLv2+Classpath (ojluni), Apache 2.0 (`StreamGobbler`).

`SECURITY.md` apunta a https://termux.dev/security.

`FORK_IDENTITY.md` / `BRANDING.md`: conservar atribución a The412Banner y mantenedores de Termux; no presentar marcas upstream como propias.

README de este fork dice `Consulte LICENSE` pero el archivo real es `LICENSE.md` (desalineación documental).

No hay `COPYING` ni `NOTICE` separados. El texto de `LICENSE.md` es el mismo en primer commit, parent actual y `termux/termux-app` (primeras 6 líneas comparadas vía `git show` / `gh api`).

**Fase 2 no debe borrar ni reescribir estos avisos.** Opportunity Fabric / Quantus docs son añadidos del fork y no sustituyen GPLv3.

---

## 10. Inventario goargs / bootstrap / repos / PRoot / applicationId

Solo inventario; **nada implementado** en esta fase.

### 10.1 `goargs`

```text
rg -n 'goargs' .
→ No matches found
```

No hay rastro de `goargs` en el source de NewTermux. Si el runtime Go de Play está parcheado, **no entra por este árbol de app**. Entraría por el zip de bootstrap / recetas de `termux/termux-packages` (Fase 4).

Bootstrap pinneado: `termux/termux-packages` release `bootstrap-2026.02.12-r1+apt.android-7` (y variante android-5 `2022.04.28-r6` en el task Gradle). Contenido del zip **no se descomprimió** aquí.

### 10.2 Repos APT / PREFIX

Runtime Java usa `PREFIX=/data/data/com.termux/files/usr`.

Scripts Quantus/Opportunity Fabric asumen Termux oficial (`PREFIX` con `com.termux`, keyring `termux-autobuilds.gpg`, `https://packages.termux.dev/apt/termux-main`).

Workflows abandonados de bootstrap custom apuntan a `com.newtermux.app`.

### 10.3 PRoot / Debian

`rg` de `proot` / `PRoot` / `proot-distro` en md/java/sh/yml/xml: **sin implementación**. Solo menciones Debian en javadocs de manpages y `TermuxBootstrap` describiendo APT.

PRoot/Debian, si existe, sería paquete post-bootstrap, no código de esta app. **PENDIENTE Fase 5.**

### 10.4 Coexistencia applicationId (Fase 3)

Hechos ya visibles:

| Variante | applicationId | sharedUserId | Conflicto con Termux Play (`com.termux`) |
| --- | --- | --- | --- |
| `debug` / `release` | `com.termux` | `com.termux` | **Sí: no pueden instalarse juntos** (mismo ID + mismo sharedUser; release.yml lo admite: “Uninstall the original Termux before installing the main build”) |
| `demo` | `com.termux.demo` | `com.termux.demo` | Diseñado para coexistir; shell no real |

`FORK_IDENTITY.md` elige `com.termux` a propósito. El objetivo de la misión (APK debug coexistente con Termux Play) **choca** con el `applicationId` actual de debug/release.

No se cambió ningún ID en esta fase.

---

## 11. Tabla PASS / FAIL / PENDIENTE

| Ítem de auditoría Fase 1 | Estado | Evidencia |
| --- | --- | --- |
| Repo correcto (no PR Terminal / TBM / Termux Play) | **PASS** | remote origin + `gh api` `full_name=joselofarias-byte/NewTermux` |
| Default branch + HEAD SHA | **PASS** | `main` @ `94c5e7f...` |
| Listado ramas / tags / releases / PRs | **PASS** | §3–§4 |
| Parent GitHub identificado | **PASS** | `The412Banner/NewTermux` |
| Descartado parent git = Termux oficial | **PASS** | parent `fork=false`; SHA inicial ausente en `termux/termux-app` |
| Merge-base + counts 46/25 | **PASS** | compare API + `git rev-list --count` |
| Clasificación de cambios propios | **PASS** | §6.1 |
| Inventario Gradle/AGP/SDK/NDK/módulos | **PASS** | §7 |
| applicationId / PREFIX / demo | **PASS** (inconsistencias documentadas) | §7.3–§7.5 |
| CI assembleDebug en HEAD | **PASS** | run `34510906662` success + artefactos |
| Unit tests en CI | **FAIL** (trigger muerto `master`/`android-10`) | `run_tests.yml`; 0 runs |
| Wrapper validation en CI | **FAIL** (mismo trigger) | YAML |
| Bootstrap custom workflows | **PENDIENTE** (0 runs; scripts desalineados con `com.termux`) | §8.2 / §7.4 |
| Submódulos | **PASS** (no hay) | `.gitmodules` ausente |
| Licencias localizadas | **PASS** | `LICENSE.md`, `termux-shared/LICENSE.md` |
| `goargs` en este repo | **PASS** (ausente) | `rg` 0 hits |
| PRoot/Debian en este repo | **PASS** (ausente como feature) | `rg` |
| Build local Gradle en este agente | **PENDIENTE** | no ejecutado a propósito |
| Instalación / prueba HONOR 200 | **PENDIENTE** | prohibido declarar éxito físico |
| Contenido real del zip bootstrap (Go sano o no) | **PENDIENTE** | Fase 4 |
| Revisión Android 16 (FGS, 16 KiB, storage) | **PENDIENTE** | Fase 2; `targetSdk=28` heredado |
| Merge/port 1.6.2 | **PENDIENTE** | PR #8 abierta; no merge |

---

## 12. Riesgos (para Fase 2, sin mitigar aquí)

1. **`applicationId=com.termux` en debug/release** impide coexistir con Termux Play. Un “update” de esta APK sobre Play (o viceversa) es el peor caso de datos. La vía `demo` existe pero no es un entorno Go/PRoot real.
2. **Entities XML `com.newtermux.app` + scripts de bootstrap** pueden reintroducir un PREFIX distinto al de los binarios oficiales (`/data/data/com.termux/files/usr`) si alguien reactiva esos workflows.
3. **`patch-bootstrap.sh` recorta paths ELF** (`.../fil`, `.../file`) — frágil; no usar sin re-auditoría.
4. **Checksum de bootstrap comentado** — un zip alterado se empaquetaría igual.
5. **`targetSdkVersion=28`** con `compileSdk=36` en Android 16: storage, FGS, notificaciones e intents quedan para análisis Fase 2. Parent 1.6.2 **tampoco** subió targetSdk.
6. **PR #8 es grande** (Compose, FGS keep-alive, version 1.6.2). Fusionarla sin preservar branding + termcap + BigText + NO_GO Quantus perdería trabajo propio. CI de esa PR está verde, pero no se revisó el diff completo aquí.
7. **Workflows huérfanos** en el registro de Actions (one-shot / verify-build) y triggers `master` dan señal falsa de cobertura.
8. **Keystore debug + passwords en git** — típico de Termux; no es release key, pero no debe usarse como firma de distribución.
9. **Quantus/Opportunity Fabric** mete scripts que tocan `apt` y keyrings de Termux. El NO_GO debe permanecer; no reabrir build nativo en el teléfono.
10. **`sharedUserId=com.termux`** acopla plugins oficiales; cambiar ID es migración atómica (Fase 3), no un replace textual.

---

## 13. Qué falta para Fase 2 (actualización conservadora)

Trabajar en **otra** rama dedicada, nunca `main`.

1. Tratar `The412Banner/NewTermux` @ `6001da84` / tag `v1.6.2` como upstream git; **no** rebasar sobre `termux/termux-app`.
2. Decidir explícitamente si se toma PR #8 (Compose + keep-alive) o un port más chico. En ambos casos **replay** de:
   - branding JoseloFarias (`BRANDING.md`, `FORK_IDENTITY.md`, estilos)
   - `KeyHandler` kP/kN
   - guard `BigTextStyle`
   - árbol Opportunity Fabric + script NO_GO
3. **No revertir** `94c5e7f`.
4. Justificar cualquier bump Gradle/AGP/SDK/NDK (hoy ya AGP 8.13.2 / Gradle 9.2.1 / compileSdk 36 / NDK 29; targetSdk sigue 28).
5. Revisar Android 16 sobre el código **resultante**, no sobre hipótesis de Termux oficial.
6. Dejar Fase 3 (ID de coexistencia) **después** de fijar el árbol; no cambiar `applicationId` en el mismo PR que el port 1.6.2 salvo análisis escrito.
7. Corregir triggers CI `master`→`main` y alinear entities XML solo con justificación (es cambio de producto; no se hizo aquí).
8. No reactivar bootstrap `com.newtermux.app` ni debilitar TLS / añadir claves.
9. No desinstalar Termux Play ni tocar TBM.

---

## 14. Límites cumplidos en esta corrida

- No merge, no release, no force-push, no borrado de ramas, no cambio de visibilidad/secrets.
- No se editó código de producto; solo se añade este informe en rama dedicada.
- No se estudió PR Terminal como base (no se clonó).
- No se declaró éxito en HONOR 200.
- No se añadieron claves ni se debilitó TLS.

---

## 15. Anexo: SHAs canónicos

| Objeto | SHA |
| --- | --- |
| Este `main` auditado | `94c5e7fbd9b945c1e952104a89b4936810c02026` |
| Merge-base con parent | `13d57e9fe19cc2a4c7498084887123140cd5750a` |
| Parent `main` / tag `v1.6.2` | `6001da84bb2775508e3e3346084958eb7b143480` |
| Primer commit NewTermux | `a0001f10c57266ba3b0d973a9daa6357c05dbe24` |
| Revert package → `com.termux` | `94bbbc111d21d0e74ba40adb00e173417b95dba2` |
| Tip PR #8 | `ac4a643c0d2bb9a79e5078f20ca46a46e25603b8` |
| `termux/termux-app` master (contexto, no base) | `084d709fbf23ea83b5cb85fd3d795c775be06676` |
