package com.smartcodejo.tvlock;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class AdminGuardActivity extends Activity {
    private final int CYAN=Color.rgb(83,210,255);
    private boolean uninstallFlow=false;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        uninstallFlow=getIntent()!=null && getIntent().getBooleanExtra("uninstall_flow",false);

        boolean light=Prefs.pinStyle(this)==1;
        float dn=getResources().getDisplayMetrics().density;
        int fg=light?Color.rgb(34,40,58):Color.WHITE;
        int muted=light?Color.rgb(100,108,130):Color.rgb(200,212,236);

        FrameLayout frame=new FrameLayout(this);
        frame.addView(new PinBackgroundView(this,light), new FrameLayout.LayoutParams(-1,-1));

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28),dp(22),dp(28),dp(22));
        if(light){
            GradientDrawable cb=new GradientDrawable(); cb.setColor(Color.WHITE); cb.setCornerRadius(dp(28));
            root.setBackground(cb); root.setElevation(dp(16));
            frame.addView(root,new FrameLayout.LayoutParams(dp(460),ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.CENTER));
        } else {
            frame.addView(root,new FrameLayout.LayoutParams(-1,-1));
        }

        TextView title=txt(uninstallFlow?"🗑  تأكيد حذف TV Lock":"🔒  الإعدادات محمية",22,fg,true);
        title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView note=txt(uninstallFlow?"أدخل الرمز للسماح بحذف التطبيق":"أدخل الرمز لفتح الإعدادات لمدة دقيقتين",14,muted,false);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2); np.setMargins(0,dp(6),0,0);
        root.addView(note,np);

        MaskedPinEditText pin=new MaskedPinEditText(this);
        pin.setMaskColor(fg); pin.setTextSize(24); pin.setGravity(Gravity.CENTER);
        pin.setSingleLine(true); pin.setBackground(null); pin.setPadding(0,0,0,0);
        pin.setImeOptions(EditorInfo.IME_ACTION_DONE);
        pin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(240),dp(40)); pp.setMargins(0,dp(8),0,dp(6));
        root.addView(pin,pp);

        Runnable verify=()->{
            String x=pin.getText().toString();
            if(x.isEmpty()) return;
            if(Prefs.checkPin(this,x)){
                hideKeyboard(pin);
                Prefs.setAdminBypassUntil(this,System.currentTimeMillis()+2*60*1000L);
                EventLog.add(this,uninstallFlow?"PIN صحيح: السماح بالحذف":"PIN صحيح: سماح دقيقتين للإعدادات");
                if(uninstallFlow){
                    allowUninstall();
                }else{
                    Toast.makeText(this,"تم السماح مؤقتًا لمدة دقيقتين",Toast.LENGTH_SHORT).show();
                    finish();
                }
            }else{
                pin.setText("");
                EventLog.add(this,"PIN غلط في شاشة الحماية");
                Toast.makeText(this,"الرمز غير صحيح",Toast.LENGTH_SHORT).show();
            }
        };
        pin.setOnEditorActionListener((v,id,event)->{
            if(id==EditorInfo.IME_ACTION_DONE){ verify.run(); return true; }
            return false;
        });

        String[][] rows={{"1","2","3"},{"4","5","6"},{"7","8","9"},{"⌫","0","✓"}};
        Button five=null;
        for(String[] r:rows){
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_LTR);
            for(String k:r){
                Button kb=new Button(this);
                kb.setText(k); kb.setTextSize(k.equals("⌫")||k.equals("✓")?18:22); kb.setAllCaps(false);
                kb.setFocusable(true); kb.setMinHeight(0); kb.setMinWidth(0); kb.setPadding(0,0,0,0); kb.setStateListAnimator(null);
                kb.setBackground(keyBg(light,dn));
                kb.setTextColor(new android.content.res.ColorStateList(
                        new int[][]{{android.R.attr.state_focused},{}},new int[]{Color.WHITE,fg}));
                kb.setOnClickListener(v->{
                    String cur=pin.getText().toString();
                    if(k.equals("⌫")){ if(!cur.isEmpty()) pin.setText(cur.substring(0,cur.length()-1)); }
                    else if(k.equals("✓")) verify.run();
                    else if(cur.length()<8) pin.setText(cur+k);
                });
                int w=light?dp(70):dp(58), h=light?dp(44):dp(58);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(w,h); lp.setMargins(dp(6),dp(4),dp(6),dp(4));
                row.addView(kb,lp);
                if(k.equals("5")) five=kb;
            }
            root.addView(row,new LinearLayout.LayoutParams(-2,-2));
        }
        TextView brand=txt("HKS  ·  hksalameh",10,light?Color.argb(190,255,255,255):Color.argb(115,255,255,255),false);
        brand.setLetterSpacing(.3f); brand.setGravity(Gravity.CENTER);
        frame.addView(brand,new FrameLayout.LayoutParams(-1,dp(30),Gravity.BOTTOM));

        setContentView(frame);
        final Button f0=five; if(f0!=null) f0.post(f0::requestFocus);
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
        return super.dispatchKeyEvent(e);
    }

    private void allowUninstall(){
        try{
            DevicePolicyManager dpm=(DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin=new ComponentName(this,TvDeviceAdminReceiver.class);
            if(dpm!=null && dpm.isAdminActive(admin)) dpm.removeActiveAdmin(admin);
        }catch(Exception ignored){}

        Toast.makeText(this,"تم قبول PIN. سيتم فتح نافذة الحذف.",Toast.LENGTH_SHORT).show();
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            try{
                Intent delete=new Intent(Intent.ACTION_DELETE, Uri.parse("package:"+getPackageName()));
                delete.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(delete);
            }catch(Exception e){
                Toast.makeText(this,"تعذر فتح نافذة الحذف",Toast.LENGTH_SHORT).show();
            }
            finish();
        },900);
    }

    @Override public void onBackPressed(){
        // Never return an unauthenticated viewer to the protected Settings screen.
        Intent home=new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(home); } catch(Exception ignored){}
        finish();
    }

    private void hideKeyboard(EditText e){
        InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm!=null) imm.hideSoftInputFromWindow(e.getWindowToken(),0);
    }

    private TextView txt(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }
    private GradientDrawable roundBg(int fill,int stroke,int radius){
        GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setStroke(dp(1),stroke); g.setCornerRadius(dp(radius)); return g;
    }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
}
