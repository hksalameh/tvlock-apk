package com.smartcodejo.tvlock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/** Small non-blocking banner shown a few minutes before the lock starts. */
final class WarnBanner {
    private WarnBanner() {}

    static void show(Context ctx) {
        final Context c = ctx.getApplicationContext();
        if (!Prefs.enabled(c) || ScheduleUtil.shouldLock(c) || !Settings.canDrawOverlays(c)) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                final WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
                final TextView t = new TextView(c);
                t.setText("⏰  سيُقفل التلفزيون بعد " + (ScheduleUtil.WARN_MS / 60000) + " دقائق");
                t.setTextColor(Color.WHITE);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                t.setGravity(Gravity.CENTER);
                int pad = Math.round(16 * c.getResources().getDisplayMetrics().density);
                t.setPadding(pad * 2, pad, pad * 2, pad);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.argb(225, 12, 75, 148));
                bg.setStroke(2, Color.rgb(83, 210, 255));
                bg.setCornerRadius(pad);
                t.setBackground(bg);
                WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                p.y = pad * 2;
                wm.addView(t, p);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { wm.removeView(t); } catch (Exception ignored) {}
                }, 20_000L);
            } catch (Exception ignored) {
            }
        });
    }
}
