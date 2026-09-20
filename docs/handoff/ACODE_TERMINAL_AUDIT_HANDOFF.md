# NewTermux + Acode Audit Handoff

Self-contained continuation document for ChatGPT / later agents. Do **not** assume the originating conversation. This research branch audits Acode’s terminal/Linux stack against NewTermux, then lands **only** low-risk, high-benefit Termux-compatible changes.

**Hard constraints (still in force):**

1. Do **not** replace NewTermux’s native Termux backend with Acode’s terminal.
2. Do **not** transplant xterm.js (no justified advantage over `TerminalView` + JNI PTY).
3. Preserve Termux package compatibility (`applicationId=com.termux`, `$PREFIX`, bootstrap, plugins).
4. Prefer Termux **upstream** when it already covers the same need.
5. Do **not** modify `main`/`master` directly. Work stays on `research/acode-terminal-integration`.
6. No giant refactors. Don’t break existing sessions / RUN_COMMAND / failsafe.
7. No exported service that can execute commands may be left insecure. No new remote/local command-execution surface.
8. Licenses: Acode MIT was reviewed; **no Acode source was copied**. Patterns were reimplemented.
9. Alpine/Debian PRoot is an **optional future additional environment**, never a Termux replacement.

---

## Objetivo

1. Map Acode-Foundation/Acode terminal/Linux architecture (FGS, PTY multiplexer, Alpine/PRoot, OSC, SAF, security).
2. Map joselofarias-byte/NewTermux for the same topics (sessions, FGS, reconnect, failsafe, CLI, ADB/Shizuku, exported components).
3. Decide what is reusable vs discarded, preferring Termux upstream over Acode.
4. Implement only low-risk high-benefit items aligned with priorities 1–6 and 10.
5. Leave a complete handoff so work can continue without this conversation.

**Priorities (original order):**

1. Maximum possible session persistence
2. Recovery/reconnect after Android kills UI or processes
3. Robust foreground service
4. Clear visual status indicators
5. Restart/reconnect buttons
6. Failsafe/recovery mode
7. Possible future Alpine/Debian PRoot as ADDITIONAL env (never replacing Termux)
8. CLI ↔ NewTermux Android features bridge
9. Clean integration with ADB/Shizuku features being developed
10. Security: no insecure exported command-execution services

---

## Repositorios analizados

| Repo | URL | What was used |
|---|---|---|
| **NewTermux (this repo)** | https://github.com/joselofarias-byte/NewTermux | Working tree at start of research: `main` @ `94c5e7f` (“Quantus: hard-disable native Android build after NO_GO decision”). Branch created: `research/acode-terminal-integration`. |
| **Acode** | https://github.com/Acode-Foundation/Acode | Read-only sparse clone at `/tmp/acode-src` (not committed). HEAD reviewed: `20e4fa52a125a5f8c0105184b2347ea49273d9dc` (“Update zh-cn.json and zh-hant.json (#2914)”). License: MIT (`license.txt`, Foxdebug / Ajit Kumar, 2020). |
| **Termux upstream** | https://github.com/termux/termux-app | Manifest + wiki checked so Acode patterns were not adopted when Termux already has a more mature equivalent. `RUN_COMMAND` is `protectionLevel="dangerous"`. `TermuxService` remains untyped FGS at the historic `targetSdk=28` strategy. |

Acode files reviewed (sparse checkout + GitHub for missing bits):

- `src/plugins/terminal/src/android/TerminalService.java`
- `ProcessManager.java`, `Executor.java`, `BackgroundExecutor.java`, `ProcessServer.java`, `ProcessUtils.java`, `StreamHandler.java`, `AlpineDocumentProvider.java`
- `src/plugins/terminal/scripts/init-sandbox.sh`, `init-alpine.sh`, `rm-wrapper.sh`
- `src/plugins/terminal/www/Terminal.js`, `Executor.js`, `plugin.xml`
- `src/components/terminal/terminalManager.js`, `terminal.js`
- PRoot plugin layout (per-ABI `libproot*.so`, `libtalloc.so`, `libaxs.so`, Alpine rootfs) — binaries not vendored into NewTermux
- Searches: WebSocket, localhost, pending intents, bind service, DocumentProvider, SAF, page size, failsafe, recovery, OSC

NewTermux files reviewed:

- `app/src/main/AndroidManifest.xml`
- `TermuxService.java`, `TermuxActivity.java`, `RunCommandService.java`, `TermuxInstaller.java`
- `TermuxTerminalSessionActivityClient.java`, `TermuxTerminalSessionServiceClient.java`, `MiniTerminalPipView.java`, `TerminalSession.java` / JNI `termux.c`
- `termux-shared` shell manager, `ExecutionCommand`, `TermuxConstants`, DocumentsProvider, `TermuxAmSocketServer`
- `com.newtermux.features.*` (settings, SSH, root toggle, STT)
- Strings, shortcuts, extra keys, CI workflows

