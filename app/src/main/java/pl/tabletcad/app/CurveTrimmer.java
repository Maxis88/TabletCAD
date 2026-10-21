package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.SketchPrimitive;

/** Analytic trimming; a circle has cyclic parameters, never a fabricated cut at its seam. */
public final class CurveTrimmer {
    private CurveTrimmer() {}
    private static double positive(double angle){double r=angle%(2*Math.PI);return r<0?r+2*Math.PI:r;}
    private static double[] line(double x,double y,double u,double v){return new double[]{0,x,y,(x+u)/2,(y+v)/2,u,v};}
    private static List<double[]> segments(SketchPrimitive p) {
        List<double[]> result=new ArrayList<>();
        if(p.curves!=null)for(int i=0;i<p.curves.length;i+=7)result.add(Arrays.copyOfRange(p.curves,i,i+7));
        else if(p.type.equals("Okrąg")) {
            double x=p.x1,y=p.y1,r=Math.hypot(p.x2-x,p.y2-y);
            result.add(SketchCurves.arc(x+r,y,x,y+r,x-r,y));result.add(SketchCurves.arc(x-r,y,x,y-r,x+r,y));
        }else if(p.isEllipse()||p.isEllipseArc()||SketchSpline.is(p)){for(double[] e:SketchTrimmer.curvePolyline(p))result.add(line(e[0],e[1],e[2],e[3]));}
        else for(double[] e:SketchTrimmer.edges(p))result.add(line(e[0],e[1],e[2],e[3]));
        return result;
    }
    private static double parameter(double[] segment,double x,double y) {
        if(segment[0]==0){double dx=segment[5]-segment[1],dy=segment[6]-segment[2];return Math.max(0,Math.min(1,((x-segment[1])*dx+(y-segment[2])*dy)/(dx*dx+dy*dy)));}
        SketchCurves.Arc a=SketchCurves.arc(segment,0);double angle=Math.atan2(y-a.y,x-a.x);
        double delta=a.sweep>0?positive(angle-a.start):positive(a.start-angle);
        if(delta>Math.PI*2-1e-7)delta=0;
        if(delta<=Math.abs(a.sweep)+1e-7)return Math.max(0,Math.min(1,delta/Math.abs(a.sweep)));
        return Math.hypot(x-segment[1],y-segment[2])<=Math.hypot(x-segment[5],y-segment[6])?0:1;
    }
    private static double[] point(double[] segment,double t) {
        return segment[0]==0?new double[]{segment[1]+t*(segment[5]-segment[1]),segment[2]+t*(segment[6]-segment[2])}:SketchCurves.arc(segment,0).point(t);
    }
    private static List<Double> unique(List<Double> values,boolean cyclic) {
        List<Double> sorted=new ArrayList<>();
        for(double t:values){if(cyclic&&t>1-1e-8)t=0;sorted.add(t);}Collections.sort(sorted);
        List<Double> result=new ArrayList<>();for(double t:sorted)if(result.isEmpty()||t-result.get(result.size()-1)>1e-8)result.add(t);return result;
    }
    private static void append(List<SketchPrimitive> result,SketchPrimitive target,double[] segment,double from,double to) {
        if(to-from<1e-8)return;double[] a=point(segment,from),b=point(segment,to);
        if(Math.hypot(a[0]-b[0],a[1]-b[1])<.001)throw new IllegalArgumentException("Pozostający fragment jest zbyt mały; użyj Usuń dla całego elementu");
        String id=result.isEmpty()?target.id:UUID.randomUUID().toString();
        if(segment[0]==0)result.add(new SketchPrimitive(id,"Linia",a[0],a[1],b[0],b[1]));
        else if(from==0&&to==1)result.add(new SketchPrimitive(id,segment,false));
        else {double[] mid=point(segment,(from+to)/2);result.add(new SketchPrimitive(id,SketchCurves.arc(a[0],a[1],mid[0],mid[1],b[0],b[1]),false));}
    }
    public static List<SketchPrimitive> trim(List<SketchPrimitive> geometry,SketchPrimitive target,double x,double y) {
        if(target!=null&&(target.isEllipse()||target.isEllipseArc()))return SketchTrimmer.trimEllipse(geometry,target,x,y);
        if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Niepoprawny punkt przycinania");
        if(target.driving!=null)throw new IllegalArgumentException("Odblokuj wymiary przed przycinaniem elementu");
        boolean circle=target.type.equals("Okrąg");List<double[]> outline=segments(target);
        if(circle)return trimCircle(geometry,target,outline,x,y);
        int selected=-1;double distance=Double.POSITIVE_INFINITY,at=0;
        for(int i=0;i<outline.size();i++){double t=parameter(outline.get(i),x,y);double[] p=point(outline.get(i),t);double d=Math.hypot(p[0]-x,p[1]-y);if(d<distance){distance=d;selected=i;at=t;}}
        if(selected<0)throw new IllegalArgumentException("Brak segmentu do przycięcia");
        double[] edge=outline.get(selected);List<Double> cuts=new ArrayList<>();cuts.add(0.0);cuts.add(1.0);
        for(SketchPrimitive cutter:geometry)if(!cutter.id.equals(target.id))for(double[] s:segments(cutter))
            for(double[] hit:SketchCurves.segmentHits(edge,s)){double t=parameter(edge,hit[0],hit[1]);if(t>1e-8&&t<1-1e-8)cuts.add(t);}
        List<Double> split=unique(cuts,false);
        if(split.size()<3)throw new IllegalArgumentException("Brak przecięć wewnątrz fragmentu. Cały element usuwa narzędzie Usuń.");
        for(int i=1;i<split.size()-1;i++)if(Math.abs(at-split.get(i))<1e-6)throw new IllegalArgumentException("Dotknij wnętrza fragmentu, nie punktu przecięcia");
        double low=0,high=1;for(int i=0;i<split.size()-1;i++)if(at>=split.get(i)&&at<=split.get(i+1)){low=split.get(i);high=split.get(i+1);break;}
        List<SketchPrimitive> result=new ArrayList<>();
        for(int i=0;i<outline.size();i++)if(i!=selected)append(result,target,outline.get(i),0,1);
        append(result,target,edge,0,low);append(result,target,edge,high,1);
        return result;
    }
    private static List<SketchPrimitive> trimCircle(List<SketchPrimitive> geometry,SketchPrimitive target,List<double[]> outline,double x,double y) {
        if(Math.hypot(x-target.x1,y-target.y1)<1e-8)throw new IllegalArgumentException("Wskaż fragment na obwodzie, nie środek okręgu");
        double radius=Math.hypot(target.x2-target.x1,target.y2-target.y1),at=positive(Math.atan2(y-target.y1,x-target.x1))/(2*Math.PI);
        List<Double> cuts=new ArrayList<>();
        for(SketchPrimitive cutter:geometry)if(!cutter.id.equals(target.id))for(double[] s:segments(cutter))for(double[] edge:outline)
            for(double[] p:SketchCurves.segmentHits(edge,s))cuts.add(positive(Math.atan2(p[1]-target.y1,p[0]-target.x1))/(2*Math.PI));
        List<Double> split=unique(cuts,true);
        if(split.size()<2)throw new IllegalArgumentException("Okrąg wymaga dwóch różnych przecięć. Cały okrąg usuwa narzędzie Usuń.");
        for(double t:split){double delta=Math.abs(t-at);if(Math.min(delta,1-delta)<1e-6)throw new IllegalArgumentException("Dotknij wnętrza łuku między przecięciami");}
        double low=split.get(split.size()-1)-1,high=split.get(0);
        for(int i=0;i<split.size();i++){double a=split.get(i),b=i+1<split.size()?split.get(i+1):split.get(0)+1;if(at>=a&&at<=b){low=a;high=b;break;}}
        double start=high*2*Math.PI,end=(low+1)*2*Math.PI,mid=(start+end)/2;
        double[] arc=SketchCurves.arc(target.x1+radius*Math.cos(start),target.y1+radius*Math.sin(start),target.x1+radius*Math.cos(mid),target.y1+radius*Math.sin(mid),target.x1+radius*Math.cos(end),target.y1+radius*Math.sin(end));
        return Collections.singletonList(new SketchPrimitive(target.id,arc,false));
    }
}
