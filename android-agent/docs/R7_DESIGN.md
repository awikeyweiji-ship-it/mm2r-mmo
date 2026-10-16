# AI Gateway R7.0 — Android / PC Web 持久 Agent 与上下文可信性系统

版本：Draft for approval · 2026-10-14  
底座：R6.0 `com.jeyu.clashgptstarter`，Java / Android SDK 35，保留 Clash、ChatGPT、Gemini、Claude 原有启动/安装入口。

## 0. 目标与边界

本系统是**通用研究与长任务工作台**，不是竞彩或任何特定行业的研判器。用户的例子只用来定义通用故障模式，不写入任何固定的领域判定逻辑。

**必须解决的可测问题：**
1. 单次对话超出上下文窗口前自动建立可信检查点和新执行窗口，连续任务不依赖模型记住先前内容。
2. 手机资料在 App 本地有持久数据库和原始文件，离线可读；重启进程、换会话不丢。可选加密导出/恢复与跨端同步。
3. Android 与 PC 浏览器同一个项目/任务/事件记录、同一执行状态；离线及网络恢复可正确合并。
4. 复杂任务由持久后台执行；App 退出、网络中断和后端 Worker 重启可恢复，不重复不可逆外部动作。
5. 模型性能不足、上下文污染、数据缺失、工具错误、业务规则误执行、验证失败分别记录；不能混成「模型下降」。
6. 规则验证由程序独立执行；无适用规则时标记「未验证」，不可由模型写一个 PASS 冒充完成。
7. 8GB / 16GB 级安卓手机可选下载小模型，处理分类、局部索引、摘要草稿、隐私敏感的本地操作；不让小模型充当最终数学/事实裁判。

**不是目标 / 无法保证：**
- 无法令外部 ChatGPT/Claude/Gemini 消费者 App 的原生对话窗口无限扩展、透明读写其全部聊天历史或强行切换内部模型。
- 不通过盗取 Cookie、模拟登录、未授权抓网页、注入其他 App、绕过订阅/服务条款实现「官方消费者网页版反代」。
- 不能保证任何大模型永不推理错误、每种领域无需规则配置就能 100% 自动判真伪、每台安卓机都能流畅运行 1B+ 模型。
- 「长期」意指有本地持久化与恢复/备份能力；设备损坏、用户卸载应用或删除所有备份之后不能承诺数据依然存在。

## 1. 现有 R6 代码基线（已核查）

- 原生 UI 主要在 `app/src/main/java/com/jeyu/clashgptstarter/MainActivity.java`；用户偏好、选定 AI、网络参数使用 `SharedPreferences`。
- `AndroidManifest.xml`：`INTERNET`、`ACCESS_NETWORK_STATE`、安装包权限及精确包可见性；`android:allowBackup="false"`。
- 已有 `ControllerSecrets` 采用 Android KeyStore 保存 Clash 控制密钥，优先复用该加密能力，但项目数据需要独立密钥与密钥轮换机制。
- 缺少项目/任务/附件数据库、跨端通信协议、持久执行队列、Agent Checkpoint 和本地 GGUF 推理引擎。
- R7 不修改已验收的三个 APK 资产、Clash VPN 授权逻辑与现有服务选择行为；添加独立模块和导航入口。

## 2. 用户体验：一个项目，两种客户端、多个执行会话

### Android 首页

原有「ChatGPT / Gemini / Claude」启动器保留。在首页增加「我的 AI 项目」入口：
- **新建任务**：输入目标、可选导入文档、选择是否发送到云端、选择严格模型/授权回退。
- **继续上次任务**：离线时显示最近的本地检查点；连网后显示后端实际任务状态，而非 UI 幻觉。
- **项目资料**：附件、规则、已验证事实、讨论/任务历史、错误记录，支持搜索与本机加密导出。
- **本地助手（可选）**：下载安装轻量 GGUF、执行快速分类/摘要草稿；屏幕锁定/低内存/发热时暂停。
- 默认只突出一个动作：新建任务或继续任务；细节收在「任务证据」「设置」。

### PC Web

响应式 Web 工作台（不是伪装 ChatGPT 官方网页）：项目树 + 正在执行任务 + 证据/校验抽屉。与 Android 共享 project_id；支持流式增量、断网重连、审查错误和导出。

浏览器 `IndexedDB` 仅存最近草稿/离线缓存；真正的跨端持久性来自服务端 PostgreSQL 和经过用户授权的同步，PC 如需长期离线资料，可后续提供 Tauri/SQLite 桌面客户端。

