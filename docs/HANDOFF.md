# 项目交班文档 (HANDOFF)

**版本**: 见 `.agent_state.json` 的 `ts` 字段。
**当前负责人**: Gemini (Auto-run)
**交班对象**: 接手的下一个 Gemini (Auto-run) 实例

---

## 1. 核心目标

我们的长期目标是将经典游戏《重装机兵2改 (MM2R)》改造为一个类似《宝可梦 MMO (PokeMMO)》的持久化在线世界。这意味着我们需要一个能支持多玩家实时交互的后端，并逐步将原版游戏的核心玩法（战斗、探索、交易等）在线化。

---

## 2. 已完成进度

根据 `docs/PROGRESS_LOG.md` 和 `.agent_state.json`，我们已经完成了以下关键阶段：

- **Phase A (资源提取)**
  - **A1**: 完成了基础的资源提取工具 `py/rom_tool.py`，能够从 ROM 中解包文件。

- **Phase B (内容管线 POC)**
  - **B1-B3**: 实现了从 ROM 解包 (`unpacked_v1`)。
  - **B4**: 通过图像识别和聚类，生成了关键瓦片集的 `selection.json`，这是地图渲染的基础。
  - **B5**: 基于 `selection.json` 成功渲染出世界地图的预览图 `screen.png`。
  - **B6**: 创建了客户端的 `MapDebugPage`，加载了地图预览图，并实现了本地玩家的移动控制。

- **Phase W (世界服务器)**
  - **W1**: 实现了初版的 WebSocket 世界服务器 (`server/src/index.js`) 和客户端 `MapDebugPage` 的网络同步。玩家可以在地图上移动，并通过 WS 将坐标广播给其他玩家。

---

## 3. 当前阻塞与解决方案

**问题**: 在 Firebase Studio (IDX) 环境下，Android 预览/模拟器无法通过 `http://localhost:8080` 访问后端服务。这是因为客户端和后端 Workspace 处于不同的网络命名空间。

**解决方案 (`W1_netfix`)**:
- **后端**: 已修改 `server/src/index.js` 监听 `0.0.0.0`，并统一 WebSocket 路径为 `/ws`。
- **客户端**: 已创建 `lib/app_config.dart` 并修改了相关页面，允许用户在 **设置** 页面配置后端的 **Forwarded URL** (从 IDX 的 **Ports** 面板获取)。
- **文档**: 已更新 `docs/README.md`，提供了详细的操作说明，指导用户如何获取和配置 URL。

**当前状态**: `W1_netfix` 的代码修改已完成，但尚未经过完整的用户操作验证。

---

## 4. 下一步计划

1.  **W1 验收 (Validation)**:
    - **必须** 按照 `docs/README.md` 中的步骤进行手动验证。
    - 启动后端，获取 8080 端口的 URL。
    - 在 App 的设置中填入该 URL 并保存。
    - 验证 `/health` 接口（在 "WorldPage"）和 WebSocket 连接（在 "MapDebugPage"）是否均正常工作。

2.  **W2 持久化规划 (Persistence Planning)**:
    - 在 W1 的基础上，规划如何将玩家状态（如位置）持久化。
    - 调研并选择合适的数据库（例如 Firebase Firestore 或 Realtime Database）。
    - 设计数据模型（玩家、物品、状态等）。
    - 启动 W2 的实现：当玩家移动时，将坐标存入数据库。

---

## 5. 关键产物路径

- **地图渲染预览图**: `contentpacks/poc/render_poc/screen.png`
- **瓦片集选择数据**: `contentpacks/poc/selection.json`
- **解包后的原始瓦片**: `contentpacks/poc/unpacked_v2/`
- **所有步骤的备份**: `backups/*.zip` (每个 zip 对应一个完成的步骤)
- **项目当前状态快照**: `.agent_state.json` (记录了已完成步骤的标志位)
- **详细执行日志**: `docs/PROGRESS_LOG.md` (记录了每次任务的中文摘要和产物)

---

## 6. 给新 GPT 的任务清单

在你开始任何新任务之前，**必须** 遵循以下步骤来完全理解当前上下文：

1.  **阅读本文档 (`docs/HANDOFF.md`)**：这是最高级别的指引。
2.  **查阅进度日志 (`docs/PROGRESS_LOG.md`)**：了解从项目开始到现在的每一步具体操作。
3.  **检查状态文件 (`.agent_state.json`)**：确认哪些步骤的标志位 (`"w1_netfix": true` 等) 已经被设置，这是最精确的状态判断依据。
4.  **执行 W1 验收**: 根据 `docs/README.md` 的指引，验证 `W1_netfix` 的成果。这是你接手后的第一个任务。
5.  **确认验收结果后，再开始 W2 的规划与执行**。