package com.smartcodejo.tvlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent){
        LockOverlayService.sync(context);
        ScheduleUtil.reschedule(context);
    }
}
