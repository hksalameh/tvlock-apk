package com.smartcodejo.tvlock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

public class LockBackgroundView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public LockBackgroundView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        final float w = getWidth();
        final float h = getHeight();
        if (w <= 0 || h <= 0) return;

        paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{Color.rgb(2, 12, 35), Color.rgb(8, 39, 83), Color.rgb(5, 18, 43)},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        paint.setShader(new LinearGradient(0, h * .30f, 0, h * .72f,
                new int[]{Color.TRANSPARENT, Color.argb(80, 42, 113, 190), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, h * .25f, w, h * .78f, paint);
        paint.setShader(null);

        paint.setColor(Color.WHITE);
        for (int i = 0; i < 68; i++) {
            float x = ((i * 137 + 43) % 997) / 997f * w;
            float y = ((i * 83 + 17) % 461) / 461f * h * .40f;
            paint.setAlpha(55 + (i * 31) % 145);
            float r = 0.7f + (i % 4) * 0.35f;
            c.drawCircle(x, y, r, paint);
        }
        paint.setAlpha(255);

        paint.setColor(Color.rgb(12, 36, 69));
        path.reset();
        path.moveTo(0, h * .63f);
        path.lineTo(0, h * .54f);
        path.lineTo(w * .08f, h * .47f);
        path.lineTo(w * .16f, h * .55f);
        path.lineTo(w * .27f, h * .43f);
        path.lineTo(w * .38f, h * .57f);
        path.lineTo(w * .52f, h * .46f);
        path.lineTo(w * .63f, h * .55f);
        path.lineTo(w * .76f, h * .42f);
        path.lineTo(w * .86f, h * .51f);
        path.lineTo(w, h * .44f);
        path.lineTo(w, h * .68f);
        path.close();
        c.drawPath(path, paint);

        paint.setColor(Color.rgb(3, 16, 37));
        path.reset();
        path.moveTo(0, h * .70f);
        path.lineTo(0, h * .59f);
        path.lineTo(w * .12f, h * .49f);
        path.lineTo(w * .25f, h * .64f);
        path.lineTo(w * .39f, h * .50f);
        path.lineTo(w * .51f, h * .67f);
        path.lineTo(w * .66f, h * .53f);
        path.lineTo(w * .80f, h * .66f);
        path.lineTo(w, h * .55f);
        path.lineTo(w, h * .72f);
        path.close();
        c.drawPath(path, paint);

        paint.setShader(new LinearGradient(0, h * .66f, 0, h,
                new int[]{Color.rgb(4, 28, 61), Color.rgb(2, 11, 27)}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, h * .66f, w, h, paint);
        paint.setShader(null);

        paint.setColor(Color.argb(110, 59, 142, 223));
        c.drawRect(0, h * .665f, w, h * .668f, paint);

        for (int i = 0; i < 28; i++) {
            float x = (i + .5f) / 28f * w;
            float y = h * (.665f + ((i % 3) * .0015f));
            paint.setColor(i % 3 == 0 ? Color.rgb(255, 188, 92) : Color.rgb(99, 166, 238));
            paint.setAlpha(115 + (i % 4) * 25);
            c.drawCircle(x, y, 1.7f + (i % 2), paint);
            paint.setAlpha(25 + (i % 3) * 12);
            c.drawRect(x - 1f, y + 4f, x + 1f, y + h * (.035f + (i % 5) * .008f), paint);
        }
        paint.setAlpha(255);

        paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{Color.argb(125, 0, 5, 20), Color.TRANSPARENT, Color.argb(105, 0, 4, 14)},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
    }
}
