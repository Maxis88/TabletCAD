package pl.tabletcad.app;

/** Exact touched point on a projected mesh triangle. Pure logic. */
public final class FacePick {
    private FacePick() {}
    /**
     * World point under the screen point (px,py) on a triangle with screen vertices sx/sy and
     * per-vertex q = 1/eye-distance. Screen-space barycentric weights are made perspective-correct
     * by q; points just outside the triangle are clamped onto it.
     */
    public static double[] hit(float[] sx,float[] sy,double[] q,double[] wx,double[] wy,double[] wz,double px,double py) {
        double d=(sy[1]-sy[2])*(sx[0]-sx[2])+(sx[2]-sx[1])*(sy[0]-sy[2]);
        if(Math.abs(d)<1e-12)return new double[]{(wx[0]+wx[1]+wx[2])/3,(wy[0]+wy[1]+wy[2])/3,(wz[0]+wz[1]+wz[2])/3};
        double a=((sy[1]-sy[2])*(px-sx[2])+(sx[2]-sx[1])*(py-sy[2]))/d,b=((sy[2]-sy[0])*(px-sx[2])+(sx[0]-sx[2])*(py-sy[2]))/d;
        a=Math.max(0,a);b=Math.max(0,b);double c=Math.max(0,1-a-b),sum=a+b+c;a/=sum;b/=sum;c/=sum;
        double ka=a*q[0],kb=b*q[1],kc=c*q[2],k=ka+kb+kc;
        if(!(k>0)){ka=a;kb=b;kc=c;k=1;}
        return new double[]{(ka*wx[0]+kb*wx[1]+kc*wx[2])/k,(ka*wy[0]+kb*wy[1]+kc*wy[2])/k,(ka*wz[0]+kb*wz[1]+kc*wz[2])/k};
    }
    /** A point at depth q (1/eye distance) is behind a face at depth qFace by more than tolerance (mm). */
    public static boolean behind(double q,double qFace,double tolerance) {
        if(!(q>0)||!(qFace>0))return false;
        return 1/q-1/qFace>tolerance;
    }
}
