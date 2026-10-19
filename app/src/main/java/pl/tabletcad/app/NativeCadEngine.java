package pl.tabletcad.app;

/** JNI boundary kept deliberately small so the future B-Rep kernel stays isolated. */
public final class NativeCadEngine {
    private static final boolean LOADED;

    static {
        boolean loaded;
        try {
            System.loadLibrary("tabletcad");
            loaded = true;
        } catch (UnsatisfiedLinkError error) {
            loaded = false;
        }
        LOADED = loaded;
    }

    private NativeCadEngine() {}

    public static String status() {
        return LOADED ? nativeKernelStatus() : "Rdzeń C++ niedostępny";
    }

    public static double[] createPrism(double width, double height, double depth) {
        return LOADED ? nativeCreatePrism(width, height, depth) : new double[0];
    }

    public static String modelInfo(double width, double height, double depth) {
        return LOADED ? nativeModelInfo(width, height, depth) : "Brak danych B-Rep";
    }

    /**
     * Packed triangles produced by OCCT. Every triangle contains 13 doubles:
     * 3 vertices (x/y/z), face normal (x/y/z) and a stable-in-this-shape face index.
     */
    public static double[] tessellatePrism(double width, double height, double depth) {
        return LOADED ? nativeTessellatePrism(width, height, depth) : new double[0];
    }

    public static double[] tessellateSketchExtrusion(String type, double x1, double y1,
            double x2, double y2, double depth, String plane) {
        return LOADED ? nativeTessellateSketchExtrusion(type, x1, y1, x2, y2, depth, plane)
            : new double[0];
    }

    public static double[] filletPrism(double width, double height, double depth, double radius) {
        return LOADED ? nativeFilletPrism(width, height, depth, radius) : new double[0];
    }

    public static double[] filletSketchExtrusion(String type, double x1, double y1,
            double x2, double y2, double depth, String plane, double radius) {
        return LOADED ? nativeFilletSketchExtrusion(type, x1, y1, x2, y2, depth, plane, radius)
            : new double[0];
    }

    public static double[] applyFaceFeature(double width, double height, double baseDepth,
            double filletRadius, String type, double x1, double y1, double x2, double y2,
            double featureDepth, double ox, double oy, double oz, double ux, double uy,
            double uz, double vx, double vy, double vz, String operation) {
        return LOADED ? nativeApplyFaceFeature(width, height, baseDepth, filletRadius, type,
            x1, y1, x2, y2, featureDepth, ox, oy, oz, ux, uy, uz, vx, vy, vz, operation)
            : new double[0];
    }

    public static double[] tessellateStep(String path) {
        return LOADED ? nativeTessellateStep(path) : new double[0];
    }

    public static double[] evaluateFeatures(double width, double height, double depth,
            double radius, double[] base, double[] features, String exportPath) {
        return evaluateFeatures(width, height, depth, radius, base, features, exportPath, null);
    }

    public static double[] evaluateFeatures(double width, double height, double depth,
            double radius, double[] base, double[] features, String exportPath, String sourceStep) {
        return LOADED ? nativeEvaluateFeatures(width, height, depth, radius, base, features,
            exportPath, sourceStep, 0) : new double[0];
    }

    /** Segments: signature (start/mid/end xyz), segment endpoints xyz, edge index. */
    public static double[] modelEdges(double width,double height,double depth,double radius,
            double[] base,double[] features,String sourceStep) {
        return LOADED ? cached(width,height,depth,radius,base,features,sourceStep,1) : new double[0];
    }

    /** Signature xyz*3, exact length, circle radius, circle center xyz, kind (line/circle/other), edge index. */
    public static double[] edgeMeasurements(double width,double height,double depth,double radius,
            double[] base,double[] features,String sourceStep) {
        return LOADED ? cached(width,height,depth,radius,base,features,sourceStep,2) : new double[0];
    }

