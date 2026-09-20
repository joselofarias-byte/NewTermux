# NewTermux ChatGPT handoff — `login` Permission denied (2026-09-19)

Handoff for PR **#16** (`fix/prefix-aware-bootstrap-runtime`).  
Physical PASS is **not claimed**. HONOR 200 / Android 16 must retest the new coexist APK.

Repo: https://github.com/joselofarias-byte/NewTermux  
PR: https://github.com/joselofarias-byte/NewTermux/pull/16

---

## 1. Physical evidence (trust this; do not re-ask for the same test)

Device: HONOR 200 ELI-NX9 / Android 16.

Opening the terminal failed immediately:

```
exec("/data/data/com.newtermux.dev/files/usr/bin/login"): Permission denied
[Process completed (code 1)]
```

APK tested: successful ARM64 build at commit `ede4e4af`. Treat that APK as **not physically usable**.

Already established on device:

- Coexist APK installs as `com.newtermux.dev`.
- Embedded bootstrap was still the stock Termux bootstrap.
- `login` shebang pointed at `/data/data/com.termux/files/usr/bin/sh`.
- `bash` could not resolve runtime libs under the renamed package.
- `dash` executed, so this is **not** a generic Android 16 / SELinux exec ban.
- Root cause in `app/build.gradle`: coexist still downloaded official stock `bootstrap-*.zip`.

---

## 2. Diagnosis (cloud, against that evidence)

`login` in the official aarch64 bootstrap is **not an ELF**. It is a script:

```
#!/data/data/com.termux/files/usr/bin/sh
```

The kernel resolves that shebang before userspace. Under UID `com.newtermux.dev` the Play interpreter is `EACCES` → the exact `Permission denied` line.

The same script body then hard-codes Play paths for:

- `$PREFIX/bin/bash` / `$PREFIX/bin/sh` selection
- `LD_PRELOAD` of `libtermux-exec*.so`
- `. /data/data/com.termux/files/usr/etc/termux-login.sh`
- `exec "$SHELL" -l`

`sh` is a symlink to `dash`. `dash` and `bash` are aarch64 ELF with interpreter `/system/bin/linker64` (system linker; not a PREFIX linker). Both use `DT_RUNPATH=/data/data/com.termux/files/usr/lib`.

- `dash` only needs bionic (`libc.so` / `libdl.so`) → executes on device.
- `bash` needs `libandroid-support.so`, `libreadline`, `libiconv` from PREFIX → fails when RUNPATH is Play’s private dir.

`generate-bootstraps.sh` does **not** rebuild packages. It downloads official debs. `scripts/properties.sh` at pin `648666db` hardcodes `TERMUX_APP__PACKAGE_NAME="com.termux"` and then **overwrites** `TERMUX_APP_PACKAGE` from it. Exporting `TERMUX_APP_PACKAGE` in CI was a no-op.

CI at tip `97064b76`:

| Run | Workflow | Failure |
| --- | --- | --- |
| [35469071986](https://github.com/joselofarias-byte/NewTermux/actions/runs/35469071986) | Build | `:app:downloadBootstraps` refused stock zip (intentional) |
| [35469071995](https://github.com/joselofarias-byte/NewTermux/actions/runs/35469071995) | Prefix-aware coexist ARM64 | zip was created **inside Docker**, then `mv: cannot create regular file './bootstrap-aarch64.zip': Permission denied`. Retry loop captured `rc=$?` **after** a failed `if`, so `rc` was 0 and the generate step was marked success. Locate/verify then found no zip. |

Abandoned equal-length ELF surgery (`scripts/patch-bootstrap.sh`, `com.newtermux.app`, `.../fil`) remains refused. New PREFIX is 38 chars vs stock 31; in-place ELF `.rodata` replace cannot grow.

---

## 3. What this iteration changed

### Prefix-aware bootstrap (source of truth)

Owner commit `7e9795d` switched Prefix-aware CI to **`build-bootstraps.sh` in Docker** so apt/dpkg compile against `com.newtermux.dev` (ELF `.rodata` cannot grow in-place from official debs). That path is now green.

CI wiring that made the source build produce a zip + APK:

- Patch `TERMUX_APP__PACKAGE_NAME=com.newtermux.dev` in pinned `properties.sh`.
- Copy the package-builder Android SDK to a writable tree and install `platforms;android-33` + `build-tools;30.0.3` (`termux-am` died on a read-only SDK in run `35477142849`).
- Map bootstrap `bzip2` → `libbz2` (subpackage only at pin `648666db`).
- `docker cp` the zip from `/tmp` after AppArmor denied `mv` onto the bind-mount (run `35481800800` compiled every package, then lost the zip).
- After the source zip: `scripts/fase8/rewrite-bootstrap-prefix.sh` for leftover `/data/data/com.termux` **text** in termux-tools (`login` body, `pkg`, …). ELF is not rewritten.
- `scripts/fase8/verify-bootstrap-prefix.sh`:
  - **FAIL** if login shebang/body, text files, or ELF RUNPATH/interpreter still use `/data/data/com.termux`;
  - **FAIL** if expected PREFIX is missing;
  - source-built dpkg/apt: `elf_rodata_old_prefix_files=0`.

Verified on the green run:

- login shebang → `#!/data/data/com.newtermux.dev/files/usr/bin/sh`
- login body has no stock Termux paths
- `bash` RUNPATH → `/data/data/com.newtermux.dev/files/usr/lib`
- verifier `RESULT=PASS`

### Generic Build

- `debug_build.yml` is a **successful skip** for both `apt-android-7` and `apt-android-5`.
- Check names stay `build (apt-android-*)`.
- It must not publish another stock-bootstrap coexist APK.

### Runtime hardening (does not replace the zip)

- `TermuxShellUtils`: if a shebang starts with `/data/data/com.termux/` and this variant is not Play, prefix the current-package interpreter (avoids kernel `EACCES` on leftover installs).
- `TermuxShellEnvironment`: set `LD_LIBRARY_PATH=$PREFIX/lib` when `applicationId != com.termux` (`DT_RUNPATH` is searched after `LD_LIBRARY_PATH`).
- `TermuxInstaller`:
  - extract chmod `0755` on `bin/`, `libexec/`, apt helpers;
  - after extract **and** on an already-populated PREFIX, rewrite leftover stock paths in scripts under `bin/`, `etc/`, `etc/profile.d`;
  - chmod extracted executables.

Do **not** treat these as a substitute for the rewritten zip. They exist so a previously extracted stock PREFIX on HONOR 200 can be repaired without claiming a wrapper-only runtime.

---

## 4. CI / artifact coordinates (green Prefix-aware)

**Do not claim physical PASS.** These coordinates are cloud evidence only.

| Item | Value |
| --- | --- |
| PR | https://github.com/joselofarias-byte/NewTermux/pull/16 (OPEN, do not merge) |
| Branch | `fix/prefix-aware-bootstrap-runtime` |
| APK-producing commit SHA | `5533ca59559ddc3211596b30ba5103dc30a025b3` |
| Prefix-aware workflow | [run 35487813388](https://github.com/joselofarias-byte/NewTermux/actions/runs/35487813388) — **success** |
| Package ID | `com.newtermux.dev` |
| Variant | `coexistDebug` / `arm64-v8a` / `apt-android-7` |
| APK file | `termux-app_apt-android-7-debug_arm64-v8a.apk` |
| APK SHA-256 | `6e827422aad7caabd869050e4b464f3b91eca37584eb40346e5a845fcfac468e` |
| ARM64 APK artifact name | `newtermux-prefix-aware-coexist-arm64` |
| ARM64 APK artifact ID | `10598907796` |
| Bootstrap zip SHA-256 | `2b8c8255f2ccc2d8edf64a9feb45743b47e499889ca153ab3c138427febd4bcb` |
| Bootstrap artifact name | `newtermux-prefix-aware-bootstrap-aarch64` |
| Bootstrap artifact ID | `10598708244` |
| Verified login shebang | `#!/data/data/com.newtermux.dev/files/usr/bin/sh` |
| Verified login body | no `/data/data/com.termux` paths |
| Verified bash RUNPATH | `/data/data/com.newtermux.dev/files/usr/lib` |
| ELF `.rodata` stock PREFIX files | `0` (source-built apt/dpkg) |

Download: Actions run 35487813388 → artifact `newtermux-prefix-aware-coexist-arm64` (id `10598907796`).

Generic **Build** is a green skip: it does **not** publish a coexist APK.

Earlier failed Prefix-aware runs (fixed, do not use):

| Run | Why |
| --- | --- |
| `35469071995` @ `97064b76` | Docker `mv` Permission denied; locate found no zip; `TERMUX_APP_PACKAGE` env overwritten by `properties.sh` |
| `35475342734` @ `b5e580b` | rewrite PASS, then verifier `sed \| head` SIGPIPE under `pipefail` |
| `35475694300` @ `2aeaf5d` | verify PASS; Gradle 9 removed `Project.exec()` so `:app:downloadBootstraps` failed |
| `35476078813` @ `1f076d1` | previous **host zip + rewrite** green APK (`cc8646da…`); superseded by source-built `5533ca5` |
| `35477142849` @ `7e9795d` | `termux-am` Gradle: read-only Android SDK, missing platform 33 |
| `35479458793` @ `ff94942` | `PACKAGES+=(bzip2)` — name is a `libbz2` subpackage |
| `35481713155` @ `97d74bc` | preflight regex treated `$add_pkg` as a package name |
| `35481800800` @ `820c90b` | all packages built; AppArmor `mv` of zip onto bind-mount; trap deleted `/tmp` zip |
| `35484720379` @ `03b8570` | zip + shebang/RUNPATH/.rodata PASS; leftover stock **text** in termux-tools |

---

## 5. Physical PASS criterion (not claimed)

PASS only if HONOR 200 retest of **this** APK shows a usable shell:

1. Uninstall or clear data for `com.newtermux.dev` (old extracted stock PREFIX must not be trusted; installer now also rewrites leftover `login`, but a clean extract is the intended path).
2. Install the Prefix-aware `coexistDebug` `arm64-v8a` APK (`com.newtermux.dev`). Leave Play Termux (`com.termux`) installed and untouched.
3. Open NewTermux Dev.
4. Must **not** show `exec(".../usr/bin/login"): Permission denied`.
5. Prompt must be a Termux shell, not only `/system/bin/sh`.
6. `echo $PREFIX` → `/data/data/com.newtermux.dev/files/usr`
7. `head -n 1 $PREFIX/bin/login` → `#!/data/data/com.newtermux.dev/files/usr/bin/sh`
8. `bash --version`, `sh -c 'echo ok'`, `command -v pkg` work.
9. `ldd $PREFIX/bin/bash` / running bash does not resolve libs under `/data/data/com.termux/`.

**Physical PASS is NOT claimed until that device retest.**

---

## 6. Constraints still in force

- Do not merge PR #16.
- No release, no secrets, no Play/TBM touch.
- Do not reactivate `scripts/patch-bootstrap.sh`, `com.newtermux.app`, or `/releases/latest` bootstrap.
- The green APK is from **source-built** `build-bootstraps.sh` plus text rewrite. Do not replace it with a stock official zip.

---

## 7. Files touched this iteration

- `.github/workflows/prefix_aware_coexist_arm64.yml` (source-build + writable SDK + libbz2 + docker-cp zip + text rewrite)
- `.github/workflows/debug_build.yml` (skip stock coexist APK)
- `scripts/fase8/rewrite-bootstrap-prefix.sh`
- `scripts/fase8/verify-bootstrap-prefix.sh`
- `app/src/main/java/com/termux/app/TermuxInstaller.java`
- `termux-shared/src/main/java/com/termux/shared/termux/shell/TermuxShellUtils.java`
- `termux-shared/src/main/java/com/termux/shared/termux/shell/command/environment/TermuxShellEnvironment.java`
- `app/build.gradle` (Gradle 9 `ProcessBuilder` verifier)
- `docs/NEWTERMUX_CHATGPT_HANDOFF_LOGIN_PERMISSION_DENIED_2026-09-19.md`
