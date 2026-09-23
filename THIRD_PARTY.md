# 资源说明

**部分资产来源于魅族22FlymeOS。** 本项目使用的参考版本为魅族 22 的 Flyme 12.6.0.0A，包含 AliveWallPaper 13.0.5 和 SystemUIEditor。

以下内容保留各自权利人的版权及原有许可，不适用本项目的 MIT 许可：

- `vendor/flyme/`：官方 UI 的预编译 DEX 和配套资源。
- `app/assets/ui/`、`app/assets/clock/`、`app/res/font/`：官方 UI 资源和字体。
- `app/assets/shader/`、`native/shaders.rs`：官方着色器及生成的嵌入文件；文件内的第三方版权声明保留。
- `app/assets/masks/`、`app/assets/editor/`、`app/assets/wallpapers/`：效果遮罩、预览图和壁纸。
- `app/assets/examples/`：示例图片，版权归原作者所有。
- `app/assets/cosmic/`：官方 Cosmic 系列配色、预览图及球体模型数据；来源文件校验值保存在同目录 `source.json`。
- `docs/donate.png`：项目维护者提供的赞赏码，不属于代码许可范围。

资源来源标注不代表魅族或其他权利人授予了再分发或商用许可。使用这些资源时，仍需遵守其适用许可；MIT 仅授权本项目有权授权的自实现代码。

Xposed API 82 仅用于编译，不打进 APK；运行时由 LSPosed 提供。
