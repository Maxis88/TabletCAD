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
        // Face offset of the boss top (z=30) dragged: the user's slow case.
        HistoryKernelProbe.add(p,FaceOffset.create(5,new double[]{90,55,20},new double[]{0,0,1},-1),"",1);
        f=EdgeRepairKernelProbe.packed(p);long a0=System.nanoTime();double[] fm=NativeCadEngine.evaluateModel(p.width,p.height,p.depth,0,p.featureBase,f,null);System.out.println("face offset first ms="+(System.nanoTime()-a0)/1e6+" ok="+(fm.length>0)+" "+(fm.length==0?NativeCadEngine.lastFailure().getMessage():""));
        for(int k=1;k<=4;k++){p.committedFeatures.set(7,FaceOffset.create(5+k,new double[]{90,55,20},new double[]{0,0,1},-1));f=EdgeRepairKernelProbe.packed(p);
            long a1=System.nanoTime();double[] m2=NativeCadEngine.evaluateModel(p.width,p.height,p.depth,0,p.featureBase,f,null);System.out.println("face offset drag ms="+(System.nanoTime()-a1)/1e6+" ok="+(m2.length>0));}
        // Same drag with an M10 thread earlier in the history (threads are heavy to tessellate).
        CadProject t=HistoryKernelProbe.plate();
        HistoryKernelProbe.add(t,ExtrusionPlan.feature(HistoryKernelProbe.top(),new SketchPrimitive("h","Okrąg",30,30,34.25,30),"cut",20,0,false,false),"h",1);
        CadModelResult holeModel=new CadModelResult(NativeCadEngine.evaluateModel(100,60,20,0,t.featureBase,EdgeRepairKernelProbe.packed(t),null));
        HoleThread.Preset m10=null;for(HoleThread.Preset pr:HoleThread.nearest(8.5))if(pr.diameter==10&&pr.pitch==1.5)m10=pr;
        HistoryKernelProbe.add(t,HoleThread.create(holeModel.holes.get(0),m10,15,true,false),"",1);
        HistoryKernelProbe.add(t,FaceOffset.create(5,new double[]{90,55,20},new double[]{0,0,1},-1),"",1);
        double[] tf=EdgeRepairKernelProbe.packed(t);long t0b=System.nanoTime();double[] tm=NativeCadEngine.evaluateModel(100,60,20,0,t.featureBase,tf,null);System.out.println("thread model first ms="+(System.nanoTime()-t0b)/1e6+" tris="+tm.length/13);
        for(int k=1;k<=3;k++){t.committedFeatures.set(2,FaceOffset.create(5+k,new double[]{90,55,20},new double[]{0,0,1},-1));tf=EdgeRepairKernelProbe.packed(t);
            long a2=System.nanoTime();NativeCadEngine.evaluateModel(100,60,20,0,t.featureBase,tf,null);System.out.println("thread model face offset drag ms="+(System.nanoTime()-a2)/1e6);}
        long a=System.nanoTime();double[] props=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,f,null);System.out.println("props ms="+(System.nanoTime()-a)/1e6);
    }
}