---

## Estado inicial

- Fork of Termux (`com.termux`) branded NewTermux / JoseloFarias. Version **1.5.5**, `targetSdkVersion=28`, `compileSdkVersion=36`, `minSdkVersion=21`.
- Classic Termux architecture: sessions live in **non-exported** `TermuxService` (FGS, `START_STICKY`); `TermuxActivity` binds with `startService` + `bindService(..., 0)` and swaps Activity/Service `TerminalSessionClient`s so the UI can die without killing shells.
- Sessions are **in-memory only**. SharedPreferences store the current session **handle**, not transcripts/PIDs. Process death loses all PTYs. Sticky restart previously left an **empty** FGS.
- Failsafe existed as launcher shortcut + `EXTRA_FAILSAFE_SESSION` → `/system/bin/sh`, but:
  - no in-app Failsafe button if shortcuts are gone
  - NewTermux startup-script injection ran even on failsafe sessions
  - no Restart/Reconnect UI
- Fork regression: `RUN_COMMAND` permission was `protectionLevel="normal"` (PROGRESS_LOG: “dangerous → normal”). Any app could request it at install with no prompt. Second gate `allow-external-apps` still defaulted to `false`.
- Notification PendingIntents used flags `0` (not `FLAG_IMMUTABLE`); all actions shared requestCode `0`.
- No Alpine/PRoot product surface. No Shizuku/ADB integration. No axs/xterm.js/WebSocket command server.
- Root toolbar toggle does **not** wrap the session in `su` (`getShellPrefix()` unused).
- Quantus/Adreno native-build work on `main` was explicitly NO_GO; this research branch does not reopen that.

---

## Arquitectura de Acode

Acode is **not Termux**. It is a Cordova editor that keeps one Alpine minirootfs alive under PRoot.

```
Editor (xterm.js)
    HTTP/WS 127.0.0.1:8767  ──►  axs (libaxs.so) PTY multiplexer inside Alpine
                                      ▲
Cordova Executor ──bind/start──► TerminalService (FGS specialUse, exported=false)
                                      │
                              sh -c "source init-sandbox.sh"
                                      │
                              libproot-xed.so -0 + bind mounts + Alpine 3.21
```

**Process spawn:** `Executor.start("sh")` → `TerminalService` `ProcessBuilder("sh","-c", cmd)`. For Alpine, `ProcessManager` prefixes `source $PREFIX/init-sandbox.sh`. `startAxs()` writes that script into host `sh` so PRoot/axs become children of the FGS.

**UI reconnect:** `localStorage` key `acodeTerminalSessions` stores axs PTY pids. On restore, if `$PREFIX/pid` is alive (`kill -0`), tabs reattach WebSockets (`reconnecting: true`) without `POST /terminals`. axs keeps PTY + scrollback while WS is down. **Not persisted across process death.**

**FGS:** `foregroundServiceType="specialUse"`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `START_STICKY`, notification Exit + wake lock with `FLAG_IMMUTABLE`. No `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. `startForeground(id, notification)` two-arg API.

**Failsafe:** skips PRoot; `exec /system/bin/linker64 $PREFIX/axs -c sh`. Host shell + **same unauthenticated localhost API**.

**OSC:** guest `acode` CLI emits OSC 7777 `open;type;path`. xterm handles it; **SSH is denied**.

**16 KB:** Acode PR #1649 replaced PRoot `.so` binaries; no Java page-size probe.

**Storage:** PRoot binds `/data`, `/sdcard`, `/storage`, `/dev`, `/proc`, `/sys`, Android partitions, and `$PREFIX/public` → `/public`, `/home`, `/root`. `/proc/$$/fd` is probed before binding `/dev/fd` because PRoot `realpath` cannot sanitize `pipe:[n]`.

**SAF:** `AlpineDocumentProvider` exported, `MANAGE_DOCUMENTS`, document IDs = **absolute paths**, `getFileForDocId` does not canonicalize into the tree.

**Security-critical localhost API (axs, default 8767):**

- `POST /terminals` — create PTY
- `GET /terminals/{pid}` — WebSocket attach, no token
- `POST /terminals/{pid}/terminate`
- `POST /execute-command` — **arbitrary `sh -c`**
- CORS defaults to `https://localhost` only — **does not constrain native Android apps** on loopback

**`ProcessServer`:** ephemeral `ServerSocket(0)` WebSocket; **every `onOpen` starts `ProcessBuilder(cmd)`**. No handshake secret.

