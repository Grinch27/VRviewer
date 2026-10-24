# VRviewer

Android VR 视频播放器。首页只保留两个按钮：

- **打开本地文件**：通过系统选择器打开视频。
- **打开 URL 链接**：输入 HTTP / HTTPS 视频直链。

播放控制、单眼/双眼显示与 VR 设置全部放在播放器内，首页不展示推荐、媒体库、最近列表或额外设置入口。

## 当前实现

| 能力 | 范围 |
| --- | --- |
| 片源 | 本地 content URI、HTTP/HTTPS 媒体直链；已接入 Media3 progressive、HLS、DASH 模块 |
| 投影 | 平面、180° 等距柱状、360° 等距柱状 |
| 片源排布 | 单目、左右 SBS、上下 TB、交换眼睛 |
| 显示 | 单眼窗口、双眼分屏；一个解码器供两个视口 |
| 操作 | 拖动视角、双指调节视野、回正、可选姿态控制 |
| 播放 | 暂停、进度、±10 秒、倍速、单片循环 |
| 记忆 | 本地文件保留进度/模式，最多 50 条内部记录但不显示列表；URL 只在当前会话内保存进度和模式 |
| 系统行为 | 音频焦点、耳机断开处理，进入后台释放播放器与传感器 |
| 隐私 | 无账号、广告、遥测或上传；URL 不写入永久历史，不申请全盘/相机/麦克风权限，关闭备份 |

URL 指直接媒体资源，例如 MP4、WebM、m3u8、mpd。不能把视频网站的 HTML 网页、登录页或分享页当作视频直链；当前不提供网页抓取、Cookie 登录或自定义鉴权头。网络请求仅在用户打开链接后发生。

允许用户指定的 HTTP 链接以兼容局域网媒体；HTTP 本身不加密。HTTPS 使用系统证书校验。HLS/DASH 的格式通常由链接后缀推断；无后缀或特殊鉴权的地址仍需针对具体来源验证。

**双眼分屏尚未进行镜片光学校准**，没有畸变校正、头显参数或瞳距设置。单目视频分屏不会生成真实双目深度。首版只处理 SDR，检测到 HDR 会暂停提示；鱼眼、立方体、任意网格和所有设备的 4K/8K 解码均不作兼容承诺。

## 使用

1. 点击“打开本地文件”，或点击“打开 URL 链接”输入媒体地址。
2. 进入播放器后，在“设置”中分别选择投影、片源排布、单眼/双眼显示。默认平面、单目、单眼，不按宽高比猜测。
3. 180°/360°可拖动视角、双指调整 35°–110°垂直视野，也可选择姿态控制与回正。
4. 单击画面显示/隐藏控制，双击播放/暂停；空格/确认键播放暂停，左右键快退/快进。
5. 文件授权或网络链接失效时返回首页重新打开。网络 URL 在当前会话中保留旋转时的播放状态，进程结束后不从永久记录恢复。

## 构建

基线：JDK 17、Gradle 8.13、Android SDK 36、Build Tools 36.0.0，最低 Android 8.0 / API 26，target SDK 36。依赖固定在版本目录，Compose BOM 2026.03.00 与此基线匹配。

~~~bash
./gradlew --no-daemon :core:test :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
~~~

连接测试设备/模拟器：

~~~bash
./gradlew --no-daemon :app:connectedDebugAndroidTest
~~~

[Android APK 工作流](https://github.com/Grinch27/VRviewer/actions/workflows/build-apk.yml) 使用 Non-container 路线、共享动态 runner-image 和已检查的 main 分支 Actions。输出：

- 可安装调试 APK：app/build/outputs/apk/debug/app-debug.apk。
- Release 构建验证：app/build/outputs/apk/release/app-release-unsigned.apk，未配置生产签名。
- Actions 上传 APK、SHA256、测试报告，不自动发 Release 或应用商店。
- 调试签名由构建环境生成，不能保证不同环境的调试 APK 能覆盖安装；正式分发仍需独立稳定签名。

已通过完整验证的预览：[下载 APK ZIP](https://github.com/Grinch27/VRviewer/actions/runs/40042072932/artifacts/12540041041) · [构建记录](https://github.com/Grinch27/VRviewer/actions/runs/40042072932) · [播放报告与实际首页截图](https://github.com/Grinch27/VRviewer/actions/runs/40042072932/artifacts/12539179179)。APK 为 14,827,638 字节。

在 Actions 运行页面的 **Artifacts** 中下载 `VRviewer-debug-<源码提交>`，解压后安装 `VRviewer-debug.apk`，同目录的 `SHA256SUMS` 可用于校验。产物与报告保留 14 天；过期后可手动运行工作流重新构建。

本次预览 APK 的 SHA256：

~~~text
33acc56d17bb170f1b0a281b4c74aeec82cde0b47e5930accdfb43e27e5a2688  VRviewer-debug.apk
~~~

构建与 Android 35 模拟器校验并行运行。模拟器使用官方 SDK 命令、KVM 与 SwiftShader，显式安装 Ubuntu 所需的 `libpulse0`；报告包含启动/解码日志。测试结束后重新安装调试 APK，再截取实际首页。

## 验证记录

已核验源码 `483217e5eba634f8fcfd3c922f769c0a0a79499a` 的 [Actions #40042072932](https://github.com/Grinch27/VRviewer/actions/runs/40042072932)：动态 runner-image、APK 构建与检查、Android 35 播放三个 Job 均成功，整体结论为 success。已下载测试 XML 与安装包核对数量和 SHA256，并目视检查真实首页截图。

| 验证 | 已确认结果 |
| --- | --- |
| JVM | 14 项通过，0 失败、0 错误、0 跳过；投影/选眼/比例/续播 11 项，URL 3 项 |
| Android lint | Debug、Release 各 0 错误、15 警告；主要为版本更新提示与 KTX 建议 |
| APK 构建 | Debug、启用 R8 的未签名 Release、instrumentation APK 均成功 |
| Android 35 / x86_64 模拟器 | XML 确认 6 项通过，0 失败、0 错误、0 跳过 |
| 首页与 URL 输入 | 首页恰好两个点击入口；无效协议不能打开，有效 HTTP/HTTPS 可确认，取消后仍为两个入口 |
| 实际视频链 | 人工生成 H.264 SDR MP4 经本地缓存文件与回环 HTTP 解码，PixelCopy 检查 SBS/TB、单/双眼、交换眼睛、360°取样、seek 和停止保存 |
| 工作流整体与首页截图 | 全部通过；测试后重新安装 APK 并启动首页，1080×1920 截图确认仅两个应用按钮 |
| SAF / 存储提供方 | 选择器和 content URI 实现已编译；不同文件管理器、SD 卡、撤权场景 NOT_TESTED |
| HTTPS / HLS / DASH 真实片源 | 播放模块已集成并构建；端到端真实片源 NOT_TESTED |
| API 26 实机、实体手机硬解、头部姿态、镜片光学、温升 | NOT_TESTED |

自动测试仅使用人工生成的红/蓝视频，不包含用户媒体。模拟器通过证实了已测 SDR 播放与 GL 选眼链路；球面地标/背面等数学另有 JVM 检查，不能据此认定手机性能、镜片舒适度或所有全景片源均已验收。

设计依据、完整改进清单与设备验收矩阵见 [docs/DESIGN.md](docs/DESIGN.md)。参考 [awara](https://github.com/Grinch27/awara)、[EhViewer](https://github.com/Grinch27/EhViewer)，保留原 GNU GPL v3 许可证，依赖说明见 [NOTICE](NOTICE)。
