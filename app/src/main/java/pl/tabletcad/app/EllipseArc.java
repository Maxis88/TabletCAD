package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.SketchPrimitive;

/**
 * Geometry of elliptic arcs {cx, cy, ax, ay, minor, t0, t1}: the ellipse with centre (cx, cy),
 * semi-major axis to (ax, ay) and semi-minor length, run counter-clockwise in the parameter
 * t from t0 to t1. Pure logic.
 */
public final class EllipseArc {
    private EllipseArc() {}
    /** Largest deviation of the circular-arc approximation used in closed profiles. */
    public static final double PROFILE_TOLERANCE=1e-4;
    public static void validate(double[] d) {
        if(d==null||d.length!=7)throw new IllegalArgumentException("Niepoprawny łuk eliptyczny");
        for(double v:d)if(!Double.isFinite(v))throw new IllegalArgumentException("Niepoprawny łuk eliptyczny");
        double a=Math.hypot(d[2]-d[0],d[3]-d[1]);
        if(a<.01||d[4]<.01||d[4]>a+1e-9)throw new IllegalArgumentException("Niepoprawne półosie łuku eliptycznego");
        if(Math.abs(d[0])+a>5000||Math.abs(d[1])+a>5000)throw new IllegalArgumentException("Łuk eliptyczny poza zakresem ±5000 mm");
        double sweep=d[6]-d[5];if(!(sweep>1e-6)||sweep>=2*Math.PI-1e-9)throw new IllegalArgumentException("Niepoprawny zakres łuku eliptycznego");
    }
    public static double[] point(double[] d,double t) {
        double a=Math.hypot(d[2]-d[0],d[3]-d[1]),ux=(d[2]-d[0])/a,uy=(d[3]-d[1])/a,x=a*Math.cos(t),y=d[4]*Math.sin(t);
        return new double[]{d[0]+x*ux-y*uy,d[1]+x*uy+y*ux};
    }
    /** Parameter of the ellipse point nearest to (x, y), in [0, 2π). */
    public static double parameter(double cx,double cy,double ax,double ay,double minor,double x,double y) {
        double a=Math.hypot(ax-cx,ay-cy),ux=(ax-cx)/a,uy=(ay-cy)/a,lx=(x-cx)*ux+(y-cy)*uy,ly=-(x-cx)*uy+(y-cy)*ux;
        double t=Math.atan2(ly*a,lx*minor);
        for(int i=0;i<30;i++){double c=Math.cos(t),s=Math.sin(t),ex=a*c-lx,ey=minor*s-ly,dx=-a*s,dy=minor*c,f=ex*dx+ey*dy,df=dx*dx+dy*dy+ex*(-a*c)+ey*(-minor*s);if(Math.abs(df)<1e-15)break;double step=f/df;t-=step;if(Math.abs(step)<1e-14)break;}
        t%=2*Math.PI;return t<0?t+2*Math.PI:t;
    }
    /** Polyline of n+1 points along the arc. */
    public static double[] sample(double[] d,int n) {
        double[] r=new double[2*(n+1)];for(int i=0;i<=n;i++){double[] p=point(d,d[5]+(d[6]-d[5])*i/n);r[2*i]=p[0];r[2*i+1]=p[1];}return r;
    }
    /**
     * Three-point circular arcs (sketch curve segments: 1, start, middle, end) through points of
     * the ellipse, each within {@link #PROFILE_TOLERANCE} mm of the elliptic arc.
     */
    public static List<double[]> circularArcs(double[] d) {
        for(int n=2;n<=96;n*=2) {
            List<double[]> out=new ArrayList<>();double worst=0;
            for(int i=0;i<n;i++){double t0=d[5]+(d[6]-d[5])*i/n,t1=d[5]+(d[6]-d[5])*(i+1)/n;double[] a=point(d,t0),m=point(d,(t0+t1)/2),b=point(d,t1);
                double[] seg={1,a[0],a[1],m[0],m[1],b[0],b[1]};SketchCurves.Arc c=SketchCurves.arc(seg,0);
                for(double q:new double[]{.25,.75,.125,.375,.625,.875}){double[] p=point(d,t0+(t1-t0)*q);worst=Math.max(worst,Math.abs(Math.hypot(p[0]-c.x,p[1]-c.y)-c.r));}
                out.add(seg);}
            if(worst<=PROFILE_TOLERANCE||n==96)return out;
        }
        throw new IllegalStateException();
    }
    /** Arc of the same ellipse between two parameters (counter-clockwise from t0). */
    public static SketchPrimitive part(SketchPrimitive source,String id,double t0,double t1) {
        double[] d=source.isEllipse()?new double[]{source.x1,source.y1,source.x2,source.y2,source.minor,0,0}:source.ellipseArc();
        while(t1<=t0)t1+=2*Math.PI;
        return SketchPrimitive.ellipseArc(id,d[0],d[1],d[2],d[3],d[4],t0,t1).withConstruction(source.construction);
    }
    /** Arc under a similarity (rotation, scale, translation); orientation preserved. */
    public static SketchPrimitive map(SketchPrimitive s,String id,double cx,double cy,double radians,double factor,double dx,double dy) {
        double[] d=s.ellipseArc();double c=Math.cos(radians)*factor,n=Math.sin(radians)*factor;
        double[] centre={cx+(d[0]-cx)*c-(d[1]-cy)*n+dx,cy+(d[0]-cx)*n+(d[1]-cy)*c+dy},axis={cx+(d[2]-cx)*c-(d[3]-cy)*n+dx,cy+(d[2]-cx)*n+(d[3]-cy)*c+dy};
        return SketchPrimitive.ellipseArc(id,centre[0],centre[1],axis[0],axis[1],d[4]*factor,d[5],d[6]).withConstruction(s.construction);
    }
    /** Mirror image about the line through (ax, ay)-(bx, by): the major axis is reflected and the run reversed. */
    public static SketchPrimitive mirror(SketchPrimitive s,String id,double ax,double ay,double bx,double by) {
        double[] d=s.ellipseArc();double l=Math.hypot(bx-ax,by-ay),ux=(bx-ax)/l,uy=(by-ay)/l;
        java.util.function.BiFunction<Double,Double,double[]> m=(x,y)->{double px=x-ax,py=y-ay,t=px*ux+py*uy;return new double[]{ax+2*t*ux-px,ay+2*t*uy-py};};
        double[] c=m.apply(d[0],d[1]),e=m.apply(d[2],d[3]);
        // The reflected parameter is −t (with the reflected axis), so the run reverses.
        return SketchPrimitive.ellipseArc(id,c[0],c[1],e[0],e[1],d[4],-d[6],-d[5]).withConstruction(s.construction);
    }
    /** Offset as an open spline through exact offset points (positive: away from the centre). */
    public static SketchPrimitive offset(SketchPrimitive s,String id,double dist) {
        double[] d=s.ellipseArc();double a=Math.hypot(d[2]-d[0],d[3]-d[1]),b=d[4],ux=(d[2]-d[0])/a,uy=(d[3]-d[1])/a;
        if(dist<0&&-dist>=b*b/a-1e-6)throw new IllegalArgumentException("Odsunięcie do wewnątrz większe niż najmniejszy promień krzywizny elipsy");
        int n=Math.max(8,Math.min(120,(int)Math.ceil((d[6]-d[5])/(2*Math.PI)*64)));double[] pts=new double[2*(n+1)];
        for(int i=0;i<=n;i++){double t=d[5]+(d[6]-d[5])*i/n,lx=a*Math.cos(t),ly=b*Math.sin(t),nx=b*Math.cos(t),ny=a*Math.sin(t),l=Math.hypot(nx,ny);lx+=nx/l*dist;ly+=ny/l*dist;pts[2*i]=d[0]+lx*ux-ly*uy;pts[2*i+1]=d[1]+lx*uy+ly*ux;}
        return new SketchPrimitive(id,pts,"Splajn").withConstruction(s.construction);
    }
    /** DXF ELLIPSE entity with start and end parameters. */
    public static void writeDxf(java.io.Writer w,double[] d)throws java.io.IOException {
        double a=Math.hypot(d[2]-d[0],d[3]-d[1]);
        w.write("0\nELLIPSE\n8\nSKETCH\n100\nAcDbEntity\n100\nAcDbEllipse\n10\n"+d[0]+"\n20\n"+d[1]+"\n30\n0\n11\n"+(d[2]-d[0])+"\n21\n"+(d[3]-d[1])+"\n31\n0\n40\n"+(d[4]/a)+"\n41\n"+d[5]+"\n42\n"+d[6]+"\n");
    }
}
