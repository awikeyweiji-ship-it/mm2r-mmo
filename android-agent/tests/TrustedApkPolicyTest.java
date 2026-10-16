import com.jeyu.clashgptstarter.TrustedApkPolicy;

public final class TrustedApkPolicyTest {
    private static int count;
    private static void expect(boolean expected, String pkg, String fingerprint) {
        boolean got = TrustedApkPolicy.isTrustedChatGpt(pkg, fingerprint);
        if (expected != got) throw new AssertionError(pkg + " / " + fingerprint + ": " + got);
        count++;
    }
    public static void main(String[] args) {
        String trusted = "b24f4bfbb3cf293f938703b9d87027c1102cc36dc4fa206910e08927db40473c";
        expect(true, "com.openai.chatgpt", trusted);
        expect(true, "com.openai.chatgpt", trusted.toUpperCase());
        expect(false, "com.fake.chatgpt", trusted);
        expect(false, "com.openai.chatgpt", "");
        expect(false, "com.openai.chatgpt", null);
        expect(false, "com.openai.chatgpt", "b24f4bfbb3cf293f938703b9d87027c1102cc36dc4fa206910e08927db40473a");
        expect(false, null, trusted);
        System.out.println("PASS: " + count + " trusted signer assertions");
    }
}
