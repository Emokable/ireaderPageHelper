# 掌阅 E-Ink PAGE_H 水波纹 API 调用说明

> 本文档描述掌阅 Android E-Ink 设备中的私有接口
> `android.eink.EPDCDevice`。这些接口不是 Android 标准 API，也没有公开的
> ABI/兼容性承诺。内容基于 Ocean 5 Pro（Android 14）固件反编译与 ADB
> 实机验证，并参考了旧款掌阅设备的调用方式。

## 1. 适用范围

- 已验证设备：掌阅 Ocean 5 Pro
- 已验证系统：Android 14
- 已验证功能：`PAGE_H` 水波纹翻页、方向和三档速度
- 可能兼容：包含 `android.eink.EPDCDevice` 的其他掌阅设备
- 不保证兼容：非掌阅设备、不同代际固件或被厂商修改过的系统

调用前应以“能否加载类和方法”为最终判断，不要只依赖
`ro.product.ireader.platform`。Ocean 5 Pro 上该属性可能为空，但接口仍然存在。

## 2. 核心类与方法

固件中的类名：

```text
android.eink.EPDCDevice
```

Ocean 5 Pro 固件中与本功能有关的方法：

```java
public static int nativePostCommand(String command);

public static native int nativePostCommand(
        String command,
        String[] arguments
);

public static void setForceNextPostMode(int mode);
```

对应 JNI 签名：

| 方法 | JNI 签名 | 返回值 |
| --- | --- | --- |
| `nativePostCommand(String)` | `(Ljava/lang/String;)I` | `int` |
| `nativePostCommand(String, String[])` | `(Ljava/lang/String;[Ljava/lang/String;)I` | `int` |
| `setForceNextPostMode(int)` | `(I)V` | `void` |

建议 Java/Kotlin 代码调用单参数包装方法。JNI 代码可以优先调用双参数原生方法，
第二个参数传 `null`，失败时再回退到单参数方法。

## 3. PAGE_H 模式常量

```text
PAGE_H                 = 0x63
FORCE_NEXT_POST        = 0x01000000
FORCE_NEXT_PAGE_H      = 0x01000063
```

设置下一次画面提交为 PAGE_H：

```java
EPDCDevice.setForceNextPostMode(0x01000063);
```

这个设置只作用于“下一次”画面提交，不会自动触发重绘。因此必须在页面内容绘制或
提交之前调用，随后由阅读器完成一次正常的整页更新。

## 4. 方向和速度命令

PAGE_H 的方向与速度通过以下命令设置：

```text
next-effect-type <effect>
```

`effect` 的计算方式：

```text
effect = direction | speed
```

### 4.1 速度位

| 速度 | 数值 | 十六进制 |
| --- | ---: | ---: |
| 慢速 | 128 | `0x80` |
| 中速 | 64 | `0x40` |
| 快速 | 0 | `0x00` |

### 4.2 方向值

在屏幕旋转值为 0 时：

| 翻页动作 | 方向值 |
| --- | ---: |
| 下一页 | 1 |
| 上一页 | 2 |

完整旋转映射：

```text
NEXT = [1, 4, 2, 3]
PREV = [2, 3, 1, 4]
```

数组下标对应 Android Display rotation：

| Rotation | 下一页方向 | 上一页方向 |
| ---: | ---: | ---: |
| 0 | 1 | 2 |
| 1 | 4 | 3 |
| 2 | 2 | 1 |
| 3 | 3 | 4 |

例如，竖屏 rotation 0、中速、下一页：

```text
effect = 1 | 64 = 65
command = "next-effect-type 65"
```

竖屏 rotation 0、中速、上一页：

```text
effect = 2 | 64 = 66
command = "next-effect-type 66"
```

### 4.3 反转方向

反转时必须显式取反：

```java
boolean physicalForward = reverse ? !forward : forward;
```

Lua 不要写成下面这种伪三元表达式：

```lua
-- 错误：reverse=true、forward=true 时结果仍然是 true。
local physical_forward = reverse and not forward or forward
```

正确写法：

```lua
local physical_forward = forward
if reverse then
    physical_forward = not physical_forward
end
```

## 5. 正确调用顺序

每次真正翻页时，应在新页面提交到屏幕之前执行：

