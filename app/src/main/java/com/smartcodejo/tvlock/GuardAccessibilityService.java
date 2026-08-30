package com.smartcodejo.tvlock;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;

public class GuardAccessibilityService extends AccessibilityService {
    private long lastGuardLaunch=0L;

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        LockOverlayService.sync(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(ScheduleUtil.shouldLock(this)) LockOverlayService.sync(this);
        protectUninstall(event);
    }

    private void protectUninstall(AccessibilityEvent event){
        if(event==null || Prefs.pin(this).isEmpty() || Prefs.adminBypassActive(this)) return;

        CharSequence pkgCs=event.getPackageName();
        String pkg=pkgCs==null?"":pkgCs.toString().toLowerCase(Locale.ROOT);
        String cls=event.getClassName()==null?"":event.getClassName().toString().toLowerCase(Locale.ROOT);
        String text=collectText(event).toLowerCase(Locale.ROOT);

        boolean target=text.contains("tv lock") || text.contains("com.smartcodejo.tvlock");
        boolean uninstallWord=text.contains("uninstall") || text.contains("إلغاء التثبيت") || text.contains("حذف التطبيق") || text.contains("حذف");
        boolean installer=pkg.contains("packageinstaller") || cls.contains("uninstall");
        boolean settings=pkg.contains("android.tv.settings") || pkg.contains("com.android.settings") || pkg.contains("settings");

        if(!target || !(installer || (settings && uninstallWord))) return;

        long now=SystemClock.elapsedRealtime();
        if(now-lastGuardLaunch<1200) return;
        lastGuardLaunch=now;

        Intent i=new Intent(this,MainActivity.class);
        i.putExtra("uninstall_guard",true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(i); } catch(Exception ignored) {}
    }

    private String collectText(AccessibilityEvent event){
        StringBuilder b=new StringBuilder();
        for(CharSequence s:event.getText()) if(s!=null) b.append(' ').append(s);
        if(event.getContentDescription()!=null) b.append(' ').append(event.getContentDescription());
        AccessibilityNodeInfo root=event.getSource();
        appendNodeText(root,b,0);
        if(root!=null) root.recycle();
        return b.toString();
    }

    private void appendNodeText(AccessibilityNodeInfo n,StringBuilder b,int depth){
        if(n==null || depth>5 || b.length()>3000) return;
        if(n.getText()!=null) b.append(' ').append(n.getText());
        if(n.getContentDescription()!=null) b.append(' ').append(n.getContentDescription());
        int count=Math.min(n.getChildCount(),20);
        for(int i=0;i<count;i++){
            AccessibilityNodeInfo c=n.getChild(i);
            appendNodeText(c,b,depth+1);
            if(c!=null) c.recycle();
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent event){
        if(!ScheduleUtil.shouldLock(this) || event.getAction()!=KeyEvent.ACTION_DOWN) return false;
        int k=event.getKeyCode();
        return k==KeyEvent.KEYCODE_HOME || k==KeyEvent.KEYCODE_APP_SWITCH || k==KeyEvent.KEYCODE_MENU || k==KeyEvent.KEYCODE_SETTINGS;
    }

    @Override public void onInterrupt(){}
}
