package pl.tabletcad.app;

import java.util.List;
import pl.tabletcad.app.model.SketchPrimitive;

/** Geometric snapping in the active sketch's local millimetre coordinates. */
public final class SketchSnapper {
    private SketchSnapper() {}
    public static double[] snap(double x,double y,List<SketchPrimitive> geometry,
                                String excluded,double tolerance,Double startX,Double startY) {
        double best=tolerance;double[] result={x,y};
        java.util.ArrayList<double[]> points=new java.util.ArrayList<>();
        java.util.ArrayList<double[]> lines=new java.util.ArrayList<>();
        java.util.ArrayList<double[]> arcs=new java.util.ArrayList<>();
        points.add(new double[]{0,0});
        for(SketchPrimitive p:geometry) {
            if(p.id.equals(excluded)) continue;
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
            } else if(!p.type.equals("Okrąg")) lines.add(new double[]{p.x1,p.y1,p.x2,p.y2});
        }
        for(double[] l:lines) points.add(new double[]{(l[0]+l[2])/2,(l[1]+l[3])/2});
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
        for(double[] p:points) {
            double d=Math.hypot(x-p[0],y-p[1]);
            if(d<best) {best=d;result=p;}
        }
        if(best<tolerance) return result; // endpoints/intersections before projected positions
        for(double[] l:lines) {
            double dx=l[2]-l[0],dy=l[3]-l[1],length=dx*dx+dy*dy;
            if(length<1e-12) continue;
            double t=Math.max(0,Math.min(1,((x-l[0])*dx+(y-l[1])*dy)/length));
            double px=l[0]+t*dx,py=l[1]+t*dy,d=Math.hypot(x-px,y-py);
            if(d<best) {best=d;result=new double[]{px,py};}
        }
        if(best<tolerance) return result;
        for(double[] arc:arcs) {
            SketchCurves.Arc a=SketchCurves.arc(arc,0);double angle=Math.atan2(y-a.y,x-a.x),px=a.x+a.r*Math.cos(angle),py=a.y+a.r*Math.sin(angle),d=Math.hypot(x-px,y-py);
            if(d<best&&a.contains(px,py)){best=d;result=new double[]{px,py};}
        }
        if(best<tolerance)return result;
        // Horizontal/vertical inference relative to stroke start, then the origin axes.
        double referenceX=startX==null?0:startX,referenceY=startY==null?0:startY;
        if(Math.abs(x-referenceX)<tolerance) result[0]=referenceX;
        if(Math.abs(y-referenceY)<tolerance) result[1]=referenceY;
        return result;
    }
}
