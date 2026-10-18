import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Android app_process probe for exact B-Rep volumes. Compiled on host; execute only on isolated Android. */
public final class SketchDesignKernelProbe {
    static void near(double actual,double expected,String label){if(Math.abs(actual-expected)>Math.max(1e-5,Math.abs(expected)*1e-7))throw new AssertionError(label+": "+actual+" != "+expected);System.out.println("PASS "+label);}
    static double volume(double[] base,double[] features){double[] p=NativeCadEngine.bodyProperties(80,48,32,0,base,features,null);if(p.length!=7)throw new AssertionError("OCCT properties unavailable");return p[0];}
    public static void main(String[] args){try{run();}catch(Throwable error){error.printStackTrace();System.exit(1);}}
    static void run() {
        CadProject p=new CadProject();
        SketchPrimitive slot=SketchShapes.threePoint("slot","Szczelina",new double[]{20,24},new double[]{40,24},new double[]{20,27});double area=120+9*Math.PI;
        double[] base=ExtrusionPlan.base(p,slot,10,0,false,false);near(volume(base,new double[0]),area*10,"analytic slot extrusion volume");
        double[] rotated=ProfileDependencies.replace(base,SketchTransform.rotate(slot,37,30,24),true);near(volume(rotated,new double[0]),area*10,"rotated slot B-Rep preserves volume");
        double[] scaled=ProfileDependencies.replace(base,SketchTransform.scale(slot,2,30,24),true);near(volume(scaled,new double[0]),area*40,"scaled slot B-Rep has quadratic area scaling");
        SketchPrimitive circle=SketchShapes.threePoint("circle","Okrąg 3 pkt",new double[]{0,0},new double[]{10,0},new double[]{5,5});near(volume(ExtrusionPlan.base(p,circle,10,0,false,false),new double[0]),250*Math.PI,"three-point circle exact extrusion volume");
        p.sketchOriginZ=32;double[] cut=ExtrusionPlan.feature(p,slot,"cut",32,0,false,false);near(volume(null,cut),80*48*32-area*32,"slot through-cut volume");
        double[] edited=ProfileDependencies.replace(cut,SketchTransform.scale(slot,.5,30,24),false);near(volume(null,edited),80*48*32-area*8,"edited slot updates cut while retaining depth and frame");
        p.sketchPlane="Ściana 1";p.featureBase=ExtrusionPlan.base(p,slot,8,2,true,false);p.featureBaseProfileId=slot.id;
        ProfileDependencies.propagate(p,slot.id,SketchTransform.rotate(slot,23,30,24));near(volume(p.featureBase,new double[0]),area*10,"placed reverse two-direction extrusion keeps extents after profile edit");
        System.out.println("ALL SKETCH DESIGN KERNEL CHECKS PASSED");
    }
}
