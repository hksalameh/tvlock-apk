package com.smartcodejo.tvlock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.InputType;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * PIN field for Android TV that never paints the real digits.
 * Some TV IMEs ignore the normal password transformation and briefly expose
 * numeric PINs, so the actual text is made transparent and bullets are drawn
 * by this view instead.
 */
public class MaskedPinEditText extends EditText {
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MaskedPinEditText(Context context) {
        super(context);
        init();
    }

    public MaskedPinEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MaskedPinEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        setTextColor(Color.TRANSPARENT);
        setCursorVisible(false);
        maskPaint.setColor(Color.WHITE);
        maskPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = getText() == null ? 0 : getText().length();
        if (count <= 0) return;

        StringBuilder mask = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) mask.append("  ");
            mask.append("•");
        }

        maskPaint.setTextSize(getTextSize());
        Paint.FontMetrics fm = maskPaint.getFontMetrics();
        float y = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(mask.toString(), getWidth() / 2f, y, maskPaint);
    }
}
