# R5 双 AI 选择与智能选点 Implementation Plan

> For agentic workers: required sub-skill superpowers:executing-plans. Each task follows RED → GREEN → verification, with audit records.

**Goal:** 在原 R4 基础上增加 Gemini 与经授权的 Clash Meta 节点优选，同时生成可安装的整合 APK。

**Architecture:** ServiceTarget/SmartNodePolicy 负责纯规则。GeminiInstaller 和原两个 Installer 独立提交系统安装事务；MihomoController 严格 loopback 调用本地授权 API，SmartRouteEngine 扫描、试切、回退；ServiceNetworkGate 在系统 VPN Network 通过 HTTPS 验证。MainActivity 是唯一前台控制器，密钥 AndroidKeyStore 加密。

**Tech Stack:** Android Java17, Gradle8.9, AGP8.7.x, compileSdk35, Android PackageInstaller, AndroidKeystore, Mihomo REST API.

**Spec:** docs/superpowers/specs/2026-10-13-ai-dual-launch-smart-route-r5.md

## Global constraints
- 保留三原始安装包字节不变，不能代替系统安装确认。
- 回环控制端口不允许外网、密钥不明文存储。
- 任何检测结果不可虚构实际登录、账号、地区权限已通过。
- 不改变源仓库主分支或升级现有独立应用的签名。

### Task 1：证据化核验原包与原流程
- [x] 验证 Gemini SHA、Google 签名、包名和版本
- [x] R4 流程/安装测试基线绿色
- [x] 新测试先失败（新 Java 类型和功能文件缺失）

### Task 2：服务切换与 Gemini 安装
- [x] 添加 ServiceTarget + Java 测试
- [x] GeminiInstaller 安装会话/包名/回调/哈希
- [x] MainActivity 服务选择、按目标安装、已安装判断
- [x] AndroidManifest queries 和局域回环控制 NSC

### Task 3：智能选路组件
- [x] SmartNodePolicy RED→GREEN
- [x] MihomoController 严格回环、Bearer、GET proxy/delay、PUT Selector
- [x] AndroidKeyStore 保存密钥，选项默认关闭
- [x] SmartRouteEngine 双探测排名和实际 VPN HTTP 验证，失败回滚测试
- [x] MainActivity 设置与状态，主按钮绑定当前目标

### Task 4：集成自检与编译
- [ ] 重新执行全部 Java/Python 测试，检查 Android 真编译
- [ ] Google Gemini 签名复核，安装包 Gemini资产字节校验
- [ ] 整合APK重新签名与对齐、包信息检查、输出文件库
- [ ] 记录真机未通过/未执行项，并提供三应用安装说明
