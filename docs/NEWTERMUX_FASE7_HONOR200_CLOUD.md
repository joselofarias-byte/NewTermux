# NewTermux Fase 7 — reporte de validación

**Leer estas instrucciones antes del bloque ejecutable.**

Este archivo no declara éxito físico en HONOR 200. El APK canónico es
`termux-app_v1.6.2+f3eb365-apt-android-7-github-debug_arm64-v8a.apk` @ `f3eb365` con SHA-256
`2345efa03c8677778fb418f5acc4bfd2a69e9bf383932e0a4d786247dcbd11e4` (coexist / `com.newtermux.dev`). No uses otro hash.

1. No desinstalar Termux Play. No migrar PREFIX. No mergear. No Release.
2. Instalar solo el APK debug arm64 del run CI https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701028
3. `sha256sum` debe coincidir **exactamente** con `2345efa03c8677778fb418f5acc4bfd2a69e9bf383932e0a4d786247dcbd11e4`.
4. Abrir NewTermux Dev y correr el script **dentro** de `com.newtermux.dev`.

## Bloque ejecutable (solo en HONOR 200, dentro de NewTermux Dev)

```bash
# Opcional: termux-setup-storage
bash scripts/fase7/honor200-validate.sh
# Paquetes (opt-in, sigue sin tocar Play):
# FASE7_INSTALL=1 bash scripts/fase7/honor200-validate.sh
```

- Generado: 2026-09-19T13:56:15Z
- Host: Linux x86_64
- PREFIX: ∅
- ON_DEVICE: 0
- FASE7_INSTALL: 0
- Destino reporte: /workspace/docs/NEWTERMUX_FASE7_HONOR200_CLOUD.md
- ~/storage/downloads existe: no
- TBM: no modificado

## Comprobado en CI / cloud

| Ítem | Estado | Nota |
| --- | --- | --- |
| APK coexist debug arm64 canónico | **PASS** | termux-app_v1.6.2+f3eb365-apt-android-7-github-debug_arm64-v8a.apk SHA-256 2345efa03c8677778fb418f5acc4bfd2a69e9bf383932e0a4d786247dcbd11e4 @ f3eb365 https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701028 |
| Identidad Gradle CI | **PASS** | applicationId=com.newtermux.dev variantName=coexistDebug (Fase 3/6, no inventada) |
| Debug ≠ GitHub Release | **PASS** | DEBUG-NOT-RELEASE.txt + release.yml aborta; artefacto CI only |
| Unit tests / wrapper / Fase6 guard | **PASS** | runs 35446701056 / 35446701106 / 35446701043 |
| Fase4 goargs ausente en producto | **PASS** | golang no está en el APK; receta oficial 3:1.27.1 sin runtime1.go |
| Fase5 PRoot no está en el APK | **PASS** | guest = proot-distro post-bootstrap; scripts/docs only |
| Host git (agente, no Termux) | **PASS** | git version 2.43.0  |
| Host gh (agente, no Termux) | **PASS** | gh version 2.99.0 (2026-09-01) https://github.com/cli/cli/releases/tag/v2.99.0  |
| TLS host a repos oficiales | **PASS** | packages.termux.dev + deb.debian.org (sin --insecure) |
| Producto sin goargs | **PASS** | sin hits de producto |
| Script no copia PREFIX Play | **PASS** | sin cp/rsync del árbol Play |

## Pendiente en HONOR 200

| Ítem | Estado | Nota |
| --- | --- | --- |
| Identidad / ABI / shell / PTY / paquetes | **PENDIENTE-HARDWARE** | PREFIX='' uname=Linux pkg=no linker=no |
| Go Termux | **PENDIENTE-HARDWARE** | go-smoke no corre aquí |
| PRoot / Debian / Node / Codex | **PENDIENTE-HARDWARE** | proot Ubuntu ≠ proot-distro Termux |
| Git / gh en PREFIX nuevo | **PENDIENTE-HARDWARE** | git/gh de este VM son del agente |

## Bloqueado por hardware, credenciales o acceso

| Ítem | Estado | Nota |
| --- | --- | --- |
| HONOR 200 / linker Android / pkg | **BLOQUEADO** | este entorno no es el teléfono; no simular |
| Credenciales gh / Codex | **BLOQUEADO** | sin tokens en CI ni en el repo |
| ~/storage/downloads | **BLOQUEADO** | ruta Termux ausente en cloud; fallback a docs/ o FASE7_REPORT |

## Referencia APK (no sustituir)

| Campo | Valor |
| --- | --- |
| Archivo | termux-app_v1.6.2+f3eb365-apt-android-7-github-debug_arm64-v8a.apk |
| SHA-256 | 2345efa03c8677778fb418f5acc4bfd2a69e9bf383932e0a4d786247dcbd11e4 |
| Commit | f3eb365 |
| applicationId | com.newtermux.dev |
| variant | coexistDebug |
| Run | https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701028 |
| Job | https://github.com/joselofarias-byte/NewTermux/actions/runs/35446701028/job/105906757364 |

Secretos redactados. No hay claves en este Markdown.
