package pl.tabletcad.app;

import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import java.util.*;

/**
 * Glyph outlines of the system typefaces (offline, built into Android) for {@link SketchText}.
 * Outlines are flattened with a tolerance of about 1/400 of the capital height (0.0125 mm for
 * 5 mm letters), in units where the capital letter height is 1.
 */
public final class AndroidTextFont implements SketchText.Font {
    private static final float SIZE=400f;
    private final Map<String,SketchText.Glyph> cache=new HashMap<>();
    private final Paint[] paints=new Paint[SketchText.FONTS.length];
    private final float[] cap=new float[SketchText.FONTS.length];

    private Paint paint(int font) {
        if(paints[font]==null) {
            Typeface face=font==1?Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD):font==2?Typeface.SERIF:font==3?Typeface.MONOSPACE:Typeface.SANS_SERIF;
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTypeface(face);p.setTextSize(SIZE);
            Rect r=new Rect();p.getTextBounds("H",0,1,r);cap[font]=Math.max(1,r.height());paints[font]=p;
        }
        return paints[font];
    }
    @Override public synchronized SketchText.Glyph glyph(String c,int font) {
        String key=font+":"+c;SketchText.Glyph cached=cache.get(key);if(cached!=null)return cached;
        Paint p=paint(font);float h=cap[font];
        if(!c.equals(" ")&&!p.hasGlyph(c))return null;
        double advance=p.measureText(c)/h;
        Path path=new Path();p.getTextPath(c,0,c.length(),0,0,path);
        float[] a=path.approximate(h/400f);
        List<double[]> loops=new ArrayList<>();List<Double> cur=new ArrayList<>();
        for(int i=0;i<a.length;i+=3) {
            // A new contour starts where the length fraction does not advance but the point jumps.
            boolean jump=i>0&&a[i]==a[i-3]&&(a[i+1]!=a[i-2]||a[i+2]!=a[i-1]);
            if(jump){close(cur,loops);cur=new ArrayList<>();}
            double x=a[i+1]/h,y=-a[i+2]/h;int n=cur.size();
            if(n>=2&&Math.abs(cur.get(n-2)-x)<1e-9&&Math.abs(cur.get(n-1)-y)<1e-9)continue;
            cur.add(x);cur.add(y);
        }
        close(cur,loops);
        SketchText.Glyph g=new SketchText.Glyph(advance,loops);cache.put(key,g);return g;
    }
    private static void close(List<Double> cur,List<double[]> loops) {
        int n=cur.size();if(n>=4&&Math.abs(cur.get(0)-cur.get(n-2))<1e-9&&Math.abs(cur.get(1)-cur.get(n-1))<1e-9){cur.remove(n-1);cur.remove(n-2);}
        if(cur.size()<6)return;
        // Too dense loops are thinned (the kernel and the sketch keep at most 4096 points per loop).
        int step=Math.max(1,cur.size()/2/2048);double[] l=new double[(cur.size()/2+step-1)/step*2];int k=0;
        for(int i=0;i<cur.size()/2&&k<l.length;i+=step){l[k++]=cur.get(2*i);l[k++]=cur.get(2*i+1);}
        if(Math.abs(SketchContours.area(l))<1e-8)return;
        loops.add(l);
    }
}
