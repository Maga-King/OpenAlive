# 编译

GitHub Actions 会在推送 `v*` 标签时编译、验证 APK，再发布 Release。手动运行默认只生成构建产物；勾选 `release` 时同时发布。

## 环境

- Ubuntu 24.04、JDK 17、Python 3.12。
- Rust 与 `aarch64-linux-android` 目标。
- Android SDK Platform 35、Build Tools 35.0.0、NDK 29.0.14206865。
- Xposed API 82（仅用于编译，由脚本下载并校验）。

设置 `ANDROID_HOME`、`ANDROID_NDK_HOME`、`JAVA_HOME` 后执行：

```sh
rustup target add aarch64-linux-android
python3 tools/build_release.py
```

结果在 `dist/`。脚本从 Java 和 Rust 源码编译宿主与渲染库，重新编译 UI 资源；魅族官方 UI 的预编译 DEX 位于 `vendor/flyme/`，不是本项目从源码编译的部分。

未配置签名时会创建本地开发密钥。正式构建使用 `OPENALIVE_KEYSTORE`、`OPENALIVE_STORE_PASSWORD`、`OPENALIVE_KEY_ALIAS`（默认 `development`）；密钥不会进入仓库。GitHub 工作流通过仓库 Secrets 注入同一把密钥，方便覆盖安装。

资源表以未压缩形式存储并按 4096 字节对齐，签名后再次检查。安装包暂不包含视频壁纸。

小米超级壁纸的列表和预览使用 `app/assets/xiaomi/ui.apk` 中的原版控件，构建会校验所有保留文件。需要重新生成时执行 `python tools/import_xiaomi_ui.py ThemeManager.apk 场景APK目录`，再执行 `python tools/import_xiaomi_packs.py 场景APK目录`。输入版本固定并校验原包；目录内应有 `moon.apk`、`snowmountain.apk`、`geometry.apk`、`saturn.apk`、`earth.apk` 和 `mars.apk`。

六个场景以共享的 XZ 数据块内置，首次使用自动还原原包。构建逐块解压并校验六个完整 APK 的 SHA-256，保留原始签名、ZIP 布局和全部素材；新鲜检出可直接编译，不需要本地反编译目录。XZ for Java 1.12 的固定原版源码位于 `vendor/xz/`，编译其 Java 8 部分，不引入额外原生库。`.xz` 块在最终 APK 内保持 STORED，避免再次压缩增加体积与首用开销。

## 多语言

界面支持中文、English 和 Русский。代码里照常写中文即可，中文就是原文。

新增或修改界面文字后：

1. 运行 `python tools/i18n.py update`：自动用 `I18n.t("…")` 包裹新的中文，在 `app/i18n/strings.json` 中添加空条目，并重新生成 `I18nTable.java`（由 `strings.json` 生成，请勿手动修改；构建时也会自动更新）。
2. 可选：在 `strings.json` 中填写 `"en"` 和 `"ru"`。未填写时显示中文（缺少俄文时先显示英文），不影响使用。

`python tools/i18n.py` 只做检查，列出未包裹、未翻译和不再使用的文字；构建时也会自动执行。只有真正的错误才会让构建失败：`strings.json` 格式错误，或译文中的 `%s`、`%d` 等占位符与中文不一致。

不需要翻译的中文（农历、中文日期格式）在 `tools/i18n.py` 中列出；其他行可加注释 `// i18n:ignore`。在显示时才翻译的文字用 `I18n.mark("…")` 标记，例如静态数组。

原版 UI 包（`settings-ui.apk`、`editor-ui.apk`、`xiaomi/ui.apk`）保持不变：补充的俄文字符串位于 `app/locale-overlays/<包名>/`，构建按名称从原版包读取资源 ID，原版包更新后无需手动处理，已不存在的字符串会被跳过。运行时由 `LocaleOverlay` 叠加（Android 11 及以上）。应用自身资源位于 `app/res/values-en`、`values-ru`。
