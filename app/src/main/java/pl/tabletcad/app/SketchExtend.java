package pl.tabletcad.app;

import java.util.List;
import pl.tabletcad.app.model.SketchPrimitive;

/**
 * Extends a straight sketch line along its own direction to the nearest boundary formed by other
 * sketch geometry (lines, rectangles, polygons, circles, arcs, line/arc contours and splines).
 */
public final class SketchExtend {
    private SketchExtend() {}
    private static final double MIN_STEP=1e-6;

    /** Which end to move: START, END, or AUTO (the end with the shorter valid extension). */
    public enum End { START, END, AUTO }

    public static SketchPrimitive extend(SketchPrimitive line,List<SketchPrimitive> sketch,End end) {
        if(line!=null&&(line.isEllipse()||line.isEllipseArc()))throw new IllegalArgumentException("Przedłużać można linie, nie elipsy");
        if(line==null||!(line.type.equals("Linia")||line.type.equals("Pomocnicza")))throw new IllegalArgumentException("Przedłużać można tylko linie");
        if(line.driving!=null)throw new IllegalArgumentException("Linia ma zablokowany wymiar długości — odblokuj go przed przedłużeniem");
        double length=Math.hypot(line.x2-line.x1,line.y2-line.y1);if(length<.01)throw new IllegalArgumentException("Zerowa linia");
        double ux=(line.x2-line.x1)/length,uy=(line.y2-line.y1)/length;
        double atEnd=end==End.START?Double.NaN:nearest(line.x2,line.y2,ux,uy,line.id,sketch);
        double atStart=end==End.END?Double.NaN:nearest(line.x1,line.y1,-ux,-uy,line.id,sketch);
        boolean useEnd;
        if(end==End.END)useEnd=true;else if(end==End.START)useEnd=false;
        else {
            if(Double.isNaN(atEnd)&&Double.isNaN(atStart))throw new IllegalArgumentException("Na przedłużeniu linii nie ma innej geometrii szkicu");
            useEnd=Double.isNaN(atStart)||(!Double.isNaN(atEnd)&&atEnd<=atStart);
        }
        double t=useEnd?atEnd:atStart;
        if(Double.isNaN(t))throw new IllegalArgumentException("Na przedłużeniu wybranego końca nie ma innej geometrii szkicu");
        double x1=line.x1,y1=line.y1,x2=line.x2,y2=line.y2;
        if(useEnd){x2+=ux*t;y2+=uy*t;}else{x1-=ux*t;y1-=uy*t;}
        if(Math.abs(x1)>5000||Math.abs(y1)>5000||Math.abs(x2)>5000||Math.abs(y2)>5000)throw new IllegalArgumentException("Przedłużenie wykracza poza zakres szkicu ±5000 mm");
        return new SketchPrimitive(line.id,line.type.equals("Pomocnicza")?"Linia":line.type,x1,y1,x2,y2).withConstruction(line.construction);
    }

    /** Smallest positive distance along the ray (px,py)+t(ux,uy) to other geometry, or NaN. */
    static double nearest(double px,double py,double ux,double uy,String self,List<SketchPrimitive> sketch) {
        double best=Double.POSITIVE_INFINITY;
        for(SketchPrimitive p:sketch) {
            if(p.id.equals(self)||p.isText())continue;
            double t=hit(p,px,py,ux,uy);if(t<best)best=t;
        }
        return Double.isInfinite(best)?Double.NaN:best;
    }

