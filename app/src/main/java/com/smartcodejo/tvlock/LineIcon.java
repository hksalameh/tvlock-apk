package com.smartcodejo.tvlock;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** أيقونات خطية بسيطة: lock / timer / pause / play / gear. لون يتغيّر عند التحديد. */
public class LineIcon extends Drawable {
    private final String type; private final int normal, focused; private int color;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private final RectF r=new RectF();
    public LineIcon(String type,int normal,int focused){ this.type=type; this.normal=normal; this.focused=focused; this.color=normal;
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); }

    @Override public boolean isStateful(){ return true; }
    @Override protected boolean onStateChange(int[] st){
        int c=normal; for(int s:st) if(s==android.R.attr.state_focused||s==android.R.attr.state_pressed) c=focused;
        if(c!=color){ color=c; invalidateSelf(); return true; } return false;
    }

    @Override public void draw(Canvas c){
        Rect b=getBounds(); float s=Math.min(b.width(),b.height())*.36f, cx=b.exactCenterX(), cy=b.exactCenterY();
        p.setColor(color); p.setStrokeWidth(s*.12f); p.setStyle(Paint.Style.STROKE);
        switch(type){
            case "lock":
                r.set(cx-s*.5f,cy-s*.05f,cx+s*.5f,cy+s*.6f); c.drawRoundRect(r,s*.12f,s*.12f,p);
                r.set(cx-s*.3f,cy-s*.6f,cx+s*.3f,cy+s*.05f); c.drawArc(r,180,180,false,p);
                c.drawLine(cx-s*.3f,cy-s*.28f,cx-s*.3f,cy-s*.05f,p); c.drawLine(cx+s*.3f,cy-s*.28f,cx+s*.3f,cy-s*.05f,p);
                p.setStyle(Paint.Style.FILL); c.drawCircle(cx,cy+s*.27f,s*.08f,p); break;
            case "timer":
                c.drawCircle(cx,cy+s*.08f,s*.52f,p);
                c.drawLine(cx,cy+s*.08f,cx,cy-s*.2f,p); c.drawLine(cx,cy+s*.08f,cx+s*.2f,cy+s*.2f,p);
                c.drawLine(cx-s*.15f,cy-s*.6f,cx+s*.15f,cy-s*.6f,p); break;
            case "pause":
                p.setStyle(Paint.Style.FILL);
                r.set(cx-s*.38f,cy-s*.45f,cx-s*.12f,cy+s*.45f); c.drawRoundRect(r,s*.07f,s*.07f,p);
                r.set(cx+s*.12f,cy-s*.45f,cx+s*.38f,cy+s*.45f); c.drawRoundRect(r,s*.07f,s*.07f,p); break;
            case "play":
                p.setStyle(Paint.Style.FILL); android.graphics.Path t=new android.graphics.Path();
                t.moveTo(cx-s*.3f,cy-s*.48f); t.lineTo(cx+s*.48f,cy); t.lineTo(cx-s*.3f,cy+s*.48f); t.close(); c.drawPath(t,p); break;
            default: // gear
                c.drawCircle(cx,cy,s*.2f,p);
                c.drawCircle(cx,cy,s*.42f,p);
                for(int i=0;i<8;i++){ double a=Math.PI/4*i; float x1=cx+(float)Math.cos(a)*s*.45f, y1=cy+(float)Math.sin(a)*s*.45f;
                    float x2=cx+(float)Math.cos(a)*s*.62f, y2=cy+(float)Math.sin(a)*s*.62f; c.drawLine(x1,y1,x2,y2,p); }
        }
    }
    @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){}
    @Override public int getOpacity(){ return PixelFormat.TRANSLUCENT; }
}
