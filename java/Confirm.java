package io.lhagnet.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 人工确认闸门：以悬浮窗形式盖在任何 App 之上，等人真正点按钮。
 *
 * 用悬浮窗而不是 Activity，是因为 agent 操作时 App 在后台，
 * "后台启动 Activity"在 Android 10+ 会被系统拦截，而 SYSTEM_ALERT_WINDOW 不受影响。
 */
public class Confirm {
    private final Context ctx;

    public Confirm(Context ctx) {
        this.ctx = ctx;
    }

    public boolean canShow() {
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            return Settings.canDrawOverlays(ctx);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 阻塞式弹窗：返回被点击的按钮文字，超时或无法显示返回 null。
     * 注意：本方法必须在非主线程调用。
     */
    public String show(final String title, final String text, final String positive, final String negative, int timeoutMs) {
        if (!canShow()) {
            Bus.emit("warn", "缺少悬浮窗权限，无法请求确认");
            return null;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] answer = new String[1];
        final Handler main = new Handler(Looper.getMainLooper());

        main.post(new Runnable() {
            public void run() {
                final WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (wm == null) {
                    latch.countDown();
                    return;
                }
                final LinearLayout root = new LinearLayout(ctx);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setGravity(Gravity.CENTER);
                root.setBackgroundColor(Color.parseColor("#CC000000"));
                root.setPadding(dp(18), dp(18), dp(18), dp(18));

                LinearLayout card = new LinearLayout(ctx);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(round(Color.parseColor("#F21B1F27"), dp(16), Color.parseColor("#3A4C63")));
                card.setPadding(dp(18), dp(16), dp(18), dp(12));

                TextView t = new TextView(ctx);
                t.setText(title);
                t.setTextColor(Color.WHITE);
                t.setTextSize(17);
                card.addView(t);

                ScrollView sv = new ScrollView(ctx);
                TextView body = new TextView(ctx);
                body.setText(text);
                body.setTextColor(Color.parseColor("#E6E9EF"));
                body.setTextSize(15);
                body.setLineSpacing(dp(4), 1f);
                sv.addView(body);
                LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
                svLp.topMargin = dp(10);
                card.addView(sv, svLp);

                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.END);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.topMargin = dp(14);

                final WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                if (Build.VERSION.SDK_INT >= 26) {
                    lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                } else {
                    lp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
                }
                lp.format = android.graphics.PixelFormat.TRANSLUCENT;
                lp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = WindowManager.LayoutParams.MATCH_PARENT;
                lp.gravity = Gravity.CENTER;

                final boolean[] settled = new boolean[]{false};

                TextView cancel = mkButton(negative, Color.parseColor("#3A4150"));
                cancel.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (settled[0]) return;
                        settled[0] = true;
                        answer[0] = negative;
                        try {
                            wm.removeView(root);
                        } catch (Throwable ignored) {
                        }
                        latch.countDown();
                    }
                });
                TextView ok = mkButton(positive, Color.parseColor("#2F81F7"));
                ok.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (settled[0]) return;
                        settled[0] = true;
                        answer[0] = positive;
                        try {
                            wm.removeView(root);
                        } catch (Throwable ignored) {
                        }
                        latch.countDown();
                    }
                });
                LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                btnLp.leftMargin = dp(8);
                row.addView(cancel, btnLp);
                row.addView(ok, btnLp);
                card.addView(row, rowLp);
                root.addView(card, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                try {
                    wm.addView(root, lp);
                } catch (Throwable t2) {
                    Bus.emit("warn", "悬浮窗添加失败：" + t2);
                    latch.countDown();
                }
            }
        });

        try {
            boolean done = latch.await(Math.max(5000, timeoutMs), TimeUnit.MILLISECONDS);
            if (!done) Bus.emit("warn", "确认超时，按未确认处理");
        } catch (InterruptedException ignored) {
        }
        return answer[0];
    }

    /**
     * 带输入框的人工闸门：需要"信息"（门牌号、联系人、电话、数量…）而不是"是/否"时用这个。
     * 返回：用户输入的文本；点了取消返回 negative；超时/无权限返回 null。
     * 注意：本方法必须在非主线程调用。
     */
    public String showWithInput(final String title, final String text, final String hint,
                                final String positive, final String negative, int timeoutMs) {
        if (!canShow()) {
            Bus.emit("warn", "缺少悬浮窗权限，无法请求输入");
            return null;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] answer = new String[1];
        final Handler main = new Handler(Looper.getMainLooper());

        main.post(new Runnable() {
            public void run() {
                final WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (wm == null) {
                    latch.countDown();
                    return;
                }
                final LinearLayout root = new LinearLayout(ctx);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setGravity(Gravity.CENTER);
                root.setBackgroundColor(Color.parseColor("#CC000000"));
                root.setPadding(dp(18), dp(18), dp(18), dp(18));

                LinearLayout card = new LinearLayout(ctx);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(round(Color.parseColor("#F21B1F27"), dp(16), Color.parseColor("#3A4C63")));
                card.setPadding(dp(18), dp(16), dp(18), dp(12));

                TextView t = new TextView(ctx);
                t.setText(title);
                t.setTextColor(Color.WHITE);
                t.setTextSize(17);
                card.addView(t);

                TextView body = new TextView(ctx);
                body.setText(text);
                body.setTextColor(Color.parseColor("#E6E9EF"));
                body.setTextSize(15);
                body.setLineSpacing(dp(4), 1f);
                LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                bodyLp.topMargin = dp(10);
                card.addView(body, bodyLp);

                final android.widget.EditText input = new android.widget.EditText(ctx);
                input.setHint(hint == null ? "" : hint);
                input.setHintTextColor(Color.parseColor("#8A93A3"));
                input.setTextColor(Color.WHITE);
                input.setTextSize(15);
                input.setBackground(round(Color.parseColor("#22283480"), dp(10), Color.parseColor("#3A4C63")));
                input.setPadding(dp(12), dp(10), dp(12), dp(10));
                input.setMinLines(2);
                input.setGravity(Gravity.TOP | Gravity.START);
                LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                inLp.topMargin = dp(12);
                card.addView(input, inLp);

                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.END);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.topMargin = dp(14);

                final WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                if (Build.VERSION.SDK_INT >= 26) {
                    lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                } else {
                    lp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
                }
                lp.format = android.graphics.PixelFormat.TRANSLUCENT;
                lp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = WindowManager.LayoutParams.MATCH_PARENT;
                lp.gravity = Gravity.CENTER;
                lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

                final boolean[] settled = new boolean[]{false};

                TextView cancel = mkButton(negative, Color.parseColor("#3A4150"));
                cancel.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (settled[0]) return;
                        settled[0] = true;
                        answer[0] = negative;
                        try {
                            wm.removeView(root);
                        } catch (Throwable ignored) {
                        }
                        latch.countDown();
                    }
                });
                TextView ok = mkButton(positive, Color.parseColor("#2F81F7"));
                ok.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (settled[0]) return;
                        settled[0] = true;
                        CharSequence cs = input.getText();
                        answer[0] = cs == null ? "" : cs.toString().trim();
                        try {
                            wm.removeView(root);
                        } catch (Throwable ignored) {
                        }
                        latch.countDown();
                    }
                });
                LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                btnLp.leftMargin = dp(8);
                row.addView(cancel, btnLp);
                row.addView(ok, btnLp);
                card.addView(row, rowLp);
                root.addView(card, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                try {
                    wm.addView(root, lp);
                    input.requestFocus();
                } catch (Throwable t2) {
                    Bus.emit("warn", "悬浮窗添加失败：" + t2);
                    latch.countDown();
                }
            }
        });

        try {
            boolean done = latch.await(Math.max(5000, timeoutMs), TimeUnit.MILLISECONDS);
            if (!done) Bus.emit("warn", "输入超时，按取消处理");
        } catch (InterruptedException ignored) {
        }
        return answer[0];
    }

    private TextView mkButton(String label, int color) {
        TextView b = new TextView(ctx);
        b.setText(TextUtils.isEmpty(label) ? "确定" : label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setBackground(round(color, dp(10), Color.TRANSPARENT));
        return b;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
