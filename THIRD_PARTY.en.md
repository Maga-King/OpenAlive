# Asset notes

**Some assets come from FlymeOS on the Meizu 22.** The reference version used by this project is Flyme 12.6.0.0A on the Meizu 22, including AliveWallPaper 13.0.5 and SystemUIEditor.

The following keep their owners' copyright and original licenses and are not covered by this project's MIT license:

- `vendor/flyme/`: the prebuilt DEX of the official UI and its resources.
- `app/assets/ui/`, `app/assets/clock/`, `app/res/font/`: official UI resources and fonts. `settings-ui.apk` keeps WallpaperSetting's original layouts, widgets and resources; source checksums are in `settings-ui-source.json`.
- `app/assets/shader/`, `native/shaders.rs`: official shaders and generated embedded files; third-party copyright notices inside the files are retained.
- `app/assets/masks/`, `app/assets/editor/`, `app/assets/wallpapers/`: effect masks, previews and wallpapers.
- `app/assets/examples/`: sample images, copyright belongs to their authors.
- `app/assets/cosmic/`: official Cosmic and Phoenix color schemes, previews and model data; source checksums are in `source.json` and `phoenix-source.json` in the same folder.
- `app/assets/bubble/`, `app/assets/soundviz/`: numeric settings and flowing background shaders of the official Bubble series; source checksums are in `bubble/source.json`.
- `docs/donate.png`: the maintainer's donation code, not part of the code license.
- `app/assets/notification/`: the original notification halo and side light bar animations from the Meizu 22 SystemUI; source and checksums are in `source.json` in the same folder. They are decoded and drawn with the included official SVGA runtime.
- `app/assets/native-clock/`: digit fonts from the Meizu 22 SystemUI and HyperOS MIUIAod. `original/` keeps the unmodified files; the working copies adjust line height and padding to ColorOS's fixed font conventions without changing glyphs or advance widths. Source, adaptation notes and checksums are in `source.json`.
- `app/assets/native-clock/templates/flyme/`: the ALIVE horizontal and vertical AOD clock templates and fonts from the Meizu 22 SystemUIEditor, original XML and fonts retained; source and checksums are in each template's `source.json`.
- `app/assets/native-clock/runtime/hyperos/fonts/`: original clock fonts from the system partition of the HyperOS test phone, used by the standalone HyperOS widgets with glyphs and metrics unchanged; source and checksums are in `source.json` one level up.
- `app/assets/native-clock/runtime/hyperos/runtime.apk`: the original MIUIAod program and resources from the test phone, whose clock widgets are called through an isolated class loader; version, source and checksums are in `source.json` in the same folder, the file is unmodified.
- `app/assets/native-clock/previews/`: picker thumbnails drawn with the original HyperOS widgets above and the original perspective clock widget of Meizu SystemUIEditor; template parameters, original runtimes and image checksums are in `source.json`.
- `app/assets/native-clock/vivo/`: original clock layouts, resource table, fonts and glass/blur shaders from OriginOS SystemUIPlugin. `layouts.apk` keeps the original DEX and its layout widgets are loaded in isolation without starting Vivo services; source and checksums are in `source.json`. `previews/` are drawn with the adapted original layouts. These resources remain with their owners and are not covered by this project's MIT license.

Naming the source of an asset does not mean Meizu or any other owner has granted a redistribution or commercial license. Using these assets still requires following their applicable licenses; MIT only covers the self-written code this project is entitled to license.

Xposed API 82 is used only for compiling and is not packaged in the APK; LSPosed provides it at runtime.

- `app/assets/vivo/runtime/`: the original Vivo LiveWallpaperBox 7.0.1.02 program and the downloaded holo card and lenticular textures. The original rendering code is reused through an isolated class loader; the APK and texture contents are unmodified, only offline texture paths are remapped. Source and SHA-256 are in `source.json` in the same folder. These remain with their owners and are not covered by this project's MIT license.
- `app/res/drawable/vivo_*`: original Vivo BBKTheme editor icons; source and checksums are in `app/assets/vivo/ui-source.json`. The original PAG animations of the lenticular options and the accompanying `libpag` and `libffavc` are stored in the Vivo runtime above and keep their own copyrights and licenses.
- `app/assets/xiaomi/ui.apk`: the original list, preview, progress bar and landing point widgets from Xiaomi ThemeManager. DEX, resources and asset contents are unchanged; only the unused video editing native libraries and the original APK signature metadata were removed and the package recompressed. Checksums of the original package and of each file are in `vendor/xiaomi/ui-manifest.json`. The six banners come from the respective original scene APKs. `app/assets/xiaomi/packs/` stores lossless shared blocks of the six original packages; restored according to `packs.json`, they match the original APK SHA-256 recorded in `catalog.json` exactly. Xiaomi programs, assets and their dependencies keep their original rights and are not covered by this project's MIT license.
- `vendor/xz/xz-1.12-sources.jar`: the original XZ for Java 1.12 Java sources; source and SHA-256 are in `source.json` in the same folder, licensed 0BSD (see `COPYING` and `LICENSE`). The build compiles only the Java 8 sources for streaming decompression of the built-in scenes; original copyright and contributor comments are retained.
