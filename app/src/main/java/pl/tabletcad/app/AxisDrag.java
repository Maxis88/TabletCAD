package pl.tabletcad.app;

/** Closest-point parameter on a world-space axis for this renderer's perspective camera. */
public final class AxisDrag {
    private AxisDrag() {}
    public static double parameter(double[] origin,double[] axis,double[] center,
            double yawDegrees,double pitchDegrees,double scale,double cx,double cy,double x,double y) {
        return parameter(origin,axis,center,yawDegrees,pitchDegrees,scale,cx,cy,x,y,420);
    }
    /** @param distance camera distance from the model centre in millimetres (renderer perspective). */
    public static double parameter(double[] origin,double[] axis,double[] center,
            double yawDegrees,double pitchDegrees,double scale,double cx,double cy,double x,double y,double distance) {
        if(!Double.isFinite(distance)||distance<=0)return Double.NaN;
        if(origin==null||axis==null||center==null||origin.length!=3||axis.length!=3||center.length!=3
                ||!Double.isFinite(scale)||scale<=0)return Double.NaN;
        double yaw=Math.toRadians(yawDegrees),pitch=Math.toRadians(pitchDegrees);
        double[] o=rotate(new double[]{origin[0]-center[0],origin[1]-center[1],origin[2]-center[2]},yaw,pitch);
        double[] n=rotate(axis,yaw,pitch),r={(x-cx)/scale,-(y-cy)/scale,-distance};
        double nn=dot(n,n),rr=dot(r,r);if(!Double.isFinite(nn)||nn<1e-20||!Double.isFinite(rr)||rr<1e-20)return Double.NaN;
        double length=Math.sqrt(rr);for(int i=0;i<3;i++)r[i]/=length;
        double[] w={o[0],o[1],o[2]-distance};double b=dot(n,r),d=dot(n,w),e=dot(r,w);
        double denominator=nn-b*b;
        if(!Double.isFinite(denominator)||denominator/nn<1e-8)return Double.NaN;
        double ray=(nn*e-b*d)/denominator;if(ray<0)return Double.NaN;
        double result=(b*e-d)/denominator;return Double.isFinite(result)?result:Double.NaN;
    }
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static double[] rotate(double[] v,double yaw,double pitch) {
        double x=v[0]*Math.cos(yaw)+v[2]*Math.sin(yaw),z=-v[0]*Math.sin(yaw)+v[2]*Math.cos(yaw);
        return new double[]{x,v[1]*Math.cos(pitch)-z*Math.sin(pitch),v[1]*Math.sin(pitch)+z*Math.cos(pitch)};
    }
}
