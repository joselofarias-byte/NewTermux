# Playcompat CI status — 2026-09-25

Closest CI unlock for replacing Termux Play without touching its data: the
verified `playcompatDebug` gate now sits on the prefix-aware stack, and a
source check locks the invariant that this variant still embeds the stock
`com.termux` bootstrap.

`main` does not have the `playcompat` / `coexist` flavors. PR #18 proved the
workflow on `cursor/fase7-validacion-fisica-2026-09-19-0b82`, which is behind
`fix/prefix-aware-bootstrap-runtime`. This note does not move either branch
onto `main` and does not authorize a device install.

## CI already proven (reused, not rebuilt here)

| Item | Value |
| --- | --- |
| Workflow | Build playcompat migration APK |
| Run | [35521967350](https://github.com/joselofarias-byte/NewTermux/actions/runs/35521967350) |
| Commit | `7a1741cc1c3214d5c8bda68c9ff7bfe683845879` |
| Branch | `codex/playcompat-migration-build-2026-09-20` |
| Variant | `playcompatDebug` / `com.termux` / apt-android-7 |
| APK SHA-256 | `c949a76f84367d2c416c0480385afcc414f9b81015f01411134451c6aa152aca` |

That run built every ABI. It is **not** the SHA of a prefix-aware-tree APK.
Prefix-aware Java changes can change APK bytes. They do not change bootstrap
selection for `assemblePlaycompatDebug`: the stock-bootstrap refusal matches
only task names that contain `Coexist`.

## What this tree adds

- `.github/workflows/playcompat_migration_build.yml` on top of
  `fix/prefix-aware-bootstrap-runtime`.
- ARM64-only (`TERMUX_ABIS=arm64-v8a`), debug, no Release, no coexist bootstrap
  directory. Artifact retention 14 days.
- `scripts/fase8/assert-playcompat-stock-bootstrap.sh` checks, without Gradle:
  - `playcompat` application id stays `com.termux`.
  - `assemblePlaycompatDebug` does not trip the coexist stock-bootstrap refusal.
  - `repairStockPrefixReferencesIfNeeded()` returns immediately when the
    package is `com.termux`, so a playcompat process does not rewrite Play's
    PREFIX. The login-shebang and `LD_LIBRARY_PATH` overrides are the same:
    they run only when the package is not `com.termux`.

## CI versus HONOR 200

| Check | Where it is decided |
| --- | --- |
| Flavor id, variant name, stock login shebang, `libandroid-support.so` | CI (run 35521967350, and the workflow on this branch) |
| Coexist refusal does not apply to playcompat | CI preflight on this branch |
| Runtime prefix rewrite is a no-op for `com.termux` | CI preflight (source guard) |
| APK signature matches the installed Termux Play app | **BLOCKED_PHYSICAL** — HONOR 200 |
| Backup of Play `files/usr` and home before any install | **BLOCKED_PHYSICAL** — HONOR 200 |
| Install or upgrade of `com.termux` over Play | **BLOCKED_PHYSICAL** — do not do this from CI |
| Smoke of login, PTY, packages, and preserved home | **BLOCKED_PHYSICAL** — HONOR 200 |
| TBM restore and NewTermux cutover | **BLOCKED_PHYSICAL** — not authorized |

Coexist (`com.newtermux.dev`) remains the side-by-side debug identity. It is
not a Play replacement. Playcompat is the replacement-shaped APK and stays
uninstalled until the backup and signature check exist.
