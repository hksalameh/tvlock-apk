package com.smartcodejo.tapmover;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String PREFS = "tap_settings";
    private static final String KEY_TAPS_PER_SECOND = "taps_per_second";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int savedSpeed = clamp(prefs.getInt(KEY_TAPS_PER_SECOND, 10), 5, 20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(36), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(248, 248, 248));

        TextView title = new TextView(this);
        title.setText("Tap Mover");
        title.setTextSize(28);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView speedTitle = new TextView(this);
        speedTitle.setText("عدد الضغطات في الثانية");
        speedTitle.setTextSize(20);
        speedTitle.setTextColor(Color.DKGRAY);
        speedTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams speedTitleLp = new LinearLayout.LayoutParams(-1, -2);
        speedTitleLp.topMargin = dp(24);
        root.addView(speedTitle, speedTitleLp);

        TextView speedValue = new TextView(this);
        speedValue.setText(savedSpeed + " ضغطات/ثانية");
        speedValue.setTextSize(18);
        speedValue.setTextColor(Color.BLACK);
        speedValue.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams speedValueLp = new LinearLayout.LayoutParams(-1, -2);
        speedValueLp.topMargin = dp(6);
        root.addView(speedValue, speedValueLp);

        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(5);
        picker.setMaxValue(20);
        picker.setWrapSelectorWheel(false);
        picker.setValue(savedSpeed);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        picker.setOnValueChangedListener((view, oldVal, newVal) -> {
            speedValue.setText(newVal + " ضغطات/ثانية");
            prefs.edit().putInt(KEY_TAPS_PER_SECOND, newVal).apply();
        });
        LinearLayout.LayoutParams pickerLp = new LinearLayout.LayoutParams(dp(140), dp(120));
        pickerLp.topMargin = dp(4);
        root.addView(picker, pickerLp);

        TextView hint = new TextView(this);
        hint.setText("اختر أي سرعة من 5 إلى 20. يتم حفظ الاختيار تلقائيًا.");
        hint.setTextSize(14);
        hint.setTextColor(Color.GRAY);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(this);
        info.setText("1) فعّل خدمة Tap Mover من إمكانية الوصول.\n\n" +
                "2) سيظهر زر ▶ وعلامة ◎ فوق الشاشة.\n\n" +
                "3) اسحب ◎ إلى المنطقة المطلوبة.\n\n" +
                "4) اضغط ▶ للتشغيل. ينتقل الضغط بين نقاط قريبة حول ◎ كل ثانية.");
        info.setTextSize(16);
        info.setTextColor(Color.DKGRAY);
        info.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = dp(22);
        root.addView(info, infoLp);

        Button open = new Button(this);
        open.setText("فتح إعدادات إمكانية الوصول");
        open.setTextSize(17);
        open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, dp(58));
        btnLp.topMargin = dp(24);
        root.addView(open, btnLp);

        TextView note = new TextView(this);
        note.setText("لإيقافه فورًا اضغط زر ■ العائم.");
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        note.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(18);
        root.addView(note, noteLp);

        setContentView(root);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
