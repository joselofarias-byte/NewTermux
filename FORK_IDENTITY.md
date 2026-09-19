# Fork identity

- Maintainer / distribution brand: **JoseloFarias**
- Application: **NewTermux**
- Android application ID: `com.termux` (compatibility exception)
- GitHub account suffix `-byte` is not part of the product brand.
- Fork-specific UI, release metadata and documentation must identify **JoseloFarias** consistently.

## Package identity

`com.termux` is intentionally retained **for release** because the app package, bootstrap expectations, shared-user behavior and plugin ecosystem are tightly coupled to that identifier. Rebranding release would require a separate compatibility migration and is not part of a safe branding-only change.

**Debug coexist identity (Fase 3):** `assembleDebug` uses `com.newtermux.dev` so the APK can sit beside Termux Play without sharing `sharedUserId`, authorities or `/data/data/com.termux/`. That id is 16 characters (same length as the abandoned `com.newtermux.app` experiment) so a later PREFIX-aware bootstrap rebuild can stay length-aligned. `com.joselofarias.newtermux.debug` was rejected: it is too long for official bootstrap ELF path strings. See `docs/NEWTERMUX_FASE3_COEXIST_2026-09-19.md`.

## Credits

Original/base authors: **The412Banner** and the **Termux maintainers and contributors**.

Original copyright notices and applicable licenses remain intact. Attribution mentions original authors by name or alias; fork branding does not add links to upstream repositories.

## Visual identity

- Canonical brand: **JoseloFarias**.
- Canonical primary color: **`#6750A4`** (Material 3 violet).
- Fork-owned iconography, splash, About surfaces, README and release assets must use one coherent JoseloFarias visual identity.
- Upstream trademarks and logos are not presented as fork-owned assets.

## Support identity

Support is optional and belongs only to the **JoseloFarias** edition.

- EVM address: `0x5d580ac4f1eabff84379fa8e217df4684ad30934`
- Mode: **multichain EVM**.
- Preferred assets, in order: **USDT**, **USDC**, **FDUSD**.
- Other tokens are acceptable only when the selected token and EVM network are compatible with the receiving wallet.
- UI warning: verify both token and network compatibility before sending.
- Binance Pay name: **CriptoUy**.
- Binance Pay URL: `https://app.binance.com/uni-qr/Dhzk73AN`
- **PayPal is not used and must not be displayed, linked or kept as a placeholder.**

Any About/Support UI, README support section or release metadata added by this fork must reuse these canonical values instead of duplicating divergent addresses, colors or payment identifiers.
