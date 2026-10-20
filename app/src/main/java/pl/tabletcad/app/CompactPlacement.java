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
    /**
     * Top-left corner of a w×h options group next to a drag arrow (base a → tip h) that covers
     * neither the arrow shaft nor the tip (kept clear by {@code clear}); first fitting candidate
     * of: right, left, above, below the tip, then either side of the shaft middle.
     */
    public static float[] beside(float ax,float ay,float hx,float hy,float w,float h,float left,float top,float right,float bottom,float clear) {
        float mx=(ax+hx)/2,my=(ay+hy)/2;
        float[][] c={{hx+clear,hy-h/2},{hx-clear-w,hy-h/2},{hx-w/2,hy-clear-h},{hx-w/2,hy+clear},
            {mx+clear,my-h/2},{mx-clear-w,my-h/2},{mx-w/2,my-clear-h},{mx-w/2,my+clear}};
        for(float[] p:c) {
            if(p[0]<left||p[1]<top||p[0]+w>right||p[1]+h>bottom)continue;
            if(segmentHitsBox(ax,ay,hx,hy,p[0]-clear/2,p[1]-clear/2,p[0]+w+clear/2,p[1]+h+clear/2))continue;
            return p;
        }
        return new float[]{Math.max(left,Math.min(right-w,hx+clear)),Math.max(top,Math.min(bottom-h,hy+clear))};
    }
    static boolean segmentHitsBox(float ax,float ay,float bx,float by,float x0,float y0,float x1,float y1) {
        for(int i=0;i<=24;i++){float t=i/24f,x=ax+(bx-ax)*t,y=ay+(by-ay)*t;if(x>=x0&&x<=x1&&y>=y0&&y<=y1)return true;}
        return false;
    }
}
