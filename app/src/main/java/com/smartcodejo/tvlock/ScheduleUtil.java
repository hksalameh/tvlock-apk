package com.smartcodejo.tvlock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;

public final class ScheduleUtil {
    static final long WARN_MS = 5 * 60 * 1000L;

    private static LocalDateTime baseStart(Context c, LocalDate d){
        int iso = d.getDayOfWeek().getValue();
        return d.atTime(Prefs.dayStartHour(c, iso), Prefs.dayStartMinute(c, iso));
    }

    private static LocalDateTime startFor(Context c, LocalDate d){
        if(d.toString().equals(Prefs.tonightDate(c))) return d.atTime(Prefs.tonightHour(c), Prefs.tonightMinute(c));
        return baseStart(c, d);
    }

    /** Date of the lock window that "tonight" refers to: the current window if locked, else the next one. */
    public static LocalDate tonightWindowDate(Context c){
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.ofHours(3));
        LocalDate today = now.toLocalDate();
        for(int back=1; back>=0; back--){
            LocalDate d = today.minusDays(back);
            if(!Prefs.dayEnabled(c, d.getDayOfWeek().getValue())) continue;
            ZonedDateTime s = startFor(c,d).atZone(now.getZone());
            ZonedDateTime e = endFor(c,d).atZone(now.getZone());
            if(!now.isBefore(s) && now.isBefore(e)) return d;
        }
        for(int i=0; i<8; i++){
            LocalDate d = today.plusDays(i);
            if(!Prefs.dayEnabled(c, d.getDayOfWeek().getValue())) continue;
            if(baseStart(c, d).atZone(now.getZone()).isAfter(now)) return d;
        }
        return today;
    }

    /** Effective start time (hour, minute) for the window on date d, with any one-night override applied. */
    public static int[] effectiveStart(Context c, LocalDate d){
        LocalDateTime s = startFor(c, d);
        return new int[]{ s.getHour(), s.getMinute() };
    }

    /** Effective end time (hour, minute) of the window that starts on date d. */
    public static int[] effectiveEnd(Context c, LocalDate d){
        LocalDateTime e = endFor(c, d);
        return new int[]{ e.getHour(), e.getMinute() };
    }

    public static void setTonightStart(Context c, int h, int m){
        Prefs.setTonight(c, tonightWindowDate(c).toString(), h, m);
        Prefs.setBypassUntil(c, 0L);
        reschedule(c);
        LockOverlayService.sync(c);
    }

    public static void clearTonightStart(Context c){
        Prefs.clearTonight(c);
        reschedule(c);
        LockOverlayService.sync(c);
    }

    private static LocalDateTime endFor(Context c, LocalDate d){
        LocalDateTime s = startFor(c, d);
        int iso = d.getDayOfWeek().getValue();
        LocalDateTime e = d.atTime(Prefs.dayEndHour(c, iso), Prefs.dayEndMinute(c, iso));
        if(!e.isAfter(s)) e = e.plusDays(1);
        return e;
    }

    private static boolean inNight(Context c, ZonedDateTime now){
        LocalDate today = now.toLocalDate();
        for(int back=0; back<=1; back++){
            LocalDate d = today.minusDays(back);
            if(!Prefs.dayEnabled(c, d.getDayOfWeek().getValue())) continue;
            ZonedDateTime s = startFor(c,d).atZone(now.getZone());
            ZonedDateTime e = endFor(c,d).atZone(now.getZone());
            if(!now.isBefore(s) && now.isBefore(e)) return true;
        }
        return false;
    }

    // ---- extra daily window ("exam mode") ----
    private static LocalDateTime extraStart(Context c, LocalDate d){ return d.atTime(Prefs.extraStartHour(c), Prefs.extraStartMinute(c)); }
    private static LocalDateTime extraEnd(Context c, LocalDate d){
        LocalDateTime s = extraStart(c,d);
        LocalDateTime e = d.atTime(Prefs.extraEndHour(c), Prefs.extraEndMinute(c));
        return e.isAfter(s) ? e : e.plusDays(1);
    }
    private static ZonedDateTime extraEndNow(Context c, ZonedDateTime now){
        if(!Prefs.extraOn(c)) return null;
        LocalDate today = now.toLocalDate();
        for(int back=0; back<=1; back++){
            LocalDate d = today.minusDays(back);
            ZonedDateTime s = extraStart(c,d).atZone(now.getZone());
            ZonedDateTime e = extraEnd(c,d).atZone(now.getZone());
            if(!now.isBefore(s) && now.isBefore(e)) return e;
        }
        return null;
    }

    public static boolean shouldLock(Context c){
        long nowMs = System.currentTimeMillis();
        if(Prefs.forceLockUntil(c) > nowMs) return true;
        if(Prefs.bypassUntil(c) > nowMs) return false;
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.ofHours(3));
        if(Prefs.enabled(c) && inNight(c, now)) return true;
        return extraEndNow(c, now) != null;
    }

    /** Key that identifies the current "night" for once-per-night features (emergency unlock). */
    public static String nightKey(Context c){
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.ofHours(3));
        if(Prefs.enabled(c) && inNight(c, now)) return "N" + tonightWindowDate(c);
        return "D" + now.toLocalDate();
    }

    /** Remote "lock now": locks for the given minutes, overriding temporary unlocks. */
    public static void lockFor(Context c, int minutes){
        Prefs.setForceLockUntil(c, System.currentTimeMillis() + minutes * 60_000L);
        Prefs.setBypassUntil(c, 0L);
        LockOverlayService.sync(c);
    }

    /** +30 minute bonus: if locked, unlock for 30 min; otherwise push tonight's lock start 30 min later. */
    public static String bonus30(Context c){
        long now = System.currentTimeMillis();
        if(shouldLock(c)){
            Prefs.setForceLockUntil(c, 0L);
            Prefs.setBypassUntil(c, now + 30 * 60_000L);
            LockOverlayService.sync(c);
            return "تم فتح التلفزيون 30 دقيقة ✔";
        }
        LocalDate d = tonightWindowDate(c);
        ZonedDateTime s = startFor(c, d).atZone(ZoneOffset.ofHours(3));
        long hoursAway = (s.toInstant().toEpochMilli() - now) / 3_600_000L;
        if(!Prefs.enabled(c) || hoursAway > 14 || hoursAway < 0) return "لا يوجد قفل قريب لتأجيله";
        LocalTime t = s.toLocalTime().plusMinutes(30);
        setTonightStart(c, t.getHour(), t.getMinute());
        return String.format(java.util.Locale.ROOT, "تم تأجيل القفل 30 دقيقة (يبدأ %02d:%02d) ✔", t.getHour(), t.getMinute());
    }

    public static long currentWindowEnd(Context c){
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.ofHours(3));
        LocalDate today = now.toLocalDate();
        long best = 0L;
        if(Prefs.enabled(c)){
            for(int back=0; back<=1; back++){
                LocalDate d = today.minusDays(back);
                if(!Prefs.dayEnabled(c, d.getDayOfWeek().getValue())) continue;
                ZonedDateTime s = startFor(c,d).atZone(now.getZone());
                ZonedDateTime e = endFor(c,d).atZone(now.getZone());
                if(!now.isBefore(s) && now.isBefore(e)) best = Math.max(best, e.toInstant().toEpochMilli());
            }
        }
        ZonedDateTime xe = extraEndNow(c, now);
        if(xe != null) best = Math.max(best, xe.toInstant().toEpochMilli());
        return best > 0 ? best : now.plusMinutes(1).toInstant().toEpochMilli();
    }

    public static void reschedule(Context c){
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am == null) return;
        cancel(am,c,1001,"START");
        cancel(am,c,1002,"END");
        cancel(am,c,1003,"WARN");
        cancel(am,c,1004,"REPORT");
        set(am,c,1004,"REPORT",nextReport());
        if(!Prefs.enabled(c) && !Prefs.extraOn(c)) return;
        set(am,c,1001,"START",nextTransition(c,true));
        set(am,c,1002,"END",nextTransition(c,false));
        if(Prefs.enabled(c)){
            long warn = nextWarn(c);
            if(warn > 0) set(am,c,1003,"WARN",warn);
        }
    }

    /** Next 21:00 (Amman) for the daily report. */
    private static long nextReport(){
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.ofHours(3));
        ZonedDateTime t = now.toLocalDate().atTime(21, 0).atZone(now.getZone());
        if(!t.isAfter(now.plusSeconds(1))) t = t.plusDays(1);
        return t.toInstant().toEpochMilli();
    }

    private static long nextWarn(Context c){
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.ofHours(3));
        long nowMs = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        for(int i=0; i<9; i++){
            LocalDate d = now.toLocalDate().plusDays(i);
            if(!Prefs.dayEnabled(c, d.getDayOfWeek().getValue())) continue;
            long ms = startFor(c,d).atZone(now.getZone()).toInstant().toEpochMilli() - WARN_MS;
            if(ms > nowMs + 1000 && ms < best) best = ms;
        }
        return best == Long.MAX_VALUE ? -1L : best;
    }

    private static long nextTransition(Context c, boolean start){
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.ofHours(3));
        long nowMs = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        int first = start ? 0 : -1;
        for(int i=first; i<9; i++){
            LocalDate d = now.toLocalDate().plusDays(i);
            if(Prefs.enabled(c) && Prefs.dayEnabled(c, d.getDayOfWeek().getValue())){
                ZonedDateTime z = (start ? startFor(c,d) : endFor(c,d)).atZone(now.getZone());
                long ms = z.toInstant().toEpochMilli();
                if(ms > nowMs + 1000 && ms < best) best = ms;
            }
            if(Prefs.extraOn(c)){
                ZonedDateTime z = (start ? extraStart(c,d) : extraEnd(c,d)).atZone(now.getZone());
                long ms = z.toInstant().toEpochMilli();
                if(ms > nowMs + 1000 && ms < best) best = ms;
            }
        }
        return best == Long.MAX_VALUE ? nowMs + 24L*60L*60L*1000L : best;
    }

    private static PendingIntent pi(Context c,int req,String action){
        Intent i = new Intent(c, AlarmReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c,req,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancel(AlarmManager am,Context c,int req,String action){ am.cancel(pi(c,req,action)); }

    private static void set(AlarmManager am,Context c,int req,String action,long when){
        PendingIntent pi = pi(c,req,action);
        try {
            if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
        } catch(Exception e){
            am.set(AlarmManager.RTC_WAKEUP,when,pi);
        }
    }
}
