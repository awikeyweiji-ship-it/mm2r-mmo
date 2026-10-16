package com.jeyu.clashgptstarter;

/** Account-safety guardrails, not an assurance of eligibility or a ban-prevention mechanism. */
public final class ClaudeSafetyPolicy {
    /** Never silently cycle IPs after an auth/rate/geo restriction. */
    public static final long COOLDOWN_MS = 30 * 60 * 1000L;
    public static final String OFFICIAL_REGIONS =
            "https://support.claude.com/en/articles/8461763-where-can-i-access-claude";
    public static final String OFFICIAL_TERMS = "https://www.anthropic.com/legal/consumer-terms";
    public static final String OFFICIAL_PLAY = "https://play.google.com/store/apps/details?id=com.anthropic.claude";

    private ClaudeSafetyPolicy() {}

    public static boolean requiresEligibilityConfirmation(ServiceTarget target) {
        return target == ServiceTarget.CLAUDE;
    }

    public static boolean canLaunch(ServiceTarget target, boolean selfConfirmed) {
        return !requiresEligibilityConfirmation(target) || selfConfirmed;
    }

    /** Claude defaults to current user-selected route: no automatic node/IP churn. */
    public static boolean shouldAutoSelect(ServiceTarget target, boolean enabled) {
        return enabled && target != ServiceTarget.CLAUDE;
    }

    /** These codes never mean a safe or authorized login path. */
    public static boolean isRestrictionResponse(int code) {
        return code == 401 || code == 403 || code == 429 || code == 451;
    }

    /** Monotonic time guards: a reboot or bad clock cannot create endless lockouts. */
    public static boolean inCooldown(long nowElapsed, long lastRestrictedElapsed) {
        return lastRestrictedElapsed > 0 && nowElapsed > 0 && nowElapsed >= lastRestrictedElapsed
                && nowElapsed - lastRestrictedElapsed < COOLDOWN_MS;
    }
}
