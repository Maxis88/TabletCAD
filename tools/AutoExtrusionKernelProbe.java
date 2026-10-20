import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Automatic Połącz / Wytnij: outward extrusion partly overlapping a boss joins; a pocket pulled into the plate cuts. */
public final class AutoExtrusionKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static CadProject withTool(SketchPrimitive profile,double depth,boolean reverse)throws Exception {
        CadProject p=HistoryKernelProbe.plate();
        HistoryKernelProbe.add(p,ExtrusionPlan.feature(HistoryKernelProbe.top(),new SketchPrimitive("boss","Prostokąt",30,20,60,40),"add",10,0,false,false),"boss",1);
        p.committedFeatures.add(ExtrusionPlan.feature(HistoryKernelProbe.top(),profile,"new",depth,0,reverse,false));p.committedProfileIds.add(profile.id);p.featureBodyTargets.add(0);p.featureBodyOutputs.add(BodyGraph.allocate(p));
        return p;
    }
    public static void main(String[] args)throws Exception {
        CadProject a=withTool(new SketchPrimitive("t","Prostokąt",50,25,80,35),10,false);
        check(AutoExtrusion.resolve(a,1,1,null).equals("Połącz z bryłą"),"outward profile overlapping a third of the boss → Połącz");
        double v=HistoryKernelProbe.volume(a);check(Math.abs(v-(120000+6000+2000))<1e-6,"joined: plate + boss + the part outside the boss");
        CadProject b=withTool(new SketchPrimitive("p","Prostokąt",70,10,90,30),5,true);
        check(AutoExtrusion.resolve(b,1,1,null).equals("Wytnij"),"profile pulled into the plate → Wytnij");
        check(Math.abs(HistoryKernelProbe.volume(b)-(120000+6000-2000))<1e-6,"pocket 20×20×5 removed");
        System.out.println("PASS AutoExtrusionKernelProbe: "+checks+" checks");
    }
}
