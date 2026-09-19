# NewTermux — Session handoff 2026-09-19

Handoff parcial de **Fase 1 + Fase 2**. Fases 3–7 no implementadas.  
Repo: `https://github.com/joselofarias-byte/NewTermux`  
Mantenedor de esta sesión: agente Cursor sobre el fork JoseloFarias.  
**No hay éxito físico en HONOR 200.**

---

## PRs y ramas

| Pieza | URL / ref | Estado |
| --- | --- | --- |
| Fase 1 auditoría | https://github.com/joselofarias-byte/NewTermux/pull/9 | DRAFT, rama `cursor/audit-fase1-2026-09-19-0b82` |
| Fase 2 port 1.6.2 | (este PR) rama `cursor/port-banner-1.6.2-conservador-2026-09-19-0b82` | DRAFT, **no mergear a main** |
| Port previo Banner | https://github.com/joselofarias-byte/NewTermux/pull/8 | OPEN; usado como *fuente* del merge, no cerrado ni mergeado |
| `main` | `94c5e7fbd9b945c1e952104a89b4936810c02026` | intacto |

Informes:

- `docs/NEWTERMUX_AUDIT_FASE1_2026-09-19.md`
- `docs/NEWTERMUX_FASE2_PORT_2026-09-19.md`

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

## 5. Coexistencia (Fase 3 — no hecha)

`com.termux` debug/release **choca con Termux Play**. Variante `demo` = `com.termux.demo` (UI sí, shell no).  
Siguiente PR debe analizar authorities, PREFIX, bootstrap y firma **antes** de tocar IDs. No replace textual.

## 6. Go / goargs (Fase 4 — no hecha)

`rg goargs` = 0 en el árbol. Bootstrap pin: `termux-packages` `2026.02.12-r1+apt.android-7`. Checksum verify sigue comentado. No inspeccionar zip = Go sano **no** está demostrado.

## 7. PRoot / Debian / Node / Codex (Fase 5 — no hecha)

No hay código de PRoot en la app. Queda post-bootstrap.

## 8. CI / artefactos (Fase 6 — parcial)

- Esperado al abrir PR a `main`: workflow `Build` (`debug_build.yml`) `assembleDebug` apt-android-5/7.
- Reportar run URL + conclusion cuando exista. No es release.
- Unit tests / wrapper validation siguen sin trigger útil.

## 9. Pruebas

| Ámbito | Estado |
| --- | --- |
| Comprobado en git/API | genealogía, merge limpio, preservaciones, IDs |
| Comprobado en CI cloud (Fase 1 `main`) | Build `34510906662` success |
| CI de esta rama Fase 2 | PENDIENTE al momento de escribir el handoff |
| HONOR 200 | PENDIENTE — no declarar éxito |

## 10. Riesgos y reversión

- Instalar APK `com.termux` sobre Termux Play (o al revés) = firma/datos. No hacerlo.
- No revertir NO_GO Quantus (batería/CPU/toolchain).
- PR #8 + Compose es un cambio de UI grande; humo físico pendiente.
- Reversión: no mergear este PR. `main` permanece en `94c5e7f`.

Plan TBM: **no se modificó TBM**. Migración selectiva sigue siendo decisión posterior.

## 11. Recomendación

El port conservador está listo para **revisión y CI**, no para merge ni para reemplazar Play.  
Siguiente paso humano/agente: Fase 3 en PR nuevo (identidad de coexistencia) **después** de CI verde de esta rama y de no haber mergeado a `main`.

## 12. Tabla PASS / FAIL / PENDIENTE (sesión)

| Ítem | Fase | Estado |
| --- | --- | --- |
| Auditoría verificable + PR #9 draft | 1 | PASS |
| Upstream = Banner, no Termux oficial | 1 | PASS |
| Port 1.6.2 desde `main` + PR #8 | 2 | PASS |
| Personalizaciones + NO_GO | 2 | PASS |
| applicationId intacto | 2 | PASS |
| Android 16 review documentada | 2 | PASS (estática) |
| PendingIntent IMMUTABLE | 2 | FAIL (documentado) |
| Unit tests CI | 1–2 | FAIL |
| CI Build esta rama | 2 | PENDIENTE |
| HONOR 200 | 7 | PENDIENTE |
| Coexistencia Play | 3 | PENDIENTE |
| goargs / bootstrap zip | 4 | PENDIENTE |
| PRoot/Debian/Codex | 5 | PENDIENTE |
