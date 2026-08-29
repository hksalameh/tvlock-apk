package com.smartcodejo.tvlock;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String NAME = "tv_lock_prefs";
    private static SharedPreferences p(Context c){ return c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }

    public static boolean enabled(Context c){ return p(c).getBoolean("enabled", false); }
    public static void setEnabled(Context c, boolean v){ p(c).edit().putBoolean("enabled", v).apply(); }
    public static int startHour(Context c){ return p(c).getInt("start_h", 1); }
    public static int startMinute(Context c){ return p(c).getInt("start_m", 30); }
    public static int endHour(Context c){ return p(c).getInt("end_h", 8); }
    public static int endMinute(Context c){ return p(c).getInt("end_m", 0); }
    public static void setStart(Context c, int h, int m){ p(c).edit().putInt("start_h", h).putInt("start_m", m).apply(); }
    public static void setEnd(Context c, int h, int m){ p(c).edit().putInt("end_h", h).putInt("end_m", m).apply(); }
    public static String pin(Context c){ return p(c).getString("pin", ""); }
    public static void setPin(Context c, String pin){ p(c).edit().putString("pin", pin).apply(); }
    public static String message(Context c){ return p(c).getString("message", "انتهى وقت استخدام التلفزيون"); }
    public static void setMessage(Context c, String s){ p(c).edit().putString("message", s).apply(); }
    public static boolean dayEnabled(Context c, int isoDay){ return p(c).getBoolean("day_"+isoDay, true); }
    public static void setDayEnabled(Context c, int isoDay, boolean v){ p(c).edit().putBoolean("day_"+isoDay, v).apply(); }
    public static long bypassUntil(Context c){ return p(c).getLong("bypass_until", 0L); }
    public static void setBypassUntil(Context c, long t){ p(c).edit().putLong("bypass_until", t).apply(); }
}
