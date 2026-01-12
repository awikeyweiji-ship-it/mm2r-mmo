
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

## 20260111_125824 - S1 同屏双人验收 + 观感升级
- 状态：完成。实现了房间管理、15Hz 广播节流、客户端插值、玩家信息展示及双客户端自动化测试。
- 变更文件：server/src/index.js, lib/main.dart, lib/app_config.dart, tools/ws_dual_client_sim.js
- 验收：开启两个预览窗口，进入同一房间，即可看到对方平滑移动的方块。
- 备份：backups/s1_w1_true_2client_20260111_125824.zip

## 20260111_130319 - World 页自检修复
- 状态：完成。修复了 World 页使用 localhost 的问题，增加了启动时自动探测/验证后端连接的机制。
- 变更文件：lib/main.dart, lib/app_config.dart
- 验收：启动 App 进入 World 页，会自动进行后端连接测试，无需用户任何操作。
- 备份：backups/world_selftest_fix_20260111_130319.zip

## 20260111_131313 - Web预览localhost修复
- 状态：完成。修复了Web预览下强制使用localhost的问题，并改用Python创建备份。
- 核心修复：在app_config.dart中，强制在Web端从浏览器地址(Uri.base)推导并验证后端URL，覆盖了不可靠的localhost默认值。
- 验收：打开Flutter Web预览并进入World页，顶部应自动显示非localhost的URL，并且自检通过。
- 备份：backups/world_baseurl_fix_20260111_131313.zip

## 20260111_132102 - Web预览CORS与URL修复
- 状态：完成。修复了Web预览下强制使用localhost、CORS跨域以及备份工具缺失的问题。
- 核心修复：1) 服务端(server/src/index.js)添加了cors包，解决了浏览器跨域问题。2) 客户端(lib/app_config.dart)实现了更鲁棒的URL推导，强制从浏览器地址(Uri.base)生成并探测可用后端URL，彻底阻止了localhost的使用。3) 备份脚本改用tar.gz，避免了zip/python3缺失的问题。
- 验收：打开Flutter Web预览并进入World页，顶部应自动显示非localhost的URL，并且自检通过。
- 备份：backups/world_web_fix_20260111_132102.tar.gz

## 20260111_143956 - W1_web_finalize 
- 状态：W1_web_finalize 通过
- 核心修复：彻底重写客户端URL发现逻辑，强制使用Uri.base推导后端地址，解决Flutter Web端回落localhost问题。

## 20260111_163000 - S3_first_playable + visual_proof
- 状态：完成。实现了 10 秒爽点（传送门）及程序内视觉证据导出逻辑。
- 10秒爽点：在 (400, 400) 处增加黄色传送门，进入后触发瞬移回起点并显示 UI 提示。
- 视觉证据：添加 RepaintBoundary 并在启动 3 秒后自动触发 Base64 截图导出（输出见 logs/s3_flutter_run.log）。
- 变更文件：lib/main.dart
- 备份：backups/s3_first_playable_20260112_032000.tar.gz
- [2026-01-12 03:29] Setup IDX Previews for Flutter Web and Backend. Updated docs/README.md.
- [2026-01-12 03:30] Android Emulator check skipped (SDK missing). Recommended Web Preview.
- [2026-01-12 03:36] IDX Previews configured (Web, Android, Backend). Added instructions to README.
- [2026-01-12 04:00] Web Preview Port Fix: dev.nix 强制使用 $PORT 和 0.0.0.0，修复 Starting server 卡死。
- [2026-01-12 04:05] Web Preview Static Fix: 切换到 flutter build web --release + npx serve 静态托管模式，解决 flutter run web-server 启动不稳定问题。
## WS Reconnect Fix Mon Jan 12 07:12:07 AM UTC 2026
- Fixed wsUrl derivation (http->ws, https->wss, no trailing slash).
- Added Debug UI Overlay in WorldScreen (BaseURL, Health, WS Status, Room info).
- Auto-reconnect enabled on start.
- Verified WS connection via node script (WS_OPEN_SUCCESS).

## WSS E2E Fix Mon Jan 12 07:23:27 AM UTC 2026
- Enforced strict WSS derivation in app_config.dart (https -> wss, path=/ws).
- Added comprehensive Debug UI in main.dart (BaseURL, Health, WS Status, Last Error, Retry Button).
- Created tools/wss_probe_preview.js for verifying connection logic.
- Verified local connection (ws://localhost:8080/ws) -> SUCCESS.
- Ready for Preview environment verification (UI will show derived wss://.../ws).

## S3.1 Web WS Real Fix Mon Jan 12 07:35:17 AM UTC 2026
- Disabled Service Worker in dev.nix (build web --pwa-strategy=none).
- Added SW unregister logic in web/index.html as safety net.
- Switched web preview server to http-server with -c-1 (no-cache).
- Updated app_config.dart to force WSS on HTTPS pages.
- Verified local connectivity with tools/wss_probe_preview.js.

- Backend URL derivation fixed (9000 -> 8080) for Cloud Workstations/IDX
- Health check force-directed to backend 8080
- WS connection force-directed to backend 8080 wss://
- Web Preview Command Updated: Force Rebuild + Timestamp + No-Cache + No-PWA
- UI now displays BUILD_ID for version verification
