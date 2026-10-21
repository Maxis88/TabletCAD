package pl.tabletcad.app;

import android.graphics.*;

/** Native vector glyphs; the operation silhouette and modifier share the same visual scale. */
public final class SemanticIcons {
    private SemanticIcons() {}
    public static boolean draw(Canvas canvas,String id,float x,float y,float scale,int color) {
        if(id.equals("Bryła"))id="Cieniowanie";
        else if(id.equals("Bryła z liniami niewidocznymi"))id="Krawędzie";
        if(drawConstruction(canvas,id,x,y,scale,color)||drawView(canvas,id,x,y,scale,color)||drawSolidCopy(canvas,id,x,y,scale,color))return true;
        if(id.equals("Zmierz kąt"))id="Kąt";
        if(!java.util.Arrays.asList("Napis","Splajn","Wielokąt","Kwadrat","Prostokąt od środka","Zaokrąglij szkic","Wymiary","Wyciągnij","Wytnij","Wycięcie","Połączenie","Dodanie","Nowa bryła","Część wspólna","Gwint","Kąt").contains(id))return false;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
        canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);
        if(id.equals("Napis")) {
            // Letters "AT" standing on a curved (circular) baseline.
            canvas.drawLine(-11,7,-5.5f,-10,p);canvas.drawLine(-5.5f,-10,0,7,p);canvas.drawLine(-8.6f,0,-2.4f,0,p);
            canvas.drawLine(3,-10,13,-10,p);canvas.drawLine(8,-10,8,7,p);
            p.setStrokeWidth(1.1f);p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawArc(new RectF(-16,8,16,22),200,140,false,p);p.setPathEffect(null);
        } else if(id.equals("Splajn")) {
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
        if(id.equals("Rzutuj")) {
            // Model edge above the sketch plane, dashed projectors, projected edge drawn on the plane.
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);
            Path plane=new Path();plane.moveTo(-12,6);plane.lineTo(6,6);plane.lineTo(12,12);plane.lineTo(-6,12);plane.close();p.setStrokeWidth(1.2f);canvas.drawPath(plane,p);
            p.setStrokeWidth(1.7f);canvas.drawLine(-6,-10,6,-6,p);
            p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawLine(-6,-10,-6,7,p);canvas.drawLine(6,-6,6,9,p);p.setPathEffect(null);
            p.setStrokeWidth(2.4f);canvas.drawLine(-6,8,6,10,p);
            canvas.restore();return true;
        }
        if(id.equals("Przekrój")) {
            // Cube cut by a section plane; the cut face is hatched.
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(1.5f);p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
            canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);
            Path cut=new Path();cut.moveTo(-2,-3);cut.lineTo(5,-10);cut.lineTo(5,4);cut.lineTo(-2,11);cut.close();
            canvas.drawLine(-11,-3,-2,-3,p);canvas.drawLine(-11,-3,-4,-10,p);canvas.drawLine(-4,-10,5,-10,p);canvas.drawLine(-11,-3,-11,11,p);canvas.drawLine(-11,11,-2,11,p);
            canvas.drawPath(cut,p);canvas.save();canvas.clipPath(cut);p.setStrokeWidth(1);for(int i=-14;i<16;i+=3)canvas.drawLine(-4,i,8,i-12,p);canvas.restore();
            p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawLine(5,-10,12,-10,p);canvas.drawLine(12,-10,12,4,p);canvas.drawLine(12,4,5,4,p);
            canvas.restore();return true;
        }
        if(id.equals("Cieniowanie")||id.equals("Krawędzie")||id.equals("Przezroczystość")) {
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(1.6f);p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
            Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);fill.setColor(color);fill.setStyle(Paint.Style.FILL);
            canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);
            Path front=new Path();front.moveTo(-10,-3);front.lineTo(4,-3);front.lineTo(4,11);front.lineTo(-10,11);front.close();
            Path top=new Path();top.moveTo(-10,-3);top.lineTo(-3,-10);top.lineTo(11,-10);top.lineTo(4,-3);top.close();
            Path side=new Path();side.moveTo(4,-3);side.lineTo(11,-10);side.lineTo(11,4);side.lineTo(4,11);side.close();
            if(id.equals("Cieniowanie")){fill.setAlpha(70);canvas.drawPath(front,fill);fill.setAlpha(130);canvas.drawPath(top,fill);fill.setAlpha(190);canvas.drawPath(side,fill);}
            else if(id.equals("Przezroczystość")){fill.setAlpha(45);canvas.drawPath(front,fill);canvas.drawPath(top,fill);canvas.drawPath(side,fill);}
            canvas.drawPath(front,p);canvas.drawPath(top,p);canvas.drawPath(side,p);
            if(!id.equals("Cieniowanie")) {
                // Hidden edges of the cube, dashed.
                p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));p.setStrokeWidth(1.1f);
                canvas.drawLine(-10,11,-3,4,p);canvas.drawLine(-3,4,11,4,p);canvas.drawLine(-3,4,-3,-10,p);
            }
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
    /** Construction planes/axis/point and face measurement glyphs (planes drawn as parallelograms). */
    private static boolean drawConstruction(Canvas canvas,String id,float x,float y,float scale,int color) {
        if(!java.util.Arrays.asList("Płaszczyzna odsunięta","Płaszczyzna pod kątem","Płaszczyzna przez 3 punkty","Oś konstrukcyjna","Punkt konstrukcyjny","Pole ściany","Elementy konstrukcyjne","Odległość brył","Nawisy","Połóż na stole","Komponent","Nowy komponent","Wstaw wystąpienie","Dosuń ściany","Kolizje","Lista części").contains(id))return false;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(1.6f);p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
        canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);
        if(id.equals("Płaszczyzna odsunięta")) {
            plane(canvas,p,0,6);p.setPathEffect(new DashPathEffect(new float[]{2.5f,2f},0));plane(canvas,p,0,-6);p.setPathEffect(null);
            canvas.drawLine(0,4,0,-5,p);canvas.drawLine(0,-5,-2.5f,-2.5f,p);canvas.drawLine(0,-5,2.5f,-2.5f,p);
        } else if(id.equals("Płaszczyzna pod kątem")) {
            plane(canvas,p,0,6);Path tilt=new Path();tilt.moveTo(-6,6);tilt.lineTo(-1,-11);tilt.lineTo(12,-11);tilt.lineTo(7,6);canvas.drawPath(tilt,p);
            canvas.drawArc(new RectF(-4,-4,12,14),-80,-40,false,p);
        } else if(id.equals("Płaszczyzna przez 3 punkty")) {
            plane(canvas,p,0,0);Paint f=new Paint(p);f.setStyle(Paint.Style.FILL);
            canvas.drawCircle(-6,3,2.2f,f);canvas.drawCircle(5,4,2.2f,f);canvas.drawCircle(2,-4,2.2f,f);
        } else if(id.equals("Oś konstrukcyjna")) {
            p.setPathEffect(new DashPathEffect(new float[]{6f,2.5f,1.5f,2.5f},0));canvas.drawLine(-12,12,12,-12,p);p.setPathEffect(null);
            canvas.drawLine(12,-12,6,-11,p);canvas.drawLine(12,-12,11,-6,p);canvas.drawOval(new RectF(-7,-3,3,5),p);
        } else if(id.equals("Punkt konstrukcyjny")) {
            canvas.drawCircle(0,0,6,p);canvas.drawLine(-11,0,11,0,p);canvas.drawLine(0,-11,0,11,p);
            Paint f=new Paint(p);f.setStyle(Paint.Style.FILL);canvas.drawCircle(0,0,1.8f,f);
        } else if(id.equals("Pole ściany")) {
            Path face=new Path();face.moveTo(-12,6);face.lineTo(-5,-7);face.lineTo(12,-7);face.lineTo(5,6);face.close();canvas.drawPath(face,p);
            canvas.save();canvas.clipPath(face);p.setStrokeWidth(1);for(int i=-20;i<20;i+=4)canvas.drawLine(i,8,i+10,-9,p);canvas.restore();
        } else if(id.equals("Komponent")||id.equals("Nowy komponent")) {
            canvas.drawRect(-11,-3,-1,7,p);canvas.drawRect(2,-9,12,1,p);p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawRoundRect(new RectF(-14,-12,14,10),3,3,p);p.setPathEffect(null);
            if(id.equals("Nowy komponent")){canvas.drawLine(8,6,8,12,p);canvas.drawLine(5,9,11,9,p);}
        } else if(id.equals("Wstaw wystąpienie")) {
            canvas.drawRect(-12,-2,-2,8,p);p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawRect(2,-10,12,0,p);p.setPathEffect(null);
            canvas.drawLine(-4,-4,1,-8,p);canvas.drawLine(1,-8,-2,-8,p);canvas.drawLine(1,-8,1,-5,p);
        } else if(id.equals("Dosuń ściany")) {
            canvas.drawRect(-12,-8,-3,8,p);canvas.drawRect(5,-8,12,8,p);canvas.drawLine(-1,0,4,0,p);canvas.drawLine(4,0,1,-3,p);canvas.drawLine(4,0,1,3,p);
        } else if(id.equals("Kolizje")) {
            canvas.drawRect(-12,-8,3,6,p);canvas.drawRect(-3,-3,12,11,p);Paint f=new Paint(p);f.setStyle(Paint.Style.FILL);f.setAlpha(110);canvas.drawRect(-3,-3,3,6,f);
        } else if(id.equals("Lista części")) {
            for(int i=0;i<4;i++){float yy=-9+i*6;canvas.drawRect(-12,yy-1.5f,-9,yy+1.5f,p);canvas.drawLine(-5,yy,12,yy,p);}
        } else if(id.equals("Połóż na stole")) {
            canvas.drawLine(-12,11,12,11,p);canvas.drawRect(-6,1,8,11,p);
            canvas.drawArc(new RectF(-12,-12,6,6),200,110,false,p);canvas.drawLine(1,-11,3,-7,p);canvas.drawLine(1,-11,-3,-9,p);
        } else if(id.equals("Nawisy")) {
            // Part on a build plate with a hatched overhang under its arm.
            canvas.drawLine(-12,11,12,11,p);canvas.drawLine(-8,11,-8,-9,p);canvas.drawLine(-8,-9,10,-9,p);canvas.drawLine(10,-9,10,-3,p);canvas.drawLine(10,-3,-2,-3,p);canvas.drawLine(-2,-3,-2,11,p);
            p.setStrokeWidth(1);for(int i=0;i<4;i++)canvas.drawLine(1+i*3,-1,3+i*3,-3,p);p.setPathEffect(new DashPathEffect(new float[]{1.5f,1.5f},0));canvas.drawLine(8,-1,8,10,p);p.setPathEffect(null);
        } else if(id.equals("Odległość brył")) {
            canvas.drawRect(-12,-2,-4,8,p);canvas.drawRect(4,-10,12,0,p);
            p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawLine(-4,-2,4,0,p);p.setPathEffect(null);
            canvas.drawLine(-6,-8,2,-10,p);canvas.drawLine(-6,-8,-4,-11,p);
        } else {
            plane(canvas,p,0,4);p.setPathEffect(new DashPathEffect(new float[]{4f,2f,1.2f,2f},0));canvas.drawLine(0,-12,0,10,p);p.setPathEffect(null);
        }
        canvas.restore();return true;
    }
    private static void plane(Canvas c,Paint p,float x,float y){Path path=new Path();path.moveTo(x-12,y+5);path.lineTo(x-6,y-5);path.lineTo(x+12,y-5);path.lineTo(x+6,y+5);path.close();c.drawPath(path,p);}
    /** Small isometric block used by the 3D pattern, mirror and shell glyphs. */
    private static void block(Canvas c,Paint p,float x,float y,float s) {
        Path top=new Path();top.moveTo(x-s,y-s*.3f);top.lineTo(x,y-s*.8f);top.lineTo(x+s,y-s*.3f);top.lineTo(x,y+s*.2f);top.close();c.drawPath(top,p);
        c.drawLine(x-s,y-s*.3f,x-s,y+s*.7f,p);c.drawLine(x+s,y-s*.3f,x+s,y+s*.7f,p);c.drawLine(x,y+s*.2f,x,y+s*1.2f,p);
        c.drawLine(x-s,y+s*.7f,x,y+s*1.2f,p);c.drawLine(x,y+s*1.2f,x+s,y+s*.7f,p);
    }
    private static boolean drawSolidCopy(Canvas canvas,String id,float x,float y,float scale,int color) {
        if(!java.util.Arrays.asList("Lustro operacji","Szyk liniowy operacji","Szyk kołowy operacji","Powłoka","Otwór","Pochyl ściany","Odsuń ścianę","Przesuń bryłę","Obróć bryłę","Skaluj bryłę","Połącz bryły","Odejmij bryły","Część wspólna brył","Podziel bryłę","Przejście","Przeciągnij","Położenie","Szyk po ścieżce","Punkt","Lustro bryły","Szyk liniowy bryły","Szyk kołowy bryły","Elipsa").contains(id))return false;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStrokeWidth(1.5f);p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
        canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);
        if(id.equals("Lustro operacji")) {
            // Two solids symmetric about a dashed mirror plane.
            block(canvas,p,-7,-2,5);block(canvas,p,7,-2,5);
            p.setPathEffect(new DashPathEffect(new float[]{2.5f,2f},0));canvas.drawLine(0,-13,0,13,p);p.setPathEffect(null);
            canvas.drawLine(-4,11,4,11,p);canvas.drawLine(-4,11,-2,9,p);canvas.drawLine(4,11,2,9,p);
        } else if(id.equals("Szyk liniowy operacji")) {
            // Repeated solids along one direction with the pattern arrow.
            for(int i=0;i<3;i++)block(canvas,p,-9+9*i,-4,3.6f);
            p.setStrokeWidth(1.1f);canvas.drawLine(-12,10,12,10,p);canvas.drawLine(12,10,9,8,p);canvas.drawLine(12,10,9,12,p);
        } else if(id.equals("Szyk kołowy operacji")) {
            // Solids arranged around a rotation axis.
            p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawCircle(0,0,9,p);canvas.drawLine(0,-13,0,13,p);p.setPathEffect(null);
            for(int i=0;i<4;i++){double a=Math.PI/2*i+Math.PI/4;block(canvas,p,(float)Math.cos(a)*9,(float)Math.sin(a)*9-2,2.8f);}
        } else if(id.equals("Pochyl ściany")) {
            // Tapered side wall leaning from a neutral base line, with the draft angle arc.
            canvas.drawLine(-12,10,12,10,p);canvas.drawLine(-8,10,-4,-10,p);canvas.drawLine(8,10,4,-10,p);canvas.drawLine(-4,-10,4,-10,p);
            p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawLine(-8,10,-8,-10,p);p.setPathEffect(null);
            canvas.drawArc(new RectF(-16,-6,0,10),-90,22,false,p);
        } else if(id.equals("Odsuń ścianę")) {
            // Press-pull: a block whose top face is lifted along its normal (dashed original face, arrow).
            block(canvas,p,0,2,9);
            p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));Path face=new Path();face.moveTo(-9,-8.3f);face.lineTo(0,-12.8f);face.lineTo(9,-8.3f);face.lineTo(0,-3.8f);face.close();canvas.drawPath(face,p);p.setPathEffect(null);
            canvas.drawLine(0,-5.2f,0,-14,p);canvas.drawLine(0,-14,-2.5f,-11,p);canvas.drawLine(0,-14,2.5f,-11,p);
        } else if(id.equals("Punkt")) {
            // Sketch point: a cross with a dot on a faint sketch line.
            p.setStrokeWidth(1.1f);p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawLine(-13,9,13,9,p);p.setPathEffect(null);
            p.setStrokeWidth(1.5f);canvas.drawLine(-7,0,7,0,p);canvas.drawLine(0,-7,0,7,p);
            Paint dot=new Paint(p);dot.setStyle(Paint.Style.FILL);canvas.drawCircle(0,0,2.2f,dot);
        } else if(id.equals("Szyk po ścieżce")) {
            // Small squares repeated along a dashed curved path.
            p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));Path way=new Path();way.moveTo(-13,9);way.cubicTo(-6,-12,6,12,13,-9);canvas.drawPath(way,p);p.setPathEffect(null);
            canvas.drawRect(-15,6,-10,11,p);canvas.drawRect(-2.5f,-2.5f,2.5f,2.5f,p);canvas.drawRect(10,-11,15,-6,p);
        } else if(id.equals("Położenie")) {
            // An element placed by X and Y distances from a reference corner (dimension lines with ticks).
            canvas.drawLine(-12,12,-12,-12,p);canvas.drawLine(-12,12,12,12,p);canvas.drawCircle(4,-3,5,p);
            p.setStrokeWidth(1.1f);canvas.drawLine(-12,-3,-1,-3,p);canvas.drawLine(-3,-5,-1,-3,p);canvas.drawLine(-3,-1,-1,-3,p);
            canvas.drawLine(4,12,4,2,p);canvas.drawLine(2,4,4,2,p);canvas.drawLine(6,4,4,2,p);
        } else if(id.equals("Elipsa")) {
            canvas.save();canvas.rotate(-20);canvas.drawOval(new RectF(-12,-7,12,7),p);canvas.restore();p.setStrokeWidth(1);canvas.drawPoint(0,0,p);
            p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawLine(0,0,11.3f,-4.1f,p);p.setPathEffect(null);
        } else if(id.equals("Przejście")) {
            // Loft: a rectangle below blending into a circle above.
            Path base=new Path();base.moveTo(-11,9);base.lineTo(3,9);base.lineTo(10,5);base.lineTo(-4,5);base.close();canvas.drawPath(base,p);
            canvas.drawOval(new RectF(-5,-12,5,-7),p);
            p.setStrokeWidth(1.1f);canvas.drawLine(-11,9,-5,-9.5f,p);canvas.drawLine(10,5,5,-9.5f,p);canvas.drawLine(3,9,2,-7.5f,p);
        } else if(id.equals("Przeciągnij")) {
            // Sweep: a circular profile carried along a curved dashed path.
            p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));Path path=new Path();path.moveTo(-12,10);path.cubicTo(-12,-2,0,-2,12,-10);canvas.drawPath(path,p);p.setPathEffect(null);
            canvas.drawOval(new RectF(-15,5,-9,15),p);
            Path tube=new Path();tube.moveTo(-9,7);tube.cubicTo(-9,-4,2,-5,10,-13);canvas.drawPath(tube,p);
        } else if(id.equals("Lustro bryły")) {
            block(canvas,p,-7,-2,5);block(canvas,p,7,-2,5);p.setPathEffect(new DashPathEffect(new float[]{2.5f,2f},0));canvas.drawLine(0,-13,0,13,p);p.setPathEffect(null);
            Paint fill=new Paint(p);fill.setStyle(Paint.Style.FILL);canvas.drawCircle(0,-13,1.6f,fill);
        } else if(id.equals("Szyk liniowy bryły")) {
            for(int i=0;i<3;i++)block(canvas,p,-9+9*i,-1,4.2f);
        } else if(id.equals("Szyk kołowy bryły")) {
            p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawCircle(0,0,9,p);p.setPathEffect(null);
            for(int i=0;i<3;i++){double a=2*Math.PI/3*i-Math.PI/2;block(canvas,p,(float)Math.cos(a)*9,(float)Math.sin(a)*9-2,3.4f);}
        } else if(id.equals("Podziel bryłę")) {
            // Block split by a dashed plane into two separated parts.
            block(canvas,p,-6,-1,5);block(canvas,p,6,-1,5);
            p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawLine(0,-13,0,13,p);p.setPathEffect(null);
        } else if(id.equals("Przesuń bryłę")) {
            block(canvas,p,-4,-1,6);p.setStrokeWidth(1.2f);canvas.drawLine(4,8,13,8,p);canvas.drawLine(13,8,10,6,p);canvas.drawLine(13,8,10,10,p);canvas.drawLine(-12,-6,-12,-14,p);canvas.drawLine(-12,-14,-14,-11,p);canvas.drawLine(-12,-14,-10,-11,p);
        } else if(id.equals("Obróć bryłę")) {
            block(canvas,p,0,-1,6);p.setStrokeWidth(1.2f);canvas.drawArc(new RectF(-13,-13,13,13),200,140,false,p);canvas.drawLine(10.5f,-7.5f,12.3f,-11.5f,p);canvas.drawLine(10.5f,-7.5f,6.5f,-8.5f,p);
        } else if(id.equals("Skaluj bryłę")) {
            block(canvas,p,-5,1,4);p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));block(canvas,p,2,-3,8);p.setPathEffect(null);
        } else if(id.equals("Połącz bryły")||id.equals("Odejmij bryły")||id.equals("Część wspólna brył")) {
            // Two overlapping solids; the resulting region is filled.
            Paint fill=new Paint(p);fill.setStyle(Paint.Style.FILL);fill.setAlpha(120);
            RectF a=new RectF(-12,-8,4,8),b=new RectF(-4,-4,12,12);
            if(id.equals("Połącz bryły")){canvas.drawRect(a,fill);canvas.drawRect(b,fill);}
            else if(id.equals("Odejmij bryły")){canvas.save();canvas.clipOutRect(b);canvas.drawRect(a,fill);canvas.restore();}
            else {canvas.drawRect(new RectF(-4,-4,4,8),fill);}
            canvas.drawRect(a,p);if(id.equals("Odejmij bryły"))p.setPathEffect(new DashPathEffect(new float[]{2f,2f},0));canvas.drawRect(b,p);p.setPathEffect(null);
        } else if(id.equals("Otwór")) {
            // Plate with a drilled hole: opening on the top face, dashed bore and drill point below.
            Path top=new Path();top.moveTo(-12,-2);top.lineTo(0,-9);top.lineTo(12,-2);top.lineTo(0,5);top.close();canvas.drawPath(top,p);
            canvas.drawLine(-12,-2,-12,4,p);canvas.drawLine(12,-2,12,4,p);canvas.drawLine(0,5,0,11,p);canvas.drawLine(-12,4,0,11,p);canvas.drawLine(0,11,12,4,p);
            canvas.drawOval(new RectF(-4.5f,-4.6f,4.5f,-.4f),p);
            p.setPathEffect(new DashPathEffect(new float[]{1.6f,1.6f},0));canvas.drawLine(-4.5f,-2.5f,-4.5f,6,p);canvas.drawLine(4.5f,-2.5f,4.5f,6,p);canvas.drawLine(-4.5f,6,0,9,p);canvas.drawLine(4.5f,6,0,9,p);p.setPathEffect(null);
        } else {
            // Shell: open block with the hollow interior and the wall thickness visible.
            Path outer=new Path();outer.moveTo(-11,-4);outer.lineTo(0,-10);outer.lineTo(11,-4);outer.lineTo(0,2);outer.close();canvas.drawPath(outer,p);
            Path inner=new Path();inner.moveTo(-7,-4);inner.lineTo(0,-7.8f);inner.lineTo(7,-4);inner.lineTo(0,-.2f);inner.close();canvas.drawPath(inner,p);
            canvas.drawLine(-11,-4,-11,7,p);canvas.drawLine(11,-4,11,7,p);canvas.drawLine(0,2,0,13,p);canvas.drawLine(-11,7,0,13,p);canvas.drawLine(0,13,11,7,p);
            canvas.drawLine(-7,-4,-7,1,p);canvas.drawLine(0,-.2f,0,5,p);canvas.drawLine(-7,1,0,5,p);
        }
        canvas.restore();return true;
    }
    private static void arrow(Canvas c,Paint p,float x1,float y1,float x2,float y2) {
        c.drawLine(x1,y1,x2,y2,p);float direction=Math.signum(y2-y1);c.drawLine(x2,y2,x2-4,y2-4*direction,p);c.drawLine(x2,y2,x2+4,y2-4*direction,p);
    }
}
