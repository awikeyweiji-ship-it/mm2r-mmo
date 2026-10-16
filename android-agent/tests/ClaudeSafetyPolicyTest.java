import com.jeyu.clashgptstarter.ClaudeSafetyPolicy;
import com.jeyu.clashgptstarter.ServiceTarget;
public class ClaudeSafetyPolicyTest {
 public static void main(String[] args) {
  assert ServiceTarget.CLAUDE.packageName.equals("com.anthropic.claude");
  assert ServiceTarget.CLAUDE.id.equals("claude");
  assert ServiceTarget.CLAUDE.probeUrl.equals("https://claude.ai/");
  assert ServiceTarget.fromId("claude") == ServiceTarget.CLAUDE;
  assert ClaudeSafetyPolicy.requiresEligibilityConfirmation(ServiceTarget.CLAUDE);
  assert !ClaudeSafetyPolicy.requiresEligibilityConfirmation(ServiceTarget.GEMINI);
  assert !ClaudeSafetyPolicy.canLaunch(ServiceTarget.CLAUDE,false);
  assert ClaudeSafetyPolicy.canLaunch(ServiceTarget.CLAUDE,true);
  assert ClaudeSafetyPolicy.canLaunch(ServiceTarget.CHATGPT,false);
  assert !ClaudeSafetyPolicy.shouldAutoSelect(ServiceTarget.CLAUDE,true);
  assert ClaudeSafetyPolicy.shouldAutoSelect(ServiceTarget.GEMINI,true);
  assert !ClaudeSafetyPolicy.shouldAutoSelect(ServiceTarget.CHATGPT,false);
  assert ClaudeSafetyPolicy.isRestrictionResponse(403);
  assert ClaudeSafetyPolicy.isRestrictionResponse(429);
  assert ClaudeSafetyPolicy.isRestrictionResponse(451);
  assert ClaudeSafetyPolicy.isRestrictionResponse(401);
  assert !ClaudeSafetyPolicy.isRestrictionResponse(302);
  long now=2_000_000L;
  assert ClaudeSafetyPolicy.inCooldown(now,now-1_000L);
  assert !ClaudeSafetyPolicy.inCooldown(now,now-ClaudeSafetyPolicy.COOLDOWN_MS-1L);
  assert !ClaudeSafetyPolicy.inCooldown(now,0L);
  assert !ClaudeSafetyPolicy.inCooldown(0L,now);
  System.out.println("PASS: 21 Claude safety assertions");
 }
}
