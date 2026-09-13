# 掌阅水波纹助手

为掌阅 E-Ink 阅读设备尝试接入系统 PAGE_H 水波纹。

版本 **0.3.1**，实验性调试版。实测设备为 Ocean 5 Pro / Android 14，不保证其他固件或设备兼容。

### 电脑启动触屏桥接(触屏翻页功能)

  前提：掌阅设备已开启adb。

  首次准备：

  1. 阅读器安装助手 APK，开启 USB 调试和助手的无障碍服务。
  2. 电脑准备 ADB 工具，以及同版本桥接文件包。
  3. 数据线连接电脑，在阅读器上允许 USB 调试授权。

  在文件包目录打开 PowerShell，执行：

  adb devices
  adb root
  adb wait-for-device
  adb shell id

  最后一条出现 uid=0(root) 才能继续。然后执行：

  powershell -ExecutionPolicy Bypass -File .\start-bridge.ps1 -Adb adb

  回到助手，确认显示“触屏监听已连接”，绑定正文界面并开启“原触摸翻页动效”。

## API 来源和许可

水波纹 API 来自 **掌阅（iReader）系统**：`android.eink.EPDCDevice`，包括 `next-effect-type`、`epdc-force-next-post-mode`。底层水波纹并非本项目原创。

© 2026 nuku。本项目原创代码与资源采用 **GNU GPL v3.0（GPL-3.0-only）**，完整条款见 [LICENSE](LICENSE)。软件不附带担保。

本项目是第三方独立工具，非掌阅官方产品。掌阅系统实现、API 及第三方商标不属于本项目授权范围。

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
