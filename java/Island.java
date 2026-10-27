package io.lhagnet.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 灵动岛：跑任务时贴一块悬浮胶囊在屏幕顶部，显示当前在干什么（最多 8 个字）。
 *
 * 用 SYSTEM_ALERT_WINDOW 悬浮窗实现，所以用户切到美团/微信时也能看见进度；
 * 点它可以直接回到 App 聊天页。任务结束后自动收起。
 */
public class Island {

    private static final int STATE_RUNNING = 0;
    private static final int STATE_DONE = 1;
    private static final int STATE_FAIL = 2;

    private static Context ctx;
    private static WindowManager wm;
    private static LinearLayout pill;
    private static View dot;
    private static TextView label;
    private static Handler main;
    private static boolean showing = false;
    /** 结束提示常驻：等用户点一下才收起，避免"结果一闪就没了" */
    private static boolean pinned = false;

    public static void init(Context context) {
        ctx = context.getApplicationContext();
        main = new Handler(Looper.getMainLooper());
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                if (!Settings.canDrawOverlays(ctx)) {
                    Bus.emit("warn", "没有悬浮窗权限：灵动岛无法显示");
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** 显示并给一句状态（超出 8 个字会被截断） */
    public static void show(String text) {
        update(text, STATE_RUNNING);
    }

    public static void update(final String text, final int state) {
        if (ctx == null || main == null) return;
        main.post(new Runnable() {
            public void run() {
                try {
                    ensureView();
                    if (pill == null) return;
                    label.setText(clip8(text));
                    int color = state == STATE_DONE ? Color.parseColor("#4ADE80")
                            : state == STATE_FAIL ? Color.parseColor("#F87171")
                            : Color.parseColor("#7C9CFF");
                    if (dot != null) dot.setBackground(circle(color));
                    if (label != null) label.setTextColor(state == STATE_RUNNING ? 0xFFE9EDF2 : color);
                    if (!showing) {
                        showing = true;
                        pill.setAlpha(0f);
                        pill.setVisibility(View.VISIBLE);
                        try {
                            wm.addView(pill, params());
                        } catch (Throwable t) {
                            showing = false;
                            return;
                        }
                        pill.animate().alpha(1f).setDuration(180).start();
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    public static void hide() {
        pinned = false;
        if (main == null) return;
        main.post(new Runnable() {
            public void run() {
                try {
                    if (pill != null && showing) {
                        showing = false;
                        pill.animate().alpha(0f).setDuration(160).withEndAction(new Runnable() {
                            public void run() {
                                try {
                                    wm.removeView(pill);
                                } catch (Throwable ignored) {
                                }
                            }
                        }).start();
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** 结束后停 2.2 秒再收起，让用户看到结果是成功还是失败 */
    public static void finishThenHide(final String text, final boolean ok) {
        if (!ok) {
            // 失败/被打断：不自动收，等用户点一下或回到 App
            pin(text, false);
            return;
        }
        update(text, STATE_DONE);
        if (main == null) return;
        main.postDelayed(new Runnable() {
            public void run() {
                if (!pinned) hide();
            }
        }, 2200);
    }

    /**
     * 常驻显示（不自动收起）。用于失败/被打断：消息绝不能一闪而过，
     * 用户点一下胶囊就收起并回到 App 看详情。
     * 兜底：一直没人点也会在 60 秒后收起，别真的赖在别人 App 上面。
     */
    public static void pin(final String text, final boolean ok) {
        pinned = true;
        update(text, ok ? STATE_DONE : STATE_FAIL);
        if (main == null) return;
        main.postDelayed(new Runnable() {
            public void run() {
                if (pinned) hide();
            }
        }, 60000);
    }

    public static boolean isShowing() {
        return showing;
    }

    // ———————————————————— 内部 ————————————————————

    private static void ensureView() {
        if (pill != null) return;
        if (!canShow()) return;

        pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(14), dp(8), dp(16), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2161921);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0xFF2A313C);
        pill.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) pill.setElevation(dp(6));

        dot = new View(ctx);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dlp.rightMargin = dp(8);
        dot.setBackground(circle(Color.parseColor("#7C9CFF")));
        pill.addView(dot, dlp);

        label = new TextView(ctx);
        label.setTextSize(13);
        label.setTextColor(0xFFE9EDF2);
        label.setSingleLine(true);
        label.setMaxWidth(dp(200));
        pill.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        pill.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                hide();
                try {
                    android.content.Intent i = new android.content.Intent(ctx, MainActivity.class);
                    i.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    ctx.startActivity(i);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static WindowManager.LayoutParams params() {
        if (wm == null) wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(42);
        lp.format = android.graphics.PixelFormat.TRANSLUCENT;
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        return lp;
    }

    private static boolean canShow() {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            return Settings.canDrawOverlays(ctx);
        } catch (Throwable t) {
            return false;
        }
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** 只留 8 个字 */
    private static String clip8(String text) {
        if (text == null) return "";
        String t = text.replace("\n", " ").trim();
        if (t.length() <= 8) return t;
        return t.substring(0, 8);
    }

    private static int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
