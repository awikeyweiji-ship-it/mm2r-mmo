package com.jeyu.clashgptstarter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.os.Build;

/** Receives only the installer system callback. It does not silently install any app. */
public final class InstallResultReceiver extends BroadcastReceiver {
    public static final String PREFS = "install_result";
    public static final String LAST_RESULT = "status_text";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !"com.jeyu.clashgptstarter.INSTALL_RESULT".equals(intent.getAction())) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String pkg = intent.getStringExtra("expectedPackage");
        String displayName = AppInstaller.CHATGPT_PACKAGE.equals(pkg) ? "ChatGPT" :
                AppInstaller.GEMINI_PACKAGE.equals(pkg) ? "Gemini" : "Clash Meta";
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= 33) {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if (confirmation != null) {
                try {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmation);
                    return;
                } catch (Exception error) {
                    save(context, "安装确认无法打开，请重回助手重试");
                    return;
                }
            }
            save(context, "没有收到系统安装确认，请重新安装");
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            save(context, AppInstaller.CHATGPT_PACKAGE.equals(pkg) ?
                    "ChatGPT 安装完成，返回助手选择并启动。" :
                    AppInstaller.GEMINI_PACKAGE.equals(pkg) ?
                    "Gemini 安装完成，返回助手选择并启动。" :
                    "Clash 已安装。返回助手，继续安装 AI 应用并配置连接。");
        } else {
            String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            save(context, displayName + " 安装未完成：" +
                    (detail == null ? "可能已取消或受系统限制" : detail));
        }
    }

    private void save(Context context, String text) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(LAST_RESULT, text).apply();
    }
}
