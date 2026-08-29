package com.smartcodejo.tvlock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

public class HksLogoView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path triangle = new Path();

    public HksLogoView(Context context) {
        super(context);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float markW = Math.min(w * .42f, h * 1.45f);
        float cx = markW * .50f;
        float cy = h * .50f;
        float r = Math.min(markW, h) * .36f;

        triangle.reset();
        triangle.moveTo(cx - r * .78f, cy - r);
        triangle.lineTo(cx + r * 1.08f, cy);
        triangle.lineTo(cx - r * .78f, cy + r);
        triangle.close();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, h * .055f));
        paint.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                new int[]{Color.rgb(49, 124, 255), Color.rgb(50, 225, 255)},
                null, Shader.TileMode.CLAMP));
        c.drawPath(triangle, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(h * .47f);
        paint.setShader(new LinearGradient(0, cy - h * .3f, 0, cy + h * .3f,
                Color.WHITE, Color.rgb(73, 176, 255), Shader.TileMode.CLAMP));
        c.drawText("HKS", cx, cy - (paint.ascent() + paint.descent()) / 2f, paint);
        paint.setShader(null);
    }
}
