import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** 0.81 "Odsuń ścianę": press-pull of the picked planar face of the body (not of its source sketch). */
public final class FaceOffsetKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static void close(String what,double actual,double expected,double tolerance){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(what+": "+actual+" != "+expected);checks++;System.out.println("PASS "+what+" = "+actual);}
    static CadProject plate(){CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=20;p.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(p);return p;}
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] raw=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,raw,at,f.length);at+=f.length;}BodyGraph.validate(p);return BodyGraph.pack(p,raw);}
    static double volume(CadProject p){double[] r=NativeCadEngine.bodyInertia(100,60,20,0,p.featureBase,packed(p),null);if(r.length==0)throw new AssertionError("Evaluation failed: "+NativeCadEngine.lastFailure().getMessage());double v=0;for(BodyAnalysis.Body b:BodyAnalysis.read(p,r))v+=b.volume;return v;}
    static void add(CadProject p,double[] f){p.committedFeatures.add(f);p.committedProfileIds.add("");p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);}
    static boolean fails(CadProject p){double[] r=NativeCadEngine.bodyInertia(100,60,20,0,p.featureBase,packed(p),null);return r.length==0;}
    public static void main(String[] args)throws Exception {
        CadProject p=plate();add(p,FaceOffset.create(5,new double[]{50,30,20},new double[]{0,0,1},-1));
        close("top face pulled out by 5 mm",volume(p),100*60*25,1e-6);
        p.committedFeatures.set(0,FaceOffset.create(-5,new double[]{50,30,20},new double[]{0,0,1},-1));close("top face pushed in by 5 mm",volume(p),100*60*15,1e-6);
        p.committedFeatures.set(0,FaceOffset.create(10,new double[]{100,30,10},new double[]{1,0,0},-1));close("side face pulled out (not from the sketch plane)",volume(p),110*60*20,1e-6);
        p.committedFeatures.set(0,FaceOffset.create(-20,new double[]{50,30,20},new double[]{0,0,1},-1));check(fails(p),"offset removing the whole body is refused: "+NativeCadEngine.lastFailure().getMessage());
        p.committedFeatures.set(0,FaceOffset.create(5,new double[]{50,30,40},new double[]{0,0,1},-1));close("a stale face point resolves to the face with the same creator and normal (parametric)",volume(p),100*60*25,1e-6);
        p.committedFeatures.set(0,FaceOffset.create(5,new double[]{50,30,20},new double[]{0,1,1},-1));check(fails(p),"a face that does not exist is reported: "+NativeCadEngine.lastFailure().getMessage());
        // A face created by a later operation: pull the bottom of a through hole's plate after the cut.
        CadProject q=plate();CadProject at=new CadProject();at.sketchOriginZ=20;
        q.committedFeatures.add(ExtrusionPlan.feature(at,new SketchPrimitive("h","Okrąg",50,30,55,30),"cut",20,0,false,false));q.committedProfileIds.add("h");q.featureBodyTargets.add(1);q.featureBodyOutputs.add(1);
        add(q,FaceOffset.create(5,new double[]{20,20,20},new double[]{0,0,1},-1));
        close("pulled face keeps the hole",volume(q),(100*60-Math.PI*25)*25,1e-6);
        // Parametric: edit the base depth, the offset follows the moved face.
        q.featureBase=new double[]{0,0,0,100,60,0,30};q.depth=30;
        double[] r=NativeCadEngine.bodyInertia(100,60,30,0,q.featureBase,packed(q),null);check(r.length>0,"rebuild after editing the base depth: "+(r.length==0?NativeCadEngine.lastFailure().getMessage():"ok"));
        // Records, references, persistence.
        double[] f=q.committedFeatures.get(1);FaceOffset.validate(f);check(FeatureHistory.references(f).isEmpty(),"base-face offset has no history references");
        double[] tagged=FaceOffset.create(2,new double[]{1,1,1},new double[]{0,0,1},0);check(FeatureHistory.references(tagged).equals(Collections.singletonList(0)),"face creator is a history reference");
        check(FeatureHistory.remap(tagged,i->i==0?1:i)[8]==1,"face creator is renumbered");
        CadProject saved=CadProject.fromJson(new JSONObject(q.toJson().toString()));check(FaceOffset.is(saved.committedFeatures.get(1)),"face offset persists");
        boolean bad=false;try{FaceOffset.create(0,new double[3],new double[]{0,0,1},-1);}catch(IllegalArgumentException e){bad=true;}check(bad,"zero offset rejected");
        check(CadTimeline.entries(saved).stream().anyMatch(i->i.title.startsWith("Odsunięcie ściany")),"timeline label");
        System.out.println("PASS FaceOffsetKernelProbe: "+checks+" checks");
    }
}