There is **no** Termux-style `RUN_COMMAND` intent. External exec is via localhost once axs is up.

Debian is **not** in Acode core (Distro Manager plugin only).

---

## Arquitectura de NewTermux

```
TermuxActivity  --startService + bindService(flags=0)-->  TermuxService
       |                                                      |
  LocalBinder (same process, not exported)              TermuxShellManager
       |                                                      |
  ActivityClient  <--swap on unbind-->  ServiceClient         |
                                                              ├── TermuxSession[]  (JNI PTY + TerminalSession)
                                                              ├── AppShell[]       (background Runtime.exec)
                                                              └── PendingPluginExecutionCommands[]
```

**Persistence that already works (Termux heritage):** Activity death does **not** kill shells. Client swap avoids leaking the Activity. FGS notification keeps the process in the foreground bucket. User and auto wake locks exist (auto: Activity `onStop` → `acquireWakeLockAuto()`).

**What process death does:** PTY master, I/O threads, and child shells die with the app UID. `START_STICKY` restarts an empty service. This research branch now **stops that empty FGS** instead of idling it. True reconnect of an orphaned `/proc/<pid>` from a dead zygote is **impossible** without a daemon outside the app UID (Android will not give that).

**Plugin path:** exported `RunCommandService` (`permission=com.termux.permission.RUN_COMMAND`) → non-exported `TermuxService.ACTION_SERVICE_EXECUTE`. Second gate: `allow-external-apps` in `termux.properties` (default `false`). Canonical path + executable validation + symlink-applet handling already exist.

**Failsafe (Termux):** skip `$PREFIX/bin` login shells; `/system/bin/sh`; keep Android `PATH`/`TMPDIR`.

**OSC:** titles, colors, clipboard **set**; title-report OSC disabled (security). No Acode OSC 7777.

**`termux-am`:** unix socket, peer UID must be app UID or root.

**DocumentsProvider:** `com.termux.documents`, `MANAGE_DOCUMENTS`. Document IDs are also absolute paths (same class of issue as Acode; **not hardened here** — SAF compatibility risk).

**targetSdk 28** is why untyped FGS still runs on Android 14+ devices. Bumping targetSdk is a separate, high-risk project (Play FGS types, POST_NOTIFICATIONS, PendingIntent crashes, background activity starts). Termux upstream still uses this strategy; this branch does **not** copy Acode `specialUse` yet.

---

## Tabla comparativa Acode -> NewTermux

