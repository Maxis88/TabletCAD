package pl.tabletcad.app;

import java.util.function.DoubleFunction;

/**
 * Sign of the on-model extrusion value relative to the sketch plane: material going above the
 * plane (along its normal) reads positive, below it reads negative — for join, new body and cut
 * alike. The stored record keeps its own convention (magnitude + "reverse" along the operation axis).
 */
public final class ExtrusionSign {
    private ExtrusionSign() {}
    /** Depth measured from the sketch plane: positive above, negative below. */
    public static double relativeToPlane(double signedDepth,double planeSign,boolean symmetric) {
        if(symmetric)return Math.abs(signedDepth);
        return signedDepth*(planeSign<0?-1:1);
    }
    /** Text for the value chip: "−20 mm" below the plane, "20 mm" above it, "↕ 20 mm" for symmetric (total length, as in the field). */
    public static String label(double signedDepth,double planeSign,boolean symmetric,DoubleFunction<String> format) {
        if(symmetric)return "↕ "+format.apply(Math.abs(signedDepth));
        double value=relativeToPlane(signedDepth,planeSign,false);
        return (value<0?"−":"")+format.apply(Math.abs(value));
    }
}
