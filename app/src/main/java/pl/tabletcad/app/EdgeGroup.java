package pl.tabletcad.app;

import java.util.*;

/** One simultaneous OCCT edge treatment; each member retains its own exact/root reference. */
public final class EdgeGroup {
    private EdgeGroup() {}
    public static boolean is(double[] f){return f!=null&&f.length>=16&&(f[0]==-6||f[0]==-7);}
    public static boolean chamfer(double[] f){return is(f)&&f[0]==-7;}
    public static double[] create(List<double[]> edges,double radius,boolean chamfer) {
        if(edges==null||edges.size()<1||edges.size()>256)throw new IllegalArgumentException("Wybierz od 1 do 256 krawędzi");
        int length=16;for(double[] edge:edges)length+=1+edge.length;
        double[] result=new double[length];result[0]=chamfer?-7:-6;result[1]=edges.size();result[11]=radius;int offset=16;
        for(double[] edge:edges){result[offset++]=edge.length;System.arraycopy(edge,0,result,offset,edge.length);result[offset+11]=radius;offset+=edge.length;}
        validate(result);return result;
    }
    public static List<double[]> members(double[] f) {
        if(!is(f)||f[1]!=Math.floor(f[1])||f[1]<1||f[1]>256)throw new IllegalArgumentException("Niepoprawna grupa krawędzi");
        List<double[]> result=new ArrayList<>();int offset=16;
        for(int i=0;i<(int)f[1];i++) {
            if(offset>=f.length)throw new IllegalArgumentException("Niekompletna grupa krawędzi");
            double size=f[offset++];if(size!=16&&size!=24&&size!=39||offset+size>f.length)throw new IllegalArgumentException("Niepoprawne odniesienie grupy");
            result.add(Arrays.copyOfRange(f,offset,offset+(int)size));offset+=(int)size;
        }
        if(offset!=f.length)throw new IllegalArgumentException("Nadmiarowe dane grupy krawędzi");return result;
    }
    private static boolean same(double[] a,double[] b) {
        boolean forward=true,backward=true;
        for(int i=0;i<9;i++){forward&=Math.abs(a[2+i]-b[2+i])<=1e-5;backward&=Math.abs(a[2+i]-b[2+(2-i/3)*3+i%3])<=1e-5;}
        return forward||backward;
    }
    public static void validate(double[] f) {
        List<double[]> members=members(f);
        for(double value:f)if(!Double.isFinite(value))throw new IllegalArgumentException("Nieskończona wartość grupy");
        if(f[11]<.1||f[11]>1000)throw new IllegalArgumentException("Wymiar krawędzi: 0,1–1000 mm");
        for(int i=2;i<16;i++)if(i!=11&&f[i]!=0)throw new IllegalArgumentException("Niepoprawne flagi grupy krawędzi");
        for(int i=0;i<members.size();i++) {
            double[] edge=members.get(i);if(is(edge))throw new IllegalArgumentException("Zagnieżdżona grupa krawędzi");
            if(chamfer(f))EdgeChamfer.validate(edge);else EdgeFillet.validate(edge);
            if(edge[11]!=f[11])throw new IllegalArgumentException("Niezgodne wymiary w grupie");
            for(int j=0;j<i;j++)if(same(edge,members.get(j)))throw new IllegalArgumentException("Krawędź występuje w grupie dwukrotnie");
        }
    }
    public static double[] withRadius(double[] feature,double radius) {
        if(!is(feature)){double[] result=feature.clone();result[11]=radius;return result;}
        return create(members(feature),radius,chamfer(feature));
    }
}
