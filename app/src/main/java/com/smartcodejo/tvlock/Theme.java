package com.smartcodejo.tvlock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/** Colour palettes for the whole app (chosen from Settings → المظهر). */
final class Theme {
    private Theme() {}

    static final String[] NAMES = {"ليل أزرق", "زمرّد", "غروب", "ذهبي", "بنفسجي", "رمادي"};
    static final String[] ICONS = {"🌙", "🌿", "🌇", "✨", "🔮", "🪨"};

    private static int[] rgb(int r, int g, int b) { return new int[]{r, g, b}; }

    // 5 background stops, accent, focus start, focus end, aurora x3, mountain, card fill
    private static final int[][][] BG = {
            {rgb(1,8,28), rgb(6,27,68), rgb(22,64,118), rgb(36,48,103), rgb(6,18,43)},
            {rgb(1,18,16), rgb(4,45,40), rgb(14,90,78), rgb(20,70,70), rgb(3,22,22)},
            {rgb(24,6,30), rgb(60,16,64), rgb(130,40,90), rgb(190,80,70), rgb(30,10,36)},
            {rgb(8,8,8), rgb(26,22,10), rgb(60,46,12), rgb(40,30,10), rgb(10,9,6)},
            {rgb(12,6,32), rgb(32,14,80), rgb(70,34,140), rgb(50,28,110), rgb(14,8,36)},
            {rgb(6,8,10), rgb(18,22,28), rgb(40,48,58), rgb(30,36,44), rgb(8,10,14)},
    };
    private static final int[][] ACC = {
            rgb(83,210,255), rgb(88,235,190), rgb(255,170,110), rgb(255,208,90), rgb(190,150,255), rgb(150,200,230)};
    private static final int[][] FOC1 = {
            rgb(11,102,255), rgb(14,159,110), rgb(217,72,15), rgb(184,134,11), rgb(109,40,217), rgb(59,91,122)};
    private static final int[][] FOC2 = {
            rgb(43,140,255), rgb(43,203,147), rgb(249,115,22), rgb(230,180,34), rgb(139,92,246), rgb(91,132,171)};
    private static final int[][][] AUR = {
            {rgb(26,179,214), rgb(97,92,224), rgb(219,86,163)},
            {rgb(20,200,150), rgb(60,180,120), rgb(190,220,90)},
            {rgb(255,120,60), rgb(220,70,130), rgb(150,70,200)},
            {rgb(230,180,50), rgb(200,130,30), rgb(255,220,120)},
            {rgb(140,90,255), rgb(220,90,200), rgb(90,160,255)},
            {rgb(120,160,190), rgb(90,120,160), rgb(160,180,200)},
    };
    private static final int[][] MTN = {
            rgb(25,45,82), rgb(14,56,52), rgb(50,18,56), rgb(36,30,14), rgb(34,22,72), rgb(26,32,40)};
    private static final int[][] CARD = {
            rgb(6,30,66), rgb(4,40,36), rgb(50,14,50), rgb(34,28,10), rgb(28,14,64), rgb(20,26,34)};

    static int idx(Context c) { return 0; }
    private static int col(int[] v) { return Color.rgb(v[0], v[1], v[2]); }
    private static int col(int[] v, int a) { return Color.argb(a, v[0], v[1], v[2]); }

    static int[] bg(Context c) {
        int[][] s = BG[idx(c)];
        int[] o = new int[5];
        for (int i = 0; i < 5; i++) o[i] = col(s[i]);
        return o;
    }
    static int bgAt(Context c, int i) { return col(BG[idx(c)][i]); }
    static int accent(Context c) { return col(ACC[idx(c)]); }
    static int accent(Context c, int alpha) { return col(ACC[idx(c)], alpha); }
    static int aurora(Context c, int i, int alpha) { return col(AUR[idx(c)][i], alpha); }
    static int mountain(Context c) { return col(MTN[idx(c)]); }
    static int card(Context c, int alpha) { return col(CARD[idx(c)], alpha); }
    static int focus1(Context c) { return col(FOC1[idx(c)]); }
    static int focus2(Context c) { return col(FOC2[idx(c)]); }

    /** Button background: normal / focused / pressed, in the current palette. */
    static StateListDrawable focusBg(Context c, float density) {
        int r = (int) (16 * density), w2 = Math.max(1, (int) (2 * density)), w1 = Math.max(1, (int) density);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.argb(85, 255, 255, 255));
        focused.setCornerRadius(r);
        focused.setStroke(w2, Color.WHITE);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.argb(28, 255, 255, 255));
        normal.setCornerRadius(r);
        normal.setStroke(w1, Color.argb(60, 255, 255, 255));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, focused);
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }
}
