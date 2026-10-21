# Power review before 0.4.41 release

Read-only batterystats, history, current power state and crash-buffer review was
performed on the authorized primary device on 2026-10-21. Battery statistics were
not reset, simulated or altered. Raw device logs are kept only in the ignored
local build/power-release-20261021 directory.

The history starts at 18:05:05; capture was around 21:28. It contains charging,
normal interactive use, two earlier reboots and development tests across several
builds. The installed 0.4.41 APK matches the reviewed local SHA-256.

- On-battery time: 37m 51.779s; screen-off time: 12m 59.096s; screen-doze time:
  3m 47.960s. These are mixed-use samples, not an idle endurance benchmark.
- OpenAlive UID's model-estimated CPU energy is 18.4 mAh across all reported
  buckets, including plugged-in use. Its on-battery screen-on CPU bucket is
  2.14 mAh and screen-off/doze CPU bucket is 0.971 mAh. Do not interpret the
  total as battery drain caused by this version.
- No positive-duration app partial wake lock, wakeup alarm or used sensor was
  listed in the app's legacy on-battery detail. Its modeled sensor energy is
  negligible, but this alone does not measure GPU/display cost. Current global
  held wake-lock count was zero at the snapshot.
- SystemUI's aggregate CPU/locks cannot separate the module's injected clock
  from the rest of the system. Its on-battery screen-off/doze CPU estimate was
  2.99 mAh; these figures are not attributed wholly to OpenAlive.
- No post-install OpenAlive or SystemUI crash appeared in the retained bounded
  crash buffer. An unrelated application's error was excluded from this verdict.

Conclusion: no direct evidence of a new persistent wake-lock/sensor leak was
found. These data support proceeding with the accepted build; they do not prove
zero overhead or a particular long-term standby drain. The new battery row uses
protected battery broadcasts, no timer or additional wake lock; device tests
verified receiver release and that Xiaomi's vendor BatteryController stayed
uninitialized.
