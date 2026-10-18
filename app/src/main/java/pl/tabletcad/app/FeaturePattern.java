package pl.tabletcad.app;

import java.util.*;

/**
 * Parametric mirror and linear/circular patterns of history operations.
 *
 * <p>Record (16 values + source indices): -13, kind (1 mirror, 2 linear, 3 circular),
 * source count, origin xyz, unit direction xyz (mirror normal, pattern direction or
 * rotation axis), instance count including the original, spacing in mm (linear) or
 * total angle in degrees (circular), five reserved zeros, then the strictly increasing
 * indices of earlier committed operations.</p>
 *
 * <p>The record is never sent to OCCT. Before evaluation it is expanded into exact
 * rigid copies (or reflections) of the current source records, so editing a source
 * operation updates every instance. Only operations that carry their own placement
 * (profile extrusions/cuts, revolutions, holes and threads) can be repeated; edge operations
 * refer to concrete edges and are rejected instead of being guessed.</p>
 */
public final class FeaturePattern {
    public static final double TYPE=-13;
    public static final int MIRROR=1,LINEAR=2,CIRCULAR=3,PREFIX=16,MAX_SOURCES=16,MAX_COUNT=100;
    private FeaturePattern() {}

    public static boolean is(double[] f){return f!=null&&f.length>=PREFIX&&f[0]==TYPE;}
    public static int kind(double[] f){return (int)f[1];}
    public static String label(double[] f) {
        int kind=(int)f[1];return kind==MIRROR?"Lustro":kind==LINEAR?"Szyk liniowy":"Szyk kołowy";
    }
    public static int[] sources(double[] f) {
        int[] result=new int[(int)f[2]];for(int i=0;i<result.length;i++)result[i]=(int)f[PREFIX+i];return result;
    }
    public static double[] create(int kind,int[] sources,double[] origin,double[] direction,int count,double value) {
        if(sources==null||sources.length<1||sources.length>MAX_SOURCES)throw new IllegalArgumentException("Wybierz od 1 do "+MAX_SOURCES+" operacji do powielenia");
        double length=Math.sqrt(direction[0]*direction[0]+direction[1]*direction[1]+direction[2]*direction[2]);
        if(!(length>1e-9))throw new IllegalArgumentException(kind==MIRROR?"Niepoprawna płaszczyzna lustra":"Niepoprawny kierunek szyku");
        double[] f=new double[PREFIX+sources.length];f[0]=TYPE;f[1]=kind;f[2]=sources.length;
        for(int i=0;i<3;i++){f[3+i]=origin[i];f[6+i]=direction[i]/length;}
        f[9]=kind==MIRROR?2:count;f[10]=kind==MIRROR?0:value;
        int[] sorted=sources.clone();Arrays.sort(sorted);for(int i=0;i<sorted.length;i++)f[PREFIX+i]=sorted[i];
        validate(f);return f;
    }
    public static double[] withParameters(double[] f,double[] origin,double[] direction,int count,double value) {
        return create((int)f[1],sources(f),origin,direction,count,value);
    }

    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis szyku");
        for(double value:f)if(!Double.isFinite(value))throw new IllegalArgumentException("Parametry szyku muszą być skończone");
        int kind=(int)f[1];if(f[1]!=kind||kind<MIRROR||kind>CIRCULAR)throw new IllegalArgumentException("Nieznany rodzaj szyku");
        int sources=(int)f[2];if(f[2]!=sources||sources<1||sources>MAX_SOURCES||f.length!=PREFIX+sources)throw new IllegalArgumentException("Niepoprawna lista operacji szyku");
        for(int i=3;i<6;i++)if(Math.abs(f[i])>5000)throw new IllegalArgumentException("Punkt odniesienia szyku poza zakresem ±5000 mm");
        double n=f[6]*f[6]+f[7]*f[7]+f[8]*f[8];if(Math.abs(n-1)>1e-9)throw new IllegalArgumentException("Kierunek szyku musi być wektorem jednostkowym");
        for(int i=11;i<16;i++)if(f[i]!=0)throw new IllegalArgumentException("Niepoprawne pola zarezerwowane szyku");
        int count=(int)f[9];
        if(kind==MIRROR){if(f[9]!=2||f[10]!=0)throw new IllegalArgumentException("Niepoprawne parametry lustra");}
        else {
            if(f[9]!=count||count<2||count>MAX_COUNT)throw new IllegalArgumentException("Liczba wystąpień: od 2 do "+MAX_COUNT);
            if(kind==LINEAR&&(Math.abs(f[10])<.001||Math.abs(f[10])*(count-1)>10000))throw new IllegalArgumentException("Odstęp szyku: od 0,001 mm, łączna długość do 10000 mm");
            if(kind==CIRCULAR&&(f[10]<.01||f[10]>360))throw new IllegalArgumentException("Kąt szyku kołowego: od 0,01° do 360°");
        }
        int previous=-1;
        for(int i=0;i<sources;i++){double s=f[PREFIX+i];if(s!=Math.floor(s)||s<=previous)throw new IllegalArgumentException("Indeksy operacji szyku muszą rosnąć");previous=(int)s;}
    }

    /** Whether a committed operation can be repeated by a pattern; null when it can. */
    public static String unsupported(double[] f) {
        if(f==null)return "Brak operacji";
        if(is(f))return "Szyk nie może powielać innego szyku ani lustra";
        if(HoleThread.is(f)||Revolve.is(f)||HoleFeature.is(f))return null;
        if(f[0]==-1||f[0]==-2||f[0]==-4||f[0]==-5||f[0]==-6||f[0]==-7)return "Zaokrąglenia i fazowania odnoszą się do konkretnych krawędzi – nie można ich powielić";
        if(f[0]<0||f.length<16)return "Tej operacji nie można powielić";
        int flags=(int)f[1];
        if((flags&4)!=0)return "Operacja tworząca nową bryłę nie może być powielona szykiem";
        if((flags&8)!=0)return "Część wspólna nie może być powielona szykiem";
        return null;
    }
    public static boolean eligible(double[] f){return unsupported(f)==null;}

    /** Validates source references of the pattern stored at {@code index}. */
    public static void validateSources(List<double[]> features,int index) {
        double[] f=features.get(index);validate(f);
        for(int source:sources(f)) {
            if(source<0||source>=index)throw new IllegalArgumentException("Szyk odwołuje się do operacji, która nie występuje przed nim w historii");
            String reason=unsupported(features.get(source));if(reason!=null)throw new IllegalArgumentException("Operacja nr "+(source+1)+": "+reason);
        }
    }

    /** 3x4 affine matrix [r00 r01 r02 t0 | r10 ... | r20 ...] and orientation (+1 rigid, -1 reflection). */
    static final class Placement {
        final double[] m;final boolean reflection;
        Placement(double[] m,boolean reflection){this.m=m;this.reflection=reflection;}
        double[] point(double x,double y,double z){return new double[]{m[0]*x+m[1]*y+m[2]*z+m[3],m[4]*x+m[5]*y+m[6]*z+m[7],m[8]*x+m[9]*y+m[10]*z+m[11]};}
        double[] vector(double x,double y,double z){return new double[]{m[0]*x+m[1]*y+m[2]*z,m[4]*x+m[5]*y+m[6]*z,m[8]*x+m[9]*y+m[10]*z};}
    }
    static List<Placement> placements(double[] f) {
        validate(f);List<Placement> result=new ArrayList<>();int kind=(int)f[1];
        double px=f[3],py=f[4],pz=f[5],ax=f[6],ay=f[7],az=f[8];
        if(kind==MIRROR) {
            double[] m={1-2*ax*ax,-2*ax*ay,-2*ax*az,0,-2*ay*ax,1-2*ay*ay,-2*ay*az,0,-2*az*ax,-2*az*ay,1-2*az*az,0};
            double d=2*(px*ax+py*ay+pz*az);m[3]=d*ax;m[7]=d*ay;m[11]=d*az;result.add(new Placement(m,true));return result;
        }
        int count=(int)f[9];
        for(int k=1;k<count;k++) {
            if(kind==LINEAR) {
                double t=f[10]*k;result.add(new Placement(new double[]{1,0,0,ax*t,0,1,0,ay*t,0,0,1,az*t},false));
            } else {
                double step=Math.abs(f[10]-360)<1e-9?360.0/count:f[10]/(count-1);
                double a=Math.toRadians(step*k),c=Math.cos(a),s=Math.sin(a),t=1-c;
                double[] r={t*ax*ax+c,t*ax*ay-s*az,t*ax*az+s*ay,t*ax*ay+s*az,t*ay*ay+c,t*ay*az-s*ax,t*ax*az-s*ay,t*ay*az+s*ax,t*az*az+c};
                double[] m={r[0],r[1],r[2],0,r[3],r[4],r[5],0,r[6],r[7],r[8],0};
                m[3]=px-(r[0]*px+r[1]*py+r[2]*pz);m[7]=py-(r[3]*px+r[4]*py+r[5]*pz);m[11]=pz-(r[6]*px+r[7]*py+r[8]*pz);
                result.add(new Placement(m,false));
            }
        }
        return result;
    }

    /** Exact placed copy of one source operation. */
    static double[] place(double[] source,Placement placement) {
        String reason=unsupported(source);if(reason!=null)throw new IllegalArgumentException(reason);
        double[] copy=source.clone();
        if(HoleFeature.is(source)) {
            // Point and face normal move with the instance; the kernel projects onto the face.
            double[] o=placement.point(source[2],source[3],source[4]),n=placement.vector(source[5],source[6],source[7]);
            System.arraycopy(o,0,copy,2,3);System.arraycopy(normalized(n),0,copy,5,3);return copy;
        }
        if(HoleThread.is(source)) {
            double[] o=placement.point(source[2],source[3],source[4]),a=placement.vector(source[5],source[6],source[7]);
            System.arraycopy(o,0,copy,2,3);System.arraycopy(normalized(a),0,copy,5,3);
            if(placement.reflection)copy[1]=-copy[1];
            return copy;
        }
        double[] o=placement.point(source[7],source[8],source[9]),u=placement.vector(source[10],source[11],source[12]),v=placement.vector(source[13],source[14],source[15]);
        System.arraycopy(o,0,copy,7,3);System.arraycopy(normalized(u),0,copy,10,3);System.arraycopy(normalized(v),0,copy,13,3);
        // A reflection keeps the profile in local coordinates but reverses the frame
        // handedness: the extrusion normal and the sense of revolution are negated.
        if(placement.reflection)copy[6]=-copy[6];
        return copy;
    }
    private static double[] normalized(double[] v) {
        double l=Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);return new double[]{v[0]/l,v[1]/l,v[2]/l};
    }

    /** Concatenated kernel records of all additional instances, in instance order. */
    public static double[] expand(List<double[]> features,int index) {
        validateSources(features,index);double[] f=features.get(index);
        List<double[]> records=new ArrayList<>();int length=0;
        for(Placement placement:placements(f))for(int source:sources(f)){double[] copy=place(features.get(source),placement);records.add(copy);length+=copy.length;}
        double[] packed=new double[length];int at=0;for(double[] r:records){System.arraycopy(r,0,packed,at,r.length);at+=r.length;}return packed;
    }

    /** Patterns that reference {@code index}; used to show deletion consequences. */
    public static List<Integer> dependents(List<double[]> features,int index) {
        List<Integer> result=new ArrayList<>();
        for(int i=index+1;i<features.size();i++)if(is(features.get(i)))for(int s:sources(features.get(i)))if(s==index){result.add(i);break;}
        return result;
    }

    public static String describe(double[] f) {
        int kind=(int)f[1];String ops=f[2]==1?"1 operacja":(int)f[2]+" operacje";
        if(kind==MIRROR)return String.format(Locale.US,"Lustro · %s · normalna (%.3f, %.3f, %.3f)",ops,f[6],f[7],f[8]);
        if(kind==LINEAR)return String.format(Locale.US,"Szyk liniowy · %s · %d × %.3f mm",ops,(int)f[9],f[10]);
        return String.format(Locale.US,"Szyk kołowy · %s · %d × %.3f°",ops,(int)f[9],f[10]);
    }
}
