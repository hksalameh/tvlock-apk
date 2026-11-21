package com.smartcodejo.tvlock;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class TvDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override public void onEnabled(Context context, Intent intent){
        EventLog.add(context, "حماية الحذف (Device Admin) فُعّلت");
    }
    @Override public void onDisabled(Context context, Intent intent){
        EventLog.alert(context, "⚠ حماية الحذف (Device Admin) أُلغيت");
    }
}
