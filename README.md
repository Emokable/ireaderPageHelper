# 掌阅水波纹助手

为掌阅 E-Ink 阅读设备尝试接入系统 PAGE_H 水波纹。

版本 **0.3.1**，实验性调试版。实测设备为 Ocean 5 Pro / Android 14，不保证其他固件或设备兼容。

## 当前已知问题：触屏桥接退出（2026-09-13）

**0.3.1 尚未解决桥接退出后的自动启动，也未保证断开电脑后持续可用。**

### 现象与已确认事实

- 触屏动画一度正常，退出重进正文后没有动画；按键动画仍可工作。
- 故障时正文窗口已重新识别，但高权限桥接进程和本地 socket 已不存在，App 持续记录触屏连接重试。
- 手动重新启动桥接后，App 自动重连并恢复正文触屏授权；用户反馈其他阅读软件也一起恢复，因为它们共用该桥接。
- 这次故障不能证明是“退出阅读器”或“重启 App”直接结束了桥接，也不能解释所有触屏漏动画问题。
### 自动重连不等于自动启动

| 场景 | 当前能力 |
| --- | --- |
| 连接断开，桥接仍存活 | 触屏客户端每 2 秒尝试重连；桥接状态约每 4 秒检测一次，实际恢复时间受调度影响 |
| 重启 App，桥接仍存活 | 无障碍服务重新连接、设置与正文绑定有效后，可自动重连桥接 |
| 桥接进程已退出 | App 只能继续重试连接，不能自行重新启动高权限进程 |
| 设备重启 | 需在设备具备 ADB Root 后重新从电脑启动桥接；未实现开机启动 |
| 拔线或切换 USB 模式 | 持续存活尚未保证，可能受 adbd 生命周期影响 |

### 权限与替代方案的边界

- **水波纹 API 调用**：本设备已实测普通 App 可调用；桥接退出后按键仍可能走普通 App 直连路径。这不保证其他固件也允许。
- **原始触屏监听**：当前实现被动读取 `/dev/input/event*`，不拦截或重新注入触摸。实测触屏节点属于 `root:input`、权限为 `0660`，普通 App 无权直接读取；当前后端还明确要求以 Root 身份启动。
- **不强制依赖 `su` 程序**：当前使用 ADB Root 启动桥接。尚未发现普通 App 可用的 `su` 启动入口，因此不能仅靠 App 重启恢复已退出的 Root 桥接。
- 普通无障碍事件不等价于全局原触摸的透明旁听；只在页面变化后补发，也不能保证赶上新页首次提交。

待实现并验证：让桥接独立于 ADB 清理组存活、补充退出原因记录、增加有次数限制的异常重启，并测试拔线、USB 切换、App 重启和锁屏恢复。上述能力**尚未实现**；不包含修改系统分区或开机启动的承诺。

此外，同 Activity 的自绘弹窗和评论控件仍可能识别不准；没有面板完成回调，不能承诺每页零遗漏。多看按键水波纹适配仍暂停，不作为已解决功能推荐。

## API 来源和许可

水波纹 API 来自 **掌阅（iReader）系统**：`android.eink.EPDCDevice`，包括 `next-effect-type`、`epdc-force-next-post-mode`。底层水波纹并非本项目原创。

© 2026 nuku。本项目原创代码与资源采用 **GNU GPL v3.0（GPL-3.0-only）**，完整条款见 [LICENSE](LICENSE)。软件不附带担保。

本项目是第三方独立工具，非掌阅官方产品。掌阅系统实现、API 及第三方商标不属于本项目授权范围。仓库不分发厂商固件、反编译的系统实现、阅读器 APK、SDK、签名密钥、设备日志或书页截图。

源码地址：https://github.com/Emokable/ireaderPageHelper

## 构建

GitHub 自动构建、正式签名及 Release 草稿流程见 [Release 构建与分发](docs/RELEASE.md)。Release 构建不会解决未 Root 设备的触屏权限问题；重启后免电脑恢复触屏尚未实现。

Windows PowerShell + 原生 Java / Android UI，无第三方 UI 框架。自行准备：

- JDK，开发环境为 JDK 21，编译目标 Java 8。
- Android API 34 的 `android.jar`：放入 `tools/platform/android-34/android.jar`。
- Android Build Tools：放入 `tools/build/android-14/`，包含 aapt2、zipalign、lib/apksigner.jar。
- 兼容的 D8/R8 jar：需含 `com.android.tools.r8.D8`，开发环境使用 D8 8.13.17。

```powershell
./build.ps1 -Jdk 'C:\path\to\jdk' -D8Classpath 'C:\path\to\r8.jar'
adb install -r ./dist/pageh-helper-0.3.1.apk
./start-bridge.ps1 -Adb 'C:\path\to\adb.exe'
```

可通过 `JAVA_HOME` 和 `R8_JAR` 环境变量提供构建工具路径；桥接脚本默认使用 PATH 中的 `adb`，也可传 `-Adb` 指定路径。

构建会签署调试 APK、验证签名，运行 105 项方向/窗口/输入分类/触屏授权测试和 12 项被动输入源码检查，共 117 项。源码检查不是运行时或面板测试。调试密钥在 `build/debug.keystore`，不要公开或用于正式发行；不同签名的 APK 不能直接覆盖安装。
