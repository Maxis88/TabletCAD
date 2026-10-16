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

    public static String stepRoundTrip(String path) {
        return LOADED ? nativeStepRoundTrip(path) : "STEP: rdzeń C++ niedostępny";
    }

    private static native String nativeKernelStatus();
    private static native double[] nativeCreatePrism(double width, double height, double depth);
    private static native String nativeModelInfo(double width, double height, double depth);
    private static native String nativeStepRoundTrip(String path);
}
