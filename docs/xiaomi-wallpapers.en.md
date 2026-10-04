# Xiaomi Alive wallpapers

The home page entry sits below "Vivo Alive wallpapers". The list, preview, progress bar and landing point widgets come from the original DEX and resources of Xiaomi's theme app; the host provides local data, system state and apply actions. The original theme app's initialization, account and download services are not called.

## Scenes and offline use

| Scene | Original renderer | Landing points |
| --- | --- | --- |
| Moon | Filament / MoonMrePlayer | 5 |
| Snow Mountain | Snow JNI | 1 |
| Geometry | Unity | 1 |
| Saturn Rings | Unity | 1 |
| Earth | Filament / EarthMrePlayer | 5 |
| Red Mars | Unity | 5 |

All six are built in; tapping a scene prepares it in the background, with no download or manual import. Previously cached valid original packages or installed matching versions are still reused. Versions and SHA-256 are pinned in `app/assets/xiaomi/catalog.json`; a corrupted cache is restored from the built-in data, and a temporary file only replaces the target after full verification.

The six original scene packages total 434,688,109 bytes (414.6 MiB). They are split along large ZIP entry boundaries, identical content is stored once and compressed losslessly with XZ: 521 shared blocks, 330,241,580 bytes (314.9 MiB). On first use the chosen package is restored on demand with a full SHA-256 identical to the original; no textures, landing points, code, ABIs or time-of-day branches were removed. The original UI package is about 27.6 MiB and reuses the existing HyperOS clock assets. Each scene runs in its own process with a native library folder keyed by the package digest, so same-named Unity libraries never overwrite each other.

Restoring runs in the background with a cross-process file lock and read-only temporary files; each block's length and digest are verified, and the original APK digest is verified before an atomic commit. The XZ decoder is built from pinned Java sources with decoding memory limited to 32 MiB. Once prepared, the file on disk is reused directly; nothing is decompressed while rendering, and unused scenes are not expanded in advance.

The full 0.4.23-preview package is about 570.4 MiB. On the main ColorOS device, 46 checks passed in a separate empty folder, covering byte-exact restore of all six, cache reuse, error rollback, corruption repair and concurrent preparation; the existing 198 clock and resource checks passed as well. First preparation took about 2.4 s for Moon, 1.8 s for Snow Mountain, 3.4 s for Geometry, 4.3 s for Saturn Rings, 3.1 s for Earth and 18.8 s for Mars. These are single measurements on one device, not a performance guarantee. Preparation longer than 300 ms shows a wait message; Back only cancels automatically opening the preview, and preparation that has started finishes in the background.

The same day, entering the Moon preview from the real list on the main phone was confirmed by screenshot. For Mars, the existing cache was temporarily moved away: tapping it in the list showed the preparation message, restored it from the built-in data and opened the original live preview; the new file's digest matched the original and the temporary backup was removed after verification. The user's current wallpaper was not applied or changed. The final package matched the local SHA-256, and test-only packages and temporary extraction folders were cleaned up.

## State and applying

- The original AOD, lock screen and home animations are driven by the existing ColorOS SystemUI state channel; on other systems wallpaper commands, screen state and lock state are the fallback.
- The original renderer pauses while the wallpaper is invisible or the display is off, and resumes when visible again.
- The system page position `0..1` is converted to the original `Offset_0..100`, and each change is passed to the renderer 100 ms later like the original service, without dropping intermediate samples. The total amplitude is fixed and divided by the actual number of home pages; the per-page amplitude is not enlarged.
- The surface is bound and rendering resumed only after the Unity render thread's message queue is ready, then the original `InitFinish` callback is awaited. This avoids losing the resume message on a cold start, which caused a black screen.
- Applying saves the landing point. The Root path only allows this app's six wallpaper components and verifies the lock and home results; without Root the system live wallpaper confirmation page is used. Cancelling the system confirmation restores the previous landing point.
- The AOD preview first uses Xiaomi's original snapshot interface; if it is missing, the existing original MIUI AOD renderer draws the preview, with no imitation clock.
- The real AOD reuses the original SuperWallpaperClock and Linger Light's clock hiding, notification avoidance and restore channels. The real lock screen keeps the ColorOS clock. Xiaomi does not use Linger Light's photo unfold frame confirmation.
- The lock screen candidate command on waking from AOD uses Xiaomi's 100 ms wait window; an authenticated unlock within the window cancels the candidate, so the camera does not move towards the lock screen before switching to home.
- The three small previews on the home page use the package's `aod_small_preview`, `lockscreen_small_preview`, `home_small_preview_N` and their dark versions, with the home image following the saved landing point. They are original static thumbnails and do not start three extra native renderers; tapping opens the live preview of that stage. The small AOD clock updates with the current time and stops when leaving the home page.

