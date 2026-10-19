package pl.tabletcad.app;

import java.util.Locale;

/**
 * Exact angle and face measurements from B-Rep data (edge directions, plane normals, surface axes).
 * Inputs come from {@link NativeCadEngine#edgeMeasurements} and {@link NativeCadEngine#faceAnalysis}.
 */
public final class GeometryMeasure {
    private GeometryMeasure() {}
    public static final int FACE_STRIDE=12;
    /** A picked direction: a straight edge / surface axis ({@code line}) or a plane normal. */
    public static final class Direction {
        public final double[] vector;public final boolean line;public final String label;
        public Direction(double[] v,boolean line,String label){double l=Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);if(!(l>1e-12))throw new IllegalArgumentException("Zerowy kierunek");vector=new double[]{v[0]/l,v[1]/l,v[2]/l};this.line=line;this.label=label;}
    }
    static double angleBetween(double[] a,double[] b){double d=Math.max(-1,Math.min(1,a[0]*b[0]+a[1]*b[1]+a[2]*b[2]));return Math.toDegrees(Math.acos(d));}
    /** Angle in degrees in [0, 90] for lines/planes; plane–plane uses the planes, not the normals. */
    public static double angle(Direction a,Direction b) {
        double between=angleBetween(a.vector,b.vector),acute=Math.min(between,180-between);
        if(a.line==b.line)return acute;
        return 90-acute;// line and plane: complement of the angle to the normal
    }
    /** For two planar faces: angle between outward normals in [0, 180] (180 = opposite parallel faces). */
    public static double normalsAngle(Direction a,Direction b){return angleBetween(a.vector,b.vector);}
    public static String describeAngle(Direction a,Direction b) {
        double value=angle(a,b);String text;
        if(a.line&&b.line)text="Kąt między krawędziami/osiami: "+deg(value);
        else if(!a.line&&!b.line)text="Kąt między płaszczyznami ścian: "+deg(value)+"\nKąt między normalnymi zewnętrznymi: "+deg(normalsAngle(a,b));
        else text="Kąt między krawędzią/osią a płaszczyzną: "+deg(value);
        if(value<1e-7)text+="\n(równoległe)";else if(Math.abs(value-90)<1e-7)text+="\n(prostopadłe)";
        return text;
    }
    static String deg(double v){return String.format(Locale.US,"%.4f°",v);}
    /** Row of the face analysis for a 0-based mesh face, or null. */
    public static double[] face(double[] analysis,int face) {
        for(int i=0;i+FACE_STRIDE<=analysis.length;i+=FACE_STRIDE)if((int)analysis[i]==face+1)return java.util.Arrays.copyOfRange(analysis,i,i+FACE_STRIDE);
        return null;
    }
    public static String surfaceName(double type) {
        switch((int)type){case 0:return "Płaska";case 1:return "Walcowa";case 2:return "Stożkowa";case 3:return "Kulista";case 4:return "Toroidalna";default:return "Swobodna";}
    }
    /** Direction of a face for angle measurement: plane normal or surface axis; null for spheres/free surfaces. */
    public static Direction faceDirection(double[] row) {
        int type=(int)row[3];if(type==0)return new Direction(new double[]{row[7],row[8],row[9]},false,"Ściana płaska");
        if(type==1||type==2||type==4)return new Direction(new double[]{row[7],row[8],row[9]},true,"Oś ściany "+surfaceName(type).toLowerCase(Locale.ROOT));
        return null;
    }
    /** Direction of a straight edge from its measurement row (stride 16, kind at 14). */
    public static Direction edgeDirection(double[] measurement) {
        if(measurement[14]!=0)return null;
        return new Direction(new double[]{measurement[6]-measurement[0],measurement[7]-measurement[1],measurement[8]-measurement[2]},true,"Krawędź prosta");
    }
}
