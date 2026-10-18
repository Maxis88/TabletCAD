package pl.tabletcad.app;

/**
 * "Do powierzchni": extrusion length taken from a planar target face parallel to the sketch.
 * The distance is computed from the exact B-Rep triangulation nodes of that face, so it is
 * the real plane offset (not a rounded on-screen value).
 */
public final class ExtrudeToFace {
    private ExtrudeToFace() {}

    /** Signed distance along the sketch normal U×V from the sketch plane to the face. */
    public static double distance(double[] origin,double[] u,double[] v,double[] vertices) {
        double nx=u[1]*v[2]-u[2]*v[1],ny=u[2]*v[0]-u[0]*v[2],nz=u[0]*v[1]-u[1]*v[0],length=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(!(length>1e-12)||vertices==null||vertices.length<3||vertices.length%3!=0)throw new IllegalArgumentException("Brak płaszczyzny szkicu lub ściany docelowej");
        nx/=length;ny/=length;nz/=length;double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;
        for(int i=0;i<vertices.length;i+=3) {
            double d=(vertices[i]-origin[0])*nx+(vertices[i+1]-origin[1])*ny+(vertices[i+2]-origin[2])*nz;
            if(!Double.isFinite(d))throw new IllegalArgumentException("Niepoprawna geometria ściany");
            min=Math.min(min,d);max=Math.max(max,d);
        }
        if(max-min>1e-6+1e-9*Math.abs(max))throw new IllegalArgumentException("Wskaż płaską ścianę równoległą do płaszczyzny szkicu");
        double d=Math.rint((min+max)*.5*1e9)/1e9;
        if(Math.abs(d)<.1)throw new IllegalArgumentException("Ściana leży na płaszczyźnie szkicu — wskaż inną");
        if(Math.abs(d)>5000)throw new IllegalArgumentException("Ściana jest dalej niż 5000 mm");
        return d;
    }

    /** "Odwróć kierunek" state that makes the operation reach the face (cuts run against the normal). */
    public static boolean reverse(double signedDistance,boolean cut) {return (signedDistance<0)^cut;}

    public static String format(double value) {
        return new java.math.BigDecimal(Math.abs(value)).setScale(6,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
