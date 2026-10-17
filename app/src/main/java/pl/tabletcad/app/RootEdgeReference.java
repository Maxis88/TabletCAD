package pl.tabletcad.app;

import java.util.*;

/** An edge tied to the generator parameters of a supported root feature, not a proximity guess. */
public final class RootEdgeReference {
    private RootEdgeReference() {}
    public static boolean is(double[] f){return f!=null&&f.length>=24&&(f[0]==-4||f[0]==-5);}
    public static boolean supports(double[] base) {
        if(base==null)return false;
        try{validateBase(base);return true;}catch(IllegalArgumentException e){return false;}
    }
    private static void validateBase(double[] base) {
        for(double value:base)if(!Double.isFinite(value))throw new IllegalArgumentException("Niepoprawne źródło odniesienia krawędzi");
        if(base.length==22&&Revolve.is(base)&&base[20]==0&&base[1]==0){Revolve.validate(base);return;}
        if(base.length!=7||(base[0]!=0&&base[0]!=1))throw new IllegalArgumentException("Nieobsługiwana bryła źródłowa odniesienia");
        FeatureDirection.basePlane(base[5]);
        if(Math.abs(base[6])<1e-6)throw new IllegalArgumentException("Zerowa długość źródłowego wyciągnięcia");
        if(base[0]==0?(Math.abs(base[3]-base[1])<1e-6||Math.abs(base[4]-base[2])<1e-6):Math.hypot(base[3]-base[1],base[4]-base[2])<1e-6)
            throw new IllegalArgumentException("Zerowy profil źródłowy");
    }
    public static void validate(double[] f) {
        if(!is(f)||(f[16]!=7&&f[16]!=22)||f.length!=17+(int)f[16])throw new IllegalArgumentException("Niepoprawny zapis odniesienia krawędzi");
        validateBase(Arrays.copyOfRange(f,17,f.length));
    }
    private static boolean matches(double[] feature,double[] edges,int offset) {
        boolean forward=true,reverse=true;
        for(int i=0;i<9;i++) {
            forward&=Math.abs(feature[2+i]-edges[offset+i])<=1e-5;
            reverse&=Math.abs(feature[2+i]-edges[offset+(2-i/3)*3+i%3])<=1e-5;
        }
        return forward||reverse;
    }
    public static double[] anchor(double[] operation,double[] base,double[] rootEdges) {
        if(operation==null||operation.length!=16||(operation[0]!=-1&&operation[0]!=-2))throw new IllegalArgumentException("Niepoprawna operacja krawędzi");
        if(!supports(base)||rootEdges==null)return operation.clone();
        Set<Double> matches=new HashSet<>();
        for(int i=0;i+15<rootEdges.length;i+=16)if(matches(operation,rootEdges,i))matches.add(rootEdges[i+15]);
        if(matches.size()!=1)return operation.clone();
        double[] result=Arrays.copyOf(operation,17+base.length);result[0]=operation[0]==-1?-4:-5;result[16]=base.length;
        System.arraycopy(base,0,result,17,base.length);validate(result);return result;
    }
}