    /** Exact per-face analysis, stride 12: face (1-based), body, area, type (0 plane, 1 cylinder, 2 cone,
     *  3 sphere, 4 torus, 5 other), point xyz, direction xyz, radius, creating operation. */
    public static double[] faceAnalysis(double width,double height,double depth,double radius,
            double[] base,double[] features,String sourceStep) {
        return LOADED?cached(width,height,depth,radius,base,features,sourceStep,10):new double[0];
    }

    /** Per body, stride 16: id, volume, centroid xyz, inertia about the centroid for unit density (Ixx Iyy Izz Ixy Ixz Iyz,
     *  mm^5), principal moments (mm^5), area, 0. */
    public static double[] bodyInertia(double width,double height,double depth,double radius,double[] base,double[] features,String sourceStep) {
        return LOADED?cached(width,height,depth,radius,base,features,sourceStep,11):new double[0];
    }
    /** Minimum distance between bodies a and b: distance, closest point on a xyz, on b xyz, overlap volume. */
    public static double[] bodyDistance(double width,double height,double depth,double radius,double[] base,double[] features,String sourceStep,int a,int b) {
        if(!LOADED)return new double[0];
        nativeSetQueryArguments(new double[]{a,b});
        try{return nativeEvaluateFeatures(width,height,depth,radius,base,features,null,sourceStep,12);}finally{nativeSetQueryArguments(null);}
    }
    private static native void nativeSetQueryArguments(double[] values);
    /** Export tessellation [n, mesh(n), face, body …] with chordal deviation [mm] and angular deviation [°]. */
    public static double[] exportMesh(double width,double height,double depth,double radius,double[] base,double[] features,String sourceStep,double deflection,double angleDegrees) {
        if(!LOADED)return new double[0];
        nativeSetQueryArguments(new double[]{deflection,Math.toRadians(angleDegrees)});
        try{return nativeEvaluateFeatures(width,height,depth,radius,base,features,null,sourceStep,13);}finally{nativeSetQueryArguments(null);}
    }

    /** Exact OCCT volume, area, centroid XYZ, solid count and face count (mm units). */
    public static double[] bodyProperties(double width,double height,double depth,double radius,
            double[] base,double[] features,String sourceStep) {
        return LOADED?cached(width,height,depth,radius,base,features,sourceStep,3):new double[0];
    }

    public static double[] evaluateModel(double width,double height,double depth,double radius,double[] base,double[] features,String sourceStep) {
        return LOADED?cached(width,height,depth,radius,base,features,sourceStep,8):new double[0];
    }
    /** Kernel failure with the index of the failing history record (-1 base body, -2 output stage). */
    public static final class KernelException extends IllegalArgumentException {
        public final int feature;
        public final String reason;
        KernelException(int feature,String reason){super(reason);this.feature=feature;this.reason=reason;}
    }

    /** Diagnostics of the last failed evaluation; must be read on the thread that evaluated. */
    public static KernelException lastFailure() {
        if(!LOADED)return new KernelException(-2,"Rdzeń C++ niedostępny");
        String value;
        try{value=nativeLastError();}catch(UnsatisfiedLinkError error){value=null;}
        if(value==null)return new KernelException(-2,"Brak poprawnego wyniku OCCT");
        int tab=value.indexOf('\t');int feature=-2;String reason=tab<0?value:value.substring(tab+1);
        try{feature=Integer.parseInt(tab<0?"-2":value.substring(0,tab));}catch(NumberFormatException ignored){}
        if(reason.isEmpty())reason="Brak poprawnego wyniku OCCT";
        return new KernelException(feature,reason);
    }

    /** Like {@link #evaluateModel} but throws a {@link KernelException} describing the failure. */
    public static double[] evaluateModelChecked(double width,double height,double depth,double radius,double[] base,double[] features,String sourceStep) {
        double[] result=evaluateModel(width,height,depth,radius,base,features,sourceStep);
        if(result==null||result.length==0)throw lastFailure();
        return result;
    }

