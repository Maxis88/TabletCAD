package pl.tabletcad.app;

/** Equal-distance OCCT chamfer, identified by an oriented-independent edge signature. */
public final class EdgeChamfer {
    private EdgeChamfer() {}
    public static boolean is(double[] feature){return feature!=null&&(feature.length==16&&feature[0]==-2||RootEdgeReference.is(feature)&&feature[0]==-5);}
    public static double[] create(double[] segments,int offset,double distance) {
        double[] result=EdgeFillet.create(segments,offset,distance);result[0]=-2;return result;
    }
    public static void validate(double[] feature) {
        if(!is(feature))throw new IllegalArgumentException("Niepoprawna operacja fazowania");
        double[] fillet=feature.clone();fillet[0]=RootEdgeReference.is(feature)?-4:-1;
        try{EdgeFillet.validate(fillet);}
        catch(IllegalArgumentException e){throw new IllegalArgumentException("Wielkość fazy: 0,1–1000 mm; pozostałe parametry muszą być poprawne",e);}
    }
}
