package pl.tabletcad.app;

/** Validation of the renderer's triangle buffer, independent of the B-Rep document. */
public final class CadMesh {
    public static final int TRIANGLE_STRIDE = 13;
    private CadMesh() {}

    public static boolean isValid(double[] triangles, boolean allowEmpty) {
        if (triangles == null || triangles.length % TRIANGLE_STRIDE != 0) return false;
        if (triangles.length == 0) return allowEmpty;
        for (double value : triangles) if (!Double.isFinite(value)) return false;
        for (int i = 12; i < triangles.length; i += TRIANGLE_STRIDE) {
            double face = triangles[i];
            if (face < 0 || face > Integer.MAX_VALUE || face != Math.floor(face)) return false;
        }
        return true;
    }
}
