# ColorOS 官方歌词时钟

## 原版依据

从授权主力机 `3B1F4UE5MS13HQRR` 的 `/system_ext/app/KeyguardPersonalityClocks/KeyguardPersonalityClocks.apk` 拉取并使用 jadx 1.5.5 分析。本地输出在 `build/lyric-clock/stock`，设备系统 APK 不上传仓库。

- `SceneKt.resolveScene`：`uiState=2/5` 且 `clockSize=2` 对应 `KEYGUARD_IMMERSED`；`uiState=3` 是独立 AOD 场景。
- `ClockContainer.createClockView` 为歌词场景创建独立 `SingleClockView`，包含原版日期、星期和五个数字／冒号控件。
- `KeyguardImmersedViewLayoutHandler` 决定布局与字体：日期顶部 80dp，支持原版固定／可变字体分支；不复用厂商通知小钟的高度。
- `onClockLayoutCalculated` 单独上报 `clockHeightImmersed`、`clockTopImmersed`，供歌词页面避让。
- 官方日期、数字控件实现 `BlurInterface`，通过 `setBlurRatio` 和 RenderEffect 支持模糊；配色模式、HSL 深度和背景取样由官方颜色控制器处理。

## 实现

`NativeOfficialClockFaces` 通过本机 `OplusStyleClockPluginProvider` 创建真正的 `com.oplus.keyguard.clock.digital` 插件，使用其原版 `ClockViewRoot`、日期／星期和数字视图。不存在自行模仿的 TextView 兜底。

包装层只裁掉完整屏幕布局的外部空白；内容的位置、字号、字体轴、文本格式和绘制仍归官方插件。按原版内容视图计算实际裁切高度，不用估算字体高度。编辑器／测试进程不属于 SystemUI 时，仅恢复这个私有实例的日期和时间可见性，不改全局进程标记。几何反射方法缓存；监听随视图 attach/detach 注册与移除，释放可重复调用，构造失败会清理已创建资源。

官方 `getClockTimeGlobalVisibleRect` 使用全屏父视图裁切后的全局矩形再加数字偏移。在外部容器裁切后，这会重复计算顶部偏移。因此数字边界直接从原版 `getDigitalTimeViews()` 变换到包装层坐标；不改变数字视图本身。

`NativeImmersedClockFaces` 在歌词场景选择这个官方视图，其余场景仍使用所选厂商视图。退出歌词、通知、真正 AOD、解锁隐藏保留现有语义。`NativeOriginalClockPlugin` 上报独立歌词尺寸，不计入已隐藏组件的占位。时间跟随原有 `setTime`，没有新增计时器或持续调试日志。

原生配色模式交给官方 `setClockColor`；自定义材质 5–8 保留模块原有材质处理。清空自选 HSL 时显式恢复本机官方默认值，避免官方接口省略参数时沿用旧值。原版日期、数字控件的模糊能力保留；跨厂商大钟与歌词钟之间仍采用本项目现有场景弹簧过渡，不能等同于完整复刻官方的全部模糊过渡。弹簧驱动的透明 overlay 设置为 1×1 并请求首帧，避免零尺寸导致不触发动画。

官方插件会共享字体缓存和动态景深引擎管理器；本项目沿用其正常创建／释放协议，没有隔离 ClassLoader 或修改全局管理器。原版 release 会清缓存及 keepAlive 标志，并存原版动态景深预览的极端切换尚未做专门验收。

## 验证

在指定主力机运行独立测试包，未写入系统当前时钟配置：

`python tools/check_native_clock_fonts.py 3B1F4UE5MS13HQRR --official-immersed --immersed-only`

- SystemUI 身份的测试上下文加载实际官方管线，并与独立原版实例对照日期、星期、五个数字／冒号的控件类型、文本、字号、字体、字体轴、颜色及布局。
- 验证七个官方文本控件的模糊参数设置与恢复，实际像素绘制、数字边界和裁切尺寸；另检查 HSL 恢复默认、移除重挂、移除后释放及重复释放。
- 全部 63 款已接入时钟验证进入歌词、`uiState=2/5`、退出歌词、AOD、解锁隐藏与过渡完成后的坐标和透明度。本机官方歌词内容高度为 68dp。
- 三家代表款通过本机官方外层容器验证歌词场景路由和原款式恢复。

日志：`build/lyric-clock/final-tests.log`。成功标记：`NATIVE_OFFICIAL_IMMERSED_OK`、`NATIVE_IMMERSED_CLOCK_OK styles=63`。截图：`build/native-clock-test/3B1F4UE5MS13HQRR-immersed-doodle.png`。

真实 SystemUI 会继续使用已加载的 Hook 类。覆盖安装后仍需用户重启系统界面或设备，再对实际歌词页进行肉眼验收；独立测试不能代替这一步。

## 2026-10-15 本地交付状态

本轮修复数字坐标、HSL 重置、绘制监听生命周期之后，上述独立真机测试全部通过。完整 `tools/build_release.py` 构建成功，`tools/verify_release.py` 校验 APK、SHA256SUMS 和构建报告一致。

已通过 `install -r` 在指定主力机覆盖安装 `0.4.31-preview`（versionCode 47），并读取设备 base.apk 校验 SHA-256 与本地完全一致：`e13242a5c2c6a5bd444fecafe53be1676361d0250ca646e8944a2babb3fd9bc1`。本轮尚未上传 GitHub 或创建 Release。

另对此前 AOD 开关、液态灵敏度、选择器兜底、16 个 Activity 竖屏改动完成只读审查，未发现确定性新问题。选择器首选路径和应用结果核验与 HEAD 一致；液态回调字段已对照原包反编译，停止／恢复回调／销毁顺序正确。此前 `VIVO_SCENE_OK` 和 `VIVO_EDITOR_FLOW_OK` 记录保留，本轮没有将旧测试冒充重新运行。
