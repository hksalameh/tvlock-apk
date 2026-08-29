package com.smartcodejo.tvlock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LockOverlayService extends Service {
    private static final String CHANNEL = "tv_lock_guard";
    private static final int NOTIF_ID = 22;

    private WindowManager wm;
    private View overlay;
    private TextView clock;
    private TextView sideClock;
    private TextView pinDots;
    private TextView hint;
    private final StringBuilder entered = new StringBuilder();
    private boolean forced;
    private int sw;
    private int sh;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateClock();
            if (!forced && !ScheduleUtil.shouldLock(LockOverlayService.this)) stopSelf();
            else handler.postDelayed(this, 1000);
        }
    };

    public static void sync(Context c) {
        if (ScheduleUtil.shouldLock(c) && Settings.canDrawOverlays(c)) {
            Intent i = new Intent(c, LockOverlayService.class);
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
                else c.startService(i);
            } catch (Exception ignored) {}
        } else {
            try { c.stopService(new Intent(c, LockOverlayService.class)); }
            catch (Exception ignored) {}
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIF_ID, notification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("force", false)) forced = true;

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "يجب تفعيل صلاحية الظهور فوق التطبيقات", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (forced || ScheduleUtil.shouldLock(this)) showOverlay();
        else stopSelf();

        return START_STICKY;
    }

    private void showOverlay() {
        if (overlay != null) return;

        sw = Math.max(1280, getResources().getDisplayMetrics().widthPixels);
        sh = Math.max(720, getResources().getDisplayMetrics().heightPixels);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(3, 12, 31));
        frame.addView(new LockBackgroundView(this),
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout safe = new LinearLayout(this);
        safe.setOrientation(LinearLayout.VERTICAL);
        safe.setGravity(Gravity.CENTER_HORIZONTAL);
        int safeX = Math.round(sw * 0.045f);
        int safeY = Math.round(sh * 0.030f);
        safe.setPadding(safeX, safeY, safeX, safeY);
        safe.setFocusableInTouchMode(true);
        safe.setOnKeyListener((v, key, event) ->
                event.getAction() == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_BACK);

        frame.addView(safe, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView lockIcon = tv("🔒", sh * .050f, Color.WHITE, true);
        lockIcon.setGravity(Gravity.CENTER);
        safe.addView(lockIcon, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(sh * .070f)));

        clock = tv("", sh * .067f, Color.WHITE, true);
        clock.setGravity(Gravity.CENTER);
        safe.addView(clock, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(sh * .078f)));

        TextView msg = tv(Prefs.message(this), sh * .036f, Color.rgb(246, 249, 255), true);
        msg.setGravity(Gravity.CENTER);
        safe.addView(msg, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(sh * .055f)));

        TextView until = tv("سيفتح التلفزيون تلقائيًا الساعة " + formatEnd(), sh * .020f,
                Color.rgb(171, 207, 245), false);
        until.setGravity(Gravity.CENTER);
        safe.addView(until, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(sh * .042f)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(sh * .455f));
        safe.addView(row, rowLp);

        int cardW = Math.round(sw * .205f);
        int cardH = Math.round(sh * .190f);

        LinearLayout leftCard = card();
        TextView leftTitle = cardText("الوقت الحالي", sh * .019f, Color.rgb(197, 220, 245), false);
        sideClock = cardText("", sh * .039f, Color.WHITE, true);
        TextView date = cardText(new SimpleDateFormat("EEEE، d MMMM", new Locale("ar")).format(new Date()),
                sh * .017f, Color.rgb(183, 207, 235), false);
        leftCard.addView(leftTitle);
        leftCard.addView(sideClock);
        leftCard.addView(date);
        row.addView(leftCard, boxParams(cardW, cardH, Math.round(sw * .018f)));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);

        pinDots = tv("○  ○  ○  ○", sh * .031f, Color.rgb(89, 205, 255), true);
        pinDots.setGravity(Gravity.CENTER);
        center.addView(pinDots, new LinearLayout.LayoutParams(
                Math.round(sw * .34f), Math.round(sh * .055f)));

        hint = tv("أدخل PIN للفتح قبل الموعد", sh * .018f, Color.rgb(207, 225, 245), false);
        hint.setGravity(Gravity.CENTER);
        center.addView(hint, new LinearLayout.LayoutParams(
                Math.round(sw * .34f), Math.round(sh * .043f)));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(4);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        int gridW = Math.round(sw * .34f);
        int gridH = Math.round(sh * .315f);
        int gap = Math.max(4, Math.round(sh * .0055f));
        int bw = (gridW - gap * 6) / 3;
        int bh = (gridH - gap * 8) / 4;

        String[] labels = {"1","2","3","4","5","6","7","8","9","مسح","0","فتح"};
        Button first = null;
        for (String label : labels) {
            Button b = new Button(this);
            if (first == null) first = b;
            b.setText(label);
            b.setTextSize(TypedValue.COMPLEX_UNIT_PX, sh * .025f);
            b.setTextColor(Color.WHITE);
            b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            b.setAllCaps(false);
            b.setFocusable(true);
            b.setMinWidth(0);
            b.setMinHeight(0);
            b.setPadding(0, 0, 0, 0);
            b.setBackgroundResource(R.drawable.focus_button);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = bw;
            lp.height = bh;
            lp.setMargins(gap, gap, gap, gap);
            b.setLayoutParams(lp);

            if (label.matches("\\d")) b.setOnClickListener(v -> digit(label));
            else if (label.equals("مسح")) b.setOnClickListener(v -> backspace());
            else b.setOnClickListener(v -> tryUnlock());

            grid.addView(b);
        }

        center.addView(grid, new LinearLayout.LayoutParams(gridW, gridH));
        row.addView(center, new LinearLayout.LayoutParams(
                Math.round(sw * .40f), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout rightCard = card();
        TextView rightTitle = cardText("موعد الفتح", sh * .019f, Color.rgb(197, 220, 245), false);
        TextView endValue = cardText(formatEnd(), sh * .039f, Color.WHITE, true);
        TextView endSub = cardText("تلقائيًا حسب الجدول", sh * .017f, Color.rgb(183, 207, 235), false);
        rightCard.addView(rightTitle);
        rightCard.addView(endValue);
        rightCard.addView(endSub);
        row.addView(rightCard, boxParams(cardW, cardH, Math.round(sw * .018f)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER);

        HksLogoView logo = new HksLogoView(this);
        brand.addView(logo, new LinearLayout.LayoutParams(
                Math.round(sw * .085f), Math.round(sh * .055f)));

        TextView footer = tv("من صنع  hksalameh  |  HKS", sh * .016f,
                Color.rgb(166, 196, 231), false);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        brand.addView(footer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Math.round(sh * .055f)));

        safe.addView(brand, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(sh * .065f)));

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;

        overlay = frame;
        wm.addView(overlay, p);

        if (first != null) first.requestFocus();
        else safe.requestFocus();

        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private LinearLayout.LayoutParams boxParams(int w, int h, int horizontalMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(horizontalMargin, 0, horizontalMargin, 0);
        return lp;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        int px = Math.round(sw * .014f);
        int py = Math.round(sh * .018f);
        l.setPadding(px, py, px, py);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(105, 24, 55, 96));
        bg.setStroke(Math.max(1, Math.round(sh * .0015f)), Color.argb(145, 90, 151, 225));
        bg.setCornerRadius(sh * .026f);
        l.setBackground(bg);
        return l;
    }

    private TextView cardText(String s, float sizePx, int color, boolean bold) {
        TextView t = tv(s, sizePx, color, bold);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Math.round(sh * .004f), 0, Math.round(sh * .004f));
        return t;
    }

    private void digit(String d) {
        if (entered.length() == 0) {
            hint.setText("أدخل PIN للفتح قبل الموعد");
            hint.setTextColor(Color.rgb(207, 225, 245));
        }
        if (entered.length() < 8) {
            entered.append(d);
            updateDots();
        }
    }

    private void backspace() {
        if (entered.length() > 0) {
            entered.deleteCharAt(entered.length() - 1);
            updateDots();
        }
    }

    private void updateDots() {
        StringBuilder s = new StringBuilder();
        int n = Math.max(4, Math.max(Prefs.pin(this).length(), entered.length()));
        for (int i = 0; i < n; i++) {
            if (i > 0) s.append("  ");
            s.append(i < entered.length() ? "●" : "○");
        }
        pinDots.setText(s.toString());
    }

    private void tryUnlock() {
        if (Prefs.pin(this).equals(entered.toString())) {
            if (!forced && ScheduleUtil.shouldLock(this)) {
                Prefs.setBypassUntil(this, ScheduleUtil.currentWindowEnd(this));
            }
            entered.setLength(0);
            forced = false;
            stopSelf();
        } else {
            entered.setLength(0);
            updateDots();
            hint.setText("PIN غير صحيح — حاول مرة أخرى");
            hint.setTextColor(Color.rgb(255, 125, 125));
        }
    }

    private void updateClock() {
        String now = new SimpleDateFormat("h:mm a", new Locale("ar")).format(new Date());
        if (clock != null) clock.setText(now);
        if (sideClock != null) sideClock.setText(now);
    }

    private String formatEnd() {
        int h = Prefs.endHour(this), m = Prefs.endMinute(this), hh = h % 12;
        if (hh == 0) hh = 12;
        return String.format(new Locale("ar"), "%d:%02d %s", hh, m, h < 12 ? "ص" : "م");
    }

    private TextView tv(String s, float sizePx, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "TV Lock", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps scheduled TV lock active");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
        }
    }

    private Notification notification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("TV Lock")
                .setContentText("الحماية والجدول يعملان")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        if (overlay != null && wm != null) {
            try { wm.removeView(overlay); } catch (Exception ignored) {}
            overlay = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) {
        return null;
    }
}
