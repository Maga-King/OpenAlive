# 地球冷启动黑屏

主力机 3B1F4UE5MS13HQRR，1440 × 3168，0.4.27-preview。用户报告重启后地球黑屏，首次进入 AOD 后可恢复；月球和火星正常。发布工作暂停，先核对真实服务。

覆盖安装后恢复地球服务，捕获 `UnsupportedOperationException: Wallpapers currently only support sizing from layout`。调用链为原版 `EarthMrePlayer.setupSurfaceView` → `UiHelper.SurfaceHolderHandler` → `XiaomiPlayer.DeferredHolder.setFixedSize` → `WallpaperService.Engine`。地球原包在显示高度超过 2400 px 时请求固定尺寸，原版 SuperWallpaper 宿主另有私有 `setFixedSizeAllowed` 调用。OpenAlive 没有开放这个权限，故直接转发请求会使构造失败；这段代码早于本轮桌面滑条修改。

0.4.28 在系统拒绝固定尺寸时使用 `setSizeFromLayout`，继续通过实际 surfaceChanged 尺寸设置 viewport 与投影。支持固定尺寸的普通预览保持原有行为。构造失败会解除临时 SurfaceHolder 回调。地球状态仅在手动 dumpsys 时读取原生帧计数、swapchain、场景与相机参数，不增加后台监测或逐帧日志。

`python tools/check_xiaomi_startup.py --serial 3B1F4UE5MS13HQRR` 使用隔离 APK 和原始地球引擎，在高分辨率设备上模拟系统壁纸拒绝固定尺寸，读取真实 GPU 输出。修复前复现相同异常，修复后锁屏、桌面、AOD 能出图。测试需保持前台，否则 ColorOS 会限制无界面的测试进程。测试只卸载自身包，不更换主应用壁纸。

主力机覆盖安装 0.4.28 后，系统自动恢复地球服务，未重新应用：`nativeReady=true swapChain=true frames=814 state=HOME`，相机参数有限，用户反馈恢复正常。高分辨率独立测试同时通过冷启动锁屏与桌面两种入口、AOD 往返和有效像素断言（`XIAOMI_STARTUP_OK`）。整机重启尚未再次验证，不能将服务冷启动测试等同于完整开机测试。
