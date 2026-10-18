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
        public Cylinder(double[] data,int offset) {
            face=(int)data[offset]-1;body=(int)data[offset+1];radius=data[offset+2];origin=Arrays.copyOfRange(data,offset+3,offset+6);axis=Arrays.copyOfRange(data,offset+6,offset+9);min=data[offset+9];max=data[offset+10];
            if(face<0||body<1||radius<=0||max-min<.1)throw new IllegalArgumentException("Niepoprawny otwór walcowy");
        }
        public double[] end(boolean maximum){double t=maximum?max:min;return new double[]{origin[0]+axis[0]*t,origin[1]+axis[1]*t,origin[2]+axis[2]*t};}
    }
    private static final double[][] METRIC={{2,.4},{2.5,.45},{3,.5},{3.5,.6},{4,.7},{5,.8},{6,1},{7,1},{8,1.25},{8,1},{10,1.5},{10,1},{12,1.75},{12,1.5},{14,2},{16,2},{18,2.5},{20,2.5},{22,2.5},{24,3},{27,3},{30,3.5},{36,4},{42,4.5},{48,5}};
    public static List<Preset> nearest(double holeDiameter) {
        List<Preset> result=new ArrayList<>();for(double[] value:METRIC)if(value[0]>holeDiameter+.01&&(value[0]-holeDiameter)/2<value[1]*.78)result.add(new Preset(value[0],value[1]));
        result.sort(Comparator.comparingDouble(p->Math.abs(holeDiameter-(p.diameter-p.pitch))));if(result.size()>6)return new ArrayList<>(result.subList(0,6));return result;
    }
    public static boolean is(double[] f){return f!=null&&f.length==16&&f[0]==-8;}
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis gwintu");for(double value:f)if(!Double.isFinite(value))throw new IllegalArgumentException("Niepoprawne parametry gwintu");
        if((f[1]!=1&&f[1]!=-1)||f[8]<=0||f[9]<=f[8]||f[10]<.05||f[11]<.1||f[11]>5000||f[11]/f[10]>200||f[9]-f[8]>f[10]*.78)throw new IllegalArgumentException("Gwint: sprawdź średnicę, skok i głębokość (maks. 200 zwojów)");
        double n=f[5]*f[5]+f[6]*f[6]+f[7]*f[7];if(Math.abs(n-1)>1e-6)throw new IllegalArgumentException("Niepoprawna oś gwintu");if(f[12]!=0&&f[12]<f[11])throw new IllegalArgumentException("Gwint jest głębszy od źródłowego otworu");for(int i=13;i<16;i++)if(f[i]!=0)throw new IllegalArgumentException("Niepoprawne flagi gwintu");
    }
    public static double[] create(Cylinder hole,Preset preset,double depth,boolean fromMaximum,boolean left) {
        if(depth>hole.max-hole.min+1e-6)throw new IllegalArgumentException("Gwint jest głębszy od wybranego walcowego otworu");
        double[] origin=hole.end(fromMaximum);double sign=fromMaximum?-1:1;
        double[] f={-8,left?-1:1,origin[0],origin[1],origin[2],hole.axis[0]*sign,hole.axis[1]*sign,hole.axis[2]*sign,hole.radius,preset.diameter/2,preset.pitch,depth,hole.max-hole.min,0,0,0};validate(f);return f;
    }
}
