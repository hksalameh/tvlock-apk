package com.smartcodejo.tvlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent){
        String act = intent==null ? null : intent.getAction();
        if("WARN".equals(act)) WarnBanner.show(context);
        if("REPORT".equals(act)){
            String r = "📊 تقرير اليوم: تم تشغيل التلفزيون " + ScreenTracker.format(ScreenTracker.todayMs(context));
            EventLog.add(context, r);
            Notifier.send(context, r, true);
        }
        PhoneUnlockServer.ensureStarted(context);
        LockOverlayService.sync(context);
        ScheduleUtil.reschedule(context);
    }
}