### 官方消费者应用与自有 Agent 的双通道

1. **官方应用模式**：仅负责合法启动对应 Android App / 打开服务商官方网页，支持用户主动用系统分享、文件导入或允许的导出方式加入项目；不宣称能自动延续其私人聊天窗口。
2. **托管 Agent 模式**：R7 自有对话工作台，经用户授权的官方 API 或兼容 API/中转协议调用模型。由 R7 控制输入切片、持久任务、续接和日志。此模式才能实现自动内部新会话续接。

## 3. 三层系统架构

```
[Android R7 Native]       [PC Browser PWA]
  ├─ Room SQLite            ├─ IndexedDB（草稿缓存）
  ├─ Keystore + AES-GCM      └─ HTTPS/SSE
  ├─ 文件与证据索引                 │
  ├─ GGUF Lite Agent（可选）       │
  └───────── HTTPS + 增量同步 ─────┤
                                  ▼
                       [R7 API + Auth Gateway]
                         ├─ 持久事件/任务 API
                         ├─ Model Gateway（严格型号）
                         ├─ Context Budget Manager
                         ├─ Evidence/Rule Validator
                         ├─ Sync/Backup API
                         └─ Task Queue / Worker Lease
                                  ▼
                      [LangGraph Agent Workers]
                         ├─ Planner / Task Runner
                         ├─ Evidence Reviewer
                         └─ Independent QA Gate
                                  ▼
                        [Postgres + Blob Store]
                          checkpoints / events
                          source objects / hashes
```

**MVP 技术选型：** Android 保持 Java + AndroidX Room 2.8.5（`annotationProcessor`）；`llama.cpp` JNI/Android 示例提供本地 GGUF 引擎；PC React/Vite PWA；服务端 Python FastAPI + LangGraph + Postgres checkpointer / PostgreSQL；Model Gateway 先实现 OpenAI-compatible 适配器，其他服务商按原生协议接入。后续接入 LiteLLM（显式关闭未经授权的 fallback）、Blob 存储和 OpenTelemetry。不要第一版同时装完所有中间件。

## 4. 本地持久化与加密

### Android Room 表（最小集合）

| 表名 | 关键字段与规则 |
|---|---|
| `projects` | `project_id UUID`, `title`, `created_at`, `updated_at`, `sync_rev`, `pinned_model_policy` |
| `source_objects` | `source_id`, `project_id`, `sha256`, `mime`, `bytes`, `local_uri`, `remote_object_id nullable`, `origin`, `imported_at` |
| `facts` | `fact_id`, `source_id`, `locator`, `value_json`, `status`, `validator_version`, `sha256` |
| `rules` | `rule_id`, `project_id`, `version`, `scope`, `json_schema`, `effective_at`, `status` |
| `tasks` | `task_id`, `project_id`, `status`, `step`, `remote_run_id`, `checkpoint_id`, `last_verified_rev` |
| `events` | `event_id`, `task_id`, `seq`, `type`, `payload_hash`, `created_at`, `origin_device_id`, `revision` |
| `model_calls` | `call_id`, `provider`, `requested_model`, `actual_model_if_reported`, `tokens`, `error_type`, `fallback_authorized` |
| `validation_results` | `check_id`, `task_id`, `source_hash`, `rule_version`, `pass_fail_unknown`, `severity`, `location`, `diagnostics` |
| `sync_outbox` | `op_id`, `project_id`, `payload`, `created_at`, `ack_at`, `retry_count` |

本地附件放在 `filesDir/projects/<UUID>/objects/<sha256>`，采用内容寻址和 SHA256 去重；SQLite 存索引和引用，不将大型 PDF/视频整份塞进聊天输入。附件用文件级 AES-GCM 加密，密钥交由 Android Keystore 保护；数据库本身采用可维护的全库加密实现或敏感列加密，并需要实测升级/迁移。

**永不丢失的工程边界：** SQLite 使用 WAL 与事务；元数据原子提交后再标记已导入。用户主动启用加密导出/备份，包含可恢复的独立恢复密钥（不能只依赖不可导出的设备 Keystore），定期校验恢复。不同设备冲突不采用静默最后写入覆盖源证据；按事件追加及冲突显式审查。

默认不自动上传敏感原文到第三方模型；运行远程 Agent 时先说明将会把哪些资料发送到哪类服务。单纯 E2EE 存储与可阅读原文的云端 Agent 不能同时无条件成立，应明确选择执行时授权解密的路径。