## Variation within a scene

- The original Moon keeps 15 lock screen camera/lighting sets, random satellites and orbit animation. The real wallpaper calls the original pure function to compute the moon phase and sends `Phrase_`; the preview follows the original package and does not inject the phase commands meant only for the real service.
- The original Earth computes the sun and day/night from local time; day and night assets of all five landing points are kept.
- The original Snow Mountain's `CameraCtrl.lua` uses `os.time` / `os.date` to split the local day into 9 periods mapped to textures, lighting and clouds; the original automatic time update and transitions are kept, and the system dark mode does not replace the periods.
- Mars, Saturn Rings and Geometry load the complete original Unity data and native libraries with no scene branches removed. Their internal camera combinations have not been enumerated one by one, so the total number of variants cannot be inferred from the landing point count alone.

## Test scope, 2026-09-28

Device: Redmi Note 12 Turbo, Android 17 / HyperOS 4, `arm64-v8a`.

- All six native renderers and the original preview pages display; the Earth landing point page and selection were checked.
- Startup of the six real wallpaper services, pausing when covered by other windows and resuming when visible were checked; after the Unity race fix, Mars, Saturn Rings, Geometry and Snow Mountain were retested.
- 60 device checks passed: original package verification, offline import, rollback of a bad import, saving and loading all landing points, font loading inside an external SystemUI host, and the original UI / offline AOD preview with MIUI framework classes blocked.
- A test window called the real `WallpaperManager.setWallpaperOffsets`, confirming the native wallpaper service receives `0 / 50 / 100`.
- The release build runs source checks, 35 Rust tests, digest checks of 5669 original UI files, and APK signature and resource alignment checks.

Some service checks used a transparent test window shown above the secure lock screen so the real system wallpaper was visible; this window does not unlock the device. It verifies the rendering lifecycle and system offset delivery, but does not replace checking real home paging or the system AOD.

After this first round, the home screen and system apply paths were checked on the main ColorOS device, see below. The system attachment of the standalone AOD clock and the fingerprint unlock hand-off still need acceptance after the main phone loads the new SystemUI module. A preview showing the AOD scene does not mean the system AOD has been verified.

### Follow-up device checks

- On the main ColorOS phone, the real home screen's Snow Mountain service received `50 / 60 / 70 / 80`; step `0.1`, i.e. 11 pages. On the test phone, real paging with the original Snow Mountain received `0 / 50 / 100`, i.e. 3 pages.
- The `.gnu_debugdata` of the HyperOS launcher's `libapp.so` keeps Dart function symbols. ARM64 inspection confirmed `WorkspaceGetxController._updateWallpaperOffset` computes `page / (screenCount - 1)`, which the original service converts to `Offset_0..100`. The per-page amplitude difference between the two phones comes from the page count. The user confirmed keeping the original algorithm.
- 152 device checks of the signed APK passed, including mounting/drawing/restoring the six original clocks, day/night thumbnail resources for every scene and landing point, the three entry stages, and Linger Light clock independence. This does not replace acceptance of the real ColorOS SystemUI AOD and fingerprint authentication.
- After temporarily applying OpenAlive's Snow Mountain on the test phone, the three small home previews and entering the AOD/lock/home stages were checked by screenshot. While the app UI covers the wallpaper the service reports `running=false`, and `running=true` after returning home. After the test the test phone's original Xiaomi Snow Mountain service was restored.

### Switching and AOD size fixes

