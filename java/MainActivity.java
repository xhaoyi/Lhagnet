package io.lhagnet.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 主界面（DeepSeek 风格）：
 *   顶栏：风车 + 三个状态灯（手 / 眼 / 模）+ 明暗切换
 *   聊天：用户气泡靠右、助手正文靠左、工具步骤淡淡灰字
 *   页签：聊天 / 技能 / 记忆 / 设置
 */
public class MainActivity extends Activity implements Bus.Listener {

    private static final int REQ_CAPTURE = 1001;
    private static final int REQ_IMPORT = 1002;
    private static final int REQ_PICK_IMAGE = 1003;
    private static final int REQ_PICK_FILE = 1004;
    private static final int TAB_CHAT = 0;
    private static final int TAB_SKILLS = 1;
    private static final int TAB_MEMORY = 2;
    private static final int TAB_CONFIG = 3;
    private static final int TAB_USER = 4;
    private static final int TAB_SCHED = 5;
    private static final int TAB_LANG = 6;
    private static final int TAB_FONT = 7;

    private Prefs cfg;
    private ChatStore chat;
    private SkillsStore skills;
    private MemoryStore memory;
    private Palette p;

    private View root, topBar, inputRow;
    private View drawerLayer, drawerPanel, drawerScrim;
    private TextView btnMenu, drawerTitle, lbClearChat;
    private View pageChat, pageSkills, pageMemory, pageConfig, pageUser, pageSched, pageLang, pageFont;
    // 定时任务
    private EditText etSchedGoal, etSchedMin;
    private CheckBox cbSchedDaily;
    private TextView btnSchedAdd, tvSchedHint, tvSecSched;
    private LinearLayout boxSched;
    private ListView listChat, listSkills, listMemory;
    private ChatAdapter chatAdapter;
    private ArrayAdapter<String> skillsAdapter;
    private final List<SkillsStore.Skill> skillList = new ArrayList<SkillsStore.Skill>();

    private View btnClearChat;
    private TextView tvBanner;
    private TextView chipHand, chipEye, chipBrain, btnTheme, tvThemeLabel;
    private Spinner spTheme;
    private boolean populatingTheme = false;
    private static final String[] THEME_MODES = new String[]{"system", "light", "dark"};
    private static final String[] THEME_NAMES = new String[]{"跟随系统", "浅色", "深色"};
    private View cardTheme;
    private android.widget.ImageView btnSend;
    private EditText etInput;
    private TextView tvPermA11y, tvPermShot, tvPermOverlay, tvPermNotif, tvPermBattery, tvLogPath, tvSkillsHint, tvSendMode, tvVersion, tvCheckUpdate;
    private TextView btnPlus, tvAttach, tvThink;
    private boolean permPromptShowing = false;
    /** 正在思考：输入框上方显示一条会呼吸的「正在思考···」 */
    private boolean thinking = false;
    private int thinkDots = 1;
    private String thinkState = "正在思考";
    private android.os.Handler thinkHandler;
    private long lastSendAt = 0L;
    /** 输入框「＋」带进来的附件（图片存文件、文件存文字） */
    private final List<String> attachImages = new ArrayList<String>();
    private final List<String> attachFileNames = new ArrayList<String>();
    private final StringBuilder attachFileText = new StringBuilder();
    private ArrayAdapter<String> memAdapter;
    private final List<MemRow> memRows = new ArrayList<MemRow>();

    /** "记忆"页的一行：要么是任务履历，要么是站点经验 */
    private static class MemRow {
        static final int HEADER = 0;
        static final int TASK = 1;
        static final int SITE = 2;
        int kind;
        int index;
        String text;

        MemRow(int kind, int index, String text) {
            this.kind = kind;
            this.index = index;
            this.text = text;
        }
    }
    private TextView tvSecPerm, tvSecModel, tvSecSafety;
    private TextView tvModelState, tvModelArrow, tvProviderHint, btnProviderSignup;
    private View rowModelToggle, boxModel;
    private boolean modelBoxOpen = false;
    // 用户管理
    private TextView tvSecUser, lbUserName, tvUserHint, btnSaveUser, btnLogout;
    private TextView tvUserNameTip;
    // 修改密码
    private View rowChgPwd;
    private TextView lbChgPwd, tvChgPwdState, tvChgPwdArrow;
    // 登录页
    private View loginLayer;
    private EditText etLoginPhone, etLoginPwd;
    private TextView btnLogin, tvLoginRegister, tvLoginForgot, tvLoginBrand, tvLoginSub;

    private CheckBox cbAgree, cbAgreeReg;
    private TextView tvAgree, tvAgreeReg;
    // 注册页（独立一屏，从登录页「注册」进来）
    private View registerLayer;
    private EditText etRgPhone, etRgCode, etRgPwd, etRgPwd2, etRgName, etRgQ1, etRgA1, etRgQ2, etRgA2;
    private TextView btnRgCode, btnRgSubmit, tvRgBack, tvRgBrand, tvRgSub;
    private android.os.Handler codeHandler;
    private EditText etUserName;
    private Spinner spProvider, spModel;
    private ArrayAdapter<String> modelAdapter;
    private final List<String> modelList = new ArrayList<String>();
    private boolean populatingModels = false;
    private String uiModel = null;   // 程序化选中的模型名，用来忽略 Spinner 的回调
    private EditText etBaseUrl, etApiKey, etMaxAmount;
    private CheckBox cbVision, cbAutoPay;
    private int currentTab = TAB_CHAT;

    /**
     * 是否为 debug 构建。
     *
     * 用来门禁所有「能通过 Intent 触发副作用」的调试入口。MainActivity 是 exported=true，
     * 第三方 App 可以任意 startActivity 进来，所以这些入口在 release 构建里必须彻底失效。
     *
     * 优先用反射读 BuildConfig.DEBUG（兼容没有 BuildConfig 类的纯源码工程）；
     * 读不到时退回 android:debuggable 应用标记；再退回到 false（安全默认值）。
     */
    private static boolean isDebugBuild() {
        try {
            Class<?> bc = Class.forName("io.lhagnet.app.BuildConfig");
            java.lang.reflect.Field f = bc.getField("DEBUG");
            return f.getBoolean(null);
        } catch (Throwable ignored) {
        }
        try {
            android.content.ApplicationInfo ai = io.lhagnet.app.MainActivity.class.getPackageManager()
                    .getApplicationInfo("io.lhagnet.app", 0);
            return (ai.flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Throwable ignored) {
        }
        return false;   // 安全默认值：读不出来就当作非 debug
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 系统控件（CheckBox / Spinner / 对话框 / 列表项文字）跟随明暗，必须先设主题
        Prefs boot = Prefs.load(this);
        setTheme(boot.darkMode ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        super.onCreate(savedInstanceState);
        Bus.init(getApplicationContext());
        // 字体大小：用系统 fontScale 缩放整个 App 的字号（必须在 setContentView 前）
        Lang.current = boot.lang == null || boot.lang.length() == 0 ? "zh" : boot.lang;
        try {
            float fs = boot.fontScale <= 0 ? 1.0f : boot.fontScale;
            android.content.res.Configuration conf = new android.content.res.Configuration(getResources().getConfiguration());
            conf.fontScale = fs;
            getResources().updateConfiguration(conf, getResources().getDisplayMetrics());
        } catch (Throwable ignored) {
        }
        setContentView(R.layout.activity_main);

        cfg = Prefs.load(this);
        chat = new ChatStore(this);
        skills = new SkillsStore(this);
        memory = new MemoryStore(this);
        p = Palette.of(cfg.darkMode);

        root = findViewById(R.id.root);
        topBar = findViewById(R.id.topBar);
        inputRow = findViewById(R.id.inputRow);
        drawerLayer = findViewById(R.id.drawerLayer);
        drawerPanel = findViewById(R.id.drawerPanel);
        drawerScrim = findViewById(R.id.drawerScrim);
        btnMenu = (TextView) findViewById(R.id.btnMenu);
        drawerTitle = (TextView) findViewById(R.id.drawerTitle);
        lbClearChat = (TextView) findViewById(R.id.lbClearChat);
        pageChat = findViewById(R.id.pageChat);
        pageSkills = findViewById(R.id.pageSkills);
        pageMemory = findViewById(R.id.pageMemory);
        pageConfig = findViewById(R.id.pageConfig);
        pageUser = findViewById(R.id.pageUser);
        pageSched = findViewById(R.id.pageSched);
        loginLayer = findViewById(R.id.loginLayer);
        etLoginPhone = (EditText) findViewById(R.id.etLoginPhone);
        etLoginPwd = (EditText) findViewById(R.id.etLoginPwd);
        btnLogin = (TextView) findViewById(R.id.btnLogin);
        tvLoginRegister = (TextView) findViewById(R.id.tvLoginRegister);
        tvLoginForgot = (TextView) findViewById(R.id.tvLoginForgot);
        tvLoginBrand = (TextView) findViewById(R.id.tvLoginBrand);
        tvLoginSub = (TextView) findViewById(R.id.tvLoginSub);
        cbAgree = (CheckBox) findViewById(R.id.cbAgree);
        tvAgree = (TextView) findViewById(R.id.tvAgree);
        cbAgreeReg = (CheckBox) findViewById(R.id.cbAgreeReg);
        tvAgreeReg = (TextView) findViewById(R.id.tvAgreeReg);
        pageLang = findViewById(R.id.pageLang);
        pageFont = findViewById(R.id.pageFont);
        etSchedGoal = (EditText) findViewById(R.id.etSchedGoal);
        etSchedMin = (EditText) findViewById(R.id.etSchedMin);
        cbSchedDaily = (CheckBox) findViewById(R.id.cbSchedDaily);
        btnSchedAdd = (TextView) findViewById(R.id.btnSchedAdd);
        tvSchedHint = (TextView) findViewById(R.id.tvSchedHint);
        tvSecSched = (TextView) findViewById(R.id.tvSecSched);
        boxSched = (LinearLayout) findViewById(R.id.boxSched);
        tvSecUser = (TextView) findViewById(R.id.tvSecUser);
        lbUserName = (TextView) findViewById(R.id.lbUserName);
        tvUserHint = (TextView) findViewById(R.id.tvUserHint);
        btnSaveUser = (TextView) findViewById(R.id.btnSaveUser);
        btnLogout = (TextView) findViewById(R.id.btnLogout);
        etUserName = (EditText) findViewById(R.id.etUserName);
        tvUserNameTip = (TextView) findViewById(R.id.tvUserNameTip);
        rowChgPwd = findViewById(R.id.rowChgPwd);
        lbChgPwd = (TextView) findViewById(R.id.lbChgPwd);
        tvChgPwdState = (TextView) findViewById(R.id.tvChgPwdState);
        tvChgPwdArrow = (TextView) findViewById(R.id.tvChgPwdArrow);
        registerLayer = findViewById(R.id.registerLayer);
        tvRgBrand = (TextView) findViewById(R.id.tvRgBrand);
        tvRgSub = (TextView) findViewById(R.id.tvRgSub);
        etRgPhone = (EditText) findViewById(R.id.etRgPhone);
        etRgCode = (EditText) findViewById(R.id.etRgCode);
        etRgPwd = (EditText) findViewById(R.id.etRgPwd);
        etRgPwd2 = (EditText) findViewById(R.id.etRgPwd2);
        etRgName = (EditText) findViewById(R.id.etRgName);
        etRgQ1 = (EditText) findViewById(R.id.etRgQ1);
        etRgA1 = (EditText) findViewById(R.id.etRgA1);
        etRgQ2 = (EditText) findViewById(R.id.etRgQ2);
        etRgA2 = (EditText) findViewById(R.id.etRgA2);
        btnRgCode = (TextView) findViewById(R.id.btnRgCode);
        btnRgSubmit = (TextView) findViewById(R.id.btnRgSubmit);
        tvRgBack = (TextView) findViewById(R.id.tvRgBack);
        listChat = (ListView) findViewById(R.id.listChat);
        listSkills = (ListView) findViewById(R.id.listSkills);
        listMemory = (ListView) findViewById(R.id.listMemory);
        btnPlus = (TextView) findViewById(R.id.btnPlus);
        tvAttach = (TextView) findViewById(R.id.tvAttach);
        tvThink = (TextView) findViewById(R.id.tvThink);
        etInput = (EditText) findViewById(R.id.etInput);
        btnSend = (android.widget.ImageView) findViewById(R.id.btnSend);
        btnClearChat = findViewById(R.id.btnClearChat);
        tvBanner = (TextView) findViewById(R.id.tvBanner);
        tvBanner.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openNotificationSettings();
            }
        });
        chipHand = (TextView) findViewById(R.id.chipHand);
        chipEye = (TextView) findViewById(R.id.chipEye);
        chipBrain = (TextView) findViewById(R.id.chipBrain);
        btnTheme = (TextView) findViewById(R.id.btnTheme);
        tvThemeLabel = (TextView) findViewById(R.id.tvThemeLabel);
        spTheme = (Spinner) findViewById(R.id.spTheme);
        cardTheme = findViewById(R.id.cardTheme);
        tvPermA11y = (TextView) findViewById(R.id.tvPermA11y);
        tvPermShot = (TextView) findViewById(R.id.tvPermShot);
        tvPermOverlay = (TextView) findViewById(R.id.tvPermOverlay);
        tvPermNotif = (TextView) findViewById(R.id.tvPermNotif);
        tvPermBattery = (TextView) findViewById(R.id.tvPermBattery);
        tvLogPath = (TextView) findViewById(R.id.tvLogPath);
        tvSkillsHint = (TextView) findViewById(R.id.tvSkillsHint);
        tvSendMode = (TextView) findViewById(R.id.tvSendMode);
        tvVersion = (TextView) findViewById(R.id.tvVersion);
        tvCheckUpdate = (TextView) findViewById(R.id.tvCheckUpdate);
        setupVersionRow();
        setupSendMode();
        tvSecPerm = (TextView) findViewById(R.id.tvSecPerm);
        tvSecModel = (TextView) findViewById(R.id.tvSecModel);
        tvModelState = (TextView) findViewById(R.id.tvModelState);
        tvModelArrow = (TextView) findViewById(R.id.tvModelArrow);
        tvProviderHint = (TextView) findViewById(R.id.tvProviderHint);
        btnProviderSignup = (TextView) findViewById(R.id.btnProviderSignup);
        rowModelToggle = findViewById(R.id.rowModelToggle);
        boxModel = findViewById(R.id.boxModel);
        tvSecSafety = (TextView) findViewById(R.id.tvSecSafety);
        spProvider = (Spinner) findViewById(R.id.spProvider);
        etBaseUrl = (EditText) findViewById(R.id.etBaseUrl);
        etApiKey = (EditText) findViewById(R.id.etApiKey);
        spModel = (Spinner) findViewById(R.id.spModel);
        etMaxAmount = (EditText) findViewById(R.id.etMaxAmount);
        cbVision = (CheckBox) findViewById(R.id.cbVision);
        cbAutoPay = (CheckBox) findViewById(R.id.cbAutoPay);

        setupTabs();
        setupChat();
        setupSkills();
        setupMemory();
        setupConfig();
        setupUser();
        setupRegister();
        setupSched();
        setupLogin();
        Bus.add(this);

