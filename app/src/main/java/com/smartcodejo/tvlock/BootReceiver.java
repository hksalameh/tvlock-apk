package com.smartcodejo.tvlock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    public static final String ACTION_RECHECK = "com.smartcodejo.tvlock.ACTION_BOOT_RECHECK";

    @Override public void onReceive(Context context, Intent intent){
        String action = intent == null ? "" : intent.getAction();

        // Always rebuild the normal schedule first. A full power cut clears
        // in-memory alarms, so they must be recreated after Android starts.
        ScheduleUtil.reschedule(context);

        // Try immediately. If the TV framework/overlay manager is not fully
        // ready yet, the retry alarms below will enforce the lock shortly after.
        LockOverlayService.sync(context);

        if (!ACTION_RECHECK.equals(action)) {
            scheduleRecheck(context, 4, 1);
            scheduleRecheck(context, 12, 2);
            scheduleRecheck(context, 30, 3);
            scheduleRecheck(context, 60, 4);
        }
    }

    private void scheduleRecheck(Context context, int seconds, int requestCode){
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(context, BootReceiver.class).setAction(ACTION_RECHECK);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                7000 + requestCode,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long when = System.currentTimeMillis() + seconds * 1000L;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }
}
