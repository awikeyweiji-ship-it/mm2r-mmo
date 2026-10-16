package com.jeyu.clashgptstarter;

import java.util.Arrays;
import java.util.List;

public class ChatGptSplitSelectorTest {
    private static int passed = 0;
    private static final List<String> ENTRIES = Arrays.asList(
            "base.apk", "split_config.arm64_v8a.apk", "split_config.x86_64.apk",
            "split_config.ldpi.apk", "split_config.mdpi.apk", "split_config.tvdpi.apk",
            "split_config.hdpi.apk", "split_config.xhdpi.apk",
            "split_config.xxhdpi.apk", "split_config.xxxhdpi.apk",
            "split_config.en.apk", "split_config.zh.apk", "split_config.ja.apk");
    private static void yes(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
        passed++;
    }
    private static void rejects(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException expected) { passed++; return; }
        throw new AssertionError("Expected validation failure");
    }
    public static void main(String[] args) {
        List<String> zh = ChatGptSplitSelector.select(ENTRIES, 35, new String[]{"arm64-v8a"}, 420, "zh");
        yes(zh.contains("base.apk"), "missing base");
        yes(zh.contains("split_config.arm64_v8a.apk"), "missing arm64");
        yes(zh.contains("split_config.xxhdpi.apk"), "420dpi should choose xxhdpi");
        yes(zh.contains("split_config.zh.apk"), "missing zh");
        yes(zh.contains("split_config.en.apk"), "missing en fallback");
        yes(!zh.contains("split_config.x86_64.apk"), "x86_64 must not enter session");
        yes(zh.size()==5, "wrong number of splits");
        List<String> en = ChatGptSplitSelector.select(ENTRIES, 32, new String[]{"arm64-v8a"}, 320, "en");
        yes(en.size()==4, "English shouldn't be included twice");
        yes(en.contains("split_config.xhdpi.apk"), "320dpi mismatch");
        List<String> ar = ChatGptSplitSelector.select(ENTRIES, 35, new String[]{"arm64-v8a"}, 640, "ar");
        yes(ar.contains("split_config.en.apk"), "missing English fallback for ar");
        yes(!ar.contains("split_config.ar.apk"), "missing language shouldn't be fabricated");
        rejects(()->ChatGptSplitSelector.select(ENTRIES, 31, new String[]{"arm64-v8a"}, 480,"zh"));
        rejects(()->ChatGptSplitSelector.select(ENTRIES, 35, new String[]{"armeabi-v7a"},480,"zh"));
        rejects(()->ChatGptSplitSelector.select(Arrays.asList("base.apk","split_config.arm64_v8a.apk","split_config.en.apk"),35,new String[]{"arm64-v8a"},480,"en"));
        rejects(()->ChatGptSplitSelector.select(Arrays.asList("base.apk","split_config.arm64_v8a.apk","split_config.xxhdpi.apk","split_config.xxhdpi.apk","split_config.en.apk"),35,new String[]{"arm64-v8a"},480,"en"));
        rejects(()->ChatGptSplitSelector.select(Arrays.asList("split_config.arm64_v8a.apk","split_config.xxhdpi.apk","split_config.en.apk"),35,new String[]{"arm64-v8a"},480,"en"));
        System.out.println("PASS: " + passed + " split-selection assertions");
    }
}
