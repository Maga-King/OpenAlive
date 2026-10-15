# 歌词退出／音乐通知时钟大小抽动

## 现场与官方链路

设备仅 `3B1F4UE5MS13HQRR`。现场日志保存在忽略的 `build/clock-size-jitter`，没有新增常驻调试日志。

`full-logcat.txt` 的 09:26:21.422–.431：退出歌词后先收到 size=1，组件随后回报 `cardOccupiedRows=1, originCardOccupiedRows=0`，小钟高度变成906，大钟仍为873；系统把大钟高度改成2147483327，并下发size=0。原始行数补齐后大钟恢复1241，系统再次计算是否缩小。

通过本地 `build/black-wake/SystemUI.apk` 定向反编译三类到 `build/clock-size-jitter/stock-systemui`：

- `KeyguardStyleClockCallback.onClockLayoutCalculated` 先读取小钟高度，再解析同包的大钟参数。
- `KeyguardStyleClockControllerImpl.access$extractBigClockLayoutParams` 检查 `bigCur==0 || bigMin>bigCur || bigCur<=smallHeight`。条件成立时写入 `Integer.MAX_VALUE-bigClockTop`，本机就是2147483327。因此该数字是宿主的无效尺寸标记，不是已经证实的浮点／测量溢出。
- `KeyguardClockSizeChangeWrapper.pluginClockSize` 根据系统内部尺寸状态与 `suppressSmallSizeDeliver` 决定对插件发送0或1。播放器／通知是否让时钟变小由实际剩余高度决定，不单看通知数量。
- 官方 `StyleWidgetState` 允许行数回调瞬态不一致；本项目此前同步上报每次回调，从而暴露了这个无效组合。下面的避让保护是本项目适配，不声称原版存在同样的 max 校正。

该无效值也有合法用途：官方 `BaseClockImpl`、`GalleryClockImpl` 不提供独立大钟参数，宿主会稳定采用小钟布局。因此原生横排大小尺寸相同的三方模板不应被强行补造1像素“大钟”。全款回归检查的是组件行数瞬态不能改变既定的大钟可用性，允许这些模板始终采用小钟规则。

## 修复

`NativeClockWidgetBridge.occupiedRows(true)` 用 `max(originalRows, occupiedRows)` 计算大钟避让。保留两种原始行数，等待正常后续回调；两者都回到0时仍清空。现场数值变为大钟873+368=1241，小钟538+368=906，满足宿主约束。

逐帧回归另外复现了同一帧歌词→大钟→小钟的中断错误：第一段还未绘制，第二段已从隐藏的大钟读取尺寸，造成小钟起始缩放1.6099815，矩形扩大到1091×829，超过歌词／小钟两个可见端点。

`NativeClockSceneTransition.capture()` 在仍等待首次pre-draw时保留原屏幕起点；已经绘制的动画仍捕获当前可见位置。只有“新过渡替代旧过渡”的路径丢弃旧完成回调，避免旧场景在新状态提交前同步结算；其他cancel调用保持原语义。

未更改uiState=5的既有AOD适配，也未猜测修改官方尺寸抑制策略。官方按请求场景查询数字边界的协议与当前模块仍有差异，但本次INTMAX根因已被宿主源码确认，未把未证实的差异混入补丁。

## 验证

新 `NativeClockJitterInstrumentation` 在独立Activity逐帧测试，不修改当前系统时钟配置：

- 未修复包重放1/0行数，确定失败于 `bigCur=873, smallClockHeight=906`：`baseline-unlocked.log`。
- 只修行数后，同一帧转场确定失败于1.61倍缩放：`baseline-transition.log`。
- 两处修复后通过HyperOS手绘和Flyme代表款，共837个绘制帧、76次布局回调：`fixed-tests.log`，标记 `NATIVE_CLOCK_JITTER_OK`。
- 覆盖完整转场、同帧重定向、8ms重定向、中途反向、小钟state5→state2唤醒及唤醒中断；检查每次尺寸上报、有限坐标、过渡范围和最终恢复。

全款及官方实例回归日志：`all-clocks.log`。实际歌词页面和真实黑屏双击仍需安装后重启SystemUI再验收，不能由独立窗口测试替代。

## 唤醒时祖先缩放引起的 1 像素波动

安装 0.4.32 后的真实验收还发现：SystemUI 外层唤醒缩放为 0.9 时，大钟内容高度在 873／874 之间变化。原先通过全局矩阵再乘祖先逆矩阵计算局部内容边界，浮点误差经过 floor／ceil 后使固有高度增加 1 像素，正好跨过下面记录的 1562 阈值。

`NativeHyperOsFace.map()` 改为只累计数字控件到目标祖先之间的局部矩阵、位置和父级滚动，最终只映射一次矩形。保留内部旋转／缩放；不包含目标祖先及更外层动画，也不修改取整规则或人为增加误差容限。

