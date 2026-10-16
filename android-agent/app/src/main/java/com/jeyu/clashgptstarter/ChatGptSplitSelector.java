package com.jeyu.clashgptstarter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

/** Pure compatibility mapping for a verified APKMirror split APK archive. */
public final class ChatGptSplitSelector {
    private static final int[] DENSITY = {120,160,213,240,320,480,640};
    private static final String[] QUALIFIER = {"ldpi","mdpi","tvdpi","hdpi","xhdpi","xxhdpi","xxxhdpi"};
    private ChatGptSplitSelector() {}

    public static List<String> select(List<String> entries, int sdk, String[] abis, int dpi, String language) {
        if (sdk < 32) throw new IllegalArgumentException("此 ChatGPT 安装包需要 Android 12L 或更高版本");
        if (abis == null || !Arrays.asList(abis).contains("arm64-v8a")) {
            throw new IllegalArgumentException("此整合版仅支持 ARM64 安卓手机");
        }
        if (entries == null) throw new IllegalArgumentException("ChatGPT 分包目录为空");
        Set<String> present = new HashSet<>();
        for (String name : entries) {
            if (name == null || !present.add(name)) throw new IllegalArgumentException("安装包包含重复文件，已拒绝安装");
        }
        if (!present.contains("base.apk") || !present.contains("split_config.arm64_v8a.apk")) {
            throw new IllegalArgumentException("缺少 ChatGPT 基础包或 ARM64 分包");
        }
        String density = nearestDensity(present, dpi);
        if (density == null) throw new IllegalArgumentException("缺少适配屏幕的 ChatGPT 资源分包");
        List<String> selected = new ArrayList<>();
        selected.add("base.apk");
        selected.add("split_config.arm64_v8a.apk");
        selected.add(density);
        if (present.contains("split_config.en.apk")) selected.add("split_config.en.apk");
        String normalized = language == null ? "en" : language.toLowerCase(Locale.US).split("[-_]")[0];
        if ("he".equals(normalized)) normalized="iw";
        if ("id".equals(normalized)) normalized="in";
        String locale = "split_config." + normalized + ".apk";
        if (!selected.contains(locale) && present.contains(locale)) selected.add(locale);
        return selected;
    }

    private static String nearestDensity(Set<String> entries, int dpi) {
        int difference = Integer.MAX_VALUE; String found = null;
        for (int i=0;i<DENSITY.length;i++) {
            String filename = "split_config." + QUALIFIER[i] + ".apk";
            if (!entries.contains(filename)) continue;
            int delta = Math.abs(dpi - DENSITY[i]);
            if (delta < difference || (delta == difference && DENSITY[i] > densityOf(found))) {
                difference = delta;
                found = filename;
            }
        }
        return found;
    }
    private static int densityOf(String name) {
        if (name == null) return 0;
        for (int i=0;i<QUALIFIER.length;i++) if (name.equals("split_config."+QUALIFIER[i]+".apk")) return DENSITY[i];
        return 0;
    }
}
