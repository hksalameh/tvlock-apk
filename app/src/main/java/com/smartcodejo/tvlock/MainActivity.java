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

    private int BLUE=Color.rgb(48,157,255);
    private int CYAN=Color.rgb(83,210,255);
    private final int TEXT=Color.rgb(244,248,255);
    private final int MUTED=Color.rgb(173,201,232);

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        applyTheme();
        if(!Prefs.hasPin(this)) showFirstSetup(); else showPinGate();
    }

    @Override protected void onResume(){
        super.onResume();
        LockOverlayService.sync(this);
        if(authenticated){
            if(inSettings) buildSettings(); else buildDashboard();
        }
    }

    @Override public boolean onKeyDown(int keyCode,android.view.KeyEvent e){
        if(keyCode==android.view.KeyEvent.KEYCODE_BACK && authenticated){
            if(inSettings){ inSettings=false; page=null; buildDashboard(); return true; }
        }
        return super.onKeyDown(keyCode,e);
    }

    private void applyTheme(){ CYAN=Theme.accent(this); BLUE=Theme.focus2(this); }

    private void baseRoot(){
        applyTheme();
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
        badge.setBackground(roundBg(Theme.focus1(this),CYAN,14));
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
        pinPad(pin,()->action.onClick(pin));
        footer();
    }

    private void pinPad(MaskedPinEditText pin,Runnable submit){
        String[][] rows={{"1","2","3"},{"4","5","6"},{"7","8","9"},{"⌫","0","✓"}};
        for(String[] r:rows){
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
            for(String k:r){
                Button b=new Button(this);
                b.setText(k); b.setTextSize(19); b.setTextColor(Color.WHITE); b.setAllCaps(false);
                b.setFocusable(true); b.setMinHeight(0); b.setMinWidth(0); b.setPadding(0,0,0,0);
                b.setBackground(Theme.focusBg(this,getResources().getDisplayMetrics().density));
                b.setOnClickListener(v->{
                    String cur=pin.getText().toString();
                    if(k.equals("⌫")){ if(!cur.isEmpty()) pin.setText(cur.substring(0,cur.length()-1)); }
                    else if(k.equals("✓")) submit.run();
                    else if(cur.length()<8) pin.setText(cur+k);
                    pin.setSelection(pin.getText().length());
                });
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(78),dp(42));
                lp.setMargins(dp(4),dp(3),dp(4),dp(3));
                row.addView(b,lp);
            }
            root.addView(row,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        note("أدخل الرقم من أزرار الشاشة بالريموت، أو من لوحة مفاتيح الموبايل");
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
            if(Prefs.checkPin(this,pin.getText().toString())){
                hideKeyboard(pin);
                authenticated=true;
                buildDashboard();
            } else {
                pin.setText("");
                { EventLog.add(this,"PIN غلط في لوحة التحكم"); toast("PIN غير صحيح"); }
            }
        };
        enter.setOnClickListener(action);
        pin.setOnEditorActionListener((v,id,event)->{
            if(id==EditorInfo.IME_ACTION_DONE){ action.onClick(pin); return true; }
            return false;
        });
        pinPad(pin,()->action.onClick(pin));
        footer();
    }

    private Button tile(String icon,String title,String summary){
        Button b=new Button(this);
        b.setText(icon+"  "+title+"\n"+summary); b.setTextSize(15); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setFocusable(true); b.setMinHeight(0); b.setMinWidth(0); b.setPadding(dp(12),dp(6),dp(12),dp(6));
        b.setBackground(Theme.focusBg(this,getResources().getDisplayMetrics().density));
        return b;
    }

    private Button iconBtn(String icon,String label){
        Button b=new Button(this);
        b.setText(icon+"\n"+label); b.setTextSize(14); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setFocusable(true); b.setMinHeight(0); b.setMinWidth(0); b.setPadding(dp(6),dp(8),dp(6),dp(8));
        b.setBackground(Theme.focusBg(this,getResources().getDisplayMetrics().density));
        return b;
    }

    private String fmtMs(long ms){
        java.time.ZonedDateTime z=java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.ofHours(3));
        return time(z.getHour(),z.getMinute());
    }

    private void buildDashboard(){
        authenticated=true; inSettings=false; page=null; hideKeyboard(null); baseRoot();
        TextView badge=text("HKS",13,Color.WHITE);
        badge.setTypeface(Typeface.DEFAULT,Typeface.BOLD); badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundBg(Theme.focus1(this),CYAN,14));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(60),dp(26)); bp.setMargins(0,dp(4),0,dp(2));
        root.addView(badge,bp);

        android.widget.TextClock clock=new android.widget.TextClock(this);
        clock.setFormat12Hour("h:mm"); clock.setFormat24Hour("h:mm");
        clock.setTextColor(TEXT); clock.setTextSize(84); clock.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        clock.setGravity(Gravity.CENTER); clock.setIncludeFontPadding(false);
        root.addView(clock,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(112)));
        android.widget.TextClock date=new android.widget.TextClock(this);
        date.setFormat12Hour("a  ·  EEEE d MMMM"); date.setFormat24Hour("a  ·  EEEE d MMMM");
        date.setTextColor(MUTED); date.setTextSize(17); date.setGravity(Gravity.CENTER);
        root.addView(date,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(30)));

        boolean locked=ScheduleUtil.shouldLock(this);
        boolean temp=!locked && Prefs.bypassUntil(this)>System.currentTimeMillis();
        int[] st=ScheduleUtil.effectiveStart(this,ScheduleUtil.tonightWindowDate(this));
        String pill;
        int pc;
        if(locked){ pill="● مقفول  ·  يُفتح "+fmtMs(ScheduleUtil.currentWindowEnd(this)); pc=Color.rgb(255,178,120); }
        else if(!Prefs.enabled(this)){ pill="○ الجدول متوقف"; pc=MUTED; }
        else if(temp){ pill="● فتح مؤقت حتى "+fmtMs(Prefs.bypassUntil(this)); pc=CYAN; }
        else { pill="● مفتوح  ·  يقفل "+time(st[0],st[1]); pc=CYAN; }
        TextView pl=text(pill,18,pc); pl.setGravity(Gravity.CENTER);
        pl.setPadding(dp(22),dp(7),dp(22),dp(7));
        pl.setBackground(roundBg(Color.argb(60,255,255,255),Color.argb(0,0,0,0),22));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.setMargins(0,dp(10),0,dp(26));
        root.addView(pl,pp);
        status=null;

        Button lockNow=iconBtn("🔒","قفل الآن");
        lockNow.setOnClickListener(v->{
            Prefs.setBypassUntil(this,0);
            Intent i=new Intent(this,LockOverlayService.class).putExtra("force",true);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        });
        Button tmp=iconBtn("⏱","فتح مؤقت");
        tmp.setOnClickListener(v->showTempUnlockDialog());
        Button enable=iconBtn(Prefs.enabled(this)?"⏸":"▶",Prefs.enabled(this)?"إيقاف الجدول":"تشغيل الجدول");
        enable.setOnClickListener(v->{
            Prefs.setEnabled(this,!Prefs.enabled(this));
            Prefs.setBypassUntil(this,0);
            ScheduleUtil.reschedule(this);
            LockOverlayService.sync(this);
            buildDashboard();
        });
        Button settingsBtn=iconBtn("⚙","الإعدادات");
        settingsBtn.setOnClickListener(v->{ inSettings=true; page="sched"; buildSettings(); });
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER);
        for(Button x:new Button[]{lockNow,tmp,enable,settingsBtn}){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(130),dp(76)); lp.setMargins(dp(10),0,dp(10),0);
            bar.addView(x,lp);
        }
        root.addView(bar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        if(!Settings.canDrawOverlays(this) || !isAccessibilityEnabled() || !isDeviceAdminActive()){
            TextView warning=note("⚠ بعض صلاحيات الحماية غير مفعّلة. افتح الإعدادات ثم الصلاحيات لإكمالها.");
            warning.setTextColor(Color.rgb(255,202,120));
        }
        footer();
    }

    private String page=null;

    private Button toggle(String label,boolean on){
        Button b=button((on?"🟢  ":"⚪  ")+label+"   —   "+(on?"شغّال":"موقوف"));
        b.setTextColor(on?Color.rgb(150,255,170):Color.rgb(200,210,225));
        return b;
    }

    private void section2(String s){
        TextView t=text(s,15,CYAN); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setGravity(Gravity.CENTER);
        t.setPadding(0,dp(10),0,dp(3));
        root.addView(t,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(34)));
    }

    private Button card(String icon,String title,String summary,String target){
        Button b=tile(icon,title,summary);
        b.setOnClickListener(v->{ page=target; buildSettings(); });
        return b;
    }

    private void cardRow(Button l,Button r){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
        int w=Math.min(dp(780),(int)(getResources().getDisplayMetrics().widthPixels*.62f));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(92),1f); lp.setMargins(dp(5),dp(5),dp(5),dp(5));
        row.addView(l,lp);
        LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,dp(92),1f); lp2.setMargins(dp(5),dp(5),dp(5),dp(5));
        if(r!=null) row.addView(r,lp2); else { View sp=new View(this); row.addView(sp,lp2); }
        root.addView(row,new LinearLayout.LayoutParams(w,ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private String onOff(boolean on){ return on?"شغّال":"موقوف"; }

    private void buildSettings(){
        authenticated=true; inSettings=true; hideKeyboard(null); baseRoot();
        accordion=false;
        if(page==null) page="sched";

        TextView title=text("الإعدادات",22,TEXT); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(40)));

        final LinearLayout outer=root;
        LinearLayout split=new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        split.setGravity(Gravity.TOP);

        LinearLayout menu=new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(6),dp(6),dp(6),dp(6));
        menu.setBackground(roundBg(Color.argb(90,0,0,0),Color.argb(0,0,0,0),16));
        String[][] items={
            {"sched","🕒","الجدول الليلي"},{"exam","📚","الامتحانات"},{"session","⏸","حد التشغيل"},
            {"apps","📱","قفل التطبيقات"},{"protect","🔐","الحماية والرسالة"},{"perms","✅","الصلاحيات"},
            {"theme","🎨","المظهر"},{"update","⬇","التحديث"}};
        Button back=menuItem("←","العودة",false);
        back.setOnClickListener(v->{ inSettings=false; page=null; buildDashboard(); });
        menu.addView(back,menuLp());
        Button selected=null;
        for(String[] it:items){
            final String target=it[0];
            boolean sel=target.equals(page);
            Button m=menuItem(it[1],it[2],sel);
            m.setOnClickListener(v->{ page=target; buildSettings(); });
            menu.addView(m,menuLp());
            if(sel) selected=m;
        }
        int sw=getResources().getDisplayMetrics().widthPixels;
        split.addView(menu,new LinearLayout.LayoutParams(Math.min(dp(300),(int)(sw*.24f)),ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL); content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(16),0,dp(8),0);
        LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
        split.addView(content,cl);
        outer.addView(split,new LinearLayout.LayoutParams(Math.min(dp(1100),(int)(sw*.88f)),ViewGroup.LayoutParams.WRAP_CONTENT));

        root=content;
        switch(page){
            case "sched": section2("الجدول الليلي"); pgSchedule(); break;
            case "exam": section2("وضع الامتحانات"); pgExam(); break;
            case "session": section2("حد التشغيل المتواصل"); pgSession(); break;
            case "apps": section2("قفل التطبيقات"); pgApps(); break;
            case "update": section2("التحديث"); pgUpdate(); break;
            case "protect": section2("الحماية والرسالة"); pgProtect(); break;
            case "perms": section2("الصلاحيات"); pgPerms(); break;
            case "theme": section2("المظهر والألوان"); pgTheme(); break;
            default: break;
        }
        root=outer;
        footer();
        if(selected!=null) selected.requestFocus();
    }

    private LinearLayout.LayoutParams menuLp(){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44));
        lp.setMargins(0,dp(2),0,dp(2)); return lp;
    }

    private Button menuItem(String icon,String label,boolean sel){
        Button b=new Button(this);
        b.setText(icon+"   "+label); b.setTextSize(15); b.setAllCaps(false); b.setFocusable(true);
        b.setTextColor(sel?Color.WHITE:MUTED); b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        b.setMinHeight(0); b.setMinWidth(0); b.setPadding(dp(12),0,dp(12),0);
        android.graphics.drawable.StateListDrawable d=Theme.focusBg(this,getResources().getDisplayMetrics().density);
        if(sel){ b.setBackground(Theme.focusBg(this,getResources().getDisplayMetrics().density)); b.setSelected(true); }
        else { 
            android.graphics.drawable.StateListDrawable sl=new android.graphics.drawable.StateListDrawable();
            GradientDrawable focused=new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,new int[]{Theme.focus1(this),Theme.focus2(this)});
            focused.setCornerRadius(dp(12)); focused.setStroke(dp(2),CYAN);
            GradientDrawable flat=new GradientDrawable(); flat.setColor(Color.TRANSPARENT); flat.setCornerRadius(dp(12));
            sl.addState(new int[]{android.R.attr.state_focused},focused);
            sl.addState(new int[]{},flat);
            b.setBackground(sl);
        }
        return b;
    }

    private void pgTheme(){
        note("اختر مجموعة الألوان — تنطبق على لوحة التحكم وشاشة القفل");
        for(int i=0;i<Theme.NAMES.length;i++){
            final int k=i;
            boolean sel=Theme.idx(this)==i;
            Button b=button(Theme.ICONS[i]+"  "+Theme.NAMES[i]+(sel?"   ✓":""));
            if(sel) b.setTextColor(Theme.accent(this));
            b.setOnClickListener(v->{ Prefs.setTheme(this,k); buildSettings(); });
        }
    }

    private void pgSchedule(){
        Button enable=toggle("الجدول الليلي",Prefs.enabled(this));
        enable.setOnClickListener(v->{
            Prefs.setEnabled(this,!Prefs.enabled(this));
            Prefs.setBypassUntil(this,0);
            ScheduleUtil.reschedule(this);
            LockOverlayService.sync(this);
            buildSettings();
        });
        Button start=button("بدء القفل:  "+time(Prefs.startHour(this),Prefs.startMinute(this)));
        start.setOnClickListener(v->showRemoteTimeDialog(true,false));
        Button end=button("الفتح التلقائي:  "+time(Prefs.endHour(this),Prefs.endMinute(this)));
        end.setOnClickListener(v->showRemoteTimeDialog(false,false));
        java.time.LocalDate tw=ScheduleUtil.tonightWindowDate(this);
        boolean tonightSet=tw.toString().equals(Prefs.tonightDate(this));
        int[] es=ScheduleUtil.effectiveStart(this,tw);
        Button tonight=button("🌙 قفل هذه الليلة فقط:  "+time(es[0],es[1])+(tonightSet?"  (معدّل)":""));
        tonight.setOnClickListener(v->showRemoteTimeDialog(true,true));
        if(tonightSet){
            Button undo=button("إلغاء تعديل الليلة");
            undo.setOnClickListener(v->{ ScheduleUtil.clearTonightStart(this); buildSettings(); });
        }
        String ip=localIp();
        if(ip!=null) note("التحكم من الموبايل (نفس الواي فاي): http://"+ip+":"+PhoneUnlockServer.PORT);
        section2("أيام العمل");
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
        section2("وقت لكل يوم");
        for(int i=1;i<=7;i++){
            final int d=i; final String nm=names[i-1];
            Button b=button(nm+":  "+time(Prefs.dayStartHour(this,d),Prefs.dayStartMinute(this,d))+" ← "+time(Prefs.dayEndHour(this,d),Prefs.dayEndMinute(this,d))+(Prefs.hasDayTimes(this,d)?"  *":""));
            b.setOnClickListener(v->editDayTimes(d,nm));
        }
        Button resetDays=button("إعادة كل الأيام للوقت العام");
        resetDays.setOnClickListener(v->{ Prefs.clearAllDayTimes(this); ScheduleUtil.reschedule(this); LockOverlayService.sync(this); buildSettings(); });
    }

    private void pgExam(){
        Button exam=toggle("وضع الامتحانات",Prefs.extraOn(this));
        exam.setOnClickListener(v->{ Prefs.setExtraOn(this,!Prefs.extraOn(this)); ScheduleUtil.reschedule(this); LockOverlayService.sync(this); buildSettings(); });
        Button exS=button("بداية:  "+time(Prefs.extraStartHour(this),Prefs.extraStartMinute(this)));
        exS.setOnClickListener(v->pickTime("بداية وضع الامتحانات",Prefs.extraStartHour(this),Prefs.extraStartMinute(this),(h,m)->{
            Prefs.setExtra(this,Prefs.extraOn(this),h,m,Prefs.extraEndHour(this),Prefs.extraEndMinute(this)); ScheduleUtil.reschedule(this); buildSettings(); }));
        Button exE=button("نهاية:  "+time(Prefs.extraEndHour(this),Prefs.extraEndMinute(this)));
        exE.setOnClickListener(v->pickTime("نهاية وضع الامتحانات",Prefs.extraEndHour(this),Prefs.extraEndMinute(this),(h,m)->{
            Prefs.setExtra(this,Prefs.extraOn(this),Prefs.extraStartHour(this),Prefs.extraStartMinute(this),h,m); ScheduleUtil.reschedule(this); buildSettings(); }));
    }

    private void pgSession(){
        Button ses=toggle("الحد من التشغيل المتواصل",Prefs.sessionOn(this));
        ses.setOnClickListener(v->{ Prefs.setSessionOn(this,!Prefs.sessionOn(this)); buildSettings(); });
        Button sl=button("بعد "+Prefs.sessionLimitMin(this)+" دقيقة (اضغط للتغيير: 60/90/120/180)");
        sl.setOnClickListener(v->{ int c=Prefs.sessionLimitMin(this); Prefs.setSessionLimitMin(this,c<60?60:c<90?90:c<120?120:c<180?180:60); buildSettings(); });
        Button sb=button("استراحة "+Prefs.sessionBreakMin(this)+" دقيقة (اضغط للتغيير: 10/15/30)");
        sb.setOnClickListener(v->{ int c=Prefs.sessionBreakMin(this); Prefs.setSessionBreakMin(this,c<15?15:c<30?30:10); buildSettings(); });
        note("تشغيل اليوم: "+ScreenTracker.format(ScreenTracker.todayMs(this)));
    }

    private void pgApps(){
        Button ap=toggle("قفل التطبيقات ("+Prefs.appsBlocked(this).size()+")",Prefs.appsOn(this));
        ap.setOnClickListener(v->{ Prefs.setAppsOn(this,!Prefs.appsOn(this)); buildSettings(); });
        Button apPick=button("اختيار التطبيقات"); apPick.setOnClickListener(v->pickApps());
        Button apS=button("من:  "+time(Prefs.appsStartHour(this),Prefs.appsStartMinute(this)));
        apS.setOnClickListener(v->pickTime("بداية قفل التطبيقات",Prefs.appsStartHour(this),Prefs.appsStartMinute(this),(h,m)->{
            Prefs.setAppsWindow(this,h,m,Prefs.appsEndHour(this),Prefs.appsEndMinute(this)); buildSettings(); }));
        Button apE=button("إلى:  "+time(Prefs.appsEndHour(this),Prefs.appsEndMinute(this)));
        apE.setOnClickListener(v->pickTime("نهاية قفل التطبيقات",Prefs.appsEndHour(this),Prefs.appsEndMinute(this),(h,m)->{
            Prefs.setAppsWindow(this,Prefs.appsStartHour(this),Prefs.appsStartMinute(this),h,m); buildSettings(); }));
    }

    private void pgUpdate(){
        int rc=Prefs.updateReadyCode(this);
        if(rc>UpdateChecker.currentCode(this)){
            Button up=button("⬇ تثبيت التحديث "+Prefs.updateReadyName(this));
            up.setOnClickListener(v->{ String e=UpdateChecker.install(this); if(e!=null) Toast.makeText(this,e,Toast.LENGTH_LONG).show(); });
        }
        Button chk=button("🔄 فحص التحديثات الآن");
        chk.setOnClickListener(v->{ UpdateChecker.checkNow(this); Toast.makeText(this,"جارٍ الفحص… ارجع للإعدادات بعد دقيقة",Toast.LENGTH_LONG).show(); });
        note("الإصدار الحالي: "+currentVersionName()+"  |  تنبيهات تيليجرام وبقية الإعدادات من صفحة الموبايل");
    }

    private void pgProtect(){
        Button logBtn=button("📋 سجل الأحداث"); logBtn.setOnClickListener(v->showLogDialog());
        Button pin=button("تغيير PIN"); pin.setOnClickListener(v->changePin());
        Button msg=button("رسالة شاشة القفل"); msg.setOnClickListener(v->changeMessage());
        section2("إدارة التطبيق");
        TextView uninstallNote=note("لحذف TV Lock بشكل مقصود، استخدم هذا الزر وأدخل PIN الصحيح.");
        uninstallNote.setTextColor(Color.rgb(255,202,150));
        Button uninstall=button("🗑  حذف التطبيق");
        uninstall.setOnClickListener(v->{
            Intent i=new Intent(this,AdminGuardActivity.class);
            i.putExtra("uninstall",true);
            startActivity(i);
        });
    }

    private void pgPerms(){
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

    private String localIp(){
        try{
            for(java.util.Enumeration<java.net.NetworkInterface> e=java.net.NetworkInterface.getNetworkInterfaces();e!=null&&e.hasMoreElements();){
                for(java.util.Enumeration<java.net.InetAddress> a=e.nextElement().getInetAddresses();a.hasMoreElements();){
                    java.net.InetAddress x=a.nextElement();
                    if(x instanceof java.net.Inet4Address && x.isSiteLocalAddress()) return x.getHostAddress();
                }
            }
        }catch(Exception ignored){}
        return null;
    }

    private void showRemoteTimeDialog(boolean startTime,boolean tonight){
        int h,m;
        if(tonight){ int[] t=ScheduleUtil.effectiveStart(this,ScheduleUtil.tonightWindowDate(this)); h=t[0]; m=t[1]; }
        else { h=startTime?Prefs.startHour(this):Prefs.endHour(this); m=startTime?Prefs.startMinute(this):Prefs.endMinute(this); }
        String title=tonight?"وقت بدء القفل لهذه الليلة فقط":(startTime?"تغيير وقت بدء القفل":"تغيير وقت الفتح التلقائي");
        pickTime(title,h,m,(hh,mm)->{
            if(tonight){ ScheduleUtil.setTonightStart(this,hh,mm); buildSettings(); return; }
            if(startTime) Prefs.setStart(this,hh,mm); else Prefs.setEnd(this,hh,mm);
            ScheduleUtil.reschedule(this); LockOverlayService.sync(this); buildSettings();
        });
    }

    private interface TimeSaver { void save(int h,int m); }

    private String currentVersionName(){
        try{ return getPackageManager().getPackageInfo(getPackageName(),0).versionName; }catch(Exception e){ return "?"; }
    }

    /** Multi-choice dialog of launchable apps for the per-app lock. */
    private void pickApps(){
        android.content.pm.PackageManager pm=getPackageManager();
        java.util.LinkedHashMap<String,String> apps=new java.util.LinkedHashMap<>();
        for(String cat:new String[]{Intent.CATEGORY_LAUNCHER,Intent.CATEGORY_LEANBACK_LAUNCHER}){
            Intent q=new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for(android.content.pm.ResolveInfo r:pm.queryIntentActivities(q,0)){
                String pk=r.activityInfo.packageName;
                if(pk.equals(getPackageName()) || apps.containsKey(pk)) continue;
                apps.put(pk,String.valueOf(r.loadLabel(pm)));
            }
        }
        final String[] pkgs=apps.keySet().toArray(new String[0]);
        final String[] labels=new String[pkgs.length];
        final boolean[] on=new boolean[pkgs.length];
        java.util.Set<String> cur=Prefs.appsBlocked(this);
        for(int i=0;i<pkgs.length;i++){ labels[i]=apps.get(pkgs[i]); on[i]=cur.contains(pkgs[i]); }
        android.app.AlertDialog d=new android.app.AlertDialog.Builder(this)
                .setTitle("التطبيقات المحجوبة")
                .setMultiChoiceItems(labels,on,(dlg,i,checked)->on[i]=checked)
                .setPositiveButton("حفظ",(dlg,w)->{
                    java.util.Set<String> set=new java.util.HashSet<>();
                    for(int i=0;i<pkgs.length;i++) if(on[i]) set.add(pkgs[i]);
                    Prefs.setAppsBlocked(this,set); buildSettings();
                }).setNegativeButton("إلغاء",null).create();
        d.show();
    }

    private void editDayTimes(int iso,String name){
        final int sh0=Prefs.dayStartHour(this,iso), sm0=Prefs.dayStartMinute(this,iso);
        final int eh0=Prefs.dayEndHour(this,iso), em0=Prefs.dayEndMinute(this,iso);
        pickTime("بدء القفل — "+name,sh0,sm0,(sh,sm)->
            pickTime("الفتح التلقائي — "+name,eh0,em0,(eh,em)->{
                Prefs.setDayTimes(this,iso,sh,sm,eh,em);
                ScheduleUtil.reschedule(this); LockOverlayService.sync(this); buildSettings();
            }));
    }

    private void showLogDialog(){
        android.widget.ScrollView sv=new android.widget.ScrollView(this);
        sv.setFocusable(true);
        TextView t=text(EventLog.read(this),13,Color.WHITE);
        t.setPadding(dp(14),dp(10),dp(14),dp(10));
        t.setTextIsSelectable(false);
        sv.addView(t);
        android.app.AlertDialog d=new android.app.AlertDialog.Builder(this)
                .setTitle("سجل الأحداث").setView(sv).setPositiveButton("إغلاق",null).create();
        d.setOnShowListener(x->{
            if(d.getWindow()!=null){
                int w=getResources().getDisplayMetrics().widthPixels, h=getResources().getDisplayMetrics().heightPixels;
                d.getWindow().setLayout(Math.min(dp(700),(int)(w*.7f)),(int)(h*.8f));
            }
        });
        d.show();
    }

    private void pickTime(String title,int h0,int m0,TimeSaver saver){
        final int[] value={h0,m0};
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
                .setTitle(title)
                .setView(box)
                .setPositiveButton("حفظ",(d,w)->saver.save(value[0],value[1])).setNegativeButton("إلغاء",null).create();
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
        String s="الجدول "+(Prefs.enabled(this)?"يعمل":"متوقف")+"  •  "+time(ScheduleUtil.effectiveStart(this,ScheduleUtil.tonightWindowDate(this))[0],ScheduleUtil.effectiveStart(this,ScheduleUtil.tonightWindowDate(this))[1])+" ← "+time(ScheduleUtil.effectiveEnd(this,ScheduleUtil.tonightWindowDate(this))[0],ScheduleUtil.effectiveEnd(this,ScheduleUtil.tonightWindowDate(this))[1]);
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
        c.setBackground(roundBg(Theme.card(this,150),Theme.accent(this,150),14));
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
        t.setBackground(roundBg(Theme.card(this,150),Theme.accent(this,150),14));
        t.setPadding(dp(14),dp(7),dp(14),dp(7));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.min(dp(520),(int)(getResources().getDisplayMetrics().widthPixels*.45f)),dp(46));
        lp.setMargins(0,dp(3),0,dp(5)); root.addView(t,lp);
    }

    private TextView note(String s){
        TextView t=text(s,13,MUTED); t.setGravity(Gravity.CENTER); t.setPadding(dp(8),dp(2),dp(8),dp(8));
        root.addView(t,new LinearLayout.LayoutParams(Math.min(dp(700),(int)(getResources().getDisplayMetrics().widthPixels*.60f)),ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    // ---- collapsible sections (settings screen) ----
    private boolean accordion=false;
    private LinearLayout mainRoot;
    private static String openSec="الجدول اليومي";
    private final java.util.LinkedHashMap<String,LinearLayout> bodies=new java.util.LinkedHashMap<>();
    private final java.util.LinkedHashMap<String,Button> heads=new java.util.LinkedHashMap<>();

    private void section(String s){
        if(accordion){
            if(mainRoot==null) mainRoot=root;
            root=mainRoot;
            final boolean open=s.equals(openSec);
            final Button h=button((open?"▾  ":"◂  ")+s);
            h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            h.setTextColor(CYAN);
            LinearLayout body=new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL); body.setGravity(Gravity.CENTER_HORIZONTAL);
            body.setVisibility(open?View.VISIBLE:View.GONE);
            mainRoot.addView(body,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            bodies.put(s,body); heads.put(s,h);
            h.setOnClickListener(v->{
                boolean wasOpen=s.equals(openSec);
                openSec=wasOpen?"":s;
                for(java.util.Map.Entry<String,LinearLayout> e:bodies.entrySet()){
                    boolean o=e.getKey().equals(openSec);
                    e.getValue().setVisibility(o?View.VISIBLE:View.GONE);
                    heads.get(e.getKey()).setText((o?"▾  ":"◂  ")+e.getKey());
                }
            });
            root=body;
            return;
        }
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
        b.setBackground(Theme.focusBg(this,getResources().getDisplayMetrics().density));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.min(dp(500),(int)(getResources().getDisplayMetrics().widthPixels*.44f)),dp(46));
        lp.setMargins(0,dp(3),0,dp(3)); root.addView(b,lp); return b;
    }

    private Button buttonSmall(String s){
        Button b=new Button(this);
        b.setText(s); b.setTextSize(10); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setFocusable(true);
        b.setMinHeight(0); b.setMinWidth(0); b.setPadding(0,0,0,0);
        b.setBackground(Theme.focusBg(this,getResources().getDisplayMetrics().density));
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
        b.setMinHeight(0); b.setPadding(0,0,0,0); b.setBackground(Theme.focusBg(this,getResources().getDisplayMetrics().density)); return b;
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
