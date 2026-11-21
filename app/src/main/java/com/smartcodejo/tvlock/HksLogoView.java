package com.smartcodejo.tvlock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** شعار HKS: قفل داخل درع مدوّر + اسم بحروف متباعدة. */
public class HksLogoView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public HksLogoView(Context context) { super(context); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float s = h * .92f;
        float x0 = (w - (s + h * .30f + h * 1.55f)) / 2f;
        if (x0 < 0) x0 = 0;
        float y0 = (h - s) / 2f;

        // درع مدوّر متدرّج
        r.set(x0, y0, x0 + s, y0 + s);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(x0, y0, x0 + s, y0 + s,
                Color.rgb(46, 128, 255), Color.rgb(36, 224, 214), Shader.TileMode.CLAMP));
        c.drawRoundRect(r, s * .30f, s * .30f, p);
        p.setShader(null);

        // قفل أبيض
        float cx = x0 + s / 2f, bw = s * .40f, bh = s * .30f, by = y0 + s * .46f;
        p.setColor(Color.WHITE);
        r.set(cx - bw / 2, by, cx + bw / 2, by + bh);
        c.drawRoundRect(r, s * .06f, s * .06f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * .075f);
        r.set(cx - bw * .32f, y0 + s * .22f, cx + bw * .32f, y0 + s * .22f + bw * .72f);
        c.drawArc(r, 180, 180, false, p);
        c.drawLine(cx - bw * .32f, y0 + s * .22f + bw * .36f, cx - bw * .32f, by + s * .02f, p);
        c.drawLine(cx + bw * .32f, y0 + s * .22f + bw * .36f, cx + bw * .32f, by + s * .02f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(30, 110, 235));
        c.drawCircle(cx, by + bh * .42f, s * .045f, p);
        r.set(cx - s * .017f, by + bh * .42f, cx + s * .017f, by + bh * .78f);
        c.drawRoundRect(r, s * .01f, s * .01f, p);

        // النص
        float tx = x0 + s + h * .22f;
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(h * .56f);
        p.setLetterSpacing(.12f);
        p.setShader(new LinearGradient(tx, 0, tx + h * 1.5f, 0,
                Color.WHITE, Color.rgb(110, 200, 255), Shader.TileMode.CLAMP));
        c.drawText("HKS", tx, y0 + s * .56f, p);
        p.setShader(null);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(h * .20f);
        p.setLetterSpacing(.38f);
        p.setColor(Color.argb(190, 150, 205, 255));
        c.drawText("SALAMEH", tx + h * .02f, y0 + s * .86f, p);
        p.setLetterSpacing(0);
    }
}
