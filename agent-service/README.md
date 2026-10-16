# R8 Durable Research Agent Service

这是安卓集成 App 的持久后台。Android 负责本地资料仓、任务控制和状态查看；复杂长任务在服务端继续运行。

## 核心保证
1. 原文按 SHA-256 内容寻址保存，任务创建时冻结 source_ids。
2. 每一步只把当前原文片段和必要的 VERIFIED memory 放入模型上下文。
3. 上下文达到安全预算时创建新的 context_epoch；不靠无限增长的聊天历史。
4. 未验证模型正文不能进入 VERIFIED memory。
5. 规则在结果进入后续依赖前立即执行；失败不推进任务游标。
6. source_id、locator、step、rule state 等可信元数据由程序生成，模型不能覆盖。
7. STRICT 模式不跨模型降级；没有实际模型 ID 或 ID 不一致时暂停。
8. 网络请求状态不确定时保持 IN_FLIGHT，显式恢复前不自动重复调用。
9. 没有规则、证据不足或推理冲突不能发布 VERIFIED report。
10. quality_state 单独记录为 NOT_EVALUATED，不能用它解释工程故障。

## 启动
Python 3.12+；安装 requirements.txt；复制 .env.example 并设置 AGENT_ACCESS_TOKEN。
本机开发可设置 AGENT_ALLOW_INSECURE_LOCAL=true；公网部署必须使用 HTTPS 反向代理。
设置 MODEL_API_BASE 与 MODEL_API_KEY 后才会真正调用模型；未设置时任务仍可创建，但会暂停在 MODEL_UNAVAILABLE。
运行 python run_server.py。若需要独立 Worker，将 AGENT_AUTOSTART_WORKER=false，然后另起 python run_worker.py。

## 生产架构
HTTPS 反向代理 → Agent API → 持久任务 Worker → Context Compiler → 严格 Model Gateway → Independent Validator → SQLite/PostgreSQL + Object Store。
当前内置 SQLite-WAL 是单节点、易部署的持久实现。高可用阶段将 store 层换成 PostgreSQL，并把 Worker 状态机接入 Temporal Durable Execution。
LiteLLM 可作为 Model Gateway：为同一 canonical model 配置多个 deployment 可以做同模型 provider failover；不要给 STRICT 研究任务配置跨模型 fallback。

## Android
App → 长期任务中枢 → 新建长期 Agent 任务。填写目标、可选规则和精确模型 ID。
运行前 App 会明确确认当前项目资料是否上传到你的 HTTPS Agent 服务。
退出 App 不会终止远程任务；重新打开后 App 从服务端重新核对 snapshot 与事件，再写回手机 Room。

## 数据
data/agent.sqlite3：项目、任务、事件、记忆、证据、模型调用元数据。
data/objects：原始资料。
data/texts：解析后的检索文本。
data/results：模型调用结果、草稿和验证报告。
定期备份整个 data 目录；不要把 token 或上游模型密钥提交到仓库。

## API
GET /health
POST /v1/sync/push
POST /v1/projects/{project_id}/sources
POST /v1/tasks
GET /v1/tasks/{task_id}
GET /v1/tasks/{task_id}/events?after_seq=N
POST /v1/tasks/{task_id}/resume
POST /v1/tasks/{task_id}/validate
所有 /v1/* 都需要 Bearer token。

## 边界
不会读取第三方 App Cookie、密码或私人聊天历史；不会注入或修改 ChatGPT、Gemini、Claude 官方 App；不会绕过 Android 安装确认、VPN 授权或服务商地区限制。