| Acode feature | Acode implementation | NewTermux equivalent | Verdict | Reason | Risk | Priority |
|---|---|---|---|---|---|---|
| Session persistence while UI dead | FGS + axs in-memory PTYs + `localStorage` pids | `TermuxService` holds `TermuxSession`; Activity rebinds by stored handle | **Keep Termux** (already better for native PTY) | Termux sessions **are** the PTY; no extra multiplexer | Low | 1 |
| Persist across **process** death | Not actually persisted; sticky empty FGS | Same limitation; now stop empty FGS | **Reimplemented cleanup** | Cannot restore dead PTYs in-UID | Low | 1–2 |
| UI ↔ process reconnect | WS reattach to axs pid if axs alive | Bind + `getCurrentStoredSessionOrLast()` | **Keep Termux + add Reconnect button** | Rebind/reattach; not a new protocol | Low | 2, 5 |
| FGS | `specialUse` + IMMUTABLE PIs + POST_NOTIFICATIONS | Untyped FGS, flags `0`, Exit/Wake only | **Adapt PI flags + failsafe action; defer FGS type** | Termux upstream also untyped at targetSdk 28 | Med if type added now | 3 |
| Status indicators | Editor tabs + axs liveness | Session pips; notification text | **Reimplemented strip + pip dead/FS styling** | Pure UI | Low | 4 |
| Restart/reconnect buttons | Close tab / restore pids | Reset = emulator only; Kill; no Restart | **Reimplemented Restart/Reconnect/Failsafe** | Recreates shell, does not magically revive PID | Low | 5 |
| Failsafe | Skip PRoot; host `sh` via linker + axs | `/system/bin/sh` session | **Keep Termux failsafe; expose in UI/notification/bootstrap** | Do not copy Acode linker/axs failsafe | Low | 6 |
| Startup script vs failsafe | n/a | Injected into **all** new sessions | **Fixed: skip on failsafe** | Failsafe must stay clean | Low | 6 |
| Alpine/PRoot | Core userspace | User can `pkg install proot-distro` | **Deferred (optional extra env)** | Constraint 9 | High if bundled now | 7 |
| xterm.js | Cordova UI | Native `TerminalView` | **Not advisable** | No advantage; huge UI rewrite | High | — |
| axs HTTP/WS multiplexer | Unauthenticated loopback command API | None | **Not advisable** | Any UID can `POST /execute-command` | Critical | 10 |
| ProcessServer spawn-on-connect | WS starts ProcessBuilder | None | **Not advisable** | Same class of bug | Critical | 10 |
| OSC 7777 editor open | Guest CLI → editor; SSH denied | OSC 0/2/4/52 only | **Deferred pattern** | Useful later; not needed for persistence | Med (guest→UI) | 8 |
| CLI → Android | OSC + SAF | `termux-api` plugin, `termux-open`, `termux-am`, pending prefs write | **Keep Termux; no new exec bridge** | Don’t add a localhost command server | High if copied | 8 |
| ADB/Shizuku | None in terminal plugin | None (`ADB_SHELL` stub commented; root toggle unused) | **Deferred** | Separate in-progress work; don’t block | Med | 9 |
| RUN_COMMAND / exported exec | None (good) | Exported `RunCommandService` + `normal` permission (fork regression) | **Restored upstream `dangerous`** | Plugin-compatible; Tasker already requests it | Low | 10 |
| PendingIntent flags | IMMUTABLE | `0` | **Reimplemented helper** | Prepares API 31+; safe at minSdk 21 with M+ guard | Low | 3, 10 |
| DocumentsProvider IDs | Absolute paths, no canonicalize | Same Termux-upstream pattern | **Not copied; not changed** | SAF breakage; needs dedicated audit | Med | 10 |
| `/proc/self/fd` bind sanitizer | `can_bind()` on `/proc/$$/fd` | JNI closes extra FDs in child | **Keep Termux JNI**; Acode script only relevant if PRoot is added later | Copy as comments then, not now | Low | 7 |
| 16 KB page size | Rebuilt PRoot `.so` | Bootstrap already 16 KB; app JNI had no explicit flag | **Added `LOCAL_LDFLAGS -Wl,-z,max-page-size=16384`** | Verified PT_LOAD align `0x4000` in this environment | Low | — |
| Wake lock | FGS toggle | FGS toggle + auto onStop | **Keep NewTermux auto lock** | Helps OEM killers; battery cost known | Low | 1, 3 |
| `exported` Settings | n/a | `SettingsActivity` exported (launcher shortcut) | **Left exported** | Launcher shortcut would break if false | Low | 10 |
| ReportActivity | n/a | implicit default export | **Set `exported=false`** | No intent-filter / shortcut | Low | 10 |

---

## Hallazgos importantes

1. **Acode’s interesting idea is “FGS + reconnectable PTY multiplexer.”** The implementation puts an unauthenticated command-and-PTY HTTP server on `127.0.0.1`. On Android, loopback is shared across UIDs. **Do not copy axs or ProcessServer.**
2. **NewTermux already has the right persistence model** (sessions in a non-exported FGS). The gaps were UX (no Restart/Failsafe in-app), sticky empty FGS, failsafe startup-script injection, and the RUN_COMMAND permission downgrade.
3. **True session restore after process death is impossible** inside the app UID. Document this to users; Restart creates a **new** shell with remembered name/cwd/failsafe flag.
4. **`RUN_COMMAND` `normal` was a real security regression** vs Termux upstream `dangerous`. Combined with `allow-external-apps=true`, any installed app could run `$PREFIX` executables.
5. **Termux upstream does not use `foregroundServiceType=specialUse` today** at targetSdk 28. Prefer that strategy until a coordinated targetSdk bump.
6. **Acode failsafe is more dangerous than Termux failsafe** (host `sh` + axs localhost). Keep Termux `/system/bin/sh` without axs.
7. **DocumentsProvider absolute-path IDs** exist in **both** codebases. Hardening is a dedicated SAF project, not this branch.
8. **No Shizuku/ADB session layer** exists to “hook” yet. Leave integration points documented, don’t invent a parallel shell runner.
9. **16 KB:** `$PREFIX` bootstrap (2026.02.12-r1) is already 16 KB-built. App JNI now explicitly requests 16 KB LOAD alignment; confirmed `0x4000` on arm64-v8a/armeabi-v7a/x86/x86_64 in this CI-like environment. Device load on HONOR 200 is still physical.
10. **Root toggle is cosmetic.** Out of scope here.

---

## Funciones reutilizables

Reusable as **patterns** (reimplemented, not copied):

