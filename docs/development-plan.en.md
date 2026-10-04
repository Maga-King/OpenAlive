# Current work

| Order | Item | Status and acceptance |
| --- | --- | --- |
| 1 | Linger Light: avoid the player, notifications and widgets | Local fix done; the test phone passed empty row, one row, two rows, player removal and coordinate restore tests. Still to be rechecked on a real ColorOS device; it cannot be declared fixed from this alone. |
| 2 | Keep native card sizes | Removed the notification layout cache and the clock bottom overlay; the standalone notification container is positioned after the actual card layout. Tests confirm card size, scale and inner coordinates are not modified. |
| 3 | Official live wallpaper catalog | AliveWallPaper 13.0.5 on the Meizu 22 declares 31 services: 13 Cosmic, 5 Phoenix, 5 Bubble, 4 video, 2 music, and one each for photo and text. Some are hidden in the official menu by default. |
| 4 | Cosmic series | Official models, shaders, 13 light and dark color schemes and three-state previews are integrated; GPU checks passed on the test phone. Actual AOD positioning and switching on the main phone still need verification. |
| 5 | Phoenix, Bubble | Next, check each series' camera, animation and resource dependencies and implement them separately, not just by replacing thumbnails. |
| 6 | Video, music, text | Handle playback lifecycle, asset size and data integration separately; do not replace motion with static images. |
| 7 | Release | Keep local fixes and feature checkpoints; until verified on the main phone, do not push to GitHub or copy test packages to the phone's shared storage. |

Wallpaper sources are split into **Alive live wallpapers / Meizu static wallpapers / your photo**. Live wallpapers are chosen by their official names and still use the existing three-state editor; photo framing, texture and pairing settings are kept.

Linger Light replaces only the AOD clock. The new Cosmic wallpapers use the system clock and do not apply Linger Light's hiding rules. The normal lock screen, widget content, the fingerprint area and the confirmed orb positioning logic are outside this change.

Local checks this round: the test phone passed 596 font and layout checks and 379 live wallpaper GPU and state-switching checks; light/dark settings and three states of all 13 wallpapers were rendered. Switching from a live wallpaper back to Linger Light in the editor was also checked. These results do not mean that real ColorOS player avoidance or frame-by-frame parity with the official animation have been verified.

The test package `OpenAlive-0.4.18-cosmic-test1.apk` is installed on the test phone. Resource tables of the outer and embedded UI are uncompressed and aligned to 4096 bytes. The original Rust render library and the official UI DEX are unchanged.