    /**
     * Display-only section of the evaluated model: every body intersected with the half-space
     * behind the plane (point xyz, normal xyz). Same packed format as {@link #evaluateModel}.
     */
    public static double[] sectionModel(double width,double height,double depth,double radius,double[] base,double[] features,String sourceStep,double[] plane) {
        if(!LOADED)return new double[0];
        nativeSetSectionPlane(plane);
        try{return nativeEvaluateFeatures(width,height,depth,radius,base,features,null,sourceStep,9);}
        finally{nativeSetSectionPlane(null);}
    }
    private static native void nativeSetSectionPlane(double[] plane);

    public static double[] faceOrigins(double width,double height,double depth,double radius,double[] base,double[] features,String sourceStep) {
        return LOADED?cached(width,height,depth,radius,base,features,sourceStep,6):new double[0];
    }

    /** Pure query modes (no export file, no thread-local arguments) go through the memo. */
    private static double[] cached(double width,double height,double depth,double radius,double[] base,double[] features,String sourceStep,int mode) {
        return KernelCache.get(mode,width,height,depth,radius,base,features,sourceStep,()->nativeEvaluateFeatures(width,height,depth,radius,base,features,null,sourceStep,mode));
    }
    private static native double[] nativeEvaluateFeatures(double width, double height,
        double depth, double radius, double[] base, double[] features, String exportPath,
        String sourceStep, int queryMode);

    public static String stepRoundTrip(String path) {
        return LOADED ? nativeStepRoundTrip(path) : "STEP: rdzeń C++ niedostępny";
    }

    /** Segments: view, hidden flag, x1, y1, x2, y2 in model millimetres. */
    public static double[] technicalDrawing(String path) {
        return technicalDrawing(path,.5);
    }
    public static double[] technicalDrawing(String path,double sectionFraction) {
        return LOADED ? nativeTechnicalDrawing(path,sectionFraction) : new double[0];
    }
    private static native double[] nativeTechnicalDrawing(String path,double sectionFraction);

    public static boolean exportStep(String path, double width, double height, double depth,
            boolean sketchExtruded, String type, double x1, double y1, double x2, double y2,
            String plane, double filletRadius, String featureOperation, double featureDepth,
            double ox, double oy, double oz, double ux, double uy, double uz,
            double vx, double vy, double vz) {
        return LOADED && nativeExportStep(path, width, height, depth, sketchExtruded, type,
            x1, y1, x2, y2, plane, filletRadius, featureOperation, featureDepth,
            ox, oy, oz, ux, uy, uz, vx, vy, vz);
    }

    private static native String nativeLastError();
    private static native String nativeKernelStatus();
    private static native double[] nativeCreatePrism(double width, double height, double depth);
    private static native String nativeModelInfo(double width, double height, double depth);
    private static native double[] nativeTessellatePrism(double width, double height, double depth);
    private static native double[] nativeTessellateSketchExtrusion(String type, double x1,
        double y1, double x2, double y2, double depth, String plane);
    private static native double[] nativeFilletPrism(double width, double height, double depth,
        double radius);
    private static native double[] nativeFilletSketchExtrusion(String type, double x1,
        double y1, double x2, double y2, double depth, String plane, double radius);
    private static native double[] nativeApplyFaceFeature(double width, double height,
        double baseDepth, double filletRadius, String type, double x1, double y1, double x2,
        double y2, double featureDepth, double ox, double oy, double oz, double ux, double uy,
        double uz, double vx, double vy, double vz, String operation);
    private static native String nativeStepRoundTrip(String path);
    private static native boolean nativeExportStep(String path, double width, double height,
        double depth, boolean sketchExtruded, String type, double x1, double y1, double x2,
        double y2, String plane, double filletRadius, String featureOperation,
        double featureDepth, double ox, double oy, double oz, double ux, double uy,
        double uz, double vx, double vy, double vz);
    private static native double[] nativeTessellateStep(String path);
}
