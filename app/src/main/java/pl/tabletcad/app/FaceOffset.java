package pl.tabletcad.app;

import java.util.Locale;

/**
 * Offset of a planar face ("Odsuń ścianę", press-pull). Record (16): -24, distance (+ outward adds material,
 * − inward removes it), face point xyz, outward normal xyz, creator of the face, seven reserved zeros.
 * The face is identified like shell and hole faces (creating operation + normal + point inside it).
 */
public final class FaceOffset {
    public static final double TYPE=-24;
    private FaceOffset() {}
    public static boolean is(double[] f){return f!=null&&f.length==16&&f[0]==TYPE;}
    public static double[] create(double distance,double[] point,double[] normal,int origin) {
        double l=Math.sqrt(normal[0]*normal[0]+normal[1]*normal[1]+normal[2]*normal[2]);if(!(l>1e-9))throw new IllegalArgumentException("Ściana nie ma określonej normalnej");
        double[] f=new double[16];f[0]=TYPE;f[1]=distance;System.arraycopy(point,0,f,2,3);for(int i=0;i<3;i++)f[5+i]=normal[i]/l;f[8]=origin;validate(f);return f;
    }
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis odsunięcia ściany");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry odsunięcia muszą być skończone");
        if(f[1]==0||Math.abs(f[1])>5000)throw new IllegalArgumentException("Odsunięcie ściany: od −5000 do 5000 mm, różne od zera");
        for(int i=2;i<5;i++)if(Math.abs(f[i])>10000)throw new IllegalArgumentException("Punkt ściany poza zakresem");
        if(Math.abs(f[5]*f[5]+f[6]*f[6]+f[7]*f[7]-1)>1e-6)throw new IllegalArgumentException("Normalna ściany musi być wektorem jednostkowym");
        if(f[8]!=Math.floor(f[8])||f[8]<-2)throw new IllegalArgumentException("Niepoprawne źródło ściany");
        for(int i=9;i<16;i++)if(f[i]!=0)throw new IllegalArgumentException("Niepoprawne pola zarezerwowane odsunięcia");
    }
    public static String describe(double[] f){return String.format(Locale.US,"Odsunięcie ściany o %.3f mm (%s)",f[1],f[1]>0?"na zewnątrz":"do wewnątrz");}
}
