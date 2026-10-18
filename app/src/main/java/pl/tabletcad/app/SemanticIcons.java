package pl.tabletcad.app;

import android.graphics.*;

/** Native vector glyphs; the operation silhouette and modifier share the same visual scale. */
public final class SemanticIcons {
    private SemanticIcons() {}
    public static boolean draw(Canvas canvas,String id,float x,float y,float scale,int color) {
        if(!java.util.Arrays.asList("Splajn","Wielokąt","Kwadrat","Prostokąt od środka","Zaokrąglij szkic","Wymiary","Wyciągnij","Wytnij","Wycięcie","Połączenie","Dodanie","Nowa bryła","Część wspólna","Gwint","Kąt").contains(id))return false;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
        canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);
        if(id.equals("Splajn")) {
            Path path=new Path();path.moveTo(-12,8);path.cubicTo(-8,-15,6,15,12,-8);canvas.drawPath(path,p);
            for(float[] point:new float[][]{{-12,8},{0,0},{12,-8}})canvas.drawCircle(point[0],point[1],2,p);
        } else if(id.equals("Wielokąt")) {
            Path path=new Path();for(int i=0;i<6;i++){double a=Math.PI/3*i;float px=(float)Math.cos(a)*11,py=(float)Math.sin(a)*11;if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}path.close();canvas.drawPath(path,p);
        } else if(id.equals("Kwadrat")||id.equals("Prostokąt od środka")) {
            canvas.drawRect(-11,-(id.equals("Kwadrat")?11:7),11,id.equals("Kwadrat")?11:7,p);
            if(id.equals("Prostokąt od środka")){canvas.drawLine(-4,0,4,0,p);canvas.drawLine(0,-4,0,4,p);}
            else {canvas.drawLine(-8,-8,-4,-8,p);canvas.drawLine(-8,-8,-8,-4,p);canvas.drawLine(4,8,8,8,p);canvas.drawLine(8,4,8,8,p);}
        } else if(id.equals("Zaokrąglij szkic")) {
            Path path=new Path();path.moveTo(-10,11);path.lineTo(-10,0);path.quadTo(-10,-10,0,-10);path.lineTo(11,-10);canvas.drawPath(path,p);
            p.setStrokeWidth(1);canvas.drawLine(-2,1,-8,-5,p);canvas.drawCircle(-2,1,1,p);
        } else if(id.equals("Wymiary")) {
            canvas.drawLine(-11,5,11,5,p);canvas.drawLine(-10,-2,-10,9,p);canvas.drawLine(10,-2,10,9,p);
            canvas.drawLine(-10,5,-5,2,p);canvas.drawLine(10,5,5,2,p);canvas.drawLine(-4,-6,4,-6,p);canvas.drawLine(0,-9,0,-3,p);
        } else if(id.equals("Kąt")) {
            canvas.drawLine(-10,9,11,9,p);canvas.drawLine(-10,9,7,-10,p);canvas.drawArc(new RectF(-21,-2,1,20),-48,48,false,p);
        } else if(id.equals("Gwint")) {
            canvas.drawOval(new RectF(-8,-11,8,-5),p);canvas.drawLine(-8,-8,-8,10,p);canvas.drawLine(8,-8,8,10,p);
            for(int i=0;i<4;i++)canvas.drawLine(-8,-4+4*i,8,-8+4*i,p);canvas.drawArc(new RectF(-8,7,8,13),0,180,false,p);
        } else {
            Path cube=new Path();cube.moveTo(-11,-3);cube.lineTo(4,-3);cube.lineTo(11,-9);cube.lineTo(-4,-9);cube.close();canvas.drawPath(cube,p);
            canvas.drawRect(-11,-3,4,10,p);canvas.drawLine(4,10,11,4,p);canvas.drawLine(11,4,11,-9,p);
            if(id.equals("Wyciągnij")){arrow(canvas,p,-1,-5,-1,-16);}
            else if(id.equals("Wytnij")||id.equals("Wycięcie")){arrow(canvas,p,-1,-15,-1,2);canvas.drawLine(-6,5,0,5,p);}
            else if(id.equals("Nowa bryła")){canvas.drawRect(6,6,14,14,p);canvas.drawLine(10,2,10,6,p);}
            else if(id.equals("Część wspólna")){p.setStyle(Paint.Style.FILL);canvas.drawRect(-2,1,4,8,p);p.setStyle(Paint.Style.STROKE);canvas.drawRect(-2,1,12,13,p);}
            else {canvas.drawLine(6,9,14,9,p);canvas.drawLine(10,5,10,13,p);}
        }
        canvas.restore();return true;
    }
    private static void arrow(Canvas c,Paint p,float x1,float y1,float x2,float y2) {
        c.drawLine(x1,y1,x2,y2,p);float direction=Math.signum(y2-y1);c.drawLine(x2,y2,x2-4,y2-4*direction,p);c.drawLine(x2,y2,x2+4,y2-4*direction,p);
    }
}