    private static double hit(SketchPrimitive p,double px,double py,double ux,double uy) {
        double best=Double.POSITIVE_INFINITY;
        if(p.curves!=null) {
            for(int o=0;o<p.curves.length;o+=7) {
                if(p.curves[o]==0)best=Math.min(best,segment(px,py,ux,uy,p.curves[o+1],p.curves[o+2],p.curves[o+5],p.curves[o+6]));
                else {SketchCurves.Arc a=SketchCurves.arc(p.curves,o);
                    for(double t:circle(px,py,ux,uy,a.x,a.y,a.r)){double[] q={px+ux*t,py+uy*t};if(t>MIN_STEP&&a.contains(q[0],q[1]))best=Math.min(best,t);}}
            }
            return best;
        }
        if(SketchSpline.is(p))return spline(p.vertices,px,py,ux,uy);
        if(p.isEllipse()||p.isEllipseArc()){for(double[] e:SketchTrimmer.curvePolyline(p))best=Math.min(best,segment(px,py,ux,uy,e[0],e[1],e[2],e[3]));return best;}
        if(p.vertices!=null) {
            int n=p.vertices.length/2;double[] v=p.vertices;
            for(int i=0;i<n;i++){int j=(i+1)%n;best=Math.min(best,segment(px,py,ux,uy,v[2*i],v[2*i+1],v[2*j],v[2*j+1]));}
            return best;
        }
        switch(p.type) {
            case "Linia": case "Pomocnicza": return segment(px,py,ux,uy,p.x1,p.y1,p.x2,p.y2);
            case "Prostokąt": {
                double[][] c={{p.x1,p.y1},{p.x2,p.y1},{p.x2,p.y2},{p.x1,p.y2}};
                for(int i=0;i<4;i++)best=Math.min(best,segment(px,py,ux,uy,c[i][0],c[i][1],c[(i+1)%4][0],c[(i+1)%4][1]));
                return best;
            }
            case "Okrąg": {
                for(double t:circle(px,py,ux,uy,p.x1,p.y1,Math.hypot(p.x2-p.x1,p.y2-p.y1)))if(t>MIN_STEP)best=Math.min(best,t);
                return best;
            }
            default: return best;
        }
    }

    private static double segment(double px,double py,double ux,double uy,double ax,double ay,double bx,double by) {
        double wx=bx-ax,wy=by-ay,det=ux*wy-uy*wx;
        if(Math.abs(det)<1e-12*Math.max(1,Math.hypot(wx,wy)))return Double.POSITIVE_INFINITY;
        double t=((ax-px)*wy-(ay-py)*wx)/det,s=((ax-px)*uy-(ay-py)*ux)/det;
        return t>MIN_STEP&&s>=-1e-9&&s<=1+1e-9?t:Double.POSITIVE_INFINITY;
    }

    private static double[] circle(double px,double py,double ux,double uy,double cx,double cy,double r) {
        double b=2*(ux*(px-cx)+uy*(py-cy)),c=(px-cx)*(px-cx)+(py-cy)*(py-cy)-r*r,disc=b*b-4*c;
        if(disc<0)return new double[0];double root=Math.sqrt(disc);return new double[]{(-b-root)/2,(-b+root)/2};
    }

    /** Spline crossing: coarse sampling, then bisection on the exact cubic span parameter. */
    private static double spline(double[] points,double px,double py,double ux,double uy) {
        double[] spans=SketchSpline.spans(points);double best=Double.POSITIVE_INFINITY;final int steps=64;
        for(int o=0;o<spans.length;o+=8) {
            double prevT=0,prevF=side(spans,o,0,px,py,ux,uy);
            for(int j=1;j<=steps;j++) {
                double t=(double)j/steps,f=side(spans,o,t,px,py,ux,uy);
                if(prevF==0||prevF*f<0) {
                    double lo=prevT,hi=t,flo=prevF;
                    for(int k=0;k<60&&flo!=0;k++){double mid=(lo+hi)/2,fm=side(spans,o,mid,px,py,ux,uy);if(flo*fm<=0)hi=mid;else{lo=mid;flo=fm;}}
                    double[] q=cubic(spans,o,flo==0?lo:(lo+hi)/2);double along=(q[0]-px)*ux+(q[1]-py)*uy;
                    if(along>MIN_STEP)best=Math.min(best,along);
                }
                prevT=t;prevF=f;
            }
        }
        return best;
    }
    private static double side(double[] s,int o,double t,double px,double py,double ux,double uy){double[] q=cubic(s,o,t);return ux*(q[1]-py)-uy*(q[0]-px);}
    private static double[] cubic(double[] s,int o,double t) {
        double u=1-t;double[] q=new double[2];
        for(int k=0;k<2;k++)q[k]=u*u*u*s[o+k]+3*u*u*t*s[o+2+k]+3*u*t*t*s[o+4+k]+t*t*t*s[o+6+k];
        return q;
    }
}
