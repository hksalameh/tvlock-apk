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
    private boolean uninstallDialogVisible=false;

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        LockOverlayService.sync(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(ScheduleUtil.shouldLock(this)) LockOverlayService.sync(this);
        if(event==null || Prefs.pin(this).isEmpty()) return;

        if(Prefs.adminBypassActive(this)){
            uninstallDialogVisible=false;
            return;
        }

        String pkg=event.getPackageName()==null?"":event.getPackageName().toString().toLowerCase(Locale.ROOT);
        String cls=event.getClassName()==null?"":event.getClassName().toString().toLowerCase(Locale.ROOT);
        boolean settingsOrInstaller = pkg.contains("settings") || pkg.contains("packageinstaller") || cls.contains("uninstall");
        if(!settingsOrInstaller){
            uninstallDialogVisible=false;
            return;
        }

        String screen=collectActiveWindowText().toLowerCase(Locale.ROOT);
        boolean target=screen.contains("tv lock") || screen.contains("com.smartcodejo.tvlock");
        boolean uninstall=screen.contains("uninstall") ||
                screen.contains("remove this app") ||
                screen.contains("removed from all users") ||
                screen.contains("إلغاء التثبيت") ||
                screen.contains("حذف التطبيق") ||
                screen.contains("حذف");

        uninstallDialogVisible=target && uninstall;

        if(uninstallDialogVisible && event.getEventType()==AccessibilityEvent.TYPE_VIEW_CLICKED){
            String clicked="";
            AccessibilityNodeInfo src=event.getSource();
            if(src!=null){
                if(src.getText()!=null) clicked+=src.getText().toString();
                if(src.getContentDescription()!=null) clicked+=" "+src.getContentDescription().toString();
                src.recycle();
            }
            clicked=clicked.trim().toLowerCase(Locale.ROOT);
            if(clicked.equals("ok") || clicked.contains("uninstall") || clicked.contains("حذف") || clicked.contains("إلغاء التثبيت")){
                performGlobalAction(GLOBAL_ACTION_BACK);
                launchGuard();
            }
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent event){
        if(event.getAction()!=KeyEvent.ACTION_DOWN) return false;

        if(uninstallDialogVisible && !Prefs.adminBypassActive(this)){
            int k=event.getKeyCode();
            if(k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER || k==KeyEvent.KEYCODE_NUMPAD_ENTER){
                launchGuard();
                return true;
            }
            if(k==KeyEvent.KEYCODE_BACK){
                uninstallDialogVisible=false;
                return false;
            }
        }

        if(!ScheduleUtil.shouldLock(this)) return false;
        int k=event.getKeyCode();
        return k==KeyEvent.KEYCODE_HOME || k==KeyEvent.KEYCODE_APP_SWITCH || k==KeyEvent.KEYCODE_MENU || k==KeyEvent.KEYCODE_SETTINGS;
    }

    private void launchGuard(){
        long now=SystemClock.elapsedRealtime();
        if(now-lastGuardLaunch<700) return;
        lastGuardLaunch=now;
        Intent i=new Intent(this,AdminGuardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(i); } catch(Exception ignored) {}
    }

    private String collectActiveWindowText(){
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) return "";
        StringBuilder b=new StringBuilder();
        appendNodeText(root,b,0);
        root.recycle();
        return b.toString();
    }

    private void appendNodeText(AccessibilityNodeInfo n,StringBuilder b,int depth){
        if(n==null || depth>8 || b.length()>6000) return;
        if(n.getText()!=null) b.append(' ').append(n.getText());
        if(n.getContentDescription()!=null) b.append(' ').append(n.getContentDescription());
        int count=Math.min(n.getChildCount(),40);
        for(int i=0;i<count;i++){
            AccessibilityNodeInfo c=n.getChild(i);
            appendNodeText(c,b,depth+1);
            if(c!=null) c.recycle();
        }
    }

    @Override public void onInterrupt(){}
}
