package pl.tabletcad.app;

import java.util.List;
import pl.tabletcad.app.model.SketchPrimitive;

/** Geometric snapping in the active sketch's local millimetre coordinates. */
public final class SketchSnapper {
    private SketchSnapper() {}
    public static double[] anchors(SketchPrimitive p) {
        if(p.curves!=null)return withCentre(p,SketchCurves.samples(p.curves,16));
        if(p.vertices!=null)return withCentre(p,p.vertices.clone());
        if(p.type.equals("Prostokąt"))return new double[]{p.x1,p.y1,p.x2,p.y1,p.x2,p.y2,p.x1,p.y2,(p.x1+p.x2)/2,p.y1,p.x2,(p.y1+p.y2)/2,(p.x1+p.x2)/2,p.y2,p.x1,(p.y1+p.y2)/2,(p.x1+p.x2)/2,(p.y1+p.y2)/2};
        if(p.isEllipseArc()){double[] d=p.ellipseArc(),m=EllipseArc.point(d,(d[5]+d[6])/2);return new double[]{p.x1,p.y1,p.x2,p.y2,d[0],d[1],m[0],m[1]};}
        if(p.isEllipse()){double a=p.major(),ux=(p.x2-p.x1)/a,uy=(p.y2-p.y1)/a,b=p.minor;return new double[]{p.x1,p.y1,p.x1+ux*a,p.y1+uy*a,p.x1-ux*a,p.y1-uy*a,p.x1-uy*b,p.y1+ux*b,p.x1+uy*b,p.y1-ux*b};}
        if(p.type.equals("Okrąg")){double r=Math.hypot(p.x2-p.x1,p.y2-p.y1);return new double[]{p.x1,p.y1,p.x1+r,p.y1,p.x1-r,p.y1,p.x1,p.y1+r,p.x1,p.y1-r};}
        if(p.isPoint())return new double[]{p.x1,p.y1};
        return new double[]{p.x1,p.y1,p.x2,p.y2,(p.x1+p.x2)/2,(p.y1+p.y2)/2};
    }
    /** Adds the surface centre of a closed contour so a moved shape can be centred on a point. */
    private static double[] withCentre(SketchPrimitive p,double[] anchors) {
        double[] c=SketchCenters.center(p);if(c==null)return anchors;
        double[] r=java.util.Arrays.copyOf(anchors,anchors.length+2);r[anchors.length]=c[0];r[anchors.length+1]=c[1];return r;
    }
    /** Snap actual moving geometry, independent of where the finger grabbed it. */
    public static double[] translation(SketchPrimitive source,double dx,double dy,List<SketchPrimitive> geometry,double tolerance) {
        double[] anchors=anchors(source);double best=tolerance;double sx=0,sy=0;
        for(int i=0;i<anchors.length;i+=2) {
            double x=anchors[i]+dx,y=anchors[i+1]+dy;
            double[] hit=snap(x,y,geometry,source.id,tolerance,null,null);
            double distance=Math.hypot(hit[0]-x,hit[1]-y);
            if(distance>1e-10&&distance<best){best=distance;sx=hit[0]-x;sy=hit[1]-y;}
        }
        return new double[]{dx+sx,dy+sy};
    }
    /** What the last snap() locked to (shown next to the marker): Punkt, Środek, Przecięcie, Styczna, Prostopadła, Na krzywej, Oś; empty when free. */
    public static String lastKind="";
    public static double[] snap(double x,double y,List<SketchPrimitive> geometry,
                                String excluded,double tolerance,Double startX,Double startY) {
        double best=tolerance;double[] result={x,y};lastKind="";
        java.util.ArrayList<double[]> points=new java.util.ArrayList<>();
        java.util.ArrayList<double[]> lines=new java.util.ArrayList<>();
        java.util.ArrayList<double[]> arcs=new java.util.ArrayList<>();
        points.add(new double[]{0,0});
        for(SketchPrimitive p:geometry) {
            if(p.id.equals(excluded)) continue;
            // Text: only its anchor (start of the baseline or centre of the circle) attracts.
            if(p.isText()){double[] t=p.textParams();points.add(new double[]{t[0],t[1]});continue;}
            if(SketchSpline.is(p)){for(int i=0;i<p.vertices.length;i+=2)points.add(new double[]{p.vertices[i],p.vertices[i+1]});continue;}
            if(p.type.equals("Okrąg")) {
                points.add(new double[]{p.x1,p.y1});points.add(new double[]{p.x2,p.y2});
                double r=Math.hypot(p.x2-p.x1,p.y2-p.y1);
                if(r>=.01&&r<=5000&&Math.abs(p.x1)+r<=5000&&Math.abs(p.y1)+r<=5000) {
                    points.add(new double[]{p.x1+r,p.y1});points.add(new double[]{p.x1-r,p.y1});
                    points.add(new double[]{p.x1,p.y1+r});points.add(new double[]{p.x1,p.y1-r});
                    if(arcs.size()<=62&&Math.abs(Math.hypot(x-p.x1,y-p.y1)-r)<tolerance*2) {
                        arcs.add(SketchCurves.arc(p.x1+r,p.y1,p.x1,p.y1+r,p.x1-r,p.y1));
                        arcs.add(SketchCurves.arc(p.x1-r,p.y1,p.x1,p.y1-r,p.x1+r,p.y1));
                    }
                }
                continue;
            }
            if(p.curves!=null) {
                for(int i=0;i<p.curves.length;i+=7) {
                    double[] s=java.util.Arrays.copyOfRange(p.curves,i,i+7);
                    points.add(new double[]{s[1],s[2]});points.add(new double[]{s[5],s[6]});
                    if(s[0]==0)lines.add(new double[]{s[1],s[2],s[5],s[6]});
                    else {SketchCurves.Arc a=SketchCurves.arc(s,0);points.add(a.point(.5));points.add(new double[]{a.x,a.y});
                        if(arcs.size()<64&&Math.abs(Math.hypot(x-a.x,y-a.y)-a.r)<tolerance*2)arcs.add(s);}
                }
                continue;
            }
            if(p.vertices!=null) {
                for(int i=0;i<p.vertices.length;i+=2) {
                    int j=(i+2)%p.vertices.length;
                    points.add(new double[]{p.vertices[i],p.vertices[i+1]});
                    lines.add(new double[]{p.vertices[i],p.vertices[i+1],p.vertices[j],p.vertices[j+1]});
                }
                continue;
            }
            points.add(new double[]{p.x1,p.y1});points.add(new double[]{p.x2,p.y2});
            if(p.type.equals("Prostokąt")) {
                points.add(new double[]{p.x1,p.y2});points.add(new double[]{p.x2,p.y1});
                lines.add(new double[]{p.x1,p.y1,p.x2,p.y1});
                lines.add(new double[]{p.x2,p.y1,p.x2,p.y2});
                lines.add(new double[]{p.x2,p.y2,p.x1,p.y2});
                lines.add(new double[]{p.x1,p.y2,p.x1,p.y1});
            } else if(!p.type.equals("Okrąg")&&!p.isPoint()) lines.add(new double[]{p.x1,p.y1,p.x2,p.y2});
        }
        int ends=points.size();
        for(double[] l:lines) points.add(new double[]{(l[0]+l[2])/2,(l[1]+l[3])/2});
        int mids=points.size();
        // Centres of enclosed surfaces (rectangles, polygons, closed contours); circle/ellipse centres are already end points.
        for(SketchPrimitive p:geometry) {
            if(p.id.equals(excluded)||p.type.equals("Okrąg")||p.isEllipse()||!SketchCenters.has(p))continue;
            double[] c=SketchCenters.center(p);if(c!=null)points.add(c);
        }
        int centres=points.size();
        // Bounded segment intersections; construction lines remain ordinary finite guides.
        java.util.ArrayList<double[]> nearby=new java.util.ArrayList<>();
        for(double[] l:lines) {
            if(x>=Math.min(l[0],l[2])-tolerance && x<=Math.max(l[0],l[2])+tolerance
                && y>=Math.min(l[1],l[3])-tolerance && y<=Math.max(l[1],l[3])+tolerance
                && nearby.size()<64) nearby.add(l);
        }
        for(int i=0;i<nearby.size();i++) for(int j=i+1;j<nearby.size();j++) {
            double[] a=nearby.get(i),b=nearby.get(j);
            double ax=a[2]-a[0],ay=a[3]-a[1],bx=b[2]-b[0],by=b[3]-b[1];
            double det=ax*by-ay*bx;if(Math.abs(det)<1e-10) continue;
            double dx=b[0]-a[0],dy=b[1]-a[1];
            double t=(dx*by-dy*bx)/det,u=(dx*ay-dy*ax)/det;
            if(t>=0&&t<=1&&u>=0&&u<=1) points.add(new double[]{a[0]+t*ax,a[1]+t*ay});
        }
        for(double[] line:nearby)for(double[] arc:arcs)points.addAll(SketchCurves.lineHits(line,arc));
        // Circle–circle and arc–arc intersections.
        for(int i=0;i<arcs.size();i++)for(int j=i+1;j<arcs.size();j++)points.addAll(arcHits(SketchCurves.arc(arcs.get(i),0),SketchCurves.arc(arcs.get(j),0)));
        int crossings=points.size(),tangents=crossings;
        if(startX!=null&&startY!=null) {
            // Tangent from the stroke start to a circle or arc, and the perpendicular foot on a line.
            for(double[] arc:arcs){SketchCurves.Arc c=SketchCurves.arc(arc,0);for(double[] t:tangentPoints(startX,startY,c.x,c.y,c.r))if(c.contains(t[0],t[1]))points.add(t);}
            tangents=points.size();
            for(double[] l:lines){double[] f=perpendicularFoot(startX,startY,l);if(f!=null&&Math.hypot(f[0]-startX,f[1]-startY)>1e-9)points.add(f);}
        }
        int hit=-1;
        for(int i=0;i<points.size();i++) {double[] p=points.get(i);
            double d=Math.hypot(x-p[0],y-p[1]);
            if(d<best) {best=d;result=p;hit=i;}
        }
        if(best<tolerance){lastKind=hit<ends?"Punkt":hit<mids?"Środek":hit<centres?"Środek obszaru":hit<crossings?"Przecięcie":hit<tangents?"Styczna":"Prostopadła";return result;} // endpoints/intersections before projected positions
        for(double[] l:lines) {
            double dx=l[2]-l[0],dy=l[3]-l[1],length=dx*dx+dy*dy;
            if(length<1e-12) continue;
            double t=Math.max(0,Math.min(1,((x-l[0])*dx+(y-l[1])*dy)/length));
            double px=l[0]+t*dx,py=l[1]+t*dy,d=Math.hypot(x-px,y-py);
            if(d<best) {best=d;result=new double[]{px,py};}
        }
        if(best<tolerance){lastKind="Na krzywej";return result;}
        for(double[] arc:arcs) {
            SketchCurves.Arc a=SketchCurves.arc(arc,0);double angle=Math.atan2(y-a.y,x-a.x),px=a.x+a.r*Math.cos(angle),py=a.y+a.r*Math.sin(angle),d=Math.hypot(x-px,y-py);
            if(d<best&&a.contains(px,py)){best=d;result=new double[]{px,py};}
        }
        if(best<tolerance){lastKind="Na krzywej";return result;}
        // Horizontal/vertical inference relative to stroke start, then the origin axes.
        double referenceX=startX==null?0:startX,referenceY=startY==null?0:startY;
        if(Math.abs(x-referenceX)<tolerance){result[0]=referenceX;lastKind=startX==null?"Oś":"Pionowo";}
        if(Math.abs(y-referenceY)<tolerance){result[1]=referenceY;lastKind=startX==null?"Oś":"Poziomo";}
        return result;
    }
    /** Points where a line from (sx,sy) touches the circle (centre cx,cy, radius r); empty when the point is inside. */
    public static java.util.List<double[]> tangentPoints(double sx,double sy,double cx,double cy,double r) {
        java.util.List<double[]> out=new java.util.ArrayList<>();double d=Math.hypot(sx-cx,sy-cy);
        if(r<=0||d<=r*(1+1e-12))return out;
        double theta=Math.atan2(sy-cy,sx-cx),beta=Math.acos(r/d);
        out.add(new double[]{cx+r*Math.cos(theta+beta),cy+r*Math.sin(theta+beta)});
        out.add(new double[]{cx+r*Math.cos(theta-beta),cy+r*Math.sin(theta-beta)});
        return out;
    }
    /** Foot of the perpendicular from (sx,sy) on the segment, or null when it lies outside the segment. */
    public static double[] perpendicularFoot(double sx,double sy,double[] l) {
        double dx=l[2]-l[0],dy=l[3]-l[1],len=dx*dx+dy*dy;if(len<1e-12)return null;
        double t=((sx-l[0])*dx+(sy-l[1])*dy)/len;if(t<0||t>1)return null;
        return new double[]{l[0]+t*dx,l[1]+t*dy};
    }
    /** Intersections of two arcs (or full circles) lying on both. */
    static java.util.List<double[]> arcHits(SketchCurves.Arc a,SketchCurves.Arc b) {
        java.util.List<double[]> out=new java.util.ArrayList<>();
        for(double[] p:circleHits(a.x,a.y,a.r,b.x,b.y,b.r))if(a.contains(p[0],p[1])&&b.contains(p[0],p[1]))out.add(p);
        return out;
    }
    public static java.util.List<double[]> circleHits(double x1,double y1,double r1,double x2,double y2,double r2) {
        java.util.List<double[]> out=new java.util.ArrayList<>();double d=Math.hypot(x2-x1,y2-y1);
        if(d<1e-12||d>r1+r2+1e-12||d<Math.abs(r1-r2)-1e-12)return out;
        double a=(r1*r1-r2*r2+d*d)/(2*d),h=Math.sqrt(Math.max(0,r1*r1-a*a));
        double mx=x1+a*(x2-x1)/d,my=y1+a*(y2-y1)/d,ox=-h*(y2-y1)/d,oy=h*(x2-x1)/d;
        out.add(new double[]{mx+ox,my+oy});if(h>1e-12)out.add(new double[]{mx-ox,my-oy});
        return out;
    }
}
