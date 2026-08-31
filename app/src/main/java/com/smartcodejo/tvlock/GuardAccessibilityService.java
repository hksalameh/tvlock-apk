package com.smartcodejo.tvlock;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Locale;

public class GuardAccessibilityService extends AccessibilityService {
    private long lastGuardLaunch=0L;

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        LockOverlayService.sync(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(ScheduleUtil.shouldLock(this)) LockOverlayService.sync(this);
        protectSettings(event);
    }

    private void protectSettings(AccessibilityEvent event){
        if(event==null || Prefs.pin(this).isEmpty() || Prefs.adminBypassActive(this)) return;

        CharSequence pkgCs=event.getPackageName();
        String pkg=pkgCs==null?"":pkgCs.toString().toLowerCase(Locale.ROOT);
        String cls=event.getClassName()==null?"":event.getClassName().toString().toLowerCase(Locale.ROOT);

        boolean settings = pkg.equals("com.android.tv.settings") ||
                pkg.equals("com.android.settings") ||
                pkg.contains("android.tv.settings") ||
                pkg.contains("tvsettings");
        boolean installer = pkg.contains("packageinstaller") || cls.contains("uninstall");

        if(!settings && !installer) return;

        long now=SystemClock.elapsedRealtime();
        if(now-lastGuardLaunch<900) return;
        lastGuardLaunch=now;

        Intent i=new Intent(this,AdminGuardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(i); } catch(Exception ignored) {}
    }

    @Override protected boolean onKeyEvent(KeyEvent event){
        if(!ScheduleUtil.shouldLock(this) || event.getAction()!=KeyEvent.ACTION_DOWN) return false;
        int k=event.getKeyCode();
        return k==KeyEvent.KEYCODE_HOME || k==KeyEvent.KEYCODE_APP_SWITCH || k==KeyEvent.KEYCODE_MENU || k==KeyEvent.KEYCODE_SETTINGS;
    }

    @Override public void onInterrupt(){}
}
