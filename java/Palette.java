package io.lhagnet.app;

import android.content.Context;

/**
 * 圆润风格配色（参考豆包 / DeepSeek 的克制留白 + 单一强调色）。
 * 明暗两套，全部字段运行时切换。
 */
public class Palette {
    public boolean dark = true;

    // 背景层次
    public int bg;          // 页面底
    public int surface;     // 顶栏/底栏/输入区
    public int card;        // 卡片
    public int cardAlt;     // 次级卡片（思考块、代码块）
    public int border;      // 描边（暗色下用低对比描边代替阴影）

    // 文字
    public int text;
    public int textDim;
    public int textFaint;

    // 强调
    public int accent;
    public int accentSoft;      // 强调色的淡底（选中药丸、用户气泡）
    public int onAccent;        // 强调色上的文字

    // 气泡
    public int bubbleUser;
    public int bubbleUserText;
    public int avatarBg;
    public int avatarText;

    // 状态灯
    public int on;
    public int off;
    public int onSoft;
    public int offSoft;

    public static Palette of(boolean dark) {
        Palette p = new Palette();
        p.dark = dark;
        if (dark) {
            p.bg = 0xFF0D1014;
            p.surface = 0xFF14181F;
            p.card = 0xFF1A1F27;
            p.cardAlt = 0xFF20262F;
            p.border = 0xFF2A313C;
            p.text = 0xFFE9EDF2;
            p.textDim = 0xFF9AA4B2;
            p.textFaint = 0xFF6D7885;
            p.accent = 0xFF7C9CFF;
            p.accentSoft = 0xFF232C45;
            p.onAccent = 0xFF0D1014;
            p.bubbleUser = 0xFF26334F;
            p.bubbleUserText = 0xFFE9EEFB;
            p.avatarBg = 0xFF243048;
            p.avatarText = 0xFF7C9CFF;
            p.on = 0xFF4ADE80;
            p.off = 0xFFF87171;
            p.onSoft = 0x1F4ADE80;
            p.offSoft = 0x1FF87171;
        } else {
            p.bg = 0xFFF4F5F7;
            p.surface = 0xFFFFFFFF;
            p.card = 0xFFFFFFFF;
            p.cardAlt = 0xFFF6F7F9;
            p.border = 0xFFE9EBEF;
            p.text = 0xFF1A1D23;
            p.textDim = 0xFF6B7280;
            p.textFaint = 0xFF9CA3AF;
            p.accent = 0xFF4D6BFE;
            p.accentSoft = 0xFFE8EDFF;
            p.onAccent = 0xFFFFFFFF;
            p.bubbleUser = 0xFF4D6BFE;
            p.bubbleUserText = 0xFFFFFFFF;
            p.avatarBg = 0xFFEDF1FF;
            p.avatarText = 0xFF4D6BFE;
            p.on = 0xFF12B76A;
            p.off = 0xFFEF4444;
            p.onSoft = 0x1412B76A;
            p.offSoft = 0x14EF4444;
        }
        return p;
    }

    public static Palette load(Context ctx) {
        Prefs cfg = Prefs.load(ctx);
        return of(cfg.darkMode);
    }
}
