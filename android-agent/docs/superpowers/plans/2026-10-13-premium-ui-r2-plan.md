# Premium UI R2.0 Implementation Plan

> Implement inline with superpowers TDD and verification.

**Goal:** Make the existing Clash/ChatGPT installer launcher visually refined and usable by novice Android owners.
**Architecture:** Pure Java UiCopy state model supplies localized status copy; MainActivity binds a two-screen Android Views UI; TechBackdropView supplies passive sci-fi illustration; existing installer/network safety code stays untouched.
**Tech Stack:** Java 17, Android SDK 35, Gradle 8.9.
**Spec:** docs/superpowers/specs/2026-10-13-premium-ui-r2-design.md

## Tasks
1. Add `UiCopy.java` and `tests/UiCopyTest.java` with RED→GREEN test cases for all statuses and actions; keep `FlowDecision` as the sole action gate.
2. Create visual `TechBackdropView.java` and rebuild `MainActivity` UI into home + settings, add Chinese instructions, responsive insets and accessibly labeled buttons, preserve install/connection/open implementations.
3. Add static contract tests for single main CTA, navigation/settings workflow, icon accessibility, 48dp controls and minSDK; validate Java syntax and the 30 original tests.
4. Build/sign Android 2.0.0 APK on supported host; validate hash and if possible Android emulator layout/interactions; package and deliver APK + evidence.

## Risk controls
- Refuse green “ready” for merely present VPN.
- Do not silently launch another app from background.
- Do not claim first-time permissions can be automatically granted.
- Do not mix installation and setup on the main page.
