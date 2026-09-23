package com.smartcodejo.tapmover;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(248, 248, 248));

        TextView title = new TextView(this);
        title.setText("Tap Mover");
        title.setTextSize(28);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(this);
        info.setText("1) فعّل خدمة Tap Mover من إمكانية الوصول.\n\n" +
                "2) سيظهر زر ▶ وعلامة ◎ فوق الشاشة.\n\n" +
                "3) اسحب ◎ إلى المنطقة المطلوبة.\n\n" +
                "4) اضغط ▶ للتشغيل. ينتقل الضغط بين نقاط حول ◎ كل ثانية.\n\n" +
                "السرعة الافتراضية: 4 ضغطات/ثانية.");
        info.setTextSize(18);
        info.setTextColor(Color.DKGRAY);
        info.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = dp(32);
        root.addView(info, infoLp);

        Button open = new Button(this);
        open.setText("فتح إعدادات إمكانية الوصول");
        open.setTextSize(17);
        open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, dp(58));
        btnLp.topMargin = dp(32);
        root.addView(open, btnLp);

        TextView note = new TextView(this);
        note.setText("لإيقافه فورًا اضغط زر ■ العائم. الأداة لا تحتوي على أي ميزة لإخفاء الأتمتة أو تجاوز أنظمة التطبيقات.");
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        note.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(24);
        root.addView(note, noteLp);

        setContentView(root);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
