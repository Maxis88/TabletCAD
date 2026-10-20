import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Timing of a larger history (40 operations): full rebuild, edit of the last and of an early operation. */
public final class LargeModelSpeedProbe {
    static double ms(long a){return (System.nanoTime()-a)/1e6;}
    public static void main(String[] args)throws Exception {
        CadProject p=HistoryKernelProbe.plate();p.width=400;p.height=300;p.featureBase=new double[]{0,0,0,400,300,0,20};
        for(int i=0;i<20;i++){double x=20+(i%10)*38,y=40+(i/10)*120;
            HistoryKernelProbe.add(p,ExtrusionPlan.feature(HistoryKernelProbe.top(),new SketchPrimitive("b"+i,"Prostokąt",x,y,x+20,y+30),"add",8,0,false,false),"b"+i,1);
            HistoryKernelProbe.add(p,ExtrusionPlan.feature(HistoryKernelProbe.top(),new SketchPrimitive("h"+i,"Okrąg",x+10,y+70,x+13,y+70),"cut",10,0,false,false),"h"+i,1);}
        double[] f=EdgeRepairKernelProbe.packed(p);
        long a=System.nanoTime();double[] m=NativeCadEngine.evaluateModel(p.width,p.height,p.depth,0,p.featureBase,f,null);System.out.println("40 ops full ms="+ms(a)+" ok="+(m.length>0));
        a=System.nanoTime();CadModelResult r=new CadModelResult(m);System.out.println("java parse ms="+ms(a)+" tris="+r.mesh.length/13);
        p.committedFeatures.set(39,ExtrusionPlan.feature(HistoryKernelProbe.top(),new SketchPrimitive("h19","Okrąg",20+9*38+10,160+70,20+9*38+14,160+70),"cut",10,0,false,false));
        f=EdgeRepairKernelProbe.packed(p);a=System.nanoTime();NativeCadEngine.evaluateModel(p.width,p.height,p.depth,0,p.featureBase,f,null);System.out.println("edit last ms="+ms(a));
        p.committedFeatures.set(0,ExtrusionPlan.feature(HistoryKernelProbe.top(),new SketchPrimitive("b0","Prostokąt",22,40,42,70),"add",8,0,false,false));
        f=EdgeRepairKernelProbe.packed(p);a=System.nanoTime();NativeCadEngine.evaluateModel(p.width,p.height,p.depth,0,p.featureBase,f,null);System.out.println("edit first ms="+ms(a));
        a=System.nanoTime();NativeCadEngine.modelEdges(p.width,p.height,p.depth,0,p.featureBase,f,null);System.out.println("edges ms="+ms(a));
    }
}
