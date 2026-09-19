# NewTermux — Fase 6: Build y CI

- **Fecha:** 2026-09-19
- **Rama:** `cursor/fase6-build-ci-2026-09-19-0b82`
- **Base:** punta Fase 5 `f717bcdfc2b779ac3e6c6f2307eedf2efc9253f8` (PR #13), **no** `main`
- **Regla:** APK debug = artefacto CI; nunca GitHub Release. Sin claves nuevas. Sin debilitar TLS.

---

## 1. Qué se endureció

| Pieza | Cambio |
| --- | --- |
| `debug_build.yml` (`Build`) | Sigue `assembleCoexistDebug` + SHA-256. Verifica `applicationId=com.newtermux.dev`, `variantName=coexistDebug`, firma presente, **sin** outputs `playcompat`/`release`. Artefacto `DEBUG-NOT-RELEASE.txt` |
| `run_tests.yml` | Trigger `master`/`android-10` → **`main`** + `testCoexistDebugUnitTest` en emulator |
| `gradle-wrapper-validation.yml` | Trigger → **`main`**; action `@v5` |
| `fase6_guard.yml` | secret-scan + wrapper + unit tests en cada PR a `main` |
| `release.yml` / `attach_debug_apks_to_release.yml` | **abortan**: debug no se publica como Release |
| `dependency-submission.yml` | trigger `master` → `main` (no es release) |
| Submódulos | no hay `.gitmodules` — N/A |

CI **no** construye `assemblePlaycompatDebug` (`com.termux`).

---

## 2. Reproducibilidad (debug, no bit-idéntico)

Pinneado: JDK 17 Temurin, Gradle 9.2.1 (wrapper), tarea `assembleCoexistDebug`, tag bootstrap `2026.02.12-r1+apt.android-7`, `versionName` con SHA corto.

**No** es reproducible bit a bit: timestamps de firma debug, descarga del zip (hash Gradle ≠ GitHub; verify off, Fase 4). No se reactivó el compare a ciegas. No hay `SOURCE_DATE_EPOCH`.

---

## 3. Firma y secretos

- Firma debug: `app/testkey_untrusted.jks` (untrusted, commiteado). Distinta de Play; no puede actualizar `com.termux`.
- Passwords de esa testkey están en `app/build.gradle` (preexistente). No se añadieron claves privadas nuevas.
- `scripts/fase6/secret-scan.sh` falla si aparece otro `.jks`/PEM/token.

---

## 4. Tests y lint

- Unit tests del emulador: `./gradlew :terminal-emulator:testCoexistDebugUnitTest` (JVM, sin bootstrap zip).
- `:app:testCoexistDebugUnitTest` / `lintCoexistDebug` **compilan la app** y disparan `downloadBootstraps` (~30 MiB). No se duplican en el job de tests para no triplicar el zip; lint queda **disponible** vía Gradle y se documenta. Si el job de tests del emulador falla, el bloqueo se anota con el run.

---

## 5. Artefactos

Prefijo: `termux-app_v1.6.2+<sha7>-apt-android-7-github-debug_arm64-v8a.apk`  
SHA-256: archivo `*_sha256sums` + este informe al cerrar el run de `Build`.

**Nunca** adjuntar a `softprops/action-gh-release` / `hub release edit`.

---

## 6. Tabla PASS / FAIL / PENDIENTE

| Ítem | Ámbito | Estado |
| --- | --- | --- |
| Trigger tests/wrapper → `main` | git | PASS |
| Secret-scan + no playcompat en Build | git | PASS |
| Release workflows abortan debug | git | PASS |
| Submódulos | git | PASS (N/A) |
| CI `Build` coexist arm64 + SHA-256 | cloud | se completa en este PR |
| CI `Fase6 guard` | cloud | se completa en este PR |
| Lint app en CI | cloud | **PENDIENTE** (comando listo; no en job para no re-descargar bootstrap) |
| Bit-reproducible debug | proceso | **FAIL** (documentado; no forzado) |
| HONOR 200 | dispositivo | **PENDIENTE** |
