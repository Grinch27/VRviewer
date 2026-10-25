# VRviewer

Android VR 视频播放器。首页只保留两个按钮：

- **打开本地文件**：通过系统“选择文件”行为选择文件管理器，再返回视频文件。
- **打开 URL 链接**：输入 HTTP / HTTPS 视频直链。

播放控制、投影与片源设置全部放在播放器内，首页不展示推荐、媒体库、最近列表或额外设置入口。

## 当前实现

| 能力 | 范围 |
| --- | --- |
| 片源 | 本地 content URI、HTTP/HTTPS 媒体直链；已接入 Media3 progressive、HLS、DASH 模块 |
| 投影 | 平面、180° 等距柱状、360° 等距柱状 |
| 片源排布 | 单目、左右 SBS、上下 TB；立体片源可选择左/上或右/下画面 |
| 观看窗口 | 固定单个视口与一套控制栏；方向遵循系统，不由播放器强制切换 |
| 操作 | 平面单指平移、全景单指转动视角；两指旋转画面、三指捏合缩放，回正、可选姿态控制 |
| 观看参数 | 独立缩放倍数输入；全景水平/垂直基础 FOV 分别独立调整；显示缩放后的实际视场 |
| 播放 | 暂停、进度拖动、倍速、单片循环；键盘左右键快退/快进 10 秒 |
| 控制栏 | 全部位于底部；文件名在进度条上方，超宽时自动滚动；按钮依次为设置、播放/暂停/重播、返回；回正在设置页、与重置并排 |
| 记忆 | 本地文件保留进度、模式、缩放、旋转、平移与 FOV，最多 50 条内部记录但不显示列表；URL 仅当前会话保存 |
| 系统行为 | 音频焦点、耳机断开处理，进入后台释放播放器与传感器 |
| 隐私 | 无账号、广告、遥测或上传；URL 不写入永久历史，不申请全盘/相机/麦克风权限，关闭备份 |

URL 指直接媒体资源，例如 MP4、WebM、m3u8、mpd。不能把视频网站的 HTML 网页、登录页或分享页当作视频直链；当前不提供网页抓取、Cookie 登录或自定义鉴权头。网络请求仅在用户打开链接后发生。

允许用户指定的 HTTP 链接以兼容局域网媒体；HTTP 本身不加密。HTTPS 使用系统证书校验。HLS/DASH 的格式通常由链接后缀推断；无后缀或特殊鉴权的地址仍需针对具体来源验证。

0.1.4 将手势分为“两指只旋转、三指只缩放”，避免同时触发；四指及以上暂停这两种变换，增减手指时重新建立基准。此前 0.1.3 增加文件应用选择、画面变换及两轴 FOV，0.1.2 将操作栏合并到底部，0.1.1 删除显示方式和方向请求。当前仍是一个观看窗口，旋转只改变视频画面角度；系统横竖屏不由播放器控制。SBS/TB 表示片源排布，可选择其中一眼的画面。当前只处理 SDR，检测到 HDR 会暂停提示；鱼眼、立方体、任意网格和所有设备的 4K/8K 解码均不作兼容承诺。

文件选择面板由 Android 提供，只能列出支持选择并返回视频内容的应用；不保证所有已安装的文件管理器都会出现。系统文档入口尝试取得持久读取权限，其他应用可能仅提供临时权限，下次需重新选片。不复制视频、不索取全盘权限。缩放仅要求有效正数，没有原来的固定观看范围；全景基础 FOV 可设为 0.1°–179.9°，渲染仍需遵循透视投影与浮点精度边界。

## 使用

1. 点击“打开本地文件”，选择文件应用并选片，取消后仍留在首页；或点击“打开 URL 链接”输入媒体地址。
2. 进入播放器后，在底部左侧“设置”中选择投影与片源排布。默认平面、单目；SBS 显示左图、TB 显示上图，可切换“使用右眼画面”。
3. 平面视频单指拖动可平移；180°/360°单指拖动改变视角，也可选择姿态控制。所有投影均用两指扭转画面、三指捏合缩放；多指操作后全部抬起，再开始单指操作。
4. 设置里输入大于 0 的缩放倍数并点击“应用”，或使用缩小/放大。全景水平与垂直 FOV 分别独立调整；初次水平值按当前屏幕比例生成，之后单独保存，不随另一轴或横竖屏变化。
5. 设置页的“回正”与“重置观看参数”并排。“回正”恢复视角、画面旋转和平面位置，保留缩放与 FOV；“重置观看参数”恢复 1×、80°垂直 FOV、按当前比例生成的水平 FOV 及初始角度/位置，不改片源与进度。
6. 单击画面显示/隐藏控制，双击播放/暂停；空格/确认键播放暂停，左右键快退/快进。
7. 文件授权或网络链接失效时返回首页重新打开。网络 URL 在当前会话中保留配置重建时的播放状态，进程结束后不从永久记录恢复。

