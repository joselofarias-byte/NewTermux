package com.termux.app.activities;

/**
 * Compatibility entry point kept while the Package Manager screen is migrated
 * to Jetpack Compose. AndroidManifest.xml and existing Java call sites continue
 * to reference this class, while the implementation lives in
 * ComposePackageManagerActivity.
 */
public class PackageManagerActivity extends ComposePackageManagerActivity {
}
