# R3.0 Dual Installer Implementation Plan

> **For agentic workers:** use superpowers:executing-plans, test driven and verified.

**Goal:** One Chinese guided installer with an embedded Clash APK and verified optional ChatGPT APK import/embedded path.

**Architecture:** Extend R2 MainActivity's installation page and lifecycle, use PackageInstaller for APKs and Google Play for official ChatGPT distribution, keep the VPN gate intact.

**Tech Stack:** Java 17, Android SDK 35, Gradle 8.9, no AndroidX dependency.

**Spec:** docs/superpowers/specs/2026-10-13-dual-app-install-wizard-r3.md

## Global Constraints
- Do not bypass install prompts or VPN permissions.
- Do not bundle unsigned/unverified ChatGPT binary or pretend it is official.
- Keep original Clash APK byte-identical and original application namespace.
- Preserve R2 design and existing core tests.

## Review Focus
- Wrong package in picker must fail before PackageInstaller submission.
- Untrusted APK certificate must fail even when package id matches.
- API26 to 35 signature code paths compile correctly.
- Canceled picker or refused unknown-source permission shows recovery action.
- Intent callback indicates which app was installed, and does not silently start the next installer in background.

### Task 1: Pure validation and UI state tests
- [ ] RED add signature-hash validation tests and integration static contracts.
- [ ] GREEN write minimal verifier and guide state.
- [ ] Run full suite.

### Task 2: APK input installer
- [ ] RED add contract test for streaming Uri and signer validation.
- [ ] GREEN implement picker intake, cert/package validation, user-confirmed PackageInstaller session, safe cache cleanup, and outcome message.
- [ ] Run full suite.

### Task 3: Simple setup UI and state recovery
- [ ] RED add UI tests for two acquisition paths and state-specific feedback.
- [ ] GREEN integrate picker and optional bundle support into R2 UI; keep Google Play primary and existing VPN gate.
- [ ] Run full suite.

### Task 4: Build and release QA
- [ ] Build signed APK, validate manifest, installer assets and signing.
- [ ] Record verification boundary and distribute with source/report.
