# NewTermux — Fase 7: validación física preparada

- **Fecha:** 2026-09-19
- **Rama:** `cursor/fase7-validacion-fisica-2026-09-19-0b82` (PR [#15](https://github.com/joselofarias-byte/NewTermux/pull/15))
- **Base:** punta Fase 6 `6f83bccac86f7093b55d1721587e5251d956e8ba` (PR #14), **no** `main`
- **Regla:** sin merge, sin Release, sin desinstalar Play, sin migrar datos, sin secretos, **sin éxito físico declarado**, sin TBM

---

## 1. APK canónico (no inventar otro)

El único APK que esta fase pide instalar en HONOR 200 es el de Fase 6:

| Campo | Valor |
| --- | --- |
| Archivo | `termux-app_v1.6.2+f3eb365-apt-android-7-github-debug_arm64-v8a.apk` |
| SHA-256 | `2345efa03c8677778fb418f5acc4bfd2a69e9bf383932e0a4d786247dcbd11e4` |
| Commit | `f3eb365` |
| applicationId | `com.newtermux.dev` |
| variant | `coexistDebug` |
| Run | https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701028 |
| Job android-7 | https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701028/job/105906757364 |

Un commit posterior (docs, este PR) **puede** producir otro `versionName` si se rebuild. **No lo uses.** El hash canónico sigue siendo `2345efa0…` @ `f3eb365`.

---

## 2. Script

`scripts/fase7/honor200-validate.sh`

- Instrucciones en comentarios **antes** de `set -u`.
- Idempotente: tmp en `mktemp`, trap de limpieza, overwrite del Markdown.
- No destructivo: `FASE7_INSTALL=0` por defecto (no `pkg install`, no `proot-distro install`). Nunca escribe `/data/data/com.termux`. Nunca desinstala Play. Codex no se instala.
- Aborta si el PREFIX es `com.termux`.
- Redacta tokens (`ghp_`, `github_pat_`, `Bearer`, `API_KEY`, etc.).
- Un solo Markdown: `~/storage/downloads/NEWTERMUX_FASE7_HONOR200.md` en Termux; fallback `docs/NEWTERMUX_FASE7_HONOR200_CLOUD.md` o `FASE7_REPORT` en cloud.
- El Markdown abre con instrucciones + bloque ejecutable, luego tres tablas: CI/cloud, HONOR 200, bloqueado.

Cubre: identidad, ABI, SHA del APK si está en Downloads, shell, PTY, APT/pkg, Go (marcador goargs), PRoot/Debian, Node, Git, gh, Codex.

---

## 3. Cloud vs HONOR

Este VM no es Android/Termux (`PREFIX` vacío, sin `linker64`/`pkg`). El script sale **2** y clasifica HONOR como `PENDIENTE-HARDWARE`. Eso **no** es éxito físico.

Workflow: `.github/workflows/fase7_validate.yml` (artefacto del reporte cloud; no Release).

---

## 4. Tabla PASS / FAIL / PENDIENTE-HARDWARE

| Ítem | Ámbito | Estado |
| --- | --- | --- |
| Script + instrucciones + redacción | git | PASS |
| APK canónico `2345efa0…` @ `f3eb365` documentado | git | PASS |
| Clasificación CI / HONOR / bloqueado | cloud | PASS (se anota el run) |
| Identidad / ABI / shell / PTY | HONOR 200 | **PENDIENTE-HARDWARE** |
| Paquetes / Go / PRoot / Debian | HONOR 200 | **PENDIENTE-HARDWARE** |
| Node / Git / gh / Codex | HONOR 200 | **PENDIENTE-HARDWARE** |
| gh auth / Codex keys | credenciales | **BLOQUEADO** (no en el script) |
| `~/storage/downloads` en este VM | acceso | **BLOQUEADO** |
| Éxito físico HONOR 200 | dispositivo | **no declarado** |

---

## 5. Recomendación

La misión está **preparada** para que el usuario pruebe en HONOR 200 cuando quiera: APK coexist debug con hash, identidad distinta de Play, script no destructivo. **No** está validada en el teléfono. No mergear. No Release. No desinstalar Play.
