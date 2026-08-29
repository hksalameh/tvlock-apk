package com.smartcodejo.tvlock;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class GuardAccessibilityService extends AccessibilityService {
    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        LockOverlayService.sync(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(ScheduleUtil.shouldLock(this)) LockOverlayService.sync(this);
    }

    @Override protected boolean onKeyEvent(KeyEvent event){
        if(!ScheduleUtil.shouldLock(this) || event.getAction()!=KeyEvent.ACTION_DOWN) return false;
        int k=event.getKeyCode();
        return k==KeyEvent.KEYCODE_HOME || k==KeyEvent.KEYCODE_APP_SWITCH || k==KeyEvent.KEYCODE_MENU || k==KeyEvent.KEYCODE_SETTINGS;
    }

    @Override public void onInterrupt(){}
}
