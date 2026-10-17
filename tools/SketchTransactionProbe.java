import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class SketchTransactionProbe {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    public static void main(String[] args)throws Exception {
        try { run(args); }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    static void run(String[] args)throws Exception {
        CadProject p=CadProject.blank();
        SketchPrimitive circle=new SketchPrimitive("source","Okrąg",0,0,10,0);
        SketchPrimitive chord=new SketchPrimitive("chord","Linia",10,0,-10,0);
        p.sketch.add(circle);p.sketch.add(chord);
        p.featureBase=new double[]{1,0,0,10,0,0,8};p.featureBaseProfileId=circle.id;p.sketchExtruded=true;
        String original=CadProject.fromJson(p.toJson()).toJson().toString();SketchTransactions.begin(p);
        List<SketchPrimitive> pieces=SketchTrimmer.trim(p.sketch,circle,0,10);
        SketchTransactions.replace(p,circle.id,pieces);p.sketch.remove(circle);p.sketch.addAll(pieces);
        check(!SketchTransactions.unresolved(p).isEmpty(),"open source cannot commit");
        check(Arrays.equals(p.featureBase,new double[]{1,0,0,10,0,0,8}),"working trim preserves solid graph");
        p=CadProject.fromJson(p.toJson());
        check(SketchTransactions.active(p),"unfinished session survives JSON reload");
        check(SketchTransactions.baseline(p).toJson().toString().equals(original),"cancel baseline exactly preserved");
        SketchContours.Result joined=SketchContours.join(p.sketch,null);
        SketchTransactions.join(p,joined);p.sketch.removeIf(s->joined.consumed.contains(s.id));p.sketch.add(joined.profile);
        check(SketchTransactions.unresolved(p).isEmpty(),"joined closed profile inherits source association");
        CadProject result=SketchTransactions.candidate(p);
        check(!SketchTransactions.active(result)&&result.featureBase[0]==1002,"commit changes circle into analytic mixed wire");
        check(result.featureBase[6]==8&&result.featureBaseProfileId.equals(joined.profile.id),"commit retains depth and rebinds source");
        check(SketchTransactions.active(p)&&p.featureBase[0]==1,"candidate creation does not mutate draft");
        org.json.JSONObject corrupt=p.toJson();corrupt.put("sketchId","wrong");
        boolean rejected=false;try{CadProject.fromJson(corrupt);}catch(org.json.JSONException expected){rejected=true;}
        check(rejected,"mismatched persisted session rejected");
        if(args.length>0){
            double[] mesh=NativeCadEngine.evaluateFeatures(40,30,20,0,result.featureBase,new double[0],args[0]);
            double volume=CurveTrimProbe.volume(mesh);
            check(Math.abs(volume-Math.PI*100*4)<8,"committed profile produces native half-cylinder");
        }
        System.out.println("ALL SKETCH TRANSACTION TESTS PASSED");
    }
}
