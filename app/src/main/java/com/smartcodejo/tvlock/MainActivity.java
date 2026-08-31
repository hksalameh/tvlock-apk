package com.smartcodejo.tvlock;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView status;
    private boolean authenticated=false;
    private boolean inSettings=false;

    private final int BLUE=Color.rgb(48,157,255);
    private final int CYAN=Color.rgb(83,210,255);
    private final int TEXT=Color.rgb(244,248,255);
    private final int MUTED=Color.rgb(173,201,232);

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        if(Prefs.pin(this).isEmpty()) showFirstSetup(); else showPinGate();
    }

    @Override protected void onResume(){
        super.onResume();
        LockOverlayService.sync(this);
        if(authenticated){
            if(inSettings) buildSettings(); else buildDashboard();
        }
    }

    private void baseRoot(){
        FrameLayout frame=new FrameLayout(this);
        frame.addView(new LockBackgroundView(this),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        View shade=new View(this);
        shade.setBackgroundColor(Color.argb(62,0,8,25));
        frame.addView(shade,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int sx=Math.max(dp(24),(int)(getResources().getDisplayMetrics().widthPixels*0.045f));
        root.setPadding(sx,dp(16),sx,dp(22));
        scroll.addView(root,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        frame.addView(scroll,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(frame);
    }

    private void brandHeader(String heading,String sub){
        TextView badge=text("HKS",15,Color.WHITE);
        badge.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundBg(Color.argb(170,12,75,148),CYAN,14));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(72),dp(32));
        bp.setMargins(0,0,0,dp(4));
        root.addView(badge,bp);

        TextView t=text(heading,25,TEXT);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        root.addView(t,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(36)));

        TextView s=text(sub,13,MUTED);
        s.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(26));
        sp.setMargins(0,0,0,dp(6));
        root.addView(s,sp);
    }

    private void showFirstSetup(){
        authenticated=false; inSettings=false; baseRoot();
        brandHeader("TV LOCK","إعداد الحماية لأول مرة");
        addCardTitle("أنشئ رمز الحماية");
        note("اختر PIN من 4 إلى 8 أرقام. ستحتاجه لفتح التلفزيون قبل الموعد أو تغيير الإعدادات.");
        MaskedPinEditText pin=edit("PIN جديد");
        pin.setSingleLine(true);
        pin.setImeOptions(EditorInfo.IME_ACTION_DONE);
        Button save=button("حفظ وبدء الحماية");
        View.OnClickListener action=v->{
            String x=pin.getText().toString().trim();
            if(!x.matches("\\d{4,8}")){ toast("الـ PIN يجب أن يكون من 4 إلى 8 أرقام"); return; }
            hideKeyboard(pin);
            Prefs.setPin(this,x);
            Prefs.setEnabled(this,true);
            ScheduleUtil.reschedule(this);
            authenticated=true;
            buildDashboard();
            requestDeviceAdmin();
        };
        save.setOnClickListener(action);
        pin.setOnEditorActionListener((v,id,event)->{
            if(id==EditorInfo.IME_ACTION_DONE){ action.onClick(pin); return true; }
            return false;
        });
        footer();
        pin.requestFocus();
    }

    private void showPinGate(){
        authenticated=false; inSettings=false; baseRoot();
        brandHeader("TV LOCK","لوحة التحكم محمية");
        addCardTitle("أدخل PIN للمتابعة");
        MaskedPinEditText pin=edit("PIN");
        pin.setSingleLine(true);
        pin.setImeOptions(EditorInfo.IME_ACTION_DONE);
        Button enter=button("دخول إلى لوحة التحكم");
        View.OnClickListener action=v->{
            if(Prefs.pin(this).equals(pin.getText().toString())){
                hideKeyboard(pin);
                authenticated=true;
                buildDashboard();
            } else {
                pin.setText("");
                toast("PIN غير صحيح");
            }
        };
        enter.setOnClickListener(action);
        pin.setOnEditorActionListener((v,id,event)->{
            if(id==EditorInfo.IME_ACTION_DONE){ action.onClick(pin); return true; }
            return false;
        });
        footer();
        pin.requestFocus();
    }

    private void buildDashboard(){
        authenticated=true; inSettings=false; hideKeyboard(null); baseRoot();
        brandHeader("TV LOCK","حماية ذكية للتلفزيون • hksalameh");

        LinearLayout statusCard=card();
        TextView stTitle=text(Prefs.enabled(this)?"الحماية مفعّلة":"الحماية متوقفة",19,
                Prefs.enabled(this)?Color.rgb(111,225,255):Color.rgb(255,178,120));
        stTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        stTitle.setGravity(Gravity.CENTER);
        statusCard.addView(stTitle,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(30)));
        status=text("",13,MUTED); status.setGravity(Gravity.CENTER);
        statusCard.addView(status,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(26)));
        refreshStatus();
        root.addView(statusCard,wideCardParams());

        LinearLayout times=new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER);
        times.addView(infoCard("بدء القفل",time(Prefs.startHour(this),Prefs.startMinute(this))),weightedCardParams());
        times.addView(infoCard("الفتح التلقائي",time(Prefs.endHour(this),Prefs.endMinute(this))),weightedCardParams());
        root.addView(times,new LinearLayout.LayoutParams(Math.min(dp(700),(int)(getResources().getDisplayMetrics().widthPixels*.58f)),dp(88)));

        section("التحكم السريع");
        Button lockNow=button("🔒  قفل الآن");
        lockNow.setOnClickListener(v->{
            Prefs.setBypassUntil(this,0);
            Intent i=new Intent(this,LockOverlayService.class).putExtra("force",true);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        });

        Button temp=button("⏱  فتح مؤقت");
        temp.setOnClickListener(v->showTempUnlockDialog());

        Button enable=button(Prefs.enabled(this)?"⏸  إيقاف الجدول":"▶  تشغيل الجدول");
        enable.setOnClickListener(v->{
            Prefs.setEnabled(this,!Prefs.enabled(this));
            Prefs.setBypassUntil(this,0);
            ScheduleUtil.reschedule(this);
            LockOverlayService.sync(this);
            buildDashboard();
        });

        Button settingsBtn=button("⚙  الإعدادات");
        settingsBtn.setOnClickListener(v->{ inSettings=true; buildSettings(); });

        if(!Settings.canDrawOverlays(this) || !isAccessibilityEnabled() || !isDeviceAdminActive()){
            TextView warning=note("⚠ بعض صلاحيات الحماية غير مفعّلة. افتح الإعدادات لإكمالها.");
            warning.setTextColor(Color.rgb(255,202,120));
        }
        footer();
    }

    private void buildSettings(){
        authenticated=true; inSettings=true; hideKeyboard(null); baseRoot();
        brandHeader("الإعدادات","إعدادات TV LOCK");

        Button back=button("← العودة");
        back.setOnClickListener(v->{ inSettings=false; buildDashboard(); });

        section("الجدول اليومي");
        Button start=button("بدء القفل:  "+time(Prefs.startHour(this),Prefs.startMinute(this)));
        start.setOnClickListener(v->showRemoteTimeDialog(true));
        Button end=button("الفتح التلقائي:  "+time(Prefs.endHour(this),Prefs.endMinute(this)));
        end.setOnClickListener(v->showRemoteTimeDialog(false));

        section("أيام العمل");
        String[] names={"الاثنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت","الأحد"};
        LinearLayout days=new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL); days.setGravity(Gravity.CENTER);
        for(int i=1;i<=7;i++){
            final int d=i;
            Button b=buttonSmall((Prefs.dayEnabled(this,d)?"✓ ":"")+names[i-1]);
            b.setOnClickListener(v->{
                Prefs.setDayEnabled(this,d,!Prefs.dayEnabled(this,d));
                ScheduleUtil.reschedule(this); buildSettings();
            });
            days.addView(b);
        }
        root.addView(days,new LinearLayout.LayoutParams(Math.min(dp(900),(int)(getResources().getDisplayMetrics().widthPixels*.74f)),dp(46)));

        section("الحماية والرسالة");
        Button pin=button("تغيير PIN"); pin.setOnClickListener(v->changePin());
        Button msg=button("رسالة شاشة القفل"); msg.setOnClickListener(v->changeMessage());

        section("الصلاحيات");
        Button overlay=button(Settings.canDrawOverlays(this)?"✓ الظهور فوق التطبيقات مفعّل":"تفعيل الظهور فوق التطبيقات");
        overlay.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))));
        Button acc=button(isAccessibilityEnabled()?"✓ Accessibility مفعّلة":"تفعيل Accessibility");
        acc.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Button admin=button(isDeviceAdminActive()?"✓ حماية الحذف مفعّلة":"تفعيل حماية الحذف");
        admin.setOnClickListener(v->{
            if(isDeviceAdminActive()) toast("حماية الحذف مفعّلة");
            else requestDeviceAdmin();
        });

        if(Build.VERSION.SDK_INT>=31){
            AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
            Button alarm=button(am.canScheduleExactAlarms()?"✓ التوقيت الدقيق مفعّل":"تفعيل التوقيت الدقيق");
            alarm.setOnClickListener(v->{
                try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))); }
                catch(Exception e){ toast("سيستخدم التطبيق أفضل توقيت متاح على هذه الشاشة."); }
            });
        }

        section("حالة الجدول");
        Button enable=button(Prefs.enabled(this)?"إيقاف الجدول":"تشغيل الجدول");
        enable.setOnClickListener(v->{
            Prefs.setEnabled(this,!Prefs.enabled(this));
            Prefs.setBypassUntil(this,0);
            ScheduleUtil.reschedule(this);
            LockOverlayService.sync(this);
            buildSettings();
        });

        section("إدارة التطبيق");
        TextView uninstallNote=note("لحذف TV Lock بشكل مقصود، استخدم هذا الزر وأدخل PIN الصحيح.");
        uninstallNote.setTextColor(Color.rgb(255,202,150));
        Button uninstall=button("🗑  حذف التطبيق");
        uninstall.setOnClickListener(v->{
            Intent i=new Intent(this,AdminGuardActivity.class);
            i.putExtra("uninstall",true);
            startActivity(i);
        });

        footer();
    }

    private void showTempUnlockDialog(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(8),dp(18),dp(6));
        TextView msg=text("اختر مدة الفتح المؤقت",16,Color.DKGRAY); msg.setGravity(Gravity.CENTER);
        box.addView(msg,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(40)));
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
        Button b15=dialogButton("15 دقيقة"), b30=dialogButton("30 دقيقة"), b60=dialogButton("60 دقيقة");
        row.addView(b15,dialogButtonParams()); row.addView(b30,dialogButtonParams()); row.addView(b60,dialogButtonParams());
        box.addView(row,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(60)));

        android.app.AlertDialog d=new android.app.AlertDialog.Builder(this)
                .setTitle("فتح مؤقت").setView(box).setNegativeButton("إلغاء",null).create();
        View.OnClickListener choose=v->{
            int min=v==b15?15:(v==b30?30:60);
            Prefs.setBypassUntil(this,System.currentTimeMillis()+min*60*1000L);
            LockOverlayService.sync(this); d.dismiss(); refreshStatus();
            toast("تم الفتح مؤقتًا لمدة "+min+" دقيقة");
        };
        b15.setOnClickListener(choose); b30.setOnClickListener(choose); b60.setOnClickListener(choose);
        d.setOnShowListener(x->b30.requestFocus()); d.show();
    }

    private void showRemoteTimeDialog(boolean startTime){
        final int[] value={
                startTime?Prefs.startHour(this):Prefs.endHour(this),
                startTime?Prefs.startMinute(this):Prefs.endMinute(this)
        };
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(18),dp(6),dp(18),dp(4));

        TextView display=text(time(value[0],value[1]),24,Color.WHITE);
        display.setTypeface(Typeface.DEFAULT,Typeface.BOLD); display.setGravity(Gravity.CENTER);
        display.setBackground(roundBg(Color.rgb(7,32,68),Color.rgb(73,154,238),12));
        box.addView(display,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46)));
        TextView help=text("استخدم الأسهم وزر OK فقط",12,Color.rgb(190,210,235)); help.setGravity(Gravity.CENTER);
        box.addView(help,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(24)));

        LinearLayout hours=new LinearLayout(this); hours.setOrientation(LinearLayout.HORIZONTAL); hours.setGravity(Gravity.CENTER);
        Button hourMinus=dialogButton("الساعة −"), hourPlus=dialogButton("الساعة +");
        hours.addView(hourMinus,dialogButtonParams()); hours.addView(hourPlus,dialogButtonParams());
        box.addView(hours,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));

        LinearLayout minutes=new LinearLayout(this); minutes.setOrientation(LinearLayout.HORIZONTAL); minutes.setGravity(Gravity.CENTER);
        Button minuteMinus=dialogButton("الدقيقة −"), minutePlus=dialogButton("الدقيقة +");
        minutes.addView(minuteMinus,dialogButtonParams()); minutes.addView(minutePlus,dialogButtonParams());
        box.addView(minutes,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));

        Runnable update=()->display.setText(time(value[0],value[1]));
        hourMinus.setOnClickListener(v->{ value[0]=(value[0]+23)%24; update.run(); });
        hourPlus.setOnClickListener(v->{ value[0]=(value[0]+1)%24; update.run(); });
        minuteMinus.setOnClickListener(v->{ value[1]=(value[1]+59)%60; update.run(); });
        minutePlus.setOnClickListener(v->{ value[1]=(value[1]+1)%60; update.run(); });

        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
                .setTitle(startTime?"تغيير وقت بدء القفل":"تغيير وقت الفتح التلقائي")
                .setView(box)
                .setPositiveButton("حفظ",(d,w)->{
                    if(startTime) Prefs.setStart(this,value[0],value[1]); else Prefs.setEnd(this,value[0],value[1]);
                    ScheduleUtil.reschedule(this); LockOverlayService.sync(this); buildSettings();
                }).setNegativeButton("إلغاء",null).create();
        dialog.setOnShowListener(x->{
            hourPlus.requestFocus();
            if(dialog.getWindow()!=null){
                int screenW=getResources().getDisplayMetrics().widthPixels;
                int screenH=getResources().getDisplayMetrics().heightPixels;
                dialog.getWindow().setLayout(Math.min(dp(560),(int)(screenW*.46f)),Math.min(dp(330),(int)(screenH*.62f)));
                dialog.getWindow().setGravity(Gravity.CENTER);
            }
        });
        dialog.show();
    }

    private void refreshStatus(){
        if(status==null) return;
        String s="الجدول "+(Prefs.enabled(this)?"يعمل":"متوقف")+"  •  "+time(Prefs.startHour(this),Prefs.startMinute(this))+" ← "+time(Prefs.endHour(this),Prefs.endMinute(this));
        if(ScheduleUtil.shouldLock(this)) s+="  •  وقت القفل الآن";
        else if(Prefs.bypassUntil(this)>System.currentTimeMillis()) s+="  •  فتح مؤقت";
        status.setText(s);
    }

    private void changePin(){
        MaskedPinEditText e=new MaskedPinEditText(this);
        e.setHint("PIN جديد");
        e.setHintTextColor(Color.GRAY);
        e.setSingleLine(true);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        android.app.AlertDialog d=new android.app.AlertDialog.Builder(this)
                .setTitle("تغيير PIN").setView(e).setPositiveButton("حفظ",null).setNegativeButton("إلغاء",null).create();
        d.setOnShowListener(x->d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String p=e.getText().toString();
            if(!p.matches("\\d{4,8}")){ toast("من 4 إلى 8 أرقام"); return; }
            hideKeyboard(e); Prefs.setPin(this,p); d.dismiss(); toast("تم تغيير PIN");
        }));
        d.show();
    }

    private void changeMessage(){
        EditText e=new EditText(this); e.setText(Prefs.message(this)); e.setSingleLine(true);
        new android.app.AlertDialog.Builder(this).setTitle("رسالة شاشة القفل").setView(e)
                .setPositiveButton("حفظ",(d,w)->{
                    hideKeyboard(e);
                    String s=e.getText().toString().trim();
                    if(!s.isEmpty()){ Prefs.setMessage(this,s); toast("تم حفظ الرسالة"); }
                }).setNegativeButton("إلغاء",null).show();
    }

    private boolean isAccessibilityEnabled(){
        String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(enabled==null) return false;
        ComponentName me=new ComponentName(this,GuardAccessibilityService.class);
        return enabled.toLowerCase().contains(me.flattenToString().toLowerCase());
    }

    private ComponentName adminComponent(){
        return new ComponentName(this,TvDeviceAdminReceiver.class);
    }

    private boolean isDeviceAdminActive(){
        DevicePolicyManager dpm=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
        return dpm!=null && dpm.isAdminActive(adminComponent());
    }

    private void requestDeviceAdmin(){
        if(isDeviceAdminActive()) return;
        try{
            Intent i=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,adminComponent());
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"تفعيل هذه الحماية يمنع حذف TV Lock بالطريقة العادية دون إلغاء صلاحية الإدارة أولًا.");
            startActivity(i);
        }catch(Exception e){
            toast("هذه الشاشة لا تدعم تفعيل حماية الحذف من النظام.");
        }
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER);
        c.setPadding(dp(14),dp(8),dp(14),dp(8));
        c.setBackground(roundBg(Color.argb(125,6,30,66),Color.argb(155,77,167,255),14));
        return c;
    }

    private LinearLayout infoCard(String label,String value){
        LinearLayout c=card();
        TextView l=text(label,12,MUTED); l.setGravity(Gravity.CENTER);
        TextView v=text(value,19,TEXT); v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); v.setGravity(Gravity.CENTER);
        c.addView(l,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(24)));
        c.addView(v,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(32)));
        return c;
    }

    private LinearLayout.LayoutParams weightedCardParams(){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(72),1);
        lp.setMargins(dp(5),dp(3),dp(5),dp(3)); return lp;
    }

    private LinearLayout.LayoutParams wideCardParams(){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.min(dp(700),(int)(getResources().getDisplayMetrics().widthPixels*.58f)),dp(72));
        lp.setMargins(0,dp(2),0,dp(5)); return lp;
    }

    private GradientDrawable roundBg(int fill,int stroke,int radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(1),stroke); return g;
    }

    private void addCardTitle(String s){
        TextView t=text(s,17,TEXT); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setGravity(Gravity.CENTER);
        t.setBackground(roundBg(Color.argb(125,7,37,76),Color.argb(150,79,170,255),14));
        t.setPadding(dp(14),dp(7),dp(14),dp(7));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.min(dp(520),(int)(getResources().getDisplayMetrics().widthPixels*.45f)),dp(46));
        lp.setMargins(0,dp(3),0,dp(5)); root.addView(t,lp);
    }

    private TextView note(String s){
        TextView t=text(s,13,MUTED); t.setGravity(Gravity.CENTER); t.setPadding(dp(8),dp(2),dp(8),dp(8));
        root.addView(t,new LinearLayout.LayoutParams(Math.min(dp(700),(int)(getResources().getDisplayMetrics().widthPixels*.60f)),ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private void section(String s){
        TextView t=text(s,15,CYAN); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setGravity(Gravity.CENTER);
        t.setPadding(0,dp(8),0,dp(3));
        root.addView(t,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(34)));
    }

    private TextView text(String s,int sp,int color){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); return t;
    }

    private MaskedPinEditText edit(String hint){
        MaskedPinEditText e=new MaskedPinEditText(this);
        e.setHint(hint); e.setHintTextColor(Color.rgb(155,180,210));
        e.setTextSize(18); e.setGravity(Gravity.CENTER);
        e.setBackground(roundBg(Color.argb(155,7,32,68),Color.rgb(73,154,238),14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.min(dp(360),(int)(getResources().getDisplayMetrics().widthPixels*.32f)),dp(48));
        lp.setMargins(0,dp(5),0,dp(8)); root.addView(e,lp); return e;
    }

    private Button button(String s){
        Button b=new Button(this);
        b.setText(s); b.setTextSize(14); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setFocusable(true);
        b.setMinHeight(0); b.setMinWidth(0); b.setPadding(dp(10),0,dp(10),0);
        b.setBackgroundResource(R.drawable.focus_button);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.min(dp(500),(int)(getResources().getDisplayMetrics().widthPixels*.44f)),dp(46));
        lp.setMargins(0,dp(3),0,dp(3)); root.addView(b,lp); return b;
    }

    private Button buttonSmall(String s){
        Button b=new Button(this);
        b.setText(s); b.setTextSize(10); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setFocusable(true);
        b.setMinHeight(0); b.setMinWidth(0); b.setPadding(0,0,0,0);
        b.setBackgroundResource(R.drawable.focus_button);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);
        lp.setMargins(dp(2),0,dp(2),0); b.setLayoutParams(lp); return b;
    }

    private LinearLayout.LayoutParams dialogButtonParams(){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);
        lp.setMargins(dp(4),dp(3),dp(4),dp(3)); return lp;
    }

    private Button dialogButton(String s){
        Button b=new Button(this);
        b.setText(s); b.setTextSize(13); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setFocusable(true);
        b.setMinHeight(0); b.setPadding(0,0,0,0); b.setBackgroundResource(R.drawable.focus_button); return b;
    }

    private void footer(){
        TextView f=text("من صنع  hksalameh  |  HKS",11,Color.rgb(146,183,222)); f.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(30));
        lp.setMargins(0,dp(8),0,0); root.addView(f,lp);
    }

    private void hideKeyboard(View v){
        try{
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            View target=v!=null?v:getCurrentFocus();
            if(imm!=null && target!=null) imm.hideSoftInputFromWindow(target.getWindowToken(),0);
            if(target!=null) target.clearFocus();
        }catch(Exception ignored){}
    }

    private String time(int h,int m){
        int hh=h%12; if(hh==0) hh=12;
        return String.format("%d:%02d %s",hh,m,h<12?"ص":"م");
    }

    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
}