`local-geometry-unlocked.log`：独立真实窗口通过 2 款、939 帧、176 次布局回调；测试覆盖外层 0.9／0.95／1／0.99 缩放、分数位移及反复大小钟切换，要求内容尺寸与基线精确相等。此前 `local-geometry-tests.log` 的 frames=0 是测试窗口被锁屏遮挡，未当作产品几何失败或成功证据。本轮结果仍不能替代安装到 SystemUI 后的真实验收。

## 官方通知／播放器尺寸策略复核

以下结论来自指定设备的本地 APK。宿主源码根目录记为 `S=build/clock-size-jitter/stock-systemui`；时钟插件源码根目录记为 `C=build/lyric-clock/stock/sources/com/oplus/keyguard/clock`。这些反编译产物在忽略目录中，行号用于本地复核，不表示所有 ColorOS 版本均采用相同策略。

### 可见通知先决定内部大小，剩余空间再决定插件大小

官方存在两个连续决策层，不能把日志里的内部 `ClockSize.SMALL` 直接等同于插件收到 `clockSize=0`。

| 环节 | 已确认行为 | 源码位置 |
| --- | --- | --- |
| 可见内容 | `hasVisibleNotifications()` 为 `StackedNotificationEx.isAnyNotificationVisibleForClock().value` 或可见的 Oplus Seedling 播放器卡片；判断对象是供时钟布局使用的可见状态，不是系统通知总数 | `S/NotificationPanelViewController.java:3266` |
| 普通常规布局 | 可见内容存在时选内部 SMALL，否则 LARGE，并经 `KeyguardClockInteractor.setClockSize()` 写入 repository | `S/NotificationPanelViewController.java:4296`、`S/KeyguardClockInteractor.java:145` |
| 分栏布局 | 除可见内容外，还考虑 `hasActiveMedia()`；当 `mDozing && getAlwaysOn()` 时，该 active media 条件本身不强制 SMALL | `S/NotificationPanelViewController.java:4297` |
| ColorOS 扩展态 | VM 组合 repository 大小、通知扩展态和 suppress 标志；扩展态 `newState=0` 沿用 repository，`1` 强制内部 LARGE，`2` 强制 IMMERSED | `S/OplusKeyguardClockViewModel.java:128`、`S/ClockSizeFlow.java:58` |
| 插件尺寸 | 内部 LARGE→1；IMMERSED→2；SMALL 且 `suppressSmallSizeDeliver=false`→0；SMALL 且该标志为 true→1 | `S/KeyguardClockSizeChangeWrapper.java:95` |

因此，可见通知或可见播放器卡片会先让常规通知面板选择内部 SMALL；ColorOS 仍可能保留插件大钟1，让其在通知上方可用的空间内显示。歌词等沉浸扩展态另走2，不等同普通播放器卡片。

`KeyguardStyleClockControllerImpl.onClockExpectedHeightChange()` 在 `S/KeyguardStyleClockControllerImpl.java:3383` 计算：

```text
expectedClockHeight = screenHeight - notificationHeight - keyguardBottomPadding - clockBottomMargin
```

随后 `sendExpectedClockHeight$default()` 的核心比较在同文件2664行：`enterSmallClock = expectedClockHeight <= bigClockMinHeight + bigClockTop`。当内部大小不是 LARGE 时，条件成立会将 suppress 设为 false，允许发小钟0；剩余高度严格大于该阈值时将 suppress 设为 true，内部 SMALL 也会映射成插件1。内部已是 LARGE 时同样将 suppress 设为 true。等号属于小钟分支，源码没有为这个比较提供1像素容差或滞回区间。

这条结论适用于现场走到的常规默认锁屏分支。低高斯能力设备会跳过部分 expected/suppress 更新；非默认锁屏、解锁时采用桌面尺寸，以及扩展沉浸态也各有分支，不宜概括成所有场景都按同一个条件切换。黑屏双击从全景 AOD 回到锁屏时，也没有“只要仍有通知就强制0”的额外规则：`S/KeyguardStyleClockControllerImpl.java:413` 重新取当前 VM 状态，随后按包装器结果下发。

### 本次1562像素边界允许稳定显示大钟

后续真实验收 `acceptance.log` 的 09:49:02.831–.832 另记录了与行数错误不同的1像素翻转。通知占用高度为1102，屏幕高度3168，底部预留424，钟与通知间距80，因此 `expectedClockHeight=1562`；`bigClockTop=320`。

| 上报 bigMin | bigMin+top | 比较结果 | 内部 SMALL 对插件的结果 |
| ---: | ---: | --- | --- |
| 1242 | 1562 | `1562 <= 1562` | suppress=false，0 |
| 1241 | 1561 | `1562 > 1561` | suppress=true，1 |

