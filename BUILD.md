# 编译

GitHub Actions 会在推送 `v*` 标签时编译、验证 APK，再发布 Release。手动运行工作流只生成构建产物。

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
