package com.jeyu.clashgptstarter;

/** Every important instruction is driven from observed state, never cosmetic connection animation. */
public final class UiCopy {
    public static final class Model {
        public final String eyebrow, heading, subtitle, primary, helper;
        public final String toolStatus, vpnStatus, gptStatus;
        public final boolean ready;
        Model(String eyebrow, String heading, String subtitle, String primary, String helper,
              String toolStatus, String vpnStatus, String gptStatus, boolean ready) {
            this.eyebrow = eyebrow;
            this.heading = heading;
            this.subtitle = subtitle;
            this.primary = primary;
            this.helper = helper;
            this.toolStatus = toolStatus;
            this.vpnStatus = vpnStatus;
            this.gptStatus = gptStatus;
            this.ready = ready;
        }
    }

    private UiCopy() { }

    /** Verified only if VPN is currently present and a recent VPN-bound HTTPS probe passed. */
    public static Model from(boolean clash, boolean chatGpt, boolean vpn, boolean probePassed) {
        boolean verified = vpn && clash && probePassed;
        String tool = clash ? "已安装" : "未安装";
        String gpt = chatGpt ? "已安装" : "未安装";
        String network = !vpn ? "未连接" : verified ? "已验证" : "待验证";
        if (!clash) {
            return new Model("首次设置", "从这里，\n开启 ChatGPT", "先装好连接工具，再跟随提示启动网络。",
                    "安装连接工具", "首次只需设置一次，之后打开更加简单。",
                    tool, network, gpt, false);
        }
        if (!chatGpt) {
            return new Model("第 2 步 / 3", "安装 ChatGPT，\n不用找安装包", "安装包已放在助手里，点击后由安卓确认安装。",
                    "安装 ChatGPT", "安装确认后，返回这里继续连接网络。",
                    tool, network, gpt, false);
        }
        if (!vpn) {
            return new Model("还差一步", "先连接网络，\n再进入 GPT", "打开连接工具，选择可用配置并开启连接。",
                    "去连接网络", "连接完成后，返回本助手继续。",
                    tool, network, gpt, false);
        }
        if (!verified) {
            return new Model("正在准备", "检测网络，\n然后就能使用", "VPN 已建立，还需要确认 ChatGPT 网站是否可达。",
                    chatGpt ? "验证网络并打开 GPT" : "验证网络并安装 GPT",
                    "只有检测通过后，才会打开 ChatGPT。", tool, network, gpt, false);
        }
        return new Model("连接已验证", "一切就绪，\n开始探索", "现在可以打开官方 ChatGPT。",
                "打开 ChatGPT", "离开后请保持连接工具正常运行。",
                tool, network, gpt, true);
    }
}
