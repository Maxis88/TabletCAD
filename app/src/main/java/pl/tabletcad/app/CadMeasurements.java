package pl.tabletcad.app;

/** Exact kernel properties, separate from display polylines and triangle edges. */
public final class CadMeasurements {
    private CadMeasurements() {}
    public static double[] edge(double[] properties,double id) {
        if(properties.length%16!=0)throw new IllegalArgumentException("Invalid edge properties");
        for(int i=0;i<properties.length;i+=16)if(properties[i+15]==id) {
            double[] result=java.util.Arrays.copyOfRange(properties,i,i+16);
            for(double v:result)if(!Double.isFinite(v))throw new IllegalArgumentException("Non-finite edge measurement");
            if(result[9]<0||result[10]<0)throw new IllegalArgumentException("Negative edge measurement");
            return result;
        }
        return null;
    }
    public static double distance(double[] a,double[] b) {
        return Math.sqrt(Math.pow(a[0]-b[0],2)+Math.pow(a[1]-b[1],2)+Math.pow(a[2]-b[2],2));
    }
}
