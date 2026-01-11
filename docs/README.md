# Docs

工程图、协议、路线图会放在这里。

## 开发环境后端连接说明

在 Firebase Studio (IDX) 环境下开发时，前端 Flutter 应用（运行在 web 或 emulator）和后端 Node.js 服务（运行在 workspace 容器）不在同一个网络命名空间。

**如何让前端连接到后端：**

1. 确保后端服务已启动（运行 `./scripts/dev_server.sh`），默认端口 8080。
2. 在 IDX 右侧面板 "Project IDX" -> "Backend Ports" 中找到 8080 端口。
3. 如果状态是 Active，点击那个链接图标（或查看 Details），获取 **公开的预览 URL**（例如 `https://8080-idx-your-project-url.cluster.idx.dev`）。
4. 打开 Flutter 客户端，点击 "设置"。
5. 将 "后端地址" 修改为上面获取到的 URL。
6. 点击 "保存"。
7. 回到 "进入世界" 页面，点击 "连接测试" 即可成功连接。
