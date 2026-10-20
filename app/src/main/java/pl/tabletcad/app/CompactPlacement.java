package pl.tabletcad.app;

/**
 * Where a small property card goes: beside the touched geometry, never over the touch point
 * itself, never under the left tree, the options strip or the history bar. Pure logic.
 */
public final class CompactPlacement {
    private CompactPlacement() {}

    /**
     * Top-left corner of a {@code w}×{@code h} card for a touch at (x,y) inside the work area
     * [left,top]–[right,bottom]. The card prefers the right of the touch, then the left; it keeps
     * {@code gap} from the touch and {@code margin} from the area border. A missing touch (NaN)
     * puts the card in the top-right corner of the work area.
     */
    public static float[] place(float x,float y,float w,float h,float left,float top,float right,float bottom,float margin,float gap) {
        float minX=left+margin,maxX=Math.max(minX,right-margin-w),minY=top+margin,maxY=Math.max(minY,bottom-margin-h);
        if(Float.isNaN(x)||Float.isNaN(y))return new float[]{maxX,minY};
        float cx;
        if(x+gap+w<=right-margin)cx=x+gap;
        else if(x-gap-w>=minX)cx=x-gap-w;
        else cx=x<(left+right)/2?maxX:minX;
        float cy=Math.max(minY,Math.min(maxY,y-h/3));
        return new float[]{Math.max(minX,Math.min(maxX,cx)),cy};
    }
    /** True when the card would cover the point (used by tests and to avoid hiding the edited edge). */
    public static boolean covers(float[] at,float w,float h,float x,float y){return x>=at[0]&&x<=at[0]+w&&y>=at[1]&&y<=at[1]+h;}
}
