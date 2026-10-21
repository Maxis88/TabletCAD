package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.SketchPrimitive;

/** Removes the touched interval between consecutive actual sketch intersections. */
public final class SketchTrimmer {
    private SketchTrimmer() {}
    static List<double[]> edges(SketchPrimitive p) {
        List<double[]> result=new ArrayList<>();
        if(p.curves!=null||SketchSpline.is(p)||p.isEllipse()||p.isEllipseArc()||p.isPoint()||p.isText())return result;
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
    /** Exact line–ellipse intersections (in the ellipse frame the ellipse is (u/a)²+(v/b)²=1). */
    private static void ellipseIntersections(double[] e,SketchPrimitive el,List<Double> cuts) {
        double a=el.major(),b=el.minor,ux=(el.x2-el.x1)/a,uy=(el.y2-el.y1)/a;
        double px=e[0]-el.x1,py=e[1]-el.y1,dx=e[2]-e[0],dy=e[3]-e[1];
        double pu=(px*ux+py*uy)/a,pv=(-px*uy+py*ux)/b,du=(dx*ux+dy*uy)/a,dv=(-dx*uy+dy*ux)/b;
        double qa=du*du+dv*dv,qb=2*(pu*du+pv*dv),qc=pu*pu+pv*pv-1,disc=qb*qb-4*qa*qc;
        if(qa<1e-18||disc<0)return;
        cut(cuts,(-qb-Math.sqrt(disc))/(2*qa));cut(cuts,(-qb+Math.sqrt(disc))/(2*qa));
    }
    /** Fine polyline of a curve that is neither a line nor an arc (ellipse, spline) for cutting other elements. */
    static List<double[]> curvePolyline(SketchPrimitive p) {
        List<double[]> out=new ArrayList<>();double[] pts=null;
        if(SketchSpline.is(p))pts=SketchSpline.sample(p.vertices,128);
        else if(p.isEllipse()){int n=720;pts=new double[(n+1)*2];double a=p.major(),ux=(p.x2-p.x1)/a,uy=(p.y2-p.y1)/a;
            for(int i=0;i<=n;i++){double t=2*Math.PI*i/n,c=a*Math.cos(t),d=p.minor*Math.sin(t);pts[2*i]=p.x1+c*ux-d*uy;pts[2*i+1]=p.y1+c*uy+d*ux;}}
        else if(p.isEllipseArc())pts=EllipseArc.sample(p.ellipseArc(),Math.max(16,(int)Math.ceil(720*(p.ellipseArc()[6]-p.ellipseArc()[5])/(2*Math.PI))));
        if(pts!=null)for(int i=0;i+3<pts.length;i+=2)out.add(new double[]{pts[i],pts[i+1],pts[i+2],pts[i+3]});
        return out;
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
        if(target!=null&&(target.isEllipse()||target.isEllipseArc()))return trimEllipse(geometry,target,x,y);
        List<SketchPrimitive> result=new ArrayList<>();for(SketchPrimitive p:trimGeometry(geometry,target,x,y))result.add(p.withConstruction(target.construction));return result;
    }
    /** Polyline segments of any element used as a cutter. */
    static List<double[]> cutterSegments(SketchPrimitive c) {
        List<double[]> out=new ArrayList<>();
        if(c.curves!=null){double[] p=SketchCurves.samples(c.curves,256);for(int i=0;i+3<p.length;i+=2)out.add(new double[]{p[i],p[i+1],p[i+2],p[i+3]});}
        else if(c.type.equals("Okrąg")){double r=Math.hypot(c.x2-c.x1,c.y2-c.y1);int n=1440;for(int i=0;i<n;i++){double a=2*Math.PI*i/n,b=2*Math.PI*(i+1)/n;out.add(new double[]{c.x1+r*Math.cos(a),c.y1+r*Math.sin(a),c.x1+r*Math.cos(b),c.y1+r*Math.sin(b)});}}
        else if(c.isEllipse()||c.isEllipseArc()||SketchSpline.is(c))out.addAll(curvePolyline(c));
        else out.addAll(edges(c));
        return out;
    }
    /**
     * Trim of an ellipse or elliptic arc at its intersections with the other elements: the touched
     * span between consecutive intersections is removed. A closed ellipse needs two intersections
     * and becomes one elliptic arc; an arc may split in two.
     */
    static List<SketchPrimitive> trimEllipse(List<SketchPrimitive> geometry,SketchPrimitive target,double x,double y) {
        double[] d=target.isEllipse()?new double[]{target.x1,target.y1,target.x2,target.y2,target.minor,0,2*Math.PI}:target.ellipseArc();
        boolean closed=target.isEllipse();double t0=d[5],span=d[6]-d[5];
        java.util.function.DoubleUnaryOperator rel=t->{double r=(t-t0)%(2*Math.PI);return r<0?r+2*Math.PI:r;};
        int n=4096;double[] poly=new double[2*(n+1)];for(int i=0;i<=n;i++){double[] q=EllipseArc.point(d,t0+span*i/n);poly[2*i]=q[0];poly[2*i+1]=q[1];}
        List<Double> cuts=new ArrayList<>();
        for(SketchPrimitive c:geometry){if(c.id.equals(target.id)||c.isText()||c.isPoint())continue;
            boolean straight=c.curves==null&&!c.type.equals("Okrąg")&&!c.isEllipse()&&!c.isEllipseArc()&&!SketchSpline.is(c);
            for(double[] e:cutterSegments(c))for(int i=0;i<n;i++){
                double ax=poly[2*i],ay=poly[2*i+1],bx=poly[2*i+2]-ax,by=poly[2*i+3]-ay,ex=e[2]-e[0],ey=e[3]-e[1],det=bx*ey-by*ex;if(Math.abs(det)<1e-14)continue;
                double qx=e[0]-ax,qy=e[1]-ay,s=(qx*ey-qy*ex)/det,u=(qx*by-qy*bx)/det;if(s<0||s>1||u<-1e-9||u>1+1e-9)continue;
                double t=t0+span*(i+s)/n;
                if(straight){// Newton on the exact line: cross((P(t)−E0), dir) = 0.
                    for(int k=0;k<8;k++){double[] p=EllipseArc.point(d,t),p2=EllipseArc.point(d,t+1e-7);double f=(p[0]-e[0])*ey-(p[1]-e[1])*ex,f2=(p2[0]-e[0])*ey-(p2[1]-e[1])*ex,df=(f2-f)/1e-7;if(Math.abs(df)<1e-15)break;t-=f/df;}}
                else{double[] p=EllipseArc.point(d,t);t=EllipseArc.parameter(d[0],d[1],d[2],d[3],d[4],p[0],p[1]);}
                double r=rel.applyAsDouble(t);if(closed||r>1e-7&&r<span-1e-7)cuts.add(r);
            }}
        Collections.sort(cuts);List<Double> unique=new ArrayList<>();for(double t:cuts)if(unique.isEmpty()||t-unique.get(unique.size()-1)>1e-7)unique.add(t);
        if(closed&&unique.size()>1&&2*Math.PI-unique.get(unique.size()-1)+unique.get(0)<1e-7)unique.remove(unique.size()-1);
        double tap=rel.applyAsDouble(EllipseArc.parameter(d[0],d[1],d[2],d[3],d[4],x,y));
        for(double c:unique)if(Math.abs(c-tap)<1e-6)throw new IllegalArgumentException("Dotknij wnętrza fragmentu, nie punktu przecięcia");
        List<SketchPrimitive> result=new ArrayList<>();
        if(closed) {
            if(unique.size()<2)throw new IllegalArgumentException("Elipsę przycina się między dwoma przecięciami – narysuj elementy przecinające ją w co najmniej dwóch miejscach");
            int k=0;while(k<unique.size()&&unique.get(k)<tap)k++;
            double high=k<unique.size()?unique.get(k):unique.get(0)+2*Math.PI,low=k>0?unique.get(k-1):unique.get(unique.size()-1)-2*Math.PI;
            result.add(EllipseArc.part(target,target.id,t0+high,t0+low+2*Math.PI));return result;
        }
        if(tap>span)tap=tap-span>2*Math.PI-tap?0:span;
        if(unique.isEmpty())throw new IllegalArgumentException("Brak przecięć na tym łuku. Cały element usuwa narzędzie Usuń.");
        double low=0,high=span;for(double c:unique){if(c<tap)low=c;else{high=c;break;}}
        if(low>1e-6)result.add(EllipseArc.part(target,target.id,t0,t0+low));
        if(span-high>1e-6)result.add(EllipseArc.part(target,result.isEmpty()?target.id:UUID.randomUUID().toString(),t0+high,t0+span));
        return result;
    }
    private static List<SketchPrimitive> trimGeometry(List<SketchPrimitive> geometry,SketchPrimitive target,double x,double y) {
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
            if(cutter.id.equals(target.id)||cutter.isText())continue;
            if(cutter.curves!=null)for(double[] p:SketchCurves.lineHits(edge,cutter.curves))cut(cuts,parameter(edge,p[0],p[1]));
            else if(cutter.type.equals("Okrąg")) circleIntersections(edge,cutter,cuts);
            else if(cutter.isEllipse()) ellipseIntersections(edge,cutter,cuts);
            else if(SketchSpline.is(cutter)||cutter.isEllipseArc()) for(double[] e:curvePolyline(cutter)) intersections(edge,e,cuts);
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
