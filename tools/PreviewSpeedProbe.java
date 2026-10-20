import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Timing of the preview path (mode 8) while the last operation's depth is dragged. */
public final class PreviewSpeedProbe {
    public static void main(String[] args)throws Exception {
        CadProject p=HistoryKernelProbe.plate();
        for(int i=0;i<6;i++)HistoryKernelProbe.add(p,ExtrusionPlan.feature(HistoryKernelProbe.top(),new SketchPrimitive("b"+i,"Okrąg",15+i*14,30,20+i*14,30),"cut",5,0,false,false),"b"+i,1);
        HistoryKernelProbe.add(p,ExtrusionPlan.feature(HistoryKernelProbe.top(),new SketchPrimitive("boss","Prostokąt",30,20,60,40),"add",10,0,false,false),"boss",1);
        double[] f=EdgeRepairKernelProbe.packed(p);
        long t0=System.nanoTime();double[] m=NativeCadEngine.evaluateModel(p.width,p.height,p.depth,0,p.featureBase,f,null);long t1=System.nanoTime();
        System.out.println("first full model ms="+(t1-t0)/1e6+" len="+m.length);
        for(int k=1;k<=5;k++){
            p.committedFeatures.set(6,ExtrusionPlan.feature(HistoryKernelProbe.top(),new SketchPrimitive("boss","Prostokąt",30,20,60,40),"add",10+k,0,false,false));
            f=EdgeRepairKernelProbe.packed(p);long a=System.nanoTime();NativeCadEngine.evaluateModel(p.width,p.height,p.depth,0,p.featureBase,f,null);long b=System.nanoTime();
            System.out.println("drag step ms="+(b-a)/1e6);
        }
        long a=System.nanoTime();double[] props=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,f,null);System.out.println("props ms="+(System.nanoTime()-a)/1e6);
    }
}