## 构建

基线：JDK 17、Gradle 8.13、Android SDK 36、Build Tools 36.0.0，最低 Android 8.0 / API 26，target SDK 36。依赖固定在版本目录，Compose BOM 2026.03.00 与此基线匹配。

~~~bash
./gradlew --no-daemon --console=plain --stacktrace :app:assembleDebug :app:assembleRelease
~~~

[Android APK 工作流](https://github.com/Grinch27/VRviewer/actions/workflows/build-apk.yml) 参考 [awara 的原生 APK 构建](https://github.com/Grinch27/awara/blob/main/.github/workflows/build-apk.yml)，仅保留环境准备 → Debug/Release 打包 → 上传 APK。使用 Non-container、共享动态 runner-image 和已核验的 main 分支 Actions。

- 可安装调试 APK：app/build/outputs/apk/debug/app-debug.apk。
- 未签名 Release APK：app/build/outputs/apk/release/app-release-unsigned.apk，产物名 VRviewer-release-unsigned-<源码提交>；需自行签名后安装。
- Actions 分别上传调试 APK、未签名 Release APK 与各自 SHA256SUMS；不运行单元测试、instrumentation、模拟器或独立 lint 步骤，不上传测试报告，不发 GitHub Release 或应用商店。
- 调试签名由构建环境生成，不保证不同运行的 APK 能覆盖旧版本；卸载会清空应用内进度与设置。需要保留记录时先确认迁移方式；稳定签名需要另行配置。更新签名要求见 [Android 官方说明](https://developer.android.com/google/play/app-updates)。

0.1.4 已构建成功：[下载 Debug APK](https://github.com/Grinch27/VRviewer/actions/runs/40242529361/artifacts/12617711408) · [下载未签名 Release](https://github.com/Grinch27/VRviewer/actions/runs/40242529361/artifacts/12617134957) · [构建记录](https://github.com/Grinch27/VRviewer/actions/runs/40242529361)。手势明确分为两指旋转、三指缩放，增减手指时重新建立操作基准。

在 Actions 运行页面的 **Artifacts** 中下载 `VRviewer-debug-<源码提交>`，解压后安装 `VRviewer-debug.apk`。未签名 Release 单独位于 `VRviewer-release-unsigned-<源码提交>`，每份压缩包均含 `SHA256SUMS`。产物保留 14 天，过期后可手动运行工作流重建。

Gradle 编译与打包的内置检查按插件默认行为执行。当前没有测试 Job、测试 APK 构建、模拟器/KVM/音频运行库安装或自动截图。测试源码保留为手动诊断材料，只有用户另行要求时才执行。

## 最近已完成的构建记录（0.1.4）

源码 `00b4723f11eb43c33f7a60fc50441926345aa194` 的 [Actions #40242529361](https://github.com/Grinch27/VRviewer/actions/runs/40242529361) 已成功，runner-image 和 APK build 两个 Job 均完成，Debug 与未签名 Release 已上传。工作流继续仅调用 assembleDebug/assembleRelease，不设置测试环节；assembleRelease 自带的 lintVital 构建检查由 AGP 默认执行。

按项目规则，本次只确认 Actions 结果并提供 APK 链接，没有下载或扫描产物。构建成功不代表功能实测通过；两指/三指切换、触控灵敏度与手机系统手势兼容在模拟器和手机上均为 NOT_TESTED。

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

历史测试仅使用人工生成的红/蓝视频，不包含用户媒体。模拟器通过证实了对应版本的 SDR 播放与 GL 选眼链路；球面地标/背面等数学另有 JVM 检查，不能据此认定手机性能、镜片舒适度或所有全景片源均已验收。

设计依据、完整改进清单与设备验收矩阵见 [docs/DESIGN.md](docs/DESIGN.md)。参考 [awara](https://github.com/Grinch27/awara)、[EhViewer](https://github.com/Grinch27/EhViewer)，保留原 GNU GPL v3 许可证，依赖说明见 [NOTICE](NOTICE)。
