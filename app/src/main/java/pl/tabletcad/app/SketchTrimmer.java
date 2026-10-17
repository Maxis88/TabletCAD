package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.SketchPrimitive;

/** Removes the touched interval between consecutive actual sketch intersections. */
public final class SketchTrimmer {
    private SketchTrimmer() {}
    static List<double[]> edges(SketchPrimitive p) {
        List<double[]> result=new ArrayList<>();
        if(p.curves!=null)return result;
        if(p.vertices!=null) {
            for(int i=0;i<p.vertices.length;i+=2) {int j=(i+2)%p.vertices.length;
                result.add(new double[]{p.vertices[i],p.vertices[i+1],p.vertices[j],p.vertices[j+1]});}
        } else if(p.type.equals("Prostokąt")) {
            result.add(new double[]{p.x1,p.y1,p.x2,p.y1});result.add(new double[]{p.x2,p.y1,p.x2,p.y2});
            result.add(new double[]{p.x2,p.y2,p.x1,p.y2});result.add(new double[]{p.x1,p.y2,p.x1,p.y1});
        } else if(!p.type.equals("Okrąg")) result.add(new double[]{p.x1,p.y1,p.x2,p.y2});
        return result;
    }
    private static double parameter(double[] edge,double x,double y) {
        double dx=edge[2]-edge[0],dy=edge[3]-edge[1],length=dx*dx+dy*dy;
        if(length<1e-12) throw new IllegalArgumentException("Zerowy odcinek");
        return Math.max(0,Math.min(1,((x-edge[0])*dx+(y-edge[1])*dy)/length));
    }
    private static void cut(List<Double> cuts,double t) {
        if(Double.isFinite(t)&&t>1e-8&&t<1-1e-8) cuts.add(t);
    }
    private static void intersections(double[] a,double[] b,List<Double> cuts) {
        double ax=a[2]-a[0],ay=a[3]-a[1],bx=b[2]-b[0],by=b[3]-b[1];
        double det=ax*by-ay*bx,dx=b[0]-a[0],dy=b[1]-a[1];
        if(Math.abs(det)<1e-10) {
            if(Math.abs(dx*ay-dy*ax)<1e-8) {
                cut(cuts,parameter(a,b[0],b[1]));cut(cuts,parameter(a,b[2],b[3]));
            }
            return;
        }
        double t=(dx*by-dy*bx)/det,u=(dx*ay-dy*ax)/det;
        if(u>=-1e-8&&u<=1+1e-8) cut(cuts,t);
    }
    private static void circleIntersections(double[] e,SketchPrimitive circle,List<Double> cuts) {
        double dx=e[2]-e[0],dy=e[3]-e[1],x=e[0]-circle.x1,y=e[1]-circle.y1;
        double r=Math.hypot(circle.x2-circle.x1,circle.y2-circle.y1);
        double a=dx*dx+dy*dy,b=2*(x*dx+y*dy),c=x*x+y*y-r*r;
        double discriminant=b*b-4*a*c;
        if(a<1e-12||discriminant<0)return;
        cut(cuts,(-b-Math.sqrt(discriminant))/(2*a));cut(cuts,(-b+Math.sqrt(discriminant))/(2*a));
    }
    public static List<SketchPrimitive> trim(List<SketchPrimitive> geometry,SketchPrimitive target,double x,double y) {
        if(target.driving!=null)throw new IllegalArgumentException("Odblokuj wymiary przed przycinaniem elementu");
        if(target.curves!=null||target.type.equals("Okrąg")) return CurveTrimmer.trim(geometry,target,x,y);
        List<double[]> outline=edges(target);int selected=-1;double nearest=Double.POSITIVE_INFINITY,at=0;
        for(int i=0;i<outline.size();i++) {
            double[] e=outline.get(i);double t=parameter(e,x,y);
            double distance=Math.hypot(x-(e[0]+t*(e[2]-e[0])),y-(e[1]+t*(e[3]-e[1])));
            if(distance<nearest){nearest=distance;selected=i;at=t;}
        }
        if(selected<0)throw new IllegalArgumentException("Wybierz odcinek do przycięcia");
        double[] edge=outline.get(selected);List<Double> cuts=new ArrayList<>();cuts.add(0.0);cuts.add(1.0);
        for(SketchPrimitive cutter:geometry) {
            if(cutter.id.equals(target.id))continue;
            if(cutter.curves!=null)for(double[] p:SketchCurves.lineHits(edge,cutter.curves))cut(cuts,parameter(edge,p[0],p[1]));
            else if(cutter.type.equals("Okrąg")) circleIntersections(edge,cutter,cuts);
            else for(double[] e:edges(cutter)) intersections(edge,e,cuts);
        }
        Collections.sort(cuts);List<Double> unique=new ArrayList<>();
        for(double t:cuts) if(unique.isEmpty()||t-unique.get(unique.size()-1)>1e-8) unique.add(t);
        if(unique.size()<3)throw new IllegalArgumentException("Brak przecięć na tym odcinku. Cały element usuwa narzędzie Usuń.");
        for(int i=1;i<unique.size()-1;i++)if(Math.abs(at-unique.get(i))<1e-6)
            throw new IllegalArgumentException("Dotknij wnętrza fragmentu, nie punktu przecięcia");
        double low=0,high=1;
        for(int i=0;i<unique.size()-1;i++)if(at>=unique.get(i)&&at<=unique.get(i+1)) {low=unique.get(i);high=unique.get(i+1);break;}
        List<double[]> remaining=new ArrayList<>();
        for(int i=0;i<outline.size();i++)if(i!=selected)remaining.add(outline.get(i));
        if(low>0)remaining.add(new double[]{edge[0],edge[1],edge[0]+low*(edge[2]-edge[0]),edge[1]+low*(edge[3]-edge[1])});
        if(high<1)remaining.add(new double[]{edge[0]+high*(edge[2]-edge[0]),edge[1]+high*(edge[3]-edge[1]),edge[2],edge[3]});
        List<SketchPrimitive> result=new ArrayList<>();
        for(double[] e:remaining)if(Math.hypot(e[2]-e[0],e[3]-e[1])>=.01) {
            result.add(new SketchPrimitive(result.isEmpty()?target.id:UUID.randomUUID().toString(),
                target.type.equals("Pomocnicza")?"Pomocnicza":"Linia",e[0],e[1],e[2],e[3]));
        }
        return result;
    }
}
