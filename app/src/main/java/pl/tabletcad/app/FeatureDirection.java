package pl.tabletcad.app;

/** Signed record depth: positive keeps the established add/cut direction, negative reverses it. */
public final class FeatureDirection {
    private FeatureDirection() {}
    public static double signed(double length,boolean reverse) {
        if(!Double.isFinite(length)||length<.1||length>5000)
            throw new IllegalArgumentException("Głębokość: 0,1–5000 mm");
        return reverse?-length:length;
    }
    public static double signed(double length,boolean reverse,boolean symmetric) {
        return signed(length,reverse&&!symmetric);
    }
    /** Operation bits: 1=cut, 2=symmetric. Base-plane bits: 0..2=plane, 4=symmetric. */
    public static int featureCode(boolean cut,boolean symmetric){return (cut?1:0)|(symmetric?2:0);}
    public static boolean isCut(double code){validateOperation(code);return ((int)code&1)!=0;}
    public static boolean isSymmetric(double code){validateOperation(code);return ((int)code&2)!=0;}
    public static void validateOperation(double code) {
        if(!Double.isFinite(code)||code!=Math.floor(code)||code<0||code>3)throw new IllegalArgumentException("Niepoprawny tryb operacji");
    }
    public static int basePlane(double code) {
        if(!Double.isFinite(code)||code!=Math.floor(code)||code<0||code>6||((int)code&3)>2)throw new IllegalArgumentException("Niepoprawna płaszczyzna wyciągnięcia");
        return (int)code&3;
    }
    public static boolean baseSymmetric(double code){basePlane(code);return ((int)code&4)!=0;}
    public static int baseCode(double existing,boolean symmetric){return basePlane(existing)|(symmetric?4:0);}
}
