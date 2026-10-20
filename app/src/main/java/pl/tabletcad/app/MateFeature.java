package pl.tabletcad.app;

import java.util.Locale;

/**
 * Persistent face-to-face mate of two bodies ("Dosuń" between ordinary bodies), re-solved on every rebuild,
 * so the moved body follows later changes of either body. Routed to the moving body.
 * Record (24): -25, kind (1 contact – opposite normals, 2 flush – same direction, 3 coaxial cylinders: points
 * and normals are axis points and directions), other body id, gap,
 * moving face point xyz, outward normal xyz, creator; target face point xyz, outward normal xyz, creator;
 * six reserved zeros. Faces are found like shell/hole faces (creator + normal + point); a lost face is reported.
 */
public final class MateFeature {
    public static final double TYPE=-25;
    public static final int LENGTH=24,CONTACT=1,FLUSH=2,AXIS=3,ANGLE=4;
    private MateFeature() {}
    public static boolean is(double[] f){return f!=null&&f.length==LENGTH&&f[0]==TYPE;}
    public static double[] create(int kind,int otherBody,double gap,double[] movingPoint,double[] movingNormal,int movingCreator,double[] targetPoint,double[] targetNormal,int targetCreator) {
        double[] f=new double[LENGTH];f[0]=TYPE;f[1]=kind;f[2]=otherBody;f[3]=gap;
        double[] m=unit(movingNormal),t=unit(targetNormal);
        for(int i=0;i<3;i++){f[4+i]=movingPoint[i];f[7+i]=m[i];f[11+i]=targetPoint[i];f[14+i]=t[i];}
        f[10]=movingCreator;f[17]=targetCreator;validate(f);return f;
    }
    static double[] unit(double[] v){double l=Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);if(!(l>1e-9))throw new IllegalArgumentException("Ściana nie ma określonej normalnej");return new double[]{v[0]/l,v[1]/l,v[2]/l};}
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis dosunięcia brył");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry dosunięcia muszą być skończone");
        if(f[1]!=CONTACT&&f[1]!=FLUSH&&f[1]!=AXIS&&f[1]!=ANGLE)throw new IllegalArgumentException("Nieznany rodzaj dosunięcia");
        if(f[1]==ANGLE&&(f[3]<0||f[3]>180))throw new IllegalArgumentException("Kąt między ścianami: 0–180°");
        if(f[2]!=Math.floor(f[2])||f[2]<1)throw new IllegalArgumentException("Niepoprawna bryła docelowa dosunięcia");
        if(Math.abs(f[3])>5000)throw new IllegalArgumentException("Odstęp dosunięcia: ±5000 mm");
        for(int at:new int[]{4,11})for(int i=0;i<3;i++)if(Math.abs(f[at+i])>10000)throw new IllegalArgumentException("Punkt ściany poza zakresem");
        for(int at:new int[]{7,14})if(Math.abs(f[at]*f[at]+f[at+1]*f[at+1]+f[at+2]*f[at+2]-1)>1e-6)throw new IllegalArgumentException("Normalna ściany musi być wektorem jednostkowym");
        for(int at:new int[]{10,17})if(f[at]!=Math.floor(f[at])||f[at]<-2)throw new IllegalArgumentException("Niepoprawne źródło ściany dosunięcia");
        for(int i=18;i<LENGTH;i++)if(f[i]!=0)throw new IllegalArgumentException("Niepoprawne pola zarezerwowane dosunięcia");
    }
    public static int other(double[] f){return (int)f[2];}
    public static double[] withGap(double[] f,int kind,double gap){double[] r=f.clone();r[1]=kind;r[3]=gap;validate(r);return r;}
    public static String describe(double[] f,String otherName){return String.format(Locale.US,"Dosunięcie (%s) do: %s%s",f[1]==AXIS?"współosiowo":f[1]==ANGLE?"kąt":f[1]==FLUSH?"wyrównanie":"styk",otherName,f[1]==ANGLE?String.format(Locale.US,", %.2f°",f[3]):f[3]!=0?String.format(Locale.US,", odstęp %.3f mm",f[3]):"");}
}