## 5. 持久 Agent / 自动新会话

状态机：`CREATED → INGESTED → PLANNED → RUNNING → CHECKPOINTED → VALIDATING → READY | NEEDS_REVIEW | FAILED`；断网/崩溃标记 `INTERRUPTED`，恢复只走 `checkpoint_id` 和 `event_id`。

每个任务执行至少有：`project_id, task_id, thread_id, checkpoint_id, job_step, source_hashes[], rule_version, model_policy, last_event_seq, pending_tools[], unresolved_issues[]`。

**上下文策略（可配置，默认）：**
- 每次模型调用前计算模型可用窗口、已用 token 的估计/实际值、预留输出与工具响应预算。
- 预计使用约 55% 开始评估分段；到 65–70% 时写验证检查点并创建新的内部执行窗口；超出安全预算则拒绝继续当前调用，不赌 truncation。
- 原文、原始工具输出、已验证事实不依赖模型摘要保存；摘要只是索引和快速恢复提示。
- 新窗口通过 `source_hash + locator` 按需检索证据，而不是贴整段历史；恢复前做规则版本、事件序号、任务所有权和来源哈希校验。
- LangGraph `thread_id` / Postgres checkpointer 执行图状态恢复；服务商 API conversation/compaction 为可选加速，不作为系统权威源。

**长时间工作保活：** Cloud Worker 将任务 checkpoint 提交数据库后再 ACK，任务锁有租约和心跳；崩溃恢复仅重试幂等步骤。发送邮件、购买、删除等不可逆外部动作必须带 `idempotency_key`，需要用户显式批准的动作不得自动执行。

## 6. 规则层与模型质量层独立

每条输出结论依赖 `source_id + locator + transform_id + rule_version + proof_record`。通用校验器只检查用户明确配置的 JSON Schema、日期/类型/数学不变量、引用完整性、文档一致性约束。无规则或语义无法确定时返回 `UNKNOWN/NEEDS_REVIEW`，**不能返回 PASS**。

独立故障标签：
- `CTX` 上下文预算或恢复漂移
- `DATA` 原始资料缺失/矛盾/过期
- `EXEC` 工具超时、请求失败、幂等或队列问题
- `RULE` 结构化业务规则违反
- `REASON` 模型推理有证据缺口/前后矛盾
- `QUALITY` 模型在基准测试上的表现下降
- `OUTCOME` 结果性预测/决策表现，不推断为技术 BUG

一项任务可以同时具有多个标签，但报表必须分列状态、根因与证据。任何高严重度发布阻断失败都会拒绝 `READY`。

模型降级模式：`STRICT` 默认；请求指定模型不可用、实际模型报告不匹配或上下文超限时**暂停，不自动切低模型**。`APPROVED_FALLBACK` 仅在用户明确授权且执行记录完整时可回退。服务商不返回底层精确模型时写 `UNKNOWN_PROVIDER_IMPLEMENTATION`，不假称检测到了真实模型。

## 7. 本地模型（可选安装，无需内置进 144MB 主 APK）

| 手机级别 | 第一选择 | 模型大小（参考 GGUF Q4_K_M） | 任务范围 |
|---|---|---:|---|
| 8GB 内存 | Qwen3-0.6B | 约 0.48GB 模型文件 | 意图分类、片段摘要草稿、标签与初级文本整理 |
| 16GB 内存 | Qwen3-1.7B | 约 1.28GB 模型文件 | 更长文段整理、离线问题分解、上下文摘要草稿 |

模型大小**不等于运行内存占用**：KV cache、上下文长度、系统和其他 App 也占内存。默认 CPU 线程低优先级、ctx 2048 起测，不在无真机验收的情况下宣称支持 4K/8K。运行时检查 `ActivityManager.MemoryInfo` 与温度、电量和应用可用内存；不足则停载/退回规则型离线搜索，绝不后台强驻内存。模型通过可验证 SHA-256、许可证记录和用户明确确认下载；原生 CLI/JNI 集成 llama.cpp。量化模型仅负责提议，不允许自己更新已验证事实、规则或发起付费远程任务。

从商业级产品工程考虑：**默认先不下载模型**，让 8GB 手机使用开箱即用的本地 SQLite FTS 搜索与程序型规则检查；愿意使用离线 AI 的客户再下载模型。

## 8. API 契约（最小必需）

