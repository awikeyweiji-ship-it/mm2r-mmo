# R4 Offline Dual Installer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task. TDD per task.

**Goal:** Deliver one Chinese technology-themed Android APK that embeds both user-supplied installer archives and guides system-confirmed installation of Clash then ChatGPT split APKs.

**Architecture:** Preserve existing native Java Android UI/VPN gate. Use pure Java split selection, a stream-based APKM verifier/PackageInstaller component, and minimal updates to main state logic. Android security consent stays visible.

**Tech Stack:** Java 17; Android SDK35; Gradle 8.9; Python validation; GitHub Actions artifact.

**Spec:** docs/superpowers/specs/2026-10-13-r4-offline-two-package-design.md

## Global Constraints
- Never modify/re-sign nested Clash or ChatGPT package content.
- SHA256-match both supplied files and select only compatible split APKs.
- Real system installer confirmation and VPN user consent must remain intact.
- ARM64, Android 12L+ device requirement; Chinese UI and one-primary-action home.

## Review Focus
- Device SDK <32 must not submit ChatGPT install session.
- Missing/incompatible ABI split must fail before installation.
- Wrong APKM hash or corrupted archive must never commit.
- ChatGPT session includes exactly one base and at most one ABI and density split, no duplicate split names.
- After users cancel installation, home state and primary action should remain retryable.

### Task 1: Split selector
**Files:** Create `app/src/main/java/com/jeyu/clashgptstarter/ChatGptSplitSelector.java`; `tests/ChatGptSplitSelectorTest.java`, update `tests/run_core_tests.sh`.
- [ ] Write failing test for supported ARM64, density and localized selection; sdk/abi/missing/duplicate negative cases.
- [ ] Run tests (RED).
- [ ] Implement selection and re-run tests (GREEN).

### Task 2: Offline bundle session
**Files:** Create `app/src/main/java/com/jeyu/clashgptstarter/BundledChatGptInstaller.java`, update `ChatGptSourceInstaller.java`, `app/build.gradle`, `tests/test_dual_install_contract.py`, `tests/test_apkm_archive.py`.
- [ ] Tests require APKM hash, PackageInstaller.Session stream operation, and no silent installer.
- [ ] Run RED then implement; preserve single .apk import fallback.
- [ ] Run GREEN full core and Python tests.

### Task 3: Guided installation UI
**Files:** Modify `FlowDecision.java`, `UiCopy.java`, `MainActivity.java`, `InstallResultReceiver.java`, tests `FlowDecisionTest.java`, `UiCopyTest.java`.
- [ ] RED: primary step must be Clash → ChatGPT → VPN → HTTPS probe → ChatGPT.
- [ ] GREEN: modify state logic and UI to expose built-in ChatGPT with explicit Android consent.
- [ ] Run all tests plus static checks.

### Task 4: Build, inspect and deliver
**Files:** `app/src/main/assets/chatgpt-original.apkm` (user original), `README.md`, CI script, QA report.
- [ ] Verify archive hash and internal entries; build using real Android SDK/Gradle; verify apksigner and zipalign.
- [ ] Check generated APK contains BOTH original assets unchanged and is a valid APK.
- [ ] Save final APK, source and QA to user Library; report any unrun real-device checks.
