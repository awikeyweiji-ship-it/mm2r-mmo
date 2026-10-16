package com.jeyu.clashgptstarter;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;

/** Writes the byte-identical supplied APK to Android PackageInstaller; system obtains user consent. */
public final class AppInstaller {
    public static final String CLASH_PACKAGE = "com.github.metacubex.clash.meta";
    public static final String CHATGPT_PACKAGE = "com.openai.chatgpt";
    public static final String GEMINI_PACKAGE = "com.google.android.apps.bard";
    public static final String CLAUDE_PACKAGE = "com.anthropic.claude";
    public static final String CLASH_ASSET = "clash-meta.apk";
    public static final String CLASH_SHA256 =
            "93a719347dc2318b6b6356c588646a7ee82fd7ab0355e7a865fe6954de1234f0";

    private AppInstaller() { }

    /** Streams a verified local ChatGPT file to Android; caller must validate signing info first. */
    static void installLocalChatGpt(Context context, java.io.File apk) throws Exception {
        if (apk == null || !apk.isFile() || apk.length() > ChatGptSourceInstaller.MAX_BYTES) {
            throw new java.io.IOException("ChatGPT 安装文件不存在或过大");
        }
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        final String expectedPackage = CHATGPT_PACKAGE;
        params.setAppPackageName(expectedPackage);
        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = null;
        boolean committed = false;
        try {
            session = installer.openSession(sessionId);
            try (InputStream input = new java.io.FileInputStream(apk);
                 OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                session.fsync(output);
            }
            Intent callback = new Intent(context, InstallResultReceiver.class);
            callback.setAction("com.jeyu.clashgptstarter.INSTALL_RESULT");
            callback.putExtra("expectedPackage", expectedPackage);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, callback, flags);
            session.commit(pending.getIntentSender());
            committed = true;
        } finally {
            if (session != null) {
                if (!committed) try { session.abandon(); } catch (Exception ignored) { }
                session.close();
            }
        }
    }

    public static boolean supportsIncludedClashApk() {
        return Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a");
    }

    /** Worker-thread operation; never call on UI thread. */
    public static void installBundledClash(Context context) throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(CLASH_PACKAGE);
        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = null;
        boolean committed = false;
        try {
            session = installer.openSession(sessionId);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = context.getAssets().open(CLASH_ASSET);
                 OutputStream output = session.openWrite("base.apk", 0, -1)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
                session.fsync(output);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
            if (!CLASH_SHA256.equals(hex.toString())) {
                throw new SecurityException("Clash 安装包校验失败；已取消安装");
            }

            Intent callback = new Intent(context, InstallResultReceiver.class);
            callback.setAction("com.jeyu.clashgptstarter.INSTALL_RESULT");
            callback.putExtra("expectedPackage", CLASH_PACKAGE);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, callback, flags);
            session.commit(pending.getIntentSender());
            committed = true;
        } finally {
            if (session != null) {
                if (!committed) {
                    try { session.abandon(); } catch (Exception ignored) { }
                }
                session.close();
            }
        }
    }
}
