package pl.tabletcad.app;

import java.util.*;

/** Metric 60-degree V thread presets; actual swept B-Rep cutting operations. */
public final class HoleThread {
    private HoleThread() {}
    public static final class Preset {
        public final double diameter,pitch;
        Preset(double d,double p){diameter=d;pitch=p;}
        public String label(){return "M"+java.math.BigDecimal.valueOf(diameter).stripTrailingZeros().toPlainString()+" × "+java.math.BigDecimal.valueOf(pitch).stripTrailingZeros().toPlainString();}
    }
    public static final class Cylinder {
        public final int face,body;public final double radius,min,max;public final double[] origin,axis;
        /** Convex cylinder (shaft, pin): takes an external thread. */
        public final boolean external;
        public Cylinder(double[] data,int offset) {
            face=(int)data[offset]-1;body=(int)data[offset+1];radius=data[offset+2];origin=Arrays.copyOfRange(data,offset+3,offset+6);axis=Arrays.copyOfRange(data,offset+6,offset+9);min=data[offset+9];max=data[offset+10];external=data[offset+14]==1;
            if(face<0||body<1||radius<=0||max-min<.1)throw new IllegalArgumentException("Niepoprawny otwór walcowy");
        }
        Cylinder(Cylinder a,double min,double max){face=a.face;body=a.body;radius=a.radius;origin=a.origin;axis=a.axis;this.min=min;this.max=max;external=a.external;}
        public double[] end(boolean maximum){double t=maximum?max:min;return new double[]{origin[0]+axis[0]*t,origin[1]+axis[1]*t,origin[2]+axis[2]*t};}
    }
    /**
     * True when the cylinder belongs to a hole that already has a thread: after threading, the
     * kernel still reports the short crest strips of the original bore as cylinders, and a second
     * thread there would cut nothing.
     */
    public static boolean threaded(Cylinder c,List<double[]> features) {
        for(double[] f:features) {
            if(!is(f)||(f[13]==1)!=c.external||Math.abs((c.external?f[9]:f[8])-c.radius)>1e-4)continue;
            double[] d={f[5],f[6],f[7]};double dot=d[0]*c.axis[0]+d[1]*c.axis[1]+d[2]*c.axis[2];
            if(Math.abs(Math.abs(dot)-1)>1e-6)continue;
            double[] w={f[2]-c.origin[0],f[3]-c.origin[1],f[4]-c.origin[2]};double t=w[0]*c.axis[0]+w[1]*c.axis[1]+w[2]*c.axis[2];
            double off=Math.sqrt(Math.max(0,w[0]*w[0]+w[1]*w[1]+w[2]*w[2]-t*t));
            if(off<1e-4)return true;
        }
        return false;
    }
    /** Same bore: same body, radius and axis line (OCCT may split one hole into several cylinder faces). */
    public static boolean sameHole(Cylinder a,Cylinder b) {
        if(a.body!=b.body||a.external!=b.external||Math.abs(a.radius-b.radius)>1e-4)return false;
        double dot=a.axis[0]*b.axis[0]+a.axis[1]*b.axis[1]+a.axis[2]*b.axis[2];if(Math.abs(Math.abs(dot)-1)>1e-6)return false;
        double[] w={b.origin[0]-a.origin[0],b.origin[1]-a.origin[1],b.origin[2]-a.origin[2]};double t=w[0]*a.axis[0]+w[1]*a.axis[1]+w[2]*a.axis[2];
        return Math.sqrt(Math.max(0,w[0]*w[0]+w[1]*w[1]+w[2]*w[2]-t*t))<1e-4;
    }
    /** One cylinder per bore; faces of one bore are merged into their full axial range. */
    public static List<Cylinder> unique(List<Cylinder> list) {
        List<Cylinder> out=new ArrayList<>();
        for(Cylinder c:list){boolean merged=false;
            for(int i=0;i<out.size();i++){Cylinder o=out.get(i);if(!sameHole(o,c))continue;
                double[] w={c.origin[0]-o.origin[0],c.origin[1]-o.origin[1],c.origin[2]-o.origin[2]};double shift=w[0]*o.axis[0]+w[1]*o.axis[1]+w[2]*o.axis[2];
                double sign=Math.signum(c.axis[0]*o.axis[0]+c.axis[1]*o.axis[1]+c.axis[2]*o.axis[2]);double a=shift+sign*c.min,b=shift+sign*c.max;
                out.set(i,new Cylinder(o,Math.min(o.min,Math.min(a,b)),Math.max(o.max,Math.max(a,b))));merged=true;break;}
            if(!merged)out.add(c);}
        return out;
    }
    private static final double[][] METRIC={{2,.4},{2.5,.45},{3,.5},{3.5,.6},{4,.7},{5,.8},{6,1},{7,1},{8,1.25},{8,1},{10,1.5},{10,1},{12,1.75},{12,1.5},{14,2},{16,2},{18,2.5},{20,2.5},{22,2.5},{24,3},{27,3},{30,3.5},{36,4},{42,4.5},{48,5}};
    public static List<Preset> nearest(double holeDiameter) {
        List<Preset> result=new ArrayList<>();for(double[] value:METRIC)if(value[0]>holeDiameter+.01&&(value[0]-holeDiameter)/2<value[1]*.78)result.add(new Preset(value[0],value[1]));
        result.sort(Comparator.comparingDouble(p->Math.abs(holeDiameter-(p.diameter-p.pitch))));if(result.size()>6)return new ArrayList<>(result.subList(0,6));return result;
    }
    /** ISO metric external thread depth (d − d3) / 2 = 0.61343 · P. */
    public static final double EXTERNAL_DEPTH=0.61343;
    /** Presets for a shaft: nominal diameters close to the shaft (the cut keeps the shaft as major diameter). */
    public static List<Preset> nearestExternal(double shaftDiameter) {
        List<Preset> result=new ArrayList<>();for(double[] value:METRIC)if(Math.abs(value[0]-shaftDiameter)<=Math.max(.6,shaftDiameter*.06)&&value[1]*EXTERNAL_DEPTH<shaftDiameter/2*.8)result.add(new Preset(value[0],value[1]));
        result.sort(Comparator.comparingDouble((Preset p)->Math.abs(shaftDiameter-p.diameter)).thenComparing(p->-p.pitch));if(result.size()>6)return new ArrayList<>(result.subList(0,6));return result;
    }
    public static List<Preset> presets(Cylinder c){return c.external?nearestExternal(c.radius*2):nearest(c.radius*2);}
    public static boolean external(double[] f){return is(f)&&f[13]==1;}
    public static boolean is(double[] f){return f!=null&&f.length==16&&f[0]==-8;}
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis gwintu");for(double value:f)if(!Double.isFinite(value))throw new IllegalArgumentException("Niepoprawne parametry gwintu");
        if((f[1]!=1&&f[1]!=-1)||f[8]<=0||f[9]<=f[8]||f[10]<.05||f[11]<.1||f[11]>5000||f[11]/f[10]>200||f[9]-f[8]>f[10]*.78)throw new IllegalArgumentException("Gwint: sprawdź średnicę, skok i głębokość (maks. 200 zwojów)");
        double n=f[5]*f[5]+f[6]*f[6]+f[7]*f[7];if(Math.abs(n-1)>1e-6)throw new IllegalArgumentException("Niepoprawna oś gwintu");if(f[12]!=0&&f[12]<f[11])throw new IllegalArgumentException("Gwint jest głębszy od źródłowego otworu");if((f[13]!=0&&f[13]!=1)||f[14]!=0||f[15]!=0)throw new IllegalArgumentException("Niepoprawne flagi gwintu");
    }
    public static double[] create(Cylinder hole,Preset preset,double depth,boolean fromMaximum,boolean left) {
        if(depth>hole.max-hole.min+1e-6)throw new IllegalArgumentException("Gwint jest głębszy od wybranego walcowego otworu");
        double[] origin=hole.end(fromMaximum);double sign=fromMaximum?-1:1;
        double minor=hole.external?hole.radius-EXTERNAL_DEPTH*preset.pitch:hole.radius,major=hole.external?hole.radius:preset.diameter/2;
        if(hole.external&&minor<=hole.radius*.2)throw new IllegalArgumentException("Wałek jest zbyt cienki dla tego skoku gwintu");
        double[] f={-8,left?-1:1,origin[0],origin[1],origin[2],hole.axis[0]*sign,hole.axis[1]*sign,hole.axis[2]*sign,minor,major,preset.pitch,depth,hole.max-hole.min,hole.external?1:0,0,0};validate(f);return f;
    }
}