日志在第一组参数下打印 `enterSmallClock=true`，紧接第二组打印 false，同时记录唤醒祖先 `clockScale=0.9`。需要消除的是动画／坐标换算引入的固有高度波动；不能为了保证验收时始终看到小钟而改变官方不等号、抬高 bigMin 或强制所有通知场景使用0。若真实固定大钟内容873加组件368，稳定得到1241，那么现场确实比官方阈值多1像素，官方允许保留大钟1；“存在通知”本身不能证明这个最终结果错误。

### 尺寸回调、INTMAX与项目适配的边界

`C/common/callback/KeyguardStyleHostCallbackWrapper.java:156` 同时上报小钟 `clockHeight/clockTop`、独立歌词钟参数和大钟 bundle。宿主 `S/KeyguardStyleClockCallback.java:107` 先更新小钟高度，111行再解析大钟；`S/KeyguardStyleClockControllerImpl.java:2218` 用 `bigCur==0 || bigMin>bigCur || bigCur<=smallHeight` 判断没有可采用的独立大钟参数，并写入 `Integer.MAX_VALUE-bigClockTop`。2269行重新发送 expectedHeight、请求通知布局更新，回调自身还会在 `S/KeyguardStyleClockCallback.java:126` 请求重排时钟和通知。

这个 sentinel 本身是合法协议结果。`C/base/BaseClockImpl.java:692`、`C/gallery/GalleryClockImpl.java:584` 明确传入空大钟 bundle；默认参数包装器把它保留为 null，宿主便稳定走上述回退。`S/KeyguardStyleClockControllerImpl.java:2987` 的 `getClockHeightAndTop()` 在该路径选择小钟高度和顶部。所以 HyperOS `classic.horizontal` 原版大小钟天然等高时，应允许一直使用该回退；不能用 `big=small+1` 制造不存在的大钟，也不能将“所有模板必须 big>small”作为通用断言。本次问题是行数瞬态令原来可用的大钟突然进入、随后退出该回退。

0.4.32 现场版本与原版的相关差异如下（后续适配见下节）：

- 官方 `C/digital/domain/model/logic/StyleConfigLogic.java:819` 接受 showing/origin 两种行数，437行原样写入状态，没有 max 校正；`C/digital/util/ext/StyleWidgetExKt.java:237` 分别用它们计算小钟和大钟组件占位。项目此前在每次 `onWidgetDataRowChanged` 中同步上报几何，暴露了 `showing=1, origin=0` 的短暂组合；`NativeClockWidgetBridge.occupiedRows(true)` 的 max 是本项目对同步上报的保护，且保留了原始记录。
- 官方 digital 插件在 `C/digital/DigitalClockImpl.java:1633` 接收 expectedHeight，再由 model 调整可变字体及布局；大钟当前高度和最小高度可以不同。项目固定尺寸外厂脸在 `app/java/org/aliveclean/NativeOriginalClockPlugin.java:347` 诚实地将 Cur/Min 都报为该脸实际高度加组件占位，没有实现原版的可变字体压缩。宿主因而在这一固定高度阈值处决定0或1，不能直接照搬原版大钟的数值阈值。
- 项目数字边界、外部动画矩形与固有布局高度属于不同用途。现场唤醒祖先有缩放，局部裁切／测量不应因屏幕坐标往返及取整而从1241变成1242；后续几何修复应验证父级缩放期间上报值稳定，并保留真实的本地高度。日志证明了1像素跨界，不能仅凭该日志宣称真实 SystemUI 问题已经修复或验收完成。

在已核对的这条 ColorOS 插件链路中，没有发现“有任意通知就强制插件0”的设置。AOSP 的 `lockscreen_use_double_line_clock` 由 `S/KeyguardClockRepositoryImpl.java:63` 监听，`S/ClockSizePolicy.java:45` 可使 `KeyguardClockInteractor` 的 selected 输出恒为 SMALL；但 ColorOS VM 在128行直接读取 repository 的动态大小流，绕过该 selected 输出。不能据此承诺修改这个键会让本机三方时钟在所有通知场景强制缩小，本轮也未修改该设置。

## 0.4.33 可用高度适配

`NativeOriginalClockPlugin` 接收 `onClockExpectedHeightChange`，将屏幕底部坐标扣除固定顶部与实际组件占位。`NativeAdaptiveClockFace` 保持厂商视图自然测量，按剩余空间等比缩放内部画面，外包装独立承载场景转场。自然 min／cur 端点不随当前视觉比例变化，避免布局反馈循环。歌词期间缓存预算；转场中更新预算时在重新测量前捕获当前画面并重新定向，隐藏大钟的预算变化不重启其他场景。

