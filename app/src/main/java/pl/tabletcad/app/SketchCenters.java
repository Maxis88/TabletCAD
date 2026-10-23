package pl.tabletcad.app;

import pl.tabletcad.app.model.SketchPrimitive;

/**
 * Centre of the area enclosed by a closed sketch element (centroid of the surface),
 * in sketch millimetres. Circles, ellipses and rectangles use their exact centre;
 * line/arc contours use exact Green's-theorem integrals (arcs in closed form);
 * closed splines are integrated over a dense sampling. Open geometry, points and
 * text have no surface centre.
 */
public final class SketchCenters {
    private SketchCenters() {}

    /** Whether the element encloses a surface whose centre can be shown and snapped to. */
    public static boolean has(SketchPrimitive p) {
        return p!=null&&!p.isText()&&!p.isPoint()&&p.closedGeometry();
    }

    /** {x, y} of the surface centre, or null for open/degenerate geometry. */
    public static double[] center(SketchPrimitive p) {
        if(!has(p))return null;
        if(p.type.equals("Okrąg")||p.isEllipse())return new double[]{p.x1,p.y1};
        if(p.type.equals("Prostokąt"))return new double[]{(p.x1+p.x2)*.5,(p.y1+p.y2)*.5};
        try {
            if(p.curves!=null)return curves(p.curves);
            if(SketchSpline.is(p))return polygon(SketchSpline.sample(p.vertices,64));
            if(p.vertices!=null)return polygon(p.vertices);
        }catch(IllegalArgumentException degenerate){return null;}
        return null;
    }

    /** Centroid of a closed polygon given as x0,y0,x1,y1,… (closing edge implied; a repeated last point is harmless). */
    public static double[] polygon(double[] v) {
        if(v==null||v.length<6)return null;
        double[] m=new double[3];int n=v.length/2;
        for(int i=0;i<n;i++){int j=(i+1)%n;line(m,v[2*i],v[2*i+1],v[2*j],v[2*j+1]);}
        return finish(m);
    }

    /** Centroid of a closed chain of 7-value segments (kind 1 = three-point arc, otherwise straight). */
    public static double[] curves(double[] s) {
        if(s==null||s.length<7||s.length%7!=0)return null;
        double[] m=new double[3];
        for(int o=0;o<s.length;o+=7) {
            if(s[o]==1)arc(m,SketchCurves.arc(s,o));
            else line(m,s[o+1],s[o+2],s[o+5],s[o+6]);
        }
        // Close a chain whose last end does not return to its first start (tolerated by the contour model).
        int last=s.length-7;
        if(Math.hypot(s[last+5]-s[1],s[last+6]-s[2])>1e-9)line(m,s[last+5],s[last+6],s[1],s[2]);
        return finish(m);
    }

    /** Accumulates A = ½∮(x dy − y dx), Mx = ½∮x² dy, My = −½∮y² dx along a straight edge. */
    private static void line(double[] m,double x0,double y0,double x1,double y1) {
        double dx=x1-x0,dy=y1-y0;
        m[0]+=.5*(x0*y1-x1*y0);
        m[1]+=.5*dy*(x0*x0+x0*dx+dx*dx/3);
        m[2]-=.5*dx*(y0*y0+y0*dy+dy*dy/3);
    }

    /** Same integrals along a circular arc, in closed form (exact for any signed sweep). */
    private static void arc(double[] m,SketchCurves.Arc a) {
        double cx=a.x,cy=a.y,r=a.r,s=a.start,e=a.start+a.sweep;
        double ss=Math.sin(s),se=Math.sin(e),cs=Math.cos(s),ce=Math.cos(e);
        m[0]+=.5*(cx*r*(se-ss)-cy*r*(ce-cs)+r*r*a.sweep);
        double cos1=se-ss, cos2=(e-s)/2+(Math.sin(2*e)-Math.sin(2*s))/4, cos3=(se-se*se*se/3)-(ss-ss*ss*ss/3);
        m[1]+=.5*(cx*cx*r*cos1+2*cx*r*r*cos2+r*r*r*cos3);
        double sin1=-(ce-cs), sin2=(e-s)/2-(Math.sin(2*e)-Math.sin(2*s))/4, sin3=(-ce+ce*ce*ce/3)-(-cs+cs*cs*cs/3);
        m[2]+=.5*(cy*cy*r*sin1+2*cy*r*r*sin2+r*r*r*sin3);
    }

    private static double[] finish(double[] m) {
        if(!Double.isFinite(m[0])||Math.abs(m[0])<1e-12)return null;
        double x=m[1]/m[0],y=m[2]/m[0];
        return Double.isFinite(x)&&Double.isFinite(y)?new double[]{x,y}:null;
    }
}
