package com.jeyu.clashgptstarter;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.DisplayMetrics;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs the unmodified official split APKs contained in the exact user-supplied APKM.
 * The container SHA-256 is pinned; Android independently checks signer and package
 * identity/version consistency across all installed split APKs.
 *
 * All I/O must run on a background worker thread.
 */
public final class BundledChatGptInstaller {
    public static final String ASSET = "chatgpt-original.apkm";
    public static final String SHA256 =
            "cd7af30278fcca6b37cb0a7ee77aebc2fd562039b05a2ea4263c6498698e3c8f";
    public static final long EXPECTED_ARCHIVE_BYTES = 94_860_022L;
    private static final long MAX_UNCOMPRESSED_BYTES = 220L * 1024L * 1024L;
    private static final long MAX_SPLIT_BYTES = 120L * 1024L * 1024L;
    private static final int MAX_FILES = 64;

    private BundledChatGptInstaller() { }

    public static void installBundled(Context context) throws Exception {
        if (Build.VERSION.SDK_INT < 32) {
            throw new IllegalArgumentException("此内置 ChatGPT 需要 Android 12L（安卓 12.1）以上系统");
        }
        if (!AppInstaller.supportsIncludedClashApk()) {
            throw new IllegalArgumentException("此整合版仅支持 ARM64 手机");
        }
        verifyAssetDigest(context);
        List<String> names = getArchiveNames(context);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        List<String> selected = ChatGptSplitSelector.select(names,
                Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS,
                metrics.densityDpi, Locale.getDefault().getLanguage());
        Set<String> required = new HashSet<>(selected);

        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(AppInstaller.CHATGPT_PACKAGE);
        int id = installer.createSession(params);
        PackageInstaller.Session session = null;
        boolean committed = false;
        try {
            session = installer.openSession(id);
            long total = 0;
            Set<String> written = new HashSet<>();
            try (InputStream asset = context.getAssets().open(ASSET);
                 ZipInputStream zip = new ZipInputStream(asset)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory() || !required.contains(name)) {
                        zip.closeEntry();
                        continue;
                    }
                    if (!written.add(name)) throw new SecurityException("ChatGPT 分包重复，已取消安装");
                    try (OutputStream output = session.openWrite(name, 0, -1)) {
                        byte[] buffer = new byte[64 * 1024];
                        long perSplit = 0;
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            perSplit += read;
                            total += read;
                            if (perSplit > MAX_SPLIT_BYTES || total > MAX_UNCOMPRESSED_BYTES)
                                throw new IOException("ChatGPT 安装文件超过安全容量，已停止安装");
                            output.write(buffer, 0, read);
                        }
                        session.fsync(output);
                    }
                    zip.closeEntry();
                }
            }
            if (!written.equals(required)) {
                throw new SecurityException("ChatGPT 缺少必要的设备分包，已取消安装");
            }
            Intent callback = new Intent(context, InstallResultReceiver.class);
            callback.setAction("com.jeyu.clashgptstarter.INSTALL_RESULT");
            callback.putExtra("expectedPackage", AppInstaller.CHATGPT_PACKAGE);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(context, id, callback, flags);
            session.commit(pending.getIntentSender());
            committed = true;
        } finally {
            if (session != null) {
                if (!committed) try { session.abandon(); } catch (Exception ignored) { }
                session.close();
            }
        }
    }

    private static void verifyAssetDigest(Context context) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count = 0;
        try (InputStream input = context.getAssets().open(ASSET)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                count += read;
                if (count > EXPECTED_ARCHIVE_BYTES) throw new SecurityException("ChatGPT 安装文件大小异常");
                digest.update(buffer,0,read);
            }
        }
        if (count != EXPECTED_ARCHIVE_BYTES || !SHA256.equals(hex(digest.digest()))) {
            throw new SecurityException("ChatGPT 原始安装文件 SHA-256 不匹配，已停止安装");
        }
    }

    private static List<String> getArchiveNames(Context context) throws IOException {
        List<String> names = new ArrayList<>();
        try (InputStream asset = context.getAssets().open(ASSET);
             ZipInputStream zip = new ZipInputStream(asset)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    names.add(entry.getName());
                    if (names.size() > MAX_FILES) throw new IOException("安装包包含过多分包");
                }
                zip.closeEntry();
            }
        }
        return names;
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) hex.append(String.format(Locale.US, "%02x", value & 0xff));
        return hex.toString();
    }
}
