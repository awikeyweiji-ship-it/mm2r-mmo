package com.jeyu.clashgptstarter;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Install exactly the user-supplied Google-signed Gemini APK, independently from the assistant.
 * Android PackageInstaller performs final signature and install-consent checks.
 */
public final class GeminiInstaller {
    public static final String ASSET = "gemini-original.apk";
    public static final long EXPECTED_BYTES = 5_736_249L;
    public static final String SHA256 =
            "df5d131c354853764de41cb6a5e579692387971e17d4b0b3beb2e08edd587c68";
    public static final String SIGNER_SHA256 =
            "997c9c5d63e84a5024f308a615c4e4a87773a52073b0e1547a4d3b8b1081efa7";
    private GeminiInstaller() {}

    public static void installBundledGemini(Context context) throws Exception {
        if (Build.VERSION.SDK_INT < 28) {
            throw new IllegalStateException("Gemini 安装文件要求 Android 9 或更高系统");
        }
        if (!AppInstaller.supportsIncludedClashApk()) {
            throw new IllegalStateException("内置 Gemini 为 ARM64 版本，设备不兼容");
        }
        PackageInstaller installer=context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(ServiceTarget.GEMINI.packageName);
        int id=installer.createSession(params);
        PackageInstaller.Session session=null;
        boolean committed=false;
        try {
            session=installer.openSession(id);
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            long bytes=0;
            try (InputStream input=context.getAssets().open(ASSET);
                 OutputStream output=session.openWrite("base.apk",0,EXPECTED_BYTES)) {
                byte[] buf=new byte[64*1024];
                int count;
                while ((count=input.read(buf))!=-1) {
                    bytes+=count;
                    if (bytes>EXPECTED_BYTES) throw new SecurityException("Gemini 安装包容量异常");
                    sha.update(buf,0,count);
                    output.write(buf,0,count);
                }
                session.fsync(output);
            }
            StringBuilder actual=new StringBuilder();
            for(byte b:sha.digest()) actual.append(String.format(Locale.US,"%02x",b&255));
            if (bytes!=EXPECTED_BYTES || !SHA256.equals(actual.toString())) {
                throw new SecurityException("Gemini 原始安装包完整性校验失败");
            }
            Intent cb=new Intent(context,InstallResultReceiver.class);
            cb.setAction("com.jeyu.clashgptstarter.INSTALL_RESULT");
            cb.putExtra("expectedPackage",ServiceTarget.GEMINI.packageName);
            PendingIntent pi=PendingIntent.getBroadcast(context,id,cb,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
            session.commit(pi.getIntentSender());
            committed=true;
        } finally {
            if(session!=null) {
                if(!committed) try {session.abandon();}catch(Exception ignored){}
                session.close();
            }
        }
    }
}
