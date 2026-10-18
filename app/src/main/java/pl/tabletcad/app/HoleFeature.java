package pl.tabletcad.app;

import java.util.Locale;

/**
 * Parametric hole placed on a planar face (Fusion-style Hole command).
 *
 * <p>Record (16 values): -14, end (0 blind, 1 through all), point on the face xyz,
 * outward face normal xyz, creator tag of the face, diameter, depth (blind),
 * head (0 simple, 1 counterbore, 2 countersink), head diameter, counterbore depth
 * or countersink angle [°], drill-point angle [°] (0 = flat bottom), reserved 0.
 * The kernel projects the point onto the current plane of the referenced face, so the
 * hole follows parametric edits of that face; a lost face is reported, never guessed.</p>
 */
public final class HoleFeature {
    public static final double TYPE=-14;
    public static final int SIMPLE=0,COUNTERBORE=1,COUNTERSINK=2;
    private HoleFeature() {}
    public static boolean is(double[] f){return f!=null&&f.length==16&&f[0]==TYPE;}
    public static double[] create(double[] point,double[] normal,int origin,double diameter,boolean through,double depth,
            int head,double headDiameter,double headValue,double tipAngle) {
        double l=Math.sqrt(normal[0]*normal[0]+normal[1]*normal[1]+normal[2]*normal[2]);
        if(!(l>1e-9))throw new IllegalArgumentException("Wskazana ściana nie ma określonej normalnej");
        double[] f={TYPE,through?1:0,point[0],point[1],point[2],normal[0]/l,normal[1]/l,normal[2]/l,origin,diameter,through?0:depth,
            head,head==SIMPLE?0:headDiameter,head==SIMPLE?0:headValue,through?0:tipAngle,0};
        validate(f);return f;
    }
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis otworu");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry otworu muszą być skończone");
        if(f[1]!=0&&f[1]!=1)throw new IllegalArgumentException("Niepoprawny rodzaj zakończenia otworu");
        for(int i=2;i<5;i++)if(Math.abs(f[i])>10000)throw new IllegalArgumentException("Środek otworu poza zakresem");
        if(Math.abs(f[5]*f[5]+f[6]*f[6]+f[7]*f[7]-1)>1e-6)throw new IllegalArgumentException("Normalna ściany musi być wektorem jednostkowym");
        if(f[8]!=Math.floor(f[8])||f[8]<-2)throw new IllegalArgumentException("Niepoprawne źródło ściany otworu");
        if(f[9]<.05||f[9]>2000)throw new IllegalArgumentException("Średnica otworu: od 0,05 do 2000 mm");
        boolean through=f[1]==1;
        if(through?f[10]!=0||f[14]!=0:f[10]<.01||f[10]>5000)throw new IllegalArgumentException("Głębokość otworu: od 0,01 do 5000 mm");
        if(f[14]!=0&&(f[14]<30||f[14]>179))throw new IllegalArgumentException("Kąt wierzchołkowy wiertła: 30–179° albo 0 (płaskie dno)");
        int head=(int)f[11];if(f[11]!=head||head<SIMPLE||head>COUNTERSINK)throw new IllegalArgumentException("Niepoprawny rodzaj pogłębienia");
        if(head==SIMPLE&&(f[12]!=0||f[13]!=0))throw new IllegalArgumentException("Otwór prosty nie ma pogłębienia");
        if(head!=SIMPLE&&f[12]<=f[9])throw new IllegalArgumentException("Średnica pogłębienia musi być większa od średnicy otworu");
        if(head==COUNTERBORE&&(f[13]<.01||(!through&&f[13]>=f[10])))throw new IllegalArgumentException("Głębokość pogłębienia walcowego musi być dodatnia i mniejsza od głębokości otworu");
        if(head==COUNTERSINK&&(f[13]<30||f[13]>179))throw new IllegalArgumentException("Kąt pogłębienia stożkowego: 30–179°");
        if(f[15]!=0)throw new IllegalArgumentException("Niepoprawne pole zarezerwowane otworu");
    }
    /** Exact removed volume of the hole tool when it lies fully inside material (for checks). */
    public static double toolVolume(double[] f,double throughLength) {
        double r=f[9]/2,depth=f[1]==1?throughLength:f[10],v=Math.PI*r*r*depth;
        if(f[1]==0&&f[14]>0){double h=r/Math.tan(Math.toRadians(f[14]/2));v+=Math.PI*r*r*h/3;}
        if(f[11]==COUNTERBORE){double R=f[12]/2;v+=Math.PI*(R*R-r*r)*f[13];}
        if(f[11]==COUNTERSINK){double R=f[12]/2,h=(R-r)/Math.tan(Math.toRadians(f[13]/2));v+=Math.PI*h*(R*R+R*r+r*r)/3-Math.PI*r*r*h;}
        return v;
    }
    public static String describe(double[] f) {
        String head=f[11]==COUNTERBORE?String.format(Locale.US," · pogłębienie Ø%.3f × %.3f",f[12],f[13]):f[11]==COUNTERSINK?String.format(Locale.US," · fazka Ø%.3f / %.0f°",f[12],f[13]):"";
        return String.format(Locale.US,"Otwór Ø%.3f · %s%s",f[9],f[1]==1?"na wylot":String.format(Locale.US,"głęb. %.3f",f[10]),head);
    }
}
