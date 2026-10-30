package com.smartcodejo.tvlock;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.security.SecureRandom;

/**
 * ADB-shell-only one-time initial setup and read-only schedule diagnostics.
 * The manifest requires android.permission.DUMP for callers, which ordinary
 * Android apps and children using the remote do not hold.
 */
public final class ProvisionReceiver extends BroadcastReceiver {
    private static final String SETUP = "com.smartcodejo.tvlock.ADB_PROVISION";
    private static final String STATUS = "com.smartcodejo.tvlock.ADB_STATUS";
    private static final String MIDNIGHT = "com.smartcodejo.tvlock.ADB_SET_MIDNIGHT";

    private void result(String message) {
        setResultData(message);
    }

    @Override public void onReceive(Context c, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (STATUS.equals(action)) {
            String schedule = String.format(java.util.Locale.ROOT,
                "enabled=%s,start=%02d:%02d,end=%02d:%02d,pin_set=%s",
                Prefs.enabled(c), Prefs.startHour(c), Prefs.startMinute(c),
                Prefs.endHour(c), Prefs.endMinute(c), !Prefs.pin(c).isEmpty());
            DevicePolicyManager dpm =
                (DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);
            boolean admin = dpm != null && dpm.isAdminActive(
                new ComponentName(c, TvDeviceAdminReceiver.class));
            result(schedule + ",admin=" + admin);
            return;
        }
        if (MIDNIGHT.equals(action)) {
            // Privileged ADB shell only (manifest requires android.permission.DUMP).
            // Change just the start time; preserve PIN, end time, enabled state and weekdays.
            if (Prefs.pin(c).isEmpty() || !Prefs.enabled(c)) {
                result("HOLD: TV Lock not configured or disabled; settings unchanged");
                return;
            }
            Prefs.setStart(c, 0, 0);
            ScheduleUtil.reschedule(c);
            LockOverlayService.sync(c);
            result(String.format(java.util.Locale.ROOT,
                "PASS: start=%02d:%02d,end=%02d:%02d,enabled=%s,pin_set=%s",
                Prefs.startHour(c), Prefs.startMinute(c),
                Prefs.endHour(c), Prefs.endMinute(c),
                Prefs.enabled(c), !Prefs.pin(c).isEmpty()));
            return;
        }
        if (!SETUP.equals(action)) {
            result("BLOCK: unsupported operation");
            return;
        }
        String pin = intent.getStringExtra("pin");
        if (pin == null || !pin.matches("[0-9]{6,8}")) {
            result("BLOCK: a 6-8 digit PIN is required");
            return;
        }
        // A repeat setup with the same PIN is safe; never reset an unknown PIN.
        if (!Prefs.pin(c).isEmpty() && !Prefs.pin(c).equals(pin)) {
            result("HOLD: a different PIN is already configured. Existing settings unchanged");
            return;
        }
        Prefs.setPin(c, pin);
        Prefs.setStart(c, 0, 0);
        Prefs.setEnd(c, 8, 0);
        for (int day=1; day<=7; day++) Prefs.setDayEnabled(c, day, true);
        Prefs.setBypassUntil(c, 0L);
        Prefs.setEnabled(c, true);
        ScheduleUtil.reschedule(c);
        LockOverlayService.sync(c);
        result("PASS: schedule 00:00-08:00 set, PIN set, all 7 days enabled");
    }
}
