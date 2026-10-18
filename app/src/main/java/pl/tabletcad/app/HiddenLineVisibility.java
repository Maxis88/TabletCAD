package pl.tabletcad.app;

import java.util.*;

/** Perspective-correct analytic edge/surface occlusion in screen space. */
public final class HiddenLineVisibility {
    private HiddenLineVisibility() {}
    private static final double DEPTH_EPS=1e-8;
    /** Per-projection broad phase; exact perspective clipping is unchanged. */
    public static final class Index extends AbstractList<Triangle> {
        private final ArrayList<Triangle> all=new ArrayList<>(),scratch=new ArrayList<>();
        private ArrayList<Integer>[] cells;
        private int[] stamps=new int[0];private int stamp,side;
        private double minX,minY,scaleX,scaleY,padding;private boolean dirty=true;
        @Override public Triangle get(int i){return all.get(i);}
        @Override public int size(){return all.size();}
        @Override public boolean add(Triangle t){dirty=true;return all.add(t);}
        @Override public void clear(){all.clear();scratch.clear();dirty=true;}
        private int cellX(double x){return Math.max(0,Math.min(side-1,(int)Math.floor((x-minX)*scaleX)));}
        private int cellY(double y){return Math.max(0,Math.min(side-1,(int)Math.floor((y-minY)*scaleY)));}
        @SuppressWarnings("unchecked") private void rebuild() {
            side=Math.max(1,Math.min(64,(int)Math.ceil(Math.sqrt(all.size()/8.0))));
            cells=(ArrayList<Integer>[])new ArrayList<?>[side*side];stamps=new int[all.size()];stamp=0;
            minX=minY=Double.POSITIVE_INFINITY;double maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
            for(Triangle t:all){minX=Math.min(minX,t.minX);minY=Math.min(minY,t.minY);maxX=Math.max(maxX,t.maxX);maxY=Math.max(maxY,t.maxY);}
            scaleX=side/Math.max(1e-6,maxX-minX);scaleY=side/Math.max(1e-6,maxY-minY);
            padding=all.isEmpty()?0:Math.max(maxX-minX,maxY-minY)*2e-7+1e-7;
            for(int i=0;i<all.size();i++){Triangle t=all.get(i);
                for(int y=cellY(t.minY);y<=cellY(t.maxY);y++)for(int x=cellX(t.minX);x<=cellX(t.maxX);x++){
                    int c=y*side+x;if(cells[c]==null)cells[c]=new ArrayList<>();cells[c].add(i);
                }
            }dirty=false;
        }
        private List<Triangle> candidates(double ax,double ay,double bx,double by) {
            if(dirty)rebuild();scratch.clear();
            if(++stamp==Integer.MAX_VALUE){Arrays.fill(stamps,0);stamp=1;}
            for(int y=cellY(Math.min(ay,by)-padding);y<=cellY(Math.max(ay,by)+padding);y++)for(int x=cellX(Math.min(ax,bx)-padding);x<=cellX(Math.max(ax,bx)+padding);x++) {
                ArrayList<Integer> bucket=cells[y*side+x];if(bucket==null)continue;
                for(int i:bucket)if(stamps[i]!=stamp){stamps[i]=stamp;scratch.add(all.get(i));}
            }return scratch;
        }
    }
    private static List<Triangle> candidates(List<Triangle> surfaces,double ax,double ay,double bx,double by) {
        return surfaces instanceof Index?((Index)surfaces).candidates(ax,ay,bx,by):surfaces;
    }
    public static final class Triangle {
        final double[] x,y,q;
        double denominator;
        double minX,minY,maxX,maxY;
        public Triangle(double[] x,double[] y,double[] reciprocalDepth) {
            this.x=x.clone();this.y=y.clone();this.q=reciprocalDepth.clone();
            bounds();
        }
        /** Reused only after clearing the per-projection index. */
        public void update(float[] sx,float[] sy,double[] reciprocalDepth) {
            for(int i=0;i<3;i++){x[i]=sx[i];y[i]=sy[i];q[i]=reciprocalDepth[i];}bounds();
        }
        private void bounds() {
            minX=Math.min(x[0],Math.min(x[1],x[2]));maxX=Math.max(x[0],Math.max(x[1],x[2]));
            minY=Math.min(y[0],Math.min(y[1],y[2]));maxY=Math.max(y[0],Math.max(y[1],y[2]));
            denominator=(y[1]-y[2])*(x[0]-x[2])+(x[2]-x[1])*(y[0]-y[2]);
        }
        private double[] weights(double px,double py) {
            if(Math.abs(denominator)<1e-9) return null;
            double a=((y[1]-y[2])*(px-x[2])+(x[2]-x[1])*(py-y[2]))/denominator;
            double b=((y[2]-y[0])*(px-x[2])+(x[0]-x[2])*(py-y[2]))/denominator;
            return new double[]{a,b,1-a-b};
        }
        public double depthAt(double px,double py) {
            if(Math.abs(denominator)<1e-9)return Double.NaN;
            double a=((y[1]-y[2])*(px-x[2])+(x[2]-x[1])*(py-y[2]))/denominator;
            double b=((y[2]-y[0])*(px-x[2])+(x[0]-x[2])*(py-y[2]))/denominator,c=1-a-b;
            if(a<-1e-7||b<-1e-7||c<-1e-7)return Double.NaN;
            return a*q[0]+b*q[1]+c*q[2];
        }
        double[] hides(double ax,double ay,double aq,double bx,double by,double bq) {
            // Cheap bounds test before barycentric clipping.
            if(Math.max(ax,bx)<minX||Math.min(ax,bx)>maxX||Math.max(ay,by)<minY||Math.min(ay,by)>maxY)return null;
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
        for(Triangle surface:candidates(surfaces,x-1e-7,y-1e-7,x+1e-7,y+1e-7)) {
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
        for(Triangle surface:candidates(surfaces,ax,ay,bx,by)) {
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
