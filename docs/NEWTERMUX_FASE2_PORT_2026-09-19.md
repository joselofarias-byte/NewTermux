# NewTermux — Fase 2: port conservador a Banner 1.6.2

- **Fecha:** 2026-09-19
- **Rama:** `cursor/port-banner-1.6.2-conservador-2026-09-19-0b82`
- **Base:** `origin/main` @ `94c5e7fbd9b945c1e952104a89b4936810c02026`
- **Upstream git:** `The412Banner/NewTermux` tag `v1.6.2` @ `6001da84bb2775508e3e3346084958eb7b143480` (SHA verificado: merge-commit, mensaje keep-alive 1.6.2)
- **Vehículo:** merge limpio de `origin/port/upstream-1.6.2-20260905` (PR #8 tip `ac4a643c0d2bb9a79e5078f20ca46a46e25603b8`)
- **Merge propio:** `0938ec95ad49dd1f3374bcfaceff94c2fcd1f42d` (`94c5e7f` + `ac4a643`, strategy `ort`, 0 conflictos)

No se cambió `applicationId`, `sharedUserId` ni authorities. Fase 3 (coexistencia) va en otro PR.

---

## 1. ¿Sirvió la PR #8?

**Sí, como base de port, no como merge a `main`.**

| Pregunta | Evidencia |
| --- | --- |
| ¿Contiene parent `6001da84`? | `git merge-base --is-ancestor 6001da84 origin/port/upstream-1.6.2-20260905` → sí; `rev-list` parent∉PR8 = 0 |
| ¿Contiene branding / termcap / BigText? | sí (`4d791aa`, `aac94fa`, `23df9f8` ancestros) |
| ¿Contiene Opportunity Fabric + NO_GO `94c5e7f`? | **no** (35 commits de `main` posteriores al merge-base `d7dd19a`) |
| ¿Commits extra vs parent? | `c083395` (merge 1.6.2), `a07afcd` (security RUN_COMMAND), `ac4a643` (terminal render/compat) |
| ¿Solape de archivos con `main` post-`d7dd19a`? | **ninguno** (`comm -12` vacío) |
| ¿CI previa? | run `34711279460` success en el tip de PR #8 |

Decisión: nueva rama **desde `main`** (conserva OF + NO_GO) + merge de PR #8 (trae 1.6.2 + puertos extra ya verdes). No se rehízo el port archivo por archivo. No se mergeó a `main`.

---

## 2. Qué se portó (upstream / PR #8)

- Jetpack Compose + Kotlin 2.2.20 (Settings / Theme / Package / File / SSH).
- Keep-alive FGS `specialUse` + `SessionStatePersister` (tabs: nombre/cwd/failsafe; **no** PTY ni procesos).
- `versionCode 28` / `versionName "1.6.2"`.
- Java 11 + Compose BOM `2025.06.01` (requerido por el port; AGP/SDK/NDK **sin** bump).
- Actions en workflows de bootstrap/release: checkout v6, setup-java v5, upload-artifact v7, action-gh-release v2, setup-buildx v4.
- Security: no aplicar `resultDirectoryPath` hasta pasar `allow-external-apps` (`a07afcd`).
- Terminal: scroll persistente, `drawTextRun` fallback, wrap stale, cursor float, margen multi-ventana (`ac4a643`).

### Gradle / AGP / SDK — justificación

| Ítem | `main` 1.5.5 | Este port | ¿Por qué? |
| --- | --- | --- | --- |
| AGP | 8.13.2 | 8.13.2 | sin cambio |
| Gradle | 9.2.1 | 9.2.1 | sin cambio |
| min/target/compile/NDK | 21 / 28 / 36 / 29.0.14206865 | igual | sin cambio |
| Kotlin / Compose plugins | ausentes | 2.2.20 | exigido por Activities `.kt` del parent |
| Java | 1.8 | 11 | exigido por Compose/AndroidX del parent |
| Compose BOM | — | 2025.06.01 | pin del parent 1.6.2, no un upgrade independiente |

No se subió `targetSdk`. No se tocó el pin de bootstrap `2026.02.12-r1`.

---

## 3. Qué se preservó (propio)

Verificado en HEAD del port:

| Personalización | Estado | Evidencia |
| --- | --- | --- |
| Branding `BRANDING.md` / `FORK_IDENTITY.md` | intacto | archivos presentes; `styles.xml` `colorAccent` `#6750A4` |
| README JoseloFarias | intacto (versión PR #8) | título “JoseloFarias fork”; crédito Banner + Termux |
| termcap PageUp/PageDown | intacto | `kP`→PAGE_UP, `kN`→PAGE_DOWN |
| BigTextStyle null guard | intacto | `if (notificationBigText != null)` |
| Opportunity Fabric | intacto | `tools/opportunity-fabric/` |
| Quantus NO_GO `94c5e7f` | **no revertido** | `git diff 94c5e7f -- scripts/quantus-node-android-native-build.sh` vacío; script sigue `Decision: NO_GO` / `Build disabled: true` |
| `applicationId` / `sharedUserId` | **sin cambio** | `com.termux` debug/release; `com.termux.demo` demo |
| Licencias | intactas | `LICENSE.md`, `termux-shared/LICENSE.md` |

Nota de branding no resuelta (ya existía): `NewTermuxTheme.DEFAULT_COLOR` sigue `0xFFBB86FC` (parent). `FORK_IDENTITY.md` pide `#6750A4`. No se cambió en este PR para no mezclar un retune visual con el port.

---

## 4. applicationId — Fase 3 aparte

```
applicationId "com.termux"
manifestPlaceholders.TERMUX_PACKAGE_NAME = "com.termux"
android:sharedUserId="${TERMUX_PACKAGE_NAME}"
```

Esto **choca con Termux Play** (`com.termux`). El APK debug/release principal no puede instalarse al lado de Play. La variante `demo` (`com.termux.demo`) sí, con shell falso.

**No se modificó identidad de paquete en este PR.** Cualquier ID de coexistencia es Fase 3, PR separado, análisis atómico (providers, PREFIX, bootstrap).

---

## 5. Revisión Android 16 / runtime (estática)

No hay dispositivo HONOR 200. Esto es revisión de código + herencia 1.6.2.

| Área | Hallazgo | Estado |
| --- | --- | --- |
| Storage | `targetSdk=28`, `requestLegacyExternalStorage=true`, permisos READ/WRITE/MANAGE_EXTERNAL_STORAGE | **PENDIENTE** al subir target; con target 28 el modelo viejo sigue “funcionando” de forma heredada |
| Procesos / FGS | `TermuxService` `foregroundServiceType=specialUse` + `FOREGROUND_SERVICE_SPECIAL_USE` + `startForeground(..., SPECIAL_USE)` en API 34+ | **PASS** en código (parent 1.6.2). Play policy `specialUse` en Android 16: **PENDIENTE** (no se publica release aquí) |
| Notificaciones | canal existente; BigText null-safe | **PASS** guard; canales/FGS notif en 16 **PENDIENTE** hardware |
| Intents | `getParcelableExtra` sin tipo (API 33+) | inofensivo mientras `targetSdk<33`; **PENDIENTE** si se sube target |
| PendingIntent | `TermuxService` usa flags `0`; crash/plugin utils usan `FLAG_UPDATE_CURRENT` sin IMMUTABLE | **FAIL documentado** para `targetSdk>=31`; con target 28 suele no crashear. No se parcheó para no ampliar el port |
| Ejecución de binarios | JNI PTY `termux.c` (`/dev/ptmx`, fork, exec); bootstrap zip embebido | sin cambio vs 1.5.5; **PENDIENTE** 16 KiB / linker en APK real |
| Páginas 16 KiB | `Android.mk` sin `-Wl,-z,max-page-size=16384`; NDK 29 suele alinear 16K por defecto | **PENDIENTE** verificar ELF de `.so` en CI artifact |
| JNI | `libtermux`, `libtermux-bootstrap`; `useLegacyPackaging true` | heredado; no se tocó |
| PTY | `create_subprocess` sin cambios en este port | heredado |
| Teclado | termcap kP/kN conservado; extra-keys View + Compose menus | **PASS** estático |
| Clipboard | `ClipboardManager` / `ShareUtils.copyTextToClipboard` | sin cambio de política |
| Sesiones | persistencia de metadatos; PTY no se revive | documentado en `SessionStatePersister` |
| Wake-lock | `PARTIAL_WAKE_LOCK` + toggle en notificación | heredado |
| Background | keep-alive FGS; `SessionStatePersister.clear()` al stop explícito | portado |

Sixel/iTerm (excluido en PR #8) sigue fuera.

---

## 6. Incompatibilidades no resueltas

1. `targetSdkVersion=28` en Android 16 (compileSdk 36): storage scoped, FGS types enforcement en Play, photo picker, etc. no se revalidaron.
2. `PendingIntent` flags `0` / sin `FLAG_IMMUTABLE`.
3. Entities XML `com.newtermux.app` vs Java `com.termux` (Fase 1).
4. Checksum de bootstrap sigue comentado.
5. Unit tests CI siguen gatillando `master`/`android-10`.
6. Default accent `#BB86FC` vs marca `#6750A4`.
7. Coexistencia con Termux Play imposible en debug/release.
8. 16 KiB page-size no medido sobre `.so` construidos.
9. `getParcelableExtra` deprecated sin overload tipado.

---

## 7. Reversión

```bash
# Esta rama no se mergeó a main. Descartar el port = no mergear el PR.
git checkout main
# main permanece en 94c5e7f hasta decisión humana.
```

Para deshacer solo el merge en esta rama (no force-push a main):

```bash
git log -1 --format='%H %P'   # 0938ec9  parents 94c5e7f ac4a643
```

No force-push. No borrar `main` ni PR #8.

---

## 8. Tabla PASS / FAIL / PENDIENTE

| Ítem | Estado |
| --- | --- |
| SHA parent `6001da84` verificado y ancestro | PASS |
| Rama desde `main` (no commits en `main`) | PASS |
| Merge PR #8 sin conflictos | PASS |
| Branding / termcap / BigText / OF / NO_GO | PASS |
| `applicationId` intacto | PASS |
| AGP/SDK/NDK sin bump injustificado | PASS |
| Kotlin/Compose añadidos con justificación | PASS |
| FGS specialUse en código | PASS |
| Unit tests CI | FAIL (triggers muertos; no corregidos aquí) |
| PendingIntent IMMUTABLE | FAIL (documentado, no parcheado) |
| CI Build de esta rama | PENDIENTE (se reporta al correr) |
| Gradle local en este agente | PENDIENTE |
| HONOR 200 / Android 16 físico | PENDIENTE |
| 16 KiB ELF | PENDIENTE |
| Fase 3 applicationId coexistencia | PENDIENTE (PR aparte) |
