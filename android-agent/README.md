# AI 智能启动助手 R5.0｜ChatGPT / Gemini / Clash Meta

用户自用的第三方应用安装和网络诊断助手。首页提供 **ChatGPT / Gemini** 两个目标按钮，保留深空蓝 + 电光青科技 UI，按安装状态显示一个主要操作。不会读取 AI 账号密码或代理订阅文件。

## 实际功能

- 启动器 Android package: `com.jeyu.clashgptstarter`，`versionCode=5`，`versionName=5.0.0`；编译 `compileSdk35 / targetSdk35 / Java17 / Gradle8.9`。
- R5 安装发布版中原样内置三份用户提供安装档案：Clash Meta 2.11.30 ARM64 (`clash-meta.apk`)、ChatGPT 1.2026.279 (`chatgpt-original.apkm`,37个split APK)、Gemini 1.0.882555348 (`gemini-original.apk`)。**三者始终是独立 APP**，安装通过 Android PackageInstaller 由用户确认。
- 主界面选择 ChatGPT/Gemini；缺少目标 app 时先安装。Clash 已连接后通过 Android VPN 网络进行服务网站可达性测试，成功再进入对应 app。
- 用户主动启用 Clash Meta 本地控制器（只接受 `127.0.0.1:<端口>` 和访问密钥）后，助手可以在指定 Selector 策略组中获取当前节点、针对选定服务测试网络延迟、依分数尝试切换、在 VPN 中探测网站可达性，失败后尽力恢复原来的节点。密钥在 AndroidKeyStore 加密保存。

## 注意的运行限制

- **本次附带 ChatGPT 版本要求 Android 12L/API32 或更新、ARM64**。虽然安装助手 manifest 的 `minSdk` 是26，但较低安卓版本无法使用此次三应用整合的完整功能；请勿把低版本系统计入适配成功。
- Android 安装确认、未知应用来源许可和 VPN 首次授权都必须用户操作，不能绕过。
- 默认智能选点关闭；Clash Meta 必须先通过「覆写」配置本机控制器地址与 secret（只绑定 loopback，勿暴露 `0.0.0.0`）并确认正确路由组。无此能力时只能沿用当前 Clash 连接或手动选点。
- 仅对当前已配置 Selector 的叶节点进行扫描，最多512个节点、90秒、优选排名前6个。未扫完会明确反馈数量。
- `https://chatgpt.com/` 和 `https://gemini.google.com/app` 网站可达，**不证明用户地区、账户、登录或付费权益可用**；节点名称里的国家标签不作为结论。具体地域与功能以官方服务规则为准。
- 系统 VPN 网络探测不等于证明该条请求必然经过刚切换的 Selector；Clash 的规则要正确指向该组。
- **未做 Android 真机端到端验收**；代码测试和构建不是实际可登录的证明。

## 构建与校验

此仓库的公开源代码副本**故意不包含第三方应用二进制和私有密钥**。构建者自备符合上述哈希的原始档案到 `app/src/main/assets/`，并通过各应用原签名验证，然后运行：

```bash
bash tests/run_core_tests.sh
python3 tests/test_contract.py
python3 tests/test_ui_contract.py
python3 tests/test_target_ui_contract.py
python3 tests/test_route_restoration_contract.py
python3 tests/test_dual_install_contract.py
python3 tests/test_apkm_archive.py
python3 tests/test_gemini_contract.py
gradle --no-daemon :app:assembleDebug
```

完整交付版先把已通过 GitHub Actions 真编译的 `r5-shell-debug.apk` 放进项目约定位置，再运行 `assemble_r5_offline.py` 使用单独保存的私有签名证书包装、zipalign、apksigner、三文件SHA256逐字节验证。源工程里的 `assemble_offline.py` 是早期 R4 兼容脚本，不用于 R5 交付。

详细操作与限制见交付附件 `客户安装与选点说明.txt`、`技术验收报告.md`，第三方声明见 `THIRD_PARTY_NOTICES.md`。
