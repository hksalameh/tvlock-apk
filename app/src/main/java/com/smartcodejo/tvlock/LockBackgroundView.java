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

/** خلفية حديثة: تدرّج عميق + توهّجات ناعمة + شبكة نقاط خفيفة + خطوط انسيابية. تتبع ألوان الثيم. */
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
        Context x=getContext();

        // قاعدة قطرية
        p.setShader(new LinearGradient(0,0,w,h,
                new int[]{Theme.bgAt(x,0),Theme.bgAt(x,2),Theme.bgAt(x,1)},
                new float[]{0f,.55f,1f},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);

        // توهّجات كبيرة ناعمة
        glow(c,w*.12f,h*.08f,Math.max(w,h)*.55f,Theme.aurora(x,0,120));
        glow(c,w*.92f,h*.95f,Math.max(w,h)*.60f,Theme.aurora(x,2,105));
        glow(c,w*.80f,h*.20f,Math.max(w,h)*.32f,Theme.accent(x,55));

        // خطوط انسيابية شفافة
        p.setStyle(Paint.Style.STROKE);
        p.setShader(null);
        for(int i=0;i<5;i++){
            float y0=h*(.52f+i*.075f);
            path.reset();
            path.moveTo(-w*.05f,y0+h*.10f);
            path.cubicTo(w*.25f,y0-h*.16f,w*.55f,y0+h*.20f,w*1.05f,y0-h*.06f);
            p.setStrokeWidth(1.4f+i*.5f);
            p.setColor(Theme.accent(x,12+i*9));
            c.drawPath(path,p);
        }
        p.setStyle(Paint.Style.FILL);

        // شبكة نقاط تتلاشى نحو الأسفل
        float step=Math.max(26f,w/46f);
        for(float gy=step/2;gy<h;gy+=step){
            float fade=1f-(gy/h);
            int a=(int)(10+fade*46);
            for(float gx=step/2;gx<w;gx+=step){
                p.setColor(Theme.accent(x,a));
                c.drawCircle(gx,gy,1.5f,p);
            }
        }

        // حلقات خفيفة (لمسة هندسية)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f);
        for(int i=1;i<=4;i++){
            p.setColor(Theme.accent(x,10+i*5));
            c.drawCircle(w*.84f,h*.22f,h*.11f*i,p);
        }
        p.setStyle(Paint.Style.FILL);

        // تعتيم حواف
        p.setShader(new RadialGradient(w*.5f,h*.5f,Math.max(w,h)*.75f,
                new int[]{Color.TRANSPARENT,Color.argb(110,0,3,14)},
                new float[]{.45f,1f},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);
        p.setShader(null);
    }

    private void glow(Canvas c,float cx,float cy,float r,int col){
        p.setShader(new RadialGradient(cx,cy,r,new int[]{col,Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,r,p);
        p.setShader(null);
    }
}
