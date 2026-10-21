# 雪山 Surface 交接崩溃（2026-10-21）

设备为一加 `3B1F4UE5MS13HQRR`。0.4.38-preview 在系统壁纸选择器应用雪山后，
`org.aliveclean:xiaomi_snowmountain` 的 `FEngine::loop` 多次 SIGSEGV，
最后一次旧版现场记录为 19:09:34。不是 Java 异常，也不是用户操作错误。

## 已核实的根因

- 栈为 `eglDestroySurfaceImpl -> egl_surface_t -> BBQSurface -> BLASTBufferQueue
  -> JavaBBinderExt::~JavaBBinderExt`。从本机 libandroid_runtime.so 核实，
  最后一帧调用 JVM GetEnv 后，没有检查未附加线程的空 JNIEnv 就解引用。
- 原版雪山 `nSetSurface` 先 release 旧 ANativeWindow，再绑定新窗口。
  `GraphicSystem::updateSwapChain` 将旧 swapchain 销毁排到 Filament 队列，
  没有 flush/fence；因此最后一个窗口引用可能在原生渲染线程释放。
- `nSetSurface(null)` 不销毁旧 swapchain，不能作为 detach 修复。
- 原包导出 `MI::GEngine` 和 `filament::Engine::flushAndWait`。GEngine 是真实
  Filament Engine 指针；无需硬编码对象布局或地址。
- `nDestroy` 最终销毁 Filament Engine 并 join 渲染线程，返回后可释放窗口引用。

## 修复与上轮复查

雪山适配器在 SysRes/nSetSurface 前取得额外 ANativeWindow 引用。新窗口绑定后
等待原生销毁队列完成，再由调用 JNI 的 Java 主线程释放旧引用。当前窗口持续
保留到下一次绑定或 nDestroy 返回。失败路径保留引用，不猜延时、不提前释放。
桥接类由雪山自己的 class loader 加载，RTLD_NOLOAD 仅查现有原生库，不另建引擎。
其他小米 family 的原生类加载方式不变。

撤回上轮 refresh 中每次恢复重发 Force* 的试改；它可能覆盖正常 AOD/解锁动画，
也没有证据能修复地球黑屏。雪山服务最终销毁后退出独立进程与官方一致，但不能
当作 Surface 交接崩溃的修复。

## 验证记录

- Rust 单测和 arm64 构建、完整发布构建检查、签名和资源对齐检查通过。
- `tools/check_xiaomi_surface.py --serial 3B1F4UE5MS13HQRR`：独立包使用真实
  SurfaceView，先销毁旧窗口再重绑，12 次 PixelCopy 非黑检查及最终关闭通过。
  旧版在这个独立窗口测试中也通过，因此它只证明回归与桥接可用，不能单独
  证明系统壁纸路径的崩溃已消除。
- 0.4.39-preview / versionCode 55 于 19:13:02 覆盖安装；设备与本地 APK
  SHA-256 均为 `7624e3f8d6f9c528f3696444874905336808d1f6741198974b9b10958362e067`。
- 19:13:59 系统选择器再次应用真实雪山服务；采集到的安装后日志无新原生崩溃、
  桥接错误或服务崩溃重连，用户反馈“似乎不崩溃了”。仍不应宣称所有机型已验证。
- 未推送 GitHub、未发布 Release；未在此次验证中操作其他设备。

## 独立待查

地球 preview/home 的 holder 回调来源需要另行验证，不能以雪山修复替代。
用户随后指出小米超级壁纸 AOD 时钟尺寸、字体与官方不同，应单独比对密度、
官方布局及字体，保持已有动画、锁屏和 AOD 状态协议。
