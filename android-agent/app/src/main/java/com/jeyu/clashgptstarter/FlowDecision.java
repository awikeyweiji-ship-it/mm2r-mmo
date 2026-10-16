package com.jeyu.clashgptstarter;

/** Pure decision gate. It never treats a running application as an active VPN. */
public final class FlowDecision {
    public enum Step { INSTALL_CLASH, OPEN_CLASH, VERIFY_NETWORK, INSTALL_CHATGPT, OPEN_CHATGPT }

    private FlowDecision() { }

    /** probePassed is meaningful only when a VPN transport is present. */
    public static Step next(boolean clashInstalled, boolean chatGptInstalled,
                            boolean vpnPresent, boolean probePassed) {
        if (!clashInstalled) return Step.INSTALL_CLASH;
        if (!chatGptInstalled) return Step.INSTALL_CHATGPT;
        if (!vpnPresent) return Step.OPEN_CLASH;
        if (!probePassed) return Step.VERIFY_NETWORK;
        return Step.OPEN_CHATGPT;
    }
}
