# Xiaomi super-wallpaper battery display — 2026-10-21

The shared XiaomiAodPreview renderer now enables the original battery row for
all six catalog families: moon, snowmountain, geometry, saturn, earth and mars.
This covers the actual AOD clock and the home-screen AOD preview.

The original battery container, BatteryMeterIconView, percentage TextView,
drawables, resource dimensions and spacing are retained. Only the original
AODBatteryMeterView wrapper is replaced with a plain Android LinearLayout: its
onAttachedToWindow would otherwise initialize Xiaomi BatteryController and a
vendor drawing wake lock. The replacement consumes Android's protected
ACTION_BATTERY_CHANGED sticky broadcast and subsequent updates.

The observer is registered only while its host is active and released on
deactivation/detachment. There is no polling timer, additional wake lock or
frame-by-frame log. Unknown battery data hides the optional battery row rather
than displaying a fabricated zero. Charging/full and discharging use the
original icon update method; MIUI-specific fast-charging tiers are not inferred
from generic Android data.

The original icon's private android.util.PathParser reflection is blocked in
ordinary application processes on this device. Missing masks are rebuilt from
the same original path strings using the bundled AndroidX parser. Charging masks
use battery_meter_path_to_dp; the rounded progress mask separately uses
battery_meter_progress_mask_scale_path_to_dp, exactly as updateResources does
in the original BatteryMeterIconView. Valid original masks are retained.
If optional battery setup fails, the battery subtree is removed before attachment
and the original time/date clock remains available.

Validation on 3B1F4UE5MS13HQRR:

- All six metadata entries rendered with live 20% battery data.
- Each family exercised 14 combinations: 0/1/19/20/50/99/100%, with and without
  charging; original artwork produced 12 distinct frames.
- An initial candidate incorrectly reused the charging-mask scale for the
  progress mask, cutting the right upper outline at intermediate levels. It was
  caught before installation and corrected to the original separate scale.
- All 14 battery frames now match the original system-parser reference byte for
  byte across RGBA channels. The reference runs with hidden-API checks disabled
  only for the disposable instrumentation process; normal application mode uses
  the public parser. No global hidden-API policy is changed.
- Invalid battery level hides the row; repeated activation registers only once,
  repeated deactivation unregisters only once.
- Original vendor BatteryController remained uninitialized.
- Time font remains the original MiClock-Light, 128 px on the current 1080-wide
  display; date remains 48 px, battery percentage uses its original 36 px size.
- No device battery values were spoofed; test states were local Intent objects
  delivered only to the disposable test renderer.

Command: python tools/check_xiaomi_aod_device.py --serial 3B1F4UE5MS13HQRR --foreground --compare-native-paths
The temporary foreground test page avoids ColorOS freezing the background-only
instrumentation process. Earlier offscreen multi-family attempts were interrupted
by kernel do_freezer_trap; they are not counted as complete passes. The successful
test package was removed automatically. Evidence is under build/xiaomi-aod-probe.

## Installed build and acceptance

0.4.41-preview / versionCode 57 was installed on the authorized primary device
at 2026-10-21 20:19:01. Local and installed APK SHA-256 both equal
99361d0275ef98f00206c9f53202efadf5686cc82011497a58c2d37a0e47bb71.
SystemUI was restarted from PID 26967 to 9194; its maps reference the new APK.
The user subsequently accepted the appearance. The later bounded crash-buffer
review contained no new OpenAlive or SystemUI crash after this installation.

The user subsequently authorized publishing after the power review and exact
cloud/local artifact comparison. See docs/power-review-20261021.md; raw device
logs remain local under the ignored build directory.
