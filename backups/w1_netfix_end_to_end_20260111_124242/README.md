# Docs

工程图、协议、路线图会放在这里。

## 开发环境后端连接说明（IDX 网络修复版）

在 Firebase Studio (IDX) 环境下开发时，前端 Flutter 应用（运行在 web 或 emulator）和后端 Node.js 服务（运行在 workspace 容器）不在同一个网络命名空间。**不能直接使用 localhost。**

**如何让前端连接到后端：**

1. **启动后端**：确保后端服务已启动（运行 `./scripts/dev_server.sh`），默认端口 8080。
2. **获取 URL**：在 IDX 底部或右侧面板 "Ports" 中找到 8080 端口。
   - 复制其 **Forwarded URL**（例如 `https://8080-idx-your-project-url.cluster.idx.dev`）。
   - 确保该 URL 在浏览器中可访问（应返回 JSON）。
3. **配置 App**：
   - 打开 Flutter 客户端，点击主页的 **设置**。
   - 将 **后端地址** 修改为上面获取到的 URL。
   - 点击 **保存**。
4. **验证连接**：
   - 回到 **进入世界** 页面，点击 **连接测试 /health**，确保显示 **SUCCESS**。
   - 进入 **地图调试 MapDebug** 页面，点击 **连接世界WS**，确保能连接成功（App 会自动将 https 替换为 wss 并连接 /ws 路径）。

注意：每次重新打开 Workspace，该 URL 可能会变化，如果连接失败请重新检查 URL。
