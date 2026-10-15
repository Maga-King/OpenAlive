# 音乐锁屏进入全景 AOD 的卡顿调查（2026-10-15）

已实现一个待设备验证的减负方案：当官方全屏不透明音乐背景完全覆盖动态壁纸时，只暂停其下方的壁纸渲染；开始淡出、解锁或失去可靠覆盖条件时恢复。尚未证明它消除了原生显示提交阻塞，不能宣称卡顿已修复。生产时钟代码、已验收的 0.4.33 缩放、歌词与布局行为保持现状。0.4.34 的壁纸集成本身不能作为此问题已解决的依据。

已确认：这次复现包含明显的渲染队列等待，以及一次约 75 ms 的原生 DRM 显示提交阻塞。时钟 Java 测量、布局和绘制录制耗时很小；没有发现它在这次转场中反复布局或运行自定义时钟弹簧。尚未确认：DRM 阻塞由 GPU 输入 fence、显示驱动/面板、刷新率或亮度切换中的哪一项触发；也没有原生时钟的同条件对照，不能据此证明本模块完全无关。

## 材料和范围

- `build/music-aod-20261015/reproduction-logcat.txt`：完整复现日志。
- 同目录 `reproduction-gfxinfo.txt`、`reproduction.perfetto-trace`、`trace-summary.json`。
- 同目录 `clock-render-chain.json`：本报告关键 SQL 和查询结果；`clock-perf-evidence.json` 为前期查询。
- 官方 SystemUI APK：`build/black-wake/SystemUI.apk`。本次只反编译读取了 `PlatformBlurManagerImpl.java`、`OplusPanoramicAodCoreStartable.java`、`OplusKgdImmersiveController.java`，输出在复现目录。
- 官方时钟源码：`build/lyric-clock/stock/sources/com/oplus/keyguard/clock/`。

分析窗口使用 Perfetto `ts` 范围 `12879418000000..12880250000000`，约为设备日志 `12:52:10.148..10.980`。所有耗时为 wall time，slice 可以包含等待；父子 slice 的总和不能当作独立 CPU 开销相加。

## 日志时间轴

| 设备时间 | 事件 |
| --- | --- |
| 12:52:10.148 | 电源键进入睡眠流程。 |
| 10.164 | `SmoothTransitionController` KEYGUARD → PANORAMIC_AOD；音乐黑色蒙层比例从 0 到 0.5；`OplusPanoramicAodCoreStartable.isPanoramicAod=true`。 |
| 10.167 | 外层插件收到 `uiState=5, clockSize=2`，仍为音乐沉浸时钟。 |
| 10.177 | 原生 `PanoramicScreenAnim` 开始，duration=720 ms。 |
| 10.181 | 时钟状态尺寸仍为 size=2；natural big=873、top=320、expected=1452、widgets=0。 |
| 10.182 | 官方 `OplusKgdImmersiveController` 因壁纸 surface/矩阵更新重新挂接音乐背景 surface。此日志本身不证明创建新窗口或执行重布局。 |
| 10.201–10.205 | Dreaming 开始；原生请求保持屏幕 ON 3000 ms；进入全景 AOD 亮度渐变，亮度策略从自动亮度转为 Doze。 |
| 约 10.214–10.289 | HAL 提交线程的 `DRMAtomicReq::Commit::` 持续 75.432 ms。 |
| 10.237–10.239 | `setAodIsInShow=true`、刷新 AOD 时间、重复 state=5/size=2。模块会去重相同状态和尺寸。 |
| 10.267 | 官方 `onFinishedGoingToSleep` 明确记录 `shouldUpdateUi=true`，允许全景 AOD 界面更新。 |
| 10.631 | 官方通知全景 AOD progress 动画结束。 |
| 10.904 | 原生面板转场结束，clockScale=0.9。 |
| 12:52:13.201 起 | 屏幕进入 DOZE。 |
| 12:52:18.213–18.215 | 官方 Seedling 将 `shouldUpdateUi=false`；AOD repository 发出 `(isPanoramicAod=true, shouldUpdateUi=false)`；平台调用 `pauseBlur`，`com.oplus.blur` 确认暂停 NotificationShade 采样。 |

