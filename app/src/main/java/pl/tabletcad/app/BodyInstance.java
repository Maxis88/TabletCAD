package pl.tabletcad.app;

import java.util.Locale;

/**
 * Associative instance of a body (component copy). Record (16): -23, id of the new body, translation xyz,
 * rotation axis xyz (unit; zero = no rotation), angle [°], rotation point xyz, four reserved zeros.
 * Routed to the source body, which stays unchanged; the instance is the source rotated about the point and
 * then translated, re-evaluated from the current source on every rebuild (edits of the source propagate).
 */
public final class BodyInstance {
    public static final double TYPE=-23;
    private BodyInstance() {}
    public static boolean is(double[] f){return f!=null&&f.length==16&&f[0]==TYPE;}
    public static int created(double[] f){return (int)f[1];}
    public static double[] create(int created,double[] translation,double[] axis,double angle,double[] point) {
        double l=Math.sqrt(axis[0]*axis[0]+axis[1]*axis[1]+axis[2]*axis[2]);
        double[] f=new double[16];f[0]=TYPE;f[1]=created;System.arraycopy(translation,0,f,2,3);
        if(l>1e-12&&Math.abs(angle)>1e-12){for(int i=0;i<3;i++)f[5+i]=axis[i]/l;f[8]=angle;System.arraycopy(point,0,f,9,3);}
        validate(f);return f;
    }
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis wystąpienia bryły");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry wystąpienia muszą być skończone");
        if(f[1]!=Math.floor(f[1])||f[1]<2)throw new IllegalArgumentException("Niepoprawny identyfikator wystąpienia");
        for(int i:new int[]{2,3,4,9,10,11})if(Math.abs(f[i])>10000)throw new IllegalArgumentException("Położenie wystąpienia poza zakresem ±10000 mm");
        double n=f[5]*f[5]+f[6]*f[6]+f[7]*f[7];
        if(n==0?f[8]!=0||f[9]!=0||f[10]!=0||f[11]!=0:Math.abs(n-1)>1e-6||Math.abs(f[8])>360)throw new IllegalArgumentException("Niepoprawny obrót wystąpienia");
        for(int i=12;i<16;i++)if(f[i]!=0)throw new IllegalArgumentException("Niepoprawne pola zarezerwowane wystąpienia");
    }
    public static String describe(double[] f) {
        String text=String.format(Locale.US,"Wystąpienie: przesunięcie (%.3f, %.3f, %.3f)",f[2],f[3],f[4]);
        if(f[8]!=0)text+=String.format(Locale.US,", obrót %.3f° wokół (%.3f, %.3f, %.3f)",f[8],f[5],f[6],f[7]);
        return text;
    }
    /** 4×3 rigid transform of the record (rows: rotation | translation), applied as R·(x − p) + p + t. */
    public static double[] apply(double[] f,double x,double y,double z) {
        double[] q={x,y,z};
        if(f[8]!=0){double a=Math.toRadians(f[8]),c=Math.cos(a),s=Math.sin(a);double[] k={f[5],f[6],f[7]},v={x-f[9],y-f[10],z-f[11]};
            double d=k[0]*v[0]+k[1]*v[1]+k[2]*v[2];double[] cr={k[1]*v[2]-k[2]*v[1],k[2]*v[0]-k[0]*v[2],k[0]*v[1]-k[1]*v[0]};
            for(int i=0;i<3;i++)q[i]=v[i]*c+cr[i]*s+k[i]*d*(1-c)+f[9+i];}
        return new double[]{q[0]+f[2],q[1]+f[3],q[2]+f[4]};
    }
}
