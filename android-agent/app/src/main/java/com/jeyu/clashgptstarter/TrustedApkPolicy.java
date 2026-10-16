package com.jeyu.clashgptstarter;

import java.util.Locale;

/** Whitelist for an explicitly selected ChatGPT APK, not a downloader or certificate authority. */
public final class TrustedApkPolicy {
    public static final String CHATGPT_PACKAGE = "com.openai.chatgpt";
    // Published APK signing digest for the official Google Play build (APKMirror, 2026).
    // Recheck if Google/OpenAI rotates the Play App Signing certificate.
    public static final String CHATGPT_SIGNER_SHA256 =
            "b24f4bfbb3cf293f938703b9d87027c1102cc36dc4fa206910e08927db40473c";

    private TrustedApkPolicy() { }

    public static boolean isTrustedChatGpt(String packageName, String signerDigest) {
        return CHATGPT_PACKAGE.equals(packageName) && signerDigest != null &&
                CHATGPT_SIGNER_SHA256.equals(signerDigest.trim().toLowerCase(Locale.US));
    }
}
