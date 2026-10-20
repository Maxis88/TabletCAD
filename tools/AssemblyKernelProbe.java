import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** 0.80 assemblies: components, associative instances, face-to-face placement, interference, BOM. */
public final class AssemblyKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static void close(String what,double actual,double expected,double tolerance){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(what+": "+actual+" != "+expected);checks++;System.out.println("PASS "+what+" = "+actual);}
    static CadProject plate(){CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=20;p.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(p);return p;}
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] raw=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,raw,at,f.length);at+=f.length;}BodyGraph.validate(p);return BodyGraph.pack(p,raw);}
    static List<BodyAnalysis.Body> bodies(CadProject p){double[] r=NativeCadEngine.bodyInertia(100,60,20,0,p.featureBase,packed(p),null);if(r.length==0)throw new AssertionError("Evaluation failed: "+NativeCadEngine.lastFailure().getMessage());return BodyAnalysis.read(p,r);}
    static BodyAnalysis.Body body(List<BodyAnalysis.Body> list,int id){for(BodyAnalysis.Body b:list)if(b.id==id)return b;throw new AssertionError("no body "+id);}
    static int addInstance(CadProject p,Component src,Component inst,double[] delta){int created=0;for(int b:new ArrayList<>(src.bodies)){created=BodyGraph.allocate(p);p.committedFeatures.add(Assembly.compose(delta,BodyInstance.create(created,new double[3],new double[3],0,new double[3])));p.committedProfileIds.add("");p.featureBodyTargets.add(b);p.featureBodyOutputs.add(b);inst.bodies.add(created);}return created;}
    public static void main(String[] args)throws Exception {
        CadProject p=plate();Component part=Assembly.group(p,"Płyta",Collections.singletonList(1));check(p.components.size()==1&&part.bodies.contains(1),"component created from the base body");
        Component inst=Component.create("Płyta · wystąpienie 1");inst.instanceOf=part.id;
        int copy=addInstance(p,part,inst,Assembly.instance(2,new double[]{150,0,0},new double[]{0,0,1},0,new double[3]));p.components.add(inst);
        List<BodyAnalysis.Body> b=bodies(p);check(b.size()==2,"instance is a separate body");close("instance volume",body(b,copy).volume,120000,1e-6);close("instance centroid X",body(b,copy).centroid[0],200,1e-9);
        // Associativity: a cut in the source plate appears in the instance.
        CadProject at=new CadProject();at.sketchOriginZ=20;p.committedFeatures.add(0,ExtrusionPlan.feature(at,new SketchPrimitive("h","Okrąg",50,30,55,30),"cut",20,0,false,false));p.committedProfileIds.add(0,"h");p.featureBodyTargets.add(0,1);p.featureBodyOutputs.add(0,1);
        b=bodies(p);close("instance follows the edited source",body(b,copy).volume,120000-Math.PI*25*20,1e-6);
        // Rotated instance: 90° about Z through the plate centre, then mate its −X face (now facing −Y) … checked via compose.
        double[] rot=Assembly.compose(Assembly.instance(2,new double[3],new double[]{0,0,1},90,new double[]{200,30,10}),p.committedFeatures.get(1));
        double[] moved=BodyInstance.apply(rot,0,0,0),expected={230,-20,0};
        close("compose: rotation about a point applied after the translation (x)",moved[0],expected[0],1e-9);close("(y)",moved[1],expected[1],1e-9);
        // Face-to-face: put the instance's left face (x = 150, normal −X) onto the plate's right face (x = 100, normal +X).
        double[] mated=Assembly.mate(p.committedFeatures.get(1),new double[]{150,30,10},new double[]{-1,0,0},new double[]{100,30,10},new double[]{1,0,0},0);
        p.committedFeatures.set(1,mated);b=bodies(p);close("mated instance starts at the plate face (centroid X)",body(b,copy).centroid[0],150,1e-9);
        double[] d=NativeCadEngine.bodyDistance(100,60,20,0,p.featureBase,packed(p),null,1,copy);close("mated faces touch",d[0],0,1e-9);close("without overlap",d[7],0,1e-6);
        p.committedFeatures.set(1,Assembly.mate(p.committedFeatures.get(1),new double[]{100,30,10},new double[]{-1,0,0},new double[]{100,30,10},new double[]{1,0,0},5));
        d=NativeCadEngine.bodyDistance(100,60,20,0,p.featureBase,packed(p),null,1,copy);close("gap of 5 mm",d[0],5,1e-9);
        // Turning mate: the instance's top face (normal +Z) onto the plate's right face → instance turned 90° about Y.
        p.committedFeatures.set(1,Assembly.mate(p.committedFeatures.get(1),new double[]{155,30,20},new double[]{0,0,1},new double[]{100,30,10},new double[]{1,0,0},0));
        b=bodies(p);d=NativeCadEngine.bodyDistance(100,60,20,0,p.featureBase,packed(p),null,1,copy);close("turned instance touches the face",d[0],0,1e-7);close("turned instance does not overlap",d[7],0,1e-6);
        close("turned instance: depth 20 now along X (centroid X = 100 + 10)",body(b,copy).centroid[0],110,1e-7);
        // Interference when overlapping.
        p.committedFeatures.set(1,Assembly.instance(copy,new double[]{50,0,0},new double[3],0,new double[3]));d=NativeCadEngine.bodyDistance(100,60,20,0,p.featureBase,packed(p),null,1,copy);
        check(d[7]>1000,"overlapping instance is reported with its common volume ("+d[7]+")");
        // Bill of materials.
        p.materialDensityKgM3=7850;String bom=Assembly.bom(p,bodies(p),false);check(bom.contains("Płyta × 2")&&bom.contains("Masa całkowita"),"BOM counts the instance as a second piece:\n"+bom);
        String csv=Assembly.bom(p,bodies(p),true);check(csv.startsWith("Komponent;Bryła")&&csv.split("\n").length==3,"BOM CSV with two rows");
        // Persistence and validation.
        CadProject saved=CadProject.fromJson(new JSONObject(p.toJson().toString()));check(saved.components.size()==2&&saved.components.get(1).instance()&&saved.components.get(1).bodies.contains(copy),"components persist");
        JSONObject twice=new JSONObject(p.toJson().toString());twice.getJSONArray("components").getJSONObject(1).put("bodies",new JSONArray(new int[]{1}));boolean refused=false;try{CadProject.fromJson(twice);}catch(JSONException e){refused=true;}check(refused,"a body in two components is refused");
        // Removing the instance record prunes the instance component.
        FeatureRemoval.apply(saved,FeatureRemoval.plan(saved,1));Assembly.prune(saved);check(saved.components.size()==1,"removed instance leaves only the source component");
        boolean blocked=false;try{FeatureRemoval.plan(p,0);}catch(IllegalArgumentException e){blocked=true;}check(!blocked,"source operations stay editable");
        try{FeatureHistory.setSuppressed(p,1,true);check(true,"an instance can be suppressed");}catch(IllegalArgumentException e){throw new AssertionError(e);}
        // 0.96: persistent mate of an instance (its faces are created by the instance record).
        CadProject m=plate();Component src=Assembly.group(m,"Płyta",Collections.singletonList(1));
        Component w=Component.create("Płyta · wystąpienie");w.instanceOf=src.id;
        int instBody=addInstance(m,src,w,Assembly.instance(2,new double[]{150,0,30},new double[]{0,0,1},0,new double[3]));m.components.add(w);
        int instanceRecord=m.committedFeatures.size()-1;
        m.committedFeatures.add(MateFeature.create(MateFeature.CONTACT,1,0,new double[]{150,30,40},new double[]{-1,0,0},instanceRecord,new double[]{100,30,10},new double[]{1,0,0},-1));
        m.committedProfileIds.add("");m.featureBodyTargets.add(instBody);m.featureBodyOutputs.add(instBody);
        close("mated instance lies against the plate (centroid X)",body(bodies(m),instBody).centroid[0],150,1e-7);
        close("mate keeps the height (centroid Z)",body(bodies(m),instBody).centroid[2],40,1e-7);
        m.featureBase=new double[]{0,0,0,150,60,0,20};m.width=150;
        close("widened source: the instance is wider and still against the plate",body(bodies(m),instBody).centroid[0],225,1e-7);
        CadProject mSaved=CadProject.fromJson(new JSONObject(m.toJson().toString()));check(MateFeature.is(mSaved.committedFeatures.get(mSaved.committedFeatures.size()-1)),"instance mate is saved");
        boolean held=false;try{FeatureRemoval.plan(mSaved,instanceRecord);}catch(IllegalArgumentException e){held=true;}
        check(held,"the instance cannot be removed while its mate exists (the UI removes the mate first)");
        check(Assembly.removeMates(mSaved,w.bodies)==1,"removing the instance removes its mate first");
        FeatureRemoval.apply(mSaved,FeatureRemoval.plan(mSaved,instanceRecord));Assembly.prune(mSaved);BodyGraph.validate(mSaved);
        check(BodyGraph.bodies(mSaved).size()==1,"then the instance itself is removed");
        System.out.println("PASS AssemblyKernelProbe: "+checks+" checks");
    }
}