最小数字行高调用本机官方 `TextFontHeightUtil.getMinClockHeight`，根据实际数字叶节点是否分行选择横／纵规则，不采用固定缩放系数。厂商数字行高无法可靠识别时保留固定尺寸；已经小于官方最低行高的款式也不继续缩小。本方案等比缩放原画面，日期与装饰会同比变化；它不是官方可变字体逐元素排版的复制实现。组件本身不缩放，材质继续绑定原厂商 face。

验证记录：

- `adaptive-all-clocks-final.log`：补全原版 TimeView 单基线字体度量后，63 款及三个官方外层宿主对照通过。43 款在本机测得可缩放区间，20 款保持固定尺寸；每款检查中间预算、恢复原尺寸、自然端点稳定和歌词/AOD场景。TimeView 的小时、分钟原本是独立控件，取原 paint 副本测0–9，不用双行合并高度冒充数字行高。
- `retarget-tests.log`：三家代表款、2007 帧、414 次布局回调通过，包含动画途中缩小→无限空间→再次缩小的重定向。
- `clipping-effects-v3.log`：官方外层容器中的三款、15 次硬件转场、402 个裁剪检查帧通过，同时验证玻璃材质和调色实际像素。这是独立进程中的官方容器测试，仍不能替代真实 SystemUI 验收。
- 硬件探针发现本机官方颜色控制器新增折叠参数。`NativeClockMaterialBootstrap` 优先调用新签名并使用官方默认 false，旧版保留原签名；只有方法不存在时回退，调用异常不会被吞掉。

## 测试动画底边截断的像素对照

用户观察到独立测试中的数字底部偶尔被直边裁掉。`NativeClockPixelClippingInstrumentation` 固定真实 spring 第 2／5 个可见帧，在黑／白／黑背景下 PixelCopy，用原数字叶节点遮罩检查完全缺失的内部像素，同时确认几何与重复黑底截图稳定。没有为了通过测试修改任何容器裁剪标志。

`pixel-clipping.log` 共 10 组：两种宿主各测自然大钟端点、大→小两帧、大→歌词两帧。旧 direct wrap_content 宿主的四个中间画面均缺像素，底界与 `root` 从大钟收缩到目标内容后的边界吻合；官方安装包宿主的五组全部为 0 缺失、0 不稳定。

| 固定姿态 | 旧 direct 宿主缺失像素 | 官方宿主缺失像素 |
| --- | ---: | ---: |
| 大→小，第2帧 | 49053 | 0 |
| 大→小，第5帧 | 27750 | 0 |
| 大→歌词，第2帧 | 117983 | 0 |
| 大→歌词，第5帧 | 67250 | 0 |

证据保存于忽略目录 `build/clock-size-jitter/pixel-evidence`，包括实际截图、遮罩、缺失像素图、逐行CSV及完整裁剪链。该次探针返回 FAILED 是它正确检出了作为对照的旧 direct 路径，不能写成整轮通过。常规 jitter 测试窗口现改为与官方 `KeyguardClockPluginContainer` 相同的全屏 root，clockContainer 仍按真实内容占高。官方容器的独立硬件对照通过不代表所有实际 SystemUI 场景都已验收。

全屏测试窗口回归 `fullscreen-jitter.log`：三款、1996 帧、414 次布局回调通过；完整 trace 没有 `GEOMETRY_CLIP_RISK`。像素对照中的旧 direct fixture 保留，用于明确复现原测试条件。

## 安装记录

0.4.33-preview（versionCode 49）完整构建与 `verify_release.py` 通过，仅覆盖安装到 `3B1F4UE5MS13HQRR`。设备 base.apk 与本地 APK 的 SHA256 均为 `1c091365c2be088cb20e47c23dd83281189ecba56b50be2c4644130cbbfaa9b6`。已按用户本任务授权重启 SystemUI，旧 PID 32625、新 PID 5351。`acceptance-0.4.33.log` 为 120 秒限时日志，采集已自动结束。未上传 GitHub，未发布。

用户实机先确认“不抽了”和“锁屏歌词页面时钟OK”，随后明确验收“保持现在这样就行……现在这个版本完全好了”。本轮修复验收完成，不实施额外的“有通知就强制略缩”策略。现场通知占用1102后可用底边1562，原大钟及组件底边1561，因此该次保持原尺寸符合协议；空间进一步收紧时使用已经接入的连续适配。

最后复核：官方 `ClockScalePanelMap` 的0.9来自面板展开比例，`OplusPanelKeyguardAnimControllerImpl` 的0.9来自全景息屏动画。该缩放作用于本项目 root，未发现项目重置它；不能将其误写为“任意通知必缩0.9”。此次后续研究只新增忽略目录中的反编译证据，没有改变已验收生产源码。
