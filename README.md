# VRviewer

Android VR 视频播放器。首页只保留两个按钮：

- **打开本地文件**：通过系统选择器打开视频。
- **打开 URL 链接**：输入 HTTP / HTTPS 视频直链。

播放控制、投影与片源设置全部放在播放器内，首页不展示推荐、媒体库、最近列表或额外设置入口。

## 当前实现

| 能力 | 范围 |
| --- | --- |
| 片源 | 本地 content URI、HTTP/HTTPS 媒体直链；已接入 Media3 progressive、HLS、DASH 模块 |
| 投影 | 平面、180° 等距柱状、360° 等距柱状 |
| 片源排布 | 单目、左右 SBS、上下 TB；立体片源可选择左/上或右/下画面 |
| 观看窗口 | 固定单个视口与一套控制栏；方向遵循系统，不由播放器强制切换 |
| 操作 | 拖动视角、双指调节视野、回正、可选姿态控制 |
| 播放 | 暂停、进度、±10 秒、倍速、单片循环 |
| 记忆 | 本地文件保留进度/模式，最多 50 条内部记录但不显示列表；URL 只在当前会话内保存进度和模式 |
| 系统行为 | 音频焦点、耳机断开处理，进入后台释放播放器与传感器 |
| 隐私 | 无账号、广告、遥测或上传；URL 不写入永久历史，不申请全盘/相机/麦克风权限，关闭备份 |

URL 指直接媒体资源，例如 MP4、WebM、m3u8、mpd。不能把视频网站的 HTML 网页、登录页或分享页当作视频直链；当前不提供网页抓取、Cookie 登录或自定义鉴权头。网络请求仅在用户打开链接后发生。

允许用户指定的 HTTP 链接以兼容局域网媒体；HTTP 本身不加密。HTTPS 使用系统证书校验。HLS/DASH 的格式通常由链接后缀推断；无后缀或特殊鉴权的地址仍需针对具体来源验证。

0.1.1 已按用户决定删除“显示方式（单眼窗口/双眼分屏）”，统一为一个观看窗口，移除方向请求与销毁时恢复方向的逻辑。SBS/TB 表示片源排布，仍可选择其中一眼的画面。当前只处理 SDR，检测到 HDR 会暂停提示；鱼眼、立方体、任意网格和所有设备的 4K/8K 解码均不作兼容承诺。

## 使用

1. 点击“打开本地文件”，或点击“打开 URL 链接”输入媒体地址。
2. 进入播放器后，在“设置”中选择投影与片源排布。默认平面、单目；SBS 显示左图、TB 显示上图，可切换“使用右眼画面”。
3. 180°/360°可拖动视角、双指调整 35°–110°垂直视野，也可选择姿态控制与回正。
4. 单击画面显示/隐藏控制，双击播放/暂停；空格/确认键播放暂停，左右键快退/快进。
5. 文件授权或网络链接失效时返回首页重新打开。网络 URL 在当前会话中保留旋转时的播放状态，进程结束后不从永久记录恢复。

## 构建

基线：JDK 17、Gradle 8.13、Android SDK 36、Build Tools 36.0.0，最低 Android 8.0 / API 26，target SDK 36。依赖固定在版本目录，Compose BOM 2026.03.00 与此基线匹配。

~~~bash
./gradlew --no-daemon --console=plain --stacktrace :app:assembleDebug :app:assembleRelease
~~~

