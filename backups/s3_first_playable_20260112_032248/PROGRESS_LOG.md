
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
- 验证：自动化双客户端交互测试通过。

## 20260111_150000 - S1_visual_true_mmo
- 状态：完成。实现了真实可视化同屏移动 + 最小观感（插值/节流/名字标签）。
- 广播节流：Server端实现10Hz Tick广播，每10秒输出统计。
- 客户端插值：Client端实现last->target lerp线性插值，移动平滑。

## 20260111_153000 - S2_netcore_scalable
- 状态：完成。实现了权威校验 + 限频 + AOI 网格 + 增量(delta)同步 + bot 压测脚本。
- 服务器改造：AOI 九宫格广播，Delta Sync 增量同步，速度/频率边界检查。

## 20260111_160000 - W2_persistence_min
- 状态：完成。实现了“最小持久化（文件快照）”。
- 核心功能：玩家下线再上线（通过 playerKey）可恢复位置、名称、颜色和房间。
- 存储实现：server/data/world_state.json，采用原子写 + 1.5s 节流防刷 IO。
- 验证：w2_persistence_test.js 自动化测试通过（Client 1 移动，Client 2 登录可见恢复位置）。
- 兼容性：保持 S2 的 AOI/delta 逻辑不回退。
