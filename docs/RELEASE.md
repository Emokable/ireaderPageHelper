# Release 构建与分发

Release 是构建类型，不代表功能已稳定。当前未实现“未 Root 设备重启后无需电脑恢复触屏动画”，请勿以此宣传分发版。签名不能赋予 App Root、input 组或厂商系统权限。

## GitHub Actions

- Android build：main 推送、PR 或手动运行；安装 API 34 与 Build Tools 34.0.0，分别构建 Debug 和未签名 Release，并各运行现有 117 项检查。下载运行页面的 pageh-apks artifact；只有 Debug APK 可直接安装，未签名 Release 不能直接安装。
- Signed Release draft：仅允许从 main 手动运行，使用 release Environment 的签名配置，构建、测试并创建预发布草稿。不会自动对用户公开发布，也不会覆盖已有版本。
- 创建 release Environment，建议设置审批人和仅允许 main。签名密钥不得用于 PR 构建。
- 首次配置后仍须查看 Actions 实际运行结果；本地构建通过不等于云端已验证。

在 release Environment 或仓库 Actions Secrets 中设置：

| Secret | 内容 |
| --- | --- |
| PAGEH_KEYSTORE_BASE64 | 专用正式签名 keystore 文件的 Base64，不是 APK |
| PAGEH_KEY_ALIAS | 密钥别名 |
| PAGEH_STORE_PASSWORD | keystore 密码 |
| PAGEH_KEY_PASSWORD | 私钥密码 |

不要把密钥或 Base64 写进源码、Issue 或聊天；请自行保管加密备份。正式版本必须沿用同一签名身份，CI 不会自动生成一次性发行密钥。尚未配置这些 Secrets 时，签名工作流会明确失败，不会回退到 debug 签名。

每次新发布前同步更新 Manifest 的 versionName 和递增 versionCode。草稿标签使用 v + versionName；已有同名 Release 时失败，不强制覆盖。确认已知问题、实机结果、APK 签名和源码版本后再手动发布草稿。

## 本地

准备 JDK 21、Android API 34 和 Build Tools 34.0.0。JAVA_HOME 指向 JDK，ANDROID_HOME 指向 SDK；也可显式传 -Jdk 和 -AndroidSdk。D8 默认由 prepare-d8.ps1 下载 Google 官方 R8 8.13.17 并校验固定 SHA-256，不依赖 JADX。首次构建需联网，工具缓存位于被 Git 排除的 tools/。

~~~powershell
./build.ps1 -BuildType Debug
./build.ps1 -BuildType Release -Unsigned
~~~

生成正式签名包前，在本机安全配置 PAGEH_KEYSTORE（文件路径）、PAGEH_KEY_ALIAS、PAGEH_STORE_PASSWORD、PAGEH_KEY_PASSWORD 环境变量，然后执行：

~~~powershell
./build.ps1 -BuildType Release
~~~

输出 dist/pageh-helper-版本-release.apk；构建检查 android:debuggable=false、ZIP 对齐与 APK 签名。未签名构建输出 -release-unsigned.apk。密码使用 apksigner 的 env: 参数读取，不放进命令行实参。

Release 与当前设备已安装的 Debug 签名可能不同，不能直接覆盖安装；卸载会丢失应用设置，切勿在没有备份和确认的情况下卸载。后续升级须保持签名一致并递增 versionCode。

## 当前功能边界

没有 Root 的用户不能通过签名 Release APK 获得读取 /dev/input/event* 的权限。当前可用范围仍是兼容固件上的无障碍按键动效，以及已有高权限桥接运行时的实验性触屏动效。免电脑、免 Root 的触屏方案尚待另行研究；不包含在本次构建改造中。

参考：[Android apksigner](https://developer.android.com/tools/apksigner)、[GitHub 工作流权限](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)、[gh release create](https://cli.github.com/manual/gh_release_create)。
