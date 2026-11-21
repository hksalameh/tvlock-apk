package com.smartcodejo.tvlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { c.startActivity(confirm); } catch (Exception ignored) {}
            }
        } else if (st != PackageInstaller.STATUS_SUCCESS) {
            EventLog.add(c, "فشل تثبيت التحديث (رمز " + st + "): " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        }
    }
}
