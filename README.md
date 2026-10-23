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

## 验证记录

| 验证 | 已确认结果 |
| --- | --- |
| 初次 CI #39817920976 | core 测试阶段完成；Kotlin 编译因状态类型递归、DisplayMode 同名冲突失败，未生成 APK |
| 本轮修正 | 明确状态类型、使用显式枚举导入；改为两按钮首页并新增 URL 播放 |
| 当前源码完整构建与模拟器 | 待本轮 Actions 核验，不视为通过 |
| 实体手机、头显光学、温升 | NOT_TESTED |

自动测试使用人工生成的红/蓝视频：验证左右/上下选眼、单/双眼、交换、seek，以及回环 HTTP 实际解码与 GL 像素输出；另有首页恰好两个入口、URL 校验、投影数学与历史保存检查。模拟器测试不能替代手机和镜片验收。

设计依据、完整改进清单与设备验收矩阵见 [docs/DESIGN.md](docs/DESIGN.md)。参考 [awara](https://github.com/Grinch27/awara)、[EhViewer](https://github.com/Grinch27/EhViewer)，保留原 GNU GPL v3 许可证，依赖说明见 [NOTICE](NOTICE)。
