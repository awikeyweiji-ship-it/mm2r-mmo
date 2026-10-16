# Clash Meta / ChatGPT 中文助手 — 施工计划

**Spec:** `../specs/2026-10-12-clash-gpt-assistant.md`

1. 建立不依赖 Android 的决策状态机。先写安装/联网/启动决策测试，看到失败，再实现对应 Java 类。验收 `bash tests/run_core_tests.sh` 全绿。
2. 建立 AndroidManifest、主题与中文版 Android Activity：按状态绘制 UI；只在前台用户请求或返回时触发 GPT。验收 `gradle assembleDebug` 与 Manifest 审查。
3. 建立内置 CMFA 安装通道：PackageInstaller Session、签名不变、arm64 限制、Android 权限、安装结果回调；必要时保留用户确认。验收 SHA256 字节比对与 Android 真机安装测试。
4. 建立外部 START_CLASH 调用和 VPN+TLS 双检测。网络失败必须停止 GPT 启动。验收真实 Android VPN 连接与断连路径。
5. 加官方 Google Play 跳转、运行态 UI 提示、隐私和开源告知；整理交付说明、源码 ZIP、自动 CI 构建工作流。
6. 最终复检：本地 Java 单元测试、静态清单/资源/原始 APK 哈希、如 Android SDK 可用则 assembleDebug / 签名/Android 设备冒烟测试；如不具备则明确记录受限范围，不虚构安装包。

## Review Focus
- Android 14+ PackageInstaller PendingIntent 可变性与系统确认回调
- Android 11+ 包可见性声明
- Android 系统对后台启动 Activity 的限制
- CMFA external start 失败或配置为空时回退
- VPN 网络不代表 CMFA / 不代表公网可达
- 在 UI 线程之外流式写入 44MB APK
