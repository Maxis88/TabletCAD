package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.SketchPrimitive;

/** Persistent point-by-point sketch construction, independent of screen coordinates. */
public final class PolylineDraft {
    private PolylineDraft() {}
    public static void add(List<double[]> points,double u,double v) {
        if(!Double.isFinite(u)||!Double.isFinite(v)||Math.abs(u)>5000||Math.abs(v)>5000)
            throw new IllegalArgumentException("Punkt poza zakresem ±5000 mm");
        if(points.size()>=256)throw new IllegalArgumentException("Łańcuch może mieć najwyżej 256 punktów");
        if(!points.isEmpty()) {double[] last=points.get(points.size()-1);
            if(Math.hypot(u-last[0],v-last[1])<.01)throw new IllegalArgumentException("Wskaż inny punkt, co najmniej 0,01 mm dalej");}
        points.add(new double[]{u,v});
    }
    public static SketchPrimitive closed(List<double[]> points) {
        double[] vertices=new double[points.size()*2];
        for(int i=0;i<points.size();i++) {vertices[i*2]=points.get(i)[0];vertices[i*2+1]=points.get(i)[1];}
        SketchContours.validate(vertices);return new SketchPrimitive(UUID.randomUUID().toString(),vertices);
    }
    public static List<SketchPrimitive> open(List<double[]> points) {
        if(points.size()<2)throw new IllegalArgumentException("Otwarty łańcuch wymaga co najmniej 2 punktów");
        List<SketchPrimitive> result=new ArrayList<>();
        for(int i=1;i<points.size();i++) {double[] a=points.get(i-1),b=points.get(i);
            result.add(new SketchPrimitive(UUID.randomUUID().toString(),"Linia",a[0],a[1],b[0],b[1]));}
        return result;
    }
}