- The Meizu editor decides whether the system apply flow is needed by the specific `CleanWallpaper` component, so a Xiaomi service from the same package is not mistaken for already applied. On the main phone, cancelling kept the original settings and Snow Mountain service; the system then switched to `CleanWallpaper`.
- The Super wallpaper AOD clock gained the original MIUIX density calculation, applied only to its own resource context while keeping the original window dp qualifiers. The main phone computes 586 dpi while the host stays at 640 dpi; 157 checks passed, including unchanged density and size of other HyperOS clocks and mounting and restoring the six clocks. The final size in SystemUI needs acceptance after a reboot.
- The Filament versions of Earth and Moon do not handle the generic `ForceAOD`, so restoring state sends their native `ForceStaticAOD_1` instead. Models, projection and scene scale were not changed. On the main phone the Moon body on the real AOD is about 60% of the screen width, matching the original reference; the real Snow Mountain AOD was checked too.
- The five Meizu Bubble wallpapers keep the original RGB blending and animation; only the final output alpha is kept opaque so window composition does not show through. Before the fix all five produced translucent pixels; after it, 90 RGBA frames read back from the device GPU (light/dark, three stages) all have alpha 255. The user confirmed the whitening when returning home is gone.

### 2026-09-29 AOD clock fade in and out

- Following `DozeHost.startEnterAnim` in the original `MIUIAod.apk`: the normal Super wallpaper clock container fades in over 1000 ms with `AccelerateInterpolator`, with a default style delay of 0. The original lock screen has a separate 500 ms clock animation; the sleep call site missing in JADX was checked with baksmali, but this implementation does not take over the ColorOS lock screen clock animation.
- Fading applies only to the standalone AOD clock. The AOD fade-in is multiplied by the native display window's opacity; on a fully black screen the enter animation is cancelled, and tapping to show the AOD again fades in again, without extra display requests, wake locks or timed wakeups.
- When waking to the lock screen the standalone AOD clock fades out over 500 ms, and the lock screen clock is handed back to ColorOS immediately, as before the new animation. **The 500 ms AOD exit is an adaptation choice** and should not be taken as a confirmed original AOD exit parameter. Xiaomi's timeout to black differs from ColorOS's display window control, so ColorOS still ends the display.
- Repeated time updates do not restart the animation; when the screen toggles quickly, the AOD reverses from its current opacity. The existing AOD clock hiding and the logic that keeps the mask until keyguardGone on direct unlock are kept. Linger Light's commit frame confirmation and 167 ms restore animation are unchanged.
- The original preview page keeps running its own scene animation; its 200 / 250 / 300 ms parameters were not replaced. The real lock screen still uses the ColorOS clock chosen by the user.
- `tests/android/XiaomiFidelityTest.java` passed 198 checks on the main phone, covering the existing six clocks and resources plus AOD opacity, interruption, repeated updates, waking after a black screen, authenticated unlock, resource cleanup and Linger Light independence. New checks confirm that entering AOD creates no lock screen fade-out, returning to the lock screen releases the original clock immediately, and the original clock's opacity stays unchanged during the AOD fade-out. The new APK was installed and its hash verified; the standalone window test does not replace acceptance on the real SystemUI lock screen, which needs a reboot to load the new module.

### Motion and direction sensor permission

- On the main phone, this package's `DIRECTION_SENSORS` op was set to `allow` via Root and read back. Before granting, `default` and recent rejections were read; this only shows the system had restricted the op and does not prove it is the whole cause of occasional lenticular failures or extra power use.
- The new Root platform adapter looks up the op number by the ROM's `android:direction_sensors` name, grants it to `org.aliveclean` for the primary user and checks again. It runs along with a successful AOD apply and is also available as a separate `motion-permission` command. Other apps' permissions are not changed and system settings are not rewritten in a loop. Systems without this op return unsupported, which does not affect applying the wallpaper.
- The new APK's `motion-permission` adapter ran twice in a row on the main phone, returning `motionAllowed=true` both times, and the system then read back `DIRECTION_SENSORS: allow`, confirming repeated calls also work.
- The saved power and process logs did not identify the cause of the few-second freeze when the screen turns on. At the user's request it was not repeatedly reproduced, and the original renderer's pause logic was not changed on that basis.