1. 计算上一页或下一页对应的方向值。
2. 合并速度位，得到 `effect`。
3. 发送 `next-effect-type <effect>`。
4. 调用 `setForceNextPostMode(0x01000063)`。
5. 绘制并提交新页面。

伪代码：

```text
direction = forward ? NEXT[rotation] : PREV[rotation]
effect = direction | speed

nativePostCommand("next-effect-type " + effect)
setForceNextPostMode(0x01000063)

drawAndPostNewPage()
```

不应在菜单弹窗、状态栏刷新或同页重绘时设置 PAGE_H，否则这些非翻页更新也可能
消耗“下一次提交”标记。

## 6. Kotlin 反射示例

由于这是系统私有类，普通工程通常没有可直接编译的 SDK 声明，建议使用反射。

```kotlin
object IReaderPageH {
    private const val FORCE_NEXT_PAGE_H = 0x01000063
    private val next = intArrayOf(1, 4, 2, 3)
    private val prev = intArrayOf(2, 3, 1, 4)
    private val speedBits = intArrayOf(128, 64, 0) // 慢、中、快

    private val epdcClass by lazy {
        Class.forName("android.eink.EPDCDevice")
    }

    private val postCommand by lazy {
        epdcClass.getMethod("nativePostCommand", String::class.java)
    }

    private val setForceNextPostMode by lazy {
        epdcClass.getMethod(
            "setForceNextPostMode",
            Int::class.javaPrimitiveType
        )
    }

    fun prepare(
        forward: Boolean,
        rotation: Int,
        speedIndex: Int,
        reverse: Boolean = false
    ): Boolean {
        return runCatching {
            val physicalForward = if (reverse) !forward else forward
            val r = rotation and 3
            val direction = if (physicalForward) next[r] else prev[r]
            val speed = speedBits[speedIndex.coerceIn(0, 2)]
            val effect = direction or speed

            postCommand.invoke(null, "next-effect-type $effect")
            setForceNextPostMode.invoke(null, FORCE_NEXT_PAGE_H)
        }.isSuccess
    }
}
```

调用示例：

```kotlin
val rotation = view.display?.rotation ?: Surface.ROTATION_0

// 必须在新页面绘制/提交之前调用。
IReaderPageH.prepare(
    forward = true,
    rotation = rotation,
    speedIndex = 1
)

view.invalidate()
```

反射得到的 `Class` 和 `Method` 应缓存，不要每次翻页重新查找，以减少延迟和
内存分配。

## 7. Java 反射示例

```java
public final class IReaderPageH {
    private static final int FORCE_NEXT_PAGE_H = 0x01000063;
    private static final int[] NEXT = {1, 4, 2, 3};
    private static final int[] PREV = {2, 3, 1, 4};
    private static final int[] SPEED = {128, 64, 0};

    private static Method postCommand;
    private static Method setForceNextPostMode;

    private static void ensureMethods() throws Exception {
        if (postCommand != null && setForceNextPostMode != null) return;

        Class<?> cls = Class.forName("android.eink.EPDCDevice");
        postCommand = cls.getMethod("nativePostCommand", String.class);
        setForceNextPostMode =
                cls.getMethod("setForceNextPostMode", int.class);
    }

    public static boolean prepare(
            boolean forward,
            int rotation,
            int speedIndex,
            boolean reverse
    ) {
        try {
            ensureMethods();
            boolean physicalForward = reverse ? !forward : forward;
            int r = rotation & 3;
            int direction = physicalForward ? NEXT[r] : PREV[r];
            int speed = SPEED[Math.max(0, Math.min(speedIndex, 2))];
            int effect = direction | speed;

            postCommand.invoke(null, "next-effect-type " + effect);
            setForceNextPostMode.invoke(null, FORCE_NEXT_PAGE_H);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
```

## 8. KOReader / LuaJIT JNI 注意事项

### 8.1 `JNIEnv` 不能跨线程复用

`JNIEnv*` 是线程局部对象。KOReader 的 Lua 事件循环与 Android Java UI
线程不是同一个线程，不能缓存或复用：

```lua
-- 错误：该 env 可能属于其他线程。
local env = android.app.activity.env
```

应使用 KOReader Android 层提供的 JavaVM 上下文，在当前线程附加后调用：

