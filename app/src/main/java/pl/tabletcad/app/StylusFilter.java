package pl.tabletcad.app;

/**
 * Stylus and palm handling. Once a stylus has been used, a resting palm or a finger touching the
 * screen while the stylus is down (or right after it lifted) must not draw, select or move the
 * camera. Two-finger gestures stay available when no stylus is in contact. Pure logic: the
 * workspace feeds tool type, contact size and timing of each new gesture.
 */
public final class StylusFilter {
    /** MotionEvent.TOOL_TYPE_* values (copied to keep this class free of Android). */
    public static final int FINGER=1,STYLUS=2,MOUSE=3,ERASER=4;
    /** A finger touching within this time after the stylus lifted is treated as the palm. */
    public static final long PALM_GRACE_MS=350;
    private boolean stylusSeen,stylusDown;private long stylusUpAt=Long.MIN_VALUE/2;
    private boolean rejecting;

    public boolean stylusSeen(){return stylusSeen;}
    public boolean stylusDown(){return stylusDown;}
    public boolean rejecting(){return rejecting;}

    /**
     * Decision for the first pointer of a gesture (ACTION_DOWN). {@code contactMm} is the touch
     * major axis in millimetres (0 when unknown). Returns true when the gesture is accepted.
     */
    public boolean down(int toolType,double contactMm,long now) {
        boolean pen=toolType==STYLUS||toolType==ERASER;
        if(pen){stylusSeen=true;stylusDown=true;rejecting=false;return true;}
        if(stylusSeen&&(stylusDown||now-stylusUpAt<PALM_GRACE_MS)){rejecting=true;return false;}
        // A contact far larger than a fingertip is a palm or the side of the hand.
        if(stylusSeen&&contactMm>14){rejecting=true;return false;}
        rejecting=false;return true;
    }
    /** An additional pointer joined; a finger joining an active stylus stroke is ignored. */
    public boolean pointerDown(int toolType) {
        return !(stylusDown&&toolType!=STYLUS&&toolType!=ERASER);
    }
    /** End of a gesture (ACTION_UP / ACTION_CANCEL). */
    public void up(int toolType,long now) {
        if(toolType==STYLUS||toolType==ERASER){stylusDown=false;stylusUpAt=now;}
        rejecting=false;
    }
    /** Pick and snap radii are smaller for the stylus: the tip is precise, a finger is not. */
    public static float pickScale(int toolType){return toolType==STYLUS||toolType==ERASER?0.6f:1f;}
}