| Pattern | From | How NewTermux used it |
|---|---|---|
| `FLAG_IMMUTABLE` on app-owned notification PIs | Acode TerminalService + Android 12 guidance | `PendingIntentFlags.immutableUpdateCurrent()` |
| Don’t restore UI session list if backend is dead | Acode `isAxsRunning()` wipe | Sticky null-intent restart → `updateNotification()` stops empty FGS |
| Failsafe reachable without relying on launcher shortcuts | Acode settings toggle (different mechanism) | Notification action + toolbar + long-press New + context/pip menus + bootstrap dialog |
| Distinct PendingIntent request codes | Acode used 0 vs 1 | Content/Exit/Wake/Failsafe = 0/1/2/3 |
| Visual dead vs live session | Acode tab restore skip | Pip EXIT overlay + red border; status strip |
| `/proc/$$/fd` bind comments | Acode `init-sandbox.sh` | **Documented only** for a future optional PRoot extra env |
| OSC-not-on-SSH | Acode | **Documented** for a future NewTermux CLI bridge |

Termux-upstream items we **restored or kept** rather than taking Acode’s version:

- `RUN_COMMAND` `dangerous`
- Native PTY in `TermuxService` (not axs)
- Failsafe as `/system/bin/sh` without PRoot/linker
- Untyped FGS while `targetSdk=28`

---

## Funciones descartadas y motivo

| Discarded | Motivo |
|---|---|
| Replace Termux backend with Acode TerminalService/Executor | Constraint 1; Cordova Messenger + `sh -c` is a worse PTY model |
| xterm.js | Constraint 2; NewTermux already has `TerminalView` |
| axs / acodex_server / port 8767 | Unauthenticated `POST /execute-command` and PTY create on loopback |
| `ProcessServer` spawn-on-connect WebSocket | Connect-to-exec; XSS in WebView would be RCE |
| Alpine as default environment | Constraint 9; breaks `pkg` / `$PREFIX` / plugins |
| Shipping Acode PRoot `.so` / Alpine rootfs | License mix (PRoot historically GPL), Play packaging, 16 KB binary provenance; use `proot-distro` later if wanted |
| Acode DocumentsProvider | Absolute-path IDs + prefix `isChildDocument`; Termux already has a provider |
| FGS `specialUse` + Play subtype property | Termux upstream doesn’t; targetSdk 28 still works; Play policy cost without bumping SDK |
| Global `usesCleartextTraffic=true` | Only needed for axs HTTP |
| JS `kill -9` any PID / `loadLibrary` | Acode already disabled loadLibrary; don’t add kill-any-pid to Termux |
| Bind-mount `/data` into a fake-root guest | Enlarges blast radius vs Termux prefix model |
| New exported command API | Constraint 7/10 |
| SettingsActivity `exported=false` | Would break the launcher Settings shortcut |

---

## Riesgos de seguridad

### Fixed on this branch

- **`RUN_COMMAND` `normal` → `dangerous`.** Third-party apps must be granted the permission in system Settings (Additional permissions). Termux:Tasker / Widget already request it. **Existing sideload installs** that got the permission automatically under `normal` may need a re-grant after upgrade — intended.
- **Notification PendingIntents** now IMMUTABLE (API 23+) with unique request codes. Service is still `exported=false`; actions are Exit / Wake / Failsafe only (Failsafe creates a shell **inside** the existing non-exported service).
- **`ReportActivity` `exported=false`.**
- **Failsafe notification action** uses explicit component + non-exported service. Not a plugin API. Constant is internal: `com.termux.service_new_failsafe_session`.

### Remaining (documented, not silently “fixed”)

| Risk | Status |
|---|---|
| `allow-external-apps=true` + granted RUN_COMMAND = arbitrary `$PREFIX` exec | By design (Tasker). Default is `false`. Do not default true. |
| `SettingsActivity` exported | Required for shortcut. No command extras. |
| File share/view aliases exported | Needed; disable flags exist in properties |
| `TermuxOpenReceiver$ContentProvider` exported, gated by RUN_COMMAND | Stronger now that permission is dangerous |
| DocumentsProvider absolute paths | Termux upstream; dedicated follow-up |
| `sharedUserId=com.termux` | Plugins share UID; upstream design |
| Auto wake lock on `onStop` | Battery / OEM “running in background” perception |
| Plugin result PendingIntents in `TermuxPluginUtils` still `FLAG_UPDATE_CURRENT` without IMMUTABLE | Not changed: those wrap **caller-provided** PIs; mutating flags can break Tasker |
| SSH profile command concatenation unsanitized | Out of scope (not terminal persistence) |
| Bootstrap zip SHA-256 verify commented out in `app/build.gradle` | Out of scope; noted for later |
| No new localhost/WebSocket exec surface | Intentionally not added |

### What must never land

- Exported service that accepts argv/script without both RUN_COMMAND **dangerous** and `allow-external-apps`
- Loopback HTTP/WS command API without SO_PEERCRED + per-socket secret
- Copying Acode `Executor.spawn` / `ProcessServer`

