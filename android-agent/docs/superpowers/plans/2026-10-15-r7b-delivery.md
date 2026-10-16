# R7B Reliable Long-Running Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Tasks run RED→GREEN with a durable ledger.

**Goal:** Finish working R7B vertical slice: Android R7 signed APK + independent PC Web/API Worker handling durable files, checkpoints, and strict model policies.

**Architecture:** Keep the existing R6/R7B Android launcher as-is; locally bundle its three original assets and sign with the prior R6 certificate. Build a separate FastAPI SQLite-WAL development server with a restartable leased Worker, append-only events, immutable sources, context rollover and fail-closed validations, used by both Android and PC web.

**Tech Stack:** Android Java/Room; FastAPI/Python/SQLite; vanilla HTML/CSS/JS (no build tool); pytest/httpx; Java apksigner and zipalign.

**Spec:** `docs/R7_DESIGN.md`, approved 2026-10-14.

## Global Constraints
- No fake research conclusions: without a provider API model configuration, document indexing is real but model inference is unavailable.
- Authorization required on every /v1 endpoint except health; HTTPS/TLS required for remote Android; local HTTP only for 127.0.0.1 development.
- Preserve all original vendor APK bytes and their SHA256, never re-sign vendor APKs.
- STRICT model may not fall back on errors or unknown model IDs; actual-provider implementation info remains UNKNOWN when not supplied.
- An absent rule, incomplete proof or missing source shall never yield PASS or publish success.
- Async jobs must resume from persisted cursor, not chat memory; duplicate submissions idempotent.
- Failed side-effectful operations must not be silently retried.

## Review Focus
- Duplicate source upload while original blob is corrupted → 409, no false success.
- Requesting another project's source ID → reject before creating task.
- Expired Worker lease following SIGKILL → a different Worker continues from committed cursor without duplicate events.
- Context rollover at >65% budget → new session ID, bounded source slice, source hash retained.
- Mismatched model response, missing rules/quote, malformed source, bad authorization → fail closed with distinct issue codes.

### Task 1: Backend source, events, idempotency (TDD)
**Files:** `server/agent/store.py`, `server/agent/api.py`, `server/tests/test_api.py`.
- [ ] Write tests for authenticated project creation, source import and hash, duplicate corruption, revision conflicts, cross-project privacy.
- [ ] Confirm expected RED failures.
- [ ] Implement transactional WAL store and FastAPI contracts matching `R7SyncClient.java`.
- [ ] Run tests to GREEN and commit/stage.

### Task 2: Durable Worker, context and validation (TDD)
**Files:** `server/agent/worker.py`, `server/agent/quality.py`, `server/tests/test_agent.py`.
- [ ] Write failing tests for lease crash recovery, duplicate idempotency, 12 context rotations, strict model pause, evidence-gated validation, frozen rules.
- [ ] Implement deterministic processing, model adapter only for configured profiles, context budget with 65% pre-rotation and WAL checkpoints.
- [ ] Test RED→GREEN plus full suite.

### Task 3: Functional PC Web Client (TDD)
**Files:** `server/web/index.html`, `server/web/app.js`, `server/web/style.css`, `server/tests/test_web.py`.
- [ ] Write page/API UI contract tests with no inert buttons and bearer auth.
- [ ] Implement real browser project/task/file/validation/event screens; cache only draft text, not credentials.
- [ ] Headless Chromium desktop/mobile interactive acceptance and screenshots.

### Task 4: APK package/signature (verification)
**Files:** existing Android shell, prior R6 APK assets, R6 private signing key; delivery bundle.
- [ ] ZIP-rebuild shell with all three asset blobs and original checksums; zipalign and v2/v3 sign.
- [ ] Verify APK via apksigner; compare outer signing fingerprint with R6; full archive CRC; Android API target and Room classes preserved.
- [ ] Perform local/static Android validation, document missing real device and model provider prerequisites.

### Task 5: Release package
- [ ] Zip server, Windows/Linux run instructions, tests, README, health-check scripts and APK; no secrets/keystores, no intermediate binaries.
- [ ] Persist final artifacts to Library; check mutation statuses; report verified results and open gaps.
