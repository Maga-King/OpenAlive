# Xiaomi super-wallpaper clock density — 2026-10-21

## Scope

Restore the original clock's relative size on the Oplus host, while avoiding
model-specific multipliers and double scaling after resolution changes. Only
the private super-wallpaper AOD Resources use this policy; the host's resources,
wallpaper player, lockscreen transitions and other clock families are unchanged.

## Evidence and decision

The OnePlus test device (3B1F4UE5MS13HQRR) reports 1080×2376, logical density 480,
stable density / ro.sf.lcd_density 640, and persist.sys.display.density 480.
Its supported modes include 1440×3168. The installed 0.4.39 clock resolved to
330 DPI and a 96 px time font, because the old default-density denominator
still used 640. The candidate resolves to 440 DPI and a 128 px time font.

An initial candidate inferred the default density from the largest supported
mode. That approach was rejected: Android's stable density is a system property,
not a density explicitly paired with that mode. Vendors can use per-resolution
density mappings, and a device whose baseline is already normalized would be
scaled twice. There is no generic proof from the mode list alone.

The implemented Oplus default-display lookup follows the local official
KStyleUtil.getInitialDisplayDensity implementation:

1. A valid persist.sys.display.density on an Oplus/ColorOS ROM.
2. Otherwise IWindowManager.getInitialDisplayDensity for that display.
3. If unavailable, leave a successful original MIUI calculation unchanged.

The original MIUI PPI/SKU/device policy remains authoritative; only its default
density denominator is corrected when the host supplies a known baseline.
User-selected logical density remains in the numerator. Neither the display's
xdpi/ydpi nor the global system density is modified. Unknown/default-density
sources do not trigger an inferred multiplier. Secondary displays never use
the default-display property or the global stable DPI in the portable fallback.

Local evidence: build/lyric-clock/stock/sources/com/oplus/keyguard/clock/common/
util/KStyleUtil.java, lines 58–77; original bundled com.miui.aod
DisplayDensityConfig and AutoDensityPolicy bytecode.

## Fonts

Both installed and candidate renderers use the original APK's MiClock-Light.otf
for the time digits, verified by Typeface equality in the device probe (weight
300). The candidate supplies bundled MiSans regular (weight 400) only for date,
lunar date and city labels, which otherwise inherited the host ROM font. It
does not replace the original time font or enable vendor battery/notification
controllers. This is not a claim that all Xiaomi firmware revisions share the
same auxiliary-font configuration.

## Validation

- tools/check_xiaomi_aod_density.py: 52 assertions across 720/1080/1220/1440
  widths, several aspect ratios, rotation, +10% display-size preference,
  already-normalized and unknown baselines, invalid DPI and invalid HAL metrics.
- tools/check_xiaomi_aod_device.py --serial 3B1F4UE5MS13HQRR: offscreen
  instrumentation renders the installed module and candidate source in separate
  class loaders, using the installed original runtime/assets. No Activity is
  opened and no device resolution, density, wallpaper or clock setting is changed.
- Device probe before main-package installation: installed 330 DPI / time 96 px
  / date 36 px; candidate 440 DPI / time 128 px / date 48 px. Both render nonblank
  clock pixels and preserve the original time Typeface. Evidence and comparison
  images are in build/xiaomi-aod-probe.

The formula matrix is not a multi-brand hardware test. The property writer and
its behavior on every ColorOS version have not been verified; the implementation
uses the source selected by the current official clock. The portable phone
fallback cannot guarantee identical physical sizing when HAL xdpi/ydpi are
inaccurate. Native foldable/tablet policies are retained when the original
renderer can resolve them. Live resolution-switch and visual AOD acceptance
remain separate from the offscreen checks.

No GitHub push or release is part of this change.

## Installed build

0.4.40-preview / versionCode 56 was installed on the authorized OnePlus at
2026-10-21 19:44:12. APK signature verification passed; local and installed
SHA-256 both equal
e83cf47e115187db04cc70307a7ce3f1bf531113309c5a922afe6897c8f6ab42.
The build's checks, including the 52 density assertions, completed. The shell
wrapper subsequently failed while printing mixed-encoding compiler output;
the build report, signed APK and installed hash were verified independently.

The device probe was rerun against this installed main APK with normal hidden
API checks (no --no-hidden-api-checks): installed and candidate both rendered
440 DPI, 128 px time and 48 px date, with identical pixel counts/bounds.
before-main-install-result.txt retains the 0.4.39 comparison; result.txt is
the post-install probe. SystemUI was restarted from PID 7461 to 26967 at
19:45:31; its memory map references the new installed module APK. No crash
buffer entries occurred after installation during this bounded check.

The attempted live AOD capture was interrupted by a WakingUp event and shows
the wake/lockscreen composition, not a stable AOD endpoint. Do not count it as
visual AOD acceptance or a resolution-switch test; final appearance needs the
user's confirmation. No additional display settings were changed.