---

## Decisiones tomadas

1. **Keep native Termux PTY + TermuxService.** Acode is a reference for UX/FGS hygiene only.
2. **Prefer Termux upstream** for RUN_COMMAND, failsafe shell, and FGS typing timeline.
3. **Reimplement patterns from scratch** (status text, PI flags, failsafe actions). No Acode Java/JS/sh copied into the tree.
4. **Maximum persistence = keep FGS sessions + stop lying about sticky restore.** Empty sticky FGS is not persistence.
5. **Restart = new shell** with same name/cwd/failsafe. Reconnect = rebind UI to live sessions / restart service bind.
6. **Failsafe must not run `startup-script.sh`.**
7. **Alpine/PRoot = future extra env** via Termux `proot-distro`, not Acode’s sandbox scripts.
8. **Do not invent ADB/Shizuku** on this branch; leave a documented hook (don’t run sessions through a new exported runner).
9. **Don’t bump targetSdk.**
10. **Don’t export TermuxService.** Failsafe-from-notification stays inside it.

---

## Implementación realizada

All of these are NewTermux/Termux-shaped; none replace the backend.

1. **Security:** `RUN_COMMAND` `dangerous`; `ReportActivity` `exported=false`; IMMUTABLE notification PIs.
2. **FGS robustness:** distinct PI request codes; Failsafe notification action; sticky restart with null intent calls `updateNotification()` which `requestStopService()` when there are 0 sessions/tasks and no wake lock.
3. **Status UI:** toolbar row `Service connected · session running|exited · failsafe · wake lock`; pip red/EXIT when dead; orange border + `FS` prefix when failsafe.
4. **Restart / Reconnect / Failsafe buttons** on the status row; long-press New Session = failsafe; pip menu Restart/Failsafe/Close; terminal context menu items.
5. **Failsafe recovery:** bootstrap error dialog Neutral = Failsafe; failsafe may bypass the 8-session cap so recovery still works.
6. **Failsafe purity:** `maybeRunStartupScript()` returns immediately when `isFailSafe`.
7. **16 KB:** `LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384` in app, terminal-emulator, and termux-shared `Android.mk`.
8. **CI:** `research/**` triggers debug APK workflow; unit-test workflow includes `main` and `research/**`.
9. **Tests:** `SessionStatusText` pure-Java formatter + unit tests.

---

## Archivos modificados

**Commit 1** (`90900b7` — implementation):

| File | Change |
|---|---|
| `app/src/main/AndroidManifest.xml` | RUN_COMMAND `dangerous`; ReportActivity `exported=false`; comments |
| `app/src/main/java/com/termux/app/TermuxService.java` | sticky cleanup, failsafe action, PI flags/codes |
| `app/src/main/java/com/termux/app/PendingIntentFlags.java` | **new** |
| `app/src/main/java/com/termux/app/TermuxActivity.java` | status bar, recovery actions, pip labels, menus |
| `app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java` | skip failsafe startup script; restart/reconnect; failsafe max bypass |
| `app/src/main/java/com/termux/app/terminal/MiniTerminalPipView.java` | dead/failsafe visuals |
| `app/src/main/java/com/termux/app/TermuxInstaller.java` | bootstrap Failsafe button |
| `app/src/main/java/com/newtermux/features/SessionStatusText.java` | **new** |
| `app/src/test/java/com/newtermux/features/SessionStatusTextTest.java` | **new** |
| `app/src/main/res/layout/newtermux_toolbar.xml` | status + action row |
| `app/src/main/res/values/strings.xml` | recovery strings |
| `app/src/main/cpp/Android.mk` | 16 KB |
| `terminal-emulator/src/main/jni/Android.mk` | 16 KB |
| `termux-shared/src/main/cpp/Android.mk` | 16 KB |
| `.github/workflows/debug_build.yml` | `research/**` |
| `.github/workflows/run_tests.yml` | `main` + `research/**` |

**Commit 2** (this handoff): `docs/handoff/ACODE_TERMINAL_AUDIT_HANDOFF.md`

Not committed: `/tmp/acode-src` clone, `local.properties`, Gradle/SDK caches.

---

## Código copiado/adaptado/reimplementado

