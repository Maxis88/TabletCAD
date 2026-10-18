package pl.tabletcad.app;

import java.util.Locale;

/**
 * Boolean between two document bodies (Combine): join, cut or intersect the tool body into
 * the target body. Record (16): -15, operation (0 join, 1 cut, 2 intersect), tool body id,
 * keep tool (0 consumed, 1 kept), twelve reserved zeros. Routed to the target body.
 */
public final class BodyCombine {
    public static final double TYPE=-15;
    public static final int JOIN=0,CUT=1,INTERSECT=2;
    private BodyCombine() {}
    public static boolean is(double[] f){return f!=null&&f.length==16&&f[0]==TYPE;}
    public static double[] create(int operation,int tool,boolean keep){double[] f=new double[16];f[0]=TYPE;f[1]=operation;f[2]=tool;f[3]=keep?1:0;validate(f);return f;}
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis operacji na bryłach");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Niepoprawne parametry operacji na bryłach");
        if(f[1]!=JOIN&&f[1]!=CUT&&f[1]!=INTERSECT)throw new IllegalArgumentException("Nieznana operacja na bryłach");
        if(f[2]!=Math.floor(f[2])||f[2]<1)throw new IllegalArgumentException("Niepoprawna bryła narzędzia");
        if(f[3]!=0&&f[3]!=1)throw new IllegalArgumentException("Niepoprawna flaga zachowania narzędzia");
        for(int i=4;i<16;i++)if(f[i]!=0)throw new IllegalArgumentException("Niepoprawne pola zarezerwowane operacji na bryłach");
    }
    public static int tool(double[] f){return (int)f[2];}
    public static boolean consumes(double[] f){return is(f)&&f[3]==0;}
    public static String label(double[] f){return f[1]==JOIN?"Połączenie brył":f[1]==CUT?"Odjęcie brył":"Część wspólna brył";}
    public static String describe(double[] f,String toolName){return String.format(Locale.US,"%s · narzędzie: %s%s",label(f),toolName,f[3]==1?" (zachowane)":"");}
}
