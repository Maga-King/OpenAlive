# OpenAlive

把 Flyme Alive 的连贯动画带到 ColorOS，让息屏、锁屏和桌面接起来。

目前支持启航、留光、星月、轻启、山脉和银河，可以自选照片。选图后可按完整屏幕比例裁切，支持双指缩放、拖动和复位。留光支持独立相框照片、取景和角度调整，息屏时显示时间、日期和农历。

另有魅族动态壁纸和通知光效：可以选择来通知时短暂点亮息屏、系统光效，或 Flyme 圆圈与双侧光带；圆圈支持预设和自定义颜色。

Vivo Alive 壁纸包含液态、趣味光栅和闪卡，复用原版渲染器；支持自选素材、传感器灵敏度和可见息屏下的动画，完全不可见时暂停。

小米 Alive 壁纸包含月球、雪山、几何、土星环、地球和火星，复用小米原版列表、预览控件与渲染器。六款已经内置，首次点击自动准备，无需下载、导入或安装原版应用。地球、月球和火星各有 5 个落地点。支持桌面翻页联动和原版 AOD 时钟；锁屏使用 ColorOS 时钟。详见[接入与测试记录](docs/xiaomi-wallpapers.md)。

ColorOS 系统锁屏编辑器里也加入了魅族、澎湃和 Vivo 时钟，可调字体、颜色和效果，并支持息屏衔接与留光。新增 Vivo 玻璃与模糊效果会随壁纸实时变化。

界面支持中文、English 和 Русский，可在首页右上角“语言”中切换，默认跟随系统（系统语言不在其中时使用英文）。

需要 **Root + LSPosed**。安装后启用模块，勾选系统框架、系统界面和壁纸与个性化（`com.oplus.wallpapers`），重启，再打开应用选择并应用壁纸。目前主要在 ColorOS 17 上适配，其他系统还需要慢慢调。

下载：[Releases](https://github.com/Maga-King/OpenAlive/releases) · 编译：[BUILD.md](BUILD.md)

自实现代码采用 [MIT](LICENSE) 许可。**部分资产来源于魅族22FlymeOS**，官方 UI、字体、壁纸及效果素材不纳入 MIT 授权，详见[资源说明](THIRD_PARTY.md)。

还在持续完善。遇到问题欢迎提 Issue，带上系统版本、所选样式和复现步骤就好。

如果用着顺手，也欢迎请我喝杯奶茶。

<img src="docs/donate.png" alt="微信赞赏码" width="320" />

---

## English

Brings the seamless Flyme Alive animations to ColorOS, connecting the always-on display (AOD), lock screen and home screen.

Currently supports Set Sail, Linger Light, Moon & Stars, Light Start, Mountains and Galaxy, with your own photo. A chosen photo can be cropped to the full screen ratio with pinch-to-zoom, drag and reset. Linger Light supports a separate frame photo, framing and angle adjustment; the AOD shows the time, date and the Chinese lunar calendar.

Meizu live wallpapers and notification light effects are included too: on a new notification you can briefly light up the AOD, use the system effect, or the Flyme circle and side light bars; the circle has preset and custom colors.

Vivo Alive wallpapers include Liquid, Fun lenticular and Holo card, reusing the original renderer. They support your own media, sensor sensitivity and animation while the AOD is visible, and pause when fully hidden.

Xiaomi Alive wallpapers include Moon, Snow Mountain, Geometry, Saturn Rings, Earth and Mars, reusing Xiaomi's original list, preview widgets and renderers. All six are built in and prepared automatically on first tap, with no download, import or original app needed. Earth, Moon and Mars have 5 landing points each. Home screen paging and the original AOD clock are supported; the lock screen uses the ColorOS clock. See the [integration and test notes](docs/xiaomi-wallpapers.en.md).

The ColorOS lock screen editor also gets Meizu, HyperOS and Vivo clocks with adjustable font, color and effects, plus AOD hand-off and Linger Light. The new Vivo glass and blur effects follow the wallpaper in real time.

The UI is available in Chinese, English and Russian. Switch it with "Language" in the top-right corner of the home page; by default it follows the system (English if the system language is none of these).

Requires **Root + LSPosed**. After installing, enable the module, select System Framework, System UI and Wallpapers & Personalization (`com.oplus.wallpapers`), reboot, then open the app to choose and apply a wallpaper. It is mainly adapted for ColorOS 17 for now; other systems still need work.

Download: [Releases](https://github.com/Maga-King/OpenAlive/releases) · Build: [BUILD.en.md](BUILD.en.md)

Our own code is licensed under [MIT](LICENSE). **Some assets come from FlymeOS on the Meizu 22**; the official UI, fonts, wallpapers and effect assets are not covered by MIT, see [asset notes](THIRD_PARTY.en.md).

Still a work in progress. Issues are welcome; please include your system version, the chosen style and steps to reproduce.

If you enjoy it, you are welcome to buy the author a milk tea (WeChat code above).