| Item | Classification | Notes |
|---|---|---|
| Entire NewTermux/Termux Java/JNI that was already in tree | **Existing (GPL Termux + Apache terminal-emulator)** | Unchanged license headers |
| `PendingIntentFlags` | **Reimplemented from scratch** | Same *idea* as Acode `FLAG_UPDATE_CURRENT \| FLAG_IMMUTABLE` and Android 12 docs. No Acode lines copied. |
| Sticky empty-FGS stop | **Reimplemented from scratch** | Inspired by Acode “don’t restore pids if backend dead” |
| Status strip / Restart / Reconnect / Failsafe UI | **Reimplemented from scratch** | NewTermux toolbar language |
| Failsafe-from-notification | **Reimplemented from scratch** | Internal non-exported action; not RUN_COMMAND |
| Skip startup script on failsafe | **Bugfix** | NewTermux-specific |
| 16 KB `LOCAL_LDFLAGS` | **Reimplemented from scratch** | Acode did binary replacement of PRoot; we set linker flags on *our* JNI |
| Acode `TerminalService.java` / `init-sandbox.sh` / axs / xterm | **Not copied** | Reviewed only |
| Alpine/PRoot `.so` | **Not copied** | |

If a later agent copies Acode files, they must keep MIT headers and record it in this section.

---

## Licencias y atribuciones

| Component | License | Action |
|---|---|---|
| NewTermux / termux-app code | GPLv3 only (`LICENSE.md`) | All edits remain GPL |
| `terminal-emulator` / `terminal-view` | Apache 2.0 (Jack Pal / Termux) | JNI 16 KB flag only |
| `termux-shared` | See `termux-shared/LICENSE.md` | JNI 16 KB flag only |
| Acode | MIT (Foxdebug / Ajit Kumar, 2020) | **Reviewed, not incorporated** |
| axs (`bajrangCoder/acodex_server`) | Separate project; license not confirmed in the sparse tree | **Do not vendor** until license is verified |
| PRoot / talloc / Alpine minirootfs | GPL/LGPL / Alpine licenses | **Do not vendor** on this branch |
| Android SDK used to build in the agent VM | Google SDK license accepted locally for the agent; not shipped |

Fork identity (JoseloFarias / NewTermux / `com.termux` compatibility exception) is unchanged. See `FORK_IDENTITY.md`.

---

## Rama

```
research/acode-terminal-integration
```

Created from `main` (not `master`). User-requested name (not `cursor/…` prefix). Remote: `origin/research/acode-terminal-integration`.

Do not merge to `main` until physical validation (especially HONOR 200) and product review of the RUN_COMMAND permission restore.

---

## Commits

On this branch (implementation + handoff):

1. `90900b7` — `Harden session recovery, FGS notification actions, and RUN_COMMAND.`
2. `b7410d6` — `Add Acode terminal audit handoff for research continuation.`

Confirm with `git log origin/main..HEAD`.

---

## PR

https://github.com/joselofarias-byte/NewTermux/pull/17

Opened as **draft** against `main`. Update the PR body if later commits change scope.

---

## Build

Ran in the cloud agent (Android SDK installed locally to `$HOME/android-sdk`, compileSdk 36 / NDK via AGP, Java 21):

```
./gradlew :app:assembleDebug --no-daemon
# BUILD SUCCESSFUL in 34s
```

APKs (not committed):

- `app/build/outputs/apk/debug/termux-app_apt-android-7-debug_arm64-v8a.apk` (~38M)
- `armeabi-v7a`, `x86`, `x86_64`, `universal`

JNI PT_LOAD alignment after the 16 KB flag: **16384 (0x4000)** for `libtermux.so`, `libtermux-bootstrap.so`, `liblocal-socket.so` on arm64-v8a, armeabi-v7a, x86, x86_64.

GitHub Actions `debug_build.yml` now runs on `research/**` pushes as well.

`local.properties` is gitignored (`sdk.dir=/home/ubuntu/android-sdk` on the agent only).

---

## Tests

```
./gradlew :app:testDebugUnitTest :terminal-emulator:test --no-daemon
# BUILD SUCCESSFUL in 1m 2s
```

Aggregated JUnit XML: **151 tests, 0 failures, 0 errors** (21 suites), including:

- `com.newtermux.features.SessionStatusTextTest` (4 tests)
- existing `TermuxActivityTest` URL extractor
- full `terminal-emulator` suite

CI: `.github/workflows/run_tests.yml` now also fires on `main` / `research/**` and PRs to `main`.

---

## Resultados

- Audit map completed (this file).
- Low-risk persistence/FGS/reconnect/status/failsafe/security work landed without replacing Termux.
- Debug APKs compile; unit tests green; 16 KB ELF alignment verified in this environment.
- Lint: `:app:lintDebug` **fails with 22 errors / 145 warnings**, overwhelmingly **pre-existing** (`onBackPressed` MissingSuperCall, minSdk 21 vs API 23 `getColor`, TermuxInstaller NIO API 26, `android:tint` vs `app:tint`). This branch did not add a lint baseline or mass-fix those. New files did not introduce new Error-level findings beyond the pre-existing toolbar `android:tint` on the New Session button (unchanged attribute).
- No Acode code in the tree.
- No new exported execution surface.

