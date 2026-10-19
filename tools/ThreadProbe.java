import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import java.util.*;

public final class ThreadProbe {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    public static void main(String[] args)throws Exception {
        double[] hole={1,1,15,15,17.5,15,10,0,0,10,1,0,0,0,1,0};
        CadModelResult drilled=new CadModelResult(NativeCadEngine.evaluateModel(30,30,10,0,null,hole,null));
        check(drilled.holes.size()==1,"kernel identifies actual complete internal cylindrical wall: "+drilled.holes.size());HoleThread.Cylinder cylinder=drilled.holes.get(0);
        check(Math.abs(cylinder.radius-2.5)<1e-6&&Math.abs(cylinder.max-cylinder.min-10)<1e-6,"hole measurement preserves exact diameter and depth");
        HoleThread.Preset preset=HoleThread.nearest(5).get(0);check(preset.diameter==6&&preset.pitch==1,"5 mm drill selects M6 x 1 preset");
        CadProject p=new CadProject();p.width=30;p.height=30;p.depth=10;p.committedFeatures.add(hole);p.committedProfileIds.add("");BodyGraph.begin(p);
        double[] thread=HoleThread.create(cylinder,preset,3,true,false);p.committedFeatures.add(thread);p.committedProfileIds.add("");BodyGraph.reconcile(p);
        double[] raw=new double[32];System.arraycopy(hole,0,raw,0,16);System.arraycopy(thread,0,raw,16,16);double[] packed=BodyGraph.pack(p,raw);
        double[] before=NativeCadEngine.bodyProperties(30,30,10,0,null,hole,null),after=NativeCadEngine.bodyProperties(30,30,10,0,null,packed,null);
        check(after.length==7,"helical thread produces valid OCCT B-Rep");check(after[0]<before[0]-1&&after[0]>before[0]-60&&after[5]==1,"thread removes actual wall material without splitting solid");
        check(CadProject.fromJson(p.toJson()).committedFeatures.get(1)[0]==-8,"parametric thread record persists");
        check(NativeCadEngine.evaluateFeatures(30,30,10,0,null,packed,args[0]).length>0,"real thread exports STEP");
        double[] roundTrip=NativeCadEngine.bodyProperties(30,30,10,0,null,new double[0],args[0]);check(roundTrip.length==7&&Math.abs(roundTrip[0]-after[0])<2e-5*after[0],"thread STEP round trip preserves solid volume within 2e-5 (helical faces are B-spline in STEP): "+(roundTrip.length==7?roundTrip[0]+" vs "+after[0]:"length "+roundTrip.length));
        raw[17]=-1;double[] left=NativeCadEngine.bodyProperties(30,30,10,0,null,BodyGraph.pack(p,raw),null);check(left.length==7&&Math.abs(left[0]-after[0])<.1,"left-hand preset generates real counterpart with matching volume");
        try{HoleThread.create(cylinder,preset,11,true,false);throw new AssertionError("over-depth accepted");}catch(IllegalArgumentException expected){check(true,"over-depth thread rejected before replacing document");}
        for(double depth:new double[]{3,7.25,10})for(boolean leftHand:new boolean[]{false,true})for(boolean fromMaximum:new boolean[]{true,false}) {
            double[] full=HoleThread.create(cylinder,preset,depth,fromMaximum,leftHand);
            System.arraycopy(full,0,raw,16,16);
            double[] fullResult=NativeCadEngine.bodyProperties(30,30,10,0,null,BodyGraph.pack(p,raw),null);
            check(fullResult.length==7&&fullResult[0]<before[0]-depth*.5&&fullResult[5]==1,
                "M6 x 1 in a 5 mm hole, depth="+depth+", left="+leftHand+", fromMaximum="+fromMaximum
                +(fullResult.length==0?"; "+NativeCadEngine.lastFailure().reason:"; volume="+fullResult[0]));
        }
        System.out.println("ALL THREAD TESTS PASSED");
    }
}
