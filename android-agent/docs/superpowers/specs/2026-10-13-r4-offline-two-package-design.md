# R4 离线双应用安装助手设计规格（2026-10-13）

## 目标
在已经通过验证的 R2/R3 中文科技风启动助手中，内置用户提供的 **Clash Meta 2.11.30 ARM64 原始 APK** 与 **ChatGPT 1.2026.279 APKMirror APKM 原始包**。从单一安装助手入口发起两次由 Android 系统确认的安装；今后启动先保证 Clash VPN 已建立并通过 ChatGPT 网络探针，再打开官方 ChatGPT 应用。

## 已验证原始输入
- Clash APK 包名 `com.github.metacubex.clash.meta`，SHA-256 `93a719347dc2318b6b6356c588646a7ee82fd7ab0355e7a865fe6954de1234f0`。
- ChatGPT APKM 包名 `com.openai.chatgpt`、版本 `1.2026.279`、versionCode `2627907`；原文件 94,860,022 字节；SHA-256 `cd7af30278fcca6b37cb0a7ee77aebc2fd562039b05a2ea4263c6498698e3c8f`，与 APKMirror 同版本公开 SHA-256 一致。
- 原包包含 `base.apk`、4 种 ABI、7 种屏幕密度、25 种语言的分包；最低 Android API 32。
- 此构建仅适用于 ARM64 且 Android 12L+，因为 Clash 原件 ARM64-only 且 ChatGPT APKM API32+。

## 设备端流程
1. 若设备不兼容，展示中文明确原因和恢复建议，不向系统提交错误安装会话。
2. 未安装 Clash：主按钮显示“安装连接工具”。点击后由助手核对内置 Clash SHA256，通过 PackageInstaller.Session 发起安装，系统确认。
3. Clash 安装后若 ChatGPT 未安装：主按钮改为“安装 ChatGPT”。对内置 APKM 作原包 SHA256 校验，根据设备选取 `base.apk`、`split_config.arm64_v8a.apk`、最接近当前屏幕密度的一个资源分包和 `en` 与当前系统首选语言资源分包。未经任何改签与内容修改，逐一写入同一个 ChatGPT PackageInstaller.Session。系统确认安装。
4. 两项都已安装：主按钮进入“连接 VPN”，先打开 Clash；VPN 网络检测与 VPN 网络内 HTTPS 探测通过后打开官方 ChatGPT。
5. 非法 APKM、SHA 不匹配、缺失分包、重复分包、IO 错误、安装拒绝、系统限制均中文提示；不弹过量按钮；提供 Google Play 备选入口。
6. 在用户授权未知来源应用安装、每个应用安装确认、首次 VPN 授权等 Android 安全门槛上不越权、不静默。

## 技术边界
- 提取并保留 APKM 内被选中的单独已签名 APK，绝不解包/重签 ChatGPT 原生 APK，也不嵌入账号/代理配置。
- 外层 APKM 是给安装器使用的 ZIP 载体；会验证它与用户上传原件的 SHA256；Android 系统另外核验所有选择的分包签名/包名/版本一致性。
- PackageInstaller.Session 总数据量和条目数量上限，避免 zip bomb 与存储耗尽；后台线程流式校验和写入；不会把密码上传网络。
- 安装器目标 API35，测试签名只用于个人测试；旧启动器签名不同可能需要先卸载旧启动器。
- 供用户复制的单一助手 APK ≠ 真正“两个应用合并成一个包”；应用依然独立安装。
- 核验范围：源文件 SHA、选择器 TDD、安卓项目构建、APK 结构/签名及静态 UI 约束；实际手机点击/系统确认需真机进一步测试，未做不宣称做了。