```lua
local android = require("android")

local result = { android.jni:context(
    android.app.activity.vm,
    function(jni)
        return pcall(function()
            local env = jni.env
            -- 在这里执行 FindClass、GetStaticMethodID 和 CallStatic*MethodA。
        end)
    end
) }

if not result[1] then
    error(result[2])
end
```

方法 ID 和 `NewGlobalRef` 得到的类引用可以缓存，但 `JNIEnv*` 不可以缓存。

### 8.2 精确使用方法签名

不要依次猜测多个签名。每次错误的 `GetStaticMethodID` 都会产生
`NoSuchMethodError`，必须先 `ExceptionClear` 才能继续 JNI 操作。

Ocean 5 Pro 应直接使用：

```text
nativePostCommand(String)       -> (Ljava/lang/String;)I
setForceNextPostMode(int)       -> (I)V
```

### 8.3 Java 异常处理

每次 JNI 方法调用后检查：

```c
ExceptionCheck
ExceptionOccurred
ExceptionClear
```

不要在来源不正确或已失效的 `JNIEnv*` 上调用 `ExceptionDescribe`，否则可能
直接导致 native SIGSEGV。

## 9. 接口检测建议

推荐检测顺序：

1. 确认运行于 Android。
2. 尝试加载 `android/eink/EPDCDevice`。
3. 精确查找 `nativePostCommand(String)`。
4. 精确查找 `setForceNextPostMode(int)`。
5. 只有以上步骤全部成功，才显示或启用 PAGE_H。

以下信息只能作为辅助提示：

```text
ro.product.brand
ro.product.manufacturer
ro.product.ireader.platform
/system/lib/libeinkandroid_runtime.so
/system/lib64/libeinkandroid_runtime.so
```

不要把 `ro.product.ireader.platform` 非空作为必要条件。

## 10. 已知不兼容方案

旧设备资料中可能出现直接调用：

```text
service call SurfaceFlinger 2000 ...
```

Ocean 5 Pro Android 14 的 SurfaceFlinger 不识别 transaction 2000，实机会返回类似：

```text
SurfaceFlinger did not recognize request code: 2000
Operation not permitted
```

因此不要在该设备上使用 transaction 2000 作为 JNI 失败后的兜底。它不仅无效，
每次翻页额外启动 `service` 子进程还会增加延迟和内存开销。

## 11. 常见问题

### 接口存在但提示“未检测到”

不要仅检查旧版属性或 `libepdcdevice.so`。应直接检测
`android.eink.EPDCDevice` 及其方法。

### 调用位置抛出异常或直接崩溃

优先检查：

- 是否跨线程复用了 `JNIEnv*`
- JNI 签名是否准确
- 上一个 JNI 异常是否已经清除
- 类引用是否为有效的 global reference
- 是否在 JavaVM 分离线程后继续使用旧 `JNIEnv*`

### 左右翻页动画方向相同

检查：

- 下一页和上一页是否分别使用 NEXT/PREV 表
- 是否错误使用 Lua 的 `a and b or c` 模拟三元表达式
- 当前屏幕 rotation 是否正确
- 阅读器是否把左右区域都配置成了“下一页”

### 命令成功但看不到动画

检查调用时机。PAGE_H 是“下一次提交”模式，必须在新页面提交之前设置；设置完成后
还要有一次实际的整页绘制和提交。若先完成刷新再调用，效果会落到之后的其他刷新上。

## 12. 本仓库中的实现

当前 Android 助手的实现位于：

- `app/src/main/java/dev/pageh/helper/Commands.java`：反射调用双参数 `nativePostCommand(String, String[])`，检查命令返回码。
- `Direction.java`：旋转、前后方向和速度映射。
- `Bridge.java` / `RootDaemon.java`：受限本地 IPC 和已有 ADB Root 下的调用入口。
- `PageService.java` / `TouchFeed.java`：原按键观察和原触屏观察。

上面的 Java/Kotlin 示例仅解释接口用法，并非完整错误处理实现；没有抛异常不代表命令返回成功，更不代表面板动画完整。实际接入应检查返回码，并用实屏验证调用时机。KOReader/JNI 部分为接口研究参考，本仓库不包含 KOReader 插件。

当前功能、权限要求和已知限制以 [README](README.md) 为准。