实际睡眠动画窗口没有 `onStyleWidgetsMorphologyChanged`、`onClockExpectedHeightChange`，没有 ShaderCache 刷屏、GC 或 Choreographer skipped 日志。之前其他时间段的 ShaderCache 日志不能拿来归因此次复现。

## Perfetto 已证实的等待链

SystemUI PID=5351，主线程 tid=5351，RenderThread tid=5707；SurfaceFlinger PID/tid=5857；显示 HAL PID=5596；模糊服务 PID=8866（日志确认是 `com.oplus.blur`），Capture tid=9164。

| Slice ID / 起点 ts | 工作 | 耗时 |
| --- | --- | --- |
| 50284 / 12879476985044 | SystemUI `Choreographer#doFrame` | 27.747 ms，其中 `postAndWait` 为 21.490 ms |
| 50492 / 12879480184992 | RenderThread `DrawFrames` | 23.851 ms，其中 `queueBuffer` 为 21.960 ms |
| 51198 / 12879483970878 | HAL 提交线程 6022 `DRMAtomicReq::Commit::` | 75.432 ms；线程在其中休眠 75.192 ms |
| 52264 / 12879499786867 | SF `HwcPresentOrValidateDisplay 0` | 60.679 ms |
| 52266 → 52267 | SF → HAL 主线程 5596 的同步 Binder | 60.583 ms；HAL 主线程休眠 59.485 ms |
| 52460 → 52462 | `com.oplus.blur` Capture → SF binder 线程 6193 | 57.308 ms |
| 52463 / 12879505359263 | 该 Binder 内的 `captureLayers` | 55.864 ms；SF binder 线程休眠 55.583 ms |
| 54907 / 12879561003430 | 同一请求实际进入 `captureScreenshot` | 0.211 ms |

唤醒顺序进一步支持此链：提交线程 6022 先唤醒 HAL 主线程 5596；5596 返回并唤醒 SF 主线程；SF 主线程再唤醒截图 binder 线程 6193。截图请求开始时，DRM 和 HWC 阻塞已经存在。因此“单次截图约 56 ms”主要反映它在等合成线程，不能直接当作截图/GPU 算法用了 56 ms，更不能据此认定截图造成了那次 75 ms 显示阻塞。

该窗口有 43 次 `captureLayers`，确实会产生额外合成负载。SystemUI RenderThread 也录制了两种原生 `GradientBlur radius(69.782005x69.782005)`，尺寸为 1440×744 和 1440×1384，各 20 次；这些 CPU slice 很短，不能表示真实 GPU 执行耗时。GPU completion 等待最长 36.743 ms。FrameTimeline 存在 NotificationShade Buffer Stuffing 与 SurfaceFlinger Stuffing，和队列等待相符。

这份轨迹的 `gpu_slice` 为 0，也没有 DRM/SDE/DSI/GPU/fence 内核事件，缺少进一步区分输入 GPU fence 与显示驱动等待所需的证据。75 ms 原生提交阻塞是已证实的卡顿组成部分，底层触发原因仍未定位。

继续核对发现，壁纸 PID4248 同段 `eglSwapBuffers` 为 81.054 ms，`queueBuffer` 为 80.563 ms，GPU completion1608 等待 87.541 ms；SF presentFence343 等待 82.339 ms，结束都接近上述 DRM 提交完成。这支持跨生产者的 fence/提交停顿，不能把壁纸 GPU completion 的 wall time 直接当作 shader 执行时间。

## 已实现的壁纸覆盖减负

官方 `OplusKgdImmersiveController` 的音乐 host 是黑色背景的全屏 FrameLayout，使用全屏 SurfaceControlViewHost 挂到壁纸上。因此它稳定且完全不透明时，其下方动态壁纸仍提交帧属于可避免的工作。原轨迹 .832 s 窗口内壁纸有 78 次 GPU completion，表明它仍持续渲染。

新增 `ColorOsImmersiveBackdrop` 只观察官方状态，通过现有认证 Binder 的独立消息通知壁纸。必须同时满足：当前选中本模块壁纸、currentImmersiveType=2、MUSIC_VIEW、state=4（ENTERED）、KEYGUARD/PANORAMIC_AOD 且 keyguard 可见、host 已附着/可见、背景 OPAQUE、宽高等于显示尺寸、host 与父 surface 有效、两层 alpha 精确为 1、没有 pipeline crossfade。任何反射失败都恢复渲染并停止该优化。

