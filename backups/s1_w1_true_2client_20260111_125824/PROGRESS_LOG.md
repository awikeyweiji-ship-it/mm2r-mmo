
## 20260111_115741 - B5 渲染预览图
- 状态：B5 完成
- 生成：contentpacks/poc/render_poc/screen.png
- 备份：backups/screen_20260111_115741.png

## 20260111_120848 - B6 客户端地图调试
- 状态：B6 完成
- 页面：MapDebugPage
- 备份：backups/b6_client_map_debug_20260111_120848.zip

## 20260111_121419 - W1 WebSocket世界同步
- 状态：W1 完成
- 备份：backups/w1_world_ws_sync_20260111_121419.zip

## 20260111_122749 - W1 NetFix IDX URL
- 状态：修复完成
- 后端: 监听 0.0.0.0, WS 路径 /ws
- 客户端: 新增 AppConfig, 支持从设置中配置 URL
- 文档: 更新 README 说明如何获取和配置 IDX URL
- 备份：backups/w1_netfix_idx_url_20260111_122749.zip

## 20260111_124242 - W1_netfix 端到端加固与自检
- 状态：完成。客户端增加了 localhost 防呆，并在 Workspace 内完成了 /health 和 /ws 自检。
- 变更文件：lib/main.dart
- 验收：用户需从 IDX Ports 面板复制 8080 URL，粘贴到 App 设置中，即可完成端到端验证。
- 备份：backups/w1_netfix_end_to_end_20260111_124242.zip

## 20260111_125046 - W1 用户无操作端到端自检
- 状态：完成。实现了基于 PREVIEW_URL 的后端地址自动发现机制。
- 变更文件：lib/main.dart, lib/app_config.dart
- 验收：App 启动时会自动尝试发现并连接后端，无需用户任何操作。
- 备份：backups/w1_userless_e2e_20260111_125046.zip
