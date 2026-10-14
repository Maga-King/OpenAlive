# 资源说明

**部分资产来源于魅族22FlymeOS。** 本项目使用的参考版本为魅族 22 的 Flyme 12.6.0.0A，包含 AliveWallPaper 13.0.5 和 SystemUIEditor。

以下内容保留各自权利人的版权及原有许可，不适用本项目的 MIT 许可：

- `vendor/flyme/`：官方 UI 的预编译 DEX 和配套资源。
- `app/assets/ui/`、`app/assets/clock/`、`app/res/font/`：官方 UI 资源和字体。`settings-ui.apk` 保留 WallpaperSetting 原始布局、控件和资源，其来源校验值见 `settings-ui-source.json`。
- `app/assets/shader/`、`native/shaders.rs`：官方着色器及生成的嵌入文件；文件内的第三方版权声明保留。
- `app/assets/masks/`、`app/assets/editor/`、`app/assets/wallpapers/`：效果遮罩、预览图和壁纸。
- `app/assets/examples/`：示例图片，版权归原作者所有。
- `app/assets/cosmic/`：官方 Cosmic、Phoenix 系列配色、预览图及模型数据；来源文件校验值保存在同目录 `source.json`、`phoenix-source.json`。
- `app/assets/gradient/`、`app/assets/shader/gradient/` 和 `app/assets/cosmic/gradient_*.png`：Flyme 9 系统公共组件 1.8.15 的大漠、极光、远海配色、原始着色器及缩略图；原 APK 与文件校验值见 `gradient/source.json`。园蓝、梅洛、金杏、青提原始配色和预览图见 `cosmic/flyme9-source.json`。这些资产保留原权利人的权利。
- `app/assets/bubble/`、`app/assets/soundviz/`：官方 Bubble 系列的数值配置和流动背景着色器；来源校验值见 `bubble/source.json`。
- `docs/donate.png`：项目维护者提供的赞赏码，不属于代码许可范围。
- `app/assets/notification/`：魅族 22 SystemUI 的原始通知光环和双侧光带动画，来源及文件校验值见同目录 `source.json`；使用已包含的官方 SVGA 运行库解码和绘制。
- `app/assets/native-clock/`：魅族 22 SystemUI 与澎湃 MIUIAod 的数字字体。`original/` 保留未修改文件，使用副本按 ColorOS 固定字体约定调整行高与留白，字形和字宽不变；来源、适配说明及校验值见 `source.json`。
- `app/assets/native-clock/templates/flyme/`：魅族 22 SystemUIEditor 的 ALIVE 横排、竖排息屏时钟模板及配套字体，保留原始 XML 和字体；来源及校验值见各模板的 `source.json`。
- `app/assets/native-clock/runtime/hyperos/fonts/`：澎湃备用机系统分区的原始时钟字体，供独立澎湃控件使用，未修改字形或度量；来源及校验值见上级目录 `source.json`。
- `app/assets/native-clock/runtime/hyperos/runtime.apk`：备用机 MIUIAod 的原始程序与资源，供独立类加载器调用其中的时钟控件；版本、来源及校验值见同目录 `source.json`，文件未修改。
- `app/assets/native-clock/previews/`：使用上述原始澎湃控件及魅族 SystemUIEditor 原始透视时钟控件绘制的选择器缩略图；模板参数、原始运行库和图片校验值见 `source.json`。
- `app/assets/native-clock/vivo/`：OriginOS SystemUIPlugin 原始时钟布局、资源表、字体和玻璃/模糊着色器。`layouts.apk` 保留原始 DEX，隔离加载其中的布局控件，不启动 Vivo 服务；来源和校验值见 `source.json`。`previews/` 由适配后的原始布局绘制。该目录资源保留原权利人的权利，不属于本项目 MIT 授权范围。

资源来源标注不代表魅族或其他权利人授予了再分发或商用许可。使用这些资源时，仍需遵守其适用许可；MIT 仅授权本项目有权授权的自实现代码。

Xposed API 82 仅用于编译，不打进 APK；运行时由 LSPosed 提供。

- `app/assets/vivo/runtime/`：Vivo LiveWallpaperBox 7.0.1.02 原始程序及已下载的闪卡、光栅纹理。通过独立类加载器复用原版渲染代码，APK 与纹理内容未修改；仅重映射离线纹理路径。来源和 SHA-256 见同目录 `source.json`。这部分保留原权利人的权利，不属于本项目 MIT 授权范围。
- `app/res/drawable/vivo_*`：Vivo BBKTheme 原始编辑器图标，来源及校验值见 `app/assets/vivo/ui-source.json`。光栅选项的原始 PAG 动画及配套 `libpag`、`libffavc` 保存在上述 Vivo 运行库中，保留其各自版权与许可。
- `app/assets/xiaomi/ui.apk`：小米 ThemeManager 的原始列表、预览、进度条和落地点控件。DEX、资源和素材内容不变，只去掉未调用的视频编辑原生库及原 APK 签名元数据并重新压缩；原包与逐文件校验值见 `vendor/xiaomi/ui-manifest.json`。六款横幅来自各自原版场景 APK。`app/assets/xiaomi/packs/` 保存六个原包的无损共享块，按 `packs.json` 还原后与 `catalog.json` 记录的原始 APK SHA-256 完全相同。小米程序、素材及其依赖库保留原有权利，不属于本项目 MIT 授权范围。
- `vendor/xz/xz-1.12-sources.jar`：XZ for Java 1.12 原始 Java 源码，来源与 SHA-256 见同目录 `source.json`，采用 0BSD（见 `COPYING` 与 `LICENSE`）。构建仅编译 Java 8 源码，用于内置场景的流式解压；原版权与贡献者注释保留。
- `app/assets/branding/`、`app/res/drawable/ic_xiaomi_official.xml`：小米官网的 MI 标识，保留官方路径与颜色；原始 SVG、网址及 SHA-256 见 `branding/`。商标与图形权利归原权利人。
- `vendor/onnxruntime/`：Microsoft ONNX Runtime Android 1.23.2 的 Java 类和 arm64 原生库，采用 MIT；原始发行来源和 SHA-256 见 `source.json`，许可证与第三方声明随 APK 一同打包至 `assets/licenses/onnxruntime/`。
- `app/assets/vivo/models/u2netp.onnx`：rembg 发布的 U2NetP ONNX 模型，原项目为 U-2-Net，其项目采用 Apache-2.0 并提供预训练权重链接。模型未修改；来源、固定版本的上游校验依据和 SHA-256 见同目录 `source.json`，原项目许可证一并保留。
