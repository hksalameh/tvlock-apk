package com.smartcodejo.tvlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent){
        if(intent!=null && "WARN".equals(intent.getAction())) WarnBanner.show(context);
        PhoneUnlockServer.ensureStarted(context);
        LockOverlayService.sync(context);
        ScheduleUtil.reschedule(context);
    }
}
