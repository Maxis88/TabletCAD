package pl.tabletcad.app;

/**
 * Dimensioned placement of a point on a planar face from two of its straight B-Rep edges.
 *
 * <p>Edge A is the straight face edge nearest to the point, edge B the nearest straight face edge
 * perpendicular to A. Distances are measured in the face plane, perpendicular to each edge, and
 * are exact (taken from OCCT edge end points, not from the screen mesh). The resulting position is
 * stored as the hole centre; it is not re-measured from the edges when the model changes.</p>
 */
public final class HolePlacement {
    public final double[] point,normal,aOrigin,aDirection,bOrigin,bDirection;public final double distanceA,distanceB;
    private HolePlacement(double[] point,double[] normal,double[] ao,double[] ad,double[] bo,double[] bd) {
        this.point=point.clone();this.normal=normal;aOrigin=ao;aDirection=ad;bOrigin=bo;bDirection=bd;
        distanceA=distance(point,ao,ad,normal);distanceB=bo==null?Double.NaN:distance(point,bo,bd,normal);
    }
    public boolean hasB(){return bOrigin!=null;}
    static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    static double[] unit(double[] a){double l=Math.sqrt(dot(a,a));if(l<1e-12)throw new IllegalArgumentException("Zerowy kierunek");return new double[]{a[0]/l,a[1]/l,a[2]/l};}
    static double[] minus(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    /** In-plane unit vector perpendicular to the edge, pointing from the edge towards {@code point}. */
    static double[] side(double[] point,double[] origin,double[] direction,double[] normal) {
        double[] s=unit(cross(normal,direction));if(dot(minus(point,origin),s)<0)for(int i=0;i<3;i++)s[i]=-s[i];return s;
    }
    static double distance(double[] point,double[] origin,double[] direction,double[] normal){return Math.abs(dot(minus(point,origin),unit(cross(normal,direction))));}

    /** Returns null when the face has no straight edge in its plane. */
    public static HolePlacement find(double[] point,double[] faceNormal,double[] properties) {
        double[] n=unit(faceNormal);double best=Double.POSITIVE_INFINITY;double[] ao=null,ad=null;
        for(int i=0;i+15<properties.length;i+=16) {
            if(properties[i+14]!=0)continue;double[] s={properties[i],properties[i+1],properties[i+2]},e={properties[i+6],properties[i+7],properties[i+8]};
            if(Math.abs(dot(minus(s,point),n))>1e-4||Math.abs(dot(minus(e,point),n))>1e-4)continue;
            double[] d=minus(e,s);if(Math.sqrt(dot(d,d))<1e-6)continue;d=unit(d);
            double value=distance(point,s,d,n);if(value<best-1e-9){best=value;ao=s;ad=d;}
        }
        if(ao==null)return null;
        double[] bo=null,bd=null;best=Double.POSITIVE_INFINITY;
        for(int i=0;i+15<properties.length;i+=16) {
            if(properties[i+14]!=0)continue;double[] s={properties[i],properties[i+1],properties[i+2]},e={properties[i+6],properties[i+7],properties[i+8]};
            if(Math.abs(dot(minus(s,point),n))>1e-4||Math.abs(dot(minus(e,point),n))>1e-4)continue;
            double[] d=minus(e,s);if(Math.sqrt(dot(d,d))<1e-6)continue;d=unit(d);if(Math.abs(dot(d,ad))>1e-6)continue;
            double value=distance(point,s,d,n);if(value<best-1e-9){best=value;bo=s;bd=d;}
        }
        return new HolePlacement(point,n,ao,ad,bo,bd);
    }
    /** Point at the given distances from edge A and (when present) edge B, on the same sides as now. */
    public double[] at(double distanceFromA,double distanceFromB) {
        if(!Double.isFinite(distanceFromA)||distanceFromA<0||(hasB()&&(!Double.isFinite(distanceFromB)||distanceFromB<0)))throw new IllegalArgumentException("Odległość od krawędzi musi być nieujemna");
        double[] r=point.clone(),sa=side(point,aOrigin,aDirection,normal);
        for(int i=0;i<3;i++)r[i]+=sa[i]*(distanceFromA-distanceA);
        if(hasB()){double[] sb=side(point,bOrigin,bDirection,normal);for(int i=0;i<3;i++)r[i]+=sb[i]*(distanceFromB-distanceB);}
        return r;
    }
}