- `POST /v1/projects` 创建项目及首次元数据；返回 UUID。
- `POST /v1/projects/{id}/sources` 上传文件，提交 MIME、SHA-256、内容长度、设备来源和用户隐私授权；幂等处理。
- `POST /v1/tasks` 以 `project_id, goal, source_ids[], model_policy, rule_version` 创建任务；返回 `task_id`。
- `GET /v1/tasks/{task_id}` 查询持久状态与最新 `checkpoint_id`。
- `GET /v1/tasks/{task_id}/events?after_seq=...` 分页事件；`GET /events/stream` 使用 SSE 增量推送。
- `POST /v1/tasks/{task_id}/resume` 恢复时验证 checkpoint、source hashes、last_event_seq；幂等。
- `POST /v1/tasks/{task_id}/validate` 返回 `PASS / FAIL / UNKNOWN` + 证据引用及失败原因。
- `POST /v1/sync/push` / `GET /v1/sync/pull?cursor=` 实现分段校验与断点续传。
- `GET /v1/projects/{id}/export` 产生授权的可移植项目包；导入要验证清单和 hash。

统一事件示例：
```json
{
  "event_id":"uuid", "project_id":"uuid", "task_id":"uuid",
  "seq":1024, "type":"CHECKPOINT_VERIFIED", "checkpoint_id":"opaque",
  "source_hashes":["sha256:<hash>"], "rule_version":"1.0.0",
  "requested_model":"provider/model-id", "actual_model":"unknown",
  "checks":{"source_integrity":"PASS","business_rules":"UNKNOWN"},
  "recoverable":true, "timestamp":"ISO-8601 UTC"
}
```

所有接口 HTTPS + 用户 OIDC/PKCE、短期会话 token、服务端最小权限；API 密钥通过加密服务端密钥仓或 Android Keystore 的显式 BYOK 保存，不接受将第三方网页登录 Cookie 作为长期认证信息。远程任务操作和模型调用有预算/速率限制，工具调用有域名/IP allowlist 与审批策略，防 SSRF 和 prompt injection。

## 9. R7 第一轮交付范围与验收

### R7-A（先完成，必须能生成测试 APK 和 PC Web）
1. Android Room 本地项目、文件原文入库与 Sha256；独立「AI 项目」入口、附件列表、搜索/导出/导入。
2. 可在 PC 浏览器打开同一项目的 PWA，访问后端的项目列表和任务事件，离线保存草稿。
3. FastAPI + PostgreSQL 实现项目、任务、事件、模型网关 stub；LangGraph checkpointer 执行有意义的两步可恢复任务（不要求接入真实付费模型）。
4. 可切断网络、杀 Android 进程、重启 Worker，回到同一 `project_id`，不丢 source SHA、事件序列和已完成步骤。
5. 完成端到端一次：安卓导入 5 MB 文档 → 后端排队 → PC 查询任务 → 在两端恢复并检查证据与状态。

### R7-B（能力升级，需单独验收）
6. 接入用户授权的模型 API；严格模型策略、调用成本和人工确认。
7. 真实长上下文自动续接：模拟 10 次以上新执行窗口，原始来源和事实不变。
8. JSON Schema/数学/引用 validator 与发布闸门：注入错字段、无来源、相互矛盾值，必须 FAIL 或 UNKNOWN，不可 PASS。
9. 任务崩溃与恢复、并发重复消息、丢包/断网/重发、数据冲突等混沌测试。

### R7-C（本地模型，独立 APK 分发能力）
10. 在一台实际 8GB Android 和一台 16GB Android 上记录加载峰值 RAM、推理时延、设备发热、OOM/进程杀死、离线正确率；失败则拒绝该模型配置。
11. 模型可取消下载、可删除、可重新验证 hash、可从模型模式退回无模型模式，卸载主 APK 不能当作数据备份策略。

**发布准则：** 安装/持久化/恢复/权限/用户隐私/规则独立检查/多设备冲突至少各有一条 RED→GREEN 回归测试；发布前真机视频/截图与版本/签名验收。不得把“模型回答说通过”当测试。

## 10. 决策/待审核点

架构默认：Android 原生 + PC 响应式 Web 共用自有 Agent 服务；**不把官方消费者网页聊天纳入反向代理或自动抽取链路**；本地模型默认选装。下一阶段才设计详细实现计划及动手修改 R6 工程，待此规格得到用户认可。

硬件环境、服务器域名和身份授权在施工时以可插拔配置提供；不要求先输入真实 API 密钥才能完成 R7-A 的模拟端到端验收。
