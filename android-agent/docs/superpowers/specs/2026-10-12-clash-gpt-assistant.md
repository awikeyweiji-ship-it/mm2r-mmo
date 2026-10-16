# Clash Meta / ChatGPT 中文助手 — 已确认设计

## 目标
单一中文入口面向不熟悉 Android 的客户：初次安装 CMFA → 在 CMFA 内导入合法配置并授权 VPN → 连接确认后安装及打开官方 ChatGPT；日常优先触发 CMFA，再进入 GPT。不得保存任何账号、订阅、密码、私密 URL。

## 安装边界
- 发行包保留客户上传的 arm64-v8a CMFA 2.11.30 Meta APK 字节及原有签名，作为独立包按 Android PackageInstaller 用户确认安装。
- ChatGPT 保留独立官方签名；通过 Google Play `com.openai.chatgpt` 安装及更新，绝不嵌入第三方非官方 APK。
- 安卓系统 VPN 授权、外部来源安装授权均由用户在系统界面确认，不静默安装、不代登录。
- helper 包名 `com.jeyu.clashgptstarter`，兼容 Android 8.0+；内置 Clash 仅适合 arm64-v8a，其他 ABI 需提示不可安装。

## 启动与网络条件
- 缺 CMFA：安装 CMFA；缺配置或 VPN 未建立：打开 CMFA 并使用其官方 external START_CLASH action 作非保证的启动尝试；用户返回助手后重新检查。
- 网络检查使用 Android ConnectivityManager 确认 *存在系统 VPN 通道*，再由 Android `Network.openConnection` 绑定该 VPN network 异步访问 `https://chatgpt.com/` 检查 TLS/HTTP 连通性（2xx-4xx 视为可达）；任何一步失败都不得自动打开 GPT。
- VPN 检查不能证明是由 CMFA 建立；访问站点也不能证明用户已完成 GPT 登录。UI 应明确区分。
- Android 10+ 后台启动 Activity 受限，因此只有助手在前台/用户主动返回时进入 GPT，不声明后台全自动跳转。
- ChatGPT 缺失：只打开官方 Play 商店页，Play 不可用可用网页版 Play 下载页；无法绕过地区、账号或安装限制。

## 状态与回退
用户看到：CMFA 安装、VPN 状态、GPT 安装状态、网络验证结果。异常包含：ABI 不适配、未知来源未授权、安装被取消、Clash 未配置/未连通、VPN 活跃但网站不可达、GPT 不可启动；提供返回 CMFA / 重试检查 / 打开官方商店。

## 安全和可用性
不使用可访问性权限，不后台读取剪贴板，不保存登录信息，不截获 ChatGPT 流量；联网探测仅访问指定官方域名。尽量不引入其他依赖，安装回调、异步 I/O、应用权限范围最小。

## 验收门槛
1. Android 项目可以通过 AGP assembleDebug 构建签名的 APK。
2. APK 包内 CMFA SHA256 与上传文件相同。
3. 单元测试覆盖安装/启动流程中的分支状态，失败时不能打开 GPT。
4. 用真实 Android 设备验证 APK 安装、CMFA 首次权限、VPN 建立、GPT 官方商店及日常打开；无法在无设备环境完成时必须标注未验证。
