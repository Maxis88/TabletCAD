import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** 0.76 history management: suppression, reordering and rollback marker, evaluated by OCCT. */
public final class HistoryKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static void close(String what,double actual,double expected,double tolerance){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(what+": "+actual+" != "+expected);checks++;System.out.println("PASS "+what+" = "+actual);}
    static void rejects(Runnable r,String label){try{r.run();}catch(IllegalArgumentException e){check(true,label+" → "+e.getMessage());return;}throw new AssertionError(label);}
    static CadProject plate(){CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=20;p.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(p);return p;}
    static double[] raw(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] raw=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,raw,at,f.length);at+=f.length;}return raw;}
    static double volume(CadProject p){BodyGraph.validate(p);double[] r=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,BodyGraph.pack(p,raw(p)),null);if(r.length!=7)throw new AssertionError("Evaluation failed: "+NativeCadEngine.lastFailure().getMessage());return r[0];}
    static CadProject top(){CadProject p=new CadProject();p.sketchOriginZ=20;return p;}
    static void add(CadProject p,double[] f,String ref,int target){p.committedFeatures.add(f);p.committedProfileIds.add(ref);p.featureBodyTargets.add(target);p.featureBodyOutputs.add(target==0?BodyGraph.allocate(p):target);}
    public static void main(String[] args)throws Exception {
        final double plate=120000,h1=Math.PI*25*20,h2=Math.PI*9*20;
        CadProject p=plate();
        add(p,ExtrusionPlan.feature(top(),new SketchPrimitive("a","Okrąg",20,30,25,30),"cut",20,0,false,false),"a",1);
        add(p,ExtrusionPlan.feature(top(),new SketchPrimitive("b","Okrąg",70,30,73,30),"cut",20,0,false,false),"b",1);
        add(p,FeaturePattern.create(FeaturePattern.LINEAR,new int[]{0},new double[3],new double[]{0,1,0},2,-15),"",1);
        double full=volume(p);close("two holes and a pattern copy of the first",full,plate-2*h1-h2,1e-6);
        FeatureHistory.setSuppressed(p,1,true);close("suppressed second hole is not cut",volume(p),plate-2*h1,1e-6);
        CadProject saved=CadProject.fromJson(new JSONObject(p.toJson().toString()));check(saved.suppressedFeatures.contains(1),"suppression persists");close("and evaluates the same after load",volume(saved),plate-2*h1,1e-6);
        FeatureHistory.setSuppressed(p,1,false);FeatureHistory.setSuppressed(p,0,true);
        check(FeatureHistory.inactive(p,2),"a pattern of a suppressed source is inactive");close("suppressing the source removes its copies",volume(p),plate-h2,1e-6);
        FeatureHistory.setSuppressed(p,0,false);close("restored",volume(p),full,1e-6);
        FeatureHistory.setMarker(p,1);close("rollback marker after operation 1",volume(p),plate-h1,1e-6);
        FeatureHistory.setMarker(p,0);close("rollback marker at the start",volume(p),plate,1e-6);
        CadProject kept=CadProject.fromJson(new JSONObject(p.toJson().toString()));check(kept.historyMarker==0&&kept.historyMarkerCount==3,"rollback marker is saved with the document");
        // Insertion at the marker: a new cut appended while the marker is after operation 1 is evaluated there and moved there.
        FeatureHistory.setMarker(p,1);
        double hn=Math.PI*4*20;p.committedFeatures.add(ExtrusionPlan.feature(top(),new SketchPrimitive("n","Okrąg",50,50,52,50),"cut",20,0,false,false));p.committedProfileIds.add("n");p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);
        close("appended cut is evaluated right after the marker",volume(p),plate-h1-hn,1e-6);
        FeatureHistory.settle(p);check(p.historyMarker==2&&p.historyMarkerCount==4&&p.committedProfileIds.get(1).equals("n"),"settle moves the new operation to the marker and advances it");
        check(FeaturePattern.sources(p.committedFeatures.get(3))[0]==0,"pattern source keeps pointing at the first cut after the insertion");
        close("geometry unchanged by settling",volume(p),plate-h1-hn,1e-6);
        FeatureHistory.setMarker(p,Integer.MAX_VALUE);close("marker at the end: all operations incl. the inserted one",volume(p),plate-2*h1-h2-hn,1e-6);
        FeatureRemoval.apply(p,FeatureRemoval.plan(p,1));close("removing the inserted cut restores the original model",volume(p),full,1e-6);
        CadProject mk=CadProject.fromJson(new JSONObject(p.toJson().toString()));FeatureHistory.setMarker(mk,2);FeatureRemoval.apply(mk,FeatureRemoval.plan(mk,0));
        check(mk.historyMarker==-1||mk.historyMarker<mk.historyMarkerCount,"marker stays consistent after removing an operation before it");
        // Reordering: the two cuts can swap; the pattern stays after its source.
        CadProject r=CadProject.fromJson(new JSONObject(p.toJson().toString()));FeatureHistory.swap(r,0);
        check(FeaturePattern.sources(r.committedFeatures.get(2))[0]==1,"pattern source is renumbered after the swap");close("swapped cuts give the same solid",volume(r),full,1e-6);
        rejects(()->FeatureHistory.swap(r,1),"pattern cannot move before its source");
        CadProject s=CadProject.fromJson(new JSONObject(p.toJson().toString()));s.suppressedFeatures.add(0);s.parameterBindings.put("depth:0","x");FeatureHistory.swap(s,0);
        check(s.suppressedFeatures.contains(1)&&!s.suppressedFeatures.contains(0),"suppression follows the moved operation");check(s.parameterBindings.containsKey("depth:1"),"parameter bindings follow the moved operation");
        // Hole on a face created by an earlier operation keeps its creator reference through a swap of others.
        CadProject hp=plate();add(hp,ExtrusionPlan.feature(top(),new SketchPrimitive("boss","Prostokąt",10,10,40,40),"add",10,0,false,false),"boss",1);
        add(hp,ExtrusionPlan.feature(top(),new SketchPrimitive("c","Okrąg",80,30,83,30),"cut",20,0,false,false),"c",1);
        add(hp,HoleFeature.create(new double[]{25,25,30},new double[]{0,0,1},0,4,false,5,0,0,0,0),"",1);
        double hv=volume(hp);FeatureHistory.swap(hp,1);check((int)hp.committedFeatures.get(1)[8]==0,"hole may move before an unrelated cut");close("model unchanged after the swap",volume(hp),hv,1e-6);
        rejects(()->FeatureHistory.swap(hp,0),"hole cannot move before the operation that created its face");
        CadProject hq=plate();add(hq,ExtrusionPlan.feature(top(),new SketchPrimitive("c","Okrąg",80,30,83,30),"cut",20,0,false,false),"c",1);
        add(hq,ExtrusionPlan.feature(top(),new SketchPrimitive("boss","Prostokąt",10,10,40,40),"add",10,0,false,false),"boss",1);
        add(hq,HoleFeature.create(new double[]{25,25,30},new double[]{0,0,1},1,4,false,5,0,0,0,0),"",1);
        double qv=volume(hq);FeatureHistory.swap(hq,0);check((int)hq.committedFeatures.get(2)[8]==0,"hole face creator renumbered when its creator moves");close("model unchanged after moving the face creator",volume(hq),qv,1e-6);
        // Removal keeps the suppressed set consistent.
        CadProject rm=CadProject.fromJson(new JSONObject(p.toJson().toString()));rm.suppressedFeatures.add(1);FeatureRemoval.apply(rm,FeatureRemoval.plan(rm,0));
        check(rm.suppressedFeatures.contains(0)&&rm.committedFeatures.size()==1,"suppressed index shifts when an earlier operation is removed (pattern removed with its source)");
        // A body creator used later cannot be suppressed alone.
        CadProject b=plate();add(b,ExtrusionPlan.feature(top(),new SketchPrimitive("n","Prostokąt",0,0,10,10),"new",5,0,false,false),"n",0);int body=b.featureBodyOutputs.get(0);
        add(b,ExtrusionPlan.feature(top(),new SketchPrimitive("m","Okrąg",5,5,7,5),"cut",30,0,false,false),"m",body);
        rejects(()->FeatureHistory.setSuppressed(b,0,true),"suppressing a body used later is refused");
        FeatureHistory.setSuppressed(b,1,true);FeatureHistory.setSuppressed(b,0,true);close("both suppressed: only the plate",volume(b),plate,1e-6);
        JSONObject bad=new JSONObject(p.toJson().toString());bad.put("suppressed",new JSONArray(new int[]{9}));boolean refused=false;try{CadProject.fromJson(bad);}catch(JSONException e){refused=true;}check(refused,"suppressed index outside history refused on load");
        System.out.println("PASS HistoryKernelProbe: "+checks+" checks");
    }
}
