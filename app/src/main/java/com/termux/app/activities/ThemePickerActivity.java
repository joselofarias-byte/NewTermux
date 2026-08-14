package com.termux.app.activities;

/**
 * Compatibility entry point kept while the screen is migrated to Jetpack Compose.
 * AndroidManifest.xml and existing Java call sites continue to reference this class,
 * while the implementation lives in ComposeThemePickerActivity.
 */
public class ThemePickerActivity extends ComposeThemePickerActivity {
}
