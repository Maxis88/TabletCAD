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

    public static double[] tessellateStep(String path) {
        return LOADED ? nativeTessellateStep(path) : new double[0];
    }

    public static String stepRoundTrip(String path) {
        return LOADED ? nativeStepRoundTrip(path) : "STEP: rdzeń C++ niedostępny";
    }

    public static boolean exportStep(String path, double width, double height, double depth,
            boolean sketchExtruded, String type, double x1, double y1, double x2, double y2,
            String plane) {
        return LOADED && nativeExportStep(path, width, height, depth, sketchExtruded, type,
            x1, y1, x2, y2, plane);
    }

    private static native String nativeKernelStatus();
    private static native double[] nativeCreatePrism(double width, double height, double depth);
    private static native String nativeModelInfo(double width, double height, double depth);
    private static native double[] nativeTessellatePrism(double width, double height, double depth);
    private static native double[] nativeTessellateSketchExtrusion(String type, double x1,
        double y1, double x2, double y2, double depth, String plane);
    private static native String nativeStepRoundTrip(String path);
    private static native boolean nativeExportStep(String path, double width, double height,
        double depth, boolean sketchExtruded, String type, double x1, double y1, double x2,
        double y2, String plane);
    private static native double[] nativeTessellateStep(String path);
}
