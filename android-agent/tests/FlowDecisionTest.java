package com.jeyu.clashgptstarter;

public class FlowDecisionTest {
    private static int checked;
    private static void assertStep(FlowDecision.Step expected, boolean clash, boolean chatgpt,
                                   boolean vpn, boolean probe) {
        FlowDecision.Step actual = FlowDecision.next(clash, chatgpt, vpn, probe);
        if (actual != expected) {
            throw new AssertionError("expected=" + expected + ", actual=" + actual +
               " state=["+clash+","+chatgpt+","+vpn+","+probe+"]");
        }
        checked++;
    }
    public static void main(String[] args) {
        assertStep(FlowDecision.Step.INSTALL_CLASH, false, false, false, false);
        assertStep(FlowDecision.Step.INSTALL_CLASH, false, true, true, true);
        assertStep(FlowDecision.Step.OPEN_CLASH, true, true, false, false);
        assertStep(FlowDecision.Step.INSTALL_CHATGPT, true, false, false, false);
        assertStep(FlowDecision.Step.VERIFY_NETWORK, true, true, true, false);
        assertStep(FlowDecision.Step.INSTALL_CHATGPT, true, false, true, false);
        assertStep(FlowDecision.Step.INSTALL_CHATGPT, true, false, true, true);
        assertStep(FlowDecision.Step.OPEN_CHATGPT, true, true, true, true);
        for (int c=0;c<2;c++) for (int g=0;g<2;g++) for(int v=0;v<2;v++) for(int p=0;p<2;p++) {
            FlowDecision.Step s = FlowDecision.next(c!=0,g!=0,v!=0,p!=0);
            if (s == FlowDecision.Step.OPEN_CHATGPT && !(c!=0&&g!=0&&v!=0&&p!=0))
                throw new AssertionError("bypassed the gate");
            checked++;
        }
        System.out.println("PASS: " + checked + " flow assertions");
    }
}
