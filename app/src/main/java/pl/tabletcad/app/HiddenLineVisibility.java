package pl.tabletcad.app;

import java.util.*;

/** Perspective-correct analytic edge/surface occlusion in screen space. */
public final class HiddenLineVisibility {
    private HiddenLineVisibility() {}
    private static final double DEPTH_EPS=1e-8;
    public static final class Triangle {
        final double[] x,y,q;
        final double denominator;
        public Triangle(double[] x,double[] y,double[] reciprocalDepth) {
            this.x=x.clone();this.y=y.clone();this.q=reciprocalDepth.clone();
            denominator=(y[1]-y[2])*(x[0]-x[2])+(x[2]-x[1])*(y[0]-y[2]);
        }
        private double[] weights(double px,double py) {
            if(Math.abs(denominator)<1e-9) return null;
            double a=((y[1]-y[2])*(px-x[2])+(x[2]-x[1])*(py-y[2]))/denominator;
            double b=((y[2]-y[0])*(px-x[2])+(x[0]-x[2])*(py-y[2]))/denominator;
            return new double[]{a,b,1-a-b};
        }
        public double depthAt(double px,double py) {
            double[] w=weights(px,py);
            if(w==null||w[0]<-1e-7||w[1]<-1e-7||w[2]<-1e-7) return Double.NaN;
            return w[0]*q[0]+w[1]*q[1]+w[2]*q[2];
        }
        double[] hides(double ax,double ay,double aq,double bx,double by,double bq) {
            // Cheap bounds test before barycentric clipping.
            if(Math.max(ax,bx)<Math.min(x[0],Math.min(x[1],x[2]))
                ||Math.min(ax,bx)>Math.max(x[0],Math.max(x[1],x[2]))
                ||Math.max(ay,by)<Math.min(y[0],Math.min(y[1],y[2]))
                ||Math.min(ay,by)>Math.max(y[0],Math.max(y[1],y[2]))) return null;
            double[] a=weights(ax,ay),b=weights(bx,by);if(a==null||b==null)return null;
            double[] range={0,1};
            for(int i=0;i<3;i++) if(!clip(range,a[i],b[i])) return null;
            double da=a[0]*q[0]+a[1]*q[1]+a[2]*q[2]-aq-DEPTH_EPS;
            double db=b[0]*q[0]+b[1]*q[1]+b[2]*q[2]-bq-DEPTH_EPS;
            if(!clip(range,da,db)||range[1]-range[0]<1e-8) return null;
            return range;
        }
    }
    /** Clips t to values for which a+(b-a)t >= 0. */
    private static boolean clip(double[] range,double a,double b) {
        if(a>=0&&b>=0)return true;
        if(a<0&&b<0)return false;
        double t=a/(a-b);
        if(a<0)range[0]=Math.max(range[0],t);else range[1]=Math.min(range[1],t);
        return range[0]<=range[1];
    }
    public static boolean visible(double x,double y,double q,List<Triangle> surfaces) {
        for(Triangle surface:surfaces) {
            double other=surface.depthAt(x,y);
            if(Double.isFinite(other)&&other>q+DEPTH_EPS)return false;
        }
        return true;
    }
    /** Distance only to the visible portions, matching rendered clipping exactly. */
    public static double distanceToVisibleSegment(double px,double py,double[] a,double[] b,List<Triangle> surfaces) {
        double best=Double.POSITIVE_INFINITY,dx=b[0]-a[0],dy=b[1]-a[1];
        double length=dx*dx+dy*dy;
        double projected=length<1e-18?0:((px-a[0])*dx+(py-a[1])*dy)/length;
        for(double[] interval:segments(a[0],a[1],a[2],b[0],b[1],b[2],surfaces)) {
            double t=Math.max(interval[0],Math.min(interval[1],projected));
            best=Math.min(best,Math.hypot(px-a[0]-t*dx,py-a[1]-t*dy));
        }
        return best;
    }
    /** Returns visible intervals of the original segment, parameterised by t=0..1. */
    public static List<double[]> segments(double ax,double ay,double aq,double bx,double by,double bq,List<Triangle> surfaces) {
        List<double[]> hidden=new ArrayList<>();
        for(Triangle surface:surfaces) {
            double[] interval=surface.hides(ax,ay,aq,bx,by,bq);if(interval!=null)hidden.add(interval);
        }
        hidden.sort((a,b)->Double.compare(a[0],b[0]));
        List<double[]> visible=new ArrayList<>();double end=0;
        for(double[] interval:hidden) {
            if(interval[0]>end+1e-8)visible.add(new double[]{end,interval[0]});
            end=Math.max(end,interval[1]);if(end>=1)return visible;
        }
        if(end<1-1e-8)visible.add(new double[]{end,1});return visible;
    }
}
