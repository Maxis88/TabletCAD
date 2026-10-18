package pl.tabletcad.app;

import java.util.Locale;

/**
 * Move / rotate / uniform scale of a body as a history operation.
 * Record (16): -17, kind (1 move, 2 rotate, 3 scale), vector xyz (move) or point xyz
 * (rotation axis point / scale centre), unit axis xyz (rotate), angle [°] (rotate) or
 * factor (scale), seven reserved zeros.
 */
public final class BodyTransform {
    public static final double TYPE=-17;
    public static final int MOVE=1,ROTATE=2,SCALE=3;
    private BodyTransform() {}
    public static boolean is(double[] f){return f!=null&&f.length==16&&f[0]==TYPE;}
    public static double[] move(double dx,double dy,double dz){double[] f=new double[16];f[0]=TYPE;f[1]=MOVE;f[2]=dx;f[3]=dy;f[4]=dz;validate(f);return f;}
    public static double[] rotate(double[] point,double[] axis,double angle) {
        double l=Math.sqrt(axis[0]*axis[0]+axis[1]*axis[1]+axis[2]*axis[2]);if(!(l>1e-9))throw new IllegalArgumentException("Niepoprawna oś obrotu");
        double[] f=new double[16];f[0]=TYPE;f[1]=ROTATE;System.arraycopy(point,0,f,2,3);for(int i=0;i<3;i++)f[5+i]=axis[i]/l;f[8]=angle;validate(f);return f;
    }
    public static double[] scale(double[] centre,double factor){double[] f=new double[16];f[0]=TYPE;f[1]=SCALE;System.arraycopy(centre,0,f,2,3);f[8]=factor;validate(f);return f;}
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis przekształcenia bryły");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry przekształcenia muszą być skończone");
        for(int i=2;i<5;i++)if(Math.abs(f[i])>10000)throw new IllegalArgumentException("Przesunięcie/punkt poza zakresem ±10000 mm");
        for(int i=9;i<16;i++)if(f[i]!=0)throw new IllegalArgumentException("Niepoprawne pola zarezerwowane przekształcenia");
        if(f[1]==MOVE){if(f[5]!=0||f[6]!=0||f[7]!=0||f[8]!=0)throw new IllegalArgumentException("Niepoprawne pola przesunięcia");if(Math.abs(f[2])+Math.abs(f[3])+Math.abs(f[4])<1e-9)throw new IllegalArgumentException("Przesunięcie nie może być zerowe");}
        else if(f[1]==ROTATE){if(Math.abs(f[5]*f[5]+f[6]*f[6]+f[7]*f[7]-1)>1e-6)throw new IllegalArgumentException("Oś obrotu musi być wektorem jednostkowym");if(f[8]==0||Math.abs(f[8])>360)throw new IllegalArgumentException("Kąt obrotu: niezerowy, maks. 360°");}
        else if(f[1]==SCALE){if(f[5]!=0||f[6]!=0||f[7]!=0)throw new IllegalArgumentException("Niepoprawne pola skalowania");if(f[8]<.001||f[8]>1000||f[8]==1)throw new IllegalArgumentException("Współczynnik skali: od 0,001 do 1000, różny od 1");}
        else throw new IllegalArgumentException("Nieznany rodzaj przekształcenia");
    }
    public static String label(double[] f){return f[1]==MOVE?"Przesunięcie bryły":f[1]==ROTATE?"Obrót bryły":"Skalowanie bryły";}
    public static String describe(double[] f) {
        if(f[1]==MOVE)return String.format(Locale.US,"Przesunięcie (%.3f, %.3f, %.3f) mm",f[2],f[3],f[4]);
        if(f[1]==ROTATE)return String.format(Locale.US,"Obrót %.3f° wokół osi (%.3f, %.3f, %.3f)",f[8],f[5],f[6],f[7]);
        return String.format(Locale.US,"Skalowanie × %.6f",f[8]);
    }
}
