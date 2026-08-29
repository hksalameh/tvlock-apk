package com.smartcodejo.tvlock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

public class LockBackgroundView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();

    public LockBackgroundView(Context context){
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(), h=getHeight();
        if(w<=0||h<=0) return;

        p.setShader(new LinearGradient(0,0,0,h,
                new int[]{Color.rgb(1,8,28),Color.rgb(6,27,68),Color.rgb(22,64,118),Color.rgb(36,48,103),Color.rgb(6,18,43)},
                new float[]{0f,.34f,.55f,.68f,1f}, Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);
        p.setShader(null);

        float moonX=w*.72f, moonY=h*.18f, moonR=h*.055f;
        p.setShader(new RadialGradient(moonX,moonY,moonR*5f,
                new int[]{Color.argb(180,140,214,255),Color.argb(55,90,156,255),Color.TRANSPARENT},
                new float[]{0f,.28f,1f},Shader.TileMode.CLAMP));
        c.drawCircle(moonX,moonY,moonR*5f,p);
        p.setShader(null);
        p.setColor(Color.rgb(224,242,255));
        c.drawCircle(moonX,moonY,moonR,p);

        for(int i=0;i<95;i++){
            float x=((i*173+31)%1019)/1019f*w;
            float y=((i*97+13)%487)/487f*h*.43f;
            float r=.7f+(i%4)*.42f;
            p.setColor(i%9==0?Color.rgb(160,220,255):Color.WHITE);
            p.setAlpha(80+(i*29)%155);
            c.drawCircle(x,y,r,p);
        }
        p.setAlpha(255);

        p.setShader(new LinearGradient(0,0,w,0,
                new int[]{Color.TRANSPARENT,Color.argb(90,26,179,214),Color.argb(115,97,92,224),Color.argb(80,219,86,163),Color.TRANSPARENT},
                null,Shader.TileMode.CLAMP));
        path.reset();
        path.moveTo(0,h*.33f);
        path.cubicTo(w*.22f,h*.23f,w*.38f,h*.42f,w*.55f,h*.30f);
        path.cubicTo(w*.70f,h*.20f,w*.84f,h*.39f,w,h*.27f);
        path.lineTo(w,h*.43f);
        path.cubicTo(w*.79f,h*.49f,w*.68f,h*.35f,w*.54f,h*.44f);
        path.cubicTo(w*.36f,h*.55f,w*.20f,h*.36f,0,h*.48f);
        path.close();
        c.drawPath(path,p);
        p.setShader(null);

        p.setColor(Color.rgb(25,45,82));
        path.reset();
        path.moveTo(0,h*.67f);
        path.lineTo(0,h*.57f);
        path.lineTo(w*.08f,h*.50f);
        path.lineTo(w*.17f,h*.59f);
        path.lineTo(w*.29f,h*.44f);
        path.lineTo(w*.41f,h*.60f);
        path.lineTo(w*.54f,h*.48f);
        path.lineTo(w*.66f,h*.57f);
        path.lineTo(w*.77f,h*.42f);
        path.lineTo(w*.88f,h*.55f);
        path.lineTo(w,h*.47f);
        path.lineTo(w,h*.69f);
        path.close();
        c.drawPath(path,p);

        p.setColor(Color.argb(145,105,152,205));
        path.reset();
        path.moveTo(w*.235f,h*.505f); path.lineTo(w*.29f,h*.44f); path.lineTo(w*.335f,h*.502f);
        path.lineTo(w*.305f,h*.488f); path.lineTo(w*.286f,h*.474f); path.lineTo(w*.270f,h*.491f); path.close();
        c.drawPath(path,p);
        path.reset();
        path.moveTo(w*.735f,h*.485f); path.lineTo(w*.77f,h*.42f); path.lineTo(w*.815f,h*.493f);
        path.lineTo(w*.785f,h*.472f); path.lineTo(w*.768f,h*.454f); path.lineTo(w*.753f,h*.476f); path.close();
        c.drawPath(path,p);

        p.setColor(Color.rgb(5,17,38));
        path.reset();
        path.moveTo(0,h*.73f);
        path.lineTo(0,h*.64f);
        path.lineTo(w*.11f,h*.52f);
        path.lineTo(w*.25f,h*.68f);
        path.lineTo(w*.40f,h*.53f);
        path.lineTo(w*.52f,h*.70f);
        path.lineTo(w*.67f,h*.56f);
        path.lineTo(w*.81f,h*.69f);
        path.lineTo(w,h*.58f);
        path.lineTo(w,h*.75f);
        path.close();
        c.drawPath(path,p);

        for(int i=0;i<40;i++){
            float x=(i+.4f)/40f*w;
            float y=h*(.704f+((i%3)*.0018f));
            p.setColor((i%4==0)?Color.rgb(255,177,78):Color.rgb(95,180,255));
            p.setAlpha(125+(i%4)*28);
            c.drawCircle(x,y,1.6f+(i%3)*.55f,p);
        }
        p.setAlpha(255);

        p.setShader(new LinearGradient(0,h*.70f,0,h,
                new int[]{Color.rgb(12,58,103),Color.rgb(7,38,74),Color.rgb(2,13,31)},
                null,Shader.TileMode.CLAMP));
        c.drawRect(0,h*.70f,w,h,p);
        p.setShader(null);

        p.setColor(Color.argb(125,82,177,255));
        c.drawRect(0,h*.704f,w,h*.707f,p);

        for(int i=0;i<34;i++){
            float x=(i+.5f)/34f*w;
            float top=h*.713f;
            float len=h*(.018f+(i%6)*.010f);
            p.setColor(i%4==0?Color.rgb(255,170,79):Color.rgb(68,155,238));
            p.setAlpha(18+(i%4)*12);
            c.drawRect(x-1.2f,top,x+1.2f,top+len,p);
        }
        p.setAlpha(255);

        p.setShader(new RadialGradient(w*.5f,h*.5f,Math.max(w,h)*.72f,
                new int[]{Color.TRANSPARENT,Color.argb(88,0,4,18)},
                new float[]{.42f,1f},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);
        p.setShader(null);
    }
}
