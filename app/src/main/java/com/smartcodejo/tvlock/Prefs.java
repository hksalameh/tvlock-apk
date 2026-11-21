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
    // ---- PIN: stored as PBKDF2 hash + salt (legacy plain "pin" is migrated on first use) ----
    private static byte[] hashPin(String pin, byte[] salt){
        try{
            javax.crypto.SecretKeyFactory f=javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return f.generateSecret(new javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 20000, 256)).getEncoded();
        }catch(Exception e){ throw new IllegalStateException(e); }
    }
    public static boolean hasPin(Context c){
        return !p(c).getString("pin_hash","").isEmpty() || !p(c).getString("pin","").isEmpty();
    }
    public static int pinLength(Context c){
        int n=p(c).getInt("pin_len",0);
        return n>0?n:p(c).getString("pin","").length();
    }
    public static void setPin(Context c, String pin){
        byte[] salt=new byte[16]; new java.security.SecureRandom().nextBytes(salt);
        p(c).edit()
                .putString("pin_salt", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
                .putString("pin_hash", android.util.Base64.encodeToString(hashPin(pin,salt), android.util.Base64.NO_WRAP))
                .putInt("pin_len", pin.length())
                .remove("pin").apply();
    }
    public static boolean checkPin(Context c, String input){
        if(input==null) return false;
        String hash=p(c).getString("pin_hash","");
        if(!hash.isEmpty()){
            byte[] salt=android.util.Base64.decode(p(c).getString("pin_salt",""), android.util.Base64.NO_WRAP);
            byte[] want=android.util.Base64.decode(hash, android.util.Base64.NO_WRAP);
            return java.security.MessageDigest.isEqual(want, hashPin(input,salt));
        }
        String plain=p(c).getString("pin","");
        if(plain.isEmpty()) return false;
        boolean ok=java.security.MessageDigest.isEqual(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8), input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(ok) setPin(c, plain);
        return ok;
    }
    /** Converts a legacy plain-text PIN into a hash (called at boot / service start). */
    public static void migratePin(Context c){
        String plain=p(c).getString("pin","");
        if(!plain.isEmpty()) setPin(c, plain);
    }

    // ---- Forced lock (remote "lock now", session break). Beats the schedule and temporary unlocks. ----
    public static long forceLockUntil(Context c){ return p(c).getLong("force_lock_until", 0L); }
    public static void setForceLockUntil(Context c, long ms){ p(c).edit().putLong("force_lock_until", ms).apply(); }

    // ---- Extra daily lock window ("exam mode") ----
    public static boolean extraOn(Context c){ return p(c).getBoolean("extra_on", false); }
    public static int extraStartHour(Context c){ return p(c).getInt("extra_sh", 16); }
    public static int extraStartMinute(Context c){ return p(c).getInt("extra_sm", 0); }
    public static int extraEndHour(Context c){ return p(c).getInt("extra_eh", 20); }
    public static int extraEndMinute(Context c){ return p(c).getInt("extra_em", 0); }
    public static void setExtra(Context c, boolean on, int sh, int sm, int eh, int em){
        p(c).edit().putBoolean("extra_on", on).putInt("extra_sh", sh).putInt("extra_sm", sm).putInt("extra_eh", eh).putInt("extra_em", em).apply();
    }
    public static void setExtraOn(Context c, boolean on){ p(c).edit().putBoolean("extra_on", on).apply(); }

    // ---- Session limit (continuous screen-on time) ----
    public static boolean sessionOn(Context c){ return p(c).getBoolean("session_on", true); }
    public static void setSessionOn(Context c, boolean v){ p(c).edit().putBoolean("session_on", v).apply(); }
    public static int sessionLimitMin(Context c){ return p(c).getInt("session_limit_min", 120); }
    public static int sessionBreakMin(Context c){ return p(c).getInt("session_break_min", 15); }
    public static void setSessionLimitMin(Context c, int v){ p(c).edit().putInt("session_limit_min", v).apply(); }
    public static void setSessionBreakMin(Context c, int v){ p(c).edit().putInt("session_break_min", v).apply(); }

    // ---- Emergency remote sequence: one use per night ----
    public static String emergencyUsedKey(Context c){ return p(c).getString("emergency_key", ""); }
    public static void setEmergencyUsedKey(Context c, String k){ p(c).edit().putString("emergency_key", k).apply(); }

    // ---- Telegram alerts ----
    public static String tgToken(Context c){ return p(c).getString("tg_token", ""); }
    public static String tgChat(Context c){ return p(c).getString("tg_chat", ""); }
    public static String tvName(Context c){ return p(c).getString("tv_name", android.os.Build.MODEL); }
    public static void setTelegram(Context c, String token, String chat, String name){
        p(c).edit().putString("tg_token", token).putString("tg_chat", chat).putString("tv_name", name).apply();
    }

    // ---- Per-app block ----
    public static boolean appsOn(Context c){ return p(c).getBoolean("apps_on", false); }
    public static void setAppsOn(Context c, boolean v){ p(c).edit().putBoolean("apps_on", v).apply(); }
    public static java.util.Set<String> appsBlocked(Context c){ return new java.util.HashSet<>(p(c).getStringSet("apps_set", new java.util.HashSet<>())); }
    public static void setAppsBlocked(Context c, java.util.Set<String> set){ p(c).edit().putStringSet("apps_set", new java.util.HashSet<>(set)).apply(); }
    public static int appsStartHour(Context c){ return p(c).getInt("apps_sh", 16); }
    public static int appsStartMinute(Context c){ return p(c).getInt("apps_sm", 0); }
    public static int appsEndHour(Context c){ return p(c).getInt("apps_eh", 20); }
    public static int appsEndMinute(Context c){ return p(c).getInt("apps_em", 0); }
    public static void setAppsWindow(Context c, int sh, int sm, int eh, int em){
        p(c).edit().putInt("apps_sh", sh).putInt("apps_sm", sm).putInt("apps_eh", eh).putInt("apps_em", em).apply();
    }

    // ---- Auto update ----
    public static long updateLastCheck(Context c){ return p(c).getLong("upd_check", 0L); }
    public static void setUpdateLastCheck(Context c, long v){ p(c).edit().putLong("upd_check", v).apply(); }
    public static int updateReadyCode(Context c){ return p(c).getInt("upd_ready_code", 0); }
    public static String updateReadyName(Context c){ return p(c).getString("upd_ready_name", ""); }
    public static void setUpdateReady(Context c, int code, String name){ p(c).edit().putInt("upd_ready_code", code).putString("upd_ready_name", name).apply(); }

    // ---- Backup / restore of all settings (volatile runtime keys are excluded) ----
    private static boolean volatileKey(String k){
        return k.equals("bypass_until") || k.equals("admin_bypass_until") || k.equals("force_lock_until")
                || k.startsWith("scr_") || k.startsWith("upd_") || k.equals("emergency_key");
    }
    public static String exportJson(Context c){
        try{
            org.json.JSONObject o=new org.json.JSONObject();
            for(java.util.Map.Entry<String,?> e: p(c).getAll().entrySet()){
                if(volatileKey(e.getKey())) continue;
                Object v=e.getValue(); org.json.JSONObject t=new org.json.JSONObject();
                if(v instanceof Boolean){ t.put("t","b"); t.put("v",(Boolean)v); }
                else if(v instanceof Integer){ t.put("t","i"); t.put("v",(Integer)v); }
                else if(v instanceof Long){ t.put("t","l"); t.put("v",(Long)v); }
                else if(v instanceof String){ t.put("t","s"); t.put("v",(String)v); }
                else if(v instanceof java.util.Set){ t.put("t","a"); t.put("v",new org.json.JSONArray((java.util.Set<?>)v)); }
                else continue;
                o.put(e.getKey(), t);
            }
            return o.toString();
        }catch(Exception e){ return "{}"; }
    }
    public static boolean importJson(Context c, String json){
        try{
            org.json.JSONObject o=new org.json.JSONObject(json);
            SharedPreferences.Editor ed=p(c).edit();
            java.util.Iterator<String> it=o.keys();
            while(it.hasNext()){
                String k=it.next();
                if(volatileKey(k)) continue;
                org.json.JSONObject t=o.getJSONObject(k);
                switch(t.getString("t")){
                    case "b": ed.putBoolean(k,t.getBoolean("v")); break;
                    case "i": ed.putInt(k,t.getInt("v")); break;
                    case "l": ed.putLong(k,t.getLong("v")); break;
                    case "s": ed.putString(k,t.getString("v")); break;
                    case "a": {
                        org.json.JSONArray a=t.getJSONArray("v"); java.util.Set<String> set=new java.util.HashSet<>();
                        for(int i=0;i<a.length();i++) set.add(a.getString(i));
                        ed.putStringSet(k,set); break;
                    }
                    default: break;
                }
            }
            ed.apply();
            return true;
        }catch(Exception e){ return false; }
    }
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
