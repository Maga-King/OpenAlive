# Vivo AOD 开关与壁纸选择器兜底

## AOD 开关

三个 Vivo 编辑器的「息屏与亮屏壁纸缩放」下方统一增加「息屏保持动态效果」。`VivoOptions.aodAnimation` 默认 true，缺少字段的旧配置同样默认开启；JSON 保存编辑草稿和实际应用设置。

`VivoEngineScene` 在 mode=0 且可见时按开关启停原版控制器、光栅视频和传感器。开关变化热更新，不重建场景，也不重算蒙版。锁屏、桌面不受此开关影响；屏幕完全关闭/不可见时仍停止控制器。缩放和亮度衔接独立保留。

设备 `3B1F4UE5MS13HQRR` 上已运行生产渲染代码的独立测试：

- 三类壁纸默认开启、关闭/重新开启、锁屏恢复、完全黑屏暂停与恢复。
- 液态/闪卡全部 36 种效果与范围组合，验证开关不重建控制器、关闭时无遗留传感器。
- 感应视频 AOD 开关、原版倾斜 seek；三位置图片/视频全部 8 种组合及裁切旋转回归。

命令：`python tools/check_vivo_runtime.py --serial 3B1F4UE5MS13HQRR`，视频加 `--media`。日志保存在本地 `build/vivo-aod-test.log`、`build/vivo-aod-media-test.log`。

## 选择器

首选 `ACTION_CHANGE_LIVE_WALLPAPER` + `EXTRA_LIVE_WALLPAPER_COMPONENT` 的原调用保持不变。

仅在首选调用抛出启动异常后：

1. 查询支持该 action 的导出 Activity，使用显式组件重试，保留目标壁纸服务参数。
2. 尝试 AOSP/OEM 已知的公开 `com.android.wallpaper.livepicker.LiveWallpaperChange`，兼容缺少 intent-filter 的实现。
3. 动态壁纸列表、普通壁纸应用入口、最后通用 SET_WALLPAPER 均携带同一壁纸服务参数。通用 SET_WALLPAPER 可能打开图片库，因此放在最后。

普通应用 MAIN 入口不保证读取壁纸参数，不能保证自动定位。此时提示选择 OpenAlive 对应壁纸；实际组件和壁纸 ID 的应用结果检查保持原逻辑，取消或应用其他壁纸不提交配置。

依据：GitHub 的 [LineageOS LiveWallpaperChange](https://github.com/LineageOS/android_packages_wallpapers_LivePicker/blob/lineage-22.2/src/com/android/wallpaper/livepicker/LiveWallpaperChange.java) 与主力机 `/product/app/WallpaperChooser/WallpaperChooser.apk` 中该类，均读取标准组件 extra 后查询匹配服务。未尝试调用非导出的 `LiveWallpaperPreview`，未猜测 OEM 私有 extra。

用户提供的 LSPosed 日志包含 ColorOS 17 的普通壁纸/主题页面活动，但没有本次直接预览失败的异常，不能据此确定反馈设备的具体失败原因。

本地 `tools/check_wallpaper_picker.py` 覆盖原主路径、显式重试、入口缺失、安全异常、所有兜底的组件保留及取消/错壁纸结果。独立 `tools/check_wallpaper_picker_device.py --serial ...` 使用测试 Activity 强制首选入口失败，再启动实际系统预览；主力机已确认系统绑定 OpenAlive 引擎。自动返回检查受用户同时操作影响；用户已肉眼验收从应用页进入后续选择器。独立测试包不会点击应用，也不修改当前壁纸。

生产未增加轮询或逐帧调试日志。测试输出只写在独立测试包或本地 build 目录。

## 真机现场补充

用户观察到一次锁屏壁纸短暂变黑、时钟正常。22:22:24 的 logcat 和 `dumpsys activity exit-info org.aliveclean` 均明确记录 `reason=16 (PACKAGE UPDATED)`、`killDueToPackageUpdate`，与本次覆盖安装一致。当前采集窗口没有 OpenAlive 的渲染异常或致命崩溃；这支持更新中断的解释，但用户未给出黑屏精确时间，不能据此排除其他偶发情况。用户随后反馈液态 AOD 看起来正常，因此未额外改动传感器策略。

最终安装 APK 与本地 0.4.29 构建 SHA-256 一致：`32d3b5b2474184313f9f60cd0a392ca679554d0e8a934244446042028d5ad1cc`。本轮尚未发布远端。

实际编辑页的开关位置/默认值/保存/重进断言已加入 `VivoEditorFlowProbe` 并通过编译。为避免继续打断用户当前壁纸，未运行这轮针对主应用的 instrumentation；渲染与视频的独立 APK 测试已经通过。

## 后续：液态灵敏度与竖屏

用户追加要求液态采用光栅、闪卡同样的灵敏度滑条，并让应用所有页面固定竖屏。

- 液态四种效果使用原包 `e4.b` 加速度监听器。其 `e4.a.p(x,y,z)` 回调已去除重力并处理旋转；`VivoLiquidSensitivity` 只对这一步的运动输入使用既有 `VivoSensorGain.multiplier` 增益。默认 0 为 1 倍，范围 −50～+50 对应 0.1～2 倍。原版触摸、开屏冲量、重力滤波及传感器注册方式不变。
- 四种效果均在配置热更新中调整增益，不重建控制器；释放时恢复原回调。默认增益直接传递原始回调参数。
- Manifest 全部 16 个 Activity 设置 `screenOrientation="portrait"`。
- 独立真机测试通过原版加速度事件验证多个增益、回到默认、滤波状态完全一致、原始事件未修改和回调释放；36 种液态/闪卡组合的渲染、AOD、暂停恢复回归通过。日志：`build/vivo-liquid-sensitivity-test.log`。
- 0.4.30 已在指定主力机覆盖安装，本地与设备 APK SHA-256 一致：`17be2d0e45cd06e17acadbb769b17511719ea3655316401f763dfee7a7bc5826`。`release/expected.json` 已更新并通过校验，尚未上传或发布。
- 首轮实际编辑页测试时设备处于 Dozing、Keyguard showing，预览未创建，未获得 UI 测试通过结论。测试入口现明确要求设备解锁，并仅在测试页面可见时保持亮屏，避免把锁屏导致的无画面误判为产品渲染故障。
- 用户解锁后重跑实际编辑页测试通过（`VIVO_EDITOR_FLOW_OK`）：三类壁纸的灵敏度拖动、保存和重进恢复，AOD 开关位置、默认开启、关闭保存和重进恢复，以及全部 Activity 的竖屏声明。液态/闪卡新导入照片后的涂抹与主体范围弹窗、连续双指裁切也通过，手势及队列处理分别为 788/778 ms。测试结束已恢复原编辑草稿、清理测试素材并卸载独立测试包；未更改已应用的壁纸配置。日志：`build/liquid-portrait-editor-test.log`。
