package pl.tabletcad.app;

import android.graphics.*;
import java.util.Arrays;

/**
 * Depth-buffered rasteriser for the shaded model. The former painter's algorithm (triangles sorted
 * by centroid depth) let long or large triangles cover nearer ones: thread flanks inside a bore
 * showed through the front face while orbiting. Here every pixel keeps the nearest surface:
 * 1/w (q) is affine in screen space, so it is interpolated exactly with the barycentric weights.
 */
final class DepthRaster {
    private int w,h;private int[] pixels=new int[0];private float[] depth=new float[0];private Bitmap bitmap;
    private float scale=1;private int left,top;
    /** Camera distance D: depth is stored as (q·D − 1)·D, affine in q (exact interpolation) and ≈ z, so
     *  the huge distance of the orthographic view does not wash out float precision. */
    private double distance=1;
    private static final float EMPTY=-Float.MAX_VALUE;
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    /** Starts a frame for the viewport rectangle (view pixels) at the given resolution factor. */
    void begin(Rect viewport,float resolution){begin(viewport,resolution,1);}
    void begin(Rect viewport,float resolution,double cameraDistance) {
        distance=cameraDistance>0?cameraDistance:1;
        scale=resolution;left=viewport.left;top=viewport.top;
        int width=Math.max(1,(int)Math.ceil(viewport.width()*resolution)),height=Math.max(1,(int)Math.ceil(viewport.height()*resolution));
        if(width!=w||height!=h){w=width;h=height;pixels=new int[w*h];depth=new float[w*h];bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);}
        Arrays.fill(pixels,0);Arrays.fill(depth,EMPTY);
    }
    /** Triangle in view pixels with the perspective factor q = 1/(distance − z) per vertex. */
    void triangle(float ax,float ay,float aq,float bx,float by,float bq,float cx,float cy,float cq,int color) {
        if(!(aq>0&&bq>0&&cq>0))return;
        float x0=(ax-left)*scale,y0=(ay-top)*scale,x1=(bx-left)*scale,y1=(by-top)*scale,x2=(cx-left)*scale,y2=(cy-top)*scale,q0=key(aq),q1=key(bq),q2=key(cq);
        float area=(x1-x0)*(y2-y0)-(x2-x0)*(y1-y0);
        if(area<0){float t=x1;x1=x2;x2=t;t=y1;y1=y2;y2=t;t=q1;q1=q2;q2=t;area=-area;}
        if(!(area>1e-7f))return;
        int minX=Math.max(0,(int)Math.floor(Math.min(x0,Math.min(x1,x2)))),maxX=Math.min(w-1,(int)Math.ceil(Math.max(x0,Math.max(x1,x2))));
        int minY=Math.max(0,(int)Math.floor(Math.min(y0,Math.min(y1,y2)))),maxY=Math.min(h-1,(int)Math.ceil(Math.max(y0,Math.max(y1,y2))));
        if(minX>maxX||minY>maxY)return;
        final float inv=1f/area,d0=-(y2-y1),d1=-(y0-y2),d2=-(y1-y0);
        // Tiny tolerance closes hairline cracks between neighbours (shared edges are drawn twice, never missed).
        final float eps=-1e-4f*area;
        for(int y=minY;y<=maxY;y++) {
            float py=y+.5f,px=minX+.5f;
            float w0=(x2-x1)*(py-y1)-(y2-y1)*(px-x1),w1=(x0-x2)*(py-y2)-(y0-y2)*(px-x2),w2=(x1-x0)*(py-y0)-(y1-y0)*(px-x0);
            int i=y*w+minX;
            for(int x=minX;x<=maxX;x++,i++,w0+=d0,w1+=d1,w2+=d2) {
                if(w0<eps||w1<eps||w2<eps)continue;
                float q=(w0*q0+w1*q1+w2*q2)*inv;
                if(q>depth[i]){depth[i]=q;pixels[i]=color;}
            }
        }
    }
    /**
     * Visible parts of a line (view pixels, q per end) against the filled depth buffer: a point is
     * visible when it is not farther than the stored surface by more than tolerance (distance units).
     * Linear in the line's length — replaces testing every edge against every triangle.
     */
    java.util.List<double[]> visibleSegments(float ax,float ay,double aq,float bx,float by,double bq,double tolerance) {
        java.util.List<double[]> out=new java.util.ArrayList<>();
        float x0=(ax-left)*scale,y0=(ay-top)*scale,x1=(bx-left)*scale,y1=(by-top)*scale;
        int steps=Math.max(1,(int)Math.ceil(Math.hypot(x1-x0,y1-y0)/2));double start=-1;
        for(int k=0;k<=steps;k++) {
            double t=(double)k/steps;int px=(int)(x0+(x1-x0)*t),py=(int)(y0+(y1-y0)*t);
            double q=aq+(bq-aq)*t;boolean visible=true;
            if(q>0&&px>=0&&py>=0&&px<w&&py<h) {
                float stored=depth[py*w+px],edge=key(q);
                // Farthest surface among the pixel and its neighbours (an edge lies between two faces).
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int x=px+dx,y=py+dy;if(x>=0&&y>=0&&x<w&&y<h&&depth[y*w+x]!=EMPTY)stored=stored==EMPTY?depth[y*w+x]:Math.min(stored,depth[y*w+x]);}
                if(stored!=EMPTY&&edge<stored-tolerance)visible=false;
            }
            if(visible&&start<0)start=t;
            if(!visible&&start>=0){out.add(new double[]{start,t});start=-1;}
        }
        if(start>=0)out.add(new double[]{start,1});
        return out;
    }
    private float key(double q){return (float)((q*distance-1)*distance);}
    void draw(Canvas canvas,Rect viewport) {
        bitmap.setPixels(pixels,0,w,0,0,w,h);
        canvas.drawBitmap(bitmap,new Rect(0,0,w,h),new RectF(viewport.left,viewport.top,viewport.left+w/scale,viewport.top+h/scale),paint);
    }
}
