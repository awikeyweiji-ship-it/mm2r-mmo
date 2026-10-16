# R8 Android Persistent Agent Integration Plan — 2026-10-16

## Goal
把 R7B 的持久 Agent 能力真正集成进此前的 Android「AI 智能启动助手」，让手机成为本地永久研究仓与控制台，复杂长任务由可持久运行的 Agent 服务执行。

## Non-negotiable
- 保留 Clash Meta / ChatGPT / Gemini / Claude 原有启动与安装流程。
- 不接管、窃取或修改第三方官方 App 的账号、Cookie 或私聊上下文。
- 本地原文、任务、事件、检查点和验证结果持久保存；远端同步显式授权。
- STRICT 模型默认不降级；同模型跨 Provider 故障转移可以发生，但不能静默换模型。
- 模型不能写可信步骤号、来源 ID、规则状态；这些字段由程序生成。
- 未验证不等于 PASS；任何关键规则失败阻断后续可信推进。
- 上下文轮换必须恢复必要工作记忆与原文依据，而不是只恢复 step/cursor。
- 远端任务必须能在 App 退出/重启/网络断开后继续；手机不是长任务 Worker。
- 8GB/16GB 本地模型为可选，不得取代远端严格验证。

## Implementation
1. Android Room v2: add execution snapshot, memory, evidence and local result tables with manual migration.
2. Android R8 Agent client: create task, upload authorized sources, poll full snapshot/events, resume, validate, fetch/export.
3. Android Research Console: high-tech but simple UI; one primary action, separate execution/context/data/rule/reasoning status.
4. Integrate console into existing MainActivity and preserve R7 project vault/backup.
5. Add optional local-model manager contract (GGUF metadata/import) without bundling multi-GB weights into the main APK.
6. Add self-hosted research gateway under `agent-service/`: authenticated REST, append-only SQLite/Postgres-compatible persistence, lease worker, context compiler, deterministic validator, strict model adapter, idempotency.
7. Add test fixtures covering context rollover, rule-before-next-step, metadata spoofing, source mismatch, unknown model, crash recovery, duplicate submit, and offline local persistence.
8. Build real Android debug APK through GitHub Actions, run static contracts + Gradle compile, then package delivery artifact. Release signing is only done if the private signing material is already available to the build environment.

## Architecture
Android -> HTTPS Agent API -> Durable Task Worker -> Context Compiler -> Strict Model Gateway -> Independent Validator -> Persisted Event/Memory/Result Store.
Large source/result payloads are stored by reference, not embedded in workflow/event state.