开始淡出、释放音乐 surface、pipeline crossfade、旋转或解锁时先解除覆盖；host 布局/附着改变后重新验证；SystemUI 断连/替换 owner 时接收端解除覆盖；重连重放当前状态。普通锁屏没有有效 MUSIC_VIEW/currentType2/state4，不会暂停。覆盖状态仅通过认证 Binder 发送，去重后才跨进程；不改系统 blur 启停，不移除/隐藏壁纸 surface，防止连同其上的官方音乐子 surface 一起隐藏。

`ImmersiveBackdropPolicyTest` 覆盖退出、未进入、非音乐、非锁屏、不透明/尺寸/有效性失败与淡出 alpha/NaN 条件。`tools/check_layout.py` 全部通过，新增 Hook、Bridge、SceneChannel 已通过独立 Android javac。仍需设备确认日志出现 `Native music backdrop covered=true/false`，并验证覆盖时 AliveGL 停止提交、淡出/解锁/旋转/断连时恢复；设备结果应由实际采集补充。

## 为什么当前不建议改时钟状态或禁用模糊

主线程 5351 在分析窗口内，`NativeOfficialClockFaces` 的绘制录制仅 1 次，耗时 0.062 ms。`measure` 9 次，最长 1.983 ms；`layout` 7 次，最长 1.265 ms；主线程全部 `Record View#draw()` 98 次，最长 1.813 ms。这不支持“时钟每帧重建/重布局导致卡顿”的判断。一次 `measure()` 调用还可能命中 Android 缓存，不能只数源码调用次数推算实际测量开销。

`NativeOriginalClockPlugin` 对重复状态去重，size=2 的 KEYGUARD→PANORAMIC_AOD 保持同一个 immersed view。`NativeClockSceneTransition.start()` 遇到 `previous==incoming` 立即完成，这次没有创建模块时钟弹簧。日志 `coloringType=2`，模块对该沉浸场景关闭自身材质。

`NativeOfficialClockFaces` 确实把私有官方实例初始化为 uiState=2/size=2，并没有随外层状态切换而转发 uiState=5。但官方 `digital/domain/state/SceneKt.resolveScene()` 对 `(2,2)`、`(5,2)` 都返回 `KEYGUARD_IMMERSED`；`StateUtil.isKeyguardUiLayout()` 接受 2 和 5；`DigitalClockColorCoordinator` 的音乐沉浸 mask 分支对两者都返回 WHITE。因此它不是从普通锁屏场景漏切到另一套 AOD 视图的情况。补充状态/wakefulness 生命周期转发可以另行作为正确性议题，但当前没有证据它能消除渲染等待，且会触及已验收歌词/几何行为，不能冒充已验证的性能修复。

官方 `PlatformBlurManagerImpl.updatePlatformBlurState()` 的门控为：

- `isPanoramicAod && shouldUpdateUi` 时允许模糊更新。
- `isPanoramicAod && !shouldUpdateUi` 时调用 pause（音量模糊另有其原生保护）。
- 其 AOD callback 只重新计算此门控；它不以 720 ms 面板动画完成为暂停条件。

`OplusPanoramicAodCoreStartable` 把官方通知插件提供的 `shouldUpdateUi` 广播给平台模糊服务。完整日志显示 18.215 的 pause 与此协议吻合，不是“动画结束了但我方漏调用 pause”。本项目没有 hook `PlatformBlurManagerImpl`、`OplusPanoramicAodCoreStartable`、`pauseWindowBlur` 或 `resumeWindowBlur`；新增的 `OplusKgdImmersiveController` Hook 只观察覆盖条件。`ColorOsBridge` 的全景背景 mask、壁纸 fade 和屏幕窗口适配也没有改写上述 repository 的 `shouldUpdateUi`。这些事实排除了直接拦截 pause 的实现；仍不能排除其他壁纸/合成设置间接增加渲染负担。

官方音乐背景本身使用全屏 `SurfaceControlViewHost` 挂到壁纸 surface，并监听壁纸 surface、缩放和 offset 变化重新挂接。它与私有官方时钟实例是不同的渲染对象。匿名层 `#9120` 的具体身份尚未可靠确认，不能把它直接等同于时钟或某个第三方插件。

