package pl.tabletcad.app;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import pl.tabletcad.app.model.SketchPrimitive;

/**
 * Sketch pattern along a path: copies of an element placed at equal arc-length steps along a
 * line, polyline, arc contour, spline, circle or ellipse. The reference point of the element
 * (its centre) lands on the path; optionally each copy turns with the path tangent.
 */
public final class SketchPathPattern {
    private SketchPathPattern() {}

    /** Path as a polyline (u,v pairs) and whether it is closed. */
    public static final class Path {
        public final double[] points;public final boolean closed;public final double length;
        Path(double[] points,boolean closed){this.points=points;this.closed=closed;double l=0;int n=points.length/2;
            for(int i=0;i<n-1;i++)l+=Math.hypot(points[2*i+2]-points[2*i],points[2*i+3]-points[2*i+1]);
            if(closed&&n>1)l+=Math.hypot(points[0]-points[2*n-2],points[1]-points[2*n-1]);length=l;}
        /** Point and tangent angle (radians) at a distance along the path. */
        public double[] at(double distance) {
            int n=points.length/2,segments=closed?n:n-1;double left=Math.max(0,distance);
            for(int s=0;s<segments;s++) {
                int i=s,j=(s+1)%n;double ax=points[2*i],ay=points[2*i+1],bx=points[2*j],by=points[2*j+1],l=Math.hypot(bx-ax,by-ay);
                if(l<1e-12)continue;
                if(left<=l||s==segments-1){double t=Math.min(1,left/l);return new double[]{ax+(bx-ax)*t,ay+(by-ay)*t,Math.atan2(by-ay,bx-ax)};}
                left-=l;
            }
            return new double[]{points[0],points[1],0};
        }
    }

    public static Path path(SketchPrimitive p) {
        if(p.closedGeometry()){double[] o=SketchHit.outline(p);if(o==null)throw new IllegalArgumentException("Nieobsługiwana ścieżka");return new Path(o,true);}
        if(SketchSpline.is(p))return new Path(SketchSpline.sample(p.vertices,48),false);
        if(p.curves!=null)return new Path(SketchCurves.samples(p.curves,96),false);
        if(p.vertices!=null)return new Path(p.vertices.clone(),false);
        if(p.type.equals("Linia")||p.type.equals("Pomocnicza"))return new Path(new double[]{p.x1,p.y1,p.x2,p.y2},false);
        throw new IllegalArgumentException("Ścieżką może być linia, łańcuch, łuk, splajn, okrąg lub kontur");
    }

    /**
     * Copies (without the source) at count positions. On an open path the positions include both
     * ends; on a closed path they are spread evenly around it. The source itself is moved to the
     * first position when {@code moveSource} is set, otherwise the copies are offset relative to it.
     */
    public static List<SketchPrimitive> copies(SketchPrimitive source,SketchPrimitive pathEntity,int count,boolean rotate) {
        if(count<2||count>200)throw new IllegalArgumentException("Liczba elementów: 2–200");
        if(source.id.equals(pathEntity.id))throw new IllegalArgumentException("Ścieżka musi być innym elementem niż powielany");
        Path path=path(pathEntity);if(path.length<1e-6)throw new IllegalArgumentException("Ścieżka ma zerową długość");
        double step=path.closed?path.length/count:path.length/(count-1);
        double[] centre=SketchPosition.point(source,SketchPosition.CENTRE);
        double[] first=path.at(0);
        List<SketchPrimitive> out=new ArrayList<>();
        for(int i=1;i<count;i++) {
            double[] at=path.at(step*i);
            double turn=rotate?Math.IEEEremainder(at[2]-first[2],2*Math.PI):0;
            out.add(SketchPattern.transform(source,UUID.randomUUID().toString(),centre[0],centre[1],turn,at[0]-first[0],at[1]-first[1]).withConstruction(source.construction));
        }
        return out;
    }
    /** Translation that puts the element's centre on the start of the path. */
    public static double[] snapToStart(SketchPrimitive source,SketchPrimitive pathEntity) {
        Path path=path(pathEntity);double[] c=SketchPosition.point(source,SketchPosition.CENTRE),s=path.at(0);return new double[]{s[0]-c[0],s[1]-c[1]};
    }
}
