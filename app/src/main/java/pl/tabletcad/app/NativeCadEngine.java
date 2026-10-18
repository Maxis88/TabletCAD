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
        return LOADED ? nativeEvaluateFeatures(width,height,depth,radius,base,features,null,sourceStep,1) : new double[0];
    }

    /** Signature xyz*3, exact length, circle radius, circle center xyz, kind (line/circle/other), edge index. */
    public static double[] edgeMeasurements(double width,double height,double depth,double radius,
            double[] base,double[] features,String sourceStep) {
        return LOADED ? nativeEvaluateFeatures(width,height,depth,radius,base,features,null,sourceStep,2) : new double[0];
    }

    /** Exact OCCT volume, area, centroid XYZ, solid count and face count (mm units). */
    public static double[] bodyProperties(double width,double height,double depth,double radius,
            double[] base,double[] features,String sourceStep) {
        return LOADED?nativeEvaluateFeatures(width,height,depth,radius,base,features,null,sourceStep,3):new double[0];
    }

    public static double[] evaluateModel(double width,double height,double depth,double radius,double[] base,double[] features,String sourceStep) {
        return LOADED?nativeEvaluateFeatures(width,height,depth,radius,base,features,null,sourceStep,8):new double[0];
    }
    public static double[] faceOrigins(double width,double height,double depth,double radius,double[] base,double[] features,String sourceStep) {
        return LOADED?nativeEvaluateFeatures(width,height,depth,radius,base,features,null,sourceStep,6):new double[0];
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
