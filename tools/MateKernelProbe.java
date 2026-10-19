import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** 0.82 "Dosuń": face-to-face and coaxial placement of ordinary bodies (rotation + move records). */
public final class MateKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static void close(String what,double actual,double expected,double tolerance){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(what+": "+actual+" != "+expected);checks++;System.out.println("PASS "+what+" = "+actual);}
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] raw=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,raw,at,f.length);at+=f.length;}BodyGraph.validate(p);return BodyGraph.pack(p,raw);}
    static BodyAnalysis.Body body(CadProject p,int id){double[] r=NativeCadEngine.bodyInertia(100,60,20,0,p.featureBase,packed(p),null);if(r.length==0)throw new AssertionError("Evaluation failed: "+NativeCadEngine.lastFailure().getMessage());for(BodyAnalysis.Body b:BodyAnalysis.read(p,r))if(b.id==id)return b;throw new AssertionError("no body "+id);}
    static CadProject twoBodies(SketchPrimitive second,double height){
        CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=20;p.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(p);
        CadProject at=new CadProject();p.committedFeatures.add(ExtrusionPlan.feature(at,second,"new",height,0,false,false));p.committedProfileIds.add("b");p.featureBodyTargets.add(0);p.featureBodyOutputs.add(BodyGraph.allocate(p));return p;
    }
    static void place(CadProject p,int body,double[][] delta){for(double[] r:Assembly.transformRecords(delta)){p.committedFeatures.add(r);p.committedProfileIds.add("");p.featureBodyTargets.add(body);p.featureBodyOutputs.add(body);}}
    static double[] distance(CadProject p,int a,int b){return NativeCadEngine.bodyDistance(100,60,20,0,p.featureBase,packed(p),null,a,b);}
    public static void main(String[] args)throws Exception {
        CadProject p=twoBodies(new SketchPrimitive("b","Prostokąt",200,0,220,10),30);int b=p.featureBodyOutputs.get(0);
        close("second body volume",body(p,b).volume,6000,1e-6);
        // Contact: left face of the block (x=200, −X) onto the right face of the plate (x=100, +X).
        CadProject q=twoBodies(new SketchPrimitive("b","Prostokąt",200,0,220,10),30);place(q,b,Assembly.planeDelta(new double[]{200,5,15},new double[]{-1,0,0},new double[]{100,30,10},new double[]{1,0,0},0,false));
        check(q.committedFeatures.size()==2&&BodyTransform.is(q.committedFeatures.get(1))&&q.committedFeatures.get(1)[1]==BodyTransform.MOVE,"parallel faces need only a move record");
        close("contact: centroid X",body(q,b).centroid[0],110,1e-7);double[] d=distance(q,1,b);close("faces touch",d[0],0,1e-7);close("no overlap",d[7],0,1e-6);
        // Turning contact: side face +Y of the block onto the top of the plate (+Z) → 90° rotation, block lies on the plate.
        q=twoBodies(new SketchPrimitive("b","Prostokąt",200,0,220,10),30);place(q,b,Assembly.planeDelta(new double[]{210,10,15},new double[]{0,1,0},new double[]{50,30,20},new double[]{0,0,1},0,false));
        BodyAnalysis.Body moved=body(q,b);close("turned block centroid Z (rotation direction)",moved.centroid[2],25,1e-7);close("turned block centroid Y",moved.centroid[1],10,1e-7);close("volume kept",moved.volume,6000,1e-6);
        d=distance(q,1,b);close("only the plane is constrained: block stays beside the plate (gap along X)",d[0],100,1e-7);close("turned block does not overlap",d[7],0,1e-6);
        // Gap and flush.
        q=twoBodies(new SketchPrimitive("b","Prostokąt",200,0,220,10),30);place(q,b,Assembly.planeDelta(new double[]{200,5,15},new double[]{-1,0,0},new double[]{100,30,10},new double[]{1,0,0},4,false));close("gap 4 mm",distance(q,1,b)[0],4,1e-7);
        q=twoBodies(new SketchPrimitive("b","Prostokąt",200,0,220,10),30);place(q,b,Assembly.planeDelta(new double[]{210,5,30},new double[]{0,0,1},new double[]{50,30,20},new double[]{0,0,1},0,true));
        close("flush: top faces in one plane (centroid Z)",body(q,b).centroid[2],5,1e-7);
        // Coaxial: a pin (Ø10) into the plate hole at (50,30).
        CadProject c=twoBodies(new SketchPrimitive("b","Okrąg",300,0,305,0),30);int pin=c.featureBodyOutputs.get(0);
        place(c,pin,Assembly.axisDelta(new double[]{300,0,0},new double[]{0,0,1},new double[]{50,30,10},new double[]{0,0,-1}));
        BodyAnalysis.Body pinBody=body(c,pin);close("coaxial pin X",pinBody.centroid[0],50,1e-7);close("coaxial pin Y",pinBody.centroid[1],30,1e-7);close("axial position kept",pinBody.centroid[2],15,1e-7);
        // Tilted axis: pin along X made coaxial with a Z axis.
        double[][] t=Assembly.axisDelta(new double[]{0,0,0},new double[]{1,0,0},new double[]{5,5,0},new double[]{0,0,1});
        double[] a=Assembly.apply(t[0],new double[]{10,0,0});check(Math.abs(a[0]+t[1][0]-5)<1e-9&&Math.abs(a[1]+t[1][1]-5)<1e-9,"points of the moving axis land on the target axis");
        // Persistent mates (record -25): re-solved when the target body changes.
        CadProject m=twoBodies(new SketchPrimitive("b","Prostokąt",200,0,220,10),30);int blk=m.featureBodyOutputs.get(0);
        double[] mate=MateFeature.create(MateFeature.CONTACT,1,0,new double[]{200,5,15},new double[]{-1,0,0},0,new double[]{100,30,10},new double[]{1,0,0},-1);
        m.committedFeatures.add(mate);m.committedProfileIds.add("");m.featureBodyTargets.add(blk);m.featureBodyOutputs.add(blk);
        close("persistent contact: block against the plate (centroid X)",body(m,blk).centroid[0],110,1e-7);
        m.featureBase=new double[]{0,0,0,150,60,0,20};m.width=150;
        close("after widening the plate the block follows its face",body(m,blk).centroid[0],160,1e-7);
        CadProject turn=twoBodies(new SketchPrimitive("b","Prostokąt",200,0,220,10),30);
        turn.committedFeatures.add(MateFeature.create(MateFeature.CONTACT,1,0,new double[]{210,10,15},new double[]{0,1,0},0,new double[]{50,30,20},new double[]{0,0,1},-1));turn.committedProfileIds.add("");turn.featureBodyTargets.add(blk);turn.featureBodyOutputs.add(blk);
        close("persistent turning contact: block lies on the plate",body(turn,blk).centroid[2],25,1e-7);
        turn.featureBase=new double[]{0,0,0,100,60,0,35};turn.depth=35;
        close("thicker plate lifts the mated block",body(turn,blk).centroid[2],40,1e-7);
        turn.committedFeatures.set(1,MateFeature.withGap(turn.committedFeatures.get(1),MateFeature.CONTACT,5));close("gap of 5 mm",body(turn,blk).centroid[2],45,1e-7);
        CadProject saved=CadProject.fromJson(new org.json.JSONObject(turn.toJson().toString()));check(MateFeature.is(saved.committedFeatures.get(1)),"persistent mate is saved");
        check(FeatureHistory.references(mate).equals(Collections.singletonList(0)),"moving face creator is a history reference (base face has none)");
        boolean blocked=false;try{FeatureRemoval.plan(saved,0);}catch(IllegalArgumentException e){blocked=true;}
        check(blocked||!FeatureRemoval.plan(saved,0).consequences.isEmpty(),"removing the mated body's creator is reported");
        double[] lost=MateFeature.create(MateFeature.CONTACT,1,0,new double[]{200,5,15},new double[]{1,1,0},0,new double[]{100,30,10},new double[]{1,0,0},-1);
        CadProject bad=twoBodies(new SketchPrimitive("b","Prostokąt",200,0,220,10),30);bad.committedFeatures.add(lost);bad.committedProfileIds.add("");bad.featureBodyTargets.add(blk);bad.featureBodyOutputs.add(blk);
        check(NativeCadEngine.bodyInertia(100,60,20,0,bad.featureBase,packed(bad),null).length==0,"a face that cannot be found is reported, not guessed: "+NativeCadEngine.lastFailure().getMessage());
        System.out.println("PASS MateKernelProbe: "+checks+" checks");
    }
}
