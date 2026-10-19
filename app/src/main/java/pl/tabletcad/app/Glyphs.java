package pl.tabletcad.app;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Small vector glyphs of the chrome (chevrons, undo/redo, more, home, close) drawn with the line style of the icons. */
public final class Glyphs {
    private Glyphs() {}
    public static final int LEFT=0,RIGHT=1,DOWN=2,UP=3;
    private static void stroke(Paint p,int color,float width){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(color);}
    private static void restore(Paint p){p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);p.setStrokeJoin(Paint.Join.MITER);}
    /** Chevron centred at (x, y) with half size s. */
    public static void chevron(Canvas c,Paint p,float x,float y,float s,int direction,int color,float width) {
        stroke(p,color,width);Path path=new Path();
        switch(direction){
            case LEFT:path.moveTo(x+s*.5f,y-s);path.lineTo(x-s*.5f,y);path.lineTo(x+s*.5f,y+s);break;
            case RIGHT:path.moveTo(x-s*.5f,y-s);path.lineTo(x+s*.5f,y);path.lineTo(x-s*.5f,y+s);break;
            case UP:path.moveTo(x-s,y+s*.5f);path.lineTo(x,y-s*.5f);path.lineTo(x+s,y+s*.5f);break;
            default:path.moveTo(x-s,y-s*.5f);path.lineTo(x,y+s*.5f);path.lineTo(x+s,y-s*.5f);
        }
        c.drawPath(path,p);restore(p);
    }
    /** Curved arrow: undo (pointing left) or redo (mirrored). */
    public static void undo(Canvas c,Paint p,float x,float y,float r,boolean redo,int color,float width) {
        stroke(p,color,width);c.save();if(redo)c.scale(-1,1,x,y);
        RectF arc=new RectF(x-r,y-r*.8f,x+r,y+r*1.2f);c.drawArc(arc,200,250,false,p);
        float hx=x-r*.95f,hy=y+r*.05f;Path head=new Path();head.moveTo(hx-r*.45f,hy-r*.55f);head.lineTo(hx,hy+r*.05f);head.lineTo(hx+r*.55f,hy-r*.45f);c.drawPath(head,p);
        c.restore();restore(p);
    }
    /** Three dots (menu of an element). */
    public static void more(Canvas c,Paint p,float x,float y,float gap,float radius,int color) {
        p.setStyle(Paint.Style.FILL);p.setColor(color);for(int i=-1;i<=1;i++)c.drawCircle(x+i*gap,y,radius,p);
    }
    /** House outline (home view). */
    public static void home(Canvas c,Paint p,float x,float y,float s,int color,float width) {
        stroke(p,color,width);Path path=new Path();
        path.moveTo(x-s,y-s*.05f);path.lineTo(x,y-s);path.lineTo(x+s,y-s*.05f);
        path.moveTo(x-s*.7f,y-s*.3f);path.lineTo(x-s*.7f,y+s*.85f);path.lineTo(x+s*.7f,y+s*.85f);path.lineTo(x+s*.7f,y-s*.3f);
        c.drawPath(path,p);restore(p);
    }
    /** Close cross. */
    public static void close(Canvas c,Paint p,float x,float y,float s,int color,float width) {
        stroke(p,color,width);c.drawLine(x-s,y-s,x+s,y+s,p);c.drawLine(x-s,y+s,x+s,y-s,p);restore(p);
    }
}
