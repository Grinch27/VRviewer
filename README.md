# VRviewer

Android 本地 VR 视频播放器，专注打开文件、观看和续播。原生 Kotlin、Material 3、Media3 解码与 OpenGL ES 渲染。

## 当前能力

| 维度 | 首版实现 |
| --- | --- |
| 文件入口 | 系统文件选择器、文件管理器的“打开方式”、`content://` 读取授权 |
| 视频投影 | 平面、180° 等距柱状、360° 等距柱状 |
| 片源排布 | 单目、左右 SBS、上下 TB；支持交换左右眼 |
| 显示方式 | 单眼窗口、双眼分屏；同一解码纹理分别采样两眼 |
| 观看控制 | 触摸拖动、双指调节 35°–110° 垂直视野、回正、可选姿态传感器 |
| 播放控制 | 暂停、续播、进度拖动、±10 秒、0.5–2 倍速、单片循环 |
| 本机记录 | 最多 50 条最近播放，保存进度和逐文件观看模式；播放中约每 5 秒和退出时保存 |
| 系统行为 | 自动处理音频焦点和耳机拔出，离开前台后释放解码器，不在后台继续播放 |
| 隐私 | 无应用网络权限，无全盘存储权限，无账号、广告、遥测、上传或云备份 |

“双眼分屏”是当前渲染输出能力，**尚不是经过光学校准的 Cardboard 模式**。还没有镜片畸变校正、头显二维码参数或瞳距校准。单目视频分屏不会生成真实双目深度。

首版仅面向 SDR；检测到 HDR 会暂停并提示不支持。没有宣称所有设备都能播放 HEVC、AV1、4K 或 8K。鱼眼、立方体和特殊投影网格也不属于当前支持范围。

## 使用

1. 安装调试预览 APK，点击“选择视频”，选择本地文件。
2. 打开“设置”，分别指定**投影 → 片源排布 → 显示方式**。默认是安全的平面、单目、单眼窗口，不根据宽高比猜测。
3. 对 180°/360° 等距柱状片源，可拖动画面改变观看方向，双指调整视野，按需启用陀螺仪。
4. 单击画面显示/隐藏控制，双击播放/暂停；键盘/遥控器空格或确认键播放/暂停，左右键快退/快进。
5. 文件被移动、SD 卡断开或授权撤销时，返回重新选片。支持持久授权的提供方会保留访问权限；其他提供方可能只授予临时访问。

系统文件选择器通过 `EXTRA_LOCAL_ONLY` 请求本地文件。应用本身不联网；第三方文档提供方是否遵守本地筛选由该提供方决定。

## 构建与验证

基础环境：JDK 17、Android SDK 36、Build Tools 36.0.0、Gradle 8.13；最低 Android 8.0 / API 26，target SDK 36。依赖统一记录在 `gradle/libs.versions.toml`，不使用动态 `+`。

```bash
./gradlew --no-daemon :core:test :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
```

有测试设备/模拟器时：

```bash
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

输出：

- 可安装预览：`app/build/outputs/apk/debug/app-debug.apk`。
- Release 构建验证：`app/build/outputs/apk/release/app-release-unsigned.apk`，未配置生产签名。
- 工作流：[Android APK](https://github.com/Grinch27/VRviewer/actions/workflows/build-apk.yml)。自动上传调试 APK、SHA256 和测试报告；不自动发布 Release 或应用商店。
- 调试签名由构建环境产生，不保证不同环境的调试 APK 可以互相覆盖安装；正式分发前需要确定独立且持续保管的签名方案。

工作流使用 Non-container 基础路线、动态 `runner-image`、经检查的 `@main` Actions。模拟器测试用人工生成的红/蓝测试视频，通过真实 MediaCodec → SurfaceTexture → OpenGL → PixelCopy 检查两眼裁剪，不包含用户视频。

## 验证状态

开发和提交时的实际检查结果将在这里记录。未执行的设备验证不能视为通过。

| 检查 | 状态 |
| --- | --- |
| 官方 Gradle、SDK 下载及校验 | 已完成 |
| Gradle Wrapper 生成 | 已通过 |
| JVM、lint、APK 构建 | 本地首次检查发现 Compose/SDK 不匹配；已改为兼容 BOM，最终结果以 CI 为准 |
| GitHub Actions | 待提交后验证 |
| 模拟器像素播放测试 | 待 CI 执行 |
| 实体手机、传感器、镜片、温升 | NOT_TESTED |

## 设计与后续方向

完整参考映射、功能边界、渲染方案取舍、优先级、验收矩阵与风险见 [设计与改进路线](docs/DESIGN.md)。

主要参考仓库为 [awara](https://github.com/Grinch27/awara) 和 [EhViewer](https://github.com/Grinch27/EhViewer)。本项目保留既有 GNU GPL v3 许可证；参考和依赖说明见 [NOTICE](NOTICE)。
