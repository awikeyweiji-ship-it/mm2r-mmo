package com.jeyu.clashgptstarter;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Install a selected single APK only after checking its package ID and APK signing certificate. */
public final class ChatGptSourceInstaller {
    public static final String OPTIONAL_BUNDLED_ASSET = BundledChatGptInstaller.ASSET;
    public static final long MAX_BYTES = 300L * 1024 * 1024;

    private ChatGptSourceInstaller() { }

    /** Optional build-time user-provided asset; standard public release does not include it. */
    public static boolean isBundledAvailable(Context context) {
        try (InputStream ignored = context.getAssets().open(OPTIONAL_BUNDLED_ASSET)) {
            return true;
        } catch (IOException absent) {
            return false;
        }
    }

    /** Both entrypoints must run on a background thread; user consent is handled by Android. */
    public static void installSelected(Context context, Uri selected) throws Exception {
        if (selected == null || !"content".equals(selected.getScheme())) {
            throw new SecurityException("请选择文件管理器中的可信 APK 文件");
        }
        try (InputStream input = context.getContentResolver().openInputStream(selected)) {
            if (input == null) throw new IOException("未能读取所选安装文件");
            stageAndInstall(context, input);
        }
    }

    public static void installBundled(Context context) throws Exception {
        BundledChatGptInstaller.installBundled(context);
    }

    private static void stageAndInstall(Context context, InputStream stream) throws Exception {
        File staging = File.createTempFile("chatgpt-verified-", ".apk", context.getCacheDir());
        try {
            long size = 0;
            try (OutputStream dest = new FileOutputStream(staging)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    size += count;
                    if (size > MAX_BYTES) throw new IOException("安装包超过 300MB，已停止处理");
                    dest.write(buffer, 0, count);
                }
            }
            if (size < 1024) throw new IOException("安装文件过小或不完整");
            verifyArchive(context, staging);
            AppInstaller.installLocalChatGpt(context, staging);
        } finally {
            if (staging.exists()) staging.delete();
        }
    }

    private static void verifyArchive(Context context, File apk) throws Exception {
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ?
                PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (info == null || !TrustedApkPolicy.CHATGPT_PACKAGE.equals(info.packageName)) {
            throw new SecurityException("这不是 ChatGPT 安卓安装包，已拒绝安装");
        }
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length != 1) {
            throw new SecurityException("无法确认安装包签名，请改用 Google Play");
        }
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        byte[] digest = hash.digest(signatures[0].toByteArray());
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format(Locale.US, "%02x", b & 0xff));
        if (!TrustedApkPolicy.isTrustedChatGpt(info.packageName, hex.toString())) {
            throw new SecurityException("ChatGPT 签名与已知官方发行证书不符，已拒绝安装。建议通过 Google Play 获取。");
        }
    }
}
