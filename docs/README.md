# Docs

工程图、协议、路线图会放在这里。

## 如何在 IDX 里点开可视化预览 (Recommended)

本项目已接入 Project IDX 的 Previews 功能。你不需要手动在终端跑 `flutter run`，IDX 会自动为你准备好预览入口。

### 1. 打开 Previews 面板
- 在 IDE 界面顶部 Tab 栏寻找 **"Previews"** 标签。
- 或者点击 IDE 右侧边栏的 **"Previews"** 图标。

### 2. 选择预览实例
在 Previews 面板中，你会看到两个选项：
- **flutter_web**: 真正的游戏画面预览。IDX 会自动编译并展示 Flutter Web 界面。
- **backend**: 后端服务的健康检查预览（默认 8080）。

### 3. 连接后端
- **自动连接**：默认情况下，App 会尝试连接到当前的后端。
- **手动修正**：如果连接失败，请参考下方“后端连接说明”，将 backend 预览提供的 **Forwarded URL** 填入 App 设置中。

---

## 开发环境后端连接说明（IDX 网络修复版）

在 Firebase Studio (IDX) 环境下开发时，前端 Flutter 应用（运行在 web 或 emulator）和后端 Node.js 服务（运行在 workspace 容器）不在同一个网络命名空间。**不能直接使用 localhost。**

**如何让前端连接到后端：**

1. **启动后端**：确保后端服务已启动。在 IDX 中，`backend` 预览会自动保持运行。
2. **获取 URL**：在 IDX Previews 面板或底部 "Ports" 中找到 8080 端口。
   - 复制其 **Forwarded URL**（例如 `https://8080-idx-your-project-url.cluster.idx.dev`）。
3. **配置 App**：
   - 在 **flutter_web** 预览中，点击 **设置**。
   - 将 **后端地址** 修改为上面获取到的 URL。
   - 点击 **保存**。
4. **验证连接**：
   - 回到 **进入世界** 页面，点击 **连接测试 /health**，确保显示 **SUCCESS**。

# myapp

A new Flutter project.
