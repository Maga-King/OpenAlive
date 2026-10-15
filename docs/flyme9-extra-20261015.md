# Flyme 9 六款新增壁纸

版本：0.4.34-preview / versionCode 50。仅操作设备 `3B1F4UE5MS13HQRR`。

## 接入

| ID | 名称 | 渲染路径 |
| --- | --- | --- |
| 401 | 浪流 | 原始 60fps 全 I 帧视频 + MediaCodec / SurfaceTexture |
| 402 | 深潜 | 同上，保留原版循环终点 |
| 403 | 涟漪 | 同上，保留原版循环终点 |
| 501 | 3D 壁纸 V4 | 主图、深度图、zsstereo 两阶段 shader |
| 502 | 3D 壁纸 V5 | 同上 |
| 503 | 3D 壁纸 V6 | 同上 |

资源来自用户提供的系统公共组件 1.8.15，来源哈希见 `app/assets/flyme-video/source.json` 和 `app/assets/stereo/provenance.json`。视频文件必须 ZIP_STORED，供 AssetManager.openFd 直接访问；生产构建检查该约束。

视频默认遵循现有持续播放配置；关闭持续播放后采用原版 7 秒正常播放、1 秒减速后停止请求帧。隐藏立即停止解码，保留进度，恢复重建解码器；首帧显示官方缩略图。异步解码回调运行在 AliveGL，自身异常交给渲染循环清理，最多恢复两次。

Stereo 保留原始深度图、四层深度分带、空洞修复、4% 裁切与位移限制、锁屏/AOD 0.87 和桌面 1 的场景比例以及 1150ms 解锁曲线。魅族私有静止传感器以标准陀螺仪低角速度持续 0.75 秒替代；无陀螺仪设备仍可显示画面并执行场景缩放。

RenderLoop 管理传感器和解码生命周期；视频和 Stereo 使用自身活动状态调度，不套用旧 Cosmic 的 5 秒停帧时间。视频不显示无作用的桌面保持锁屏开关；Stereo 不显示循环动画开关。

## 验证

- 完整生产构建及其必需检查通过，日志 `build/flyme9-extra-build-final.log`。
- Stereo 20 项 host 测试通过；视频播放时钟与解码回调故障注入测试通过。
- 主力机 Adreno 830 实际 GPU / MediaCodec 测试：六款各两轮、12 cases、1861 checks，通过标记 `FLYME_EXTRA_OK`。
- 实际验证首帧垫图、真正视频输出（hasFrame 和异步回调）、连续帧变化、隐藏冻结、恢复续播、三场景、Stereo 视差和 keepLock、重复创建释放。
- 独立测试包已卸载。日志、截图见 `build/flyme-extra/`，测试可通过 `python tools/check_flyme9_extra.py --serial SERIAL` 重跑。
- APK 内新增资源与本地逐字节一致，六项 catalog 映射完整。
- 已覆盖安装主应用；安装包与设备 base.apk 的 SHA-256 一致：`2be6703d2e12a103266c587da2d270ed3d5083c6313f0f002aa055f39abc3e31`。
- `release/expected.json` 对应当前产物，`python tools/verify_release.py` 通过。

上述记录为 0.4.34 接入时的验证；后续 0.4.35 继续保留已验收的 0.4.33 时钟缩放逻辑。

## 0.4.35 补充

三款 Stereo 增加 −50～+50 灵敏度和默认开启的「息屏保持动态效果」。0 为 Flyme 原版响应，负档倍率为 `10^(value/50)`，正档为 `2^(value/50)`。配置直接在 GL 线程热更新，AOD 开关同时控制可见 AOD 的感应；完全黑屏、壁纸不可见或被已确认不透明的官方音乐背景覆盖时停止绘制和传感器。

Stereo host 检查共 37 项通过，用户主力机实测 3D 正常。独立 RenderLoop 生命周期测试完成编译，未取得通过结果，不计入实机通过项；其测试包已停止并卸载。

0.4.35 安装包与设备 base.apk 的 SHA-256 一致：`d8b8fe30809ce9f05b70ef9950f49736689903edee5ce80453cf63d0ebdcaae8`。发布清单为 `release/expected.json`，云端发布前必须重新构建并逐字节校验。
