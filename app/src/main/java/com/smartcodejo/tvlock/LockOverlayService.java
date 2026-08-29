package com.smartcodejo.tvlock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LockOverlayService extends Service {
    private static final String CHANNEL="tv_lock_guard";
    private static final int NOTIF_ID=22;
    private WindowManager wm;
    private View overlay;
    private TextView clock;
    private TextView pinDots;
    private TextView hint;
    private final StringBuilder entered=new StringBuilder();
    private boolean forced;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            updateClock();
            if(!forced && !ScheduleUtil.shouldLock(LockOverlayService.this)) stopSelf();
            else handler.postDelayed(this,1000);
        }
    };

    public static void sync(Context c){
        if(ScheduleUtil.shouldLock(c) && Settings.canDrawOverlays(c)){
            Intent i=new Intent(c,LockOverlayService.class);
            try {
                if(Build.VERSION.SDK_INT>=26) c.startForegroundService(i); else c.startService(i);
            } catch(Exception ignored){}
        } else {
            try { c.stopService(new Intent(c,LockOverlayService.class)); } catch(Exception ignored){}
        }
    }

    @Override public void onCreate(){
        super.onCreate();
        createChannel();
        startForeground(NOTIF_ID,notification());
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && intent.getBooleanExtra("force",false)) forced=true;
        if(!Settings.canDrawOverlays(this)){
            Toast.makeText(this,"يجب تفعيل صلاحية الظهور فوق التطبيقات",Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        if(forced || ScheduleUtil.shouldLock(this)) showOverlay(); else stopSelf();
        return START_STICKY;
    }

    private void showOverlay(){
        if(overlay!=null) return;
        LinearLayout main=new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setGravity(Gravity.CENTER);
        main.setPadding(dp(60),dp(30),dp(60),dp(30));
        main.setBackgroundColor(Color.rgb(7,12,22));
        main.setFocusableInTouchMode(true);
        main.setOnKeyListener((v,key,event)->event.getAction()==KeyEvent.ACTION_DOWN && key==KeyEvent.KEYCODE_BACK);

        TextView lockIcon=tv("🔒",52,Color.WHITE); lockIcon.setGravity(Gravity.CENTER); main.addView(lockIcon);
        clock=tv("",58,Color.WHITE); clock.setTypeface(Typeface.DEFAULT,Typeface.BOLD); clock.setGravity(Gravity.CENTER); main.addView(clock);
        TextView msg=tv(Prefs.message(this),30,Color.rgb(244,247,251)); msg.setGravity(Gravity.CENTER); msg.setTypeface(Typeface.DEFAULT,Typeface.BOLD); msg.setPadding(0,dp(10),0,dp(6)); main.addView(msg);
        TextView until=tv("سيفتح التلفزيون تلقائيًا الساعة "+formatEnd(),20,Color.rgb(159,176,199)); until.setGravity(Gravity.CENTER); until.setPadding(0,0,0,dp(20)); main.addView(until);
        pinDots=tv("○ ○ ○ ○",30,Color.rgb(53,194,163)); pinDots.setGravity(Gravity.CENTER); pinDots.setPadding(0,0,0,dp(8)); main.addView(pinDots);
        hint=tv("أدخل PIN للفتح قبل الموعد",17,Color.rgb(159,176,199)); hint.setGravity(Gravity.CENTER); hint.setPadding(0,0,0,dp(14)); main.addView(hint);

        GridLayout grid=new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(4);
        String[] labels={"1","2","3","4","5","6","7","8","9","مسح","0","فتح"};
        Button first=null;
        for(String label:labels){
            Button b=new Button(this);
            if(first==null) first=b;
            b.setText(label); b.setTextSize(21); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setFocusable(true); b.setBackgroundResource(R.drawable.focus_button);
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=dp(150); lp.height=dp(64); lp.setMargins(dp(5),dp(5),dp(5),dp(5)); b.setLayoutParams(lp);
            if(label.matches("\\d")) b.setOnClickListener(v->digit(label));
            else if(label.equals("مسح")) b.setOnClickListener(v->backspace());
            else b.setOnClickListener(v->tryUnlock());
            grid.addView(b);
        }
        main.addView(grid);
        TextView footer=tv("TV Lock • الحماية تعمل تلقائيًا حسب الجدول",14,Color.rgb(95,111,135)); footer.setGravity(Gravity.CENTER); footer.setPadding(0,dp(18),0,0); main.addView(footer);

        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,type,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.OPAQUE);
        p.gravity=Gravity.TOP|Gravity.START;
        overlay=main;
        wm.addView(overlay,p);
        if(first!=null) first.requestFocus(); else main.requestFocus();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void digit(String d){ if(entered.length()<8){ entered.append(d); updateDots(); } }
    private void backspace(){ if(entered.length()>0){ entered.deleteCharAt(entered.length()-1); updateDots(); } }
    private void updateDots(){
        StringBuilder s=new StringBuilder(); int n=Math.max(4,entered.length());
        for(int i=0;i<n;i++){ if(i>0)s.append(" "); s.append(i<entered.length()?"●":"○"); }
        pinDots.setText(s.toString());
    }

    private void tryUnlock(){
        if(Prefs.pin(this).equals(entered.toString())){
            if(!forced && ScheduleUtil.shouldLock(this)) Prefs.setBypassUntil(this,ScheduleUtil.currentWindowEnd(this));
            entered.setLength(0); forced=false; stopSelf();
        } else {
            entered.setLength(0); updateDots(); hint.setText("PIN غير صحيح — حاول مرة أخرى"); hint.setTextColor(Color.rgb(230,106,106));
        }
    }

    private void updateClock(){ if(clock!=null) clock.setText(new SimpleDateFormat("h:mm a",new Locale("ar")).format(new Date())); }
    private String formatEnd(){ int h=Prefs.endHour(this),m=Prefs.endMinute(this),hh=h%12; if(hh==0) hh=12; return String.format(new Locale("ar"),"%d:%02d %s",hh,m,h<12?"ص":"م"); }
    private TextView tv(String s,float size,int color){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t; }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(CHANNEL,"TV Lock",NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps scheduled TV lock active");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification notification(){
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setContentTitle("TV Lock").setContentText("الحماية والجدول يعملان").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build();
    }

    @Override public void onDestroy(){
        handler.removeCallbacks(ticker);
        if(overlay!=null && wm!=null){ try{ wm.removeView(overlay); }catch(Exception ignored){} overlay=null; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i){ return null; }
}