[Android APK 工作流](https://github.com/Grinch27/VRviewer/actions/workflows/build-apk.yml) 参考 [awara 的原生 APK 构建](https://github.com/Grinch27/awara/blob/main/.github/workflows/build-apk.yml)，仅保留环境准备 → Debug/Release 打包 → 上传 APK。使用 Non-container、共享动态 runner-image 和已核验的 main 分支 Actions。

- 可安装调试 APK：app/build/outputs/apk/debug/app-debug.apk。
- 未签名 Release APK：app/build/outputs/apk/release/app-release-unsigned.apk，产物名 VRviewer-release-unsigned-<源码提交>；需自行签名后安装。
- Actions 分别上传调试 APK、未签名 Release APK 与各自 SHA256SUMS；不运行单元测试、instrumentation、模拟器或独立 lint 步骤，不上传测试报告，不发 GitHub Release 或应用商店。
- 调试签名由构建环境生成。本次 APK 与上一轮 0.1.0 预览证书已比对，签名不同，不能直接覆盖该旧包；卸载会清空应用内进度与设置。需要保留记录时先确认迁移方式；本轮未配置稳定签名。更新签名要求见 [Android 官方说明](https://developer.android.com/google/play/app-updates)。

本轮仅构建工作流已完成修改，待 Actions 实际打包确认；新的下载链接和校验值将在构建完成后记录。应用功能与版本保持 0.1.1。

在 Actions 运行页面的 **Artifacts** 中下载 `VRviewer-debug-<源码提交>`，解压后安装 `VRviewer-debug.apk`。未签名 Release 单独位于 `VRviewer-release-unsigned-<源码提交>`，每份压缩包均含 `SHA256SUMS`。产物保留 14 天，过期后可手动运行工作流重建。

Gradle 编译与打包的内置检查按插件默认行为执行。当前没有测试 Job、测试 APK 构建、模拟器/KVM/音频运行库安装或自动截图。测试源码保留为手动诊断材料，只有用户另行要求时才执行。

## 本轮构建记录

待仅构建工作流的实际运行结果；构建成功仅说明 APK 已生成，不代表执行或通过了播放测试。

## 历史验证记录（当前工作流不执行）

以下为此前已完成的历史结果，不属于本轮仅构建流程。已核验 0.1.1 源码 `28f3298be697e5da3e9f38378caf1169fdcbf0ee` 的 [Actions #40060583916](https://github.com/Grinch27/VRviewer/actions/runs/40060583916)：三个 Job 均成功。已下载 XML 核对测试数、核对安装包与校验清单，并检查 APK DEX 不再包含旧选项文字和项目原显示模式类型。

| 验证 | 已确认结果 |
| --- | --- |
| 删除范围 | 设置选项、显示模式类型/字段、分屏绘制、重复控制栏、方向请求与销毁恢复均已删除；Manifest 不锁方向 |
| JVM | 13 项通过，0 失败、0 错误、0 跳过；投影/源眼/比例/续播 10 项，URL 3 项 |
| Android lint | Debug、Release 各 0 错误、15 警告，主要为版本更新提示与 KTX 建议 |
| APK | Debug、启用 R8 的未签名 Release、instrumentation APK 均构建成功 |
| Android 35 / x86_64 | 8 项通过，0 失败、0 错误、0 跳过 |
| 播放与方向回归 | 实际播放器开启设置并连续重建 Activity 两次；无旧显示选项，不覆盖宿主请求方向，投影与片源排布恢复；返回后首页仍恰好两个入口 |
| 历史兼容 | 旧 display=DUAL 被忽略；保留进度、投影、SBS/TB 和源眼；保存后移除旧 display/swap 字段 |
| 实际视频链 | 生成的本地/回环 HTTP H.264 SDR 测试卡经 MediaCodec、OES、PixelCopy，单视口源眼选择、平面/180°/360°、seek 与停止保存均通过 |
| 历史工作流 | 当时全部通过；其模拟器、测试报告与截图环节现已移除 |
| SAF / 存储提供方 | 不同文件管理器、SD 卡、撤权场景 NOT_TESTED |
| HTTPS / HLS / DASH 真实片源 | 模块已集成；端到端真实片源 NOT_TESTED |
| 实体手机 | 原设备的方向循环仍需安装新版后复测；真实自动旋转、硬解、传感器和温升 NOT_TESTED |

自动测试仅使用人工生成的红/蓝视频，不包含用户媒体。模拟器通过证实了对应版本的 SDR 播放与 GL 选眼链路；球面地标/背面等数学另有 JVM 检查，不能据此认定手机性能、镜片舒适度或所有全景片源均已验收。

设计依据、完整改进清单与设备验收矩阵见 [docs/DESIGN.md](docs/DESIGN.md)。参考 [awara](https://github.com/Grinch27/awara)、[EhViewer](https://github.com/Grinch27/EhViewer)，保留原 GNU GPL v3 许可证，依赖说明见 [NOTICE](NOTICE)。
