# R5 双 AI 启动与智能节点选路设计

## 目标和定位
在 R4 双包安装助手上加入 Google Gemini APK 的独立安装、ChatGPT/Gemini 目标选择，以及经用户授权控制同机 Clash Meta 当前策略组的服务定向节点优选。默认中文，高端深空蓝/电光青；不能把探测通过说成账号/区域权限通过。

## 功能边界
- 内置并保持未修改的 Clash Meta 2.11.30 ARM64、ChatGPT 1.2026.279 APKM（37 个分包）、Gemini 1.0.882555348 单 APK，启动器不重签这些包。
- 普通安卓应用不可绕过系统安装确认、VPN 首次授权、地区与账号使用条款。
- 最低 Android 12L + arm64；Gemini 自身 minSdk28，但 ChatGPT 分包 minSdk32。
- CMFA 外部 START_CLASH/STOP_CLASH 可用；节点控制要在 CMFA 里通过「覆写」主动启用本地 external-controller（默认 127.0.0.1:9090）并配置 secret。未授权时绝不宣称自动切换成功。
- 不读取订阅文件、账号密码、Cookie，不把 controller secret 发送到网络。密钥本机 AndroidKeyStore AES/GCM 加密保存；只连接 127.0.0.1 选定端口。
- 只测试用户现有 Clash 策略组的叶节点，不添加或下载陌生代理，不修改配置文件。

## 选路算法
1. 确认服务已安装、VPN 网络存在、本地 controller 有效、选择正确的 Selector 策略组。规则模式下多个组无法确认真实路由时要求用户指定。
2. 对当前 Selector 所有候选叶节点执行服务定向 HTTPS URL 延迟探测，每个候选独立测两次。上限512节点与90秒；超时必须显示实际已扫数量。
3. 剔除失败、DIRECT、REJECT、非叶子组。两次延迟均有效的节点，按 mean(delay)+0.6*jitter 升序排名，相同分按节点名固定顺序。
4. 仅在选定的策略组内 PUT 候选，GET 确认切换，再通过 Android VPN Network 的 https://chatgpt.com/ 或 https://gemini.google.com/app 真正访问（不跟随跳转）；最多尝试6个领先候选。成功后显示“网站可达，账号权限待确认”。
5. HTTP 401/403/429/451 不当作通过；连接异常、VPN断开、系统限制、API不可用时失败并尽力恢复切换前选项。任何未确认状态回退手动 Clash UI。
6. 仅扫描当前设备实际配置的节点，不跨其它 VPN 应用；“最优”限定在本次实测候选与检测窗口，不是永久保证。

## 操作界面
- 首页顶部两个大按钮 ChatGPT | Gemini：先选目标再点单个主按钮；状态卡与节点状态展示真实数据。
- 首次向导：Clash 安装、ChatGPT 安装、Gemini 安装、启用 VPN；设置页单独折叠/分区呈现智能选点控制口令、端口和策略组。
- 认证凭据只在设置页填写，默认关闭智能选点；选点失败告知如何打开 Clash 或修复覆写。
- 网络检测状态与选中目标绑定，切换目标立即失效前一目标的成功标记。

## 交付与验证
- Android compileSdk35/target35; Java17. 打包前跑旧套件+新 ServiceTarget/SmartNodePolicy Java 测试与契约测试。
- Google Gemini APK 用 apksigner 验证 v3 签名，签名SHA256=997c9c5d63e84a5024f308a615c4e4a87773a52073b0e1547a4d3b8b1081efa7。
- 对三份原包重新提取并校验SHA256，验证 APK v2/v3 签名/zipalign/包名/版本。
- 真机登录、VPN路由精确归属和各种厂商 ROM 测试必须单独验收，不得以静态/编译测试代替。
