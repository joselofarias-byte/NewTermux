# NewTermux — Fase 3: coexistencia segura con Termux Play

- **Fecha:** 2026-09-19
- **Rama:** `cursor/coexist-debug-id-2026-09-19-0b82`
- **Base:** punta Fase 2 `24936418b3fd9105e8b7178907f1c818df376816` (PR #10), **no** `main` @ `94c5e7f`
- **Regla:** sin replace textual ciego; sin tocar datos de Play; sin merge a `main`

---

## 1. Decisión (después del análisis, no antes)

| Candidato | ¿Viable? | Motivo |
| --- | --- | --- |
| Dejar debug = `com.termux` | No | Actualiza/choca con Termux Play (mismo ID + sharedUserId) |
| Solo variante `demo` (`com.termux.demo`) | No para el éxito de Fase 3 | Shell falso (`IS_DEMO`); no es APK debug real |
| `com.joselofarias.newtermux.debug` | **Rechazado** | 32 chars. PREFIX oficial embebido `/data/data/com.termux/files/usr` = 31 chars. Un ID largo exige rebuild de bootstrap (Fase 4/6), no un patch ELF. Detenido a propósito |
| `com.newtermux.app` | **Rechazado** | Experimento abandonado 2026-03-02 (`94bbbc1`). Scripts/XML residuales no se reactivan |
| **`com.newtermux.dev` (debug only)** | **Sí** | Distinto de Play; 16 chars (misma longitud que el experimento viejo) si Fase 4 reconstruye PREFIX; `sharedUserId` propio; authorities propias |

**Release** permanece `com.termux` (drop-in futuro). Esta misión instala **debug**. CI `assembleDebug` produce la identidad coexistente.

No se aplicó `scripts/patch-bootstrap.sh` ni patchelf. Ese hack (`.../fil`) se documenta como frágil y fuera de Fase 3.

---

## 2. Matriz antes → después (`assembleDebug`)

| Superficie | Antes (Fase 2 debug) | Después (este PR) | ¿Por qué? |
| --- | --- | --- | --- |
| `applicationId` | `com.termux` | **`com.newtermux.dev`** | Evitar update sobre Play |
| `sharedUserId` | `com.termux` | **`com.newtermux.dev`** | UID distinto; sin sharedUser con Play (firmas distintas de todos modos fallarían) |
| `TERMUX_PACKAGE_NAME` (Java) | literal `com.termux` | `BuildConfig.TERMUX_APP_PACKAGE` por variant | PREFIX/intents siguen al ID real; no un segundo hardcode |
| PREFIX | `/data/data/com.termux/files/usr` | **`/data/data/com.newtermux.dev/files/usr`** | No escribir/borrar el árbol de Play |
| HOME / files | `/data/data/com.termux/files` | `/data/data/com.newtermux.dev/files` | Aislamiento |
| Permiso `RUN_COMMAND` | `com.termux.permission.RUN_COMMAND` | `com.newtermux.dev.permission.RUN_COMMAND` | No reclamar el permiso de Play |
| Intent `RUN_COMMAND` | `com.termux.RUN_COMMAND` | `com.newtermux.dev.RUN_COMMAND` | Plugins Play no disparan este debug |
| Provider documents | `com.termux.documents` | `com.newtermux.dev.documents` | SAF aislado |
| Provider files | `com.termux.files` | `com.newtermux.dev.files` | Content URIs aislados |
| `taskAffinity` filereceiver | `com.termux.filereceiver` | `com.newtermux.dev.filereceiver` | Placeholder de manifiesto |
| Shortcuts `targetPackage` | `com.termux` (main XML) | overlay debug → `com.newtermux.dev` | shortcuts.xml no interpola `${applicationId}` |
| Extra failsafe | `com.termux.app.failsafe_session` | `com.newtermux.dev.app.failsafe_session` | Constante derivada de `TERMUX_PACKAGE_NAME` |
| Label launcher | NewTermux | **NewTermux Dev** | Distinguir iconos si ambos están instalados |
| Firma debug | `app/testkey_untrusted.jks` | igual | Distinta de Play Store; no puede actualizar Play |
| Bootstrap zip | `termux-packages` `2026.02.12-r1` | **igual, sin patch** | No mezclar goargs de Play; no ELF hack |
| Plugin IDs derivados | `com.termux.api` etc. | `com.newtermux.dev.api` etc. | Debug no usa plugins oficiales de Play |

### Release (`assembleRelease`) — sin cambio de ID

| Superficie | Valor |
| --- | --- |
| applicationId / sharedUserId / PREFIX | `com.termux` / `/data/data/com.termux/files/usr` |
| Sigue chocando con Play | Sí — no se construye en CI `Build` |

### Demo (`assembleDemo`) — ID explícito

| Superficie | Valor |
| --- | --- |
| applicationId | `com.termux.demo` (fijado; **no** `com.newtermux.dev.demo`) |
| Shell | falso (`IS_DEMO`) |

---

## 3. Qué no se tocó (a propósito)

- Namespace Java `com.termux` / `com.termux.app` (estructura de proyecto; TermuxConstants lo desaconseja).
- Entities XML `com.newtermux.app` en `strings.xml` (texto de error residual; PREFIX real es Java). No se “corrigió” a ciegas al ID nuevo para no mentir en release.
- `scripts/patch-bootstrap.sh`, workflows bootstrap → `com.newtermux.app`.
- TBM, Termux Play, datos de usuario.
- Checksum de bootstrap (sigue comentado; Fase 4/6).

File Manager usa `packageName + ".fileprovider"` y **no** hay `FileProvider` en el manifiesto (preexistente). No se añadió un authority `com.termux.fileprovider`.

---

## 4. Guardas anti-Play

`CoexistIdentity` + cortes en `TermuxApplication` y `TermuxInstaller`:

1. `applicationId` debe igualar `TermuxConstants.TERMUX_PACKAGE_NAME`.
2. PREFIX debe ser `/data/data/<applicationId>/...`.
3. Si el proceso **no** es `com.termux` y PREFIX apunta a `/data/data/com.termux/`, **se aborta** antes de `deleteFile` / extract.

Así un mismatch de BuildConfig no borra el PREFIX de Play.

---

## 5. Bootstrap / Go (límite Fase 3)

El zip oficial sigue compilado para `com.termux`. Al extraerlo en `/data/data/com.newtermux.dev/files/usr`, **shebangs/RPATH/interpreter pueden seguir apuntando a Play**.

| Afirmación | Estado |
| --- | --- |
| APK debug identidad ≠ Play | objetivo Fase 3 |
| Datos internos no compartidos | sandbox + UID + PREFIX propio |
| Shell/apt/Go sanos | **PENDIENTE Fase 4** (rebuild PREFIX-aware; no goargs Play) |
| ELF patch `fil`/`file` | **no implementado** (frágil; Nuclear Option 2026-03-02) |

---

## 6. Reversión

No mergear este PR. `main` y PR #10 permanecen. Quitar la rama no toca Play.

---

## 7. Tabla PASS / FAIL / PENDIENTE

| Ítem | Ámbito | Estado |
| --- | --- | --- |
| Análisis IDs/authorities/PREFIX/firma | git | PASS |
| ID `com.joselofarias.newtermux.debug` | decisión | FAIL (rechazado, documentado) |
| Debug `com.newtermux.dev` + sharedUserId/authorities | código | PASS (estático) |
| Release sigue `com.termux` | código | PASS |
| Demo no hereda suffix del debug | código | PASS |
| Guardas anti-PREFIX Play | código | PASS |
| Replace textual ciego | proceso | PASS (no hecho) |
| CI `assembleDebug` esta rama | cloud | PENDIENTE al escribir; se actualiza al correr |
| SHA-256 APK arm64 | CI artifact | PENDIENTE al escribir |
| Instalación junto a Play | HONOR 200 | PENDIENTE |
| Shell real / Go / PRoot | HONOR 200 + Fase 4–5 | PENDIENTE |