        applyTheme();
        refreshChat();
        updateChips();
        fillConfigUi();
        handleIntentExtras(getIntent());
        showTab(currentTab);
        startTicker();
        showPendingAlert();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntentExtras(intent);
        showPendingAlert();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateChips();
        refreshPermLabels();
        // 任务期间界面在后台，聊天记录是服务写的：回来时一定要重读 + 滚到今天最新
        refreshChat();
        // 是被"任务结束"拉回来的：切回聊天页 + 重读记录 + 一句 toast
        if (getIntent() != null && getIntent().getBooleanExtra("fromRun", false)) {
            getIntent().removeExtra("fromRun");
            if (currentTab != TAB_CHAT) showTab(TAB_CHAT);
            closeDrawer();
            refreshChat();
            java.util.List<ChatStore.Msg> all = chat.all();
            for (int i = all.size() - 1; i >= 0; i--) {
                ChatStore.Msg m = all.get(i);
                if (m != null && "agent".equals(m.role) && m.text != null && m.text.length() > 0) {
                    String t = m.text.replace("\n", " ");
                    toast("任务结束：" + (t.length() > 60 ? t.substring(0, 60) + "…" : t));
                    break;
                }
            }
        }
        // 回到 App 说明用户已经在看界面了，常驻的结束提示可以收了
        if (!AgentService.running) Island.hide();
        checkInterrupted();
        showPendingAlert();
        // 后台发了新通告：回前台就拉一次（没登录拉也没意义，静默失败）
        if (Prefs.load(this).loggedIn) checkNotices();
        checkUpdate(false);
        // 回前台顺手心跳一次：服务端发布的通告、后台发起的「拉取技能/日志」都能几秒内生效
        if (Prefs.load(this).loggedIn) pingServer();
        // 单设备登录：回前台先校验一次，之后每 30 秒一次（被别的手机顶掉就弹回登录页）
        verifySession();
        startSessionWatch();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSessionWatch();
    }

    /**
     * 前台服务没在跑、但盘上还留着 runGoal —— 说明上次任务被系统回收了。
     * 这种情况用户只会看到"跑一半什么都没了"，必须在 App 里明确告诉他。
     */
    private void checkInterrupted() {
        Prefs f = Prefs.load(this);
        final String goal = f.runGoal;
        if (goal == null || goal.length() == 0) return;
        if (AgentService.running) return;
        long age = f.runStartedAt > 0 ? System.currentTimeMillis() - f.runStartedAt : 0;
        if (age < 5000) return; // 刚点运行、服务还在起来的窗口期
        final int steps = f.runSteps;
        Prefs.setRun(this, "", 0);
        Bus.emit("warn", "发现上次任务被系统中断（第 " + steps + " 步）");
        chat.add("agent", "【被中断】任务跑到第 " + steps + " 步时被系统中止，没能完成。");
        refreshChat();
        String shortGoal = goal.length() > 26 ? goal.substring(0, 26) + "…" : goal;
        String msg = "任务「" + shortGoal + "」跑到第 " + steps + " 步时被系统中止了：手机把后台进程回收了"
                + "（省电策略），不是你点错了，也不是任务自己跑完了。"
                + "\n\n想少被打断：设置 → 应用 → 风车 → 耗电管理 → 允许后台活动，"
                + "并把「应用启动管理」改成手动管理（自启动 / 关联启动 / 后台活动都打开）。";
        new AlertDialog.Builder(this)
                .setTitle("任务被打断了")
                .setMessage(msg)
                .setPositiveButton("重新执行", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String miss = notReadyChips();
                        if (miss != null) {
                            toast("还不能执行 —— 未就绪：" + miss);
                            showTab(TAB_CONFIG);
                            return;
                        }
                        startTask(goal);
                    }
                })
                .setNegativeButton("知道了", null)
                .show();
    }

    /** 任务失败/缺少 App 这类提示，回到 App 时弹一次 */
    private void showPendingAlert() {
        Prefs fresh = Prefs.load(this);          // 关键：内存里的 cfg 可能是旧的
        final String msg = fresh.pendingAlert;
        if (msg == null || msg.length() == 0) return;
        if (alertShowing) return;
        alertShowing = true;
        fresh.pendingAlert = "";
        fresh.save(this);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                alertShowing = false;
                showPendingAlertNow(msg);
            }
        }, 350);
    }

    private void showPendingAlertNow(String msg) {
        new AlertDialog.Builder(this)
                .setTitle("任务没能执行")
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .setNeutralButton("去装应用", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q="
                                    + Uri.encode("美团"))));
                        } catch (Throwable t) {
                            toast("打不开应用市场，请手动搜索安装");
                        }
                    }
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        Bus.remove(this);
        super.onDestroy();
    }

    @Override
    public void onEvent(String type, String message) {
        if ("act".equals(type)) {
            // agent 真的要动手了，这时候才把界面让出去（聊天不会有这一步）
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        hideKeyboard();
                    } catch (Throwable ignored) {
                    }
                    moveTaskToBack(true);
                }
            });
            return;
        }
        if ("needPerm".equals(type)) {
            // agent 想动手但手/眼没开：弹一个框引导去开，服务那边在等着，开好自动继续
            final String miss = message == null ? "" : message;
            runOnUiThread(new Runnable() {
                public void run() {
                    if (thinking) thinkState = "等你去开权限：" + miss;
                    showNeedPermDialog(miss);
                }
            });
            return;
        }
        // 思考中提示里的副标题跟着进度走（第几步、在干什么）
        if (thinking && message != null && !"debug".equals(type)) {
            String before = thinkState;
            if ("think".equals(type)) {
                thinkState = message.replace("🧠 ", "");
            } else if ("eye".equals(type)) {
                thinkState = "正在看界面…";
            } else if ("warn".equals(type)) {
                thinkState = message;
            } else if ("human".equals(type)) {
                thinkState = "等你确认…";
            }
            if (thinkState.length() > 24) thinkState = thinkState.substring(0, 24) + "…";
            if (!thinkState.equals(before)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        renderThinkBar();
                    }
                });
            }
        }
        if ("done".equals(type) || "err".equals(type)) {
            // 任务结束：把排队的消息接上（排队发送模式）
            runOnUiThread(new Runnable() {
                public void run() { dispatchQueued(); }
            });
            runOnUiThread(new Runnable() {
                public void run() {
                    setThinking(false, null);
                }
            });
        }
        runOnUiThread(new Runnable() {
            public void run() {
                refreshChat();
                updateChips();
                refreshRunState();
            }
        });
    }

    /** "要动手了，还差权限"的引导框（服务在轮询等待，开好就自动继续） */
    private void showNeedPermDialog(String miss) {
        if (permPromptShowing) return;
        permPromptShowing = true;
        String cleanMiss = miss;
        new AlertDialog.Builder(this)
                .setTitle("要动手了，还差权限")
                .setMessage("需要开启：" + cleanMiss
                        + "\n\n我已经在等你，开好之后**自动继续**，不用重新发任务。"
                        + "\n\n· 手（无障碍）：设置 → 辅助功能 → 风车 → 开启"
                        + "\n· 眼（屏幕投射）：点下面的按钮授权一次")
                .setPositiveButton("去开无障碍", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        permPromptShowing = false;
                        openA11ySettings();
                    }
                })
                .setNeutralButton("授权屏幕投射", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        permPromptShowing = false;
                        requestScreenshot();
                    }
                })
                .setNegativeButton("先不做了", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        permPromptShowing = false;
                        stopTask();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface d) {
                        permPromptShowing = false;
                    }
                })
                .show();
    }

    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && etInput != null) imm.hideSoftInputFromWindow(etInput.getWindowToken(), 0);
        } catch (Throwable ignored) {
        }
    }

    // ———————————————————— 主题 ————————————————————

    private void applyTheme() {
        p = Palette.of(cfg.darkMode);

        root.setBackgroundColor(p.bg);
        topBar.setBackgroundColor(p.surface);
        pageChat.setBackgroundColor(p.bg);
        pageSkills.setBackgroundColor(p.bg);
        pageMemory.setBackgroundColor(p.bg);
        pageConfig.setBackgroundColor(p.bg);
        View divider = findViewById(R.id.topDivider);
        if (divider != null) divider.setBackgroundColor(p.border);

        btnTheme.setTextColor(p.textDim);
        btnMenu.setTextColor(p.textDim);
        drawerPanel.setBackgroundColor(p.surface);
        drawerTitle.setTextColor(p.text);
        applyDrawerTitle();
        if (lbClearChat != null) lbClearChat.setTextColor(p.textDim);
        TextView lbFb = (TextView) findViewById(R.id.lbFeedback);
        if (lbFb != null) lbFb.setTextColor(p.textDim);
        android.widget.ImageView icFb = (android.widget.ImageView) findViewById(R.id.icFeedback);
        if (icFb != null) icFb.setColorFilter(p.textDim);
        android.widget.ImageView icClear = (android.widget.ImageView) findViewById(R.id.icClearChat);
        if (icClear != null) icClear.setColorFilter(p.textDim);
        View dd = findViewById(R.id.drawerDivider);
        if (dd != null) dd.setBackgroundColor(p.border);
        btnTheme.setText(p.dark ? "☀" : "☾");
        btnTheme.setBackground(block(p.cardAlt, p.border, dp(18)));
        // 设置页的「外观」行
        if (tvThemeLabel != null) tvThemeLabel.setTextColor(p.text);
        setupThemeSpinner();
        Bus.emit("debug", "theme=" + (p.dark ? "dark" : "light"));

        // 输入胶囊
        inputRow.setBackground(block(p.card, p.border, dp(26)));
        if (android.os.Build.VERSION.SDK_INT >= 21) inputRow.setElevation(dp(2));
        etInput.setTextColor(p.text);
        etInput.setHintTextColor(p.textDim);
        etInput.setBackground(null);
        btnSend.setBackground(block(p.accent, 0, dp(21)));
        if (btnPlus != null) btnPlus.setTextColor(p.textDim);
        if (tvAttach != null) tvAttach.setTextColor(p.accent);
        if (tvThink != null) tvThink.setTextColor(p.textDim);
        refreshRunState();

        // 卡片
        card(R.id.cardTheme);
        card(R.id.cardSendMode);
        card(R.id.cardPerm);
        card(R.id.cardModel);
        card(R.id.cardSafety);
        card(R.id.cardMem);
        card(R.id.cardUser);
        card(R.id.cardSched);

        // 界面骨架文案跟随语言设置
        applyShellTexts();

        // 定时任务页
        if (tvSecSched != null) tvSecSched.setTextColor(p.accent);
        if (tvSchedHint != null) tvSchedHint.setTextColor(p.textFaint);
        if (etSchedGoal != null) {
            etSchedGoal.setTextColor(p.text);
            etSchedGoal.setHintTextColor(p.textFaint);
        }
        if (etSchedMin != null) {
            etSchedMin.setTextColor(p.text);
            etSchedMin.setHintTextColor(p.textFaint);
        }
        if (cbSchedDaily != null) cbSchedDaily.setTextColor(p.textDim);
        if (btnSchedAdd != null) {
            btnSchedAdd.setBackground(block(p.accent, 0, dp(12)));
            btnSchedAdd.setTextColor(p.onAccent);
        }

        // 用户管理页
        if (tvSecUser != null) tvSecUser.setTextColor(p.accent);
        if (tvUserHint != null) tvUserHint.setTextColor(p.textFaint);
        TextView[] userLabels = new TextView[]{lbUserName, lbChgPwd};
        for (TextView t : userLabels) if (t != null) t.setTextColor(p.textDim);
        if (tvChgPwdState != null) tvChgPwdState.setTextColor(p.textFaint);
        if (tvChgPwdArrow != null) tvChgPwdArrow.setTextColor(p.accent);
        if (tvUserNameTip != null) tvUserNameTip.setTextColor(p.textFaint);
        EditText[] userInputs = new EditText[]{etUserName};
        for (EditText e : userInputs) {
            if (e == null) continue;
            e.setTextColor(p.text);
            e.setHintTextColor(p.textFaint);
        }
        if (btnSaveUser != null) {
            btnSaveUser.setBackground(block(p.accent, 0, dp(14)));
            btnSaveUser.setTextColor(p.onAccent);
        }

        // 正文与说明
        tvSkillsHint.setTextColor(p.textDim);
        tvPermA11y.setTextColor(p.text);
        tvPermShot.setTextColor(p.text);
        tvPermOverlay.setTextColor(p.text);
        if (tvPermNotif != null) tvPermNotif.setTextColor(p.text);
        if (tvPermBattery != null) tvPermBattery.setTextColor(p.text);
        tvLogPath.setTextColor(p.textFaint);
        tvSecPerm.setTextColor(p.accent);
        if (tvModelState != null) tvModelState.setTextColor(p.textFaint);
        if (tvModelArrow != null) tvModelArrow.setTextColor(p.accent);
        if (tvProviderHint != null) tvProviderHint.setTextColor(p.textFaint);
        if (btnProviderSignup != null) {
            btnProviderSignup.setBackground(block(p.accentSoft, 0, dp(12)));
            btnProviderSignup.setTextColor(p.accent);
        }
        tvSecModel.setTextColor(p.accent);
        tvSecSafety.setTextColor(p.accent);

        int[] ets = new int[]{R.id.etBaseUrl, R.id.etApiKey, R.id.etMaxAmount};
        for (int i = 0; i < ets.length; i++) {
            EditText et = (EditText) findViewById(ets[i]);
            if (et != null) {
                et.setTextColor(p.text);
                et.setHintTextColor(p.textFaint);
            }
        }
        cbVision.setTextColor(p.textDim);
        cbAutoPay.setTextColor(p.textDim);

        // 软按钮（不再是系统灰按钮）
        int[] soft = new int[]{R.id.btnNewSkill, R.id.btnDelSkill, R.id.btnRestoreSkill, R.id.btnBackupSkill,
                R.id.btnRefreshMem, R.id.btnClearMem, R.id.btnExportMem,
                R.id.btnImportMem, R.id.btnPermA11y,
                R.id.btnPermShot, R.id.btnPermOverlay, R.id.btnFetchModels, R.id.btnTestModel,
                R.id.btnSaveModel, R.id.btnSaveSafety, R.id.btnPermNotif, R.id.btnPermBattery};
        for (int i = 0; i < soft.length; i++) {
            TextView b = (TextView) findViewById(soft[i]);
            if (b == null) continue;
            b.setBackground(block(p.accentSoft, 0, dp(14)));
            b.setTextColor(p.accent);
        }

        listSkills.setDivider(null);
        listSkills.setDividerHeight(0);
        if (listMemory != null) {
            listMemory.setDivider(null);
            listMemory.setDividerHeight(0);
            listMemory.setBackgroundColor(p.card);
            if (memAdapter != null) memAdapter.notifyDataSetChanged();
        }

        showTab(currentTab);
        updateChips();
        if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
    }

    private void card(int id) {
        View v = findViewById(id);
        if (v != null) {
            v.setBackground(block(p.card, p.border, dp(16)));
            if (android.os.Build.VERSION.SDK_INT >= 21) v.setElevation(dp(1));
            // 统一卡片间距：之前卡片是紧贴着的，看着很挤
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).bottomMargin = dp(10);
                v.setLayoutParams(lp);
            }
            v.setPadding(dp(16), dp(14), dp(16), dp(14));
        }
    }

    /** 圆角块；stroke=0 表示不描边 */
    private GradientDrawable block(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    /** 气泡：四角分别指定，做出"指向发送方"的圆润感 */
    private GradientDrawable bubble(int fill, float tl, float tr, float br, float bl) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
        return g;
    }
    /** 外观下拉：跟随系统 / 浅色 / 深色（不再用带颜色的切换按钮） */
    private void setupThemeSpinner() {
        if (spTheme == null) return;
        populatingTheme = true;
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, THEME_NAMES) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextColor(p.text);
                t.setTextSize(14);
                return t;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(position, convertView, parent);
                t.setTextColor(p.text);
                t.setTextSize(14);
                t.setBackgroundColor(p.card);
                t.setPadding(dp(16), dp(12), dp(16), dp(12));
                return t;
            }
        };
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTheme.setAdapter(ad);
        int idx = 0;
        for (int i = 0; i < THEME_MODES.length; i++) if (THEME_MODES[i].equals(cfg.themeMode)) idx = i;
        spTheme.setSelection(idx, false);
        spTheme.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (populatingTheme) { populatingTheme = false; return; }
                String m = THEME_MODES[position];
                if (m.equals(cfg.themeMode)) return;
                cfg.themeMode = m;
                cfg.darkMode = Prefs.resolveDark(MainActivity.this, m);
                cfg.save(MainActivity.this);
                recreate();
            }

            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        populatingTheme = false;
    }

    // ———————————————————— 页签 ————————————————————

    private void setupTabs() {
        btnMenu.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openDrawer();
            }
        });
        drawerScrim.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeDrawer();
            }
        });
        findViewById(R.id.tabChat).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTab(TAB_CHAT);
                closeDrawer();
            }
        });
        findViewById(R.id.tabSkills).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTab(TAB_SKILLS);
                closeDrawer();
            }
        });
        findViewById(R.id.tabMemory).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTab(TAB_MEMORY);
                closeDrawer();
            }
        });
        findViewById(R.id.tabConfig).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTab(TAB_CONFIG);
                closeDrawer();
            }
        });
        findViewById(R.id.tabFeedback).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeDrawer();
                showFeedbackDialog();
            }
        });
        findViewById(R.id.tabUser).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTab(TAB_USER);
                closeDrawer();
            }
        });
        findViewById(R.id.tabSched).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTab(TAB_SCHED);
                closeDrawer();
            }
        });
        findViewById(R.id.tabLang).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTab(TAB_LANG);
                closeDrawer();
            }
        });
        findViewById(R.id.tabFont).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTab(TAB_FONT);
                closeDrawer();
            }
        });
    }

    private void openDrawer() {
        // 没登录就不给开抽屉：登录页/注册页是唯一入口
        if (!loggedInNow()) {
            toast("先登录才能用");
            return;
        }
        if (drawerLayer.getVisibility() == View.VISIBLE) return;
        drawerLayer.setVisibility(View.VISIBLE);
        drawerLayer.setAlpha(0f);
        drawerLayer.animate().alpha(1f).setDuration(140).start();
        drawerPanel.post(new Runnable() {
            public void run() {
                drawerPanel.setTranslationX(-drawerPanel.getWidth());
                drawerPanel.animate().translationX(0f).setDuration(180).start();
            }
        });
    }

    /** 当前是否已登录（同时看内存和落盘状态） */
    private boolean loggedInNow() {
        if (cfg != null && cfg.loggedIn) return true;
        return Prefs.load(this).loggedIn;
    }

    private void closeDrawer() {
        if (drawerLayer.getVisibility() != View.VISIBLE) return;
        drawerPanel.animate().translationX(-drawerPanel.getWidth()).setDuration(150).withEndAction(new Runnable() {
            public void run() {
                drawerLayer.setVisibility(View.GONE);
                drawerPanel.setTranslationX(0f);
            }
        }).start();
        drawerLayer.animate().alpha(0f).setDuration(150).start();
    }

    @Override
    public void onBackPressed() {
        // 注册页 → 回登录页；登录页 → 退到后台（绝不进聊天）
        if (registerLayer != null && registerLayer.getVisibility() == View.VISIBLE) {
            hideRegister();
            if (!loggedInNow()) showLoginIfNeeded();
            return;
        }
        if (!loggedInNow()) {
            moveTaskToBack(true);
            return;
        }
        if (drawerLayer != null && drawerLayer.getVisibility() == View.VISIBLE) {
            closeDrawer();
            return;
        }
        if (currentTab != TAB_CHAT) {
            showTab(TAB_CHAT);
            return;
        }
        super.onBackPressed();
    }
    private void showTab(int tab) {
        currentTab = tab;
        pageChat.setVisibility(tab == TAB_CHAT ? View.VISIBLE : View.GONE);
        pageSkills.setVisibility(tab == TAB_SKILLS ? View.VISIBLE : View.GONE);
        pageMemory.setVisibility(tab == TAB_MEMORY ? View.VISIBLE : View.GONE);
        pageConfig.setVisibility(tab == TAB_CONFIG ? View.VISIBLE : View.GONE);
        if (pageUser != null) pageUser.setVisibility(tab == TAB_USER ? View.VISIBLE : View.GONE);
        if (pageSched != null) pageSched.setVisibility(tab == TAB_SCHED ? View.VISIBLE : View.GONE);
        if (pageLang != null) pageLang.setVisibility(tab == TAB_LANG ? View.VISIBLE : View.GONE);
        if (pageFont != null) pageFont.setVisibility(tab == TAB_FONT ? View.VISIBLE : View.GONE);
        styleNav(R.id.tabChat, R.id.icChat, R.id.lbChat, tab == TAB_CHAT);
        styleNav(R.id.tabSkills, R.id.icSkills, R.id.lbSkills, tab == TAB_SKILLS);
        styleNav(R.id.tabMemory, R.id.icMemory, R.id.lbMemory, tab == TAB_MEMORY);
        styleNav(R.id.tabConfig, R.id.icConfig, R.id.lbConfig, tab == TAB_CONFIG);
        styleNav(R.id.tabUser, R.id.icUser, R.id.lbUser, tab == TAB_USER);
        styleNav(R.id.tabSched, R.id.icSched, R.id.lbSched, tab == TAB_SCHED);
        styleNav(R.id.tabLang, R.id.icLang, R.id.lbLang, tab == TAB_LANG);
        styleNav(R.id.tabFont, R.id.icFont, R.id.lbFont, tab == TAB_FONT);
        if (tab == TAB_SKILLS) refreshSkills();
        if (tab == TAB_MEMORY) refreshMemory();
        if (tab == TAB_CONFIG) refreshPermLabels();
        if (tab == TAB_USER) fillUserUi();
        if (tab == TAB_SCHED) refreshSched();
        if (tab == TAB_LANG) refreshLangPage();
        if (tab == TAB_FONT) refreshFontPage();
    }

    private void styleNav(int itemId, int iconId, int labelId, boolean selected) {
        View item = findViewById(itemId);
        android.widget.ImageView icon = (android.widget.ImageView) findViewById(iconId);
        TextView label = (TextView) findViewById(labelId);
        if (item != null) {
            item.setBackground(selected ? block(p.accentSoft, 0, dp(16)) : null);
        }
        int color = selected ? p.accent : p.textDim;
        // 细线矢量图标：用颜色滤镜上色，选中行变主题色
        if (icon != null) icon.setColorFilter(color);
        if (label != null) {
            label.setTextColor(color);
            label.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }
    /** 抽屉左上角标题：风车 + 小一号的 Beta（Beta 字号是「风车」的一半） */
    private void applyDrawerTitle() {
        if (drawerTitle == null) return;
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("风车");
        int start = sb.length();
        sb.append("Beta");
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.5f), start, sb.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 必须用 SPANNABLE：普通 setText 会把 span 丢掉，Beta 就跟「风车」一样大了
        drawerTitle.setText(sb, android.widget.TextView.BufferType.SPANNABLE);
    }

    // ———————————————————— 状态灯（手 / 眼 / 模）————————————————————

    private void updateChips() {
        boolean hand = LhA11y.ready();
        boolean eye = Build.VERSION.SDK_INT >= 30 ? LhA11y.ready() : ScreenCapture.available();
        boolean brain = cfg.ready();
        styleChip(chipHand, hand);
        styleChip(chipEye, eye);
        styleChip(chipBrain, brain);

        chipHand.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openA11ySettings();
            }
        });
        chipEye.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                requestScreenshot();
            }
        });
        chipBrain.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTab(TAB_CONFIG);
            }
        });
    }

    private void styleChip(TextView chip, boolean on) {
        chip.setTextColor(on ? p.on : p.off);
        chip.setBackground(block(on ? p.onSoft : p.offSoft, 0, dp(14)));
    }

    /** 无障碍/截图状态可能在别处被改，每 2 秒对一次 */
    private boolean alertShowing = false;

    private void startTicker() {
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            public void run() {
                updateChips();
                refreshPermLabels();
                refreshRunState();
                h.postDelayed(this, 2000);
            }
        }, 2000);
    }

    // ———————————————————— 聊天 ————————————————————

    /** 开始/结束"思考中"提示：输入框上方一条会呼吸的「正在思考 ···」，像豆包那样 */
    private void setThinking(boolean on, String state) {
        if (thinkHandler == null) thinkHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        if (on && thinking) {
            if (state != null) thinkState = state;
            return;
        }
        thinking = on;
        thinkDots = 1;
        if (state != null) thinkState = state;
        thinkHandler.removeCallbacksAndMessages(null);
        if (on) {
            Bus.emit("debug", "思考气泡：开");
            renderThinkBar();
            thinkHandler.post(new Runnable() {
                public void run() {
                    if (!thinking) return;
                    thinkDots = thinkDots % 3 + 1;
                    renderThinkBar();
                    thinkHandler.postDelayed(this, 380);
                }
            });
        } else {
            Bus.emit("debug", "思考气泡：关");
            if (tvThink != null) tvThink.setVisibility(View.GONE);
            if (etInput != null) etInput.setHint("给风车说一句话…");
        }
        refreshChat();
    }

    /** 画出/更新「正在思考 ···」这一行（带当前进度副标题） */
    private void renderThinkBar() {
        StringBuilder sb = new StringBuilder("● 正在思考");
        for (int i = 0; i < thinkDots; i++) sb.append("·");
        if (thinkState != null && thinkState.length() > 0 && !"正在思考".equals(thinkState)) {
            sb.append("   ").append(thinkState);
        }
        if (tvThink != null) {
            tvThink.setText(sb.toString());
            tvThink.setVisibility(View.VISIBLE);
        }
        // 输入框的提示字也一起动：万一上面那行被列表挡住，这里也看得见
        if (etInput != null && thinking) {
            StringBuilder h = new StringBuilder("正在思考");
            for (int i = 0; i < thinkDots; i++) h.append("·");
            etInput.setHint(h.toString());
        }
        // 注意：这里绝对不能 Bus.emit —— 会自己触发 onEvent → renderThinkBar → emit 的死循环，
        // 之前就是它把日志刷成 200KB 的「思考栏：正在思考…」
    }

    private void setupChat() {
        chatAdapter = new ChatAdapter();
        listChat.setAdapter(chatAdapter);
        btnSend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (AgentService.running) stopTask();
                else send();
            }
        });
        btnClearChat.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                confirmClearChat();
            }
        });
        btnPlus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showAttachMenu();
            }
        });
        tvAttach.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                clearAttachments();
                toast("已清掉附件");
            }
        });
    }

    /** 「＋」：拍照 / 选图（识别用）/ 选文件（读文字） */
    private void showAttachMenu() {
        final String[] items = new String[]{"拍照（直接拍一张）", "加图片（从相册选）", "加文件（读其中的文字）", "清掉已加的附件"};
        new AlertDialog.Builder(this)
                .setTitle("添加附件")
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) takePhotoForChat();
                        else if (which == 1) pickImage();
                        else if (which == 2) pickFile();
                        else {
                            clearAttachments();
                            toast("已清掉附件");
                        }
                    }
                })
                .show();
    }

    private static final int REQ_TAKE_PHOTO = 4002;
    private android.net.Uri pendingPhotoUri;

    /** 打开系统相机拍一张，拍完的照片存进 MediaStore 再读回来当附件 */
    private void takePhotoForChat() {
        try {
            String name = "fengche-shot-" + System.currentTimeMillis() + ".jpg";
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/风车");
            }
            pendingPhotoUri = getContentResolver()
                    .insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (pendingPhotoUri == null) {
                toast("建不了图片文件（存储受限），可以先截图再从相册选");
                return;
            }
            android.content.Intent i = new android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
            i.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i, REQ_TAKE_PHOTO);
        } catch (Throwable t) {
            toast("打不开相机：" + t);
        }
    }

    /** 把刚拍的照片挂进聊天附件（和「加图片」走同一条路：复制到 App 私有目录再交给模型识别） */
    private void attachTakenPhoto() {
        if (pendingPhotoUri == null) {
            toast("没拿到照片");
            return;
        }
        addImageAttachment(pendingPhotoUri);
        Bus.emit("ok", "已拍照挂为附件");
    }

    private void pickImage() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "选一张图片"), REQ_PICK_IMAGE);
        } catch (Throwable t) {
            toast("打不开相册/文件选择器");
        }
    }

    private void pickFile() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_PICK_FILE);
        } catch (Throwable t) {
            toast("打不开文件选择器");
        }
    }

    /** 图片落到 App 私有目录，之后交给模型（视觉开启时）看 */
    private void addImageAttachment(android.net.Uri uri) {
        try {
            File dir = new File(getFilesDir(), "attach");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "img-" + System.currentTimeMillis() + ".jpg");
            java.io.InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                toast("读不到这张图片");
                return;
            }
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f, false);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            fos.flush();
            fos.close();
            is.close();
            attachImages.add(f.getAbsolutePath());
            updateAttachChip();
            toast("已加图片，发出去时交给模型识别");
        } catch (Throwable t) {
            toast("加图片失败：" + t);
        }
    }

    /** 文本类文件直接把内容读进来（txt/md/json/csv/log…），其它类型只记文件名 */
    private void addFileAttachment(android.net.Uri uri) {
        String name = "文件";
        try {
            android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx);
                c.close();
            }
        } catch (Throwable ignored) {
        }
        String text = "";
        try {
            java.io.InputStream is = getContentResolver().openInputStream(uri);
            if (is != null) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                int total = 0;
                while ((n = is.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                    total += n;
                    if (total > 200 * 1024) break; // 太大就截断，别把上下文撑爆
                }
                is.close();
                text = new String(bos.toByteArray(), "UTF-8");
            }
        } catch (Throwable ignored) {
        }
        boolean texty = name.matches("(?i).*\\.(txt|md|markdown|json|csv|log|xml|html|yml|yaml|ini)$");
        if (texty && text.length() > 0) {
            attachFileNames.add(name);
            attachFileText.append("\n【文件 ").append(name).append("】\n").append(text).append("\n");
            toast("已读入文件 " + name + "（" + text.length() + " 字）");
        } else {
            attachFileNames.add(name + "（非文本，只记录名字）");
            toast("这个文件类型读不出文字，只把名字给了模型");
        }
        updateAttachChip();
    }

    private void clearAttachments() {
        attachImages.clear();
        attachFileNames.clear();
        attachFileText.setLength(0);
        updateAttachChip();
    }

    private void updateAttachChip() {
        if (tvAttach == null) return;
        int n = attachImages.size() + attachFileNames.size();
        if (n == 0) {
            tvAttach.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder("📎 ");
        for (int i = 0; i < attachImages.size(); i++) sb.append(i == 0 ? "" : "、").append("图片" + (i + 1));
        for (int i = 0; i < attachFileNames.size(); i++) {
            sb.append((sb.length() > 2) ? "、" : "").append(attachFileNames.get(i));
        }
        sb.append("（点我清除）");
        tvAttach.setText(sb.toString());
        tvAttach.setVisibility(View.VISIBLE);
    }

    /** 三项能力是否齐全；缺哪个就返回中文说明，全绿返回 null */
    private String notReadyChips() {
        java.util.List<String> miss = new ArrayList<String>();
        if (!LhA11y.ready()) miss.add("手（无障碍）");
        boolean eye = Build.VERSION.SDK_INT >= 30 ? LhA11y.ready() : ScreenCapture.available();
        if (!eye) miss.add("眼（截图）");
        if (!cfg.ready()) miss.add("模（API Key）");
        if (miss.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < miss.size(); i++) {
            if (i > 0) sb.append("、");
            sb.append(miss.get(i));
        }
        return sb.toString();
    }
    private void send() {
        String text = etInput.getText().toString().trim();
        boolean hasAttach = !attachImages.isEmpty() || attachFileText.length() > 0;
        if (text.length() == 0 && !hasAttach) return;
        if (text.length() == 0) {
            // 只发了附件：让模型先看懂附件，再问清要干什么
            text = "我发了附件，先看懂它，然后告诉我你要怎么处理（不确定就问我）";
        }
        // 句子里带"几分钟后 / 今晚8点"这类延后时间 → 不立刻执行，自动排进「任务」
        if (!hasAttach && scheduleIfDelayed(text)) return;
        // 「把这个存成技能」：直接把上次成功的任务沉淀进技能，不用再跑一遍模型
        if (!hasAttach && SkillForge.looksLikeSaveSkillRequest(text)) {
            handleSaveSkillRequest(text);
            return;
        }
        // 只有「模」是硬前提：聊天、问答不需要无障碍和截图
        if (!cfg.ready()) {
            toast("先填 API Key（设置 → 模型），不然我没法回话");
            showTab(TAB_CONFIG);
            return;
        }
        String miss = notReadyChips();
        if (miss != null) {
            // 手/眼没好也放行：聊天照常；真要动手时服务会弹框提示去开权限并等你就绪
            toast("未就绪：" + miss + " —— 现在可以先聊天；要动手时我会提示你去开权限");
            Bus.emit("warn", "能力不全（" + miss + "）：先聊天，动手前会等权限");
        }
        if (AgentService.running) {
            Prefs pf = Prefs.load(this);
            if ("interrupt".equals(pf.sendMode)) {
                // 插话：立刻打断当前任务，把这句话发出去
                Bus.emit("warn", "插话发送：先停掉当前任务");
                stopTask();
                final String keep = text;
                final boolean keepAttach = hasAttach;
                toast("插话发送：已打断上一个任务");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    public void run() {
                        etInput.setText(keep);
                        send();
                    }
                }, 900);
                return;
            }
            // 排队：等当前任务结束再自动发
            Prefs q = Prefs.load(this);
            String qq = q.queuedMsgs == null || q.queuedMsgs.length() == 0 ? text : q.queuedMsgs + "\u0001" + text;
            q.queuedMsgs = qq;
            q.save(this);
            int n = qq.split("\u0001", -1).length;
            etInput.setText("");
            clearAttachments();
            toast("已排队（第 " + n + " 条），当前任务结束后自动发送");
            Bus.emit("ok", "消息已排队（第 " + n + " 条）");
            refreshChat();
            return;
        }
        if (!notificationsEnabled()) {
            toast("提示：通知被关掉了，长任务容易被系统杀掉；聊天不受影响");
        }
        startTask(text);
    }

    /** 「把这个存成技能」：把聊天里上一次成功的任务整理成技能 */
    private void handleSaveSkillRequest(String text) {
        etInput.setText("");
        chat.add("user", text);
        String result;
        try {
            result = SkillForge.saveLastTask(this, chat, null);
        } catch (Throwable t) {
            result = "存技能时出错了：" + t;
        }
        if (result == null) {
            result = "没找到可以存的成功任务——先让我完整跑通一次（结尾是【完成】的那种），"
                    + "再说一句「把这个存成技能」就行。";
            Bus.emit("warn", result);
        } else {
            Bus.emit("ok", "已存为技能（来自上次成功任务）");
        }
        chat.add("agent", result);
        refreshChat();
        refreshSkills();
        toast(result.length() > 40 ? result.substring(0, 40) + "…" : result);
    }

    // ———————————————————— 用户反馈 ————————————————————

    /** 菜单 →「反馈」：把失败/异常/建议发到服务器，后台能直接看到（可拍照附图） */
    private void showFeedbackDialog() {
        final String[] kinds = new String[]{"任务失败", "功能异常", "界面卡住", "建议", "其他"};
        Prefs f = Prefs.load(this);
        final String phone = f.loginPhone != null && f.loginPhone.length() > 0 ? f.loginPhone
                : (f.accountPhone == null ? "" : f.accountPhone);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(6), dp(18), dp(6));

        TextView tip = new TextView(this);
        tip.setText("说说发生了什么：哪一步卡住、报了什么、你期望是什么。写得越具体越好定位问题。");
        tip.setTextSize(12);
        tip.setTextColor(0xFF8B94A3);
        tip.setPadding(0, 0, 0, dp(8));
        box.addView(tip);

        final Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, kinds);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        box.addView(sp);

        final EditText et = new EditText(this);
        et.setHint("例如：打开拼多多搜「猫牙米」，点收藏没反应，一直卡在详情页");
        et.setMinLines(3);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.LEFT);
        et.setTextSize(14);
        box.addView(et);


        final CheckBox cb = new CheckBox(this);
        cb.setText("附上最近一次任务的记录（目标 + 结论）");
        cb.setChecked(true);
        cb.setTextSize(12);
        box.addView(cb);

        // 操作日志：勾上就把运行日志一起传，后台能直接下载下来排障
        final CheckBox cbLog = new CheckBox(this);
        cbLog.setText("附上操作日志（运行日志 + 详细轨迹，约 100~400KB）");
        cbLog.setChecked(true);
        cbLog.setTextSize(12);
        cbLog.setTextColor(0xFFB45309);
        box.addView(cbLog);
        TextView logTip = new TextView(this);
        logTip.setText("日志只含本机运行记录（点了什么、模型回了什么、报了什么错），不含截图与聊天正文。");
        logTip.setTextSize(11);
        logTip.setTextColor(0xFF8B94A3);
        logTip.setPadding(dp(28), 0, 0, dp(4));
        box.addView(logTip);

        new AlertDialog.Builder(this)
                .setTitle("反馈问题")
                .setView(box)
                .setPositiveButton("发送", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String body = et.getText().toString().trim();
                        if (body.length() < 4) {
                            toast("多写两句吧，不然我看不出问题在哪");
                            return;
                        }
                        StringBuilder text = new StringBuilder(body);
                        String goal = "";
                        if (cb.isChecked()) {
                            MemoryStore.Task last = null;
                            for (MemoryStore.Task t : memory.tasks()) last = t;
                            if (last != null) {
                                goal = last.goal == null ? "" : last.goal;
                                text.append("\n\n— 最近一次任务 —\n目标：").append(clipStr(goal, 200))
                                        .append("\n结果：").append(clipStr(last.status, 10))
                                        .append("\n").append(clipStr(last.summary, 600));
                            }
                        }
                        boolean withLog = cbLog != null && cbLog.isChecked();
                        sendFeedback(phone, kinds[sp.getSelectedItemPosition()], text.toString(), goal, withLog);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static String clipStr(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }


    private void sendFeedback(final String phone, final String kind, final String text, final String goal) {
        sendFeedback(phone, kind, text, goal, true);
    }

    /** withLog=true（内测版）时把操作日志一起传上去，后台能下载 */
    private void sendFeedback(final String phone, final String kind, final String text, final String goal,
                              final boolean withLog) {
        final String logText;
        if (withLog) {
            toast("正在打包操作日志…");
            String l;
            try {
                l = LogBundle.collect(this);
            } catch (Throwable t) {
                l = "（日志打包失败：" + t + "）";
            }
            logText = l;
        } else {
            logText = "";
        }
        toast(logText.length() > 0 ? "正在发送（含日志 " + (logText.length() / 1024) + "KB）…" : "正在发送…");
        new Thread(new Runnable() {
            public void run() {
                JSONObject body = new JSONObject();
                try {
                    body.put("phone", phone);
                    body.put("userName", cfg.userName == null ? "" : cfg.userName);
                    body.put("kind", kind);
                    body.put("text", text);
                    body.put("goal", goal);
                    body.put("channel", "app");

                    if (logText.length() > 0) body.put("log", logText);
                    body.put("device", DeviceInfo.collect(MainActivity.this));
                } catch (Throwable ignored) {
                }
                final String r = Net.postJson(Prefs.SERVER.replaceAll("/+$", "") + "/api/feedback",
                        body.toString(), 15000);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) {
                            toast("没发出去：连不上服务器");
                            Bus.emit("warn", "反馈没发出去（网络不通）");
                            return;
                        }
                        try {
                            JSONObject j = new JSONObject(r);
                            if (!j.optBoolean("ok", false)) {
                                toast("反馈没发出去：" + j.optString("message", ""));
                                return;
                            }
                            toast("反馈已发送，谢谢！");
                            Bus.emit("ok", "反馈已提交：" + kind);
                            chat.add("system", "📮 已把这条反馈发给风车（" + kind + "）。"
                                    + "后台能看到，修好了会体现在后面的版本里。");
                            refreshChat();
                        } catch (Throwable t) {
                            toast("服务器返回看不懂");
                        }
                    }
                });
            }
        }, "feedback").start();
    }

    /** 真正下发任务：写聊天记录 + 起前台服务 + 退到后台 */
    private void startTask(String text) {
        // 开跑之前再确认一次「这台还是唯一在线的设备」
        verifySession();
        etInput.setText("");
        StringBuilder shown = new StringBuilder(text);
        if (!attachImages.isEmpty()) shown.append("\n📎 图片 ×").append(attachImages.size());
        if (!attachFileNames.isEmpty()) {
            shown.append("\n📎 文件：");
            for (int k = 0; k < attachFileNames.size(); k++) shown.append(k == 0 ? "" : "、").append(attachFileNames.get(k));
        }
        chat.add("user", shown.toString());
        cfg.lastGoal = text;
        cfg.save(this);
        lastSendAt = System.currentTimeMillis();
        setThinking(true, "正在思考");
        refreshChat();

        Intent i = new Intent(this, AgentService.class);
        i.setAction(AgentService.ACTION_START);
        i.putExtra(AgentService.EXTRA_GOAL, text);
        if (!attachImages.isEmpty()) {
            i.putStringArrayListExtra(AgentService.EXTRA_IMAGES, new ArrayList<String>(attachImages));
        }
        if (attachFileText.length() > 0) {
            i.putExtra(AgentService.EXTRA_FILE_TEXT, attachFileText.toString());
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        clearAttachments();
        // 注意：这里**不再**主动退到后台。只有 agent 真的要动手机时，
        // 服务会发 "act" 事件，onEvent 里才 moveTaskToBack —— 纯聊天时窗口留在原地。
    }
    /** 停止当前任务（通知服务收尾） */
    private void stopTask() {
        Bus.emit("debug", "请求停止任务（running=" + AgentService.running + "）");
        if (!AgentService.running) {
            toast("当前没有任务在跑");
            return;
        }
        Intent i = new Intent(this, AgentService.class);
        i.setAction(AgentService.ACTION_STOP);
        startService(i);
        toast("已发送停止指令，它会在下一步收尾");
        refreshRunState();
    }

    /** 发送键即"运行/停止"：空闲 ↑ 发送，运行中 ■ 停止；连不上模型时变淡 */
    private void refreshRunState() {
        boolean running = AgentService.running;
        // 服务已经收了、也没有新消息进来：把"思考中"气泡撤掉（刚发出的 3 秒内不动，避开启动竞态）
        if (!running && thinking && System.currentTimeMillis() - lastSendAt > 3000) {
            setThinking(false, null);
        }
        if (btnSend == null) return;
        boolean ready = cfg.ready(); // 只有「模」是硬前提：没 API Key 连聊天都不行
        btnSend.setAlpha(ready ? 1f : 0.45f);
        btnSend.setImageResource(running ? R.drawable.ic_stop : R.drawable.ic_send);
        btnSend.setBackground(block(running ? p.off : p.accent, 0, dp(21)));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            btnSend.setColorFilter(running ? 0xFFFFFFFF : p.onAccent);
        }
        btnSend.setContentDescription(running ? "停止任务" : "运行任务");
        if (tvBanner != null) {
            tvBanner.setVisibility(notificationsEnabled() ? View.GONE : View.VISIBLE);
        }
    }

    private void confirmClearChat() {
        if (chat.size() == 0) {
            toast("聊天记录已经是空的");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清空聊天？")
                .setMessage("聊天记录会全部删除，不能恢复（记忆里的任务履历不受影响）。")
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        chat.clear();
                        refreshChat();
                        toast("已清空");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 点（或长按）一条消息：删除 / 复制 */
    private void showMessageMenu(final ChatStore.Msg m) {
        final String[] items = new String[]{"删除这条", "复制文本"};
        new AlertDialog.Builder(this)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            chat.remove(m.role, m.text, m.ts);
                            refreshChat();
                            toast("已删除");
                        } else {
                            android.content.ClipboardManager cm =
                                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("lhagnet",
                                        m.text == null ? "" : m.text));
                            }
                            toast("已复制");
                        }
                    }
                })
                .show();
    }
    private void refreshChat() {
        if (chatAdapter != null) chatAdapter.reload();
        if (listChat != null && chatAdapter != null && chatAdapter.getCount() > 0) {
            final int last = chatAdapter.getCount() - 1;
            listChat.setSelection(last);
            // 刚回来时列表可能还没量好高度，再滚一次，保证新消息露出来
            listChat.post(new Runnable() {
                public void run() {
                    try {
                        listChat.setSelection(last);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
    }

    private class ChatAdapter extends BaseAdapter {
        private List<ChatStore.Msg> data = new ArrayList<ChatStore.Msg>();
        private boolean hint = false;

        void reload() {
            // 先同步磁盘：聊天记录是前台服务写的，界面这份内存副本不重读，
            // 就会出现"任务跑完了、回到聊天页却看不到反馈"
            chat.load();
            data = chat.all();
            hint = data.isEmpty();
            notifyDataSetChanged();
        }

        public int getCount() {
            return hint ? 1 : data.size();
        }

        public Object getItem(int position) {
            return hint ? null : data.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            int width = getResources().getDisplayMetrics().widthPixels;

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setLayoutParams(new android.widget.AbsListView.LayoutParams(
                    android.widget.AbsListView.LayoutParams.MATCH_PARENT,
                    android.widget.AbsListView.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(5), 0, dp(5));

            if (hint) {
                row.setPadding(0, dp(22), 0, 0);
                LinearLayout cardBox = new LinearLayout(MainActivity.this);
                cardBox.setOrientation(LinearLayout.VERTICAL);
                cardBox.setBackground(block(p.card, p.border, dp(20)));
                cardBox.setPadding(dp(20), dp(22), dp(20), dp(22));

                TextView title = new TextView(MainActivity.this);
                title.setText("想聊就聊，想让我动手就说目标");
                title.setTextColor(p.text);
                title.setTextSize(17);
                title.setTypeface(null, android.graphics.Typeface.BOLD);
                cardBox.addView(title);

                TextView sub = new TextView(MainActivity.this);
                sub.setText("· 聊天：「你好」「帮我翻译…」「刚才为什么失败」—— 直接回答你\n"
                        + "· 干活：「打开美团，搜炒饭，找最便宜的 5 份，挑一份下单（下单前问我）」\n"
                        + "· 看不懂的东西：点左下角 ＋ 发张截图或文件，我照着办");
                sub.setTextColor(p.textDim);
                sub.setTextSize(13);
                sub.setLineSpacing(dp(5), 1f);
                LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                subLp.topMargin = dp(10);
                cardBox.addView(sub, subLp);

                row.addView(cardBox, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                return row;
            }

            ChatStore.Msg m = data.get(position);
            String role = m.role == null ? "agent" : m.role;

            if ("user".equals(role)) {
                row.setGravity(Gravity.END);
                TextView tv = new TextView(MainActivity.this);
                tv.setText(m.text);
                tv.setTextColor(p.bubbleUserText);
                tv.setTextSize(15);
                tv.setLineSpacing(dp(3), 1f);
                tv.setBackground(bubble(p.bubbleUser, dp(18), dp(18), dp(6), dp(18)));
                tv.setPadding(dp(15), dp(11), dp(15), dp(11));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = (int) (width * 0.22);
                row.addView(tv, lp);
            } else if ("step".equals(role)) {
                row.setGravity(Gravity.START);
                LinearLayout holder = new LinearLayout(MainActivity.this);
                holder.setOrientation(LinearLayout.HORIZONTAL);
                View bar = new View(MainActivity.this);
                bar.setBackground(block(p.accentSoft, 0, dp(2)));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        dp(3), LinearLayout.LayoutParams.MATCH_PARENT);
                blp.rightMargin = dp(10);
                holder.addView(bar, blp);
                TextView tv = new TextView(MainActivity.this);
                tv.setText(m.text);
                tv.setTextColor(p.textFaint);
                tv.setTextSize(12);
                tv.setLineSpacing(dp(3), 1f);
                holder.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                LinearLayout.LayoutParams holderLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                holderLp.leftMargin = dp(36);
                row.addView(holder, holderLp);
            } else if ("system".equals(role)) {
                row.setGravity(Gravity.START);
                TextView tv = new TextView(MainActivity.this);
                tv.setText(m.text);
                tv.setTextColor(p.off);
                tv.setTextSize(12);
                tv.setLineSpacing(dp(3), 1f);
                tv.setBackground(block(p.offSoft, 0, dp(12)));
                tv.setPadding(dp(12), dp(9), dp(12), dp(9));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = dp(36);
                row.addView(tv, lp);
            } else {
                row.setGravity(Gravity.TOP | Gravity.START);
                TextView tv = new TextView(MainActivity.this);
                tv.setText(m.text);
                tv.setTextColor(p.text);
                tv.setTextSize(15);
                tv.setLineSpacing(dp(4), 1f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = 0;
                lp.rightMargin = (int) (width * 0.08);
                row.addView(tv, lp);
            }
            final ChatStore.Msg mm = m;
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showMessageMenu(mm);
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    showMessageMenu(mm);
                    return true;
                }
            });
            return row;
        }
    }

    // ———————————————————— 技能 ————————————————————

    private void setupSkills() {
        skillsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        listSkills.setAdapter(skillsAdapter);
        listSkills.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < skillList.size()) showSkillDialog(skillList.get(position));
            }
        });
        // 长按条目直接走删除（比先进编辑页再删快）
        listSkills.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= skillList.size()) return false;
                confirmDeleteSkill(skillList.get(position));
                return true;
            }
        });
        findViewById(R.id.btnNewSkill).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showSkillDialog(null);
            }
        });
        findViewById(R.id.btnDelSkill).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showDeleteSkillPicker();
            }
        });
        findViewById(R.id.btnBackupSkill).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("备份技能？")
                        .setMessage("把现在的 " + skillList.size() + " 个技能（含自建正文和内置技能的删除状态）存成一份备份，"
                                + "以后误删了可以点「↺ 恢复备份」找回来。")
                        .setPositiveButton("备份", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                int n = skills.backup();
                                refreshSkills();
                                if (n > 0) {
                                    toast("已备份 " + n + " 个技能");
                                    Bus.emit("ok", "技能已备份（" + n + " 条）");
                                } else {
                                    toast("备份失败，稍后再试");
                                }
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        findViewById(R.id.btnRestoreSkill).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!skills.hasBackup()) {
                    toast("还没有备份，先点「💾 备份」");
                    return;
                }
                String when = android.text.format.DateFormat.format("MM-dd HH:mm",
                        skills.backupAt()).toString();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("恢复备份？")
                        .setMessage("备份时间 " + when + "，共 " + skills.backupCount() + " 条技能。\n\n"
                                + "恢复后：自建技能会按备份重写（备份之后新建的会被删掉），"
                                + "内置技能按备份时的状态恢复（删过的会找回来）。")
                        .setPositiveButton("恢复", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                int n = skills.restoreBackup();
                                refreshSkills();
                                if (n > 0) {
                                    toast("已从备份恢复 " + n + " 条技能");
                                    Bus.emit("ok", "技能已从备份恢复（" + n + " 条）");
                                } else {
                                    toast("备份文件读不出来");
                                }
                            }
                        })
                        // 没有备份、或者只是删掉了内置技能时，用这个把内置技能直接找回来
                        .setNeutralButton("恢复内置技能", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                int hidden = skills.hiddenCount();
                                skills.restoreBuiltin();
                                refreshSkills();
                                toast(hidden > 0 ? ("已找回 " + hidden + " 个内置技能") : "没有隐藏的内置技能");
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
    }

    /** 选一个技能来删（内置的也能删，删了是隐藏、可恢复） */
    private void showDeleteSkillPicker() {
        if (skillList.isEmpty()) {
            toast("还没有技能可删");
            return;
        }
        final String[] items = new String[skillList.size()];
        for (int i = 0; i < skillList.size(); i++) {
            SkillsStore.Skill s = skillList.get(i);
            items[i] = (s.builtin ? "内置 · " : "自建 · ") + s.name
                    + (s.packages.length() > 0 ? "（" + s.packages.split("[,，]")[0].trim() + "）" : "");
        }
        new AlertDialog.Builder(this)
                .setTitle("删除哪个技能？")
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        if (which >= 0 && which < skillList.size()) confirmDeleteSkill(skillList.get(which));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 删除确认：自建直接删文件；内置是隐藏（可「恢复内置」） */
    private void confirmDeleteSkill(final SkillsStore.Skill s) {
        if (s == null) return;
        String msg = s.builtin
                ? "这是内置技能。删除后它就不再加载（进这个 App 时不会再用这份手册），"
                + "随时可以点下方「↺ 恢复内置」找回来。"
                : "自建技能删掉就没了，不能恢复。";
        new AlertDialog.Builder(this)
                .setTitle("删除「" + s.name + "」？")
                .setMessage(msg)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        skills.delete(s);
                        refreshSkills();
                        toast("已删除：" + s.name);
                        Bus.emit("ok", "技能已删除：" + s.name);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshSkills() {
        skills.loadAll();
        skillList.clear();
        skillList.addAll(skills.all());
        List<String> lines = new ArrayList<String>();
        for (SkillsStore.Skill s : skillList) {
            String tag = s.builtin ? "内置" : "自建";
            lines.add("【" + tag + "】" + s.name + "\n"
                    + (s.packages.length() > 0 ? s.packages : "（未指定包名）")
                    + " · " + s.body.length() + " 字");
        }
        if (lines.isEmpty()) lines.add("还没有技能，点下面「＋ 新建技能」建一个");
        skillsAdapter.clear();
        skillsAdapter.addAll(lines);
        skillsAdapter.notifyDataSetChanged();
        // 底部两个按钮一直显示：备份 / 恢复备份（有备份时把时间写在恢复按钮上）
        View restore = findViewById(R.id.btnRestoreSkill);
        if (restore != null) {
            restore.setVisibility(View.VISIBLE);
            TextView rtv = (TextView) restore;
            rtv.setText("↺ 恢复备份");
        }
        if (tvSkillsHint != null) {
            int hidden = skills.hiddenCount();
            String hint = "App 操作手册 · 点条目编辑 · 长按或点「删除」可删";
            if (hidden > 0) hint += " · 已隐藏 " + hidden + " 个内置技能（恢复备份可找回）";
            if (skills.hasBackup()) {
                hint += " · 备份：" + android.text.format.DateFormat.format("MM-dd HH:mm", skills.backupAt())
                        + "（" + skills.backupCount() + " 条）";
            }
            tvSkillsHint.setText(hint);
        }
    }

    private void showSkillDialog(final SkillsStore.Skill skill) {
        final boolean isNew = skill == null;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        final EditText etName = new EditText(this);
        etName.setHint("技能名，例如 美团外卖点单");
        etName.setText(isNew ? "" : skill.name);
        box.addView(etName);

        final EditText etPkgs = new EditText(this);
        etPkgs.setHint("适用包名，逗号分隔，例如 com.sankuai.meituan");
        etPkgs.setText(isNew ? "" : skill.packages);
        box.addView(etPkgs);

        final EditText etBody = new EditText(this);
        etBody.setHint("手册正文：入口在哪、排序在哪、结算页看什么、有哪些坑");
        etBody.setGravity(Gravity.TOP | Gravity.START);
        etBody.setMinLines(6);
        etBody.setText(isNew ? "" : skill.body);
        ScrollView sv = new ScrollView(this);
        sv.addView(etBody);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(240));
        svLp.topMargin = dp(6);
        box.addView(sv, svLp);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isNew ? "新建技能" : "编辑技能（" + (skill.builtin ? "内置，保存后变成自建副本" : "自建") + "）")
                .setView(box)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        SkillsStore.Skill target = isNew ? null : (skill.builtin ? null : skill);
                        skills.save(target, etName.getText().toString().trim(),
                                etPkgs.getText().toString().trim(), etBody.getText().toString().trim());
                        refreshSkills();
                        toast("已保存，进入该 App 时自动生效");
                    }
                })
                .setNegativeButton("取消", null);
        // 内置技能也能删（删=隐藏，可「恢复内置」找回来）
        if (!isNew) {
            b.setNeutralButton("删除", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    confirmDeleteSkill(skill);
                }
            });
        }
        b.show();
    }

    // ———————————————————— 记忆 ————————————————————

    private void setupMemory() {
        memAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = (TextView) v.findViewById(android.R.id.text1);
                if (tv != null) {
                    boolean header = position >= 0 && position < memRows.size()
                            && memRows.get(position).kind == MemRow.HEADER;
                    tv.setTextColor(header ? p.accent : p.text);
                    tv.setTextSize(header ? 12 : 13);
                    tv.setLineSpacing(dp(3), 1f);
                    tv.setPadding(dp(10), dp(header ? 10 : 8), dp(10), dp(header ? 4 : 8));
                }
                return v;
            }
        };
        listMemory.setAdapter(memAdapter);
        listMemory.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= memRows.size()) return;
                MemRow row = memRows.get(position);
                if (row.kind == MemRow.HEADER) return;
                confirmDeleteMemory(row);
            }
        });
        findViewById(R.id.btnRefreshMem).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                memory.load();
                refreshMemory();
            }
        });
        findViewById(R.id.btnClearMem).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("清空记忆？")
                        .setMessage("任务履历与站点经验都会被删除，无法恢复。")
                        .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                memory.clear();
                                refreshMemory();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        findViewById(R.id.btnExportMem).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                exportBackup();
            }
        });
        findViewById(R.id.btnImportMem).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                importBackup();
            }
        });
    }

    private void refreshMemory() {
        memory.load();
        memRows.clear();
        List<MemoryStore.Task> tasks = memory.tasks();
        List<MemoryStore.Site> sites = memory.sites();

        memRows.add(new MemRow(MemRow.HEADER, -1, "任务履历（" + tasks.size() + "）"));
        if (tasks.isEmpty()) {
            memRows.add(new MemRow(MemRow.HEADER, -1, "  还没有执行过任务"));
        } else {
            for (int i = tasks.size() - 1; i >= 0; i--) {
                MemoryStore.Task t = tasks.get(i);
                String time = t.ts > 0 ? new java.text.SimpleDateFormat("MM-dd HH:mm",
                        java.util.Locale.CHINA).format(new java.util.Date(t.ts)) : "";
                memRows.add(new MemRow(MemRow.TASK, i,
                        "[" + t.status + "] " + t.goal + "\n" + time
                                + (t.summary.length() > 0 ? " · " + t.summary : "")));
            }
        }
        memRows.add(new MemRow(MemRow.HEADER, -1, "站点经验（" + sites.size() + "）"));
        if (sites.isEmpty()) {
            memRows.add(new MemRow(MemRow.HEADER, -1, "  任务成功后会在这里沉淀"));
        } else {
            for (int i = sites.size() - 1; i >= 0; i--) {
                MemoryStore.Site s = sites.get(i);
                memRows.add(new MemRow(MemRow.SITE, i, s.pkg + "\n" + s.tip));
            }
        }

        List<String> lines = new ArrayList<String>();
        for (MemRow r : memRows) {
            if (r.kind == MemRow.HEADER) lines.add("—— " + r.text + " ——");
            else lines.add(r.text);
        }
        memAdapter.clear();
        memAdapter.addAll(lines);
        memAdapter.notifyDataSetChanged();
    }

    /** 点某条记忆 → 问一句再删 */
    private void confirmDeleteMemory(final MemRow row) {
        final boolean task = row.kind == MemRow.TASK;
        String what = task ? "这条任务履历" : "这条站点经验";
        new AlertDialog.Builder(this)
                .setTitle("删除" + what + "？")
                .setMessage(row.text + "\n\n删掉后模型就不会再参考它了。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        boolean ok = task ? memory.removeTask(row.index) : memory.removeSite(row.index);
                        refreshMemory();
                        toast(ok ? "已删除这条记忆" : "删不掉了，刷新一下试试");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 导出：记忆 + 技能 → 一个 JSON（存到下载目录 + 复制到剪贴板） */
    private void exportBackup() {
        String json = Backup.exportJson(this, memory, skills);
        if (json.length() == 0) {
            toast("导出失败，稍后再试");
            return;
        }
        String path = Backup.saveFile(this, json);
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("风车 备份", json));
        } catch (Throwable ignored) {
        }
        String msg = "内容：" + memory.tasks().size() + " 条任务履历、"
                + memory.sites().size() + " 条站点经验、"
                + skills.count() + " 个技能。\n\n"
                + (path.length() > 0 ? "文件已存到：" + path + "\n" : "")
                + "备份内容也复制到剪贴板了。\n\n"
                + "换手机时：把这份 JSON 发到新手机（微信发给自己 / 存备忘录都行），"
                + "在新手机上打开 风车 → 记忆 → 导入备份 → 粘贴进去即可。";
        new AlertDialog.Builder(this)
                .setTitle("备份已生成")
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .setNeutralButton("再看一遍 JSON", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("备份 JSON（长按可全选复制）")
                                .setMessage(json)
                                .setPositiveButton("关闭", null)
                                .show();
                    }
                })
                .show();
    }

    /** 导入：粘贴 JSON 或者填文件路径 */
    private void importBackup() {
        final EditText et = new EditText(this);
        et.setHint("把备份 JSON 粘贴到这里，或填文件路径（例如 /sdcard/Download/lhagnet-backup-....json）");
        et.setMinLines(5);
        et.setGravity(Gravity.TOP | Gravity.START);
        ScrollView sv = new ScrollView(this);
        sv.addView(et);
        new AlertDialog.Builder(this)
                .setTitle("导入备份")
                .setMessage("会把备份里的任务履历、站点经验、自建技能合并进来（已有的不会重复）。")
                .setView(sv)
                .setPositiveButton("导入", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String result = Backup.apply(MainActivity.this, memory, skills, et.getText().toString());
                        refreshMemory();
                        refreshSkills();
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("导入结果")
                                .setMessage(result)
                                .setPositiveButton("好", null)
                                .show();
                    }
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("从文件选", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            i.setType("*/*");
                            startActivityForResult(i, REQ_IMPORT);
                        } catch (Throwable t) {
                            toast("打不开文件选择器，改用粘贴 JSON 吧");
                        }
                    }
                })
                .show();
    }

    /** 选完文件后统一走这里 */
    private void applyImportedJson(String json) {
        if (json == null || json.trim().length() == 0) {
            toast("这个文件里没读到内容");
            return;
        }
        String result = Backup.apply(this, memory, skills, json);
        refreshMemory();
        refreshSkills();
        new AlertDialog.Builder(this)
                .setTitle("导入结果")
                .setMessage(result)
                .setPositiveButton("好", null)
                .show();
    }

    // ———————————————————— 设置 ————————————————————

    private void setupConfig() {
        List<String> names = new ArrayList<String>();
        for (Providers.P pr : Providers.ALL) names.add(pr.name);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProvider.setAdapter(ad);
        spProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Providers.P pr = Providers.ALL[position];
                cfg.providerId = pr.id;
                if (pr.baseUrl.length() > 0) {
                    etBaseUrl.setText(pr.baseUrl);
                }
                cfg.save(MainActivity.this);
                applyProviderModels(pr);
                updateProviderHint();
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        modelAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, modelList);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spModel.setAdapter(modelAdapter);
        spModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (populatingModels) return;
                if (position < 0 || position >= modelList.size()) return;
                String v = modelList.get(position);
                if (v.equals(uiModel)) return;   // 程序化恢复的值，不要覆盖用户配置
                if (v.startsWith("＋")) {
                    promptCustomModel();
                    return;
                }
                cfg.model = v;
                uiModel = v;
                cfg.save(MainActivity.this);
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        findViewById(R.id.btnFetchModels).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                fetchModels(false);
            }
        });

        findViewById(R.id.btnPermA11y).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openA11ySettings();
            }
        });
        findViewById(R.id.btnPermOverlay).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                requestOverlay();
            }
        });
        findViewById(R.id.btnPermNotif).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openNotificationSettings();
            }
        });
        findViewById(R.id.btnPermBattery).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                requestIgnoreBattery();
            }
        });
        findViewById(R.id.btnPermShot).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                requestScreenshot();
            }
        });
        findViewById(R.id.btnSaveModel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                saveModelUi();
                toast("模型配置已保存");
                updateChips();
                updateModelHeader();
            }
        });
        if (rowModelToggle != null) {
            rowModelToggle.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    toggleModelBox();
                }
            });
        }
        if (btnProviderSignup != null) {
            btnProviderSignup.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    openProviderSignup();
                }
            });
        }
        updateModelHeader();
        updateProviderHint();
        findViewById(R.id.btnTestModel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                testModel();
            }
        });
        findViewById(R.id.btnSaveSafety).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                saveSafetyUi();
                toast("安全设置已保存");
            }
        });
        tvLogPath.setText("日志：" + (Bus.logFile() == null ? "（未初始化）" : Bus.logFile().getAbsolutePath()));
    }

    private static final String CUSTOM_MODEL = "＋ 自定义模型名…";

    /** 展开/收起「模型」设置那一块（默认收起，要用的时候点一下） */
    private void toggleModelBox() {
        if (boxModel == null) return;
        modelBoxOpen = !modelBoxOpen;
        boxModel.setVisibility(modelBoxOpen ? View.VISIBLE : View.GONE);
        updateModelHeader();
        updateProviderHint();
        if (!modelBoxOpen) hideKeyboard();
    }

    /** 供应商说明 + 「去官网注册 / 拿 API Key」入口（新手点一下就跳到官方注册页） */
    private void updateProviderHint() {
        Providers.P pr = Providers.byId(cfg.providerId);
        String hint = pr == null ? "" : (pr.hint == null ? "" : pr.hint);
        String signup = Providers.signupFor(cfg.providerId);
        if (tvProviderHint != null) {
            StringBuilder sb = new StringBuilder();
            if (hint.length() > 0) sb.append(hint);
            if (signup.length() > 0) {
                sb.append("\n注册/拿 Key：").append(signup);
            } else {
                sb.append("\n这家没有公开注册页，需要自备网关地址。");
            }
            tvProviderHint.setText(sb.toString());
        }
        if (btnProviderSignup != null) {
            btnProviderSignup.setVisibility(signup.length() > 0 ? View.VISIBLE : View.GONE);
            btnProviderSignup.setText("去 " + (pr == null ? "官网" : shortName(pr.name)) + " 注册 / 拿 API Key");
        }
    }

    private static String shortName(String name) {
        if (name == null) return "官网";
        int i = name.indexOf('（');
        String s = i > 0 ? name.substring(0, i) : name;
        if (s.length() > 18) s = s.substring(0, 18) + "…";
        return s;
    }

    /** 用浏览器打开供应商的注册/API Key 页面 */
    private void openProviderSignup() {
        String url = Providers.signupFor(cfg.providerId);
        if (url == null || url.length() == 0) {
            toast("这家没有公开注册页，需要自己填网关地址");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                getApplicationContext().startActivity(i);
            } catch (Throwable t2) {
                toast("打不开浏览器，手动访问：" + url);
            }
        }
    }

    private void updateModelHeader() {
        if (tvModelArrow != null) tvModelArrow.setText(modelBoxOpen ? "▾ 收起" : "▸ 展开");
        if (tvModelState != null) {
            String pName = "";
            Providers.P pr = Providers.byId(cfg.providerId);
            if (pr != null) pName = pr.name;
            String m = cfg.model == null ? "" : cfg.model;
            String s;
            if (m.length() == 0) s = "未设置";
            else if (cfg.apiKey == null || cfg.apiKey.length() == 0) s = m + " · 缺 API Key";
            else s = (pName.length() > 0 ? pName + " · " : "") + m;
            if (s.length() > 34) s = s.substring(0, 34) + "…";
            tvModelState.setText(s);
        }
    }

    /** 切换供应商时，把该供应商的候选模型填进下拉 */
    private void applyProviderModels(Providers.P pr) {
        if (modelAdapter == null) return;
        populatingModels = true;
        modelList.clear();
        if (pr != null && pr.models != null) {
            for (int i = 0; i < pr.models.length; i++) modelList.add(pr.models[i]);
        }
        if (modelList.isEmpty() && pr != null && pr.model.length() > 0) modelList.add(pr.model);
        modelList.add(CUSTOM_MODEL);
        modelAdapter.notifyDataSetChanged();
        if (pr != null && pr.model.length() > 0 && modelList.contains(pr.model)) {
            spModel.setSelection(modelList.indexOf(pr.model));
        }
        // 关键：切换供应商后 spinner 里记录的选中位置可能还是上一条列表的下标（比如 8），
        // 直接拿来索引新列表就会 IndexOutOfBounds 闪退 —— 这里夹一下范围
        int pos = spModel.getSelectedItemPosition();
        if (pos < 0 || pos >= modelList.size()) {
            pos = Math.max(0, modelList.size() - 1);
            spModel.setSelection(pos);
        }
        uiModel = modelList.size() > 0 ? modelList.get(pos) : null;
        populatingModels = false;
        // 没有 baseURL 的供应商（原生协议/CLI）就别去拉模型列表了
        if (cfg.apiKey != null && cfg.apiKey.length() > 0
                && pr != null && pr.baseUrl != null && pr.baseUrl.length() > 0) {
            fetchModels(true);
        }
        updateModelHeader();
    }

    private void selectModel(String model) {
        if (modelAdapter == null || model == null || model.length() == 0) return;
        populatingModels = true;
        if (!modelList.contains(model)) modelList.add(Math.max(0, modelList.size() - 1), model);
        modelAdapter.notifyDataSetChanged();
        spModel.setSelection(modelList.indexOf(model));
        uiModel = model;
        populatingModels = false;
    }

    private void promptCustomModel() {
        final EditText input = new EditText(this);
        input.setHint("输入模型名，例如 my-model");
        input.setText(cfg.model);
        new AlertDialog.Builder(this)
                .setTitle("自定义模型名")
                .setView(input)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String name = input.getText().toString().trim();
                        if (name.length() == 0) return;
                        if (!modelList.contains(name)) modelList.add(Math.max(0, modelList.size() - 1), name);
                        populatingModels = true;
                        modelAdapter.notifyDataSetChanged();
                        spModel.setSelection(modelList.indexOf(name));
                        uiModel = name;
                        populatingModels = false;
                        cfg.model = name;
                        cfg.save(MainActivity.this);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 从端点 /models 拉真实模型列表；quiet=true 时不打扰（切换供应商时用） */
    private void fetchModels(final boolean quiet) {
        String base = etBaseUrl.getText().toString().trim();
        String key = etApiKey.getText().toString().trim();
        if (key.length() == 0) {
            if (!quiet) toast("先填 API Key 再拉取");
            return;
        }
        final Prefs tmp = Prefs.load(this);
        tmp.baseUrl = base;
        tmp.apiKey = key;
        if (!quiet) toast("正在拉取模型列表…");
        new Thread(new Runnable() {
            public void run() {
                final java.util.List<String> got = new ArrayList<String>();
                final String[] err = new String[1];
                try {
                    LlmClient client = new LlmClient(tmp);
                    got.addAll(client.listModels());
                } catch (Throwable t) {
                    err[0] = t.getMessage();
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (got.isEmpty()) {
                            if (!quiet) toast(err[0] == null ? "没拉到模型，先用内置候选" : ("拉取失败：" + err[0]));
                            return;
                        }
                        String cur = cfg.model;
                        populatingModels = true;
                        modelList.clear();
                        modelList.addAll(got);
                        modelList.add(CUSTOM_MODEL);
                        modelAdapter.notifyDataSetChanged();
                        if (cur != null && modelList.contains(cur)) {
                            spModel.setSelection(modelList.indexOf(cur));
                            uiModel = cur;
                        } else {
                            uiModel = modelList.size() > 0 ? modelList.get(spModel.getSelectedItemPosition()) : null;
                        }
                        populatingModels = false;
                        if (!quiet) toast("已拉取 " + got.size() + " 个模型，可选");
                    }
                });
            }
        }, "lhagnet-models").start();
    }
    private void fillConfigUi() {
        int idx = 0;
        for (int i = 0; i < Providers.ALL.length; i++) {
            if (Providers.ALL[i].id.equals(cfg.providerId)) idx = i;
        }
        spProvider.setSelection(idx);
        etBaseUrl.setText(cfg.baseUrl);
        etApiKey.setText(cfg.apiKey);
        applyProviderModels(Providers.byId(cfg.providerId));
        selectModel(cfg.model);
        cbVision.setChecked(cfg.vision);
        etMaxAmount.setText(String.valueOf((int) cfg.maxAmount));
        cbAutoPay.setChecked(cfg.allowAutoPay);
        refreshPermLabels();
    }

    private void saveModelUi() {
        cfg.baseUrl = etBaseUrl.getText().toString().trim();
        cfg.apiKey = etApiKey.getText().toString().trim();
        Object sel = spModel.getSelectedItem();
        if (sel != null) cfg.model = String.valueOf(sel).trim();
        cfg.vision = cbVision.isChecked();
        cfg.save(this);
    }

    private void saveSafetyUi() {
        try {
            cfg.maxAmount = Float.parseFloat(etMaxAmount.getText().toString().trim());
        } catch (Throwable ignored) {
        }
        cfg.allowAutoPay = cbAutoPay.isChecked();
        cfg.save(this);
    }

    private void testModel() {
        saveModelUi();
        if (!cfg.ready()) {
            toast("先填 API Key");
            return;
        }
        toast("正在测试…");
        new Thread(new Runnable() {
            public void run() {
                final String[] box = new String[]{"❓ 没有返回"};
                try {
                    LlmClient client = new LlmClient(cfg);
                    org.json.JSONArray msgs = new org.json.JSONArray();
                    org.json.JSONObject u = new org.json.JSONObject();
                    u.put("role", "user");
                    u.put("content", "只回复两个字：可用");
                    msgs.put(u);
                    LlmClient.Decision d = client.chat(msgs, null);
                    box[0] = "✅ 连接成功：" + (d.content == null ? "" : d.content.trim());
                } catch (Throwable t) {
                    box[0] = "❌ 失败：" + t.getMessage();
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, box[0], Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "lhagnet-test").start();
    }

    private void refreshPermLabels() {
        boolean a11y = LhA11y.ready();
        tvPermA11y.setText(a11y ? "✅ 无障碍：已开启（手）" : "⚠️ 无障碍：未开启 —— 没有它 agent 看不见也点不动");
        String shotText;
        if (Build.VERSION.SDK_INT >= 30) {
            shotText = "✅ 截图：走无障碍 API，无需额外授权（眼）";
        } else {
            shotText = ScreenCapture.available() ? "✅ 截图：屏幕投射已就绪（眼）"
                    : "⚠️ 截图：未授权 —— Android 10 及以下需要录屏投射";
        }
        tvPermShot.setText(shotText);
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        tvPermOverlay.setText(overlay ? "✅ 悬浮窗：已授权（下单/支付前会弹窗问你）"
                : "⚠️ 悬浮窗：未授权（风险动作会被直接拒绝）");
        if (tvPermNotif != null) {
            tvPermNotif.setText(notificationsEnabled()
                    ? "✅ 通知：已允许（前台服务靠它保命）"
                    : "⚠️ 通知被关闭 —— 一旦切到后台就会被系统杀掉，任务会中断得像被重置");
        }
        if (tvPermBattery != null) {
            tvPermBattery.setText(batteryIgnored()
                    ? "✅ 后台保活：已忽略电池优化"
                    : "⚠️ 后台保活：未加入白名单（部分系统可能随时清理后台）");
        }
    }

    private boolean notificationsEnabled() {
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            return nm == null || nm.areNotificationsEnabled();
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean batteryIgnored() {
        try {
            if (Build.VERSION.SDK_INT < 23) return true;
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) {
            return true;
        }
    }

    private void openNotificationSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable t2) {
                toast("打不开通知设置：" + t2);
            }
        }
    }

    private void requestIgnoreBattery() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable t2) {
                toast("打不开设置：" + t2);
            }
        }
    }

    // ———————————————————— 权限跳转 ————————————————————

    private void openA11ySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("找到「风车」打开；系统可能要验证账号密码，输一次即可");
        } catch (Throwable t) {
            toast("打不开设置页：" + t);
        }
    }

    private void requestOverlay() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            toast("打不开授权页：" + t);
        }
    }

    private void requestScreenshot() {
        if (Build.VERSION.SDK_INT >= 30) {
            toast("Android 11+ 用无障碍截图，无需授权");
            return;
        }
        if (!ScreenCapture.requestPermission(this, REQ_CAPTURE)) {
            toast("无法请求录屏授权");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                ScreenCapture.startWithResult(this, resultCode, data);
                toast("截图投射已开启");
            } else {
                toast("没有授权屏幕投射，agent 将只用控件树");
            }
        }
        if (requestCode == REQ_IMPORT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                applyImportedJson(Backup.readFromUri(this, data.getData()));
            } else {
                toast("没有选择文件");
            }
        }
        if (requestCode == REQ_PICK_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                addImageAttachment(data.getData());
            }
        }
        if (requestCode == REQ_PICK_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                addFileAttachment(data.getData());
            }
        }
        if (requestCode == REQ_TAKE_PHOTO) {
            if (resultCode == RESULT_OK) attachTakenPhoto();
            else toast("没拍照");
        }
    }

    // ———————————————————— adb 驱动 ————————————————————

    /** 抓一张当前屏幕的 Bitmap（调试比对用） */
    private android.graphics.Bitmap grabSnap(LhA11y a11y) {
        try {
            LhA11y.Shot s = a11y == null ? null : a11y.shot(720, 62);
            if (s == null) s = ScreenCapture.capture(720, 62);
            if (s == null || s.base64 == null || s.base64.length() == 0) return null;
            byte[] raw = android.util.Base64.decode(s.base64, android.util.Base64.NO_WRAP);
            return android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 处理来自 Intent 的参数。
     *
     * 【安全说明】MainActivity 是 exported=true（带 LAUNCHER），任何第三方 App 都能
     * startActivity 进来。如果这里直接接受 Intent 里的任意配置项，恶意 App 就能：
     *   - 改写 API Key / BaseUrl，把用户的 App 变成向攻击者服务器转发聊天记录与密钥的跳板
     *   - 触发任务执行、导出全部记忆技能、清空技能库
     *   - 通过 tapXY / analyze 读取屏幕内容并模拟真实点击
     * 因此：所有有副作用的调试入口一律收进 handleDebugExtras()，
     *       并且只在 BuildConfig.DEBUG 为真（debug 构建）时才生效；
     *       release 构建下这些参数被完全忽略。
     * 公开可见的部分只保留「切页面」这种无副作用的导航参数。
     */
    private void handleIntentExtras(Intent intent) {
        if (intent == null) return;
        // 调试专用参数：仅 debug 构建可用
        handleDebugExtras(intent);
        // 无副作用的页面导航：任何构建都允许
        String v;
        if ((v = intent.getStringExtra("tab")) != null) {
            if ("skills".equals(v)) showTab(TAB_SKILLS);
            else if ("memory".equals(v)) showTab(TAB_MEMORY);
            else if ("config".equals(v)) showTab(TAB_CONFIG);
            else if ("user".equals(v)) showTab(TAB_USER);
            else if ("sched".equals(v)) showTab(TAB_SCHED);
            else if ("lang".equals(v)) showTab(TAB_LANG);
            else if ("font".equals(v)) showTab(TAB_FONT);
            else showTab(TAB_CHAT);
        }
    }

    /**
     * 仅供 adb 调试使用的 Intent 参数入口。
     *
     * 【安全】整个方法体被 isDebugBuild() 门禁。release 构建下这里一行都不会执行，
     * 第三方 App 无法通过 Intent 触发任何有副作用的操作。
     *
     * 调试命令示例（仅 debug 构建）：
     *   adb shell am start -n io.lhagnet.app/.MainActivity --es cfgApiKey "sk-xxx"
     *   adb shell am start -n io.lhagnet.app/.MainActivity --es goal "打开美团搜炒饭"
     *   adb shell am start -n io.lhagnet.app/.MainActivity --es tapXY "540,1200"
     *   adb shell am start -n io.lhagnet.app/.MainActivity --es analyze --ei analyzeCount 3
     */
    private void handleDebugExtras(Intent intent) {
        if (intent == null || !isDebugBuild()) return;
        boolean changed = false;
        String v;
        if ((v = intent.getStringExtra("cfgProvider")) != null) {
            cfg.providerId = v;
            changed = true;
        }
        if ((v = intent.getStringExtra("cfgBaseUrl")) != null) {
            cfg.baseUrl = v;
            changed = true;
        }
        if ((v = intent.getStringExtra("cfgApiKey")) != null) {
            cfg.apiKey = v;
            changed = true;
        }
        if ((v = intent.getStringExtra("cfgModel")) != null) {
            cfg.model = v;
            changed = true;
        }
        if (intent.hasExtra("cfgMaxAmount")) {
            cfg.maxAmount = intent.getFloatExtra("cfgMaxAmount", cfg.maxAmount);
            changed = true;
        }
        if (intent.hasExtra("darkMode")) {
            cfg.themeMode = intent.getBooleanExtra("darkMode", cfg.darkMode) ? "dark" : "light";
            cfg.darkMode = Prefs.resolveDark(this, cfg.themeMode);
            changed = true;
        }
        // adb 驱动：--es themeMode system|light|dark
        if ((v = intent.getStringExtra("themeMode")) != null) {
            String m = v.trim();
            if ("system".equals(m) || "light".equals(m) || "dark".equals(m)) {
                cfg.themeMode = m;
                cfg.darkMode = Prefs.resolveDark(this, m);
                changed = true;
            }
        }
        if ((v = intent.getStringExtra("goal")) != null) {
            etInput.setText(v);
            cfg.lastGoal = v;
            changed = true;
        }
        if (changed) {
            cfg.save(this);
            fillConfigUi();
            applyTheme();
            Bus.emit("ok", "已通过 intent 写入配置");
        }
        if (intent.getBooleanExtra("selftest", false)) {
            SelfTest.run(this, intent.getStringExtra("target"), intent.getBooleanExtra("dryrun", false));
        }
        // adb 驱动（仅 debug 构建）：--es regPhone 13800138000 直接打开注册页并预填手机号
        // adb 调试（仅 debug 构建）：--es forgot 13800138000 直接弹「忘记密码」
        // 注意：这里的号码只是示例占位号，不是任何真实用户号码
        if (intent.getStringExtra("forgot") != null) {
            String fp = intent.getStringExtra("forgot").trim();
            if (fp.length() > 0 && etLoginPhone != null) etLoginPhone.setText(fp);
            showForgotDialog();
        }
        if ((v = intent.getStringExtra("regPhone")) != null && etRgPhone != null) {
            etRgPhone.setText(v.trim());
            showRegister();
        }
        // adb 驱动：--es schedGoal "打开抖音刷10个视频" --es schedMin 3  → 直接排一条定时任务
        if ((v = intent.getStringExtra("schedGoal")) != null && v.trim().length() > 0) {
            double mins = 1;
            try {
                mins = Double.parseDouble(intent.getStringExtra("schedMin"));
            } catch (Throwable ignored) {
            }
            long at = System.currentTimeMillis() + (long) (mins * 60 * 1000L);
            new SchedStore(this).add(v.trim(), at, intent.getBooleanExtra("schedDaily", false));
            Scheduler.rearm(this);
            refreshSched();
            Bus.emit("ok", "⏰ 已排定定时任务（" + mins + " 分钟后）：" + v.trim());
        }
        if ((v = intent.getStringExtra("attachImage")) != null) {
            File f = new File(v);
            if (f.exists()) {
                if (!attachImages.contains(f.getAbsolutePath())) attachImages.add(f.getAbsolutePath());
                updateAttachChip();
                Bus.emit("ok", "已挂上图片附件：" + f.getName());
            } else {
                Bus.emit("warn", "attachImage 找不到文件：" + v);
            }
        }
        if (intent.hasExtra("cfgVision")) {
            cfg.vision = intent.getBooleanExtra("cfgVision", cfg.vision);
            cfg.save(this);
            fillConfigUi();
            Bus.emit("ok", "视觉模式 = " + cfg.vision);
        }
        if ((v = intent.getStringExtra("exportBackup")) != null) {
            String json = Backup.exportJson(this, memory, skills);
            String path = v.trim().length() > 0 ? Backup.saveByName(this, v.trim(), json) : Backup.saveFile(this, json);
            Bus.emit("ok", "已导出备份：" + path + "（" + json.length() + " 字节）");
            refreshMemory();
        }
        if ((v = intent.getStringExtra("importBackup")) != null) {
            String result = Backup.apply(this, memory, skills, v);
            Bus.emit("ok", "导入备份：" + result);
            refreshMemory();
            refreshSkills();
        }
        if (intent.getBooleanExtra("start", false)) {
            send();
        }
        if (intent.getBooleanExtra("stop", false)) {
            stopTask();
        }
        // 调试用：清空所有自建技能 + 清掉"已隐藏的内置技能"记录（只留当前内置的）
        if (intent.hasExtra("clearSkills")) {
            int n = skills.clearUserSkills();
            skills.restoreBuiltin();
            refreshSkills();
            Bus.emit("ok", "已清空自建技能 " + n + " 个，隐藏记录也清了");
            android.util.Log.i("LhAgnet", "clearSkills: 删了 " + n + " 个自建技能");
        }
        // 调试用：adb 让 App 用「无障碍手势」点一个真机坐标，验证手势通道本身通不通
        if ((v = intent.getStringExtra("tapXY")) != null) {
            try {
                String[] ab = v.split(",");
                final int tx = Integer.parseInt(ab[0].trim());
                final int ty = Integer.parseInt(ab[1].trim());
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(Math.max(0, getIntent().getIntExtra("tapDelay", 1200)));
                            LhA11y a11y = LhA11y.get();
                            if (a11y == null) {
                                android.util.Log.i("LhAgnet", "tapXY: 无障碍没开");
                                return;
                            }
                            android.graphics.Bitmap before = grabSnap(a11y);
                            boolean ok = a11y.gestureTap(tx, ty);
                            Thread.sleep(700);
                            android.graphics.Bitmap after = grabSnap(a11y);
                            double d = (before != null && after != null) ? ScreenMap.diff(before, after) : -1;
                            android.util.Log.i("LhAgnet", "tapXY(" + tx + "," + ty + ") 注入=" + ok
                                    + " 界面差异=" + (d < 0 ? "算不出" : String.format("%.4f", d)));
                        } catch (Throwable t) {
                            android.util.Log.i("LhAgnet", "tapXY 失败：" + t);
                        }
                    }
                }, "tapxy").start();
            } catch (Throwable ignored) {
            }
        }
        // 调试用：adb 一行命令让 App 把当前屏幕的「文字版面」吐到日志里（不用真跑任务）
        if (intent.hasExtra("analyze")) {
            final int delay = intent.getIntExtra("analyzeDelay", 1500);
            final int rounds = Math.max(1, Math.min(5, intent.getIntExtra("analyzeCount", 1)));
            new Thread(new Runnable() {
                public void run() {
                    for (int k = 0; k < rounds; k++) {
                        try {
                            Thread.sleep(k == 0 ? delay : 2500);
                            LhA11y a11y = LhA11y.get();
                            LhA11y.Shot s = a11y == null ? null : a11y.shot(720, 62);
                            if (s == null) s = ScreenCapture.capture(720, 62);
                            if (s == null || s.base64 == null) {
                                android.util.Log.i("LhAgnet", "analyze[" + k + "]: 拿不到截图");
                                continue;
                            }
                            byte[] raw = android.util.Base64.decode(s.base64, android.util.Base64.NO_WRAP);
                            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length);
                            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                            int dw = dm.widthPixels;   // 真机像素，别用截图缩放后的尺寸
                            int dh = dm.heightPixels;
                            String txt = ScreenMap.describe(bmp, dw, dh);
                            android.util.Log.i("LhAgnet", "analyze[" + k + "]: " + dw + "x" + dh + "\n" + txt);
                            Bus.emit("debug", "屏幕结构：\n" + txt);
                        } catch (Throwable t) {
                            android.util.Log.i("LhAgnet", "analyze 失败：" + t);
                        }
                    }
                }
            }, "analyze").start();
        }
    }


    // ———————————————————— 登录 ————————————————————

    // ———————————————————— 登录页：必须同意条款 ————————————————————

    /** 三份条款的正文（内置，点名字就能看；不联网也能读） */
    private static final String TERMS_USER = "《风车用户协议》\n\n"
            + "一、服务说明\n"
            + "风车是一款运行在你自己手机上的自动化助手，通过无障碍服务与截屏能力，按你的指令操作手机界面。\n\n"
            + "二、账号\n"
            + "1. 你使用手机号 + 验证码（或密码）登录，手机号即账号。\n"
            + "2. 用户名一个月只能修改一次，请勿使用他人的姓名、商标或违法内容。\n"
            + "3. 账号仅限本人使用，你对账号下发生的操作负责。\n\n"
            + "三、使用规范\n"
            + "1. 不得用于刷单、诈骗、赌博、传播违法信息、爬取他人数据等用途。\n"
            + "2. 涉及下单、支付的操作默认需要你本人确认，App 不会自动付款。\n"
            + "3. 你应自行确认所操作 App 的用户协议与其条款。\n\n"
            + "四、责任限制\n"
            + "自动化操作可能因界面改版、网络异常而失败；因不可抗力或第三方服务变化造成的损失，风车不承担赔偿责任。\n\n"
            + "五、协议变更\n"
            + "协议更新后会在 App 内提示，继续使用即视为接受新版本。";

    private static final String TERMS_PRIVACY = "《风车隐私政策》\n\n"
            + "一、我们收集什么\n"
            + "1. 手机号：用于注册、登录、找回密码。\n"
            + "2. 设备信息：品牌、型号、设备名、Android 版本、App 版本、Android ID、屏幕分辨率、系统语言。\n"
            + "3. IMEI / MEID / 序列号：Android 10 起普通应用无法读取，通常会显示“不可用（系统限制）”；"
            + "仅在你授权 root 的情况下才会尝试读取并上报。\n"
            + "4. 网络信息：注册 / 登录时的公网 IP 及其归属地（省、市），用于账号安全与客户统计。\n"
            + "5. 使用数据：你输入的任务目标、任务过程与结果，保存在手机本地；任务履历与站点经验也只在本地。\n"
            + "6. 运行日志：为排查故障，App 会记录运行过程（含任务目标、界面操作步骤、错误信息），"
            + "保存在手机应用私有目录。你主动点击「反馈 → 附上操作日志」时，"
            + "该日志会被上传到运营方服务器用于排障；不上传则日志只留在本机。\n\n"
            + "二、我们怎么用\n"
            + "仅用于：登录鉴权、设备识别、账号找回、故障排查、统计在线客户数。不用于广告推送，不出售给第三方。\n\n"
            + "三、存在哪里\n"
            + "1. 账号与设备信息存在账号服务器上，由运营方维护。\n"
            + "2. 聊天记录、记忆、技能、用户名等保存在手机本地 SharedPreferences / 私有目录。\n"
            + "3. 运行日志保存在应用私有目录（其他 App 无法读取）；上传后按反馈处理流程留存。\n\n"
            + "四、你的权利\n"
            + "你可以随时在 App 内删除记忆与聊天记录、退出登录；需要注销账号或删除服务器上的数据，可联系管理员删除。\n\n"
            + "五、第三方\n"
            + "短信验证码通过中国移动及其他短信通道下发；IP 归属地查询通过公开的 IP 库接口完成。"
            + "这些环节仅传递必要的手机号 / IP，不包含你的聊天内容。\n\n"
            + "六、数据安全\n"
            + "账号密码、手机号与会话凭证均通过 HTTPS 加密传输，App 禁止明文 HTTP 连接；"
            + "本机日志存放于应用私有目录，不写入公共存储。";

    private static final String TERMS_CMCC = "《中国移动认证服务条款》\n\n"
            + "一、服务内容\n"
            + "风车的账号注册与登录，使用中国移动提供的号码认证与短信验证码能力，"
            + "由你手机号所属运营商（中国移动）及其合作方提供底层服务。\n\n"
            + "二、号码使用\n"
            + "1. 你填写的手机号将用于接收验证码短信，运营商可能按当地资费收取短信费用（通常由接收方免费）。\n"
            + "2. 请确保手机号为你本人所有；冒用他人号码注册的，风车有权停用该账号。\n\n"
            + "三、运营商责任\n"
            + "号码认证、短信下发、网络接入由运营商负责，其服务中断、延迟、短信被拦截等情况不属于风车可控范围。\n\n"
            + "四、条款关系\n"
            + "本条款与中国移动相关服务条款共同适用；如有冲突，就运营商服务部分以运营商条款为准。";

    private void setupAgreeRow() {
        Prefs f = Prefs.load(this);
        // 登录页一个、注册页一个，勾了任意一个都算"同意过"，下次进来自动勾上
        setupOneAgree(cbAgree, tvAgree, f.agreedTerms);
        setupOneAgree(cbAgreeReg, tvAgreeReg, f.agreedTerms);
    }

    private void setupOneAgree(final CheckBox cb, TextView tv, boolean agreed) {
        if (cb != null) {
            cb.setChecked(agreed);
            cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                    if (checked) {
                        Prefs p2 = Prefs.load(MainActivity.this);
                        p2.agreedTerms = true;
                        p2.save(MainActivity.this);
                    }
                }
            });
        }
        if (tv != null) {
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
            sb.append("我已阅读并同意 ");
            appendLink(sb, "《用户协议》", TERMS_USER);
            sb.append("、");
            appendLink(sb, "《隐私政策》", TERMS_PRIVACY);
            sb.append(" 与 ");
            appendLink(sb, "《中国移动认证服务条款》", TERMS_CMCC);
            tv.setText(sb);
            tv.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
            // 点文字（非链接处）也当作勾选，省得非要去点那个小方框
            tv.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (cb != null) cb.setChecked(!cb.isChecked());
                }
            });
        }
    }

    private void appendLink(android.text.SpannableStringBuilder sb, String label, final String body) {
        int start = sb.length();
        sb.append(label);
        sb.setSpan(new android.text.style.ClickableSpan() {
            @Override
            public void onClick(View widget) {
                showTermsDialog(label.replace("《", "").replace("》", ""), body);
            }

            @Override
            public void updateDrawState(android.text.TextPaint ds) {
                ds.setColor(0xFF2563EB);
                ds.setUnderlineText(false);
            }
        }, start, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void showTermsDialog(String title, String body) {
        TextView tv = new TextView(this);
        tv.setText(body);
        tv.setTextSize(13);
        tv.setLineSpacing(dp(4), 1.0f);
        tv.setTextColor(0xFF111827);
        tv.setPadding(dp(18), dp(14), dp(18), dp(8));
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("知道了", null)
                .show();
    }

    /** 没勾同意就不让登录 / 注册（勾选状态每次都要有；上次同意过的会在打开时自动勾上） */
    private boolean requireAgree() {
        // 登录页看登录那个框，注册页看注册那个框（哪一屏在用就看哪个）
        boolean regOn = registerLayer != null && registerLayer.getVisibility() == View.VISIBLE;
        CheckBox target = regOn ? cbAgreeReg : cbAgree;
        boolean ok = target != null && target.isChecked();
        if (!ok) {
            toast("请先勾选「我已阅读并同意」用户协议、隐私政策与中国移动认证服务条款");
        }
        return ok;
    }

    private void setupLogin() {
        setupAgreeRow();
        if (btnLogin != null) {
            btnLogin.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    doLogin();
                }
            });
        }
        if (tvLoginRegister != null) {
            tvLoginRegister.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showRegister();
                }
            });
        }
        if (tvLoginForgot != null) {
            tvLoginForgot.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showForgotDialog();
                }
            });
        }
        updateLoginUi();
        showLoginIfNeeded();
    }

    private void updateLoginUi() {
        Prefs f = Prefs.load(this);
        if (etLoginPhone != null && etLoginPhone.getText().length() == 0 && f.loginPhone.length() > 0) {
            etLoginPhone.setText(f.loginPhone);
        }
        showLoginIfNeeded();
    }

    /** 登录页固定用「白天」配色（不跟随 App 明暗） */
    private void showLoginIfNeeded() {
        final int bg = 0xFFFFFFFF;
        final int cardBg = 0xFFF5F6F8;
        final int text = 0xFF111827;
        final int dim = 0xFF6B7280;
        final int faint = 0xFF9CA3AF;
        final int accent = 0xFF2563EB;
        final int accentSoft = 0xFFEFF4FF;
        if (loginLayer != null) {
            loginLayer.setBackgroundColor(bg);
            loginLayer.setVisibility(cfg.loggedIn ? View.GONE : View.VISIBLE);
            // 没登录时把登录页压到最上层并吃掉所有触摸，防止点到后面的 ☰/输入框
            loginLayer.setClickable(true);
            if (!cfg.loggedIn) {
                loginLayer.bringToFront();
                if (drawerLayer != null) drawerLayer.setVisibility(View.GONE);
            }
        }
        if (tvLoginBrand != null) tvLoginBrand.setTextColor(text);
        if (tvLoginSub != null) tvLoginSub.setTextColor(dim);
        EditText[] eds = new EditText[]{etLoginPhone, etLoginPwd};
        for (EditText e : eds) {
            if (e == null) continue;
            e.setTextColor(text);
            e.setHintTextColor(faint);
            e.setBackground(block(cardBg, 0xFFE5E7EB, dp(12)));
            e.setPadding(dp(12), dp(10), dp(12), dp(10));
        }
        if (tvLoginRegister != null) tvLoginRegister.setTextColor(accent);
        if (tvLoginForgot != null) tvLoginForgot.setTextColor(dim);
        if (tvAgree != null) tvAgree.setTextColor(dim);
        if (cbAgree != null) cbAgree.setTextColor(dim);
        if (tvAgreeReg != null) tvAgreeReg.setTextColor(dim);
        if (cbAgreeReg != null) cbAgreeReg.setTextColor(dim);
        if (btnLogin != null) {
            btnLogin.setBackground(block(accent, 0, dp(14)));
            btnLogin.setTextColor(0xFFFFFFFF);
        }
    }

    private void hideLogin() {
        if (loginLayer != null) loginLayer.setVisibility(View.GONE);
    }

    // ———————————————————— 注册（独立一屏） ————————————————————

    private void setupRegister() {
        if (btnRgCode != null) {
            btnRgCode.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    requestRegCode();
                }
            });
        }
        if (btnRgSubmit != null) {
            btnRgSubmit.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    doRegister();
                }
            });
        }
        if (tvRgBack != null) {
            tvRgBack.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    hideRegister();
                    if (!cfg.loggedIn) showLoginIfNeeded();
                }
            });
        }
    }

    /** 打开注册页（同样是固定白天配色，独立于 App 明暗） */
    private void showRegister() {
        final int bg = 0xFFFFFFFF;
        final int cardBg = 0xFFF5F6F8;
        final int text = 0xFF111827;
        final int dim = 0xFF6B7280;
        final int faint = 0xFF9CA3AF;
        final int accent = 0xFF2563EB;
        final int accentSoft = 0xFFEFF4FF;
        if (registerLayer == null) return;
        registerLayer.setBackgroundColor(bg);
        if (tvRgBrand != null) tvRgBrand.setTextColor(text);
        if (tvRgSub != null) tvRgSub.setTextColor(dim);
        EditText[] eds = new EditText[]{etRgPhone, etRgCode, etRgPwd, etRgPwd2, etRgName, etRgQ1, etRgA1, etRgQ2, etRgA2};
        for (EditText e : eds) {
            if (e == null) continue;
            e.setTextColor(text);
            e.setHintTextColor(faint);
            e.setBackground(block(cardBg, 0xFFE5E7EB, dp(12)));
            e.setPadding(dp(12), dp(10), dp(12), dp(10));
        }
        if (tvRgBack != null) tvRgBack.setTextColor(accent);
        if (btnRgCode != null) {
            btnRgCode.setBackground(block(accentSoft, 0, dp(12)));
            btnRgCode.setTextColor(accent);
        }
        if (btnRgSubmit != null) {
            btnRgSubmit.setBackground(block(accent, 0, dp(14)));
            btnRgSubmit.setTextColor(0xFFFFFFFF);
        }
        // 手机号沿用登录页填过的那个，省一次输入
        if (etRgPhone != null && etRgPhone.getText().length() == 0) {
            String p = etLoginPhone == null ? "" : etLoginPhone.getText().toString().trim();
            if (p.length() == 0) {
                Prefs f = Prefs.load(this);
                p = f.loginPhone == null ? "" : f.loginPhone;
            }
            if (p.length() > 0) etRgPhone.setText(p);
        }
        hideLogin();
        registerLayer.setClickable(true);
        registerLayer.setVisibility(View.VISIBLE);
        registerLayer.bringToFront();
    }

    private void hideRegister() {
        if (registerLayer != null) registerLayer.setVisibility(View.GONE);
    }

    private void requestRegCode() {
        String phone = etRgPhone == null ? "" : etRgPhone.getText().toString().trim();
        if (!phone.matches("1\\d{10}")) {
            toast("填 11 位手机号");
            return;
        }
        askCodeFromServer(Prefs.SERVER, phone, "register");
    }

    private void doRegister() {
        if (!requireAgree()) return;
        String phone = etRgPhone == null ? "" : etRgPhone.getText().toString().trim();
        String code = etRgCode == null ? "" : etRgCode.getText().toString().trim();
        String p1 = etRgPwd == null ? "" : etRgPwd.getText().toString();
        String p2 = etRgPwd2 == null ? "" : etRgPwd2.getText().toString();
        String name = etRgName == null ? "" : etRgName.getText().toString().trim();
        String q1 = etRgQ1 == null ? "" : etRgQ1.getText().toString().trim();
        String a1 = etRgA1 == null ? "" : etRgA1.getText().toString().trim();
        String q2 = etRgQ2 == null ? "" : etRgQ2.getText().toString().trim();
        String a2 = etRgA2 == null ? "" : etRgA2.getText().toString().trim();
        if (!phone.matches("1\\d{10}")) {
            toast("填 11 位手机号");
            return;
        }
        if (code.length() == 0) {
            toast("先点「获取验证码」");
            return;
        }
        if (p1.length() < 4) {
            toast("密码至少 4 位");
            return;
        }
        if (!p1.equals(p2)) {
            toast("两次密码不一样");
            return;
        }
        if (q1.length() == 0 || a1.length() == 0 || q2.length() == 0 || a2.length() == 0) {
            toast("两个找回密码问题都要填：问题和答案都不能空");
            return;
        }
        registerOnServer(Prefs.SERVER, phone, code, p1, name, q1, a1, q2, a2);
    }

    private void loginSuccess(String phone, String pwd) {
        loginSuccess(phone, pwd, null, null);
    }

    private void loginSuccess(String phone, String pwd, String serverName) {
        loginSuccess(phone, pwd, serverName, null);
    }

    /**
     * serverName：服务器上记着的用户名，非空就以它为准（保证两边一致）
     * sid：服务器发的登录会话（一台设备一个），存起来用于「被别的手机登录就下线」的校验
     */
    private void loginSuccess(String phone, String pwd, String serverName, String sid) {
        Prefs f = Prefs.load(this);
        f.loggedIn = true;
        f.loginPhone = phone;
        if (pwd != null && pwd.length() >= 4) f.loginPwdHash = Prefs.hashPwd(pwd);
        f.accountPhone = phone;
        if (sid != null && sid.length() > 0) f.sessionId = sid;
        if (serverName != null && serverName.length() > 0) {
            if (f.userName == null || !serverName.equals(f.userName)) {
                f.userName = serverName;
                if (f.userNameSetAt <= 0) f.userNameSetAt = System.currentTimeMillis();
            }
        }
        f.save(this);
        cfg.loggedIn = true;
        cfg.loginPhone = phone;
        cfg.loginPwdHash = f.loginPwdHash;
        cfg.sessionId = f.sessionId;
        cfg.userName = f.userName;
        cfg.userNameSetAt = f.userNameSetAt;
        kickedHandled = false;
        lastSessionCheck = 0L;
        hideLogin();
        toast("登录成功");
        Bus.emit("ok", "登录成功：" + maskPhone(phone));
        fillUserUi();
        pingServer();
        checkNotices();
    }

    private void doLogin() {
        if (!requireAgree()) return;
        String phone = etLoginPhone == null ? "" : etLoginPhone.getText().toString().trim();
        if (!phone.matches("1\\d{10}")) {
            toast("填 11 位手机号");
            return;
        }
        final Prefs f = Prefs.load(this);
        String pwd = etLoginPwd == null ? "" : etLoginPwd.getText().toString();
        if (pwd.length() < 4) {
            toast("密码至少 4 位；忘了就点「忘记密码」，用找回问题重置");
            return;
        }
        // 一律先让服务器校验：要拿会话 id，也才能把别的手机上那个顶掉。
        // 连不上服务器且本地存过同样的密码时才离线登录（联网后会再校验）。
        boolean canOffline = f.loginPwdHash != null && f.loginPwdHash.length() > 0
                && f.loginPwdHash.equals(Prefs.hashPwd(pwd));
        loginWithServer(phone, null, pwd, canOffline);
    }

    /** 服务器 /api/login：密码或验证码 */
    private void loginWithServer(final String phone, final String code, final String pwd, final boolean allowOffline) {
        final String server = Prefs.SERVER;
        toast("正在登录…");
        new Thread(new Runnable() {
            public void run() {
                JSONObject body = new JSONObject();
                try {
                    body.put("phone", phone);
                    if (code != null) body.put("code", code);
                    if (pwd != null) body.put("password", pwd);
                    body.put("device", DeviceInfo.collect(MainActivity.this));
                } catch (Throwable ignored) {
                }
                final String r = Net.postJson(server.replaceAll("/+$", "") + "/api/login", body.toString(), 15000);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) {
                            if (allowOffline) {
                                toast("连不上服务器，先离线登录；联网后会自动校验设备");
                                loginSuccess(phone, pwd);
                                return;
                            }
                            toast("连不上服务器：" + server);
                            return;
                        }
                        try {
                            JSONObject j = new JSONObject(r);
                            if (!j.optBoolean("ok", false)) {
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("登录失败")
                                        .setMessage(j.optString("message", ""))
                                        .setPositiveButton("知道了", null)
                                        .show();
                                return;
                            }
                            String kickedDevice = j.optString("kickedDevice", "");
                            loginSuccess(phone, pwd, j.optString("userName", ""), j.optString("sid", ""));
                            if (kickedDevice.length() > 0) {
                                toast("已把「" + kickedDevice + "」上的登录顶下线");
                            }
                        } catch (Throwable t) {
                            toast("服务器返回看不懂");
                        }
                    }
                });
            }
        }, "login").start();
    }

    /** 忘记密码：手机号 + 验证码 → 设新密码 */
    /** 忘记密码：手机号 + 两个「找回密码问题」的答案 + 新密码 */
    private void showForgotDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        final EditText phoneEt = new EditText(this);
        phoneEt.setHint("手机号");
        phoneEt.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        phoneEt.setText(etLoginPhone == null ? "" : etLoginPhone.getText().toString().trim());
        box.addView(phoneEt);

        // 问题题目：先放默认文案，拿到服务器上的题目后会替换成你自己设的那两个
        final TextView q1Label = new TextView(this);
        q1Label.setText("问题1：你的爱好是什么？");
        q1Label.setTextSize(13);
        q1Label.setTextColor(0xFF111827);
        q1Label.setPadding(0, dp(10), 0, 0);
        box.addView(q1Label);

        final EditText a1Et = new EditText(this);
        a1Et.setHint("答案1");
        a1Et.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        box.addView(a1Et);

        final TextView q2Label = new TextView(this);
        q2Label.setText("问题2：你的偶像是谁？");
        q2Label.setTextSize(13);
        q2Label.setTextColor(0xFF111827);
        q2Label.setPadding(0, dp(6), 0, 0);
        box.addView(q2Label);

        final EditText a2Et = new EditText(this);
        a2Et.setHint("答案2");
        a2Et.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        box.addView(a2Et);

        final EditText pwdEt = new EditText(this);
        pwdEt.setHint("新密码（至少 4 位）");
        pwdEt.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(pwdEt);

        final EditText pwd2Et = new EditText(this);
        pwd2Et.setHint("再输一次新密码");
        pwd2Et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(pwd2Et);

        TextView tipEt = new TextView(this);
        tipEt.setText("答案就是注册时填的那两个（不区分大小写、空格无所谓）。换个手机号就点下面「刷新题目」。");
        tipEt.setTextSize(11);
        tipEt.setTextColor(0xFF8B94A3);
        tipEt.setPadding(0, dp(6), 0, 0);
        box.addView(tipEt);

        TextView btnReload = new TextView(this);
        btnReload.setText("↻ 刷新题目");
        btnReload.setTextSize(12);
        btnReload.setTextColor(0xFF2563EB);
        btnReload.setPadding(0, dp(8), 0, 0);
        btnReload.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                loadQuestionsFor(phoneEt.getText().toString().trim(), q1Label, q2Label);
            }
        });
        box.addView(btnReload);

        // 打开时按当前手机号拉一次题目
        loadQuestionsFor(phoneEt.getText().toString().trim(), q1Label, q2Label);
        phoneEt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    loadQuestionsFor(phoneEt.getText().toString().trim(), q1Label, q2Label);
                }
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("忘记密码")
                .setView(box)
                .setPositiveButton("重设密码", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String phone = phoneEt.getText().toString().trim();
                        String a1 = a1Et.getText().toString().trim();
                        String a2 = a2Et.getText().toString().trim();
                        String p1 = pwdEt.getText().toString();
                        String p2 = pwd2Et.getText().toString();
                        if (!phone.matches("1\\d{10}")) {
                            toast("手机号不对");
                            return;
                        }
                        if (a1.length() == 0 || a2.length() == 0) {
                            toast("两个问题的答案都要填");
                            return;
                        }
                        if (p1.length() < 4 || !p1.equals(p2)) {
                            toast("两次密码要一致且至少 4 位");
                            return;
                        }
                        resetPasswordOnServer(phone, a1, a2, p1);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 把这个手机号的两个找回问题显示到弹窗的标签上（拿不到就保持默认题目） */
    private void loadQuestionsFor(final String phone, final TextView q1Label, final TextView q2Label) {
        if (phone == null || !phone.matches("1\\d{10}")) return;
        new Thread(new Runnable() {
            public void run() {
                final String r = Net.getBody(Prefs.SERVER.replaceAll("/+$", "") + "/api/security/questions?phone="
                        + Net.encode(phone), 8000);
                if (r == null) return;
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            JSONObject j = new JSONObject(r);
                            if (!j.optBoolean("ok", false)) {
                                String msg = j.optString("message", "");
                                if (msg.length() > 0) {
                                    if (q1Label != null) q1Label.setText("问题1：" + msg);
                                    if (q2Label != null) q2Label.setText("问题2：（这个号还没设第二个问题）");
                                }
                                return;
                            }
                            JSONArray qs = j.optJSONArray("questions");
                            if (qs == null || qs.length() == 0) return;
                            if (q1Label != null) q1Label.setText("问题1：" + qs.optString(0, ""));
                            if (q2Label != null) {
                                q2Label.setText(qs.length() > 1
                                        ? "问题2：" + qs.optString(1, "")
                                        : "问题2：（这个号只设了一个问题）");
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        }, "questions").start();
    }

    private void resetPasswordOnServer(final String phone, final String a1, final String a2, final String newPwd) {
        final String server = Prefs.SERVER;
        toast("正在重设…");
        new Thread(new Runnable() {
            public void run() {
                JSONObject body = new JSONObject();
                try {
                    body.put("phone", phone);
                    JSONArray ans = new JSONArray();
                    ans.put(a1);
                    ans.put(a2);
                    body.put("answers", ans);
                    body.put("newPassword", newPwd);
                } catch (Throwable ignored) {
                }
                final String resp = Net.postJson(server.replaceAll("/+$", "") + "/api/password/reset",
                        body.toString(), 15000);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (resp == null) {
                            toast("连不上服务器");
                            return;
                        }
                        try {
                            JSONObject j = new JSONObject(resp);
                            if (!j.optBoolean("ok", false)) {
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("重设失败")
                                        .setMessage(j.optString("message", ""))
                                        .setPositiveButton("知道了", null)
                                        .show();
                                return;
                            }
                            Prefs f = Prefs.load(MainActivity.this);
                            f.loginPwdHash = Prefs.hashPwd(newPwd);
                            f.loginPhone = phone;
                            f.loggedIn = true;
                            f.save(MainActivity.this);
                            cfg.loginPwdHash = f.loginPwdHash;
                            cfg.loggedIn = true;
                            hideLogin();
                            toast("密码已重设，已帮你登录");
                        } catch (Throwable t) {
                            toast("服务器返回看不懂");
                        }
                    }
                });
            }
        }, "reset").start();
    }

    /** 退出登录：回到登录页（资料和密码摘要保留，下次还能用密码登回来） */
    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("退出登录？")
                .setMessage("退出后会回到登录页。资料、记忆、技能都还在，用手机号 + 密码（或验证码）就能登回来。")
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        if (AgentService.running) stopTask();
                        Prefs f = Prefs.load(MainActivity.this);
                        f.loggedIn = false;
                        f.save(MainActivity.this);
                        cfg.loggedIn = false;
                        if (etLoginPwd != null) etLoginPwd.setText("");
                                        updateLoginUi();
                        if (loginLayer != null) loginLayer.setVisibility(View.VISIBLE);
                        showTab(TAB_CHAT);
                        toast("已退出登录");
                        Bus.emit("ok", "已退出登录");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
    // ———————————————————— 用户管理 ————————————————————

    /** 走服务器：POST /api/sms/send，服务端发码（短信通道未开通时直接把码回给 App） */
    private void askCodeFromServer(final String server, final String phone) {
        askCodeFromServer(server, phone, null);
    }

    /** purpose：注册流程传 "register"，服务器会先查这个号有没有注册过 */
    private void askCodeFromServer(final String server, final String phone, final String purpose) {
        toast("正在请求验证码…");
        new Thread(new Runnable() {
            public void run() {
                final String url = server.replaceAll("/+$", "") + "/api/sms/send";
                JSONObject body = new JSONObject();
                String resp;
                try {
                    body.put("phone", phone);
                    if (purpose != null && purpose.length() > 0) body.put("purpose", purpose);
                    resp = Net.postJson(url, body.toString(), 12000);
                } catch (Throwable t) {
                    resp = null;
                }
                final String r = resp;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) {
                            toast("连不上服务器：" + url);
                            Bus.emit("warn", "账号服务连不上：" + url);
                            return;
                        }
                        try {
                            JSONObject j = new JSONObject(r);
                            boolean ok = j.optBoolean("ok", false);
                            String msg = j.optString("message", "");
                            if (!ok) {
                                if ("PHONE_TAKEN".equals(j.optString("code", ""))) {
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("这个号已经注册了")
                                            .setMessage(msg)
                                            .setPositiveButton("去登录", new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface d, int w) {
                                                    hideRegister();
                                                    if (etLoginPhone != null) etLoginPhone.setText(phone);
                                                                                                if (etLoginPwd != null) etLoginPwd.setText("");
                                                    showLoginIfNeeded();
                                                }
                                            })
                                            .setNegativeButton("留在这里", null)
                                            .show();
                                    return;
                                }
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("验证码没发出去")
                                        .setMessage(msg)
                                        .setPositiveButton("知道了", null)
                                        .show();
                                return;
                            }
                            if (j.optBoolean("demo", false)) {
                                // 服务器没接真实短信通道时会把验证码直接回传：静默填入，不让用户手抄
                                String code = j.optString("code", "");
                                if (etRgCode != null) etRgCode.setText(code);
                                toast("验证码已下发（已自动填入）");
                            } else {
                                toast("验证码已发送到 " + phone);
                            }
                            startCodeCountdown();
                            Bus.emit("ok", "验证码已由服务器下发：" + phone);
                        } catch (Throwable t) {
                            toast("服务器返回看不懂：" + (r.length() > 60 ? r.substring(0, 60) : r));
                        }
                    }
                });
            }
        }, "sendsms").start();
    }

    /** 走服务器：POST /api/register，带上手机号/验证码/密码/用户名/设备信息 */
    private void registerOnServer(final String server, String phone, String code, final String pwd, final String userName,
                                    String q1, String a1, String q2, String a2) {
        final JSONObject body = new JSONObject();
        try {
            body.put("phone", phone);
            body.put("code", code);
            body.put("password", pwd);
            body.put("userName", userName == null ? "" : userName);
            JSONArray qs = new JSONArray();
            JSONObject o1 = new JSONObject();
            o1.put("q", q1);
            o1.put("a", a1);
            qs.put(o1);
            JSONObject o2 = new JSONObject();
            o2.put("q", q2);
            o2.put("a", a2);
            qs.put(o2);
            body.put("questions", qs);
            body.put("device", DeviceInfo.collect(this));
        } catch (Throwable ignored) {
        }
        toast("正在注册…");
        new Thread(new Runnable() {
            public void run() {
                final String url = server.replaceAll("/+$", "") + "/api/register";
                final String resp = Net.postJson(url, body.toString(), 15000);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (resp == null) {
                            toast("连不上服务器，注册没完成");
                            return;
                        }
                        try {
                            JSONObject j = new JSONObject(resp);
                            if (!j.optBoolean("ok", false)) {
                                String errCode = j.optString("code", "");
                                String msg = j.optString("message", "");
                                if ("PHONE_TAKEN".equals(errCode)) {
                                    // 号已经注册过：直接把人送回登录页，别让他反复试
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("这个号已经注册了")
                                            .setMessage(msg)
                                            .setPositiveButton("去登录", new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface d, int w) {
                                                    hideRegister();
                                                    if (etLoginPhone != null) etLoginPhone.setText(phone);
                                                                                                if (etLoginPwd != null) etLoginPwd.setText("");
                                                    showLoginIfNeeded();
                                                }
                                            })
                                            .show();
                                    return;
                                }
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("NAME_TAKEN".equals(errCode) ? "用户名重复" : "注册失败")
                                        .setMessage(msg)
                                        .setPositiveButton("知道了", null)
                                        .show();
                                return;
                            }
                            String phone2 = j.optString("userId", "");
                            String nameOnServer = j.optString("userName", "");
                            Prefs p = Prefs.load(MainActivity.this);
                            p.accountPhone = phone2;
                            p.accountVerified = true;
                            p.accountCreatedAt = System.currentTimeMillis();
                            // 注册页填了用户名就以它为准（旧版只在本地为空时才写，导致显示的还是老名字）
                            if (nameOnServer.length() > 0) {
                                p.userName = nameOnServer;
                                if (p.userNameSetAt <= 0 || userName != null && userName.length() > 0) {
                                    p.userNameSetAt = System.currentTimeMillis();
                                }
                            } else if (userName != null && userName.length() > 0) {
                                p.userName = userName;
                                p.userNameSetAt = System.currentTimeMillis();
                            }
                            p.save(MainActivity.this);
                            cfg.accountVerified = true;
                            cfg.accountPhone = phone2;
                            cfg.userName = p.userName;
                            cfg.userNameSetAt = p.userNameSetAt;
                            if (etRgCode != null) etRgCode.setText("");
                            hideRegister();
                            fillUserUi();
                            // 注册完直接登录进来
                            loginSuccess(phone2, pwd, nameOnServer, j.optString("sid", ""));
                            Bus.emit("ok", "注册成功并已登录：" + maskPhone(phone2));
                        } catch (Throwable t) {
                            toast("服务器返回看不懂");
                        }
                    }
                });
            }
        }, "register").start();
    }

    /** 心跳：设备信息 + 技能使用情况；顺便接服务端下发的「拉取技能/日志」请求 */
    private void pingServer() {
        final Prefs p = Prefs.load(this);
        final String server = Prefs.SERVER;
        new Thread(new Runnable() {
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("phone", p.accountPhone == null ? "" : p.accountPhone);
                    body.put("userName", cfg.userName);
                    body.put("sid", p.sessionId == null ? "" : p.sessionId);
                    body.put("device", DeviceInfo.collect(MainActivity.this));
                    body.put("skills", skills.usageForReport());
                    body.put("llmProvider", cfg.providerId == null ? "" : cfg.providerId);
                    body.put("llmModel", cfg.model == null ? "" : cfg.model);
                    String r = Net.postJson(server.replaceAll("/+$", "") + "/api/device/ping", body.toString(), 12000);
                    handlePull(server, p, r);
                } catch (Throwable ignored) {
                }
            }
        }, "ping").start();
    }

    // ———————————————————— 单设备会话：被别人登录就把这边下线 ————————————————————

    private android.os.Handler sessionHandler;
    private long lastSessionCheck = 0L;
    private boolean kickedHandled = false;

    /**
     * 问服务器：这台设备还是不是「唯一在线的那个」。
     * 服务器回 KICKED 就说明别的手机登录过，直接把本地登录状态清掉、弹回登录页。
     */
    private void verifySession() {
        final Prefs p = Prefs.load(this);
        if (!p.loggedIn) return;
        if (p.accountPhone == null || p.accountPhone.length() == 0) return;
        long now = System.currentTimeMillis();
        if (now - lastSessionCheck < 8000) return;
        lastSessionCheck = now;
        final String phone = p.accountPhone;
        final String sid = p.sessionId == null ? "" : p.sessionId;
        new Thread(new Runnable() {
            public void run() {
                try {
                    // 【安全】sid 是会话凭证，不能放在 URL query 里：
                    // query 会进服务器访问日志（nginx/apache 常见的 access log），
                    // 也可能出现在 Referer / 代理缓存里。
                    // 优先用 POST + body 传递；服务器不支持时再退回旧的 GET 方式。
                    JSONObject body = new JSONObject();
                    try {
                        body.put("phone", phone);
                        body.put("sid", sid);
                    } catch (Throwable ignored) {
                    }
                    String raw = Net.postJson(Prefs.SERVER.replaceAll("/+$", "") + "/api/session/check",
                            body.toString(), 8000);
                    if (raw == null || raw.length() == 0) {
                        // 老服务器只认 GET：退回 query 方式（功能兼容优先）
                        raw = Net.getBody(Prefs.SERVER.replaceAll("/+$", "") + "/api/session/check?phone="
                                + Net.encode(phone) + "&sid=" + Net.encode(sid), 8000);
                    }
                    if (raw == null || raw.length() == 0) return;
                    JSONObject j = new JSONObject(raw);
                    if (j.optBoolean("ok", false)) {
                        // 服务器认了我们没会话记录、刚补发的 sid：存下来
                        String fresh = j.optString("sid", "");
                        if (fresh.length() > 0 && !fresh.equals(sid)) {
                            Prefs f = Prefs.load(MainActivity.this);
                            f.sessionId = fresh;
                            f.save(MainActivity.this);
                            cfg.sessionId = fresh;
                        }
                        return;
                    }
                    String code = j.optString("code", "");
                    if ("KICKED".equals(code) || "NO_ACCOUNT".equals(code) || "NO_SESSION".equals(code)) {
                        final String msg = j.optString("message", "这个账号已在别的手机上登录");
                        runOnUiThread(new Runnable() {
                            public void run() {
                                forceLogout(msg);
                            }
                        });
                    }
                } catch (Throwable ignored) {
                }
            }
        }, "session").start();
    }

    /** 被顶下线：清本地登录态 + 弹回登录页（任务在跑就先停掉） */
    // ———————————————————— 聊天发送模式（插话 / 排队） ————————————————————

    /** 任务结束后把排队的第一条发出去 */
    private void dispatchQueued() {
        try {
            Prefs f = Prefs.load(this);
            String q = f.queuedMsgs == null ? "" : f.queuedMsgs;
            if (q.length() == 0) return;
            if (AgentService.running) return;
            String[] arr = q.split("\u0001", -1);
            StringBuilder rest = new StringBuilder();
            for (int i = 1; i < arr.length; i++) {
                if (rest.length() > 0) rest.append('\u0001');
                rest.append(arr[i]);
            }
            f.queuedMsgs = rest.toString();
            f.save(this);
            final String next = arr[0].trim();
            if (next.length() == 0) return;
            toast("排队消息自动发出：" + (next.length() > 18 ? next.substring(0, 18) + "…" : next));
            Bus.emit("ok", "排队消息已发出：" + next);
            etInput.setText(next);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                public void run() { send(); }
            }, 600);
        } catch (Throwable ignored) {
        }
    }

    private void refreshSendModeLabel() {
        if (tvSendMode == null) return;
        boolean interrupt = "interrupt".equals(Prefs.load(this).sendMode);
        tvSendMode.setText(interrupt ? "插话发送 ▸" : "排队发送 ▸");
        tvSendMode.setTextColor(interrupt ? 0xFFF59E0B : p.accent);
        int n = 0;
        String q = Prefs.load(this).queuedMsgs;
        if (q != null && q.length() > 0) n = q.split("\u0001", -1).length;
        if (n > 0) tvSendMode.setText(tvSendMode.getText() + "（排队 " + n + "）");
    }

    private void setupSendMode() {
        if (tvSendMode == null) return;
        refreshSendModeLabel();
        tvSendMode.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String[] items = new String[]{
                        "排队发送：等当前任务结束再发（默认，推荐）",
                        "插话发送：立刻打断当前任务，马上发这句"
                };
                int cur = "interrupt".equals(Prefs.load(MainActivity.this).sendMode) ? 1 : 0;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("聊天发送模式")
                        .setSingleChoiceItems(items, cur, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int which) {
                                Prefs f = Prefs.load(MainActivity.this);
                                f.sendMode = which == 1 ? "interrupt" : "queue";
                                f.save(MainActivity.this);
                                refreshSendModeLabel();
                                toast(which == 1 ? "已切换：插话发送" : "已切换：排队发送");
                                d.dismiss();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
    }

    private void forceLogout(String reason) {
        if (kickedHandled) return;
        kickedHandled = true;
        if (AgentService.running) {
            try {
                Intent st = new Intent(this, AgentService.class);
                st.setAction(AgentService.ACTION_STOP);
                startService(st);
            } catch (Throwable ignored) {
            }
        }
        Prefs f = Prefs.load(this);
        f.loggedIn = false;
        f.sessionId = "";
        f.save(this);
        cfg.loggedIn = false;
        cfg.sessionId = "";
        Bus.emit("warn", "已下线：" + reason);
        if (etLoginPwd != null) etLoginPwd.setText("");
        if (etLoginPhone != null && f.accountPhone != null && etLoginPhone.getText().length() == 0) {
            etLoginPhone.setText(f.accountPhone);
        }
        showLoginIfNeeded();
        new AlertDialog.Builder(this)
                .setTitle("已被下线")
                .setMessage(reason + "\n\n一个账号同一时间只能在一台手机上登录。\n要继续用，就在这里重新登录——重新登录会把那台顶下线。")
                .setPositiveButton("重新登录", null)
                .show();
    }

    /** 回到前台后每 30 秒问一次服务器（离开前台就停，省电） */
    private void startSessionWatch() {
        if (sessionHandler == null) sessionHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        sessionHandler.removeCallbacksAndMessages(null);
        sessionHandler.postDelayed(new Runnable() {
            public void run() {
                verifySession();
                if (sessionHandler != null) sessionHandler.postDelayed(this, 30000);
            }
        }, 30000);
    }

    private void stopSessionWatch() {
        if (sessionHandler != null) sessionHandler.removeCallbacksAndMessages(null);
    }

    /**
     * 通告：后台发布后，App 每次回到前台拉一次。
     * 拉到的每条都进聊天记录（系统消息），同一个 id 只弹一次。
     */
    /** 升级检查：后台在「系统信息」里填了版本号+直链，这里比对后弹提示 */
    // ———————————————— 账户页：当前版本 / 检查更新 ————————————————

    private void setupVersionRow() {
        if (tvVersion != null) tvVersion.setText("当前版本 v" + appVersionName());
        if (tvCheckUpdate != null) {
            tvCheckUpdate.setTextColor(p.accent);
            tvCheckUpdate.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    toast("正在检查更新…");
                    lastUpdateCheck = 0L;
                    checkUpdate(true);
                }
            });
        }
    }

    /** 拿到服务端版本后更新那一行：当前 v1.0.0 · 最新 v1.0.1（有新版本时链接变成「去更新 ▸」） */
    private void updateVersionLabel(String serverVer) {
        if (tvVersion == null) return;
        String cur = appVersionName();
        if (serverVer == null || serverVer.length() == 0) {
            tvVersion.setText("当前版本 v" + cur);
            if (tvCheckUpdate != null) tvCheckUpdate.setText("检查更新 ▸");
            return;
        }
        boolean newer = compareVersion(serverVer, cur) > 0;
        tvVersion.setText("当前 v" + cur + " · 最新 v" + serverVer + (newer ? "（有新版本）" : "（已是最新）"));
        if (tvCheckUpdate != null) {
            tvCheckUpdate.setText(newer ? "去更新 ▸" : "重新检查 ▸");
            tvCheckUpdate.setTextColor(newer ? 0xFFF59E0B : p.accent);
        }
    }

    /** manual=true 时忽略 6 小时节流和「以后再说」，用于账户页点「检查更新」 */
    private void checkUpdate(final boolean manual) {
        final Prefs p = Prefs.load(this);
        if (!p.loggedIn) return;
        long now = System.currentTimeMillis();
        if (!manual && now - lastUpdateCheck < 6 * 3600 * 1000L) return;   // 6 小时查一次
        lastUpdateCheck = now;
        new Thread(new Runnable() {
            public void run() {
                try {
                    String raw = Net.get(Prefs.SERVER.replaceAll("/+$", "") + "/api/app/update", 8000);
                    if (raw == null || raw.length() == 0) return;
                    JSONObject o = new JSONObject(raw);
                    final String ver = o.optString("version", "").trim();
                    final String url = o.optString("url", "").trim();
                    final String note = o.optString("note", "").trim();
                    final boolean force = o.optBoolean("force", false);
                    if (ver.length() == 0 || url.length() == 0) {
                        if (manual) runOnUiThread(new Runnable() { public void run() { toast("后台还没配置升级信息"); updateVersionLabel(""); } });
                        return;
                    }
                    final int cmp = compareVersion(ver, appVersionName());
                    runOnUiThread(new Runnable() { public void run() { updateVersionLabel(ver); } });
                    if (cmp <= 0) {
                        if (manual) runOnUiThread(new Runnable() { public void run() { toast("已是最新版本 v" + ver); } });
                        return;   // 已经是最新
                    }
                    if (!manual && ver.equals(Prefs.load(MainActivity.this).skippedUpdate)) return; // 忽略过这个版本
                    runOnUiThread(new Runnable() {
                        public void run() {
                            StringBuilder sb = new StringBuilder();
                            sb.append("当前版本：v").append(appVersionName()).append("\n")
                              .append("最新版本：v").append(ver).append("\n");
                            if (note.length() > 0) sb.append("\n更新内容：\n").append(note).append("\n");
                            sb.append("\n点「立即下载」会用浏览器打开安装包链接，下载完点一下就能覆盖安装（数据不会丢）。");
                            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(MainActivity.this)
                                    .setTitle("发现新版本 v" + ver)
                                    .setMessage(sb.toString())
                                    .setPositiveButton("立即下载", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface d, int w) {
                                            try {
                                                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                startActivity(i);
                                            } catch (Throwable t) {
                                                toast("打不开下载链接：" + url);
                                            }
                                        }
                                    })
                                    .setCancelable(!force);
                            if (!force) {
                                b.setNegativeButton("以后再说", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int w) {
                                        Prefs f = Prefs.load(MainActivity.this);
                                        f.skippedUpdate = ver;
                                        f.save(MainActivity.this);
                                    }
                                });
                            }
                            b.show();
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }, "update").start();
    }

    private long lastUpdateCheck = 0L;

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "1.0.0";
        }
    }

    /** 1.0.10 > 1.0.9 这种比较 */
    private int compareVersion(String a, String b) {
        try {
            String[] x = a.replaceAll("[^0-9.]", "").split("\\.");
            String[] y = b.replaceAll("[^0-9.]", "").split("\\.");
            int n = Math.max(x.length, y.length);
            for (int i = 0; i < n; i++) {
                int xi = i < x.length && x[i].length() > 0 ? Integer.parseInt(x[i]) : 0;
                int yi = i < y.length && y[i].length() > 0 ? Integer.parseInt(y[i]) : 0;
                if (xi != yi) return xi - yi;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private void checkNotices() {
        final String seenBefore = Prefs.load(this).seenNoticeIds == null ? "" : Prefs.load(this).seenNoticeIds;
        new Thread(new Runnable() {
            public void run() {
                try {
                    String raw = Net.get(Prefs.SERVER.replaceAll("/+$", "") + "/api/notices", 8000);
                    if (raw == null || raw.length() == 0) return;
                    JSONObject o = new JSONObject(raw);
                    JSONArray arr = o.optJSONArray("notices");
                    if (arr == null || arr.length() == 0) return;
                    final StringBuilder ids = new StringBuilder(seenBefore);
                    final java.util.List<String> fresh = new java.util.ArrayList<String>();
                    // 服务端是新的在前：倒着走，让聊天里的顺序也是老 -> 新
                    for (int i = arr.length() - 1; i >= 0; i--) {
                        JSONObject n = arr.optJSONObject(i);
                        if (n == null) continue;
                        String id = n.optString("id", "");
                        if (id.length() == 0 || ids.indexOf(id) >= 0) continue;
                        String title = n.optString("title", "").trim();
                        String body = n.optString("body", "").trim();
                        fresh.add("📢 通告：" + title + (body.length() > 0 ? "\n" + body : ""));
                        if (ids.length() > 0) ids.append(',');
                        ids.append(id);
                    }
                    if (fresh.isEmpty()) return;
                    // 只留最近 60 个 id，避免偏好项无限增长
                    String all = ids.toString();
                    String[] parts = all.split(",");
                    if (parts.length > 60) {
                        StringBuilder cut = new StringBuilder();
                        for (int i = parts.length - 60; i < parts.length; i++) {
                            if (cut.length() > 0) cut.append(',');
                            cut.append(parts[i]);
                        }
                        all = cut.toString();
                    }
                    final String keep = all;
                    Prefs p2 = Prefs.load(MainActivity.this);
                    p2.seenNoticeIds = keep;
                    p2.save(MainActivity.this);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            for (String t : fresh) chat.add("agent", t);
                            refreshChat();
                            toast("收到 " + fresh.size() + " 条新通告");
                            Bus.emit("ok", "收到新通告（" + fresh.size() + " 条）");
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }, "notices").start();
    }

    /**
     * 服务端在心跳响应里下发拉取请求（pull:{kind:'skills'|'log',since,until}）时，
     * 立刻把技能列表或日志切片 POST 回去，客户后台就能看到。
     */
    private void handlePull(final String server, final Prefs p, final String pingResp) {
        if (pingResp == null || pingResp.length() == 0) return;
        new Thread(new Runnable() {
            public void run() {
                try {
                    JSONObject o = new JSONObject(pingResp);
                    JSONObject pull = o.optJSONObject("pull");
                    if (pull == null) return;
                    String kind = pull.optString("kind", "");
                    if (kind.length() == 0) return;
                    JSONObject body = new JSONObject();
                    body.put("phone", p.accountPhone == null ? "" : p.accountPhone);
                    body.put("sid", p.sessionId == null ? "" : p.sessionId);
                    body.put("kind", kind);
                    long since = pull.optLong("since", 0);
                    long until = pull.optLong("until", 0);
                    body.put("since", since);
                    body.put("until", until);
                    if ("skills".equals(kind)) {
                        StringBuilder sb = new StringBuilder();
                        org.json.JSONArray arr = skills.usageForReport();
                        sb.append("技能列表（").append(arr.length()).append(" 个）：\n");
                        org.json.JSONArray sorted = arr;
                        for (int i = 0; i < sorted.length(); i++) {
                            org.json.JSONObject s = sorted.optJSONObject(i);
                            if (s == null) continue;
                            sb.append("· ").append(s.optString("n", ""))
                              .append(s.optBoolean("b", false) ? "（内置）" : "（自建）")
                              .append("　用了 ").append(s.optInt("u", 0)).append(" 次");
                            long t = s.optLong("t", 0);
                            if (t > 0) {
                                sb.append("　最后 ").append(android.text.format.DateFormat
                                        .format("MM-dd HH:mm", t).toString());
                            }
                            sb.append("\n");
                        }
                        body.put("skills", arr);
                        body.put("text", sb.toString());
                    } else {
                        String log = readLogSlice(since, until);
                        body.put("text", log);
                        body.put("skills", skills.usageForReport());
                    body.put("llmProvider", cfg.providerId == null ? "" : cfg.providerId);
                    body.put("llmModel", cfg.model == null ? "" : cfg.model);
                    }
                    String r = Net.postJson(server.replaceAll("/+$", "") + "/api/device/report", body.toString(), 20000);
                    if (r != null && r.contains("\"ok\":true")) {
                        Bus.emit("ok", "已按后台请求上传" + ("log".equals(kind) ? "使用日志" : "技能列表"));
                    }
                } catch (Throwable ignored) {
                }
            }
        }, "pullreport").start();
    }

    /** 读日志切片：能把 HH:mm:ss 按时间段过滤就过滤，否则整段给（日志只有时间没有日期） */
    private String readLogSlice(long since, long until) {
        StringBuilder out = new StringBuilder();
        out.append("=== 使用日志切片 ===\n");
        out.append("时间段：").append(since > 0 ? android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", since) : "不限")
                .append(" ~ ").append(until > 0 ? android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", until) : "现在")
                .append("\n机型：").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL)
                .append(" / Android ").append(android.os.Build.VERSION.RELEASE).append("\n\n");
        boolean sameDay = since > 0 && until > 0
                && (until - since) < 24L * 3600 * 1000;
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA);
        for (String name : new String[]{"lhagnet.log", "trace.log"}) {
            try {
                // 日志目录已迁移到应用私有目录（Bus.init），这里必须同步，
                // 否则读的是外部存储那个旧文件（可能不存在，或是不该被读的那份）
                File dir = new File(getFilesDir(), "logs");
                File f = new File(dir, name);
                if (!f.exists()) continue;
                long len = f.length();
                long from = Math.max(0, len - 400 * 1024);
                java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r");
                raf.seek(from);
                byte[] buf = new byte[(int) (len - from)];
                raf.readFully(buf);
                raf.close();
                String text = new String(buf, "UTF-8");
                out.append("--- ").append(name).append(" ---\n");
                int kept = 0;
                for (String line : text.split("\n")) {
                    if (sameDay) {
                        int sp = line.indexOf(' ');
                        String ts = sp > 0 ? line.substring(0, sp) : "";
                        if (ts.matches("\\d{2}:\\d{2}:\\d{2}\\.?\\d*")) {
                            try {
                                long t = fmt.parse(ts.substring(0, 8)).getTime() % (24L * 3600 * 1000);
                                long a = since % (24L * 3600 * 1000), b = until % (24L * 3600 * 1000);
                                if (t < a || t > b) continue;
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    if (kept < 3000) {
                        out.append(line).append('\n');
                        kept++;
                    }
                }
                out.append("\n");
            } catch (Throwable ignored) {
            }
        }
        return out.toString();
    }

    private void startCodeCountdown() {
        final TextView[] btns = new TextView[]{btnRgCode};
        if (codeHandler == null) codeHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        for (TextView b : btns) if (b != null) b.setEnabled(false);
        final int[] left = new int[]{60};
        codeHandler.removeCallbacksAndMessages(null);
        codeHandler.post(new Runnable() {
            public void run() {
                left[0]--;
                for (TextView b : btns) {
                    if (b == null) continue;
                    if (left[0] <= 0) {
                        b.setEnabled(true);
                        b.setText("获取验证码");
                    } else {
                        b.setText(left[0] + "s 后重发");
                    }
                }
                if (left[0] > 0) codeHandler.postDelayed(this, 1000);
            }
        });
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone == null ? "" : phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private void setupUser() {
        if (rowChgPwd != null) {
            rowChgPwd.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showChangePwdDialog();
                }
            });
        }
        btnSaveUser.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                saveUserUi();
            }
        });
        if (btnLogout != null) {
            btnLogout.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    confirmLogout();
                }
            });
        }
    }

    /** 用户名一个月只能改一次：算出还要等多少天 */
    private static final long USERNAME_COOLDOWN_MS = 30L * 24 * 3600 * 1000;

    // ———————————————————— 修改密码 ————————————————————

    /** 账户资料 → 修改密码：旧密码 + 新密码 + 再输一次，交服务器校验 */
    private void showChangePwdDialog() {
        Prefs f = Prefs.load(this);
        final String phone = f.loginPhone != null && f.loginPhone.length() > 0 ? f.loginPhone
                : (f.accountPhone == null ? "" : f.accountPhone);
        if (phone.length() == 0) {
            toast("先登录再改密码");
            return;
        }
        final EditText oldEt = new EditText(this);
        oldEt.setHint("旧密码");
        oldEt.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText newEt = new EditText(this);
        newEt.setHint("新密码（至少 4 位）");
        newEt.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText new2Et = new EditText(this);
        new2Et.setHint("再输一次新密码");
        new2Et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(6), dp(18), dp(6));
        TextView tip = new TextView(this);
        tip.setText("账号 " + maskPhone(phone) + " 的登录密码");
        tip.setTextSize(12);
        tip.setTextColor(0xFF8B94A3);
        tip.setPadding(0, 0, 0, dp(6));
        box.addView(tip);
        box.addView(oldEt);
        box.addView(newEt);
        box.addView(new2Et);
        new AlertDialog.Builder(this)
                .setTitle("修改密码")
                .setView(box)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String oldP = oldEt.getText().toString();
                        String p1 = newEt.getText().toString();
                        String p2 = new2Et.getText().toString();
                        if (oldP.length() < 4) {
                            toast("先填旧密码");
                            return;
                        }
                        if (p1.length() < 4) {
                            toast("新密码至少 4 位");
                            return;
                        }
                        if (!p1.equals(p2)) {
                            toast("两次新密码不一样");
                            return;
                        }
                        if (p1.equals(oldP)) {
                            toast("新密码和旧密码一样，换一个吧");
                            return;
                        }
                        changePwdOnServer(phone, oldP, p1);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void changePwdOnServer(final String phone, final String oldPwd, final String newPwd) {
        toast("正在修改…");
        new Thread(new Runnable() {
            public void run() {
                JSONObject body = new JSONObject();
                try {
                    body.put("phone", phone);
                    body.put("oldPassword", oldPwd);
                    body.put("newPassword", newPwd);
                } catch (Throwable ignored) {
                }
                final String url = Prefs.SERVER.replaceAll("/+$", "") + "/api/password/change";
                final String resp = Net.postJson(url, body.toString(), 15000);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (resp == null) {
                            toast("连不上服务器，密码没改");
                            return;
                        }
                        try {
                            JSONObject j = new JSONObject(resp);
                            if (!j.optBoolean("ok", false)) {
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("没改成")
                                        .setMessage(j.optString("message", ""))
                                        .setPositiveButton("知道了", null)
                                        .show();
                                return;
                            }
                            // 本地摘要同步换掉，下次开 App 用新密码也能直接过
                            Prefs f = Prefs.load(MainActivity.this);
                            f.loginPwdHash = Prefs.hashPwd(newPwd);
                            f.save(MainActivity.this);
                            cfg.loginPwdHash = f.loginPwdHash;
                            updateUserHint();
                            toast("密码已修改，下次用新密码登录");
                            Bus.emit("ok", "密码已修改：" + maskPhone(phone));
                        } catch (Throwable t) {
                            toast("服务器返回看不懂");
                        }
                    }
                });
            }
        }, "chgpwd").start();
    }

    private long userNameCooldownLeftMs() {
        if (cfg.userNameSetAt <= 0) return 0;
        long left = cfg.userNameSetAt + USERNAME_COOLDOWN_MS - System.currentTimeMillis();
        return left > 0 ? left : 0;
    }

    private void updateUserNameLock() {
        if (etUserName == null) return;
        boolean hasName = cfg.userName != null && cfg.userName.length() > 0;
        long left = userNameCooldownLeftMs();
        boolean locked = hasName && left > 0;
        etUserName.setEnabled(!locked);
        if (tvUserNameTip != null) {
            if (locked) {
                long days = (left + 24L * 3600 * 1000 - 1) / (24L * 3600 * 1000);
                String next = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                        .format(new java.util.Date(cfg.userNameSetAt + USERNAME_COOLDOWN_MS));
                tvUserNameTip.setText("用户名一个月只能改一次，还要 " + days + " 天（" + next + " 后可改）");
            } else if (hasName) {
                tvUserNameTip.setText("可以修改（改完一个月内不能再改）");
            } else {
                tvUserNameTip.setText("设一个吧，改了之后一个月内不能改");
            }
        }
    }

    private void fillUserUi() {
        Prefs f = Prefs.load(this);
        cfg.userName = f.userName;
        cfg.userPhone = f.userPhone;
        etUserName.setText(f.userName);
        // 老数据没有时间戳：从"这次打开"开始算一个月（不然永远显示"可以改"）
        if (cfg.userName != null && cfg.userName.length() > 0 && cfg.userNameSetAt <= 0) {
            cfg.userNameSetAt = System.currentTimeMillis();
            cfg.save(this);
        }
        updateUserHint();
        updateUserNameLock();
        fetchProfileFromServer();
    }

    /** 从服务器拉一次用户名，跟本机对齐（服务器上有就以服务器为准） */
    private void fetchProfileFromServer() {
        Prefs f = Prefs.load(this);
        final String phone = f.loginPhone != null && f.loginPhone.length() > 0 ? f.loginPhone
                : (f.accountPhone == null ? "" : f.accountPhone);
        if (!phone.matches("1\\d{10}")) return;
        new Thread(new Runnable() {
            public void run() {
                final String r = Net.get(Prefs.SERVER.replaceAll("/+$", "") + "/api/profile?phone=" + Net.encode(phone), 8000);
                if (r == null) return;
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            JSONObject j = new JSONObject(r);
                            if (!j.optBoolean("ok", false)) return;
                            String name = j.optString("userName", "");
                            if (name.length() == 0) return;
                            Prefs f2 = Prefs.load(MainActivity.this);
                            String local = f2.userName == null ? "" : f2.userName;
                            if (name.equals(local)) return;
                            f2.userName = name;
                            if (f2.userNameSetAt <= 0) f2.userNameSetAt = System.currentTimeMillis();
                            f2.save(MainActivity.this);
                            cfg.userName = name;
                            cfg.userNameSetAt = f2.userNameSetAt;
                            if (etUserName != null) etUserName.setText(name);
                            updateUserNameLock();
                            Bus.emit("ok", "用户名已与服务器同步：" + name);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        }, "profile").start();
    }

    /** 把本机用户名推到服务器（保存资料时调用） */
    private void syncProfileToServer(final String userName) {
        Prefs f = Prefs.load(this);
        final String phone = f.loginPhone != null && f.loginPhone.length() > 0 ? f.loginPhone
                : (f.accountPhone == null ? "" : f.accountPhone);
        if (!phone.matches("1\\d{10}")) return;
        new Thread(new Runnable() {
            public void run() {
                JSONObject body = new JSONObject();
                try {
                    body.put("phone", phone);
                    body.put("userName", userName == null ? "" : userName);
                } catch (Throwable ignored) {
                }
                final String r = Net.postJson(Prefs.SERVER.replaceAll("/+$", "") + "/api/profile", body.toString(), 10000);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) {
                            toast("用户名已存在本机，但没同步到服务器（网络不通）");
                            return;
                        }
                        try {
                            JSONObject j = new JSONObject(r);
                            if (!j.optBoolean("ok", false)) {
                                toast("服务器没记住用户名：" + j.optString("message", ""));
                                return;
                            }
                            toast("资料已保存，并同步到服务器");
                            Bus.emit("ok", "用户名已同步到服务器：" + j.optString("userName", ""));
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        }, "profile-put").start();
    }

    /** 手机号直接用登录/注册时那个号，不再单独填 */
    private void updateUserHint() {
        Prefs f = Prefs.load(this);
        String phone = f.loginPhone != null && f.loginPhone.length() > 0 ? f.loginPhone
                : (f.accountPhone == null ? "" : f.accountPhone);
        StringBuilder sb = new StringBuilder();
        if (phone.length() > 0) sb.append("手机：").append(phone).append("（登录号）");
        if (sb.length() == 0) sb.append("还没登录");
        tvUserHint.setText(sb.toString());
    }

    private void saveUserUi() {
        // 用户名一个月只能改一次
        String newName = etUserName.getText().toString().trim();
        String oldName = cfg.userName == null ? "" : cfg.userName;
        if (oldName.length() > 0 && cfg.userNameSetAt <= 0) {
            // 老数据：从这次开始计一个月
            cfg.userNameSetAt = System.currentTimeMillis();
        }
        if (!newName.equals(oldName)) {
            if (oldName.length() > 0) {
                long left = userNameCooldownLeftMs();
                if (left > 0) {
                    long days = (left + 24L * 3600 * 1000 - 1) / (24L * 3600 * 1000);
                    String next = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                            .format(new java.util.Date(cfg.userNameSetAt + USERNAME_COOLDOWN_MS));
                    toast("用户名一个月只能改一次，还要 " + days + " 天（" + next + " 后可改）");
                    etUserName.setText(oldName);
                    updateUserNameLock();
                    return;
                }
            }
            cfg.userNameSetAt = System.currentTimeMillis();
        }
        cfg.userName = newName;
        cfg.save(this);
        updateUserHint();
        updateUserNameLock();
        Bus.emit("ok", "用户资料已保存：" + (cfg.userName.length() > 0 ? cfg.userName : "（未填用户名）"));
        syncProfileToServer(newName);
    }


    // ———————————————————— 语言 / 字体 ————————————————————

    /** 抽屉、状态灯、输入框这些"骨架"文字跟着语言设置走 */
    private void applyShellTexts() {
        setTv(R.id.lbChat, "聊天");
        setTv(R.id.lbSkills, "技能");
        setTv(R.id.lbMemory, "记忆");
        setTv(R.id.lbUser, "用户");
        setTv(R.id.lbSched, "任务");
        setTv(R.id.lbLang, "语言");
        setTv(R.id.lbFont, "字体");
        setTv(R.id.lbConfig, "设置");
        if (chipHand != null) chipHand.setText("● " + Lang.t("手"));
        if (chipEye != null) chipEye.setText("● " + Lang.t("眼"));
        if (chipBrain != null) chipBrain.setText("● " + Lang.t("模"));
        if (etInput != null) etInput.setHint(Lang.t("给风车说一句话…"));
        if (btnSend != null) btnSend.setContentDescription(Lang.t("运行任务"));
        if (btnSaveUser != null) btnSaveUser.setText(Lang.t("保存资料"));
        if (btnSchedAdd != null) btnSchedAdd.setText(Lang.t("添加"));
        if (btnRgCode != null) btnRgCode.setText(Lang.t("获取验证码"));
        if (btnRgSubmit != null) btnRgSubmit.setText(Lang.t("注册并登录"));
    }

    private void setTv(int id, String zh) {
        TextView t = (TextView) findViewById(id);
        if (t != null) t.setText(Lang.t(zh));
    }

    /** 语言页：几种界面语言，点一下立即切换（重建界面） */
    private void refreshLangPage() {
        LinearLayout box = (LinearLayout) findViewById(R.id.boxLang);
        TextView title = (TextView) findViewById(R.id.tvLangTitle);
        TextView hint = (TextView) findViewById(R.id.tvLangHint);
        if (title != null) title.setTextColor(p.text);
        if (hint != null) {
            hint.setTextColor(p.textDim);
            hint.setText(Lang.t("选一种界面语言，点一下立即生效。任务的执行日志保持中文。"));
        }
        if (box == null) return;
        box.removeAllViews();
        for (int i = 0; i < Lang.IDS.length; i++) {
            final String id = Lang.IDS[i];
            boolean on = id.equals(cfg.lang) || (cfg.lang == null && i == 0);
            TextView row = new TextView(this);
            row.setText((on ? "●  " : "○  ") + Lang.NAMES[i]);
            row.setTextSize(15);
            row.setTextColor(on ? p.accent : p.text);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setBackground(block(on ? p.accentSoft : p.card, on ? p.accent : p.border, dp(14)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    cfg.lang = id;
                    cfg.save(MainActivity.this);
                    Lang.current = id;
                    Bus.emit("ok", "界面语言切换为：" + Lang.name(id));
                    toast("语言：" + Lang.name(id));
                    recreate();
                }
            });
            box.addView(row, lp);
        }
    }

    /** 字体页：四档字号，点一下立即缩放整个 App */
    private void refreshFontPage() {
        LinearLayout box = (LinearLayout) findViewById(R.id.boxFont);
        TextView title = (TextView) findViewById(R.id.tvFontTitle);
        TextView hint = (TextView) findViewById(R.id.tvFontHint);
        if (title != null) title.setTextColor(p.text);
        if (hint != null) {
            hint.setTextColor(p.textDim);
            hint.setText(Lang.t("调的是整个 App 的字号，点一下立即生效。"));
        }
        if (box == null) return;
        box.removeAllViews();
        final String[] names = new String[]{Lang.t("小"), Lang.t("标准"), Lang.t("大"), Lang.t("特大")};
        final float[] scales = new float[]{0.85f, 1.0f, 1.15f, 1.3f};
        for (int i = 0; i < scales.length; i++) {
            final float sc = scales[i];
            boolean on = Math.abs(cfg.fontScale - sc) < 0.01f;
            TextView row = new TextView(this);
            row.setText((on ? "●  " : "○  ") + names[i] + "    " + Math.round(sc * 100) + "%");
            row.setTextSize(15 * sc);
            row.setTextColor(on ? p.accent : p.text);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setBackground(block(on ? p.accentSoft : p.card, on ? p.accent : p.border, dp(14)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    cfg.fontScale = sc;
                    cfg.save(MainActivity.this);
                    Bus.emit("ok", "字体大小切换为：" + Math.round(sc * 100) + "%");
                    toast("字体 " + Math.round(sc * 100) + "%");
                    recreate();
                }
            });
            box.addView(row, lp);
        }
    }

    // ———————————————————— 定时任务 ————————————————————

    /**
     * 聊天里如果出现"3分钟后…/今晚8点…"这类延后表达，就自动排进任务页，立刻回一句确认，
     * 不去立刻动手机、也不占用聊天。
     */
    private boolean scheduleIfDelayed(String text) {
        TimeParse.Result r = TimeParse.parse(text);
        if (r == null) return false;
        long at = r.at;
        if (at <= System.currentTimeMillis() + 3000) at += 24L * 3600 * 1000; // 时间已过 → 明天
        String goal = (r.goal == null || r.goal.trim().length() == 0) ? text : r.goal.trim();
        new SchedStore(this).add(goal, at, r.daily);
        Scheduler.rearm(this);
        refreshSched();
        String when = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date(at));
        etInput.setText("");
        chat.add("user", text);
        chat.add("agent", "⏰ 已排进「任务」：" + when + (r.daily ? "（每天）" : "")
                + " 执行「" + goal + "」。\n到点我自己跑，不占用聊天；跑完结果会进聊天记录和通知栏。");
        refreshChat();
        toast("已排进「任务」：" + when + " 执行");
        Bus.emit("ok", "⏰ 聊天识别到延后时间（" + r.matched + "），已排进任务：" + when + " · " + goal);
        return true;
    }

    private void setupSched() {
        if (btnSchedAdd != null) {
            btnSchedAdd.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    addSchedFromUi();
                }
            });
        }
        // 每次打开 App 补一次闹钟（重启/被杀后闹钟会丢）
        try {
            Scheduler.rearm(this);
        } catch (Throwable ignored) {
        }
        refreshSched();
    }

    private void addSchedFromUi() {
        String goal = etSchedGoal.getText().toString().trim();
        if (goal.length() == 0) {
            toast("先写清楚到点要做什么");
            return;
        }
        double minutes;
        try {
            minutes = Double.parseDouble(etSchedMin.getText().toString().trim());
        } catch (Throwable t) {
            toast("填“几分钟后”，例如 3 或 0.5");
            return;
        }
        if (minutes <= 0 || minutes > 24 * 60) {
            toast("时间填 0～1440 分钟之间");
            return;
        }
        long at = System.currentTimeMillis() + (long) (minutes * 60 * 1000L);
        SchedStore st = new SchedStore(this);
        st.add(goal, at, cbSchedDaily != null && cbSchedDaily.isChecked());
        Scheduler.rearm(this);
        etSchedGoal.setText("");
        etSchedMin.setText("");
        if (cbSchedDaily != null) cbSchedDaily.setChecked(false);
        refreshSched();
        String when = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date(at));
        toast("已排定 " + when + " 执行，到点我自己跑，不占用聊天");
        Bus.emit("ok", "⏰ 新建定时任务：" + when + " · " + goal);
    }

    private void refreshSched() {
        if (boxSched == null) return;
        boxSched.removeAllViews();
        SchedStore st = new SchedStore(this);
        List<SchedStore.Task> list = st.all();
        // 待执行的排前面（按时间），已完成的沉底
        java.util.Collections.sort(list, new java.util.Comparator<SchedStore.Task>() {
            public int compare(SchedStore.Task a, SchedStore.Task b) {
                if (a.done != b.done) return a.done ? 1 : -1;
                return Long.compare(a.at, b.at);
            }
        });
        long now = System.currentTimeMillis();
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.CHINA);

        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有定时任务。\n上面写一句要做什么 + 几分钟后，点「添加」就行。");
            empty.setTextColor(p.textDim);
            empty.setTextSize(13);
            empty.setLineSpacing(dp(5), 1f);
            empty.setPadding(dp(6), dp(10), dp(6), dp(10));
            boxSched.addView(empty);
        }

        int pending = 0;
        SchedStore.Task next = null;
        for (int i = 0; i < list.size(); i++) {
            final SchedStore.Task t = list.get(i);
            if (!t.done) {
                pending++;
                if (next == null || t.at < next.at) next = t;
            }
            StringBuilder sb = new StringBuilder();
            if (t.done) sb.append("✅ 已执行");
            else {
                long left = t.at - now;
                if (left <= 0) sb.append("⏰ 到点待执行");
                else sb.append("⏰ 还有 ").append(fmtLeft(left)).append("（").append(f.format(new java.util.Date(t.at))).append("）");
            }
            if (t.repeatDaily) sb.append(" · 每天");
            sb.append("\n").append(t.goal);
            if (t.done && t.lastSummary != null && t.lastSummary.length() > 0) {
                sb.append("\n结果：").append(t.lastSummary.length() > 60
                        ? t.lastSummary.substring(0, 60) + "…" : t.lastSummary);
            }

            TextView row = new TextView(this);
            row.setText(sb.toString());
            row.setTextColor(p.text);
            row.setTextSize(13);
            row.setLineSpacing(dp(4), 1f);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(block(p.card, p.border, dp(14)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    confirmDeleteSched(t);
                }
            });
            boxSched.addView(row, lp);
        }

        if (tvSchedHint != null) {
            StringBuilder h = new StringBuilder();
            h.append("共 ").append(list.size()).append(" 条，待执行 ").append(pending).append(" 条");
            if (next != null) {
                h.append("；下一条 ").append(f.format(new java.util.Date(next.at)));
            }
            h.append("\n到点会用前台服务自动执行，不需要你开着 App；结果会进聊天记录和通知栏。");
            tvSchedHint.setText(h.toString());
        }
    }

    private void confirmDeleteSched(final SchedStore.Task t) {
        new AlertDialog.Builder(this)
                .setTitle("删除这条定时任务？")
                .setMessage((t.done ? "✅ 已执行 · " : "⏰ ") + t.goal)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        SchedStore st = new SchedStore(MainActivity.this);
                        st.remove(t.id);
                        Scheduler.cancel(MainActivity.this, t.id);
                        Scheduler.rearm(MainActivity.this);
                        refreshSched();
                        toast("已删除");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static String fmtLeft(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return h + " 小时 " + m + " 分";
        if (m > 0) return m + " 分 " + sec + " 秒";
        return sec + " 秒";
    }

    // ———————————————————— 杂项 ————————————————————

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }
}
