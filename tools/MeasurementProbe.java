import java.util.*;
import pl.tabletcad.app.*;

public final class MeasurementProbe {
    static void require(boolean v,String s){if(!v)throw new AssertionError(s);System.out.println("PASS "+s);}
    static void close(double a,double b,String s){require(Math.abs(a-b)<1e-6,s+" "+a+" / "+b);}
    static double[] info(double[] base,double[] features,String source){return NativeCadEngine.edgeMeasurements(40,30,20,0,base,features,source);}
    public static void main(String[] args) {
        double[] box=info(null,new double[0],null),segments=NativeCadEngine.modelEdges(40,30,20,0,null,new double[0],null);
        require(box.length==12*16,"twelve exact B-Rep property records");
        double total=0;Set<Double> ids=new HashSet<>();
        for(int i=0;i<box.length;i+=16) {
            require(box[i+14]==0&&box[i+10]==0,"box edge classified as line without spurious radius");
            close(CadMeasurements.distance(Arrays.copyOfRange(box,i,i+3),Arrays.copyOfRange(box,i+6,i+9)),box[i+9],"straight length equals endpoint distance");
            total+=box[i+9];ids.add(box[i+15]);
        }
        close(total,360,"total unique box edge length");
        for(int i=0;i<segments.length;i+=16)require(CadMeasurements.edge(box,segments[i+15])!=null,"render segment resolves same B-Rep edge properties");
        for(int plane=0;plane<3;plane++) {
            double[] base={1,2,3,9.5,3,plane,12},circle=info(base,new double[0],null);int circles=0,lines=0;
            for(int i=0;i<circle.length;i+=16) {
                if(circle[i+14]==1){circles++;close(circle[i+10],7.5,"circle radius in plane "+plane);close(circle[i+9],15*Math.PI,"analytic circumference, not display polygon");}
                else {lines++;close(circle[i+9],12,"cylinder seam length");}
            }
            require(circles==2&&lines==1,"two circular rims and one seam on each plane");
            double[] pickSegments=NativeCadEngine.modelEdges(40,30,20,0,base,new double[0],null);
            require(pickSegments.length==2*128*16,"periodic cylinder seam excluded from edge picking");
        }
        double[] base={1,2,3,9.5,3,0,12};
        require(NativeCadEngine.evaluateFeatures(40,30,20,0,base,new double[0],args[0]).length>0,"measurement fixture STEP export");
        double[] imported=info(null,new double[0],args[0]);int rims=0;
        for(int i=0;i<imported.length;i+=16)if(imported[i+14]==1){rims++;close(imported[i+10],7.5,"imported STEP exact radius");close(imported[i+9],15*Math.PI,"imported STEP exact circumference");}
        require(rims==2,"STEP circle properties preserved without source sketch");
        double[] fillet=null;
        for(int i=0;i<segments.length;i+=16)if(segments[i]==0&&segments[i+1]==0&&segments[i+6]==0&&segments[i+7]==0) {fillet=EdgeFillet.create(segments,i,3);break;}
        require(fillet!=null,"selected vertical edge for rounded fixture");
        double[] rounded=info(null,fillet,null);int arcs=0;
        for(int i=0;i<rounded.length;i+=16)if(rounded[i+14]==1){arcs++;close(rounded[i+10],3,"fillet arc true radius");close(rounded[i+9],1.5*Math.PI,"quarter arc analytic length");}
        require(arcs==2,"two measured quarter-circle fillet edges");
        close(CadMeasurements.distance(new double[]{-10,3,2},new double[]{2,12,2}),15,"3D point-to-point independent of camera");
        require(CadMeasurements.edge(box,999)==null,"unknown edge never reports unrelated values");
        boolean rejected=false;try{CadMeasurements.edge(new double[]{1},1);}catch(IllegalArgumentException e){rejected=true;}require(rejected,"malformed property data rejected");
        System.out.println("ALL EXACT MEASUREMENT TESTS PASSED");
    }
}
