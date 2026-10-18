import pl.tabletcad.app.NativeCadEngine;

/**
 * Headless OCCT check of the reference part from the CAD workflow (100x60x20 plate,
 * through hole d10, R3 corner fillet, STEP round trip) and of kernel failure diagnostics:
 * a failing operation must report its history index and a concrete reason.
 */
public final class KernelDiagnosticsProbe {
    private static final double[] BASE = {0, 0, 0, 100, 60, 0, 20};
    private static final double[] HOLE = {1, 1, 25, 30, 30, 30, 20, 0, 0, 20, 1, 0, 0, 0, 1, 0};
    private static final double[] FILLET = {-1, 0, 0, 0, 0, 0, 0, 10, 0, 0, 20, 3, 0, 0, 0, 0};

    private static double[] concat(double[]... parts) {
        int length = 0; for (double[] p : parts) length += p.length;
        double[] out = new double[length]; int offset = 0;
        for (double[] p : parts) { System.arraycopy(p, 0, out, offset, p.length); offset += p.length; }
        return out;
    }
    private static double volume(double[] features, String step) {
        double[] p = NativeCadEngine.bodyProperties(100, 60, 20, 0, step == null ? BASE : null, features, step);
        if (p.length != 7) throw new AssertionError("No body properties: " + NativeCadEngine.lastFailure().getMessage());
        if (p[5] != 1) throw new AssertionError("Expected one solid, got " + p[5]);
        return p[0];
    }
    private static void close(String what, double actual, double expected, double tolerance) {
        if (Math.abs(actual - expected) > tolerance) throw new AssertionError(what + ": " + actual + " != " + expected);
    }
    private static void expectFailure(String what, double[] features, int index, String fragment) {
        try {
            NativeCadEngine.evaluateModelChecked(100, 60, 20, 0, BASE, features, null);
        } catch (NativeCadEngine.KernelException failure) {
            if (failure.feature != index) throw new AssertionError(what + ": failing index " + failure.feature + " != " + index + " (" + failure.reason + ")");
            if (!failure.reason.contains(fragment)) throw new AssertionError(what + ": reason '" + failure.reason + "' lacks '" + fragment + "'");
            System.out.println("PASS diagnostics " + what + ": #" + failure.feature + " " + failure.reason);
            return;
        }
        throw new AssertionError(what + ": invalid model accepted");
    }
    public static void main(String[] args) {
        close("plate", volume(new double[0], null), 120000, 1e-6);
        double hole = 120000 - Math.PI * 25 * 20;
        close("plate with hole", volume(HOLE, null), hole, 1e-6);
        double fillet = hole - (9 - Math.PI * 9 / 4) * 20;
        double[] part = concat(HOLE, FILLET);
        close("plate with hole and R3", volume(part, null), fillet, 1e-6);
        // Parametric edit of the base sketch: 100 -> 120 mm keeps the hole and the fillet.
        double[] wider = {0, 0, 0, 120, 60, 0, 20};
        double[] p = NativeCadEngine.bodyProperties(120, 60, 20, 0, wider, part, null);
        if (p.length != 7) throw new AssertionError("Edited base failed: " + NativeCadEngine.lastFailure().getMessage());
        close("edited base", p[0], fillet + 20 * 60 * 20, 1e-6);
        // STEP round trip keeps exact B-Rep volume.
        if (NativeCadEngine.evaluateFeatures(100, 60, 20, 0, BASE, part, args[0]).length == 0)
            throw new AssertionError("STEP export failed: " + NativeCadEngine.lastFailure().getMessage());
        close("STEP round trip", volume(new double[0], args[0]), fillet, 1e-4);
        System.out.println("PASS reference part: volume=" + fillet);

        double[] miss = {1, 1, 300, 30, 305, 30, 20, 0, 0, 20, 1, 0, 0, 0, 1, 0};
        expectFailure("cut outside body", concat(HOLE, miss), 1, "nie przecina");
        double[] floating = {0, 0, 200, 200, 210, 210, 5, 0, 0, 40, 1, 0, 0, 0, 1, 0};
        expectFailure("disjoint add", floating, 0, "nie styka");
        double[] huge = FILLET.clone(); huge[11] = 70;
        expectFailure("oversized fillet", concat(HOLE, huge), 1, "zaokrąglenia");
        double[] lost = FILLET.clone(); lost[2] = 7; lost[5] = 7; lost[8] = 7;
        expectFailure("missing edge", lost, 0, "Nie odnaleziono");
        double[] chain = concat(HOLE, FILLET, miss);
        expectFailure("third operation", chain, 2, "nie przecina");
        // A failure must not leak into the next successful evaluation.
        if (NativeCadEngine.evaluateModelChecked(100, 60, 20, 0, BASE, part, null).length == 0)
            throw new AssertionError("Valid model rejected after failure");
        System.out.println("PASS kernel diagnostics");
    }
}
