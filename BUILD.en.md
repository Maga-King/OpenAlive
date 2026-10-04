# Building

GitHub Actions builds and verifies the APK when a `v*` tag is pushed, then publishes a release. A manual run only produces the build artifact by default; check `release` to publish as well.

## Environment

- Ubuntu 24.04, JDK 17, Python 3.12.
- Rust with the `aarch64-linux-android` target.
- Android SDK Platform 35, Build Tools 35.0.0, NDK 29.0.14206865.
- Xposed API 82 (compile only; the script downloads and verifies it).

Set `ANDROID_HOME`, `ANDROID_NDK_HOME` and `JAVA_HOME`, then run:

```sh
rustup target add aarch64-linux-android
python3 tools/build_release.py
```

The output goes to `dist/`. The script compiles the host app and the render library from the Java and Rust sources and recompiles the UI resources. The prebuilt DEX of Meizu's official UI lives in `vendor/flyme/` and is not compiled from source by this project.

Without signing configuration a local development key is created. Release builds use `OPENALIVE_KEYSTORE`, `OPENALIVE_STORE_PASSWORD` and `OPENALIVE_KEY_ALIAS` (default `development`); the key never enters the repository. The GitHub workflow injects the same key from repository secrets so updates install over previous versions.

Resource tables are stored uncompressed and aligned to 4096 bytes, and this is checked again after signing. Video wallpapers are not included in the package yet.

The Xiaomi Super wallpaper list and preview use the original widgets in `app/assets/xiaomi/ui.apk`; the build verifies every retained file. To regenerate them, run `python tools/import_xiaomi_ui.py ThemeManager.apk <scene APK folder>`, then `python tools/import_xiaomi_packs.py <scene APK folder>`. Input versions are pinned and the original packages are verified; the folder must contain `moon.apk`, `snowmountain.apk`, `geometry.apk`, `saturn.apk`, `earth.apk` and `mars.apk`.

The six scenes are built in as shared XZ blocks and restored to the original packages on first use. The build decompresses block by block and verifies the SHA-256 of all six complete APKs, keeping the original signatures, ZIP layout and all assets; a fresh checkout builds without a local decompilation folder. The pinned original XZ for Java 1.12 sources are in `vendor/xz/`; their Java 8 part is compiled, with no extra native libraries. The `.xz` blocks stay STORED in the final APK to avoid recompression that would add size and first-use cost.

## Translations

The UI is available in Chinese, English and Russian. Keep writing Chinese in the code as usual; Chinese is the source text.

After adding or changing UI text:

1. Run `python tools/i18n.py update`. It wraps new Chinese text in `I18n.t("…")`, adds empty entries to `app/i18n/strings.json` and regenerates `I18nTable.java` (generated from `strings.json`, do not edit it; the build refreshes it as well).
2. Optional: fill in `"en"` and `"ru"` in `strings.json`. Until then the Chinese text is shown (a missing Russian text falls back to English first), so nothing breaks.

`python tools/i18n.py` only checks: it lists unwrapped, untranslated and unused text, and the build runs it too. Only real errors fail the build: invalid `strings.json`, or a translation whose `%s`/`%d` placeholders differ from the Chinese text.

Chinese that must not be translated (the lunar calendar, Chinese date formats) is listed in `tools/i18n.py`; other lines can carry a `// i18n:ignore` comment. Text that is translated where it is shown, such as static arrays, is marked with `I18n.mark("…")`.

The original UI bundles (`settings-ui.apk`, `editor-ui.apk`, `xiaomi/ui.apk`) stay unchanged: their extra Russian strings are in `app/locale-overlays/<package>/`. The build reads the resource IDs by name from each bundle, so an updated bundle needs no manual step and strings it no longer has are skipped. `LocaleOverlay` layers them at runtime (Android 11 and later). The app's own resources are in `app/res/values-en` and `values-ru`.
