import java.util.*;
import com.jeyu.clashgptstarter.SmartNodePolicy;
public final class SmartNodePolicyTest {
 private static SmartNodePolicy.Candidate c(String name,int a,int b) {
  return new SmartNodePolicy.Candidate(name,a,b);
 }
 public static void main(String[] args) {
  List<SmartNodePolicy.Candidate> input=Arrays.asList(c("DIRECT",3,3),c("快但不稳定",20,900),
    c("东京节点",103,111),c("坏掉",0,-1),c("稳定节点",80,90));
  List<SmartNodePolicy.Candidate> got=SmartNodePolicy.rank(input);
  assert got.size()==3 : "unsafe/unreachable nodes must be filtered";
  assert got.get(0).name.equals("稳定节点") : got.get(0).name;
  assert got.get(1).name.equals("东京节点");
  assert got.get(2).name.equals("快但不稳定");
  assert !SmartNodePolicy.isSelectCandidate("GLOBAL");
  assert !SmartNodePolicy.isSelectCandidate("REJECT");
  assert !SmartNodePolicy.isSelectCandidate("DIRECT");
  assert SmartNodePolicy.isSelectCandidate("香港节点") && SmartNodePolicy.isSelectCandidate("日本·01");
  List<SmartNodePolicy.Candidate> tie=SmartNodePolicy.rank(Arrays.asList(c("B",99,99),c("A",99,99)));
  assert tie.get(0).name.equals("A") : "ties must be deterministic";
  System.out.println("PASS: 9 node ranking assertions");
 }
}
