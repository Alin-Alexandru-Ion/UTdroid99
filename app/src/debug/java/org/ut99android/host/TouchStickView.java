package org.ut99android.host;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** One captured finger controls the movement stick; releasing centers it. */
final class TouchStickView extends View {
    interface Listener { void move(float x, float y); }
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int pointer = -1;
    private float x, y;
    TouchStickView(Context c, Listener listener) {
        super(c); this.listener=listener; setContentDescription("Movement stick");
    }
    @Override protected void onDraw(Canvas canvas) {
        float cx=getWidth()/2f, cy=getHeight()/2f, radius=Math.min(cx,cy)*.87f;
        paint.setColor(0x8831475a); canvas.drawCircle(cx,cy,radius,paint);
        paint.setColor(0xbbe4eef8); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); canvas.drawCircle(cx,cy,radius,paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xaae4eef8); canvas.drawCircle(cx+x*radius*.65f,cy+y*radius*.65f,radius*.32f,paint);
    }
    private void update(float px, float py) {
        float radius=Math.max(1,Math.min(getWidth(),getHeight())*.40f);
        float dx=(px-getWidth()/2f)/radius, dy=(py-getHeight()/2f)/radius;
        float length=(float)Math.sqrt(dx*dx+dy*dy); if(length>1) { dx/=length; dy/=length; }
        x=dx; y=dy; listener.move(x,y); invalidate();
    }
    void reset() { pointer=-1; x=y=0; listener.move(0,0); invalidate(); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked(), index=event.getActionIndex();
        if (action==MotionEvent.ACTION_DOWN && pointer==-1) { pointer=event.getPointerId(index); update(event.getX(index),event.getY(index)); return true; }
        if (action==MotionEvent.ACTION_MOVE && pointer!=-1) { int i=event.findPointerIndex(pointer); if(i>=0) update(event.getX(i),event.getY(i)); return true; }
        if ((action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP) && pointer==event.getPointerId(index)) { reset(); return true; }
        if (action==MotionEvent.ACTION_CANCEL) { reset(); return true; }
        return true;
    }
}
