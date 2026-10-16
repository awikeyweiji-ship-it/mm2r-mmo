package com.jeyu.clashgptstarter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.text.InputType;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two surfaces for one job: a minimal everyday home and an optional guided setup.
 * System permissions and VPN consent remain visible, user-driven Android operations.
 */
public final class MainActivity extends Activity {
    private static final int BG = UiPalette.BG;
    private static final int INK = UiPalette.TEXT;
    private static final int MUTED = UiPalette.MUTED;
    private static final int MINT = UiPalette.ACCENT;
    private static final int CAUTION = UiPalette.WARM;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private LinearLayout homePage, settingsPage;
    private ScrollView scroll;
    private TextView clashStatus, vpnStatus, gptStatus, message, settingsMessage;
    private TextView headline, description, kicker, helper, toolStepTitle, installGptStepTitle;
    private TextView toolBullet, vpnBullet, gptBullet;
    private TextView gptChoice, geminiChoice, claudeChoice, serviceTitle, nodeStatus;
    private EditText controllerPort, controllerGroup, controllerToken;
    private Switch smartSwitch;
    private ServiceTarget target=ServiceTarget.CHATGPT;
    private ServiceTarget verifiedTarget;
    private String lastNodeSummary="";
    private boolean pendingBundledGemini;
    private Button primary, toolSetupButton;
    private TechOrbitView orbit;
    private boolean launchRequested;
    private boolean installing;
    private boolean checking;
    private boolean installAfterPermission;
    private Uri pendingChatGptUri;
    private boolean pendingBundledChatGpt;
    private static final int PICK_CHATGPT_APK = 4423;
    private boolean didOpenOtherApp;
    private boolean showingSettings;
    private Network verifiedNetwork;
    private long verifiedAtElapsed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        target=ServiceTarget.fromId(getSharedPreferences("ai_gateway",MODE_PRIVATE)
                .getString("target","chatgpt"));
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!NetworkGate.hasVpnTransport(this)) verifiedNetwork = null;
        updateStatus();
        if (installAfterPermission && Build.VERSION.SDK_INT >= 26 &&
                getPackageManager().canRequestPackageInstalls()) {
            installAfterPermission = false;
            final Uri selectedApk = pendingChatGptUri;
            pendingChatGptUri = null;
            if (selectedApk != null) {
                mainThread.post(() -> installChatGptFromUri(selectedApk));
            } else if (pendingBundledChatGpt) {
                pendingBundledChatGpt = false;
                mainThread.post(this::installBundledChatGpt);
            } else if (pendingBundledGemini) {
                pendingBundledGemini = false;
                mainThread.post(this::installBundledGemini);
            } else {
                mainThread.post(this::installClash);
            }
        }
        // Android 10+ disallows unprompted activity launches from background.
        // The user deliberately returns to the assistant to resume their flow.
        if (didOpenOtherApp && launchRequested && !checking) {
            didOpenOtherApp = false;
            if (NetworkGate.hasVpnTransport(this)) {
                mainThread.postDelayed(() -> checkAndContinue(true), 250);
            } else {
                launchRequested = false;
                showMessage("还没有连接成功。请在 Clash 中开启连接，然后返回这里。", false);
            }
        }
    }

    private void buildUi() {
        FrameLayout background = new FrameLayout(this);
        background.addView(new TechBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        background.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                // Android 15 targets SDK 35 and draws behind navigation/status bars.
                content.setPadding(dp(22) + bars.left, dp(20) + bars.top,
                        dp(22) + bars.right, dp(30) + bars.bottom);
                return insets;
            });
        }
        setContentView(background);
        content.addView(header(), spaced(0, 0));
        homePage = buildHome();
        content.addView(homePage, spaced(0, 0));
        settingsPage = buildSettings();
        settingsPage.setVisibility(View.GONE);
        content.addView(settingsPage, spaced(0, 0));
        updateStatus();
    }

    private View header() {
        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setMinimumHeight(dp(52));
        TextView logo = label("G", 22, UiPalette.ACCENT, true);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable logoShape = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff234A65, 0xff10233F});
        logoShape.setCornerRadius(dp(15));
        logoShape.setStroke(dp(1), 0x8857A9E5);
        logo.setBackground(logoShape);
        logo.setContentDescription("GPT 网关标识");
        top.addView(logo, new LinearLayout.LayoutParams(dp(47),dp(47)));
        LinearLayout titles = column();
        TextView name = label("GATEWAY", 18, INK, true);
        name.setLetterSpacing(.14f);
        titles.addView(name);
        TextView product = label("AI 智能启动助手", 11, MUTED, false);
        titles.addView(product, spaced(dp(3),0));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,-2,1f);
        titleParams.leftMargin = dp(13);
        top.addView(titles, titleParams);
        TextView settings = label("设置  ↗", 13, INK, true);
        settings.setGravity(Gravity.CENTER);
        settings.setMinHeight(dp(48));
        settings.setMinWidth(dp(78));
        settings.setPadding(dp(13),0,dp(13),0);
        settings.setBackground(roundFill(UiPalette.SURFACE, dp(15), UiPalette.BORDER));
        settings.setContentDescription("打开连接设置与帮助");
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings);
        return top;
    }

    private LinearLayout buildHome() {
        LinearLayout home = column();
        TextView serviceHint=label("选择要打开的 AI",13,MUTED,false);
        serviceHint.setGravity(Gravity.CENTER);
        home.addView(serviceHint,spaced(dp(14),dp(8)));
        LinearLayout choices=horizontal();
        choices.setGravity(Gravity.CENTER);
        gptChoice=label("  ChatGPT  ",17,INK,true);
        gptChoice.setGravity(Gravity.CENTER);
        gptChoice.setMinHeight(dp(61));
        gptChoice.setContentDescription("选择 ChatGPT");
        gptChoice.setOnClickListener(v -> chooseTarget(ServiceTarget.CHATGPT));
        geminiChoice=label("  Gemini  ",17,INK,true);
        geminiChoice.setGravity(Gravity.CENTER);
        geminiChoice.setMinHeight(dp(61));
        geminiChoice.setContentDescription("选择 Gemini");
        geminiChoice.setOnClickListener(v -> chooseTarget(ServiceTarget.GEMINI));
        claudeChoice=label("Claude",15,INK,true);
        claudeChoice.setGravity(Gravity.CENTER);
        claudeChoice.setMinHeight(dp(61));
        claudeChoice.setContentDescription("选择 Claude");
        claudeChoice.setOnClickListener(v -> chooseTarget(ServiceTarget.CLAUDE));
        gptChoice.setTextSize(15);
        gptChoice.setText("ChatGPT");
        geminiChoice.setTextSize(15);
        geminiChoice.setText("Gemini");
        LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(0,dp(61),1f);
        first.rightMargin=dp(4);
        choices.addView(gptChoice,first);
        LinearLayout.LayoutParams middle=new LinearLayout.LayoutParams(0,dp(61),1f);
        middle.leftMargin=dp(4);middle.rightMargin=dp(4);
        choices.addView(geminiChoice,middle);
        LinearLayout.LayoutParams third=new LinearLayout.LayoutParams(0,dp(61),1f);
        third.leftMargin=dp(4);
        choices.addView(claudeChoice,third);
        home.addView(choices,spaced(0,dp(4)));
        kicker = label("首次设置", 12, UiPalette.ACCENT, true);
        kicker.setLetterSpacing(.05f);
        kicker.setGravity(Gravity.CENTER);
        kicker.setMinHeight(dp(31));
        kicker.setPadding(dp(18), dp(5), dp(18), dp(5));
        kicker.setBackground(roundFill(0xff162C43, dp(30), 0xff34658C));
        LinearLayout centre = horizontal();
        centre.setGravity(Gravity.CENTER);
        centre.addView(kicker, new LinearLayout.LayoutParams(-2,-2));
        home.addView(centre, spaced(dp(19),0));

        orbit = new TechOrbitView(this);
        orbit.setContentDescription("装饰性网络图形，不代表实际已连接");
        int orbitHeight = getResources().getConfiguration().screenHeightDp < 700 ? 126 : 172;
        home.addView(orbit, new LinearLayout.LayoutParams(-1, dp(orbitHeight)));

        headline = label("从这里，\n开启 ChatGPT", 31, INK, true);
        headline.setGravity(Gravity.CENTER);
        headline.setLineSpacing(dp(2), 1.07f);
        home.addView(headline, spaced(dp(0), 0));
        description = label("先安装连接工具", 14, MUTED, false);
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(dp(3),1f);
        home.addView(description, spaced(dp(10),dp(21)));

        LinearLayout statusCard = glassCard();
        LinearLayout statusHeader = horizontal();
        statusHeader.setGravity(Gravity.CENTER_VERTICAL);
        statusHeader.addView(label("连接状态", 15, INK, true),
                new LinearLayout.LayoutParams(0,-2,1f));
        TextView caption = label("3 个步骤", 11, MUTED, false);
        caption.setLetterSpacing(.06f);
        statusHeader.addView(caption);
        statusCard.addView(statusHeader, spaced(0,dp(8)));
        LinearLayout s1 = statusRow("01", "连接工具");
        toolBullet = (TextView)s1.getChildAt(0);
        clashStatus = (TextView)s1.getChildAt(2);
        statusCard.addView(s1);
        statusCard.addView(divider());
        LinearLayout s2 = statusRow("02", "VPN 网络");
        vpnBullet = (TextView)s2.getChildAt(0);
        vpnStatus = (TextView)s2.getChildAt(2);
        statusCard.addView(s2);
        statusCard.addView(divider());
        LinearLayout s3 = statusRow("03", "ChatGPT");
        gptBullet = (TextView)s3.getChildAt(0);
        serviceTitle = (TextView)s3.getChildAt(1);
        gptStatus = (TextView)s3.getChildAt(2);
        statusCard.addView(s3);
        primary = accentButton("安装连接工具");
        primary.setOnClickListener(v -> startFlow());
        home.addView(primary, spaced(0,dp(7)));
        helper = label("完成连接后返回这里继续", 12, MUTED, false);
        helper.setGravity(Gravity.CENTER);
        home.addView(helper, spaced(dp(3), dp(11)));
        home.addView(statusCard, spaced(0, dp(12)));
        nodeStatus=label("节点状态：使用 Clash 当前连接",12,MUTED,false);
        nodeStatus.setGravity(Gravity.CENTER);
        home.addView(nodeStatus,spaced(dp(10),dp(5)));

        message = label("", 13, UiPalette.WARM, false);
        message.setLineSpacing(dp(3),1f);
        message.setPadding(dp(15),dp(12),dp(15),dp(12));
        message.setBackground(roundFill(0xff232B3C,dp(13),0xff4D5871));
        message.setVisibility(View.GONE);
        home.addView(message, spaced(dp(8),dp(2)));

        // R7-A native persistent project vault; original R6 launcher and network flows stay intact.
        TextView research = label("◈  我的 AI 项目  /  长期任务与原始证据   ›", 15, UiPalette.ACCENT, true);
        research.setContentDescription("打开本机加密项目仓，离线保存任务及源文件");
        research.setMinHeight(dp(62));
        research.setGravity(Gravity.CENTER);
        research.setBackground(roundFill(0xff0D2636,dp(14),0xff347E82));
        research.setOnClickListener(v -> startActivity(new Intent(this,R7ProjectsActivity.class)));
        home.addView(research,spaced(dp(16),dp(4)));
        TextView researchNote=label("离线加密保存 · 重启可恢复 · 经同意才上传云端",11,MUTED,false);
        researchNote.setGravity(Gravity.CENTER);
        home.addView(researchNote,spaced(0,dp(4)));
        TextView help = label("遇到问题？  查看设置与帮助   ›", 13, UiPalette.ACCENT, true);
        help.setGravity(Gravity.CENTER);
        help.setMinHeight(dp(51));
        help.setContentDescription("打开详细安装设置和故障帮助");
        help.setOnClickListener(v -> showSettings());
        home.addView(help, spaced(dp(7),dp(2)));
        TextView foot = label("本助手不会读取账户密码或连接配置", 11, UiPalette.MUTED, false);
        foot.setGravity(Gravity.CENTER);
        home.addView(foot, spaced(0,dp(4)));
        return home;
    }

    private LinearLayout buildSettings() {
        LinearLayout page = column();
        TextView back = label("‹   返回首页", 14, UiPalette.ACCENT, true);
        back.setMinHeight(dp(48));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setContentDescription("返回启动首页");
        back.setOnClickListener(v -> showHome());
        page.addView(back, spaced(dp(12),dp(13)));
        page.addView(label("连接设置", 30, INK, true));
        TextView intro = label("第一次按顺序安装、连接和使用。以后只需点击首页的大按钮。", 14, MUTED, false);
        intro.setLineSpacing(dp(3),1f);
        page.addView(intro, spaced(dp(9),dp(18)));

        LinearLayout card1 = stepCard("01  安装", "安装连接工具", "这是连接网络需要的工具。首次安装时，按安卓提示点击“允许”。");
        toolStepTitle = (TextView)card1.getChildAt(1);
        toolSetupButton = outlineButton("安装连接工具");
        toolSetupButton.setOnClickListener(v -> {
            launchRequested = false;
            if (installed(AppInstaller.CLASH_PACKAGE)) openClash(false);
            else installClash();
        });
        card1.addView(toolSetupButton, spaced(dp(11),0));
        page.addView(card1, spaced(0,dp(12)));

        LinearLayout card2 = stepCard("03  连接", "在 Clash 中开启连接", "打开 Clash，导入连接配置、选择可用节点并点击启动。第一次请同意系统 VPN 授权，然后返回助手。");
        Button connect = outlineButton("打开 Clash 连接");
        connect.setOnClickListener(v -> {
            launchRequested = false;
            if (!installed(AppInstaller.CLASH_PACKAGE)) showMessage("请先完成第 1 步：安装连接工具。", false);
            else openClash(false);
        });
        card2.addView(connect, spaced(dp(11),0));

        LinearLayout card3 = stepCard("02  安装", "安装 ChatGPT", "安装包已放在助手里。点一下安装，再在安卓弹窗中确认即可。无需寻找安装文件。");
        installGptStepTitle = (TextView)card3.getChildAt(1);
        if (ChatGptSourceInstaller.isBundledAvailable(this)) {
            Button offlineGpt = outlineButton("安装内置 ChatGPT（离线）");
            offlineGpt.setOnClickListener(v -> installBundledChatGpt());
            card3.addView(offlineGpt, spaced(dp(11),0));
        }
        Button gpt = outlineButton("备用：Google Play 官方商店");
        gpt.setOnClickListener(v -> openChatGptPlay());
        card3.addView(gpt, spaced(dp(9),0));
        Button localGpt = outlineButton("高级：选择其他单 APK 文件");
        localGpt.setOnClickListener(v -> selectChatGptApk());
        card3.addView(localGpt, spaced(dp(9),0));
        page.addView(card3, spaced(0,dp(12)));
        LinearLayout geminiCard=stepCard("02  安装", "安装 Gemini",
                "Gemini 原始安装包已内置。安卓弹出确认后点击安装，登录仍需要 Google 账户。");
        Button geminiInstall=outlineButton("安装内置 Gemini（离线）");
        geminiInstall.setOnClickListener(v -> installBundledGemini());
        geminiCard.addView(geminiInstall,spaced(dp(11),0));
        page.addView(geminiCard,spaced(0,dp(12)));
        LinearLayout claudeCard=stepCard("02  安装", "安装 Claude（官方版）",
                "Claude 请通过 Anthropic 官方 Google Play 安装。当前未提供 Claude APK，" +
                "不使用来历不明的替代版；安装后登录由本人操作。");
        Button installClaude=outlineButton("前往 Google Play 安装 Claude");
        installClaude.setOnClickListener(v -> openClaudePlay());
        claudeCard.addView(installClaude,spaced(dp(11),0));
        Button supportedRegions=outlineButton("查看 Claude 官方支持地区与使用要求");
        supportedRegions.setOnClickListener(v -> openClaudeRegionHelp());
        claudeCard.addView(supportedRegions,spaced(dp(8),0));
        page.addView(claudeCard,spaced(0,dp(12)));
        page.addView(card2, spaced(0,dp(12)));

        LinearLayout check = glassCard();
        check.addView(label("检查与排障", 16, INK, true));
        TextView detail = label("连接后点击下面按钮验证网络。若失败，请返回 Clash 更换节点。", 13, MUTED, false);
        detail.setLineSpacing(dp(2),1f);
        check.addView(detail, spaced(dp(7),dp(8)));
        Button retry = outlineButton("重新检测连接");
        retry.setOnClickListener(v -> {
            if (!NetworkGate.hasVpnTransport(this)) {
                showMessage("还没有检测到 VPN，请先返回 Clash 打开连接。", false);
            } else checkAndContinue(false);
        });
        check.addView(retry, spaced(dp(9),0));
        page.addView(check, spaced(dp(2),dp(7)));
        LinearLayout smart=glassCard();
        smart.addView(label("智能选点（可选）",18,INK,true));
        TextView smartHelp=label("首次只需配置一次：在 Clash 的「覆写」里启用本地控制接口 " +
                "127.0.0.1:9090，并设置密钥。将同一密钥填在下面。策略组请选择 AI 网站实际经过的 Selector；" +
                "规则模式下如果选错策略组，切换节点可能不起作用。无需上传订阅或登录信息。",13,MUTED,false);
        smartHelp.setLineSpacing(dp(2),1f);
        smart.addView(smartHelp,spaced(dp(8),dp(12)));
        smart.addView(label("本地控制端口",12,MUTED,false));
        controllerPort=new EditText(this);
        controllerPort.setSingleLine(true);
        controllerPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        controllerPort.setText(ControllerSecrets.endpoint(this).replace("127.0.0.1:",""));
        controllerPort.setTextColor(INK);
        controllerPort.setHint("9090");
        smart.addView(controllerPort,new LinearLayout.LayoutParams(-1,dp(54)));
        smart.addView(label("策略组名（只有一个组时可留空）",12,MUTED,false),
                spaced(dp(7),0));
        controllerGroup=new EditText(this);
        controllerGroup.setSingleLine(true);
        controllerGroup.setText(ControllerSecrets.group(this));
        controllerGroup.setTextColor(INK);
        controllerGroup.setHint("例如：节点选择");
        smart.addView(controllerGroup,new LinearLayout.LayoutParams(-1,dp(54)));
        smart.addView(label("控制密钥（加密保存到本机）",12,MUTED,false),spaced(dp(7),0));
        controllerToken=new EditText(this);
        controllerToken.setSingleLine(true);
        controllerToken.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        controllerToken.setTextColor(INK);
        controllerToken.setHint("粘贴 Clash 覆写中的密钥");
        smart.addView(controllerToken,new LinearLayout.LayoutParams(-1,dp(54)));
        Button saveSmart=outlineButton("保存本地控制设置");
        saveSmart.setOnClickListener(v -> {
            try {
                String fresh=controllerToken.getText().toString();
                if(fresh.isEmpty())fresh=ControllerSecrets.loadSecret(this);
                ControllerSecrets.save(this,controllerPort.getText().toString(),
                        controllerGroup.getText().toString(),fresh);
                controllerToken.setText("");
                showMessage("已安全保存。下一步打开 Clash，启用本地控制覆写后再回来。",true);
            }catch(Exception ex){showMessage("无法保存设置："+ex.getMessage(),false);}
        });
        smart.addView(saveSmart,spaced(dp(12),0));
        smartSwitch=new Switch(this);
        smartSwitch.setText("自动选点（ChatGPT / Gemini；Claude 默认固定当前节点）");
        smartSwitch.setTextColor(INK);
        smartSwitch.setMinHeight(dp(58));
        smartSwitch.setChecked(ControllerSecrets.enabled(this));
        smartSwitch.setOnCheckedChangeListener((b,checked)->{
            if(checked && ControllerSecrets.loadSecret(this).isEmpty()){
                b.setChecked(false);
                showMessage("请先填写并保存本地控制密钥，再开启自动选点。",false);
                return;
            }
            ControllerSecrets.setEnabled(this,checked);
            updateStatus();
        });
        smart.addView(smartSwitch,spaced(dp(9),0));
        Button testControl=outlineButton("测试本地控制接口");
        testControl.setOnClickListener(v -> testLocalController());
        smart.addView(testControl,spaced(dp(4),0));
        smart.addView(label("仅允许本机 127.0.0.1；" +
                "只测试当前 Clash 策略组，不会读取订阅文件或修改其他组。",11,MUTED,false),
                spaced(dp(8),0));
        page.addView(smart,spaced(dp(8),dp(8)));
        LinearLayout risk=glassCard();
        risk.addView(label("Claude 稳定连接 / 账号风险",17,INK,true));
        TextView riskDetails=label("Claude 的可用性取决于服务支持地区、账号资格与官方规则；" +
                "不能保证不封号。支持地区中可优先使用稳定直连；若系统 VPN 已开启则会明确告知。" +
                "不自动遍历或切换节点，不自动化登录。遇到限制、限流或身份挑战会暂停探测。",13,MUTED,false);
        riskDetails.setLineSpacing(dp(3),1f);
        risk.addView(riskDetails,spaced(dp(10),dp(12)));
        Button confirmClaude=outlineButton("阅读地区说明并确认使用资格");
        confirmClaude.setOnClickListener(v -> confirmClaudeEligibility());
        risk.addView(confirmClaude,spaced(dp(9),0));
        Button officialTerms=outlineButton("查看 Anthropic 服务条款");
        officialTerms.setOnClickListener(v -> openWeb(ClaudeSafetyPolicy.OFFICIAL_TERMS));
        risk.addView(officialTerms,spaced(dp(8),0));
        page.addView(risk,spaced(dp(8),dp(8)));

        settingsMessage = label("", 13, UiPalette.WARM, false);
        settingsMessage.setPadding(dp(15),dp(12),dp(15),dp(12));
        settingsMessage.setBackground(roundFill(0xff232B3C,dp(13),0xff4D5871));
        settingsMessage.setVisibility(View.GONE);
        page.addView(settingsMessage, spaced(dp(8),0));
        TextView privacy = label("关于与隐私  ·  V6.0\n\n内置原始 Clash Meta、ChatGPT APKM 和 Gemini。Claude 通过 Anthropic 官方 Google Play 安装，并非内置二进制。\n\n本助手不读取 AI 账号密码、订阅文件或登录 Cookie。Claude 默认禁用自动节点切换；检查网站可达不等于账号可登录或保证安全。整合安装包最低 Android 12L、ARM64。", 12, MUTED, false);
        privacy.setLineSpacing(dp(3),1f);
        page.addView(privacy, spaced(dp(18),dp(24)));
        return page;
    }

    private LinearLayout stepCard(String tag, String title, String detail) {
        LinearLayout card = glassCard();
        TextView number = label(tag, 12, UiPalette.ACCENT, true);
        number.setLetterSpacing(.09f);
        card.addView(number);
        card.addView(label(title, 18, INK, true), spaced(dp(6),0));
        TextView content = label(detail, 13, MUTED, false);
        content.setLineSpacing(dp(3),1f);
        card.addView(content, spaced(dp(8),0));
        return card;
    }

    private LinearLayout glassCard() {
        LinearLayout card = column();
        card.setPadding(dp(19),dp(17),dp(19),dp(17));
        GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff1B2B43,UiPalette.SURFACE});
        surface.setCornerRadius(dp(21));
        surface.setStroke(dp(1), UiPalette.BORDER);
        card.setBackground(surface);
        card.setElevation(dp(2));
        return card;
    }

    private LinearLayout statusRow(String number, String title) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(46));
        TextView dot = label("●", 12, UiPalette.DIM, false);
        dot.setGravity(Gravity.CENTER);
        row.addView(dot, new LinearLayout.LayoutParams(dp(25),dp(38)));
        TextView name = label(title, 14, INK, true);
        LinearLayout.LayoutParams n = new LinearLayout.LayoutParams(0,-2,1f);
        n.leftMargin = dp(8);
        row.addView(name,n);
        TextView status = label("检查中", 12, MUTED, false);
        status.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        row.addView(status);
        row.setContentDescription(title + "，检查中");
        return row;
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(0xff2B3E57);
        line.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(1)));
        return line;
    }

    private Button accentButton(String name) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(name);
        b.setTextSize(16);
        b.setTextColor(UiPalette.BG);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        b.setMinHeight(dp(60));
        b.setPadding(dp(16),dp(14),dp(16),dp(14));
        GradientDrawable glow = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{UiPalette.ACCENT, 0xff81B8FF});
        glow.setCornerRadius(dp(17));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x557FFFFF),glow,null));
        b.setElevation(dp(7));
        b.setStateListAnimator(null);
        return b;
    }

    private Button outlineButton(String title) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(INK);
        b.setMinHeight(dp(52));
        b.setPadding(dp(12),dp(10),dp(12),dp(10));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x4455CFFF),
                roundFill(0xff223853,dp(13),0xff3C6488),null));
        b.setStateListAnimator(null);
        return b;
    }

    private GradientDrawable roundFill(int fill, float radius, int border) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(radius);
        shape.setStroke(dp(1),border);
        return shape;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }
    private TextView label(String value, int size, int color, boolean strong) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        if (strong) t.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        return t;
    }
    private LinearLayout.LayoutParams spaced(int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin=top;
        lp.bottomMargin=bottom;
        return lp;
    }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private void showSettings() {
        showingSettings = true;
        homePage.setVisibility(View.GONE);
        settingsPage.setVisibility(View.VISIBLE);
        scroll.post(() -> scroll.scrollTo(0,0));
        updateStatus();
    }
    private void showHome() {
        showingSettings = false;
        settingsPage.setVisibility(View.GONE);
        homePage.setVisibility(View.VISIBLE);
        scroll.post(() -> scroll.scrollTo(0,0));
        updateStatus();
    }
    @Override public void onBackPressed() {
        if (showingSettings) showHome();
        else super.onBackPressed();
    }

    private boolean installed(String pkg) {
        return getPackageManager().getLaunchIntentForPackage(pkg) != null;
    }

    private boolean recentlyVerified() {
        Network current=NetworkGate.findVpnNetwork(this);
        return verifiedTarget==target && verifiedNetwork!=null && current!=null &&
                verifiedNetwork.equals(current) &&
                SystemClock.elapsedRealtime()-verifiedAtElapsed<120_000L;
    }

    private void chooseTarget(ServiceTarget next) {
        if(checking||installing) {
            showMessage("正在处理上一项操作，请完成后再切换。",false);
            return;
        }
        target=next;
        launchRequested=false;
        didOpenOtherApp=false;
        verifiedNetwork=null;
        verifiedTarget=null;
        lastNodeSummary="";
        getSharedPreferences("ai_gateway",MODE_PRIVATE).edit()
                .putString("target",target.id).apply();
        showMessage("已选择 "+target.label+"。点击主按钮即可开始。",true);
        updateStatus();
    }

    private void testLocalController() {
        if(checking)return;
        checking=true;
        showMessage("正在验证本机 Clash 控制接口…",true);
        worker.execute(() -> {
            String result;
            try{
                MihomoController client=new MihomoController(ControllerSecrets.endpoint(this),
                        ControllerSecrets.loadSecret(this));
                org.json.JSONObject proxies=client.getProxies();
                String selector=client.findSelector(proxies,ControllerSecrets.group(this),client.mode());
                result="本地接口可达。策略组："+selector+"，候选节点 "+
                        client.members(proxies,selector).size()+" 个。";
            }catch(Exception e){result="本地控制接口不可用："+e.getMessage();}
            final String info=result;
            runOnUiThread(() -> {
                checking=false;
                showMessage(info,!info.contains("不可用"));
                updateStatus();
            });
        });
    }

    private void updateStatus() {
        if (clashStatus == null) return;
        boolean hasClash = installed(AppInstaller.CLASH_PACKAGE);
        boolean hasGpt=installed(target.packageName);
        boolean vpn=NetworkGate.hasVpnTransport(this);
        UiCopy.Model ui=UiCopy.from(hasClash,hasGpt,vpn,recentlyVerified());
        clashStatus.setText(ui.toolStatus);
        vpnStatus.setText(target==ServiceTarget.CLAUDE?
                (vpn ? "当前 VPN 已开启" : "不强制 VPN") : ui.vpnStatus);
        gptStatus.setText(ui.gptStatus);
        int toolColor = hasClash ? UiPalette.READY : CAUTION;
        int vpnColor = target==ServiceTarget.CLAUDE ? UiPalette.ACCENT :
                !vpn ? UiPalette.DIM : recentlyVerified() ? UiPalette.READY : CAUTION;
        int gptColor = hasGpt ? UiPalette.READY : UiPalette.DIM;
        clashStatus.setTextColor(toolColor);
        vpnStatus.setTextColor(vpnColor);
        gptStatus.setTextColor(gptColor);
        toolBullet.setTextColor(toolColor);
        vpnBullet.setTextColor(vpnColor);
        gptBullet.setTextColor(gptColor);
        ((View)clashStatus.getParent()).setContentDescription("连接工具：" + ui.toolStatus);
        ((View)vpnStatus.getParent()).setContentDescription("VPN 网络：" + ui.vpnStatus);
        ((View)gptStatus.getParent()).setContentDescription(target.label+"："+ui.gptStatus);
        if(serviceTitle!=null)serviceTitle.setText(target.label);
        if(gptChoice!=null && geminiChoice!=null && claudeChoice!=null) {
            gptChoice.setBackground(roundFill(target==ServiceTarget.CHATGPT?
                    0xff244A69:UiPalette.SURFACE,dp(16),target==ServiceTarget.CHATGPT?
                    UiPalette.ACCENT:UiPalette.BORDER));
            geminiChoice.setBackground(roundFill(target==ServiceTarget.GEMINI?
                    0xff244A69:UiPalette.SURFACE,dp(16),target==ServiceTarget.GEMINI?
                    UiPalette.ACCENT:UiPalette.BORDER));
            claudeChoice.setBackground(roundFill(target==ServiceTarget.CLAUDE?
                    0xff244A69:UiPalette.SURFACE,dp(16),target==ServiceTarget.CLAUDE?
                    UiPalette.ACCENT:UiPalette.BORDER));
            gptChoice.setSelected(target==ServiceTarget.CHATGPT);
            geminiChoice.setSelected(target==ServiceTarget.GEMINI);
            claudeChoice.setSelected(target==ServiceTarget.CLAUDE);
        }
        kicker.setText(target==ServiceTarget.CLAUDE?
                (hasGpt ? "官方应用就绪" : "官方安装") : ui.eyebrow);
        kicker.setTextColor((target==ServiceTarget.CLAUDE&&hasGpt)||ui.ready ?
                UiPalette.READY : UiPalette.ACCENT);
        String head=target==ServiceTarget.CHATGPT?ui.heading:
                ui.heading.replace("ChatGPT",target.label).replace("GPT",target.label);
        String sub=target==ServiceTarget.CHATGPT?ui.subtitle:
                ui.subtitle.replace("ChatGPT",target.label).replace("GPT",target.label);
        String instruction=target==ServiceTarget.CHATGPT?ui.helper:
                ui.helper.replace("ChatGPT",target.label).replace("GPT",target.label);
        if(target==ServiceTarget.CLAUDE) {
            head=hasGpt ? "Claude，\n从这里开启" : "安装官方 Claude";
            sub=hasGpt ? "符合官方使用条件时，优先保持稳定直连。不必启动 Clash。" :
                    "前往 Anthropic 官方 Google Play 安装，不使用修改版。";
            instruction=hasGpt ? "不自动切换 VPN 节点；账号使用条件请以官方说明为准。" :
                    "安装后返回助手，你将亲自确认使用资格。";
        }
        headline.setText(head);
        description.setText(sub);
        helper.setText(instruction);
        orbit.setSignal(target==ServiceTarget.CLAUDE ? (hasGpt?1:0) :
                recentlyVerified() ? 2 : vpn ? 1 : 0);
        if(nodeStatus!=null)nodeStatus.setText(lastNodeSummary.isEmpty()?
                (target==ServiceTarget.CLAUDE ?
                    "Claude 稳定模式 · 优先直连，不自动更换 IP" :
                    ControllerSecrets.enabled(this)?"智能选点已开启 · 待验证":"节点模式：沿用 Clash 当前选择") :
                lastNodeSummary);
        if(!installing && !checking) {
            String label=target==ServiceTarget.CHATGPT?ui.primary:
                    ui.primary.replace("ChatGPT",target.label).replace("GPT",target.label);
            if (target==ServiceTarget.CLAUDE)
                label = hasGpt ? "打开官方 Claude（稳定模式）" : "前往官方商店安装 Claude";
            if(hasClash && hasGpt && vpn && ClaudeSafetyPolicy.shouldAutoSelect(target,ControllerSecrets.enabled(this)))
                label="智能选点并启动 "+target.label;
            primary.setText(label+"  →");
        }
        if (toolSetupButton != null) toolSetupButton.setText(hasClash ? "已安装 · 打开 Clash" : "安装连接工具");
        if (toolStepTitle != null) toolStepTitle.setText(hasClash ? "连接工具已安装" : "安装连接工具");
        if (installGptStepTitle != null) installGptStepTitle.setText(
                installed(AppInstaller.CHATGPT_PACKAGE) ? "ChatGPT 已安装" : "安装 ChatGPT");
        String installerStatus = getSharedPreferences(InstallResultReceiver.PREFS, MODE_PRIVATE)
                .getString(InstallResultReceiver.LAST_RESULT, "");
        if (!installerStatus.isEmpty() && message != null && !checking && !installing &&
                message.getVisibility() != View.VISIBLE) {
            showMessage(installerStatus, !installerStatus.contains("失败"));
        }
    }

    private void showMessage(String content, boolean good) {
        showNotice(message, content, good);
        showNotice(settingsMessage, content, good);
    }

    private void showNotice(TextView target, String content, boolean good) {
        if (target == null) return;
        target.setText(content);
        target.setTextColor(good ? UiPalette.READY : CAUTION);
        target.setVisibility(content == null || content.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void startFlow() {
        if(checking||installing)return;
        // Claude's official Android app supports Android 8+, and need not depend
        // on the bundled ChatGPT APKM (Android 12L+) or arm64 Clash binary.
        if(target==ServiceTarget.CLAUDE) {
            startClaudeSafeFlow();
            return;
        }
        if(Build.VERSION.SDK_INT<32) {
            showMessage("这套三应用离线安装版需要 Android 12L（12.1）以上系统。",false);
            return;
        }
        if(!AppInstaller.supportsIncludedClashApk()) {
            showMessage("整合版安装包仅支持 ARM64 手机。",false);
            return;
        }
        if(!installed(AppInstaller.CLASH_PACKAGE)) {
            launchRequested=false;
            installClash();
            return;
        }
        if(!installed(target.packageName)) {
            launchRequested=false;
            if(target==ServiceTarget.GEMINI)installBundledGemini();
            else if(target==ServiceTarget.CLAUDE)openClaudePlay();
            else installBundledChatGpt();
            return;
        }
        if(!NetworkGate.hasVpnTransport(this)){
            launchRequested=true;
            didOpenOtherApp=true;
            openClash(true);
            return;
        }
        if (!ensureClaudeConfirmed()) return;
        launchRequested=true;
        checkAndContinue(true);
    }

    /** Claude never requires installing or starting a VPN to reach the official app. */
    private void startClaudeSafeFlow() {
        if(!installed(AppInstaller.CLAUDE_PACKAGE)) {
            openClaudePlay();
            return;
        }
        if(!ensureClaudeConfirmed()) return;
        // A running VPN affects all apps. We cannot safely switch it off or claim
        // a network egress country; provide a transparent choice instead.
        if(NetworkGate.hasVpnTransport(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("当前系统 VPN 已开启")
                    .setMessage("Claude 将沿用手机当前的网络出口。本助手不会替你换节点或改变 IP。"+
                            "若你所在地区已获 Claude 支持且可正常直连，通常不必使用 VPN。"+
                            "使用条件需以 Anthropic 官方说明为准，无法保证账号不受限制。")
                    .setPositiveButton("继续使用当前网络",(dialog,which)->launchClaudeDirect())
                    .setNegativeButton("取消",(dialog,which)->showMessage(
                            "未启动 Claude。可先检查网络与账号使用资格。",false))
                    .show();
        } else {
            launchClaudeDirect();
        }
    }

    private void launchClaudeDirect() {
        if(!ensureClaudeConfirmed()) return;
        Intent launch=getPackageManager().getLaunchIntentForPackage(AppInstaller.CLAUDE_PACKAGE);
        if(launch==null) {
            openClaudePlay();
            return;
        }
        try {
            launchRequested=false;
            verifiedNetwork=null;
            verifiedTarget=null;
            startActivity(launch);
            showMessage("已打开官方 Claude；不主动切换 VPN，无法保证不封号。"+
                    "请遵守官方地区、年龄与账号规则。",true);
        }catch(ActivityNotFoundException|SecurityException e) {
            showMessage("Claude 已安装但无法打开，请从桌面尝试官方应用。",false);
        }
    }

    private void installClash() {
        if (installing) return;
        if (!AppInstaller.supportsIncludedClashApk()) {
            showMessage("此内置 Clash 包只适合 arm64 安卓手机。当前设备架构不兼容，已停止安装。", false);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            installAfterPermission = true;
            showMessage("安卓需先允许本助手安装应用。打开设置后开启“允许来自此来源”，再返回这里。", false);
            Intent allow = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            try { startActivity(allow); }
            catch (Exception e) { showMessage("请在安卓设置中允许本助手安装来自此来源的应用。", false); }
            return;
        }
        installing = true;
        primary.setEnabled(false);
        primary.setText("正在准备 Clash 安装…");
        showMessage("正在校验你提供的原始 APK，随后会弹出安卓安装确认。", true);
        worker.execute(() -> {
            String err = null;
            try { AppInstaller.installBundledClash(getApplicationContext()); }
            catch (Exception e) { err = "无法提交安装：" + e.getClass().getSimpleName() + " / " + e.getMessage(); }
            final String result = err;
            runOnUiThread(() -> {
                installing = false;
                primary.setEnabled(true);
                if (result != null) showMessage(result, false);
                else showMessage("已提交系统安装请求，请在安卓弹窗中确认安装 Clash。", true);
                updateStatus();
            });
        });
    }

    private void openClash(boolean attemptStart) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(AppInstaller.CLASH_PACKAGE);
        if (launch == null) {
            showMessage("还没有安装 Clash Meta，请先点击上方按钮安装。", false);
            return;
        }
        if (attemptStart) {
            try {
                Intent command = new Intent("com.github.metacubex.clash.meta.action.START_CLASH");
                command.setClassName(AppInstaller.CLASH_PACKAGE,
                        "com.github.kr328.clash.ExternalControlActivity");
                startActivity(command);
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Some forks or device security policies reject the external control action.
                // Opening Clash UI still lets the user select a profile and start VPN manually.
            }
        }
        try {
            didOpenOtherApp = attemptStart;
            startActivity(launch);
            showMessage(ControllerSecrets.enabled(this) ?
                    "请在 Clash 中确认配置已启用并连接 VPN；返回后助手将尝试自动优选节点。" :
                    "请在 Clash 中导入配置、选择节点并启动 VPN；完成后返回本助手。", true);
        } catch (ActivityNotFoundException | SecurityException e) {
            didOpenOtherApp = false;
            showMessage("Clash 无法打开。请尝试从手机桌面打开它并检查权限。", false);
        }
    }

    private void checkAndContinue(boolean openAfterCheck) {
        if(checking||installing)return;
        if (!ensureClaudeConfirmed()) return;
        if (target==ServiceTarget.CLAUDE && ClaudeSafetyPolicy.inCooldown(
                SystemClock.elapsedRealtime(),getSharedPreferences("ai_gateway",MODE_PRIVATE)
                    .getLong("claude_last_restricted",0L))) {
            showMessage("Claude 上次出现访问限制，稳定模式已暂停重复探测约 30 分钟。" +
                    "请先确认支持地区及账号状态；不会自动更换 IP 继续尝试。",false);
            return;
        }
        if(!installed(AppInstaller.CLASH_PACKAGE)||!NetworkGate.hasVpnTransport(this)){
            showMessage("还没有发现可用的 VPN，先在 Clash 中连接。",false);
            updateStatus();
            return;
        }
        checking=true;
        final ServiceTarget selected=target;
        primary.setEnabled(false);
        primary.setText(ClaudeSafetyPolicy.shouldAutoSelect(target,ControllerSecrets.enabled(this))?
                "正在智能选点…":"正在稳定检查服务连接…");
        showMessage("正在通过 VPN 检测 "+selected.label+"，不会读取账户信息。",true);
        worker.execute(() -> {
            boolean auto=ClaudeSafetyPolicy.shouldAutoSelect(selected,
                    ControllerSecrets.enabled(getApplicationContext()));
            SmartRouteEngine.Outcome outcome=null;
            if(auto){
                outcome=SmartRouteEngine.optimize(getApplicationContext(),selected,
                        (done,total,info) -> mainThread.post(() -> {
                            if(checking) {
                                primary.setText("节点检测："+done+"/"+total);
                                showMessage(info,true);
                            }
                        }));
            }
            final SmartRouteEngine.Outcome resolved=outcome;
            ServiceNetworkGate.Result result;
            if(!auto || (outcome!=null && outcome.ready)) {
                result=ServiceNetworkGate.probe(getApplicationContext(),selected);
            }else {
                result=new ServiceNetworkGate.Result(false,0,
                        outcome==null?"智能选点失败":outcome.message);
            }
            final ServiceNetworkGate.Result site=result;
            runOnUiThread(() -> {
                checking=false;
                primary.setEnabled(true);
                if (selected==ServiceTarget.CLAUDE && ClaudeSafetyPolicy.isRestrictionResponse(site.status)) {
                    getSharedPreferences("ai_gateway",MODE_PRIVATE).edit()
                            .putLong("claude_last_restricted",SystemClock.elapsedRealtime()).apply();
                }
                boolean good=site.reachable&&NetworkGate.hasVpnTransport(this)&&
                        selected==target;
                if(good){
                    verifiedNetwork=NetworkGate.findVpnNetwork(this);
                    verifiedTarget=selected;
                    verifiedAtElapsed=SystemClock.elapsedRealtime();
                    if(resolved!=null && resolved.autoSelected) {
                        lastNodeSummary="已选节点："+resolved.node+" · "+
                                "服务可达（"+resolved.checked+"/"+resolved.total+" 个已检测）";
                    }else {
                        lastNodeSummary="沿用 Clash 当前节点 · "+selected.label+" 网站可达";
                    }
                }else{
                    verifiedNetwork=null;
                    verifiedTarget=null;
                    lastNodeSummary=resolved==null?"节点检测未通过":
                            "节点优选未完成（"+resolved.checked+"/"+resolved.total+"）";
                }
                updateStatus();
                if(!good && selected==ServiceTarget.CLAUDE &&
                        ClaudeSafetyPolicy.isRestrictionResponse(site.status)) {
                    showMessage("Claude 返回了访问/认证限制 (HTTP "+site.status+")。"+
                            "已暂停自动检查；不改变 IP 绕过限制。请在官方渠道核实地区、账号与服务状态。",false);
                    return;
                }
                if(!good) {
                    showMessage("没有通过 "+selected.label+" 的连接验证："+site.detail+
                            "。请在设置中检查控制接口，或去 Clash 手动换节点。",false);
                    return;
                }
                if(!openAfterCheck){
                    showMessage(selected.label+" 网站可达；登录和地区资格须在应用内确认。",true);
                    return;
                }
                launchTarget();
            });
        });
    }

    private void launchTarget() {
        if (!ensureClaudeConfirmed()) return;
        Intent app=getPackageManager().getLaunchIntentForPackage(target.packageName);
        if(app==null){
            showMessage(target.label+" 尚未安装，请先完成安装。",false);
            return;
        }
        if(!recentlyVerified() || !NetworkGate.hasVpnTransport(this)) {
            showMessage("网络尚未验证或选择了另一个服务，请重新检测后打开。",false);
            return;
        }
        try {
            startActivity(app);
            launchRequested=false;
            showMessage("已启动 "+target.label+"。请保持 Clash 连接；" +
                    "网站可达不代表账号已获得服务使用资格。",true);
        }catch(ActivityNotFoundException|SecurityException e){
            showMessage(target.label+" 已安装但无法打开，请检查系统权限。",false);
        }
    }

    private void openChatGpt() {
        // Compatibility wrapper; all current home navigation uses the selected target.
        if(target==ServiceTarget.CHATGPT)launchTarget();
    }

    private void openGemini() {
        if(target==ServiceTarget.GEMINI)launchTarget();
    }

    private void installBundledGemini() {
        if(installing||checking||installed(AppInstaller.GEMINI_PACKAGE))return;
        if(Build.VERSION.SDK_INT<32 || !AppInstaller.supportsIncludedClashApk()){
            showMessage("整合版需要 Android 12L+ 的 ARM64 设备。",false);
            return;
        }
        if(Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()){
            pendingBundledGemini=true;
            installAfterPermission=true;
            requestInstallPermission();
            return;
        }
        installing=true;
        primary.setEnabled(false);
        primary.setText("正在核对 Gemini APK…");
        showMessage("正在确认原始 Gemini 安装包完整性；随后需安卓系统授权。",true);
        worker.execute(() -> {
            String failure=null;
            try{GeminiInstaller.installBundledGemini(getApplicationContext());}
            catch(Exception ex){failure=ex.getMessage();}
            final String result=failure;
            runOnUiThread(() -> {
                installing=false;
                primary.setEnabled(true);
                updateStatus();
                showMessage(result==null?"请在系统安装界面确认安装 Gemini。":
                        "Gemini 安装失败："+result,result==null);
            });
        });
    }

    /** Only a user-picked APK or an explicitly bundled, verified release is installed locally. */
    private void selectChatGptApk() {
        if (installed(AppInstaller.CHATGPT_PACKAGE)) {
            showMessage("ChatGPT 已经安装，无需重复安装。", true);
            return;
        }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        try {
            startActivityForResult(picker, PICK_CHATGPT_APK);
            showMessage("请选择可信来源的 ChatGPT 单个 APK 文件，助手会核验签名。", true);
        } catch (ActivityNotFoundException | SecurityException error) {
            showMessage("无法打开文件选择器，请改用 Google Play 安装。", false);
        }
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_CHATGPT_APK) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            showMessage("已取消选择文件；也可以通过 Google Play 安装。", false);
            return;
        }
        Uri picked = data.getData();
        if (!"content".equals(picked.getScheme())) {
            showMessage("请选择文件管理器中的 ChatGPT 单个 APK 文件。", false);
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some document providers grant only temporary read permission.
        }
        installChatGptFromUri(picked);
    }

    private void installChatGptFromUri(Uri source) {
        if (checking || installing || source == null) return;
        if (installed(AppInstaller.CHATGPT_PACKAGE)) {
            showMessage("ChatGPT 已安装，直接返回首页即可。", true);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            pendingChatGptUri = source;
            installAfterPermission = true;
            requestInstallPermission();
            return;
        }
        installing = true;
        primary.setEnabled(false);
        primary.setText("正在核验 ChatGPT 安装文件…");
        showMessage("正在检查应用名称与 APK 签名，验证通过后由安卓弹窗确认安装。", true);
        worker.execute(() -> {
            String failure = null;
            try { ChatGptSourceInstaller.installSelected(getApplicationContext(), source); }
            catch (Exception error) { failure = error.getMessage() == null ? "安装文件验证失败" : error.getMessage(); }
            final String result = failure;
            runOnUiThread(() -> {
                installing = false;
                primary.setEnabled(true);
                updateStatus();
                if (result == null) showMessage("已核验安装文件，请确认安卓系统的 ChatGPT 安装提示。", true);
                else showMessage("ChatGPT 安装未完成：" + result, false);
            });
        });
    }

    private void installBundledChatGpt() {
        if (installing || checking || installed(AppInstaller.CHATGPT_PACKAGE)) return;
        if (Build.VERSION.SDK_INT < 32) {
            showMessage("这份 ChatGPT 安装包需要 Android 12L 或以上系统，请升级系统或使用兼容版本。", false);
            return;
        }
        if (!AppInstaller.supportsIncludedClashApk()) {
            showMessage("整合安装版仅支持 ARM64 设备。请换用兼容版本。", false);
            return;
        }
        if (!ChatGptSourceInstaller.isBundledAvailable(this)) {
            showMessage("未找到内置 ChatGPT 安装文件，请改用 Google Play。", false);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            pendingBundledChatGpt = true;
            installAfterPermission = true;
            requestInstallPermission();
            return;
        }
        installing = true;
        primary.setEnabled(false);
        primary.setText("正在准备 ChatGPT 分包安装…");
        worker.execute(() -> {
            String failure = null;
            try { ChatGptSourceInstaller.installBundled(getApplicationContext()); }
            catch (Exception error) { failure = error.getMessage() == null ? "安装文件验证失败" : error.getMessage(); }
            final String result = failure;
            runOnUiThread(() -> {
                installing = false;
                primary.setEnabled(true);
                updateStatus();
                showMessage(result == null ? "ChatGPT 已提交安卓安装程序，请在系统窗口点击“安装”。" : "ChatGPT 安装未完成：" + result,
                        result == null);
            });
        });
    }

    private void requestInstallPermission() {
        showMessage("安装前请先允许“GPT 启动助手”安装应用。开启开关后返回这里，助手会继续。", false);
        Intent allow = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName()));
        try { startActivity(allow); }
        catch (ActivityNotFoundException | SecurityException error) {
            showMessage("请在手机设置中允许“GPT 启动助手”安装未知来源的应用。", false);
        }
    }

    /** Consent is self-declared, not an automated location or account eligibility check. */
    private boolean ensureClaudeConfirmed() {
        if(ClaudeSafetyPolicy.canLaunch(target,
                getSharedPreferences("ai_gateway",MODE_PRIVATE)
                    .getBoolean("claude_eligibility_confirmed",false)))return true;
        confirmClaudeEligibility();
        return false;
    }

    private void confirmClaudeEligibility() {
        new AlertDialog.Builder(this)
                .setTitle("Claude 使用地区及账号须知")
                .setMessage("Anthropic 要求用户位于受支持地区并遵守其服务条款。"+
                        "官方要求用户年满 18 周岁。账号封禁无法由 VPN 节点、安装包或本助手保证避免。\n\n"+
                        "Claude 默认固定当前网络节点，不进行自动 IP 轮换；"+
                        "也不自动登录或规避安全验证。仅在你确认真实使用条件符合官方要求时继续。")
                .setPositiveButton("我确认符合官方使用要求", (dialog,which)->{
                    getSharedPreferences("ai_gateway",MODE_PRIVATE).edit()
                            .putBoolean("claude_eligibility_confirmed",true).apply();
                    showMessage("已记录你的确认。现在可以选择 Claude 并检查当前稳定连接；不能保证不封号。",true);
                    updateStatus();
                })
                .setNeutralButton("查看官方支持地区", (dialog,which)->openClaudeRegionHelp())
                .setNegativeButton("取消",(dialog,which)->{
                    showMessage("你可以先查看 Claude 的支持地区和服务条款，再决定是否使用。",false);
                })
                .show();
    }

    private void openWeb(String url) {
        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch (ActivityNotFoundException|SecurityException e) {
            showMessage("设备无法打开官方网页，请检查浏览器。",false);
        }
    }

    private void openClaudeRegionHelp(){openWeb(ClaudeSafetyPolicy.OFFICIAL_REGIONS);}

    private void openClaudePlay() {
        if(installed(AppInstaller.CLAUDE_PACKAGE)){
            showMessage("Claude 已安装，请返回首页选择 Claude。",true);
            return;
        }
        Intent market=new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id="+AppInstaller.CLAUDE_PACKAGE));
        try {
            startActivity(market);
            showMessage("已打开 Google Play 的 Anthropic 官方 Claude。安装和账号登录需本人确认。",true);
        }catch(ActivityNotFoundException|SecurityException e){
            openWeb(ClaudeSafetyPolicy.OFFICIAL_PLAY);
            showMessage("未检测到 Google Play，已尝试官方商店网页。"+
                    "不支持的地区或机型可能无法安装；请勿使用未验证的修改包。",false);
        }
    }

    private void openChatGptPlay() {
        String pkg = AppInstaller.CHATGPT_PACKAGE;
        Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
        try {
            startActivity(market);
            showMessage("已打开官方商店，安装 OpenAI 发布的 ChatGPT 后返回这里。", true);
        } catch (ActivityNotFoundException e) {
            Intent web = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + pkg));
            try {
                startActivity(web);
                showMessage("未找到 Google Play 应用，已打开官方商店网页；部分设备或地区可能无法安装。", false);
            } catch (ActivityNotFoundException | SecurityException ex) {
                showMessage("设备无法打开 Google Play。请检查 Google 服务或咨询设备管理员，勿用来历不明的安装包。", false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