---

## Errores/bloqueos

- **No physical device / emulator UI** in this agent. Toolbar, notification actions, failsafe, and Honor OEM killing cannot be click-tested here.
- **Lint is red on `main` already**; not treated as a regression of this branch.
- **Acode axs license** not confirmed; blocked any thought of vendoring axs.
- **GitHub code search 429** while probing termux-app; Manifest was fetched via `gh api` instead (`protectionLevel="dangerous"` confirmed).
- **Quantus NO_GO** on `main` is unrelated; not reopened.
- `gh` is read-only; PR created with `ManagePullRequest`.

---

## Pendientes de prueba física

Mark every item **PENDIENTE DE VALIDACIÓN FÍSICA**, especially **HONOR 200**:

1. Open app, start a long `sleep`/`htop`, Home / kill UI (not Force Stop). Confirm session still running via FGS notification, then return — same shell, same cwd.
2. Force Stop / process death. Confirm next launch is a **new** shell (not a fake reconnect) and that an empty FGS does not linger.
3. Notification **Failsafe** while UI is dead; session is `/system/bin/sh`; `echo $PREFIX` / `which bash` behave as failsafe; **no** startup-script side effects.
4. In-app Restart on a live session and on `[Process completed]`.
5. Reconnect when the status strip says disconnected (if OEM unbound the service).
6. Long-press New Session; pip `FS` label and orange border; EXIT overlay on dead pips.
7. Bootstrap failure path (simulate by breaking `$PREFIX` if safe) → Failsafe button.
8. `RUN_COMMAND`: a third-party app that only `uses-permission` without user grant must **not** start commands. After grant + `allow-external-apps=true`, Termux:Tasker still works.
9. Android 12+ notification actions (IMMUTABLE) don’t crash. Android 14/15 FGS still starts at targetSdk 28.
10. ARM64 16 KB page size: JNI `.so` load on HONOR 200; `pkg` binaries already from 16 KB bootstrap — confirm `apt`/`bash` run.
11. Wake lock auto acquire/release still works with the new notification actions (unique request codes).
12. Session cap: 8 normal sessions; Failsafe recovery still opens.
13. No new listening port on `127.0.0.1` (`ss -ltnp` / `netstat`) compared with `main`.
14. Launcher shortcuts: New session, Failsafe, Settings still work (`SettingsActivity` still exported).

---

## Estado actual

- Branch **`research/acode-terminal-integration`** pushed.
- Draft PR **#17** open.
- Handoff committed on the branch.
- Implementation of priorities **1–6 and 10** (the safe subset) is in; **7–9** documented as future.
- Ready for device testing and product decision on merging the RUN_COMMAND restore.

---

## Próximos pasos

Suggested order for the next agent / human:

1. **Physical validation** list above on HONOR 200 (and one stock Android 14/15 device if available).
2. If Tasker users complain after `dangerous` restore: document the Settings → Additional permissions grant. Do **not** revert to `normal`.
3. Optional follow-up (still low-risk): add IMMUTABLE only on **app-created** crash/plugin *notification* PIs in `TermuxCrashUtils` (not caller result PIs).
4. Optional: DocumentsProvider canonicalize-to-`$HOME` — separate PR, with SAF tests.
5. Optional: re-enable bootstrap SHA-256 in `downloadBootstrap`.
6. **Do not** bump `targetSdk` or add `specialUse` until there is a Play/F-Droid plan and `POST_NOTIFICATIONS` UX.
7. **Future extra env (priority 7):** `pkg install proot-distro` UX, not Acode `init-sandbox.sh`. If bind-mounting, reuse Acode’s `/proc/$$/fd` comments — reimplement, don’t copy GPL PRoot binaries without a license review.
8. **CLI bridge (priority 8):** prefer extending Termux OSC / `termux-api` / `termux-open`. If OSC 7777-style “open in editor” is wanted, deny it on SSH like Acode. Never axs.
9. **ADB/Shizuku (priority 9):** new non-exported APIs; do not route through `RunCommandService` without the existing two gates. Don’t use Acode `Executor.exec`.
10. Keep this handoff updated when those land.

**Continuation prompt (copy-paste):**

> Continue NewTermux research from `docs/handoff/ACODE_TERMINAL_AUDIT_HANDOFF.md` on branch `research/acode-terminal-integration` (PR https://github.com/joselofarias-byte/NewTermux/pull/17). Do not replace the Termux backend or add axs/xterm.js. Prefer Termux upstream. Next: physical validation notes and/or the optional follow-ups in “Próximos pasos”.