## 可验证的下一步

已实现上述覆盖减负，下一步由负责设备的主任务执行同条件对照：

1. 相同壁纸、音乐、歌词、颜色、亮度、刷新率和通知列表，只切换官方原生时钟与本模块时钟，各复现至少 3 次。对齐电源键/面板动画起点，比较 DRM commit 最大值与分布、SF HWC 等待、SystemUI queueBuffer、慢帧比例。保留完整日志和轨迹。
2. 相同本模块时钟，分别开启/关闭音乐沉浸场景；再使用静态与当前动态壁纸对照。由此区分官方音乐背景/模糊负载、动态壁纸负载与私有时钟实例，而不是一口气关闭系统 blur。
3. 若只有模块时钟复现，增加临时只读计数/trace：官方实例 `onClockStateChanged`、`setWallpaperBitmap`、几何 callback、`requestLayout`，以及原生 repository 的 `isPanoramicAod/shouldUpdateUi`。只有观察到异常更新/不同门控后，再设计最小代码修复。
4. 若官方原生时钟同样出现长 DRM commit，补采 GPU render stages、fence 与可用 DRM/SDE 内核事件，检查亮度/VRR 切换与输入 fence 等待。现有轨迹不足以给出驱动级修复。

采集模板为 `build/music-aod-20261015/perfetto-music-aod.pbtxt`，12 s/64 MB，包含主线程/渲染 atrace、FrameTimeline、GPU render stages 与候选 fence/DRM/SDE 事件。先读取设备 `/sys/kernel/tracing/available_events`（必要时 `/sys/kernel/debug/tracing/available_events`），按实际事件名称调整模板；不存在的事件与未提供的 GPU data source 不能计为成功采到。开始采集后保留约 2 s 稳定音乐锁屏，再按电源键，留到 AOD 稳定。每次保存完整 logcat 与壁纸/时钟配置；比较同一时钟下优化前后，再做官方时钟对照。

不建议在 AOD 转场开始或 720 ms 后硬 pause 系统 blur；它会越过官方 `shouldUpdateUi` 协议，可能冻结仍在更新的通知、歌词/音乐背景或壁纸效果。可以在明确的临时实验中测试其影响，但不能在没有对照证据时发布为修复。

## 复核 SQL

使用 `from perfetto.trace_processor import TraceProcessor` 打开上述轨迹。`clock-render-chain.json` 保存了完整查询和原始结果。以下为最短复核入口：

```sql
-- 提交、Binder、截图各层级，避免重复累计父子耗时。
SELECT s.id,s.ts,s.dur/1e6 AS ms,s.name,t.tid,p.pid,s.parent_id
FROM slice s
JOIN thread_track tt ON tt.id=s.track_id
JOIN thread t USING(utid)
LEFT JOIN process p USING(upid)
WHERE s.id IN (51198,52264,52266,52267,52460,52462,52463,52464,54907)
ORDER BY s.ts;

-- 同步 Binder 对应关系。
SELECT * FROM flow WHERE slice_out IN (52266,52460);

-- 休眠/运行状态与唤醒者；waker_utid 标在被唤醒后的状态上。
SELECT s.ts,s.dur/1e6 AS ms,s.state,t.tid,w.tid AS waker_tid
FROM thread_state s
JOIN thread t USING(utid)
LEFT JOIN thread w ON w.utid=s.waker_utid
WHERE t.tid IN (6022,5596,5857,6193)
  AND s.ts BETWEEN 12879483000000 AND 12879562000000
ORDER BY s.ts;

-- 主线程测量/布局/官方时钟录制。
SELECT s.name,COUNT(*) AS n,MAX(s.dur)/1e6 AS max_ms
FROM slice s
JOIN thread_track tt ON tt.id=s.track_id
JOIN thread t USING(utid)
WHERE t.tid=5351
  AND s.ts BETWEEN 12879418000000 AND 12880250000000
  AND (s.name IN ('measure','layout','Record View#draw()')
       OR s.name LIKE 'org.aliveclean.NativeOfficialClockFaces%')
GROUP BY s.name;
```

时间换算使用本轨迹 clock snapshot：`REALTIME(ns) = ts + (1792039893936944074 - 12843206995579)`；日志显示为设备本地时区 UTC+8。
