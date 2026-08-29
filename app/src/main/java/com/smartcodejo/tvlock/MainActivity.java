package com.smartcodejo.tvlock;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

    private final int BG_TOP=Color.rgb(3,15,39);
    private final int BG_MID=Color.rgb(7,39,78);
    private final int BG_BOTTOM=Color.rgb(2,13,31);
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
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_TOP,BG_MID,BG_BOTTOM});
        frame.setBackground(bg);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int sx=Math.max(dp(34),(int)(getResources().getDisplayMetrics().widthPixels*0.055f));
        root.setPadding(sx,dp(28),sx,dp(38));
        scroll.addView(root,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        frame.addView(scroll,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(frame);
    }

    private void brandHeader(String heading,String sub){
        TextView badge=text("HKS",20,Color.WHITE);
        badge.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundBg(Color.rgb(12,75,148),CYAN,18));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(100),dp(44));
        bp.setMargins(0,0,0,dp(8));
        root.addView(badge,bp);

        TextView t=text(heading,34,TEXT);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        root.addView(t,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));

        TextView s=text(sub,17,MUTED);
        s.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(34));
        sp.setMargins(0,0,0,dp(12));
        root.addView(s,sp);
    }

    private void showFirstSetup(){
        authenticated=false;
        inSettings=false;
        baseRoot();
        brandHeader("TV LOCK","إعداد الحماية لأول مرة");
        addCardTitle("أنشئ رمز الحماية");
        note("اختر PIN من 4 إلى 8 أرقام. ستحتاجه لفتح التلفزيون قبل الموعد أو تغيير الإعدادات.");
        EditText pin=edit("PIN جديد");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        Button save=button("حفظ وبدء الحماية");
        save.setOnClickListener(v->{
            String x=pin.getText().toString().trim();
            if(!x.matches("\\d{4,8}")){ toast("الـ PIN يجب أن يكون من 4 إلى 8 أرقام"); return; }
            Prefs.setPin(this,x);
            Prefs.setEnabled(this,true);
            ScheduleUtil.reschedule(this);
            authenticated=true;
            buildDashboard();
        });
        footer();
        pin.requestFocus();
    }

    private void showPinGate(){
        authenticated=false;
        inSettings=false;
        baseRoot();
        brandHeader("TV LOCK","لوحة التحكم محمية");
        addCardTitle("أدخل PIN للمتابعة");
        EditText pin=edit("PIN");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        Button enter=button("دخول إلى لوحة التحكم");
        enter.setOnClickListener(v->{
            if(Prefs.pin(this).equals(pin.getText().toString())){
                authenticated=true;
                buildDashboard();
            } else {
                pin.setText("");
                toast("PIN غير صحيح");
            }
        });
        footer();
        pin.requestFocus();
    }

    private void buildDashboard(){
        authenticated=true;
        inSettings=false;
        baseRoot();
        brandHeader("TV LOCK","حماية ذكية للتلفزيون • hksalameh");

        LinearLayout statusCard=card();
        TextView stTitle=text(Prefs.enabled(this)?"الحماية مفعّلة":"الحماية متوقفة",24,
                Prefs.enabled(this)?Color.rgb(111,225,255):Color.rgb(255,178,120));
        stTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        stTitle.setGravity(Gravity.CENTER);
        statusCard.addView(stTitle,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(42)));
        status=text("",17,MUTED);
        status.setGravity(Gravity.CENTER);
        statusCard.addView(status,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(36)));
        refreshStatus();
        root.addView(statusCard,wideCardParams());

        LinearLayout times=new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER);
        times.addView(infoCard("بدء القفل",time(Prefs.startHour(this),Prefs.startMinute(this))),weightedCardParams());
        times.addView(infoCard("الفتح التلقائي",time(Prefs.endHour(this),Prefs.endMinute(this))),weightedCardParams());
        root.addView(times,new LinearLayout.LayoutParams(Math.min(dp(900),(int)(getResources().getDisplayMetrics().widthPixels*.72f)),dp(126)));

        section("التحكم السريع");
        Button lockNow=button("🔒  قفل الآن للاختبار");
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

        if(!Settings.canDrawOverlays(this) || !isAccessibilityEnabled()){
            TextView warning=note("⚠ بعض صلاحيات الحماية غير مفعّلة. افتح الإعدادات لإكمالها.");
            warning.setTextColor(Color.rgb(255,202,120));
        }
        footer();
    }

    private void buildSettings(){
        authenticated=true;
        inSettings=true;
        baseRoot();
        brandHeader("الإعدادات","جميع إعدادات TV LOCK في مكان واحد");

        Button back=button("← العودة للوحة التحكم");
        back.setOnClickListener(v->{ inSettings=false; buildDashboard(); });

        section("الجدول اليومي");
        Button start=button("بدء القفل:  "+time(Prefs.startHour(this),Prefs.startMinute(this)));
        start.setOnClickListener(v->showRemoteTimeDialog(true));
        Button end=button("الفتح التلقائي:  "+time(Prefs.endHour(this),Prefs.endMinute(this)));
        end.setOnClickListener(v->showRemoteTimeDialog(false));

        section("أيام العمل");
        String[] names={"الاثنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت","الأحد"};
        LinearLayout days=new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        days.setGravity(Gravity.CENTER);
        for(int i=1;i<=7;i++){
            final int d=i;
            Button b=buttonSmall((Prefs.dayEnabled(this,d)?"✓ ":"")+names[i-1]);
            b.setOnClickListener(v->{
                Prefs.setDayEnabled(this,d,!Prefs.dayEnabled(this,d));
                ScheduleUtil.reschedule(this);
                buildSettings();
            });
            days.addView(b);
        }
        root.addView(days,new LinearLayout.LayoutParams(Math.min(dp(1120),(int)(getResources().getDisplayMetrics().widthPixels*.88f)),dp(64)));

        section("الحماية والرسالة");
        Button pin=button("تغيير PIN");
        pin.setOnClickListener(v->changePin());
        Button msg=button("تعديل رسالة شاشة القفل");
        msg.setOnClickListener(v->changeMessage());

        section("الصلاحيات");
        Button overlay=button(Settings.canDrawOverlays(this)?"✓ الظهور فوق التطبيقات مفعّل":"تفعيل الظهور فوق التطبيقات");
        overlay.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))));
        Button acc=button(isAccessibilityEnabled()?"✓ خدمة الحماية Accessibility مفعّلة":"تفعيل خدمة الحماية Accessibility");
        acc.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

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

        note("بعد تفعيل الصلاحيات مرة واحدة يعمل الجدول تلقائيًا حتى بعد إعادة تشغيل التلفزيون.");
        footer();
    }

    private void showTempUnlockDialog(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20),dp(12),dp(20),dp(8));
        TextView msg=text("اختر مدة الفتح المؤقت",19,Color.DKGRAY);
        msg.setGravity(Gravity.CENTER);
        box.addView(msg,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));

        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        Button b15=dialogButton("15 دقيقة");
        Button b30=dialogButton("30 دقيقة");
        Button b60=dialogButton("60 دقيقة");
        row.addView(b15,dialogButtonParams());
        row.addView(b30,dialogButtonParams());
        row.addView(b60,dialogButtonParams());
        box.addView(row,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(76)));

        android.app.AlertDialog d=new android.app.AlertDialog.Builder(this)
                .setTitle("فتح مؤقت")
                .setView(box)
                .setNegativeButton("إلغاء",null)
                .create();
        View.OnClickListener choose=v->{
            int min=v==b15?15:(v==b30?30:60);
            Prefs.setBypassUntil(this,System.currentTimeMillis()+min*60*1000L);
            LockOverlayService.sync(this);
            d.dismiss();
            refreshStatus();
            toast("تم الفتح مؤقتًا لمدة "+min+" دقيقة");
        };
        b15.setOnClickListener(choose); b30.setOnClickListener(choose); b60.setOnClickListener(choose);
        d.setOnShowListener(x->b30.requestFocus());
        d.show();
    }

    private void showRemoteTimeDialog(boolean startTime){
        final int[] value={
                startTime?Prefs.startHour(this):Prefs.endHour(this),
                startTime?Prefs.startMinute(this):Prefs.endMinute(this)
        };

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(32),dp(18),dp(32),dp(10));

        TextView display=text(time(value[0],value[1]),34,Color.DKGRAY);
        display.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        display.setGravity(Gravity.CENTER);
        display.setPadding(0,0,0,dp(12));
        box.addView(display,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(66)));

        TextView help=text("استخدم الأسهم وزر OK فقط",16,Color.GRAY);
        help.setGravity(Gravity.CENTER);
        box.addView(help,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(38)));

        LinearLayout hours=new LinearLayout(this);
        hours.setOrientation(LinearLayout.HORIZONTAL);
        hours.setGravity(Gravity.CENTER);
        Button hourMinus=dialogButton("الساعة −");
        Button hourPlus=dialogButton("الساعة +");
        hours.addView(hourMinus,dialogButtonParams());
        hours.addView(hourPlus,dialogButtonParams());
        box.addView(hours,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(72)));

        LinearLayout minutes=new LinearLayout(this);
        minutes.setOrientation(LinearLayout.HORIZONTAL);
        minutes.setGravity(Gravity.CENTER);
        Button minuteMinus=dialogButton("الدقيقة −");
        Button minutePlus=dialogButton("الدقيقة +");
        minutes.addView(minuteMinus,dialogButtonParams());
        minutes.addView(minutePlus,dialogButtonParams());
        box.addView(minutes,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(72)));

        Runnable update=()->display.setText(time(value[0],value[1]));
        hourMinus.setOnClickListener(v->{ value[0]=(value[0]+23)%24; update.run(); });
        hourPlus.setOnClickListener(v->{ value[0]=(value[0]+1)%24; update.run(); });
        minuteMinus.setOnClickListener(v->{ value[1]=(value[1]+59)%60; update.run(); });
        minutePlus.setOnClickListener(v->{ value[1]=(value[1]+1)%60; update.run(); });

        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
                .setTitle(startTime?"تغيير وقت بدء القفل":"تغيير وقت الفتح التلقائي")
                .setView(box)
                .setPositiveButton("حفظ",(d,w)->{
                    if(startTime) Prefs.setStart(this,value[0],value[1]);
                    else Prefs.setEnd(this,value[0],value[1]);
                    ScheduleUtil.reschedule(this);
                    LockOverlayService.sync(this);
                    buildSettings();
                })
                .setNegativeButton("إلغاء",null)
                .create();
        dialog.setOnShowListener(x->hourPlus.requestFocus());
        dialog.show();
    }

    private void refreshStatus(){
        if(status==null) return;
        String s="الجدول "+(Prefs.enabled(this)?"يعمل":"متوقف")+"  •  "+time(Prefs.startHour(this),Prefs.startMinute(this))+" ← "+time(Prefs.endHour(this),Prefs.endMinute(this));
        if(ScheduleUtil.shouldLock(this)) s+="  •  وقت القفل الآن";
        else if(Prefs.bypassUntil(this)>System.currentTimeMillis()) s+="  •  فتح مؤقت مفعّل";
        status.setText(s);
    }

    private void changePin(){
        EditText e=new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setHint("PIN جديد");
        android.app.AlertDialog d=new android.app.AlertDialog.Builder(this)
                .setTitle("تغيير PIN").setView(e).setPositiveButton("حفظ",null).setNegativeButton("إلغاء",null).create();
        d.setOnShowListener(x->d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String p=e.getText().toString();
            if(!p.matches("\\d{4,8}")){ toast("من 4 إلى 8 أرقام"); return; }
            Prefs.setPin(this,p); d.dismiss(); toast("تم تغيير PIN");
        }));
        d.show();
    }

    private void changeMessage(){
        EditText e=new EditText(this);
        e.setText(Prefs.message(this));
        e.setSingleLine(true);
        new android.app.AlertDialog.Builder(this).setTitle("رسالة شاشة القفل").setView(e)
                .setPositiveButton("حفظ",(d,w)->{
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

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(22),dp(14),dp(22),dp(14));
        c.setBackground(roundBg(Color.argb(115,9,40,82),Color.argb(150,77,167,255),18));
        return c;
    }

    private LinearLayout infoCard(String label,String value){
        LinearLayout c=card();
        TextView l=text(label,16,MUTED); l.setGravity(Gravity.CENTER);
        TextView v=text(value,25,TEXT); v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); v.setGravity(Gravity.CENTER);
        c.addView(l,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(34)));
        c.addView(v,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44)));
        return c;
    }

    private LinearLayout.LayoutParams weightedCardParams(){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(108),1);
        lp.setMargins(dp(7),dp(6),dp(7),dp(6));
        return lp;
    }

    private LinearLayout.LayoutParams wideCardParams(){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.min(dp(900),(int)(getResources().getDisplayMetrics().widthPixels*.72f)),dp(105));
        lp.setMargins(0,dp(4),0,dp(8));
        return lp;
    }

    private GradientDrawable roundBg(int fill,int stroke,int radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(1),stroke);
        return g;
    }

    private void addCardTitle(String s){
        TextView t=text(s,22,TEXT);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackground(roundBg(Color.argb(110,7,37,76),Color.argb(130,79,170,255),18));
        t.setPadding(dp(20),dp(12),dp(20),dp(12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.min(dp(680),(int)(getResources().getDisplayMetrics().widthPixels*.6f)),dp(64));
        lp.setMargins(0,dp(6),0,dp(8));
        root.addView(t,lp);
    }

    private TextView note(String s){
        TextView t=text(s,17,MUTED);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10),dp(4),dp(10),dp(14));
        root.addView(t,new LinearLayout.LayoutParams(Math.min(dp(900),(int)(getResources().getDisplayMetrics().widthPixels*.76f)),ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private void section(String s){
        TextView t=text(s,19,CYAN);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0,dp(15),0,dp(6));
        root.addView(t,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
    }

    private TextView text(String s,int sp,int color){
        TextView t=new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }

    private EditText edit(String hint){
        EditText e=new EditText(this);
        e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.rgb(155,180,210));
        e.setTextSize(24); e.setGravity(Gravity.CENTER);
        e.setBackground(roundBg(Color.argb(130,7,32,68),Color.rgb(73,154,238),16));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.min(dp(460),(int)(getResources().getDisplayMetrics().widthPixels*.42f)),dp(66));
        lp.setMargins(0,dp(8),0,dp(14)); root.addView(e,lp); return e;
    }

    private Button button(String s){
        Button b=new Button(this);
        b.setText(s); b.setTextSize(18); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setFocusable(true);
        b.setBackgroundResource(R.drawable.focus_button);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.min(dp(680),(int)(getResources().getDisplayMetrics().widthPixels*.62f)),dp(62));
        lp.setMargins(0,dp(5),0,dp(5)); root.addView(b,lp); return b;
    }

    private Button buttonSmall(String s){
        Button b=new Button(this);
        b.setText(s); b.setTextSize(13); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setFocusable(true);
        b.setBackgroundResource(R.drawable.focus_button);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(56),1);
        lp.setMargins(dp(2),0,dp(2),0); b.setLayoutParams(lp); return b;
    }

    private LinearLayout.LayoutParams dialogButtonParams(){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(60),1);
        lp.setMargins(dp(5),dp(4),dp(5),dp(4)); return lp;
    }

    private Button dialogButton(String s){
        Button b=new Button(this);
        b.setText(s); b.setTextSize(17); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setFocusable(true);
        b.setBackgroundResource(R.drawable.focus_button); return b;
    }

    private void footer(){
        TextView f=text("من صنع  hksalameh  |  HKS",14,Color.rgb(146,183,222));
        f.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(42));
        lp.setMargins(0,dp(15),0,0); root.addView(f,lp);
    }

    private String time(int h,int m){
        int hh=h%12; if(hh==0) hh=12;
        return String.format("%d:%02d %s",hh,m,h<12?"ص":"م");
    }

    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
}
