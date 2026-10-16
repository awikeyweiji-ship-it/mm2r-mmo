import com.jeyu.clashgptstarter.ServiceTarget;
public final class ServiceTargetTest {
 public static void main(String[] args) {
  assert ServiceTarget.CHATGPT.packageName.equals("com.openai.chatgpt");
  assert ServiceTarget.GEMINI.packageName.equals("com.google.android.apps.bard");
  assert ServiceTarget.GEMINI.probeUrl.equals("https://gemini.google.com/app");
  assert ServiceTarget.CHATGPT.probeUrl.equals("https://chatgpt.com/");
  assert ServiceTarget.fromId("gemini")==ServiceTarget.GEMINI;
  assert ServiceTarget.fromId(null)==ServiceTarget.CHATGPT;
  assert ServiceTarget.blockedResponse(403);
  assert !ServiceTarget.blockedResponse(302);
  assert ServiceTarget.networkReachable(302);
  assert !ServiceTarget.networkReachable(429);
  System.out.println("PASS: 10 target-switch assertions");
 }
}
