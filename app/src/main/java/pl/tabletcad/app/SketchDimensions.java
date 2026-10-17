package pl.tabletcad.app;

import pl.tabletcad.app.model.SketchPrimitive;

/** Exact dimensional constraints for independent lines, rectangles and circles. Not a general solver. */
public final class SketchDimensions {
    private SketchDimensions() {}
    public static double angle(SketchPrimitive p) {return Math.toDegrees(Math.atan2(p.y2-p.y1,p.x2-p.x1));}
    public static double length(SketchPrimitive p) {return Math.hypot(p.x2-p.x1,p.y2-p.y1);}
    public static void validate(SketchPrimitive p) {
        double[] d=p.driving;
        if(d==null)return;
        for(double coordinate:new double[]{p.x1,p.y1,p.x2,p.y2})if(!Double.isFinite(coordinate)||Math.abs(coordinate)>5000)
            throw new IllegalArgumentException("Niepoprawne współrzędne zwymiarowanego elementu");
        int size=p.type.equals("Okrąg")?1:2;
        if(p.vertices!=null||d.length!=size||!(p.type.equals("Okrąg")||p.type.equals("Prostokąt")
            ||p.type.equals("Linia")||p.type.equals("Pomocnicza"))) throw new IllegalArgumentException("Nieobsługiwane więzy elementu");
        for(double value:d)if(!Double.isFinite(value))throw new IllegalArgumentException("Niepoprawny wymiar");
        if(d[0]<.01||d[0]>10000 || (p.type.equals("Prostokąt")&&(d[1]<.01||d[1]>10000)))
            throw new IllegalArgumentException("Wymiar: 0,01–10000 mm");
        double actual=p.type.equals("Prostokąt")?Math.abs(p.x2-p.x1):length(p);
        if(Math.abs(actual-d[0])>1e-6)throw new IllegalArgumentException("Geometria nie spełnia zapisanego wymiaru");
        if(size==2) {
            double error=p.type.equals("Prostokąt")?Math.abs(p.y2-p.y1)-d[1]:Math.IEEEremainder(angle(p)-d[1],360);
            if(Math.abs(error)>1e-6)throw new IllegalArgumentException("Geometria nie spełnia zapisanego kierunku lub wysokości");
        }
    }
    public static SketchPrimitive apply(SketchPrimitive original,double first,double second,boolean lock) {
        if(original.vertices!=null)throw new IllegalArgumentException("Wymiary konturu wieloodcinkowego wymagają solvera więzów");
        if(!(original.type.equals("Prostokąt")||original.type.equals("Okrąg")||original.type.equals("Linia")||original.type.equals("Pomocnicza")))
            throw new IllegalArgumentException("Nieobsługiwany typ elementu do wymiarowania");
        if(!Double.isFinite(first)||!Double.isFinite(second)||first<.01||first>10000)
            throw new IllegalArgumentException("Wymiar: 0,01–10000 mm; kąt musi być skończony");
        double x2,y2;double[] driving;
        if(original.type.equals("Prostokąt")) {
            if(second<.01||second>10000)throw new IllegalArgumentException("Wysokość: 0,01–10000 mm");
            x2=original.x1+Math.copySign(first,original.x2-original.x1);
            y2=original.y1+Math.copySign(second,original.y2-original.y1);driving=new double[]{first,second};
        } else {
            double angle=original.type.equals("Okrąg")?angle(original):Math.IEEEremainder(second,360);
            x2=original.x1+first*Math.cos(Math.toRadians(angle));y2=original.y1+first*Math.sin(Math.toRadians(angle));
            driving=original.type.equals("Okrąg")?new double[]{first}:new double[]{first,angle};
        }
        for(double value:new double[]{original.x1,original.y1,x2,y2})if(!Double.isFinite(value)||Math.abs(value)>5000)
            throw new IllegalArgumentException("Wymiar wyprowadza geometrię poza ±5000 mm");
        return new SketchPrimitive(original.id,original.type,original.x1,original.y1,x2,y2,lock?driving:null);
    }
    public static SketchPrimitive unlocked(SketchPrimitive p) {return p.vertices!=null||p.curves!=null?p:new SketchPrimitive(p.id,p.type,p.x1,p.y1,p.x2,p.y2);}
    public static SketchPrimitive constrainDrag(SketchPrimitive original,SketchPrimitive candidate,int handle) {
        if(original.driving==null)return candidate;
        if(handle!=0 && !(original.type.equals("Okrąg")&&handle==1))return original;
        double dx=candidate.x1-original.x1,dy=candidate.y1-original.y1;
        return new SketchPrimitive(original.id,original.type,original.x1+dx,original.y1+dy,
            original.x2+dx,original.y2+dy,original.driving);
    }
}
