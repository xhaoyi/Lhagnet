package io.lhagnet.app;

import java.util.HashMap;
import java.util.Map;

/**
 * 界面语言（多国语言）。
 *
 * 做法：界面里可见的骨架文案用 {@link #t(String)} 包一层，查表翻译；
 * 没有条目时原样返回（中文）。任务日志、模型输出这类动态内容不翻译。
 */
public class Lang {

    public static final String[] IDS = new String[]{"zh", "zh-TW", "en"};
    public static final String[] NAMES = new String[]{"简体中文", "繁體中文", "English"};

    public static String current = "zh";

    public static boolean isZh() {
        return "zh".equals(current);
    }

    public static String name(String id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) return NAMES[i];
        }
        return NAMES[0];
    }

    /** 翻译一条界面文案；查不到就原样返回 */
    public static String t(String zh) {
        if (zh == null || zh.length() == 0) return "";
        if ("zh".equals(current)) return zh;
        Map<String, String> m = "en".equals(current) ? EN : ZH_TW;
        String v = m.get(zh);
        return v == null ? zh : v;
    }

    // ———————————————————— 繁体 ————————————————————

    private static final Map<String, String> ZH_TW = new HashMap<String, String>();

    static {
        ZH_TW.put("聊天", "聊天");
        ZH_TW.put("技能", "技能");
        ZH_TW.put("记忆", "記憶");
        ZH_TW.put("任务", "任務");
        ZH_TW.put("用户", "用戶");
        ZH_TW.put("设置", "設定");
        ZH_TW.put("语言", "語言");
        ZH_TW.put("字体", "字體");
        ZH_TW.put("手", "手");
        ZH_TW.put("眼", "眼");
        ZH_TW.put("模", "模");
        ZH_TW.put("给风车说一句话…", "給風車說一句話…");
        ZH_TW.put("运行任务", "執行任務");
        ZH_TW.put("停止任务", "停止任務");
        ZH_TW.put("想聊就聊，想让我动手就说目标", "想聊就聊，想讓我動手就說目標");
        ZH_TW.put("刷新", "重新整理");
        ZH_TW.put("清空记忆", "清空記憶");
        ZH_TW.put("导出备份（记忆+技能）", "匯出備份（記憶+技能）");
        ZH_TW.put("导入备份", "匯入備份");
        ZH_TW.put("新建技能", "新增技能");
        ZH_TW.put("保存", "儲存");
        ZH_TW.put("保存资料", "儲存資料");
        ZH_TW.put("保存设置", "儲存設定");
        ZH_TW.put("添加", "新增");
        ZH_TW.put("用户名", "用戶名");
        ZH_TW.put("绑定手机", "綁定手機");
        ZH_TW.put("密码设置", "密碼設定");
        ZH_TW.put("手机验证码注册", "手機驗證碼註冊");
        ZH_TW.put("获取验证码", "取得驗證碼");
        ZH_TW.put("注册 / 绑定这个手机号", "註冊 / 綁定這個手機號");
        ZH_TW.put("账号资料（只存在这台手机上）", "帳號資料（只存在這台手機上）");
        ZH_TW.put("模型", "模型");
        ZH_TW.put("安全", "安全");
        ZH_TW.put("权限与能力", "權限與能力");
        ZH_TW.put("新建任务（到点自己跑，不占用聊天）", "新增任務（到點自己跑，不佔用聊天）");
        ZH_TW.put("语言 / Language", "語言 / Language");
        ZH_TW.put("字体大小", "字體大小");
    }

    // ———————————————————— 英文 ————————————————————

    private static final Map<String, String> EN = new HashMap<String, String>();

    static {
        EN.put("聊天", "Chat");
        EN.put("技能", "Skills");
        EN.put("记忆", "Memory");
        EN.put("任务", "Tasks");
        EN.put("用户", "User");
        EN.put("设置", "Settings");
        EN.put("语言", "Language");
        EN.put("字体", "Font");
        EN.put("手", "Hands");
        EN.put("眼", "Eyes");
        EN.put("模", "Model");
        EN.put("给风车说一句话…", "Message Fengche…");
        EN.put("运行任务", "Run task");
        EN.put("停止任务", "Stop task");
        EN.put("想聊就聊，想让我动手就说目标", "Chat freely — or tell me what to do on your phone");
        EN.put("· 聊天：「你好」「帮我翻译…」「刚才为什么失败」—— 直接回答你",
                "· Chat: \"hi\", \"translate this\", \"why did it fail\" — I answer directly");
        EN.put("· 干活：「打开美团，搜炒饭，找最便宜的 5 份，挑一份下单（下单前问我）」",
                "· Act: \"open Meituan, search fried rice, pick the cheapest 5 (ask before ordering)\"");
        EN.put("· 看不懂的东西：点左下角 ＋ 发张截图或文件，我照着办",
                "· Not sure what it is: tap ＋ to send a screenshot or file");
        EN.put("刷新", "Refresh");
        EN.put("清空记忆", "Clear memory");
        EN.put("导出备份（记忆+技能）", "Export backup (memory + skills)");
        EN.put("导入备份", "Import backup");
        EN.put("新建技能", "New skill");
        EN.put("保存", "Save");
        EN.put("保存资料", "Save profile");
        EN.put("保存设置", "Save settings");
        EN.put("添加", "Add");
        EN.put("用户名", "Username");
        EN.put("绑定手机", "Phone");
        EN.put("密码设置", "Password");
        EN.put("手机验证码注册", "Register by SMS code");
        EN.put("获取验证码", "Send code");
        EN.put("注册 / 绑定这个手机号", "Register / bind this phone");
        EN.put("账号资料（只存在这台手机上）", "Profile (stored only on this phone)");
        EN.put("模型", "Model");
        EN.put("安全", "Safety");
        EN.put("权限与能力", "Permissions");
        EN.put("新建任务（到点自己跑，不占用聊天）", "New scheduled task (runs by itself, doesn't block chat)");
        EN.put("语言 / Language", "语言 / Language");
        EN.put("字体大小", "Font size");
        EN.put("小", "Small");
        EN.put("标准", "Default");
        EN.put("大", "Large");
        EN.put("特大", "Extra large");
        EN.put("几天后", "in days");
        EN.put("还有", "in");
    }
}
