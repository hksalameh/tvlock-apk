package com.smartcodejo.tvlock;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;

import java.util.Locale;

public class GuardAccessibilityService extends AccessibilityService {
    static GuardAccessibilityService instance;

    /** Puts the TV to sleep (screen off / standby). Tries Device Admin lockNow, then the accessibility lock action. */
    static boolean sleepTv(android.content.Context c){
        try{
            android.app.admin.DevicePolicyManager dpm=(android.app.admin.DevicePolicyManager)c.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE);
            android.content.ComponentName admin=new android.content.ComponentName(c,TvDeviceAdminReceiver.class);
            if(dpm!=null && dpm.isAdminActive(admin)){ dpm.lockNow(); return true; }
        }catch(Exception ignored){}
        try{
            GuardAccessibilityService s=instance;
            if(s!=null && android.os.Build.VERSION.SDK_INT>=28) return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }catch(Exception ignored){}
        return false;
    }

    private final Handler handler=new Handler(Looper.getMainLooper());
    private long lastAppBlock=0L;
    private BroadcastReceiver screenRx;

    private final Runnable tick=new Runnable(){ @Override public void run(){
        try{ checkSession(); }catch(Exception ignored){}
        try{ UpdateChecker.maybeCheck(GuardAccessibilityService.this); }catch(Exception ignored){}
        handler.postDelayed(this,30_000L);
    }};

    /** Continuous screen-on limit: force a short lock break. */
    private void checkSession(){
        if(!Prefs.sessionOn(this)) return;
        PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
        if(pm==null || !pm.isInteractive()) return;
        if(ScheduleUtil.shouldLock(this)) return;
        long limit=Prefs.sessionLimitMin(this)*60_000L;
        if(ScreenTracker.sessionMs(this)>=limit){
            int br=Prefs.sessionBreakMin(this);
            EventLog.alert(this,"⏸ استراحة إجبارية "+br+" دقيقة بعد "+Prefs.sessionLimitMin(this)+" دقيقة تشغيل متواصل");
            ScheduleUtil.lockFor(this,br);
            ScreenTracker.restartSessionAt(this,System.currentTimeMillis()+br*60_000L);
        }
    }

    private boolean appBlockedNow(String pkg){
        if(!Prefs.appsOn(this) || pkg.isEmpty() || pkg.equals(getPackageName())) return false;
        if(!Prefs.appsBlocked(this).contains(pkg)) return false;
        java.time.ZonedDateTime n=java.time.ZonedDateTime.now(java.time.ZoneOffset.ofHours(3));
        int cur=n.getHour()*60+n.getMinute();
        int s=Prefs.appsStartHour(this)*60+Prefs.appsStartMinute(this);
        int e=Prefs.appsEndHour(this)*60+Prefs.appsEndMinute(this);
        return s<e ? (cur>=s && cur<e) : (cur>=s || cur<e);
    }

    private long lastGuardLaunch=0L;
    private long lastScan=0L;
    private boolean uninstallDialogVisible=false;

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        instance=this;
        EventLog.add(this,"Accessibility شغّالة");
        PhoneUnlockServer.ensureStarted(this);
        LockOverlayService.sync(this);
        ScheduleUtil.reschedule(this);
        PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
        ScreenTracker.onScreen(this, pm==null || pm.isInteractive());
        screenRx=new BroadcastReceiver(){ @Override public void onReceive(Context c, Intent i){
            ScreenTracker.onScreen(c, Intent.ACTION_SCREEN_ON.equals(i.getAction()));
        }};
        IntentFilter fl=new IntentFilter(); fl.addAction(Intent.ACTION_SCREEN_ON); fl.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenRx, fl);
        handler.removeCallbacks(tick); handler.postDelayed(tick,30_000L);
    }

    @Override public void onDestroy(){
        if(instance==this) instance=null;
        handler.removeCallbacks(tick);
        try{ if(screenRx!=null) unregisterReceiver(screenRx); }catch(Exception ignored){}
        EventLog.alert(this,"⚠ Accessibility انطفأت");
        PhoneUnlockServer.stopInstance();
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(ScheduleUtil.shouldLock(this)) LockOverlayService.sync(this);
        if(event==null) return;
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName()!=null){
            String bp=event.getPackageName().toString();
            long t0=SystemClock.elapsedRealtime();
            if(appBlockedNow(bp) && t0-lastAppBlock>1500){
                lastAppBlock=t0;
                EventLog.add(this,"تطبيق محجوب: "+bp);
                performGlobalAction(GLOBAL_ACTION_HOME);
                Toast.makeText(this,"هذا التطبيق مقفول في هذا الوقت",Toast.LENGTH_LONG).show();
                return;
            }
        }
        if(!Prefs.hasPin(this)) return;

        if(Prefs.adminBypassActive(this)){
            uninstallDialogVisible=false;
            return;
        }

        String pkg=event.getPackageName()==null?"":event.getPackageName().toString().toLowerCase(Locale.ROOT);
        String cls=event.getClassName()==null?"":event.getClassName().toString().toLowerCase(Locale.ROOT);
        // Other screens that can start an uninstall (Play Store, permission controller, TV launchers).
        boolean otherUninstallSurface = pkg.contains("permissioncontroller") || pkg.contains("vending") ||
                pkg.contains("launcher") || pkg.contains("tvrecommendations") || pkg.contains("appstore");
        boolean settingsOrInstaller = pkg.contains("settings") || pkg.contains("packageinstaller") ||
                cls.contains("settings") || cls.contains("uninstall") || otherUninstallSurface;
        // Android TV firmware may lack Device Admin UI. Protect system settings and
        // package installer behind the parent's PIN using the bound accessibility service.
        boolean blanket = settingsOrInstaller && !otherUninstallSurface;
        if (blanket &&
                (event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                 event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED)) {
            uninstallDialogVisible=false;
            launchGuard(false);
            return;
        }
        if(!settingsOrInstaller){
            uninstallDialogVisible=false;
            return;
        }

        if(otherUninstallSurface && event.getEventType()!=AccessibilityEvent.TYPE_VIEW_CLICKED){
            long t=SystemClock.elapsedRealtime();
            if(t-lastScan<400) return;
            lastScan=t;
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
                launchGuard(true);
            }
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent event){
        if(event.getAction()!=KeyEvent.ACTION_DOWN) return false;

        if(uninstallDialogVisible && !Prefs.adminBypassActive(this)){
            int k=event.getKeyCode();
            if(k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER || k==KeyEvent.KEYCODE_NUMPAD_ENTER){
                performGlobalAction(GLOBAL_ACTION_BACK);
                launchGuard(true);
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

    private void launchGuard(boolean uninstallFlow){
        long now=SystemClock.elapsedRealtime();
        if(now-lastGuardLaunch<700) return;
        lastGuardLaunch=now;
        if(uninstallFlow) EventLog.alert(this,"🚨 محاولة حذف التطبيق");
        else EventLog.add(this,"محاولة فتح الإعدادات/المثبّت");
        Intent i=new Intent(this,AdminGuardActivity.class);
        i.putExtra("uninstall_flow",uninstallFlow);
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
