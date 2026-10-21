package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.SketchPrimitive;

/**
 * Linear and circular patterns of sketch geometry. Copies are exact rigid transforms of the
 * source with new identities; a rotated rectangle becomes an equivalent closed polygon.
 */
public final class SketchPattern {
    private SketchPattern() {}
    public static final int MAX_INSTANCES=200;

    /** Rigid transform: rotate by angle (radians) about (cx,cy), then translate by (dx,dy). */
    public static SketchPrimitive transform(SketchPrimitive s,String id,double cx,double cy,double angle,double dx,double dy) {
        if(s.isEllipseArc())return EllipseArc.map(s,id,cx,cy,angle,1,dx,dy);
        if(s.isText()){SketchPrimitive t=SketchTransform.text(s,cx,cy,angle,1,dx,dy);return SketchPrimitive.text(id,t.text(),t.textParams(),t.textLoops());}
        double c=Math.cos(angle),n=Math.sin(angle);
        java.util.function.BiFunction<Double,Double,double[]> map=(x,y)->{
            double px=cx+(x-cx)*c-(y-cy)*n+dx,py=cy+(x-cx)*n+(y-cy)*c+dy;
            if(!Double.isFinite(px)||!Double.isFinite(py)||Math.abs(px)>5000||Math.abs(py)>5000)throw new IllegalArgumentException("Szyk wykracza poza zakres szkicu ±5000 mm");
            return new double[]{px,py};
        };
        SketchPrimitive result;
        if(s.isEllipse()){double[] a=map.apply(s.x1,s.y1),b=map.apply(s.x2,s.y2);result=SketchPrimitive.ellipse(id,a[0],a[1],b[0],b[1],s.minor);}
        else if(s.curves!=null) {
            double[] r=s.curves.clone();
            for(int i=0;i<r.length;i+=7)for(int j:new int[]{1,3,5}){if(j==3&&r[i]==0)continue;double[] p=map.apply(r[i+j],r[i+j+1]);r[i+j]=p[0];r[i+j+1]=p[1];}
            result=new SketchPrimitive(id,r,s.closedGeometry());
        }else if(s.vertices!=null||(s.type.equals("Prostokąt")&&Math.abs(Math.sin(angle))>1e-12&&Math.abs(Math.cos(angle))>1e-12)) {
            double[] v=s.vertices!=null?s.vertices.clone():new double[]{s.x1,s.y1,s.x2,s.y1,s.x2,s.y2,s.x1,s.y2};
            for(int i=0;i<v.length;i+=2){double[] p=map.apply(v[i],v[i+1]);v[i]=p[0];v[i+1]=p[1];}
            result=SketchSpline.is(s)?new SketchPrimitive(id,v,"Splajn"):new SketchPrimitive(id,v);
        }else {
            double[] a=map.apply(s.x1,s.y1),b=map.apply(s.x2,s.y2);
            if(s.type.equals("Prostokąt"))result=new SketchPrimitive(id,"Prostokąt",Math.min(a[0],b[0]),Math.min(a[1],b[1]),Math.max(a[0],b[0]),Math.max(a[1],b[1]));
            else if(s.type.equals("Linia")||s.type.equals("Pomocnicza")||s.type.equals("Okrąg"))result=new SketchPrimitive(id,s.type.equals("Pomocnicza")?"Linia":s.type,a[0],a[1],b[0],b[1]);
            else if(s.isPoint())result=SketchPrimitive.point(id,a[0],a[1]);
            else throw new IllegalArgumentException("Nieobsługiwany typ geometrii szyku: "+s.type);
        }
        return result.withConstruction(s.construction);
    }

    /** count instances in total (including the source); copies are offset by i*(dx,dy). */
    public static List<SketchPrimitive> linear(SketchPrimitive s,int count,double dx,double dy) {
        validateCount(count);
        if(!Double.isFinite(dx)||!Double.isFinite(dy)||Math.hypot(dx,dy)<.001)throw new IllegalArgumentException("Odstęp szyku musi być niezerowy");
        List<SketchPrimitive> result=new ArrayList<>();
        for(int i=1;i<count;i++)result.add(transform(s,UUID.randomUUID().toString(),0,0,0,dx*i,dy*i));
        return result;
    }

    /** Rectangular grid columns×rows (including the source) with spacings along X and Y. */
    public static List<SketchPrimitive> grid(SketchPrimitive s,int columns,int rows,double dx,double dy) {
        if(columns<1||rows<1||columns*rows<2||columns*rows>MAX_INSTANCES)throw new IllegalArgumentException("Szyk: łącznie 2–"+MAX_INSTANCES+" elementów");
        if((columns>1&&Math.abs(dx)<.001)||(rows>1&&Math.abs(dy)<.001))throw new IllegalArgumentException("Odstęp szyku musi być niezerowy");
        List<SketchPrimitive> result=new ArrayList<>();
        for(int r=0;r<rows;r++)for(int c=0;c<columns;c++)if(r>0||c>0)result.add(transform(s,UUID.randomUUID().toString(),0,0,0,dx*c,dy*r));
        return result;
    }

    /** count instances (including the source) spread over totalDegrees about (cx,cy); 360° divides evenly. */
    public static List<SketchPrimitive> circular(SketchPrimitive s,int count,double cx,double cy,double totalDegrees) {
        validateCount(count);
        if(!Double.isFinite(totalDegrees)||Math.abs(totalDegrees)<.01||Math.abs(totalDegrees)>360)throw new IllegalArgumentException("Kąt szyku: 0,01–360°");
        boolean full=Math.abs(Math.abs(totalDegrees)-360)<1e-9;
        double step=Math.toRadians(totalDegrees)/(full?count:count-1);
        List<SketchPrimitive> result=new ArrayList<>();
        for(int i=1;i<count;i++)result.add(transform(s,UUID.randomUUID().toString(),cx,cy,step*i,0,0));
        return result;
    }
    private static void validateCount(int count){if(count<2||count>MAX_INSTANCES)throw new IllegalArgumentException("Liczba elementów szyku: 2–"+MAX_INSTANCES);}
}
