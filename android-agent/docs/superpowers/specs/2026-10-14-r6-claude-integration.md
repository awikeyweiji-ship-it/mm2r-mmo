# R6 Claude Android integration and account-risk-aware mode

**User outcome:** On the R5 Android launcher, a novice can select ChatGPT, Gemini, or Claude and use the same Chinese setup and launch screens. Add official Claude install from Play. Do not promise ban-proof behavior, sideload unverified Claude, or silently approve account/region eligibility.

## Source of truth and constraints
- Existing R5 source, app ID `com.jeyu.clashgptstarter` and R5 release keystore are preserved; version bumps to 6.
- Official Claude Play package is `com.anthropic.claude` and authoritative install channel `https://play.google.com/store/apps/details?id=com.anthropic.claude` (Anthropic help, July 9, 2026).
- Anthropic says account creation in unsupported locations, usage-policy and terms violations may trigger suspension; supported-region requirements apply. No automatic VPN node selection implies compliance.
- Original Clash, ChatGPT APKM, Gemini assets remain byte-for-byte identical. Claude binary is NOT provided and must NOT be invented or downloaded from unverified mirrors; the launcher provides the official installation action instead.
- Android installation dialogs and VPN consents remain user-driven.

## UX
- Home has three equal-width, accessible, labelled target choices (ChatGPT/Gemini/Claude).
- Claude setup card clearly states official Google Play install only; no falsely labelled bundled Claude APK.
- Claude security/risk notice on first use: user must affirm current eligible region/terms. Opens official Anthropic supported-region help on demand. This declaration is stored locally and does not substitute real checks.
- Claude safe direct-first connection: in supported regions, never require a VPN for Claude. If VPN is already active, explain it affects Claude and request explicit consent to continue; never scan or rotate VPN nodes for Claude, even when smart routing is enabled for other targets. A site check is reachability only and cannot establish login/account eligibility.
- Explicit warnings for HTTP 401/403/429/451 or human-check challenges. Suspend repeated Claude probes for a short cooldown and explain; manual responsible recovery only. Do not rotate endpoints to bypass restrictions.

## Verification
- Test-first: package and target selection, policy confirmation, cooldown, stable-mode auto selection suppression, UI static contract, existing target behavior.
- Fresh R6 Java tests + existing Java tests, existing Python contracts and new R6 UI/safety contract.
- CI Android 35/Gradle full build, signer and ZIP alignment checks.
- Assemble final shell with unchanged R5 bundled original files, use same R5 signing keystore, verify ZIP, signature and all binary SHA-256s. No false device/emulator install claim.
