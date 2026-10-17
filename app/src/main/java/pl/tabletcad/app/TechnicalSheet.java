package pl.tabletcad.app;

import android.graphics.*;
import java.util.*;
import pl.tabletcad.app.model.CadProject;

/** A4 landscape drawing in millimetres; same vector renderer for screen and PDF. */
public final class TechnicalSheet {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
    private final double[] segments;
    private final double[][] bounds=new double[4][4];
    private final double scale;
    private final String scaleLabel;
    private final double sectionFraction;
    public TechnicalSheet(double[] segments,double sectionFraction) {
        this.segments=segments;
        this.sectionFraction=sectionFraction;
        for(double[] b:bounds) {b[0]=b[1]=Double.POSITIVE_INFINITY;b[2]=b[3]=Double.NEGATIVE_INFINITY;}
        for(int i=0;i+5<segments.length;i+=6) {
            double[] b=bounds[(int)segments[i]];
            for(int j=2;j<=4;j+=2) {b[0]=Math.min(b[0],segments[i+j]);b[1]=Math.min(b[1],segments[i+j+1]);
                b[2]=Math.max(b[2],segments[i+j]);b[3]=Math.max(b[3],segments[i+j+1]);}
        }
        double fit=Double.POSITIVE_INFINITY;
        for(double[] b:bounds) if(Double.isFinite(b[0]))
            fit=Math.min(fit,Math.min(100/Math.max(.001,b[2]-b[0]),54/Math.max(.001,b[3]-b[1])));
        double chosen=Math.min(.001,fit);
        for(double s:new double[]{2,1,.5,.2,.1,.05,.02,.01,.005,.002,.001}) if(s<=fit) {chosen=s;break;}
        scale=chosen;
        scaleLabel=scale>=1 ? String.format(Locale.US,"%.0f:1",scale)
            : String.format(Locale.US,"1:%.0f",1/scale);
    }
    private void line(Canvas c,float x1,float y1,float x2,float y2,float width) {
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setColor(Color.BLACK);
        c.drawLine(x1,y1,x2,y2,paint);
    }
    private void text(Canvas c,String value,float x,float y,float size) {
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.BLACK);paint.setTextSize(size);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL));
        c.drawText(value,x,y,paint);
    }
    private float px(int v,double x) {return (float)((v==0||v==1?78:218)+(x-(bounds[v][0]+bounds[v][2])/2)*scale);}
    private float py(int v,double y) {return (float)((v==0||v==2?57:133)-(y-(bounds[v][1]+bounds[v][3])/2)*scale);}
    private void fittedText(Canvas c,String value,float x,float y,float size,float width) {
        paint.setTextSize(size);
        String fitted=value;
        while(fitted.length()>0&&paint.measureText(fitted)>width) fitted=fitted.substring(0,fitted.length()-1);
        if(!fitted.equals(value)&&fitted.length()>3) fitted=fitted.substring(0,fitted.length()-3)+"...";
        text(c,fitted,x,y,size);
    }
    public void draw(Canvas canvas,RectF area,CadProject project) {
        canvas.save();
        float factor=Math.min(area.width()/297,area.height()/210);
        canvas.translate(area.centerX()-297*factor/2,area.centerY()-210*factor/2);
        canvas.scale(factor,factor);
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);canvas.drawRect(0,0,297,210,paint);
        paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.35f);
        canvas.drawRect(10,10,287,200,paint);
        text(canvas,"TabletCAD - RYSUNEK TECHNICZNY",15,18,3.5f);
        text(canvas,"Rzutowanie europejskie · metoda pierwszego kąta",15,24,2.5f);
        String[] titles={"PRZÓD","GÓRA","BOK LEWY",String.format(Locale.US,"PRZEKRÓJ A-A · X %.1f%%",sectionFraction*100)};
        for(int view=0;view<4;view++) {
            if(!Double.isFinite(bounds[view][0])) continue;
            if(view==3) hatch(canvas,view);
            for(int hidden=1;hidden>=0;hidden--) {
                paint.setPathEffect(hidden==1?new DashPathEffect(new float[]{1.7f,1},0):null);
                for(int i=0;i+5<segments.length;i+=6) if((int)segments[i]==view&&(int)segments[i+1]==hidden)
                    line(canvas,px(view,segments[i+2]),py(view,segments[i+3]),
                        px(view,segments[i+4]),py(view,segments[i+5]),hidden==1?.15f:.3f);
            }
            paint.setPathEffect(new DashPathEffect(new float[]{4,1,0.5f,1},0));
            float x0=px(view,bounds[view][0])-3,x1=px(view,bounds[view][2])+3;
            float y0=py(view,bounds[view][3])-3,y1=py(view,bounds[view][1])+3;
            float cx=(x0+x1)/2,cy=(y0+y1)/2;
            line(canvas,x0,cy,x1,cy,.13f);line(canvas,cx,y0,cx,y1,.13f);
            paint.setPathEffect(null);
            text(canvas,titles[view],view==0||view==1?40:180,view==0||view==2?28:172,2.8f);
            if(view==0||view==2) dimension(canvas,x0+3,y1+6,x1-3,y1+6,
                bounds[view][2]-bounds[view][0]);
            if(view==0) {
                float cutX=(float)(x0+3+(x1-x0-6)*sectionFraction);
                paint.setPathEffect(new DashPathEffect(new float[]{4,1,.5f,1},0));
                line(canvas,cutX,y0-2,cutX,y1+2,.3f);paint.setPathEffect(null);
                text(canvas,"A",cutX+2,y0-3,3);text(canvas,"A",cutX+2,y1+11,3);
                float dx=x0-7;
                line(canvas,dx,y0+3,dx,y1-3,.15f);
                line(canvas,dx,y0+3,dx-1,y0+5,.2f);line(canvas,dx,y0+3,dx+1,y0+5,.2f);
                line(canvas,dx,y1-3,dx-1,y1-5,.2f);line(canvas,dx,y1-3,dx+1,y1-5,.2f);
                line(canvas,dx-2,y0+3,x0,y0+3,.15f);line(canvas,dx-2,y1-3,x0,y1-3,.15f);
                canvas.save();canvas.rotate(-90,dx-1,cy);
                text(canvas,String.format(Locale.US,"%.2f",bounds[view][3]-bounds[view][1]),dx-5,cy,2.5f);
                canvas.restore();
            }
        }
        line(canvas,10,180,287,180,.3f);
        line(canvas,170,180,170,200,.3f);line(canvas,235,180,235,200,.3f);
        line(canvas,170,190,287,190,.2f);
        fittedText(canvas,"Nazwa: "+project.name,14,185,3.2f,151);
        fittedText(canvas,"Nr rysunku: "+project.drawingNumber+"   Rewizja: "+project.drawingRevision,14,191,2.5f,151);
        fittedText(canvas,"Autor: "+project.drawingAuthor+"   Materiał: "+project.drawingMaterial,14,197,2.5f,151);
        text(canvas,"Skala "+scaleLabel,174,187,3);text(canvas,"A4 · 297 × 210",239,187,2.5f);
        text(canvas,"Jednostka: mm",174,197,2.5f);text(canvas,"Arkusz 1 / 1",239,197,2.5f);
        canvas.restore();
    }
    private void dimension(Canvas c,float x0,float y0,float x1,float y1,double value) {
        line(c,x0,y0-4,x0,y0+2,.15f);line(c,x1,y1-4,x1,y1+2,.15f);
        line(c,x0,y0,x1,y1,.15f);
        line(c,x0,y0,x0+2,y0-1,.2f);line(c,x0,y0,x0+2,y0+1,.2f);
        line(c,x1,y1,x1-2,y1-1,.2f);line(c,x1,y1,x1-2,y1+1,.2f);
        text(c,String.format(Locale.US,"%.2f",value),(x0+x1)/2-4,y0-1,2.5f);
    }
    private void hatch(Canvas c,int view) {
        double[] b=bounds[view];
        double spacing=2.5/scale;
        for(double level=b[0]+b[1];level<b[2]+b[3];level+=spacing) {
            ArrayList<Double> crossings=new ArrayList<>();
            for(int i=0;i+5<segments.length;i+=6) if((int)segments[i]==view) {
                double q0=segments[i+2]+segments[i+3],q1=segments[i+4]+segments[i+5];
                if((q0<=level&&level<q1)||(q1<=level&&level<q0)) {
                    double t=(level-q0)/(q1-q0);
                    crossings.add(segments[i+2]+t*(segments[i+4]-segments[i+2]));
                }
            }
            Collections.sort(crossings);
            for(int i=1;i<crossings.size();i+=2) {
                double x0=crossings.get(i-1),x1=crossings.get(i);
                line(c,px(view,x0),py(view,level-x0),px(view,x1),py(view,level-x1),.13f);
            }
        }
    }
}
