package pl.tabletcad.app;

import java.util.*;

/**
 * Parametric shell (hollow body) operation evaluated by OCCT.
 *
 * <p>Record: -12, number of removed faces, wall thickness [mm], side (0 inside,
 * 1 outside), twelve reserved zeros, then seven values per removed face:
 * a point inside the face, its outward unit normal and the history tag of the
 * operation that created the face (-1/-2 base body, otherwise the operation index).
 * During rebuild the kernel looks for the planar face with the same creator and
 * normal; when several candidates remain, the point must lie inside exactly one
 * of them. A missing or ambiguous face is reported, never guessed.</p>
 */
public final class ShellFeature {
    public static final double TYPE=-12;
    public static final int PREFIX=16,FACE=7,MAX_FACES=16;
    private ShellFeature() {}
    public static boolean is(double[] f){return f!=null&&f.length>=PREFIX&&f[0]==TYPE;}
    public static final class Face {
        public final double[] point,normal;public final int origin;
        public Face(double[] point,double[] normal,int origin) {
            double l=Math.sqrt(normal[0]*normal[0]+normal[1]*normal[1]+normal[2]*normal[2]);
            if(!(l>1e-9))throw new IllegalArgumentException("Wskazana ściana nie ma określonej normalnej");
            this.point=point.clone();this.normal=new double[]{normal[0]/l,normal[1]/l,normal[2]/l};this.origin=origin;
        }
    }
    public static double[] create(List<Face> faces,double thickness,boolean outside) {
        if(faces.size()>MAX_FACES)throw new IllegalArgumentException("Powłoka: maksymalnie "+MAX_FACES+" usuwanych ścian");
        double[] f=new double[PREFIX+faces.size()*FACE];f[0]=TYPE;f[1]=faces.size();f[2]=thickness;f[3]=outside?1:0;
        for(int i=0;i<faces.size();i++) {
            Face face=faces.get(i);int at=PREFIX+i*FACE;
            System.arraycopy(face.point,0,f,at,3);System.arraycopy(face.normal,0,f,at+3,3);f[at+6]=face.origin;
        }
        validate(f);return f;
    }
    public static double[] withThickness(double[] f,double thickness,boolean outside) {
        double[] copy=f.clone();copy[2]=thickness;copy[3]=outside?1:0;validate(copy);return copy;
    }
    public static List<Face> faces(double[] f) {
        List<Face> result=new ArrayList<>();
        for(int i=0;i<(int)f[1];i++){int at=PREFIX+i*FACE;result.add(new Face(Arrays.copyOfRange(f,at,at+3),Arrays.copyOfRange(f,at+3,at+6),(int)f[at+6]));}
        return result;
    }
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis powłoki");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry powłoki muszą być skończone");
        int count=(int)f[1];
        if(f[1]!=count||count<0||count>MAX_FACES||f.length!=PREFIX+count*FACE)throw new IllegalArgumentException("Niepoprawna lista ścian powłoki");
        if(f[2]<.01||f[2]>1000)throw new IllegalArgumentException("Grubość ścianki: od 0,01 do 1000 mm");
        if(f[3]!=0&&f[3]!=1)throw new IllegalArgumentException("Niepoprawna strona powłoki");
        for(int i=4;i<PREFIX;i++)if(f[i]!=0)throw new IllegalArgumentException("Niepoprawne pola zarezerwowane powłoki");
        for(int i=0;i<count;i++) {
            int at=PREFIX+i*FACE;
            for(int k=0;k<3;k++)if(Math.abs(f[at+k])>10000)throw new IllegalArgumentException("Punkt ściany poza zakresem");
            double n=f[at+3]*f[at+3]+f[at+4]*f[at+4]+f[at+5]*f[at+5];if(Math.abs(n-1)>1e-6)throw new IllegalArgumentException("Normalna ściany musi być wektorem jednostkowym");
            if(f[at+6]!=Math.floor(f[at+6])||f[at+6]<-2)throw new IllegalArgumentException("Niepoprawne źródło ściany powłoki");
            for(int j=0;j<i;j++){int other=PREFIX+j*FACE;if(Math.abs(f[at]-f[other])<1e-9&&Math.abs(f[at+1]-f[other+1])<1e-9&&Math.abs(f[at+2]-f[other+2])<1e-9)throw new IllegalArgumentException("Ta sama ściana została wskazana dwukrotnie");}
        }
    }
    public static String describe(double[] f) {
        return String.format(Locale.US,"Powłoka %.3f mm · %s · usunięte ściany: %d",f[2],f[3]==1?"na zewnątrz":"do wewnątrz",(int)f[1]);
    }
}
