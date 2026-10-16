package com.jeyu.clashgptstarter;

/** Public service IDs; availability of a website does not prove login or feature entitlement. */
public enum ServiceTarget {
    CHATGPT("chatgpt", "ChatGPT", "com.openai.chatgpt", "https://chatgpt.com/"),
    GEMINI("gemini", "Gemini", "com.google.android.apps.bard", "https://gemini.google.com/app"),
    CLAUDE("claude", "Claude", "com.anthropic.claude", "https://claude.ai/");

    public final String id, label, packageName, probeUrl;
    ServiceTarget(String id, String label, String packageName, String probeUrl) {
        this.id=id;this.label=label;this.packageName=packageName;this.probeUrl=probeUrl;
    }
    public static ServiceTarget fromId(String id) {
        if (GEMINI.id.equals(id)) return GEMINI;
        if (CLAUDE.id.equals(id)) return CLAUDE;
        return CHATGPT;
    }
    public static boolean blockedResponse(int status) {
        return status == 403 || status == 451 || status == 429;
    }
    public static boolean networkReachable(int status) {
        return status >= 200 && status < 400;
    }
}
