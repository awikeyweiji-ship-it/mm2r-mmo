import com.jeyu.clashgptstarter.UiCopy;

public final class UiCopyTest {
    private static int assertions;
    private static void equals(String got, String want) {
        if (!want.equals(got)) throw new AssertionError("want " + want + ", got " + got);
        assertions++;
    }
    private static void truth(boolean got, boolean want) {
        if (got != want) throw new AssertionError("want " + want + ", got " + got);
        assertions++;
    }
    public static void main(String[] args) {
        UiCopy.Model first = UiCopy.from(false, false, false, false);
        equals(first.primary, "安装连接工具");
        equals(first.toolStatus, "未安装");
        equals(first.vpnStatus, "未连接");
        equals(first.gptStatus, "未安装");
        truth(first.ready, false);
        equals(first.eyebrow, "首次设置");
        UiCopy.Model onlyClash = UiCopy.from(true, false, false, false);
        equals(onlyClash.primary, "安装 ChatGPT");
        equals(onlyClash.toolStatus, "已安装");
        truth(onlyClash.ready, false);
        UiCopy.Model needsProbe = UiCopy.from(true, true, true, false);
        equals(needsProbe.primary, "验证网络并打开 GPT");
        equals(needsProbe.vpnStatus, "待验证");
        truth(needsProbe.ready, false);
        UiCopy.Model notInstalled = UiCopy.from(true, false, true, false);
        equals(notInstalled.primary, "安装 ChatGPT");
        UiCopy.Model validated = UiCopy.from(true, false, true, true);
        equals(validated.vpnStatus, "已验证");
        equals(validated.primary, "安装 ChatGPT");
        truth(validated.ready, false);
        UiCopy.Model ready = UiCopy.from(true, true, true, true);
        equals(ready.primary, "打开 ChatGPT");
        equals(ready.vpnStatus, "已验证");
        truth(ready.ready, true);
        equals(UiCopy.from(true,true,false,true).vpnStatus, "未连接");
        truth(UiCopy.from(true,true,false,true).ready, false);
        equals(UiCopy.from(false,true,true,true).primary, "安装连接工具");
        truth(UiCopy.from(false,true,true,true).ready, false);
        System.out.println("PASS: " + assertions + " UI text/state assertions");
    }
}
