package com.smartcodejo.tvlock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public final class ScheduleUtil {
    private static LocalDateTime startFor(Context c, LocalDate d){
        return d.atTime(Prefs.startHour(c), Prefs.startMinute(c));
    }

    private static LocalDateTime endFor(Context c, LocalDate d){
        LocalDateTime s = startFor(c, d);
        LocalDateTime e = d.atTime(Prefs.endHour(c), Prefs.endMinute(c));
        if(!e.isAfter(s)) e = e.plusDays(1);
        return e;
    }

    public static boolean shouldLock(Context c){
        if(!Prefs.enabled(c)) return false;
        long nowMs = System.currentTimeMillis();
        if(Prefs.bypassUntil(c) > nowMs) return false;
        ZonedDateTime now = ZonedDateTime.now();
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

    public static long currentWindowEnd(Context c){
        ZonedDateTime now = ZonedDateTime.now();
        LocalDate today = now.toLocalDate();
        for(int back=0; back<=1; back++){
            LocalDate d = today.minusDays(back);
            if(!Prefs.dayEnabled(c, d.getDayOfWeek().getValue())) continue;
            ZonedDateTime s = startFor(c,d).atZone(now.getZone());
            ZonedDateTime e = endFor(c,d).atZone(now.getZone());
            if(!now.isBefore(s) && now.isBefore(e)) return e.toInstant().toEpochMilli();
        }
        return now.plusMinutes(1).toInstant().toEpochMilli();
    }

    public static void reschedule(Context c){
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am == null) return;
        cancel(am,c,1001,"START");
        cancel(am,c,1002,"END");
        if(!Prefs.enabled(c)) return;
        set(am,c,1001,"START",nextTransition(c,true));
        set(am,c,1002,"END",nextTransition(c,false));
    }

    private static long nextTransition(Context c, boolean start){
        ZonedDateTime now = ZonedDateTime.now();
        long nowMs = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        int first = start ? 0 : -1;
        for(int i=first; i<9; i++){
            LocalDate d = now.toLocalDate().plusDays(i);
            if(!Prefs.dayEnabled(c, d.getDayOfWeek().getValue())) continue;
            ZonedDateTime z = (start ? startFor(c,d) : endFor(c,d)).atZone(now.getZone());
            long ms = z.toInstant().toEpochMilli();
            if(ms > nowMs + 1000 && ms < best) best = ms;
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
