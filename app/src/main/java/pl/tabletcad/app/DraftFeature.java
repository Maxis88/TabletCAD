package pl.tabletcad.app;

import java.util.*;

/**
 * Draft (Pochylenie ścian): tapers planar faces about a neutral plane.
 * Record: -16, face count, angle [°] (±0,01–60), neutral plane point xyz, unit pull
 * direction xyz (neutral plane normal), seven reserved zeros, then seven values per face
 * (point inside the face, outward normal, creator tag) as in {@link ShellFeature}.
 */
public final class DraftFeature {
    public static final double TYPE=-16;
    public static final int PREFIX=16,FACE=7,MAX_FACES=16;
    private DraftFeature() {}
    public static boolean is(double[] f){return f!=null&&f.length>=PREFIX&&f[0]==TYPE;}
    public static double[] create(List<ShellFeature.Face> faces,double angle,double[] neutralPoint,double[] pull) {
        if(faces.isEmpty())throw new IllegalArgumentException("Wskaż co najmniej jedną ścianę do pochylenia");
        if(faces.size()>MAX_FACES)throw new IllegalArgumentException("Pochylenie: maksymalnie "+MAX_FACES+" ścian");
        double l=Math.sqrt(pull[0]*pull[0]+pull[1]*pull[1]+pull[2]*pull[2]);if(!(l>1e-9))throw new IllegalArgumentException("Niepoprawny kierunek wyjmowania");
        double[] f=new double[PREFIX+faces.size()*FACE];f[0]=TYPE;f[1]=faces.size();f[2]=angle;
        for(int i=0;i<3;i++){f[3+i]=neutralPoint[i];f[6+i]=pull[i]/l;}
        for(int i=0;i<faces.size();i++){ShellFeature.Face face=faces.get(i);int at=PREFIX+i*FACE;System.arraycopy(face.point,0,f,at,3);System.arraycopy(face.normal,0,f,at+3,3);f[at+6]=face.origin;}
        validate(f);return f;
    }
    public static double[] withAngle(double[] f,double angle){double[] r=f.clone();r[2]=angle;validate(r);return r;}
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis pochylenia");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry pochylenia muszą być skończone");
        int n=(int)f[1];if(f[1]!=n||n<1||n>MAX_FACES||f.length!=PREFIX+n*FACE)throw new IllegalArgumentException("Niepoprawna lista ścian pochylenia");
        if(f[2]==0||Math.abs(f[2])<.01||Math.abs(f[2])>60)throw new IllegalArgumentException("Kąt pochylenia: od 0,01° do 60° (znak zmienia kierunek)");
        for(int i=3;i<6;i++)if(Math.abs(f[i])>10000)throw new IllegalArgumentException("Płaszczyzna neutralna poza zakresem");
        if(Math.abs(f[6]*f[6]+f[7]*f[7]+f[8]*f[8]-1)>1e-6)throw new IllegalArgumentException("Kierunek wyjmowania musi być wektorem jednostkowym");
        for(int i=9;i<PREFIX;i++)if(f[i]!=0)throw new IllegalArgumentException("Niepoprawne pola zarezerwowane pochylenia");
        for(int i=0;i<n;i++){int at=PREFIX+i*FACE;if(Math.abs(f[at+3]*f[at+3]+f[at+4]*f[at+4]+f[at+5]*f[at+5]-1)>1e-6||f[at+6]!=Math.floor(f[at+6])||f[at+6]<-2)throw new IllegalArgumentException("Niepoprawne odwołanie do ściany pochylenia");}
    }
    public static String describe(double[] f){return String.format(Locale.US,"Pochylenie %.3f° · ściany: %d",f[2],(int)f[1]);}
}
