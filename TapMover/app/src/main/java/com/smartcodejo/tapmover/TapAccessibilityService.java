package com.smartcodejo.tapmover;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

public class TapAccessibilityService extends AccessibilityService {
    private WindowManager wm;
    private TextView control;
    private TextView target;
    private WindowManager.LayoutParams controlLp;
    private WindowManager.LayoutParams targetLp;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private int pointIndex = 0;
    private long lastPointChange = 0L;
    private int currentDx = 0;
    private int currentDy = -28;

    private static final long TAP_INTERVAL_MS = 250L;
    private static final long MOVE_INTERVAL_MS = 1000L;

    // Keep the working behavior of v1, but make consecutive points much closer.
    // Radius 28dp keeps taps just outside the 46dp target bubble, while the
    // next point is only about 11dp away.
    private static final int[][] OFFSETS_DP = new int[][] {
            {0, -28}, {11, -26}, {20, -20}, {26, -11},
            {28, 0}, {26, 11}, {20, 20}, {11, 26},
            {0, 28}, {-11, 26}, {-20, 20}, {-26, 11},
            {-28, 0}, {-26, -11}, {-20, -20}, {-11, -26}
    };

    private final Runnable tapLoop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            try {
                if (target == null || targetLp == null || !target.isAttachedToWindow()) {
                    stopTapping();
                    return;
                }

                long now = System.currentTimeMillis();
                if (now - lastPointChange >= MOVE_INTERVAL_MS) {
                    int[] p = OFFSETS_DP[pointIndex % OFFSETS_DP.length];
                    currentDx = dp(p[0]);
                    currentDy = dp(p[1]);
                    pointIndex++;
                    lastPointChange = now;
                }

                int targetWidth = target.getWidth() > 0 ? target.getWidth() : dp(46);
                int targetHeight = target.getHeight() > 0 ? target.getHeight() : dp(46);
                int baseX = targetLp.x + targetWidth / 2;
                int baseY = targetLp.y + targetHeight / 2;

                Point screen = getScreenSize();
                int x = clamp(baseX + currentDx, 1, Math.max(1, screen.x - 2));
                int y = clamp(baseY + currentDy, 1, Math.max(1, screen.y - 2));
                tapSafely(x, y);
            } catch (Throwable ignored) {
                // A rejected gesture must never kill the accessibility service.
            }

            if (running) handler.postDelayed(this, TAP_INTERVAL_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        try {
            showOverlays();
        } catch (Throwable ignored) {
            removeOverlays();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() {
        stopTapping();
    }

    @Override
    public void onDestroy() {
        stopTapping();
        removeOverlays();
        super.onDestroy();
    }

    private void showOverlays() {
        if (control != null || wm == null) return;

        control = makeBubble("▶", Color.rgb(30, 130, 70));
        controlLp = overlayParams(dp(58), dp(58), dp(20), dp(180));
        control.setOnClickListener(v -> {
            if (running) stopTapping(); else startTapping();
        });
        makeDraggable(control, controlLp);
        wm.addView(control, controlLp);

        target = makeBubble("◎", Color.argb(170, 230, 110, 20));
        targetLp = overlayParams(dp(46), dp(46), dp(120), dp(320));
        makeDraggable(target, targetLp);
        wm.addView(target, targetLp);
    }

    private void startTapping() {
        if (running || target == null || control == null) return;
        running = true;
        pointIndex = 0;
        currentDx = 0;
        currentDy = dp(-28);
        lastPointChange = 0L;
        control.setText("■");
        control.setBackground(circle(Color.rgb(180, 45, 45)));
        handler.removeCallbacks(tapLoop);
        handler.post(tapLoop);
    }

    private void stopTapping() {
        running = false;
        handler.removeCallbacks(tapLoop);
        if (control != null) {
            try {
                control.setText("▶");
                control.setBackground(circle(Color.rgb(30, 130, 70)));
            } catch (Throwable ignored) { }
        }
    }

    private void tapSafely(int x, int y) {
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, 40);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(stroke)
                    .build();
            dispatchGesture(gesture, null, null);
        } catch (Throwable ignored) { }
    }

    private TextView makeBubble(String text, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(24);
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setBackground(circle(color));
        v.setElevation(dp(6));
        return v;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(dp(2), Color.WHITE);
        return g;
    }

    private WindowManager.LayoutParams overlayParams(int w, int h, int x, int y) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = x;
        lp.y = y;
        return lp;
    }

    private void makeDraggable(View view, WindowManager.LayoutParams lp) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float downX, downY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                try {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = lp.x;
                            startY = lp.y;
                            downX = e.getRawX();
                            downY = e.getRawY();
                            moved = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = e.getRawX() - downX;
                            float dy = e.getRawY() - downY;
                            if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;

                            Point screen = getScreenSize();
                            int maxX = Math.max(0, screen.x - v.getWidth());
                            int maxY = Math.max(0, screen.y - v.getHeight());
                            lp.x = clamp(startX + (int) dx, 0, maxX);
                            lp.y = clamp(startY + (int) dy, 0, maxY);

                            if (wm != null && v.isAttachedToWindow()) {
                                wm.updateViewLayout(v, lp);
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (!moved && e.getActionMasked() == MotionEvent.ACTION_UP) {
                                v.performClick();
                            }
                            return true;
                    }
                } catch (Throwable ignored) {
                    return true;
                }
                return false;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private Point getScreenSize() {
        Point p = new Point();
        try {
            if (wm != null && wm.getDefaultDisplay() != null) {
                wm.getDefaultDisplay().getRealSize(p);
            }
        } catch (Throwable ignored) { }

        if (p.x <= 0 || p.y <= 0) {
            p.x = getResources().getDisplayMetrics().widthPixels;
            p.y = getResources().getDisplayMetrics().heightPixels;
        }
        return p;
    }

    private void removeOverlays() {
        try { if (control != null && wm != null && control.isAttachedToWindow()) wm.removeView(control); } catch (Throwable ignored) {}
        try { if (target != null && wm != null && target.isAttachedToWindow()) wm.removeView(target); } catch (Throwable ignored) {}
        control = null;
        target = null;
        controlLp = null;
        targetLp = null;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
