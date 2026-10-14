# 主页激活状态与小米桌面设置

主页工具栏的小米按钮使用 `https://www.mi.com/us/` 官方页面的橙色新标识。原始 SVG、静态 MI 状态和来源记录保存于 `app/assets/branding/`；`ic_xiaomi_official.xml` 保留原路径与颜色，以 30 dp 矢量显示，不随深色模式染色。按钮打开「桌面设置」。

两项桌面调节均为 −100 到 +100，初始 0 保持原服务输入和延时：

- 幅度：围绕原偏移中点 50，按 `1 + 设置值/100` 缩放，再限制在原生 `Offset_0..100` 的有效范围。−100 静止，+100 为两倍输入增益；原生范围边界处会饱和，不承诺模型能超出原厂镜头边界。
- 跟手度：每个有效偏移样本的交付延时为 `100 − 设置值` 毫秒，范围 0–200 ms。0 保留原版 100 ms。保留滑动中间样本，不把它们合并成最后一帧。

100 ms 的原版证据是 `com.miui.miwallpaper.basesuperwallpaper.SuperWallpaper.SuperWallpaperEngine.onOffsetsChanged()`：先将 `xOffset` 转为 `((int)(xOffset * 1000f))/10f`，随后 `filamentResume()`，再 `mHandler.postDelayed(...sendFilamentMessage("Offset_" + offset)..., 100L)`。原反编译文件位于旧工作区 `research/super-wallpaper-20260928/earth-src/sources/com/miui/miwallpaper/basesuperwallpaper/SuperWallpaper.java`，方法从约 1194 行开始。旧 session `01a0be1d-6045-7e01-b51d-4b92f37776a0` 的 rollout 49429 保存了实际文件读取输出，本轮只按关键词读取该片段并复核；副本位于本地 `build/home-activation/original-offset-method.txt`。

设置跨 Xiaomi 服务进程广播，新进程订阅时读取当前值；改变设置或表面所有者后，旧参数的待交付样本作废。界面每 40 ms 最多安排一次后台保存，离开页面和结束拖动立即提交最后的值。保存不在界面线程等待磁盘写入。恢复按钮同时还原两项到 0。

三个预览器上方显示激活状态行：

- Root 检测运行后台 `su -c 'id -u'`，仅退出码 0 且输出 UID 0 才是已授权；15 秒超时会结束子进程，分别呈现不可用、未授权或授权超时。
- LSPosed 检测本模块是否实际进入 SystemUI。`ModuleActivationHook` 在 SystemUI 注册查询接收器，将该进程的 Binder 回执交给现有 `NativeClockStatusProvider`。Provider 校验调用 UID 对应 SystemUI；Binder 死亡后清除状态，不持久化「已激活」。既有且仍存活的系统时钟模块回执也可证明模块已加载。
- 两项成立：绿点、`Lsposed Root 已授权`。否则红点，分别说明哪项未授权/未激活。查询中显示中性色。在主页约每 2 秒刷新模块存活状态，离开后停止刷新；点击状态行可显式重新检测 Root。

模块状态查询在独立串行后台执行，前一查询返回后才安排下一次，慢查询不会在队列中堆积。Root 探测由应用进程合并，同一时刻只运行一个有界请求，探测线程不持有 Activity。主页离开时使旧查询代次失效，并移除、清空 Root 界面监听，迟到结果不更新隐藏页面；不会因授权弹窗导致 Activity 暂停而取消用户正在确认的 su 请求。恢复或重建页面时加入尚未完成的探测，最近 15 秒的结果可复用，避免拒绝弹窗关闭后立即再次弹出；主动点击状态行绕过结果缓存。无监听时探测仍自行完成或在 15 秒超时后销毁子进程。销毁页面时关闭其模块查询线程池。

`NativeClockStatusProvider.dump()` 仅在锁内取得 Binder/API 状态快照，随后在锁外写输出并请求 SystemUI 诊断，不让远程 Binder 阻塞普通状态查询。

该提示检查的是 OpenAlive 的 SystemUI 模块，不以安装 LSPosed 管理器或仅安装本 APK 当作激活。它不代表所有系统接口已适配，也不取代原有时钟 API 版本检查。

本地验证：新资源 aapt2 编译/链接通过；主页、检测器、Hook、Provider 和小米相关 Java 定向编译通过；`check_xiaomi_desktop.py` 通过 20,301 个输入组合；`check_clock_scope.py` 通过 41 项。整包和实机由本轮主任务统一执行，本文件此处不宣称设备验证已完成。
