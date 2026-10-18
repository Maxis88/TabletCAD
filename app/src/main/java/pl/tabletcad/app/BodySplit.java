package pl.tabletcad.app;

import java.util.Locale;

/**
 * Split a body by a plane (Podziel bryłę). Record (16): -18, id of the new body, plane point
 * xyz, unit normal xyz, eight reserved zeros. The part behind the normal keeps the target
 * body; the part in front of it becomes the new body. Routed to the target body.
 */
public final class BodySplit {
    public static final double TYPE=-18;
    private BodySplit() {}
    public static boolean is(double[] f){return f!=null&&f.length==16&&f[0]==TYPE;}
    public static int created(double[] f){return (int)f[1];}
    public static double[] create(int created,double[] point,double[] normal) {
        double l=Math.sqrt(normal[0]*normal[0]+normal[1]*normal[1]+normal[2]*normal[2]);if(!(l>1e-9))throw new IllegalArgumentException("Niepoprawna płaszczyzna podziału");
        double[] f=new double[16];f[0]=TYPE;f[1]=created;for(int i=0;i<3;i++){f[2+i]=point[i];f[5+i]=normal[i]/l;}validate(f);return f;
    }
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis podziału bryły");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry podziału muszą być skończone");
        if(f[1]!=Math.floor(f[1])||f[1]<2)throw new IllegalArgumentException("Niepoprawny identyfikator nowej bryły");
        for(int i=2;i<5;i++)if(Math.abs(f[i])>10000)throw new IllegalArgumentException("Płaszczyzna podziału poza zakresem");
        if(Math.abs(f[5]*f[5]+f[6]*f[6]+f[7]*f[7]-1)>1e-6)throw new IllegalArgumentException("Normalna płaszczyzny musi być wektorem jednostkowym");
        for(int i=8;i<16;i++)if(f[i]!=0)throw new IllegalArgumentException("Niepoprawne pola zarezerwowane podziału");
    }
    public static String describe(double[] f){return String.format(Locale.US,"Podział płaszczyzną przez (%.3f, %.3f, %.3f), normalna (%.3f, %.3f, %.3f)",f[2],f[3],f[4],f[5],f[6],f[7]);}
}
