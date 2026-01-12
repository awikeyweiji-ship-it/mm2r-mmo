# 项目文档与开发指南

工程图、协议、路线图会放在这里。

## Web 预览模式切换 (WEB_MODE)

为了平衡“实时热重载”和“发布版稳定性”，本项目在 `.idx/dev.nix` 中通过 `WEB_MODE` 变量支持两种模式。

### 1. 开发模式 (dev) - **默认**
- **行为**：运行 `flutter run -d web-server`。
- **优点**：支持 **Hot Reload / Hot Restart**。修改代码后，点击 IDE 的热重载按钮或保存文件，预览画面会实时更新。
- **缺点**：性能稍弱（开发版编译），首次加载略慢。
- **如何使用**：确保 `.idx/dev.nix` 中 `env.WEB_MODE = "dev";`。

### 2. 发布模式 (release)
- **行为**：执行 `flutter build web --release` 并通过 Node.js 代理服务器提供静态服务。
- **优点**：性能最佳，模拟真实生产环境，适合最终演示。
- **缺点**：**不支持热更新**。任何代码修改都需要等待 `flutter build` 重新完成（约 1-2 分钟）。
- **如何使用**：
    1. 修改 `.idx/dev.nix` 中的 `env.WEB_MODE = "release";`。
    2. 执行 Command Palette -> **"Project IDX: Hard Restart"** 以重新启动预览服务器。

---

## 如何在 IDX 里点开可视化预览 (Recommended)

本项目已接入 Project IDX 的 Previews 功能。

**注意：如果预览没有自动刷新，请执行 Command Palette -> "Project IDX: Hard Restart"。**

### 1. 打开 Previews 面板
- 在 IDE 界面顶部 Tab 栏寻找 **"Previews"** 标签。
- 或者点击 IDE 右侧边栏的 **"Previews"** 图标。

### 2. 选择预览实例
- **web (Flutter Web)**: 真正的游戏画面预览。
- **backend (8080)**: 后端 Node.js 服务。

---

## 开发环境网络说明

在 IDX 中，我们使用了 **Tools Proxy (tools/web_dev_proxy.js)** 来处理同源问题：
- 所有指向 `/api/*` 的请求会自动转发到后端的 8080 端口。
- 所有指向 `/ws` 的 WebSocket 请求会自动转发到后端的 8080 端口。
- 这样 App 内部可以使用相对路径或同源策略，无需手动填写复杂的 Forwarded URL。
