# R6 Claude Android integration Implementation Plan

> For agentic workers: use superpowers:executing-plans and test-driven-development, verify before completion.

**Goal:** Add official Claude install/launch and low-risk stable-route UX to the R5 tri-app gateway.
**Architecture:** Extend the existing target enum, add a pure policy to centralize Claude risk decisions, modify MainActivity selectors/setup/launch checks and network gating without affecting bundled ChatGPT/Gemini/Clash installers. Build existing Android project and reassemble with R5 immutable assets and original signing identity.
**Tech stack:** Android Java 17 SDK 35, Gradle 8.9, Python archive QA.
**Spec:** `docs/superpowers/specs/2026-10-14-r6-claude-integration.md`.

## Global constraints
- No promise of no bans; no fake region detection, identity spoofing, login automation or automatic churn across regions.
- Keep package ID, minimal Android API and all three original bundled binaries.
- Official Play link for Claude. No bundled Claude APK is available.
- Don't publish keystores or passwords to GitHub.

## Review Focus
- Selecting Claude clears previously validated Gemini/GPT state; tests target switching.
- Claude app missing: offer genuine Play listing; never trigger Gemini installation.
- Claude with automated selection enabled: no node changes; start directly without VPN requirement; when VPN already active, warn before opening.
- Claude 403/429: cooldown and clear risk message; no repeated automatic retry.
- Existing R5 functionality remains intact and signing identity unchanged.

### Task 1: Target and risk rules
- [ ] Write failing tests for Claude enum, decision and cooldown.
- [ ] Observe expected failures.
- [ ] Implement minimal ServiceTarget and ClaudeSafetyPolicy.
- [ ] Run Java suite.

### Task 2: Android launch and install UX
- [ ] Write failing static UI/manifest tests.
- [ ] Observe expected failures.
- [ ] Add Claude selector, official Play channel, consent, stable-mode wording and check flow.
- [ ] Run all suites.

### Task 3: Release and signed artifact
- [ ] Build using official Android SDK on isolated branch.
- [ ] Reuse original binary assets with SHA validation and signing cert.
- [ ] Verify final APK and preserve source/QA evidence.
- [ ] Save deliverables to Library.
