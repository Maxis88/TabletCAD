import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class BodyGraphProbe {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    static double[] feature(int mode,double depth,double z){return new double[]{0,mode,10,10,20,20,depth,0,0,z,1,0,0,0,1,0};}
    public static void main(String[] args)throws Exception {
        CadProject p=new CadProject();BodyGraph.begin(p);p.committedFeatures.add(feature(4,5,10));p.committedProfileIds.add("new");BodyGraph.reconcile(p);
        check(p.featureBodyTargets.get(0)==0&&p.featureBodyOutputs.get(0)==2,"new solid obtains an explicit unique body identity");
        String uuid=p.bodyUuids.get(2);p.selectedBody=1;p.committedFeatures.add(feature(1,10,32));p.committedProfileIds.add("cut");BodyGraph.reconcile(p);
        double[] raw=new double[32];System.arraycopy(p.committedFeatures.get(0),0,raw,0,16);System.arraycopy(p.committedFeatures.get(1),0,raw,16,16);double[] packed=BodyGraph.pack(p,raw);
        double[] properties=NativeCadEngine.bodyProperties(80,48,32,0,null,packed,null);check(properties.length==7&&Math.abs(properties[0]-122380)<.001&&properties[5]==2,"cut targets root body while overlapping second body remains intact");
        double[] result=NativeCadEngine.evaluateModel(80,48,32,0,null,packed,null);check(result.length>0,"model returns mesh and matching provenance in one OCCT evaluation");
        int meshLength=(int)result[0];Set<Integer> bodies=new HashSet<>(),operations=new HashSet<>();for(int i=1+meshLength;i<result.length;i+=3){bodies.add((int)result[i+1]);operations.add((int)result[i+2]);}
        check(bodies.contains(1)&&bodies.contains(2),"face provenance distinguishes actual target bodies");check(operations.contains(0)&&operations.contains(1),"history provenance preserves old and new surviving operation faces");
        CadProject restored=CadProject.fromJson(p.toJson());check(restored.bodyUuids.get(2).equals(uuid)&&restored.featureBodyTargets.get(1)==1,"body UUID and explicit target survive reload");
        double[] invalid=packed.clone();invalid[33]=99;check(NativeCadEngine.evaluateFeatures(80,48,32,0,null,invalid,null).length==0,"missing body target rejected without fallback to another solid");
        check(NativeCadEngine.evaluateFeatures(80,48,32,0,null,packed,args[0]).length>0&&NativeCadEngine.tessellateStep(args[0]).length>0,"targeted multi-body chain exports and reloads STEP");
        System.out.println("ALL BODY GRAPH TESTS PASSED");
    }
}
