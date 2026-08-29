package com.smartcodejo.tvlock;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView status;
    private boolean authenticated=false;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        if(Prefs.pin(this).isEmpty()) showFirstSetup(); else showPinGate();
    }

    @Override protected void onResume(){
        super.onResume();
        if(authenticated) buildSettings();
        LockOverlayService.sync(this);
    }

    private void baseRoot(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(56),dp(36),dp(56),dp(48));
        root.setBackgroundColor(Color.rgb(11,18,32));
        scroll.addView(root,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private void showFirstSetup(){
        baseRoot();
        title("إعداد TV Lock لأول مرة");
        note("اختر رقم PIN من 4 إلى 8 أرقام. ستحتاجه لتغيير الإعدادات أو فتح التلفزيون قبل الموعد.");
        EditText pin=edit("PIN جديد");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        Button save=button("حفظ وبدء الإعداد");
        save.setOnClickListener(v->{
            String x=pin.getText().toString().trim();
            if(!x.matches("\\d{4,8}")){ toast("الـ PIN يجب أن يكون من 4 إلى 8 أرقام"); return; }
            Prefs.setPin(this,x);
            Prefs.setEnabled(this,true);
            ScheduleUtil.reschedule(this);
            authenticated=true;
            buildSettings();
        });
        pin.requestFocus();
    }

    private void showPinGate(){
        baseRoot();
        title("إعدادات TV Lock");
        note("الإعدادات محمية. أدخل PIN للمتابعة.");
        EditText pin=edit("PIN");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        Button enter=button("دخول");
        enter.setOnClickListener(v->{
            if(Prefs.pin(this).equals(pin.getText().toString())){ authenticated=true; buildSettings(); }
            else { pin.setText(""); toast("PIN غير صحيح"); }
        });
        pin.requestFocus();
    }

    private void buildSettings(){
        baseRoot();
        title("TV Lock");
        status=note("");
        refreshStatus();

        section("الجدول اليومي");
        Button start=button("بدء القفل: "+time(Prefs.startHour(this),Prefs.startMinute(this)));
        start.setOnClickListener(v->new TimePickerDialog(this,(p,h,m)->{
            Prefs.setStart(this,h,m); ScheduleUtil.reschedule(this); buildSettings();
        },Prefs.startHour(this),Prefs.startMinute(this),false).show());

        Button end=button("الفتح التلقائي: "+time(Prefs.endHour(this),Prefs.endMinute(this)));
        end.setOnClickListener(v->new TimePickerDialog(this,(p,h,m)->{
            Prefs.setEnd(this,h,m); ScheduleUtil.reschedule(this); buildSettings();
        },Prefs.endHour(this),Prefs.endMinute(this),false).show());

        section("أيام العمل");
        String[] names={"الاثنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت","الأحد"};
        LinearLayout days=new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        days.setGravity(Gravity.CENTER);
        days.setPadding(0,0,0,dp(12));
        for(int i=1;i<=7;i++){
            final int d=i;
            Button b=buttonSmall((Prefs.dayEnabled(this,d)?"✓ ":"")+names[i-1]);
            b.setOnClickListener(v->{ Prefs.setDayEnabled(this,d,!Prefs.dayEnabled(this,d)); ScheduleUtil.reschedule(this); buildSettings(); });
            days.addView(b);
        }
        root.addView(days,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        section("الحماية");
        Button pin=button("تغيير PIN");
        pin.setOnClickListener(v->changePin());
        Button msg=button("رسالة شاشة القفل");
        msg.setOnClickListener(v->changeMessage());

        section("تجربة وتحكم");
        Button lockNow=button("قفل الآن للاختبار");
        lockNow.setOnClickListener(v->{
            Prefs.setBypassUntil(this,0);
            Intent i=new Intent(this,LockOverlayService.class).putExtra("force",true);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        });
        Button temp=button("فتح مؤقت 30 دقيقة");
        temp.setOnClickListener(v->{ Prefs.setBypassUntil(this,System.currentTimeMillis()+30*60*1000L); LockOverlayService.sync(this); refreshStatus(); });
        Button enable=button(Prefs.enabled(this)?"إيقاف الجدول":"تشغيل الجدول");
        enable.setOnClickListener(v->{ Prefs.setEnabled(this,!Prefs.enabled(this)); Prefs.setBypassUntil(this,0); ScheduleUtil.reschedule(this); LockOverlayService.sync(this); buildSettings(); });

        section("صلاحيات مرة واحدة");
        Button overlay=button(Settings.canDrawOverlays(this)?"✓ الظهور فوق التطبيقات مفعّل":"تفعيل الظهور فوق التطبيقات");
        overlay.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))));
        Button acc=button(isAccessibilityEnabled()?"✓ خدمة الحماية مفعّلة":"تفعيل خدمة الحماية Accessibility");
        acc.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        if(Build.VERSION.SDK_INT>=31){
            AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
            Button alarm=button(am.canScheduleExactAlarms()?"✓ التوقيت الدقيق مفعّل":"تفعيل التوقيت الدقيق");
            alarm.setOnClickListener(v->{
                try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))); }
                catch(Exception e){ toast("هذه الشاشة لا تعرض إعداد التوقيت الدقيق؛ سيستخدم التطبيق البديل المتاح."); }
            });
        }
        note("بعد تفعيل الصلاحيات مرة واحدة، يعمل الجدول تلقائيًا حتى بعد إعادة تشغيل الشاشة.");
    }

    private void refreshStatus(){
        if(status==null) return;
        String s=Prefs.enabled(this)?"الجدول مفعّل":"الجدول متوقف";
        if(ScheduleUtil.shouldLock(this)) s+=" • التلفزيون داخل وقت القفل الآن";
        else if(Prefs.bypassUntil(this)>System.currentTimeMillis()) s+=" • فتح مؤقت مفعّل";
        status.setText(s);
    }

    private void changePin(){
        EditText e=new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setHint("PIN جديد");
        android.app.AlertDialog d=new android.app.AlertDialog.Builder(this).setTitle("تغيير PIN").setView(e).setPositiveButton("حفظ",null).setNegativeButton("إلغاء",null).create();
        d.setOnShowListener(x->d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String p=e.getText().toString();
            if(!p.matches("\\d{4,8}")){ toast("من 4 إلى 8 أرقام"); return; }
            Prefs.setPin(this,p); d.dismiss();
        }));
        d.show();
    }

    private void changeMessage(){
        EditText e=new EditText(this);
        e.setText(Prefs.message(this));
        e.setSingleLine(true);
        new android.app.AlertDialog.Builder(this).setTitle("رسالة شاشة القفل").setView(e).setPositiveButton("حفظ",(d,w)->{
            String s=e.getText().toString().trim(); if(!s.isEmpty()) Prefs.setMessage(this,s);
        }).setNegativeButton("إلغاء",null).show();
    }

    private boolean isAccessibilityEnabled(){
        String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(enabled==null) return false;
        ComponentName me=new ComponentName(this,GuardAccessibilityService.class);
        return enabled.toLowerCase().contains(me.flattenToString().toLowerCase());
    }

    private void title(String s){ TextView t=text(s,34,Color.rgb(244,247,251)); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,0,0,dp(12)); root.addView(t); }
    private TextView note(String s){ TextView t=text(s,18,Color.rgb(159,176,199)); t.setGravity(Gravity.CENTER); t.setPadding(0,0,0,dp(22)); root.addView(t,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)); return t; }
    private void section(String s){ TextView t=text(s,20,Color.rgb(53,194,163)); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setPadding(0,dp(18),0,dp(8)); root.addView(t); }
    private TextView text(String s,int sp,int color){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); return t; }
    private EditText edit(String hint){ EditText e=new EditText(this); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); e.setTextSize(24); e.setGravity(Gravity.CENTER); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(420),dp(70)); lp.setMargins(0,dp(8),0,dp(14)); root.addView(e,lp); return e; }
    private Button button(String s){ Button b=new Button(this); b.setText(s); b.setTextSize(19); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setFocusable(true); b.setBackgroundResource(R.drawable.focus_button); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(620),dp(66)); lp.setMargins(0,dp(6),0,dp(6)); root.addView(b,lp); return b; }
    private Button buttonSmall(String s){ Button b=new Button(this); b.setText(s); b.setTextSize(14); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setFocusable(true); b.setBackgroundResource(R.drawable.focus_button); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(58),1); lp.setMargins(dp(3),0,dp(3),0); b.setLayoutParams(lp); return b; }
    private String time(int h,int m){ int hh=h%12; if(hh==0) hh=12; return String.format("%d:%02d %s",hh,m,h<12?"ص":"م"); }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
}
