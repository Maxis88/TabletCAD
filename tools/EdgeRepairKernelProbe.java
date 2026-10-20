import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** 0.98 edge naming: a fillet on an edge of a later feature survives an edit of that feature. */
public final class EdgeRepairKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static void close(String what,double actual,double expected,double tolerance){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(what+": "+actual+" != "+expected);checks++;System.out.println("PASS "+what+" = "+actual);}
    static double[] packed(CadProject p){BodyGraph.validate(p);return BodyGraph.pack(p,HistoryKernelProbe.raw(p));}
    static EdgeRepair.Model model(CadProject source,int operations) {
        CadProject p;try{p=CadProject.fromJson(source.toJson());}catch(Exception e){throw new AssertionError(e);}
        FeatureRemoval.truncate(p,operations);
        double[] f=packed(p),packedModel=NativeCadEngine.evaluateModel(p.width,p.height,p.depth,0,p.featureBase,f,null);
        if(packedModel.length==0)return null;
        CadModelResult r=new CadModelResult(packedModel);
        return new EdgeRepair.Model(r.mesh,r.features,NativeCadEngine.modelEdges(p.width,p.height,p.depth,0,p.featureBase,f,null));
    }
    static double volume(CadProject p){double[] r=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,packed(p),null);return r.length==7?r[0]:Double.NaN;}
    static int edgeAt(double[] edges,double x,double y) {
        for(int i=0;i+15<edges.length;i+=16){double ax=edges[i],ay=edges[i+1],bx=edges[i+6],by=edges[i+7];
            if(Math.abs(ax-x)<1e-6&&Math.abs(ay-y)<1e-6&&Math.abs(bx-x)<1e-6&&Math.abs(by-y)<1e-6&&Math.abs(edges[i+2]-edges[i+8])>5)return i;}
        throw new AssertionError("no vertical edge at "+x+","+y);
    }
    public static void main(String[] args)throws Exception {
        CadProject p=HistoryKernelProbe.plate();
        HistoryKernelProbe.add(p,ExtrusionPlan.feature(HistoryKernelProbe.top(),new SketchPrimitive("boss","Prostokąt",30,20,60,40),"add",10,0,false,false),"boss",1);
        double[] edges=NativeCadEngine.modelEdges(100,60,20,0,p.featureBase,packed(p),null);
        HistoryKernelProbe.add(p,EdgeFillet.create(edges,edgeAt(edges,60,40),3),"",1);
        double corner=9*(1-Math.PI/4)*10;
        close("R3 on the vertical boss edge",volume(p),120000+6000-corner,1e-6);
        // Move and widen the boss: the filleted corner is now at (70,45).
        CadProject before=CadProject.fromJson(p.toJson());
        CadProject after=CadProject.fromJson(p.toJson());
        after.committedFeatures.set(0,ExtrusionPlan.feature(HistoryKernelProbe.top(),new SketchPrimitive("boss","Prostokąt",40,25,70,45),"add",10,0,false,false));
        check(Double.isNaN(volume(after)),"without repair the moved edge is lost (kernel reports it)");
        int repaired=EdgeRepair.repair(before,after,EdgeRepairKernelProbe::model);
        check(repaired==1,"one edge re-attached");
        close("after the edit R3 is on the same corner of the moved boss",volume(after),120000+6000-corner,1e-6);
        double[] fillet=after.committedFeatures.get(1);
        check(Math.abs(fillet[2]-70)<1e-6&&Math.abs(fillet[3]-45)<1e-6,"fillet edge now at the corner (70,45)");
        // A change that removes the corner faces cannot be repaired (no guessing).
        CadProject round=CadProject.fromJson(p.toJson());
        round.committedFeatures.set(0,ExtrusionPlan.feature(HistoryKernelProbe.top(),new SketchPrimitive("boss","Okrąg",45,30,55,30),"add",10,0,false,false));
        check(EdgeRepair.repair(before,round,EdgeRepairKernelProbe::model)==0&&Double.isNaN(volume(round)),"a round boss has no such corner: nothing is guessed");
        // Unchanged history: nothing to do.
        check(EdgeRepair.repair(before,CadProject.fromJson(p.toJson()),EdgeRepairKernelProbe::model)==0,"unchanged document needs no repair");
        System.out.println("PASS EdgeRepairKernelProbe: "+checks+" checks");
    }
}
