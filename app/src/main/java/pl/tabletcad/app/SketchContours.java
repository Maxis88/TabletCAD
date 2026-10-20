package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.SketchPrimitive;

/** Joins a single unbranched closed chain without changing its shape. */
public final class SketchContours {
    private SketchContours() {}
    private static boolean near(double x,double y,double u,double v) {return Math.hypot(x-u,y-v)<=.01;}
    public static final class Result {
        public final SketchPrimitive profile;
        public final Set<String> consumed;
        Result(SketchPrimitive p,Set<String> ids) {profile=p;consumed=ids;}
    }
    public static Result join(List<SketchPrimitive> geometry,String selected) {
        Result curved=SketchCurves.join(geometry,selected);if(curved!=null)return curved;
        List<SketchPrimitive> lines=new ArrayList<>();
        for(SketchPrimitive p:geometry) if(!p.construction&&p.type.equals("Linia")) lines.add(p);
        if(lines.size()<3) throw new IllegalArgumentException("Narysuj co najmniej 3 linie zamkniętego konturu");
        SketchPrimitive first=lines.get(0);
        for(SketchPrimitive p:lines) if(p.id.equals(selected)) first=p;
        Set<String> consumed=new HashSet<>();List<Double> points=new ArrayList<>();
        points.add(first.x1);points.add(first.y1);consumed.add(first.id);
        double x=first.x2,y=first.y2;
        while(!near(x,y,first.x1,first.y1)) {
            points.add(x);points.add(y);
            if(points.size()>512) throw new IllegalArgumentException("Kontur przekracza 256 wierzchołków");
            SketchPrimitive next=null;int degree=0;
            for(SketchPrimitive p:lines) {
                if(near(x,y,p.x1,p.y1)||near(x,y,p.x2,p.y2)) {
                    degree++;
                    if(!consumed.contains(p.id)) {
                        if(next!=null) throw new IllegalArgumentException("Rozgałęziony kontur — usuń nadmiarowe linie");
                        next=p;
                    }
                }
            }
            if(next==null||degree!=2) throw new IllegalArgumentException("Kontur nie jest zamknięty albo ma rozgałęzienie");
            consumed.add(next.id);
            if(near(x,y,next.x1,next.y1)) {x=next.x2;y=next.y2;} else {x=next.x1;y=next.y1;}
        }
        int startDegree=0;
        for(SketchPrimitive p:lines) if(near(first.x1,first.y1,p.x1,p.y1)||near(first.x1,first.y1,p.x2,p.y2)) startDegree++;
        if(startDegree!=2) throw new IllegalArgumentException("Rozgałęzienie w początku konturu");
        double[] values=new double[points.size()];for(int i=0;i<values.length;i++) values[i]=points.get(i);
        for(SketchPrimitive p:lines)if(consumed.contains(p.id)&&p.driving!=null)
            throw new IllegalArgumentException("Odblokuj wymiary linii przed połączeniem w kontur");
        validate(values);
        return new Result(new SketchPrimitive(first.id,values),consumed);
    }
    private static double cross(double ax,double ay,double bx,double by,double cx,double cy) {
        return (bx-ax)*(cy-ay)-(by-ay)*(cx-ax);
    }
    /** Signed shoelace area of a closed point list (x,y pairs; the closing edge is implied). */
    public static double area(double[] v){double a=0;int n=v.length/2;for(int i=0;i<n;i++){int j=(i+1)%n;a+=v[2*i]*v[2*j+1]-v[2*j]*v[2*i+1];}return a/2;}
    public static void validate(double[] v) {
        if(v.length<6||v.length>512||v.length%2!=0) throw new IllegalArgumentException("Kontur wymaga 3–256 punktów");
        int n=v.length/2;double area=0;
        for(int i=0;i<n;i++) {
            int a=i*2,b=((i+1)%n)*2;
            if(!Double.isFinite(v[a])||!Double.isFinite(v[a+1])||Math.abs(v[a])>5000||Math.abs(v[a+1])>5000
                ||Math.hypot(v[a]-v[b],v[a+1]-v[b+1])<.01) throw new IllegalArgumentException("Niepoprawny lub zerowy odcinek konturu");
            area+=v[a]*v[b+1]-v[b]*v[a+1];
            for(int j=i+1;j<n;j++) {
                if(j==i+1||(i==0&&j==n-1)) continue;
                int c=j*2,d=((j+1)%n)*2;
                if(Math.max(Math.min(v[a],v[b]),Math.min(v[c],v[d]))>Math.min(Math.max(v[a],v[b]),Math.max(v[c],v[d]))+1e-8
                    ||Math.max(Math.min(v[a+1],v[b+1]),Math.min(v[c+1],v[d+1]))>Math.min(Math.max(v[a+1],v[b+1]),Math.max(v[c+1],v[d+1]))+1e-8) continue;
                double p=cross(v[a],v[a+1],v[b],v[b+1],v[c],v[c+1]),q=cross(v[a],v[a+1],v[b],v[b+1],v[d],v[d+1]);
                double r=cross(v[c],v[c+1],v[d],v[d+1],v[a],v[a+1]),s=cross(v[c],v[c+1],v[d],v[d+1],v[b],v[b+1]);
                if(p*q<=1e-12&&r*s<=1e-12) throw new IllegalArgumentException("Kontur przecina lub dotyka sam siebie");
            }
        }
        if(Math.abs(area)<1e-4) throw new IllegalArgumentException("Kontur ma zerowe pole");
    }
}
