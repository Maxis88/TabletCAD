package pl.tabletcad.app;

/** An ordered B-Rep operation. Never stores a transient topology index as identity. */
public final class EdgeFillet {
    private EdgeFillet() {}
    public static boolean is(double[] feature){return feature!=null&&(feature.length==16&&feature[0]==-1||RootEdgeReference.is(feature)&&feature[0]==-4||EdgeGroup.is(feature)&&feature[0]==-6);}
    public static double[] create(double[] segments,int offset,double radius) {
        if(offset<0||offset+16>segments.length||offset%16!=0)throw new IllegalArgumentException("Invalid edge segment");
        double[] result=new double[16];result[0]=-1;
        System.arraycopy(segments,offset,result,2,9);result[11]=radius;validate(result);return result;
    }
    public static void validate(double[] feature) {
        if(EdgeGroup.is(feature)&&feature[0]==-6){EdgeGroup.validate(feature);return;}
        if(!is(feature))throw new IllegalArgumentException("Invalid edge fillet");
        for(double v:feature)if(!Double.isFinite(v))throw new IllegalArgumentException("Non-finite edge fillet");
        if(RootEdgeReference.is(feature))RootEdgeReference.validate(feature);
        if(feature[1]!=0||feature[12]!=0||feature[13]!=0||feature[14]!=0||feature[15]!=0||feature[11]<.1||feature[11]>1000)
            throw new IllegalArgumentException("Invalid edge fillet radius or flags");
    }
}
