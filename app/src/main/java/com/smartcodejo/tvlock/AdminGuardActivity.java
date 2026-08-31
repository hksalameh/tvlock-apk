package com.smartcodejo.tvlock;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class AdminGuardActivity extends Activity {
    private final int CYAN=Color.rgb(83,210,255);

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);

        FrameLayout frame=new FrameLayout(this);
        frame.addView(new LockBackgroundView(this), new FrameLayout.LayoutParams(-1,-1));

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(40),dp(26),dp(40),dp(26));
        frame.addView(root,new FrameLayout.LayoutParams(-1,-1));

        TextView badge=txt("HKS",16,Color.WHITE,true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundBg(Color.argb(190,12,75,148),CYAN,14));
        root.addView(badge,new LinearLayout.LayoutParams(dp(76),dp(34)));

        TextView title=txt("حماية TV LOCK",28,Color.WHITE,true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(48)); tp.setMargins(0,dp(8),0,0);
        root.addView(title,tp);

        TextView note=txt("يلزم إدخال PIN قبل السماح بإلغاء تثبيت التطبيق أو تعديل إعداداته الحساسة",15,Color.rgb(185,211,240),false);
        note.setGravity(Gravity.CENTER);
        root.addView(note,new LinearLayout.LayoutParams(Math.min(dp(760),(int)(getResources().getDisplayMetrics().widthPixels*.66f)),dp(58)));

        EditText pin=new EditText(this);
        pin.setHint("PIN");
        pin.setTextColor(Color.WHITE);
        pin.setHintTextColor(Color.rgb(155,180,210));
        pin.setTextSize(21);
        pin.setGravity(Gravity.CENTER);
        pin.setSingleLine(true);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setTransformationMethod(PasswordTransformationMethod.getInstance());
        pin.setImeOptions(EditorInfo.IME_ACTION_DONE);
        pin.setBackground(roundBg(Color.argb(180,7,32,68),Color.rgb(73,154,238),16));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(Math.min(dp(420),(int)(getResources().getDisplayMetrics().widthPixels*.38f)),dp(58));
        pp.setMargins(0,dp(8),0,dp(10)); root.addView(pin,pp);

        Button allow=new Button(this);
        allow.setText("السماح لمدة دقيقتين");
        allow.setTextSize(17);
        allow.setTextColor(Color.WHITE);
        allow.setAllCaps(false);
        allow.setFocusable(true);
        allow.setBackgroundResource(R.drawable.focus_button);
        root.addView(allow,new LinearLayout.LayoutParams(Math.min(dp(500),(int)(getResources().getDisplayMetrics().widthPixels*.45f)),dp(56)));

        TextView footer=txt("من صنع hksalameh | HKS",13,Color.rgb(146,183,222),false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(42)); fp.setMargins(0,dp(12),0,0); root.addView(footer,fp);

        Runnable verify=()->{
            if(Prefs.pin(this).equals(pin.getText().toString())){
                hideKeyboard(pin);
                Prefs.setAdminBypassUntil(this,System.currentTimeMillis()+2*60*1000L);
                Toast.makeText(this,"تم السماح مؤقتًا لمدة دقيقتين",Toast.LENGTH_SHORT).show();
                finish();
            }else{
                pin.setText("");
                Toast.makeText(this,"PIN غير صحيح",Toast.LENGTH_SHORT).show();
            }
        };
        allow.setOnClickListener(v->verify.run());
        pin.setOnEditorActionListener((v,id,event)->{
            if(id==EditorInfo.IME_ACTION_DONE){ verify.run(); return true; }
            return false;
        });
        pin.requestFocus();
    }

    @Override public void onBackPressed(){
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
