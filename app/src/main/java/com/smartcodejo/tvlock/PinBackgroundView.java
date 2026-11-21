package com.smartcodejo.tvlock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** خلفية شاشة الرمز: داكن (أزرق→وردي) أو فاتح (بنفسجي→وردي). */
public class PinBackgroundView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean light;
    public PinBackgroundView(Context c,boolean light){ super(c); this.light=light; }

    @Override protected void onDraw(Canvas c){
        float w=getWidth(), h=getHeight(); if(w<=0||h<=0) return;
        if(light){
            p.setShader(new LinearGradient(0,0,w,h,new int[]{Color.rgb(165,180,252),Color.rgb(196,181,253),Color.rgb(249,168,212)},null,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);
        } else {
            p.setShader(null); p.setColor(Color.rgb(11,15,42)); c.drawRect(0,0,w,h,p);
            glow(c,w*.2f,0,w*.62f,Color.argb(235,59,91,219));
            glow(c,w,h,w*.62f,Color.argb(235,194,37,92));
        }
        p.setShader(null);
    }
    private void glow(Canvas c,float x,float y,float r,int col){
        p.setShader(new RadialGradient(x,y,r,new int[]{col,Color.argb(0,Color.red(col),Color.green(col),Color.blue(col))},null,Shader.TileMode.CLAMP));
        c.drawCircle(x,y,r,p);
    }
}
