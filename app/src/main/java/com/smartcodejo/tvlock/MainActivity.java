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
    private int TEXT=Color.rgb(244,248,255);
    private int MUTED=Color.rgb(200,212,236);
    private boolean LIGHT=false;
    private static final int LBLUE=Color.rgb(76,110,245);

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

    private void applyTheme(){
        LIGHT=Prefs.pinStyle(this)==1;
        if(LIGHT){ TEXT=Color.rgb(34,40,58); MUTED=Color.rgb(100,108,130); CYAN=LBLUE; BLUE=LBLUE; }
        else { TEXT=Color.rgb(244,248,255); MUTED=Color.rgb(200,212,236); CYAN=Color.rgb(158,197,255); BLUE=Color.WHITE; }
    }

    private void baseRoot(){
        applyTheme();
        FrameLayout frame=new FrameLayout(this);
        frame.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        frame.addView(new PinBackgroundView(this,LIGHT),new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        int sw=getResources().getDisplayMetrics().widthPixels;
        if(LIGHT){
            root.setPadding(dp(26),dp(22),dp(26),dp(10));
            FrameLayout card=new FrameLayout(this);
            GradientDrawable cb=new GradientDrawable(); cb.setColor(Color.WHITE); cb.setCornerRadius(dp(28));
            card.setBackground(cb); card.setClipToOutline(true); card.setElevation(dp(14));
            card.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
            FrameLayout.LayoutParams cl=new FrameLayout.LayoutParams(-1,-1);
            int mx=(int)(sw*.06f); cl.setMargins(mx,dp(26),mx,dp(34));
            frame.addView(card,cl);
        } else {
            int sx=(int)(sw*.07f);
            root.setPadding(sx,dp(28),sx,dp(10));
            frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        }
        TextView brand=text("HKS  ·  hksalameh",10,LIGHT?Color.argb(190,255,255,255):Color.argb(115,255,255,255));
        brand.setLetterSpacing(.3f); brand.setGravity(Gravity.CENTER);
        frame.addView(brand,new FrameLayout.LayoutParams(-1,dp(30),Gravity.BOTTOM));
        setContentView(frame);
    }

    /** في الشكل الفاتح: كل الأزرار رمادي فاتح ونص داكن، وعند التحديد أزرق ونص أبيض. */
    private void lightFix(View v){
        if(!LIGHT || v==null) return;
        if(v instanceof Button && !"keep".equals(v.getTag())){
            Button b=(Button)v;
            b.setBackground(keyBg(true,getResources().getDisplayMetrics().density));
            b.setTextColor(selColors(TEXT));
        }
        if(v instanceof ViewGroup){ ViewGroup g=(ViewGroup)v; for(int i=0;i<g.getChildCount();i++) lightFix(g.getChildAt(i)); }
    }

    private android.content.res.ColorStateList selColors(int normal){
        return new android.content.res.ColorStateList(
            new int[][]{{android.R.attr.state_focused},{android.R.attr.state_pressed},{}},
            new int[]{Color.WHITE,Color.WHITE,normal});
    }

    private android.graphics.drawable.StateListDrawable rowBg(int radius){
        GradientDrawable flat=new GradientDrawable(), foc=new GradientDrawable();
        flat.setCornerRadius(dp(radius)); foc.setCornerRadius(dp(radius));
        if(LIGHT){ flat.setColor(Color.rgb(238,241,248)); foc.setColor(LBLUE); }
        else {
            flat.setColor(Color.argb(20,255,255,255)); flat.setStroke(dp(1),Color.argb(38,255,255,255));
            foc.setColor(Color.argb(70,255,255,255)); foc.setStroke(dp(2),Color.WHITE);
        }
        android.graphics.drawable.StateListDrawable sl=new android.graphics.drawable.StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused},foc);
        sl.addState(new int[]{android.R.attr.state_pressed},foc);
        sl.addState(new int[]{},flat);
        return sl;
    }

    private void showFirstSetup(){
        authenticated=false; inSettings=false;
        pinScreen("أنشئ رمز الحماية (4 إلى 8 أرقام)",x->{
            if(!x.matches("\\d{4,8}")){ toast("الـ PIN يجب أن يكون من 4 إلى 8 أرقام"); return false; }
            Prefs.setPin(this,x); Prefs.setEnabled(this,true); ScheduleUtil.reschedule(this);
            authenticated=true; buildDashboard(); requestDeviceAdmin(); return true;
        });
    }

    private void showPinGate(){
        authenticated=false; inSettings=false;
        pinScreen("أدخل الرمز",x->{
            if(Prefs.checkPin(this,x)){ authenticated=true; buildDashboard(); return true; }
            EventLog.add(this,"PIN غلط في لوحة التحكم"); toast("الرمز غير صحيح"); return false;
        });
    }

    private interface PinCheck{ boolean check(String pin); }
    private MaskedPinEditText activePin;

    /** شاشة الرمز: النمط 0 = A (زجاجي داكن، أزرار دائرية) ، 1 = C (بطاقة فاتحة). */
    private void pinScreen(String title,PinCheck checker){
        applyTheme();
        boolean light=Prefs.pinStyle(this)==1;
        float dn=getResources().getDisplayMetrics().density;
        FrameLayout frame=new FrameLayout(this);
        frame.addView(new PinBackgroundView(this,light),new FrameLayout.LayoutParams(-1,-1));

        int fg=light?Color.rgb(34,40,58):Color.WHITE;
        int accent=light?Color.rgb(76,110,245):Color.WHITE;

        // ===== لوحة المعلومات (ساعة/تاريخ/حالة) =====
        LinearLayout info=new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER);
        android.widget.TextClock clock=new android.widget.TextClock(this);
        clock.setFormat12Hour("h:mm"); clock.setFormat24Hour("h:mm");
        clock.setTextSize(light?62:96); clock.setTextColor(Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-thin",Typeface.NORMAL)); clock.setIncludeFontPadding(false);
        info.addView(clock,new LinearLayout.LayoutParams(-2,-2));
        TextView date=text(new java.text.SimpleDateFormat("EEEE d MMMM",java.util.Locale.forLanguageTag("ar-u-nu-latn")).format(new java.util.Date()),light?14:17,Color.argb(215,255,255,255));
        info.addView(date,new LinearLayout.LayoutParams(-2,-2));
        boolean locked=ScheduleUtil.shouldLock(this);
        String st=locked?"🔒  التلفزيون مقفل":"🛡  لوحة التحكم محمية";
        long end=locked?ScheduleUtil.currentWindowEnd(this):0;
        if(end>System.currentTimeMillis()){
            java.util.Calendar cal=java.util.Calendar.getInstance(); cal.setTimeInMillis(end);
            st+="  ·  يفتح "+time(cal.get(java.util.Calendar.HOUR_OF_DAY),cal.get(java.util.Calendar.MINUTE));
        }
        TextView stv=text(st,light?13:16,Color.WHITE);
        stv.setGravity(Gravity.CENTER);
        if(light){ stv.setBackground(roundBg(Color.argb(60,255,255,255),Color.TRANSPARENT,20)); stv.setPadding(dp(16),dp(6),dp(16),dp(6)); }
        LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-2,-2); sl.setMargins(0,dp(light?22:28),0,0);
        info.addView(stv,sl);

        // ===== لوحة الأرقام =====
        LinearLayout pad=new LinearLayout(this); pad.setOrientation(LinearLayout.VERTICAL); pad.setGravity(Gravity.CENTER);
        TextView tt=text(title,light?19:15,light?fg:Color.argb(210,255,255,255)); tt.setGravity(Gravity.CENTER);
        if(light) tt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        pad.addView(tt,new LinearLayout.LayoutParams(-2,-2));
        MaskedPinEditText pin=new MaskedPinEditText(this);
        activePin=pin;
        pin.setMaskColor(accent); pin.setTextSize(26); pin.setGravity(Gravity.CENTER); pin.setBackground(null);
        pin.setSingleLine(true); pin.setImeOptions(EditorInfo.IME_ACTION_DONE); pin.setPadding(0,0,0,0);
        pin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        pad.addView(pin,new LinearLayout.LayoutParams(dp(260),dp(40)));
        Runnable submit=()->{
            String x=pin.getText().toString().trim();
            if(x.isEmpty()) return;
            hideKeyboard(pin);
            if(!checker.check(x)) pin.setText("");
        };
        pin.setOnEditorActionListener((v,id,e)->{ if(id==EditorInfo.IME_ACTION_DONE){ submit.run(); return true; } return false; });

        int kw=light?dp(78):dp(64), kh=light?dp(50):dp(64);
        String[][] rows={{"1","2","3"},{"4","5","6"},{"7","8","9"},{"⌫","0","✓"}};
        Button first=null;
        for(String[] r:rows){
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            for(String k:r){
                Button b=new Button(this);
                b.setText(k); b.setTextSize(k.equals("⌫")||k.equals("✓")?20:24); b.setAllCaps(false);
                b.setTypeface(light?Typeface.DEFAULT_BOLD:Typeface.create("sans-serif-light",Typeface.NORMAL));
                b.setFocusable(true); b.setMinHeight(0); b.setMinWidth(0); b.setPadding(0,0,0,0);
                b.setStateListAnimator(null);
                b.setBackground(keyBg(light,dn));
                b.setTextColor(new android.content.res.ColorStateList(
                        new int[][]{{android.R.attr.state_focused},{}},
                        new int[]{light?Color.WHITE:Color.WHITE, light?fg:Color.WHITE}));
                b.setOnClickListener(v->{
                    String cur=pin.getText().toString();
                    if(k.equals("⌫")){ if(!cur.isEmpty()) pin.setText(cur.substring(0,cur.length()-1)); }
                    else if(k.equals("✓")) submit.run();
                    else if(cur.length()<8) pin.setText(cur+k);
                });
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(kw,kh);
                int g=light?dp(5):dp(9); lp.setMargins(g,light?dp(5):dp(6),g,light?dp(5):dp(6));
                row.addView(b,lp);
                if(first==null && k.equals("5")) first=b;
            }
            pad.addView(row,new LinearLayout.LayoutParams(-2,-2));
        }

        if(light){
            // بطاقة بيضاء: يسار بنفسجي للمعلومات + يمين أبيض للأرقام
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL);
            card.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            GradientDrawable cb=new GradientDrawable(); cb.setColor(Color.WHITE); cb.setCornerRadius(dp(28));
            card.setBackground(cb); card.setClipToOutline(true); card.setElevation(dp(16));
            GradientDrawable lb=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(76,110,245),Color.rgb(112,72,232)});
            float rr=dp(28); lb.setCornerRadii(new float[]{rr,rr,0,0,0,0,rr,rr});
            LinearLayout left=new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL); left.setGravity(Gravity.CENTER);
            left.setBackground(lb);
            left.addView(info,new LinearLayout.LayoutParams(-2,0,1f));
            TextView brand=text("HKS  ·  hksalameh",10,Color.argb(160,255,255,255)); brand.setLetterSpacing(.25f); brand.setGravity(Gravity.CENTER);
            left.addView(brand,new LinearLayout.LayoutParams(-1,dp(34)));
            card.addView(left,new LinearLayout.LayoutParams(dp(290),-1));
            card.addView(pad,new LinearLayout.LayoutParams(0,-1,1f));
            FrameLayout.LayoutParams cl=new FrameLayout.LayoutParams(dp(640),dp(400),Gravity.CENTER);
            frame.addView(card,cl);
        } else {
            LinearLayout split=new LinearLayout(this); split.setOrientation(LinearLayout.HORIZONTAL);
            split.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); split.setGravity(Gravity.CENTER_VERTICAL);
            int side=(int)(getResources().getDisplayMetrics().widthPixels*.09f);
            split.setPadding(side,0,side,0);
            split.addView(pad,new LinearLayout.LayoutParams(0,-2,1f));
            split.addView(info,new LinearLayout.LayoutParams(0,-2,1f));
            frame.addView(split,new FrameLayout.LayoutParams(-1,-1));
            TextView brand=text("HKS  ·  hksalameh",10,Color.argb(115,255,255,255)); brand.setLetterSpacing(.3f); brand.setGravity(Gravity.CENTER);
            frame.addView(brand,new FrameLayout.LayoutParams(-1,dp(30),Gravity.BOTTOM));
        }
        setContentView(frame);
        root=null;
        final Button f0=first; if(f0!=null) f0.post(f0::requestFocus);
    }

    private android.graphics.drawable.StateListDrawable keyBg(boolean light,float dn){
        android.graphics.drawable.StateListDrawable sd=new android.graphics.drawable.StateListDrawable();
        GradientDrawable on=new GradientDrawable(), off=new GradientDrawable();
        if(light){
            on.setCornerRadius(14*dn); off.setCornerRadius(14*dn);
            on.setColor(Color.rgb(76,110,245)); off.setColor(Color.rgb(238,241,248));
        } else {
            on.setShape(GradientDrawable.OVAL); off.setShape(GradientDrawable.OVAL);
            on.setColor(Color.argb(80,255,255,255)); on.setStroke((int)(2*dn),Color.WHITE);
            off.setColor(Color.argb(22,255,255,255)); off.setStroke((int)(1.3f*dn),Color.argb(60,255,255,255));
        }
        sd.addState(new int[]{android.R.attr.state_focused},on);
        sd.addState(new int[]{android.R.attr.state_pressed},on);
        sd.addState(new int[]{},off);
        return sd;
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent e){
        // أرقام من ريموت فيه أرقام أو من لوحة مفاتيح الموبايل
        if(!authenticated && activePin!=null && activePin.isAttachedToWindow() && e.getAction()==android.view.KeyEvent.ACTION_DOWN && !activePin.hasFocus()){
            int k=e.getKeyCode();
            if(k>=android.view.KeyEvent.KEYCODE_0 && k<=android.view.KeyEvent.KEYCODE_9){
                String cur=activePin.getText().toString();
                if(cur.length()<8) activePin.setText(cur+(k-android.view.KeyEvent.KEYCODE_0));
                return true;
            }
            if(k==android.view.KeyEvent.KEYCODE_DEL){
                String cur=activePin.getText().toString();
                if(!cur.isEmpty()) activePin.setText(cur.substring(0,cur.length()-1));
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private String fmtMs(long ms){
        java.time.ZonedDateTime z=java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.ofHours(3));
        return time(z.getHour(),z.getMinute());
    }

    private void buildDashboard(){
        authenticated=true; inSettings=false; page=null; hideKeyboard(null); applyTheme();
        float dn=getResources().getDisplayMetrics().density;
        FrameLayout frame=new FrameLayout(this);
        frame.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        frame.addView(new PinBackgroundView(this,LIGHT),new FrameLayout.LayoutParams(-1,-1));

        // الساعة والحالة
        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.VERTICAL); top.setGravity(Gravity.CENTER);
        android.widget.TextClock clock=new android.widget.TextClock(this);
        clock.setFormat12Hour("h:mm"); clock.setFormat24Hour("h:mm");
        clock.setTextColor(Color.WHITE); clock.setTextSize(LIGHT?72:110);
        clock.setTypeface(Typeface.create(LIGHT?"sans-serif-light":"sans-serif-thin",Typeface.NORMAL));
        clock.setGravity(Gravity.CENTER); clock.setIncludeFontPadding(false);
        top.addView(clock,new LinearLayout.LayoutParams(-2,-2));
        TextView date=text(new java.text.SimpleDateFormat("EEEE d MMMM",java.util.Locale.forLanguageTag("ar-u-nu-latn")).format(new java.util.Date()),LIGHT?15:18,Color.argb(215,255,255,255));
        top.addView(date,new LinearLayout.LayoutParams(-2,-2));

        boolean locked=ScheduleUtil.shouldLock(this);
        boolean temp=!locked && Prefs.bypassUntil(this)>System.currentTimeMillis();
        int[] st=ScheduleUtil.effectiveStart(this,ScheduleUtil.tonightWindowDate(this));
        String pill;
        if(locked) pill="●  مقفول  ·  يُفتح "+fmtMs(ScheduleUtil.currentWindowEnd(this));
        else if(!Prefs.enabled(this)) pill="○  الجدول متوقف";
        else if(temp) pill="●  فتح مؤقت حتى "+fmtMs(Prefs.bypassUntil(this));
        else pill="●  مفتوح  ·  يقفل "+time(st[0],st[1]);
        TextView pl=text(pill,LIGHT?14:16,Color.WHITE); pl.setGravity(Gravity.CENTER);
        pl.setPadding(dp(20),dp(7),dp(20),dp(7));
        pl.setBackground(roundBg(Color.argb(LIGHT?50:38,255,255,255),Color.TRANSPARENT,22));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,-2); pp.setMargins(0,dp(16),0,0);
        top.addView(pl,pp);
        status=null;

        // الأزرار
        LinearLayout bar=new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER);
        Button lockNow=dashItem(bar,"lock","قفل الآن",dn);
        lockNow.setOnClickListener(v->{
            Prefs.setBypassUntil(this,0);
            Intent i=new Intent(this,LockOverlayService.class).putExtra("force",true);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        });
        dashItem(bar,"timer","فتح مؤقت",dn).setOnClickListener(v->showTempUnlockDialog());
        dashItem(bar,Prefs.enabled(this)?"pause":"play",Prefs.enabled(this)?"إيقاف الجدول":"تشغيل الجدول",dn).setOnClickListener(v->{
            Prefs.setEnabled(this,!Prefs.enabled(this));
            Prefs.setBypassUntil(this,0);
            ScheduleUtil.reschedule(this);
            LockOverlayService.sync(this);
            buildDashboard();
        });
        dashItem(bar,"gear","الإعدادات",dn).setOnClickListener(v->{ inSettings=true; page="sched"; buildSettings(); });

        TextView warn=null;
        if(!Settings.canDrawOverlays(this) || !isAccessibilityEnabled() || !isDeviceAdminActive()){
            warn=text("⚠  بعض صلاحيات الحماية غير مفعّلة — الإعدادات ← الصلاحيات",13,LIGHT?Color.rgb(200,110,20):Color.rgb(255,202,120));
            warn.setGravity(Gravity.CENTER);
        }

        if(LIGHT){
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable cb=new GradientDrawable(); cb.setColor(Color.WHITE); cb.setCornerRadius(dp(28));
            card.setBackground(cb); card.setClipToOutline(true); card.setElevation(dp(16));
            GradientDrawable hb=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{LBLUE,Color.rgb(112,72,232)});
            float rr=dp(28); hb.setCornerRadii(new float[]{rr,rr,rr,rr,0,0,0,0});
            top.setBackground(hb);
            card.addView(top,new LinearLayout.LayoutParams(-1,0,1f));
            LinearLayout bottom=new LinearLayout(this); bottom.setOrientation(LinearLayout.VERTICAL); bottom.setGravity(Gravity.CENTER);
            bottom.addView(bar,new LinearLayout.LayoutParams(-2,-2));
            if(warn!=null){ LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(-2,-2); wl.setMargins(0,dp(10),0,0); bottom.addView(warn,wl); }
            card.addView(bottom,new LinearLayout.LayoutParams(-1,0,1f));
            int sw=getResources().getDisplayMetrics().widthPixels;
            frame.addView(card,new FrameLayout.LayoutParams(Math.min(dp(760),(int)(sw*.72f)),dp(430),Gravity.CENTER));
        } else {
            LinearLayout col=new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(new View(this),new LinearLayout.LayoutParams(1,0,1.5f));
            col.addView(top,new LinearLayout.LayoutParams(-1,-2));
            col.addView(new View(this),new LinearLayout.LayoutParams(1,0,1f));
            if(warn!=null){ LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(-2,-2); wl.setMargins(0,0,0,dp(12)); col.addView(warn,wl); }
            col.addView(bar,new LinearLayout.LayoutParams(-2,-2));
            col.setPadding(0,0,0,dp(46));
            frame.addView(col,new FrameLayout.LayoutParams(-1,-1));
        }
        TextView brand=text("HKS  ·  hksalameh",10,LIGHT?Color.argb(190,255,255,255):Color.argb(115,255,255,255));
        brand.setLetterSpacing(.3f); brand.setGravity(Gravity.CENTER);
        frame.addView(brand,new FrameLayout.LayoutParams(-1,dp(30),Gravity.BOTTOM));
        setContentView(frame);
        root=null;
        lockNow.post(lockNow::requestFocus);
    }

    private Button dashItem(LinearLayout bar,String icon,String label,float dn){
        LinearLayout it=new LinearLayout(this); it.setOrientation(LinearLayout.VERTICAL); it.setGravity(Gravity.CENTER_HORIZONTAL);
        Button b=new Button(this);
        b.setText(""); b.setFocusable(true);
        b.setForeground(new LineIcon(icon,LIGHT?TEXT:Color.WHITE,Color.WHITE));
        b.setMinHeight(0); b.setMinWidth(0); b.setPadding(0,0,0,0); b.setStateListAnimator(null);
        b.setTextColor(LIGHT?selColors(TEXT):android.content.res.ColorStateList.valueOf(Color.WHITE));
        b.setBackground(keyBg(LIGHT,dn));
        int sz=LIGHT?dp(70):dp(74);
        it.addView(b,new LinearLayout.LayoutParams(sz,sz));
        TextView l=text(label,14,LIGHT?TEXT:Color.argb(220,255,255,255)); l.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ll=new LinearLayout.LayoutParams(-2,-2); ll.setMargins(0,dp(8),0,0);
        it.addView(l,ll);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(100),-2); ip.setMargins(dp(2),0,dp(2),0);
        bar.addView(it,ip);
        return b;
    }

    private String page=null;

    private void section2(String s){
        TextView t=text(s,LIGHT?22:24,TEXT);
        t.setTypeface(LIGHT?Typeface.DEFAULT_BOLD:Typeface.create("sans-serif-light",Typeface.NORMAL)); t.setGravity(Gravity.START);
        t.setPadding(dp(4),0,dp(4),dp(6));
        root.addView(t,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private ScrollView contentSv;
    private String lastPage=null;

    private void buildSettings(){
        authenticated=true; inSettings=true; hideKeyboard(null); applyTheme();
        if(page==null) page="sched";
        // نحفظ مكان التمرير والعنصر المحدد حتى لا يقفز المؤشر بعد كل تغيير
        int keepScroll=0; String keepFocus=null;
        if(page.equals(lastPage)){
            if(contentSv!=null) keepScroll=contentSv.getScrollY();
            View cf=getCurrentFocus();
            if(cf!=null && cf.getTag() instanceof String && ((String)cf.getTag()).startsWith("r:")) keepFocus=(String)cf.getTag();
        }
        lastPage=page;

        FrameLayout frame=new FrameLayout(this);
        frame.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        frame.addView(new PinBackgroundView(this,LIGHT),new FrameLayout.LayoutParams(-1,-1));

        LinearLayout split=new LinearLayout(this);
        split.setOrientation(LinearLayout.HORIZONTAL);
        if(LIGHT){
            GradientDrawable cb=new GradientDrawable(); cb.setColor(Color.WHITE); cb.setCornerRadius(dp(28));
            split.setBackground(cb); split.setClipToOutline(true); split.setElevation(dp(14));
        }

        // القائمة الجانبية (ثابتة)
        LinearLayout menu=new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(10),dp(12),dp(10),dp(12));
        if(LIGHT) menu.setBackgroundColor(Color.rgb(244,245,251));
        else menu.setBackground(roundBg(Color.argb(16,255,255,255),Color.argb(30,255,255,255),22));
        String[][] items={
            {"sched","🕒","الجدول الليلي"},{"exam","📚","الامتحانات"},{"session","⏸","حد التشغيل"},
            {"apps","📱","قفل التطبيقات"},{"protect","🔐","الحماية"},{"perms","✅","الصلاحيات"},
            {"theme","🎨","المظهر"},{"update","⬇","التحديث"}};
        Button back=menuItem("→","العودة",false);
        back.setOnClickListener(v->{ inSettings=false; page=null; lastPage=null; buildDashboard(); });
        menu.addView(back,menuLp());
        Button selected=null;
        for(String[] it:items){
            final String target=it[0];
            boolean sel=target.equals(page);
            Button m=menuItem(it[1],it[2],sel);
            m.setOnClickListener(v->{ if(!target.equals(page)){ page=target; buildSettings(); } });
            menu.addView(m,menuLp());
            if(sel) selected=m;
        }
        split.addView(menu,new LinearLayout.LayoutParams(dp(230),-1));

        // المحتوى (يتحرك لوحده)
        contentSv=new ScrollView(this);
        contentSv.setVerticalScrollBarEnabled(false);
        contentSv.setFillViewport(true);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(LIGHT?26:24),dp(LIGHT?20:6),dp(LIGHT?26:8),dp(16));
        contentSv.addView(content,new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(0,-1,1f);
        split.addView(contentSv,cl);

        int sw=getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams sl=new FrameLayout.LayoutParams(-1,-1);
        int mx=(int)(sw*(LIGHT?.06f:.06f));
        sl.setMargins(mx,dp(LIGHT?26:30),mx,dp(34));
        frame.addView(split,sl);
        TextView brand=text("HKS  ·  hksalameh",10,LIGHT?Color.argb(190,255,255,255):Color.argb(115,255,255,255));
        brand.setLetterSpacing(.3f); brand.setGravity(Gravity.CENTER);
        frame.addView(brand,new FrameLayout.LayoutParams(-1,dp(30),Gravity.BOTTOM));
        setContentView(frame);

        root=content;
        switch(page){
            case "sched": section2("الجدول الليلي"); pgSchedule(); break;
            case "exam": section2("وضع الامتحانات"); pgExam(); break;
            case "session": section2("حد التشغيل المتواصل"); pgSession(); break;
            case "apps": section2("قفل التطبيقات"); pgApps(); break;
            case "update": section2("التحديث"); pgUpdate(); break;
            case "protect": section2("الحماية"); pgProtect(); break;
            case "perms": section2("الصلاحيات"); pgPerms(); break;
            case "theme": section2("المظهر"); pgTheme(); break;
            default: break;
        }
        lightFix(content);

        View target=keepFocus!=null?content.findViewWithTag(keepFocus):null;
        final View focusMe=target!=null?target:selected;
        final int sy=keepScroll;
        contentSv.post(()->{ contentSv.scrollTo(0,sy); if(focusMe!=null) focusMe.requestFocus(); });
    }

    // ---------- قوائم منسدلة ----------
    private static final java.util.Set<String> openFolds=new java.util.HashSet<>(java.util.Arrays.asList("times"));

    /** عنوان قابل للفتح والإغلاق؛ يرجع الجسم الذي تضاف إليه العناصر. */
    private LinearLayout fold(String key,String title,String summary){
        final boolean[] open={openFolds.contains(key)};
        LinearLayout h=row((open[0]?"▾   ":"◂   ")+title,summary,null);
        h.setTag("r:f:"+key);
        TextView lb=(TextView)h.getChildAt(0);
        lb.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14),dp(2),0,dp(6));
        body.setVisibility(open[0]?View.VISIBLE:View.GONE);
        root.addView(body,new LinearLayout.LayoutParams(-1,-2));
        h.setOnClickListener(v->{
            open[0]=!open[0];
            if(open[0]) openFolds.add(key); else openFolds.remove(key);
            body.setVisibility(open[0]?View.VISIBLE:View.GONE);
            lb.setText((open[0]?"▾   ":"◂   ")+title);
        });
        return body;
    }

    private void into(LinearLayout body,Runnable r){ LinearLayout old=root; root=body; r.run(); root=old; }

    private LinearLayout.LayoutParams menuLp(){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(40));
        lp.setMargins(0,dp(2),0,dp(2)); return lp;
    }

    private Button menuItem(String icon,String label,boolean sel){
        Button b=new Button(this);
        b.setTag("keep");
        b.setText(icon+"   "+label); b.setTextSize(15); b.setAllCaps(false); b.setFocusable(true);
        b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START); b.setStateListAnimator(null);
        b.setMinHeight(0); b.setMinWidth(0); b.setPadding(dp(14),0,dp(14),0);
        GradientDrawable on=new GradientDrawable(), focused=new GradientDrawable(), flat=new GradientDrawable();
        for(GradientDrawable g:new GradientDrawable[]{on,focused,flat}) g.setCornerRadius(dp(14));
        flat.setColor(Color.TRANSPARENT);
        if(LIGHT){ on.setColor(LBLUE); focused.setColor(Color.rgb(98,128,250)); focused.setStroke(dp(2),Color.rgb(40,60,180)); }
        else { on.setColor(Color.argb(55,255,255,255)); on.setStroke(dp(1),Color.argb(120,255,255,255));
               focused.setColor(Color.argb(80,255,255,255)); focused.setStroke(dp(2),Color.WHITE); }
        android.graphics.drawable.StateListDrawable sl=new android.graphics.drawable.StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused},focused);
        sl.addState(new int[]{},sel?on:flat);
        b.setBackground(sl);
        int normal=sel?(LIGHT?Color.WHITE:Color.WHITE):(LIGHT?Color.rgb(74,80,104):Color.argb(200,255,255,255));
        b.setTextColor(selColors(normal));
        return b;
    }

    // ---------- compact settings rows ----------
    private LinearLayout row(String label,String value,View.OnClickListener l){
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true); r.setClickable(true); r.setTag("r:"+label);
        r.setPadding(dp(18),0,dp(18),0);
        r.setBackground(rowBg(14));
        TextView lb=text(label,16,TEXT); lb.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        lb.setDuplicateParentStateEnabled(true); if(LIGHT) lb.setTextColor(selColors(TEXT));
        r.addView(lb,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        if(value!=null && !value.isEmpty()){
            TextView vl=text(value,15,CYAN); vl.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
            vl.setDuplicateParentStateEnabled(true); vl.setTextColor(selColors(CYAN));
            r.addView(vl,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if(l!=null) r.setOnClickListener(l);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));
        lp.setMargins(0,dp(4),0,dp(4));
        root.addView(r,lp);
        return r;
    }

    private LinearLayout switchRow(String label,boolean on,View.OnClickListener l){
        LinearLayout r=row(label,on?"●  شغّال":"○  موقوف",l);
        TextView v=(TextView)r.getChildAt(1);
        v.setTextColor(selColors(on?(LIGHT?Color.rgb(30,150,70):Color.rgb(140,240,160)):MUTED));
        return r;
    }

    private void group(String title){
        TextView t=text(title,13,MUTED); t.setGravity(Gravity.START);
        t.setPadding(dp(6),dp(12),dp(6),dp(2));
        root.addView(t,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void pgTheme(){
        group("شكل البرنامج — يتغيّر شاشة الرمز والرئيسية والإعدادات معاً");
        String[] ps={"🌌   داكن زجاجي","☀️   بطاقة فاتحة"};
        for(int i=0;i<ps.length;i++){
            final int k=i;
            row(ps[i],Prefs.pinStyle(this)==i?"✓":"",v->{ Prefs.setPinStyle(this,k); buildSettings(); });
        }
    }

    private void pgSchedule(){
        switchRow("الجدول الليلي",Prefs.enabled(this),v->{
            Prefs.setEnabled(this,!Prefs.enabled(this));
            Prefs.setBypassUntil(this,0);
            ScheduleUtil.reschedule(this);
            LockOverlayService.sync(this);
            buildSettings();
        });

        java.time.LocalDate tw=ScheduleUtil.tonightWindowDate(this);
        boolean tonightSet=tw.toString().equals(Prefs.tonightDate(this));
        int[] es=ScheduleUtil.effectiveStart(this,tw);
        LinearLayout times=fold("times","أوقات القفل",time(Prefs.startHour(this),Prefs.startMinute(this))+"  ←  "+time(Prefs.endHour(this),Prefs.endMinute(this)));
        into(times,()->{
            row("بدء القفل",time(Prefs.startHour(this),Prefs.startMinute(this)),v->showRemoteTimeDialog(true,false));
            row("الفتح التلقائي",time(Prefs.endHour(this),Prefs.endMinute(this)),v->showRemoteTimeDialog(false,false));
            row("🌙  الليلة فقط",time(es[0],es[1])+(tonightSet?"  (معدّل)":""),v->showRemoteTimeDialog(true,true));
            if(tonightSet) row("إلغاء تعديل الليلة","",v->{ ScheduleUtil.clearTonightStart(this); buildSettings(); });
        });

        int onCount=0, custom=0;
        for(int i=1;i<=7;i++){ if(Prefs.dayEnabled(this,i)) onCount++; if(Prefs.hasDayTimes(this,i)) custom++; }
        String sum=(onCount==7?"كل الأيام":onCount+" أيام")+(custom>0?"  ·  "+custom+" مخصّص":"");
        LinearLayout days=fold("days","أيام الأسبوع",sum);
        into(days,()->{
            TextView hint=text("اضغط على اليوم لتشغيله أو إيقافه أو تغيير وقته",12,MUTED);
            hint.setPadding(dp(4),dp(4),dp(4),dp(6));
            root.addView(hint,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout grid=new LinearLayout(this); grid.setOrientation(LinearLayout.HORIZONTAL);
            int[] order={6,7,1,2,3,4,5};
            String[] names={"","الاثنين","الثلاثاء","الأربعاء","الخميس","الجمعة","السبت","الأحد"};
            for(int iso:order) grid.addView(dayTile(iso,names[iso]),dayTileLp());
            root.addView(grid,new LinearLayout.LayoutParams(-1,-2));
            boolean anyCustom=false; for(int i=1;i<=7;i++) if(Prefs.hasDayTimes(this,i)) anyCustom=true;
            if(anyCustom){
                LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-1,dp(6));
                root.addView(new View(this),gap);
                row("إرجاع كل الأيام للوقت العام","",v->{ Prefs.clearAllDayTimes(this); ScheduleUtil.reschedule(this); LockOverlayService.sync(this); buildSettings(); });
            }
        });

        String ip=localIp();
        if(ip!=null){
            TextView n=text("📱  التحكم من الموبايل:  http://"+ip+":"+PhoneUnlockServer.PORT,12,MUTED);
            n.setPadding(dp(4),dp(12),dp(4),0);
            root.addView(n,new LinearLayout.LayoutParams(-1,-2));
        }
    }

    private LinearLayout.LayoutParams dayTileLp(){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(96),1f); lp.setMargins(dp(3),dp(2),dp(3),dp(2)); return lp;
    }

    private LinearLayout dayTile(int iso,String name){
        boolean on=Prefs.dayEnabled(this,iso), custom=Prefs.hasDayTimes(this,iso);
        LinearLayout t=new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL); t.setGravity(Gravity.CENTER);
        t.setFocusable(true); t.setClickable(true); t.setTag("r:d"+iso);
        GradientDrawable n=new GradientDrawable(), f=new GradientDrawable();
        n.setCornerRadius(dp(14)); f.setCornerRadius(dp(14));
        if(LIGHT){ n.setColor(on?Color.rgb(228,233,255):Color.rgb(244,245,250)); if(custom) n.setStroke(dp(2),LBLUE); f.setColor(LBLUE); }
        else { n.setColor(Color.argb(on?40:10,255,255,255)); n.setStroke(dp(custom?2:1),custom?Color.argb(200,158,197,255):Color.argb(on?70:25,255,255,255));
               f.setColor(Color.argb(90,255,255,255)); f.setStroke(dp(2),Color.WHITE); }
        android.graphics.drawable.StateListDrawable sl=new android.graphics.drawable.StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused},f); sl.addState(new int[]{android.R.attr.state_pressed},f); sl.addState(new int[]{},n);
        t.setBackground(sl);
        int main=on?TEXT:MUTED;
        TextView nm=text(name,14,main); nm.setTypeface(Typeface.DEFAULT_BOLD);
        t.addView(dup(nm,main),new LinearLayout.LayoutParams(-2,-2));
        if(on){
            TextView a=text(time(Prefs.dayStartHour(this,iso),Prefs.dayStartMinute(this,iso)),12,on?CYAN:MUTED);
            TextView arrow=text("↓",10,MUTED);
            TextView b=text(time(Prefs.dayEndHour(this,iso),Prefs.dayEndMinute(this,iso)),12,on?CYAN:MUTED);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2); tp.setMargins(0,dp(4),0,0);
            t.addView(dup(a,CYAN),tp); t.addView(dup(arrow,MUTED),new LinearLayout.LayoutParams(-2,-2)); t.addView(dup(b,CYAN),new LinearLayout.LayoutParams(-2,-2));
        } else {
            TextView off=text("بدون قفل",11,MUTED);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2); tp.setMargins(0,dp(8),0,0);
            t.addView(dup(off,MUTED),tp);
        }
        t.setOnClickListener(v->dayDialog(iso,name));
        return t;
    }

    private TextView dup(TextView t,int normal){ t.setGravity(Gravity.CENTER); t.setDuplicateParentStateEnabled(true); t.setTextColor(selColors(normal)); return t; }

    private void dayDialog(int iso,String name){
        android.app.Dialog d=sheet(name,340);
        LinearLayout b=sheetBody;
        boolean on=Prefs.dayEnabled(this,iso);
        String tt=time(Prefs.dayStartHour(this,iso),Prefs.dayStartMinute(this,iso))+"  ←  "+time(Prefs.dayEndHour(this,iso),Prefs.dayEndMinute(this,iso));
        Button first=sheetBtn(b,on?"⏸  إيقاف القفل في هذا اليوم":"▶  تشغيل القفل في هذا اليوم",v->{
            d.dismiss(); Prefs.setDayEnabled(this,iso,!on); ScheduleUtil.reschedule(this); LockOverlayService.sync(this); buildSettings(); });
        sheetBtn(b,"🕒  تغيير الوقت   ("+tt+")",v->{ d.dismiss(); editDayTimes(iso,name); });
        if(Prefs.hasDayTimes(this,iso)) sheetBtn(b,"↺  رجوع للوقت العام",v->{
            d.dismiss(); Prefs.clearDayTimes(this,iso); ScheduleUtil.reschedule(this); LockOverlayService.sync(this); buildSettings(); });
        sheetBtn(b,"إغلاق",v->d.dismiss());
        d.setOnShowListener(x->first.requestFocus());
        d.show();
    }

    private void pgExam(){
        note("قفل يومي إضافي (مثلاً وقت الدراسة) يعمل كل يوم بالإضافة للقفل الليلي");
        switchRow("وضع الامتحانات",Prefs.extraOn(this),v->{ Prefs.setExtraOn(this,!Prefs.extraOn(this)); ScheduleUtil.reschedule(this); LockOverlayService.sync(this); buildSettings(); });
        row("يبدأ",time(Prefs.extraStartHour(this),Prefs.extraStartMinute(this)),v->pickTime("بداية وضع الامتحانات",Prefs.extraStartHour(this),Prefs.extraStartMinute(this),(h,m)->{
            Prefs.setExtra(this,Prefs.extraOn(this),h,m,Prefs.extraEndHour(this),Prefs.extraEndMinute(this)); ScheduleUtil.reschedule(this); buildSettings(); }));
        row("ينتهي",time(Prefs.extraEndHour(this),Prefs.extraEndMinute(this)),v->pickTime("نهاية وضع الامتحانات",Prefs.extraEndHour(this),Prefs.extraEndMinute(this),(h,m)->{
            Prefs.setExtra(this,Prefs.extraOn(this),Prefs.extraStartHour(this),Prefs.extraStartMinute(this),h,m); ScheduleUtil.reschedule(this); buildSettings(); }));
    }

    private void pgSession(){
        note("بعد مدة تشغيل متواصلة، يُقفل التلفزيون استراحة قصيرة تلقائياً");
        switchRow("الحد من التشغيل المتواصل",Prefs.sessionOn(this),v->{ Prefs.setSessionOn(this,!Prefs.sessionOn(this)); buildSettings(); });
        row("مدة التشغيل",Prefs.sessionLimitMin(this)+" دقيقة",v->{ int c=Prefs.sessionLimitMin(this); Prefs.setSessionLimitMin(this,c<60?60:c<90?90:c<120?120:c<180?180:60); buildSettings(); });
        row("مدة الاستراحة",Prefs.sessionBreakMin(this)+" دقيقة",v->{ int c=Prefs.sessionBreakMin(this); Prefs.setSessionBreakMin(this,c<15?15:c<30?30:10); buildSettings(); });
        row("تشغيل اليوم حتى الآن",ScreenTracker.format(ScreenTracker.todayMs(this)),null);
        note("اضغط على المدة لتبديلها بين القيم الجاهزة");
    }

    private void pgApps(){
        note("تطبيقات تُغلق تلقائياً خلال الفترة التي تحددها");
        switchRow("قفل التطبيقات",Prefs.appsOn(this),v->{ Prefs.setAppsOn(this,!Prefs.appsOn(this)); buildSettings(); });
        row("التطبيقات المحجوبة",Prefs.appsBlocked(this).size()+" تطبيق",v->pickApps());
        row("من الساعة",time(Prefs.appsStartHour(this),Prefs.appsStartMinute(this)),v->pickTime("بداية قفل التطبيقات",Prefs.appsStartHour(this),Prefs.appsStartMinute(this),(h,m)->{
            Prefs.setAppsWindow(this,h,m,Prefs.appsEndHour(this),Prefs.appsEndMinute(this)); buildSettings(); }));
        row("إلى الساعة",time(Prefs.appsEndHour(this),Prefs.appsEndMinute(this)),v->pickTime("نهاية قفل التطبيقات",Prefs.appsEndHour(this),Prefs.appsEndMinute(this),(h,m)->{
            Prefs.setAppsWindow(this,Prefs.appsStartHour(this),Prefs.appsStartMinute(this),h,m); buildSettings(); }));
    }

    private void pgUpdate(){
        row("الإصدار الحالي",currentVersionName(),null);
        int rc=Prefs.updateReadyCode(this);
        if(rc>UpdateChecker.currentCode(this)){
            row("⬇ تثبيت التحديث",Prefs.updateReadyName(this),v->{ String e=UpdateChecker.install(this); if(e!=null) Toast.makeText(this,e,Toast.LENGTH_LONG).show(); });
        }
        row("🔄 فحص التحديثات الآن","",v->{ UpdateChecker.checkNow(this); Toast.makeText(this,"جارٍ الفحص… ارجع بعد دقيقة",Toast.LENGTH_LONG).show(); });
        note("تنبيهات تيليجرام والنسخ الاحتياطي من صفحة الموبايل");
    }

    private void pgProtect(){
        group("الدخول");
        row("تغيير PIN","",v->changePin());
        row("رسالة شاشة القفل","",v->changeMessage());
        row("📋 سجل الأحداث","",v->showLogDialog());
        group("إدارة التطبيق");
        LinearLayout un=row("🗑  حذف التطبيق","يحتاج PIN",v->{
            Intent i=new Intent(this,AdminGuardActivity.class);
            i.putExtra("uninstall",true);
            startActivity(i);
        });
        ((TextView)un.getChildAt(0)).setTextColor(Color.rgb(255,170,130));
    }

    private void pgPerms(){
        note("لازم تكون كلها مفعّلة حتى تعمل الحماية بشكل كامل");
        boolean o=Settings.canDrawOverlays(this);
        LinearLayout r1=row("الظهور فوق التطبيقات",o?"✓ مفعّل":"⚠ تفعيل",v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))));
        boolean a=isAccessibilityEnabled();
        LinearLayout r2=row("Accessibility",a?"✓ مفعّل":"⚠ تفعيل",v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        boolean ad=isDeviceAdminActive();
        LinearLayout r3=row("حماية الحذف",ad?"✓ مفعّل":"⚠ تفعيل",v->{
            if(isDeviceAdminActive()) toast("حماية الحذف مفعّلة");
            else requestDeviceAdmin();
        });
        LinearLayout[] rs={r1,r2,r3}; boolean[] ok={o,a,ad};
        for(int i=0;i<3;i++) ((TextView)rs[i].getChildAt(1)).setTextColor(ok[i]?Color.rgb(140,240,160):Color.rgb(255,202,120));
        if(Build.VERSION.SDK_INT>=31){
            AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);
            boolean e=am.canScheduleExactAlarms();
            LinearLayout r4=row("التوقيت الدقيق",e?"✓ مفعّل":"⚠ تفعيل",v->{
                try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))); }
                catch(Exception ex){ toast("سيستخدم التطبيق أفضل توقيت متاح على هذه الشاشة."); }
            });
            ((TextView)r4.getChildAt(1)).setTextColor(e?Color.rgb(140,240,160):Color.rgb(255,202,120));
        }
    }

    private void showTempUnlockDialog(){
        android.app.Dialog d=sheet("فتح مؤقت",380);
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        float dn=getResources().getDisplayMetrics().density;
        Button mid=null;
        for(int min:new int[]{15,30,60}){
            Button b=smallKey(min+" دقيقة",dn); b.setTextSize(14);
            b.setOnClickListener(v->{
                Prefs.setBypassUntil(this,System.currentTimeMillis()+min*60*1000L);
                LockOverlayService.sync(this); d.dismiss();
                toast("تم الفتح مؤقتًا لمدة "+min+" دقيقة");
                if(!inSettings && authenticated) buildDashboard();
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1f); lp.setMargins(dp(4),0,dp(4),0);
            row.addView(b,lp);
            if(min==30) mid=b;
        }
        LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(-1,-2); rl.setMargins(0,dp(10),0,dp(4));
        sheetBody.addView(row,rl);
        sheetBtn(sheetBody,"إلغاء",v->d.dismiss());
        final Button f=mid; d.setOnShowListener(x->{ if(f!=null) f.requestFocus(); });
        d.show();
    }

    /** نافذة صغيرة بنفس شكل البرنامج؛ العناصر تضاف إلى sheetBody. */
    private LinearLayout sheetBody;
    private android.app.Dialog sheet(String title,int widthDp){
        applyTheme();
        android.app.Dialog d=new android.app.Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(20),dp(16),dp(20),dp(14));
        GradientDrawable bg=new GradientDrawable(); bg.setCornerRadius(dp(22));
        if(LIGHT) bg.setColor(Color.WHITE); else { bg.setColor(Color.argb(240,24,22,58)); bg.setStroke(dp(1),Color.argb(70,255,255,255)); }
        box.setBackground(bg);
        TextView tt=text(title,16,TEXT); tt.setTypeface(Typeface.DEFAULT_BOLD); tt.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,-2); tl.setMargins(0,0,0,dp(8));
        box.addView(tt,tl);
        d.setContentView(box);
        if(d.getWindow()!=null){
            d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout(dp(widthDp),ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setGravity(Gravity.CENTER);
        }
        sheetBody=box;
        return d;
    }

    private Button sheetBtn(LinearLayout parent,String s,View.OnClickListener l){
        Button b=smallKey(s,getResources().getDisplayMetrics().density);
        b.setTextSize(14); b.setOnClickListener(l);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(42)); lp.setMargins(0,dp(4),0,dp(4));
        parent.addView(b,lp);
        return b;
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
        java.util.Set<String> chosen=new java.util.HashSet<>(Prefs.appsBlocked(this));
        android.app.Dialog d=sheet("التطبيقات المحجوبة",460);
        ScrollView sv=new ScrollView(this); sv.setVerticalScrollBarEnabled(false);
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list,new ScrollView.LayoutParams(-1,-2));
        View first=null;
        for(java.util.Map.Entry<String,String> e:apps.entrySet()){
            final String pk=e.getKey(), label=e.getValue();
            LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL);
            r.setFocusable(true); r.setClickable(true); r.setPadding(dp(14),0,dp(14),0); r.setBackground(rowBg(12));
            TextView lb=text(label,15,TEXT); lb.setDuplicateParentStateEnabled(true); lb.setTextColor(selColors(TEXT));
            TextView ck=text(chosen.contains(pk)?"🔒":"○",15,CYAN); ck.setDuplicateParentStateEnabled(true); ck.setTextColor(selColors(CYAN));
            r.addView(lb,new LinearLayout.LayoutParams(0,-2,1f)); r.addView(ck,new LinearLayout.LayoutParams(-2,-2));
            r.setOnClickListener(v->{ if(chosen.contains(pk)) chosen.remove(pk); else chosen.add(pk); ck.setText(chosen.contains(pk)?"🔒":"○"); });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(42)); lp.setMargins(0,dp(3),0,dp(3));
            list.addView(r,lp);
            if(first==null) first=r;
        }
        if(apps.isEmpty()) list.addView(text("لا توجد تطبيقات",14,MUTED));
        int maxH=Math.min(dp(300),(int)(getResources().getDisplayMetrics().heightPixels*.55f));
        sheetBody.addView(sv,new LinearLayout.LayoutParams(-1,Math.min(maxH,Math.max(dp(60),apps.size()*dp(48)))));
        LinearLayout acts=new LinearLayout(this); acts.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-1,-2); al.setMargins(0,dp(8),0,0);
        sheetBody.addView(acts,al);
        LinearLayout a1=new LinearLayout(this), a2=new LinearLayout(this);
        a1.setOrientation(LinearLayout.VERTICAL); a2.setOrientation(LinearLayout.VERTICAL);
        acts.addView(a1,new LinearLayout.LayoutParams(0,-2,1f)); acts.addView(a2,new LinearLayout.LayoutParams(0,-2,1f));
        a1.setPadding(0,0,dp(4),0); a2.setPadding(dp(4),0,0,0);
        sheetBtn(a1,"حفظ",v->{ Prefs.setAppsBlocked(this,chosen); d.dismiss(); buildSettings(); });
        sheetBtn(a2,"إلغاء",v->d.dismiss());
        final View f=first; d.setOnShowListener(x->{ if(f!=null) f.requestFocus(); });
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
        android.app.Dialog d=sheet("سجل الأحداث",620);
        ScrollView sv=new ScrollView(this); sv.setFocusable(true); sv.setVerticalScrollBarEnabled(true);
        TextView t=text(EventLog.read(this),13,TEXT);
        t.setPadding(dp(10),dp(6),dp(10),dp(6));
        sv.addView(t);
        sv.setBackground(roundBg(LIGHT?Color.rgb(244,245,251):Color.argb(20,255,255,255),Color.TRANSPARENT,12));
        int h=Math.min(dp(320),(int)(getResources().getDisplayMetrics().heightPixels*.6f));
        sheetBody.addView(sv,new LinearLayout.LayoutParams(-1,h));
        Button close=sheetBtn(sheetBody,"إغلاق",v->d.dismiss());
        d.setOnShowListener(x->sv.requestFocus());
        d.show();
    }

    private void pickTime(String title,int h0,int m0,TimeSaver saver){
        applyTheme();
        final int[] v={h0%12==0?12:h0%12, m0, h0>=12?1:0};
        float dn=getResources().getDisplayMetrics().density;
        android.app.Dialog d=new android.app.Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(22),dp(16),dp(22),dp(16));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable bg=new GradientDrawable(); bg.setCornerRadius(dp(22));
        if(LIGHT) bg.setColor(Color.WHITE); else { bg.setColor(Color.argb(235,24,22,58)); bg.setStroke(dp(1),Color.argb(70,255,255,255)); }
        box.setBackground(bg);

        TextView tt=text(title,15,TEXT); tt.setTypeface(Typeface.DEFAULT_BOLD); tt.setGravity(Gravity.CENTER);
        box.addView(tt,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout cols=new LinearLayout(this); cols.setOrientation(LinearLayout.HORIZONTAL); cols.setGravity(Gravity.CENTER);
        cols.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView hv=text("",28,TEXT), mv=text("",28,TEXT), colon=text(":",26,MUTED);
        for(TextView t:new TextView[]{hv,mv,colon}){ t.setGravity(Gravity.CENTER); t.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL)); }
        Button hu=smallKey("▲",dn), hd=smallKey("▼",dn), mu=smallKey("▲",dn), md=smallKey("▼",dn), ap=smallKey("",dn);
        ap.setTextSize(15);
        cols.addView(timeCol(hu,hv,hd),new LinearLayout.LayoutParams(dp(70),-2));
        cols.addView(colon,new LinearLayout.LayoutParams(dp(18),-2));
        cols.addView(timeCol(mu,mv,md),new LinearLayout.LayoutParams(dp(70),-2));
        LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(dp(54),dp(44)); al.setMargins(dp(12),0,0,0);
        cols.addView(ap,al);
        LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-2,-2); cl.setMargins(0,dp(10),0,dp(6));
        box.addView(cols,cl);

        Runnable upd=()->{ hv.setText(String.valueOf(v[0])); mv.setText(String.format("%02d",v[1])); ap.setText(v[2]==0?"ص":"م"); };
        upd.run();
        hu.setOnClickListener(x->{ v[0]=v[0]%12+1; upd.run(); });
        hd.setOnClickListener(x->{ v[0]=(v[0]+10)%12+1; upd.run(); });
        mu.setOnClickListener(x->{ v[1]=(v[1]+1)%60; upd.run(); });
        md.setOnClickListener(x->{ v[1]=(v[1]+59)%60; upd.run(); });
        ap.setOnClickListener(x->{ v[2]=1-v[2]; upd.run(); });

        LinearLayout acts=new LinearLayout(this); acts.setOrientation(LinearLayout.HORIZONTAL); acts.setGravity(Gravity.CENTER);
        Button save=smallKey("حفظ",dn), cancel=smallKey("إلغاء",dn);
        save.setTextSize(14); cancel.setTextSize(14);
        save.setOnClickListener(x->{ d.dismiss(); saver.save((v[0]%12)+(v[2]==1?12:0),v[1]); });
        cancel.setOnClickListener(x->d.dismiss());
        LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(dp(96),dp(38)); bl.setMargins(dp(6),0,dp(6),0);
        acts.addView(save,bl);
        LinearLayout.LayoutParams bl2=new LinearLayout.LayoutParams(dp(96),dp(38)); bl2.setMargins(dp(6),0,dp(6),0);
        acts.addView(cancel,bl2);
        LinearLayout.LayoutParams ql=new LinearLayout.LayoutParams(-2,-2); ql.setMargins(0,dp(8),0,0);
        box.addView(acts,ql);

        d.setContentView(box);
        if(d.getWindow()!=null){
            d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout(dp(340),ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setGravity(Gravity.CENTER);
        }
        d.setOnShowListener(x->mu.requestFocus());
        d.show();
    }

    private Button smallKey(String s,float dn){
        Button b=new Button(this);
        b.setText(s); b.setTextSize(13); b.setAllCaps(false); b.setFocusable(true);
        b.setMinHeight(0); b.setMinWidth(0); b.setPadding(0,0,0,0); b.setStateListAnimator(null);
        b.setBackground(rowBg(12));
        b.setTextColor(LIGHT?selColors(TEXT):android.content.res.ColorStateList.valueOf(Color.WHITE));
        return b;
    }

    private LinearLayout timeCol(Button up,TextView val,Button down){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER);
        c.addView(up,new LinearLayout.LayoutParams(-1,dp(30)));
        LinearLayout.LayoutParams vl=new LinearLayout.LayoutParams(-1,dp(48)); vl.setMargins(0,dp(4),0,dp(4));
        c.addView(val,vl);
        c.addView(down,new LinearLayout.LayoutParams(-1,dp(30)));
        return c;
    }

    private void changePin(){
        android.app.Dialog d=sheet("تغيير الرمز",330);
        float dn=getResources().getDisplayMetrics().density;
        MaskedPinEditText e=new MaskedPinEditText(this);
        e.setMaskColor(TEXT); e.setTextSize(24); e.setGravity(Gravity.CENTER); e.setSingleLine(true);
        e.setBackground(roundBg(LIGHT?Color.rgb(244,245,251):Color.argb(20,255,255,255),Color.TRANSPARENT,12));
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        sheetBody.addView(e,new LinearLayout.LayoutParams(-1,dp(44)));
        TextView hint=text("رمز جديد من 4 إلى 8 أرقام",12,MUTED); hint.setGravity(Gravity.CENTER);
        sheetBody.addView(hint,new LinearLayout.LayoutParams(-1,dp(24)));
        Runnable save=()->{
            String p=e.getText().toString();
            if(!p.matches("\\d{4,8}")){ toast("من 4 إلى 8 أرقام"); return; }
            hideKeyboard(e); Prefs.setPin(this,p); d.dismiss(); toast("تم تغيير الرمز");
        };
        e.setOnEditorActionListener((v,id,ev)->{ if(id==EditorInfo.IME_ACTION_DONE){ save.run(); return true; } return false; });
        String[][] rows={{"1","2","3"},{"4","5","6"},{"7","8","9"},{"⌫","0","✓"}};
        Button five=null;
        for(String[] r:rows){
            LinearLayout rr=new LinearLayout(this); rr.setOrientation(LinearLayout.HORIZONTAL); rr.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            for(String k:r){
                Button b=smallKey(k,dn); b.setTextSize(18);
                b.setOnClickListener(v->{
                    String cur=e.getText().toString();
                    if(k.equals("⌫")){ if(!cur.isEmpty()) e.setText(cur.substring(0,cur.length()-1)); }
                    else if(k.equals("✓")) save.run();
                    else if(cur.length()<8) e.setText(cur+k);
                });
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1f); lp.setMargins(dp(3),dp(3),dp(3),dp(3));
                rr.addView(b,lp);
                if(k.equals("5")) five=b;
            }
            sheetBody.addView(rr,new LinearLayout.LayoutParams(-1,-2));
        }
        sheetBtn(sheetBody,"إلغاء",v->d.dismiss());
        final Button f=five; d.setOnShowListener(x->{ if(f!=null) f.requestFocus(); });
        d.show();
    }

    private void changeMessage(){
        android.app.Dialog d=sheet("رسالة شاشة القفل",460);
        EditText e=new EditText(this); e.setText(Prefs.message(this)); e.setSingleLine(true);
        e.setTextColor(TEXT); e.setTextSize(15); e.setPadding(dp(12),0,dp(12),0);
        e.setBackground(rowBg(12));
        sheetBody.addView(e,new LinearLayout.LayoutParams(-1,dp(46)));
        TextView hint=text("اكتبها بلوحة المفاتيح أو من الموبايل",12,MUTED); hint.setGravity(Gravity.CENTER);
        sheetBody.addView(hint,new LinearLayout.LayoutParams(-1,dp(26)));
        sheetBtn(sheetBody,"حفظ",v->{
            hideKeyboard(e);
            String s=e.getText().toString().trim();
            if(!s.isEmpty()){ Prefs.setMessage(this,s); toast("تم حفظ الرسالة"); }
            d.dismiss();
        });
        sheetBtn(sheetBody,"إلغاء",v->{ hideKeyboard(e); d.dismiss(); });
        d.show();
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

    private GradientDrawable roundBg(int fill,int stroke,int radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(1),stroke); return g;
    }

    private TextView note(String s){
        TextView t=text(s,13,MUTED); t.setGravity(Gravity.CENTER); t.setPadding(dp(8),dp(2),dp(8),dp(8));
        root.addView(t,new LinearLayout.LayoutParams(Math.min(dp(700),(int)(getResources().getDisplayMetrics().widthPixels*.60f)),ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private TextView text(String s,int sp,int color){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); return t;
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
