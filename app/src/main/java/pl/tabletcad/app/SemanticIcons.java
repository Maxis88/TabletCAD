package pl.tabletcad.app;

import android.graphics.*;

/** Native vector glyphs; the operation silhouette and modifier share the same visual scale. */
public final class SemanticIcons {
    private SemanticIcons() {}
    public static boolean draw(Canvas canvas,String id,float x,float y,float scale,int color) {
        if(drawView(canvas,id,x,y,scale,color))return true;
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
    /** Standard-view glyphs: the same cube, with the face seen by the camera filled. */
    private static boolean drawView(Canvas canvas,String id,float x,float y,float scale,int color) {
        if(id.equals("Szyk liniowy")||id.equals("Szyk kołowy")) {
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
            canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);
            if(id.equals("Szyk liniowy")) {
                for(int i=0;i<3;i++)canvas.drawCircle(-9+9*i,0,3.2f,p);
                p.setStrokeWidth(1);canvas.drawLine(-12,7,12,7,p);canvas.drawLine(12,7,9,5,p);canvas.drawLine(12,7,9,9,p);
            }else {
                p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawCircle(0,0,8,p);p.setPathEffect(null);
                for(int i=0;i<6;i++){double a=Math.PI/3*i;canvas.drawCircle((float)Math.cos(a)*8,(float)Math.sin(a)*8,2.6f,p);}
                canvas.drawPoint(0,0,p);
            }
            canvas.restore();return true;
        }
        if(id.equals("Obróć widok")||id.equals("Widok na szkic")) {
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);
            if(id.equals("Obróć widok")) {
                // Sketch plane seen at an angle with an orbit arrow around it.
                Path plane=new Path();plane.moveTo(-8,3);plane.lineTo(4,3);plane.lineTo(9,-3);plane.lineTo(-3,-3);plane.close();canvas.drawPath(plane,p);
                canvas.drawArc(new RectF(-12,-11,12,11),200,250,false,p);
                canvas.drawLine(10.3f,-6,12,-10,p);canvas.drawLine(10.3f,-6,6.5f,-7.5f,p);
            } else {
                // Sketch plane square to the eye: square, normal arrow pointing at the viewer.
                canvas.drawRect(-10,-10,10,10,p);canvas.drawCircle(0,0,4,p);
                p.setStyle(Paint.Style.FILL);canvas.drawCircle(0,0,1.6f,p);p.setStyle(Paint.Style.STROKE);
                canvas.drawLine(-13,0,-10,0,p);canvas.drawLine(10,0,13,0,p);canvas.drawLine(0,-13,0,-10,p);canvas.drawLine(0,10,0,13,p);
            }
            canvas.restore();return true;
        }
        if(id.equals("Przedłuż")) {
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
            canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);
            canvas.drawLine(10,-11,10,11,p);canvas.drawLine(-12,4,-2,4,p);canvas.drawCircle(-2,4,1.5f,p);
            p.setPathEffect(new DashPathEffect(new float[]{2.5f,2f},0));canvas.drawLine(1,4,9,4,p);p.setPathEffect(null);
            canvas.drawLine(9,4,6,1,p);canvas.drawLine(9,4,6,7,p);
            canvas.restore();return true;
        }
        if(id.equals("Odsunięcie")) {
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
            canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);
            canvas.drawRect(-5,-3,5,5,p);p.setPathEffect(new DashPathEffect(new float[]{2.5f,2f},0));canvas.drawRoundRect(new RectF(-11,-9,11,11),3,3,p);
            p.setPathEffect(null);canvas.drawLine(5,-3,9,-7,p);canvas.drawLine(9,-7,6,-7,p);canvas.drawLine(9,-7,9,-4,p);
            canvas.restore();return true;
        }
        if(!java.util.Arrays.asList("Izometryczny","Przód","Góra","Prawy bok","Rzut prostokątny").contains(id))return false;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
        Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);fill.setColor(color);fill.setAlpha(110);fill.setStyle(Paint.Style.FILL);
        canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);
        if(id.equals("Rzut prostokątny")) {
            canvas.drawRect(-11,-8,1,4,p);canvas.drawRect(-5,-2,7,10,p);
            canvas.drawLine(-11,-8,-5,-2,p);canvas.drawLine(1,-8,7,-2,p);canvas.drawLine(1,4,7,10,p);canvas.drawLine(-11,4,-5,10,p);
            canvas.drawLine(9,-11,13,-11,p);canvas.drawLine(9,-7,13,-7,p);
        } else {
            Path front=new Path();front.moveTo(-10,-3);front.lineTo(4,-3);front.lineTo(4,11);front.lineTo(-10,11);front.close();
            Path top=new Path();top.moveTo(-10,-3);top.lineTo(-3,-10);top.lineTo(11,-10);top.lineTo(4,-3);top.close();
            Path side=new Path();side.moveTo(4,-3);side.lineTo(11,-10);side.lineTo(11,4);side.lineTo(4,11);side.close();
            if(id.equals("Przód"))canvas.drawPath(front,fill);
            else if(id.equals("Góra"))canvas.drawPath(top,fill);
            else if(id.equals("Prawy bok"))canvas.drawPath(side,fill);
            else {fill.setAlpha(60);canvas.drawPath(front,fill);fill.setAlpha(110);canvas.drawPath(top,fill);fill.setAlpha(160);canvas.drawPath(side,fill);}
            canvas.drawPath(front,p);canvas.drawPath(top,p);canvas.drawPath(side,p);
        }
        canvas.restore();return true;
    }
    private static void arrow(Canvas c,Paint p,float x1,float y1,float x2,float y2) {
        c.drawLine(x1,y1,x2,y2,p);float direction=Math.signum(y2-y1);c.drawLine(x2,y2,x2-4,y2-4*direction,p);c.drawLine(x2,y2,x2+4,y2-4*direction,p);
    }
}
