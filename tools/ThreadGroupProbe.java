import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Threads added together to several holes (one operation each, one commit). */
public final class ThreadGroupProbe {
    static int checks;
    static void check(boolean ok,String t){if(!ok)throw new AssertionError(t);checks++;System.out.println("PASS "+t);}
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] r=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,r,at,f.length);at+=f.length;}return r;}
    static CadModelResult eval(CadProject p){double[] r=NativeCadEngine.evaluateModel(p.width,p.height,p.depth,0,p.featureBase,BodyGraph.pack(p,packed(p)),null);if(r.length==0)throw new AssertionError("eval: "+NativeCadEngine.lastFailure().getMessage());return new CadModelResult(r);}
    public static void main(String[] a)throws Exception{for(String v:new String[]{"plain","pattern","hole","bodies","threaded"})run(v);System.out.println("PASS ThreadGroupProbe: "+checks+" checks");}
    static void run(String v)throws Exception{
        CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=20;p.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(p);
        CadProject top=new CadProject();top.sketchOriginZ=20;
        for(double x:new double[]{20,50,80}){p.committedFeatures.add(ExtrusionPlan.feature(top,new SketchPrimitive("h"+x,"Okrąg",x,30,x+2.5,30),"cut",20,0,false,false));p.committedProfileIds.add("h"+x);p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);}
        if(v.equals("pattern")){p.committedFeatures.add(FeaturePattern.create(FeaturePattern.LINEAR,new int[]{0},new double[]{0,0,0},new double[]{0,1,0},2,15));p.committedProfileIds.add("");p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);}
        if(v.equals("hole")){for(double x:new double[]{30,65}){p.committedFeatures.add(HoleFeature.create(new double[]{x,45,20},new double[]{0,0,1},-1,5,true,0,HoleFeature.SIMPLE,0,0,118));p.committedProfileIds.add("");p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);}}
        if(v.equals("bodies")){p.committedFeatures.add(ExtrusionPlan.feature(new CadProject(),new SketchPrimitive("b","Prostokąt",200,0,240,40),"new",20,0,false,false));p.committedProfileIds.add("b");p.featureBodyTargets.add(0);int nb=BodyGraph.allocate(p);p.featureBodyOutputs.add(nb);
            p.committedFeatures.add(ExtrusionPlan.feature(top,new SketchPrimitive("h2","Okrąg",220,20,222.5,20),"cut",20,0,false,false));p.committedProfileIds.add("h2");p.featureBodyTargets.add(nb);p.featureBodyOutputs.add(nb);}
        if(v.equals("threaded")){CadModelResult r0=eval(p);HoleThread.Cylinder h0=r0.holes.get(0);p.committedFeatures.add(HoleThread.create(h0,HoleThread.nearest(h0.radius*2).get(0),20,true,false));p.committedProfileIds.add("");p.featureBodyTargets.add(h0.body);p.featureBodyOutputs.add(h0.body);}
        CadModelResult r=eval(p);        List<HoleThread.Cylinder> free=new ArrayList<>();for(HoleThread.Cylinder h:r.holes)if(!HoleThread.threaded(h,p.committedFeatures))free.add(h);
        System.out.println("INFO "+v+": cylinders "+r.holes.size()+" unique "+HoleThread.unique(r.holes).size());
        if(v.equals("threaded"))check(free.size()==2,"crest strips of the threaded hole are not offered for another thread ("+free.size()+")");
        for(boolean fromMax:new boolean[]{true,false})for(double depth:new double[]{20,8}){
        CadProject next=CadProject.fromJson(new org.json.JSONObject(p.toJson().toString()));BodyGraph.begin(next);
        for(HoleThread.Cylinder h:free){next.committedFeatures.add(HoleThread.create(h,HoleThread.nearest(h.radius*2).get(0),depth,fromMax,false));next.committedProfileIds.add("");next.featureBodyTargets.add(h.body);next.featureBodyOutputs.add(h.body);}
        BodyGraph.reconcile(next);eval(next);check(true,v+": threads on all free holes in one commit, fromMax="+fromMax+" depth="+depth);}
    }
}
