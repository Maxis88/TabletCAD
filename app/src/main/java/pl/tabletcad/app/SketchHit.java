package pl.tabletcad.app;

import java.util.List;
import pl.tabletcad.app.model.SketchPrimitive;

/**
 * Interior picking of closed sketch geometry, in sketch coordinates. A finger is much larger than
 * an outline, so a touch inside a circle or rectangle grabs it; when outlines nest, the smallest
 * enclosing entity wins (a hole inside a plate is picked before the plate).
 */
public final class SketchHit {
    private SketchHit() {}

    /** Outline polygon of a closed entity (u,v pairs), or null for open geometry. */
    public static double[] outline(SketchPrimitive p) {
        if(p==null||!p.closedGeometry())return null;
        if(p.isEllipse()) {
            double a=p.major();if(a<=0)return null;double ux=(p.x2-p.x1)/a,uy=(p.y2-p.y1)/a;double[] r=new double[128];
            for(int i=0;i<64;i++){double t=2*Math.PI*i/64,c=Math.cos(t)*a,s=Math.sin(t)*p.minor;r[2*i]=p.x1+ux*c-uy*s;r[2*i+1]=p.y1+uy*c+ux*s;}
            return r;
        }
        if(p.curves!=null)return SketchCurves.samples(p.curves,96);
        if(p.vertices!=null)return p.vertices.clone();
        if(p.type.equals("Okrąg")) {
            double radius=Math.hypot(p.x2-p.x1,p.y2-p.y1);double[] r=new double[128];
            for(int i=0;i<64;i++){double t=2*Math.PI*i/64;r[2*i]=p.x1+radius*Math.cos(t);r[2*i+1]=p.y1+radius*Math.sin(t);}
            return r;
        }
        if(p.type.equals("Prostokąt"))return new double[]{p.x1,p.y1,p.x2,p.y1,p.x2,p.y2,p.x1,p.y2};
        return null;
    }
    /** Even-odd containment. */
    public static boolean inside(SketchPrimitive p,double u,double v) {
        if(p!=null&&p.type.equals("Okrąg")&&p.curves==null&&p.vertices==null)return Math.hypot(u-p.x1,v-p.y1)<Math.hypot(p.x2-p.x1,p.y2-p.y1);
        double[] o=outline(p);if(o==null||o.length<6)return false;
        boolean in=false;int n=o.length/2;
        for(int i=0,j=n-1;i<n;j=i++){double xi=o[2*i],yi=o[2*i+1],xj=o[2*j],yj=o[2*j+1];
            if((yi>v)!=(yj>v)&&u<(xj-xi)*(v-yi)/(yj-yi)+xi)in=!in;}
        return in;
    }
    public static double area(SketchPrimitive p) {
        double[] o=outline(p);if(o==null)return Double.POSITIVE_INFINITY;double s=0;int n=o.length/2;
        for(int i=0,j=n-1;i<n;j=i++)s+=o[2*j]*o[2*i+1]-o[2*i]*o[2*j+1];
        return Math.abs(s)/2;
    }
    /** Smallest closed entity containing the point, skipping ids for which {@code skip} is true. */
    public static SketchPrimitive smallestContaining(List<SketchPrimitive> sketch,double u,double v,java.util.function.Predicate<SketchPrimitive> skip) {
        SketchPrimitive best=null;double bestArea=Double.POSITIVE_INFINITY;
        for(SketchPrimitive p:sketch) {
            if(skip!=null&&skip.test(p))continue;
            if(!inside(p,u,v))continue;double a=area(p);
            if(a<bestArea){bestArea=a;best=p;}
        }
        return best;
    }
}
