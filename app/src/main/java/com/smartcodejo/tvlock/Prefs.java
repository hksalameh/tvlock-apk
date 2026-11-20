package com.smartcodejo.tvlock;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String NAME = "tv_lock_prefs";
    private static SharedPreferences p(Context c){ return c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }

    public static boolean enabled(Context c){ return p(c).getBoolean("enabled", false); }
    public static void setEnabled(Context c, boolean v){ p(c).edit().putBoolean("enabled", v).apply(); }
    public static int startHour(Context c){ return p(c).getInt("start_h", 0); }
    public static int startMinute(Context c){ return p(c).getInt("start_m", 0); }
    public static int endHour(Context c){ return p(c).getInt("end_h", 8); }
    public static int endMinute(Context c){ return p(c).getInt("end_m", 0); }
    public static void setStart(Context c, int h, int m){ p(c).edit().putInt("start_h", h).putInt("start_m", m).apply(); }
    public static void setEnd(Context c, int h, int m){ p(c).edit().putInt("end_h", h).putInt("end_m", m).apply(); }
    // Optional per-weekday start/end (ISO day 1=Mon..7=Sun). Falls back to the general start/end.
    public static boolean hasDayTimes(Context c, int iso){ return p(c).getBoolean("dt_set_"+iso, false); }
    public static int dayStartHour(Context c, int iso){ return hasDayTimes(c,iso) ? p(c).getInt("dt_sh_"+iso, startHour(c)) : startHour(c); }
    public static int dayStartMinute(Context c, int iso){ return hasDayTimes(c,iso) ? p(c).getInt("dt_sm_"+iso, startMinute(c)) : startMinute(c); }
    public static int dayEndHour(Context c, int iso){ return hasDayTimes(c,iso) ? p(c).getInt("dt_eh_"+iso, endHour(c)) : endHour(c); }
    public static int dayEndMinute(Context c, int iso){ return hasDayTimes(c,iso) ? p(c).getInt("dt_em_"+iso, endMinute(c)) : endMinute(c); }
    public static void setDayTimes(Context c, int iso, int sh, int sm, int eh, int em){
        p(c).edit().putBoolean("dt_set_"+iso, true).putInt("dt_sh_"+iso, sh).putInt("dt_sm_"+iso, sm)
                .putInt("dt_eh_"+iso, eh).putInt("dt_em_"+iso, em).apply();
    }
    public static void clearAllDayTimes(Context c){
        SharedPreferences.Editor e=p(c).edit();
        for(int i=1;i<=7;i++) e.remove("dt_set_"+i);
        e.apply();
    }

    // One-night override: the lock window that STARTS on tonight_date uses this start time.
    // It is date-specific, so it expires by itself and the normal schedule returns tomorrow.
    public static String tonightDate(Context c){ return p(c).getString("tonight_date", ""); }
    public static int tonightHour(Context c){ return p(c).getInt("tonight_h", 0); }
    public static int tonightMinute(Context c){ return p(c).getInt("tonight_m", 0); }
    public static void setTonight(Context c, String isoDate, int h, int m){ p(c).edit().putString("tonight_date", isoDate).putInt("tonight_h", h).putInt("tonight_m", m).apply(); }
    public static void clearTonight(Context c){ p(c).edit().remove("tonight_date").remove("tonight_h").remove("tonight_m").apply(); }
    public static String pin(Context c){ return p(c).getString("pin", ""); }
    public static void setPin(Context c, String pin){ p(c).edit().putString("pin", pin).apply(); }
    public static String message(Context c){ return p(c).getString("message", "انتهى وقت استخدام التلفزيون"); }
    public static void setMessage(Context c, String s){ p(c).edit().putString("message", s).apply(); }
    public static boolean dayEnabled(Context c, int isoDay){ return p(c).getBoolean("day_"+isoDay, true); }
    public static void setDayEnabled(Context c, int isoDay, boolean v){ p(c).edit().putBoolean("day_"+isoDay, v).apply(); }
    public static long bypassUntil(Context c){ return p(c).getLong("bypass_until", 0L); }
    public static void setBypassUntil(Context c, long t){ p(c).edit().putLong("bypass_until", t).apply(); }

    public static long adminBypassUntil(Context c){ return p(c).getLong("admin_bypass_until", 0L); }
    public static void setAdminBypassUntil(Context c, long t){ p(c).edit().putLong("admin_bypass_until", t).apply(); }
    public static boolean adminBypassActive(Context c){ return adminBypassUntil(c) > System.currentTimeMillis(); }
}
