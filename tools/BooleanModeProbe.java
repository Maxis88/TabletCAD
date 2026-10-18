import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Real OCCT checks for separate solids and intersection, using isolated scratch files. */
public final class BooleanModeProbe {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    static void close(double actual,double expected,String label){check(Math.abs(actual-expected)<.001,label+" "+actual);}
    static double[] feature(int mode,double depth,double z){return new double[]{0,mode,10,10,20,20,depth,0,0,z,1,0,0,0,1,0};}
    static double[] properties(double[] f){return NativeCadEngine.bodyProperties(80,48,32,0,null,f,null);}
    public static void main(String[] args)throws Exception {
        double[] separate=feature(4,5,40),p=properties(separate);
        check(p.length>=6,"separate bodies return exact properties");close(p[0],123380,"separate volume");close(p[5],2,"two independent solids");
        p=properties(feature(4,5,10));close(p[5],2,"overlapping new body remains independent");
        p=properties(feature(8,5,10));close(p[0],500,"intersection volume");close(p[5],1,"intersection solid");
        check(NativeCadEngine.evaluateFeatures(80,48,32,0,null,feature(8,5,40),null).length==0,"empty intersection rejected");
        check(NativeCadEngine.evaluateFeatures(80,48,32,0,null,feature(0,5,40),null).length==0,"disconnected join rejected");
        p=properties(feature(1,5,32));close(p[0],122380,"partial inward cut removes material immediately");
        CadProject project=new CadProject();project.committedFeatures.add(separate);project.committedProfileIds.add("separate");
        check(CadProject.fromJson(project.toJson()).committedFeatures.get(0)[1]==4,"new-body record survives document round trip");
        check(FeatureDirection.withSymmetric(4,true)==6&&FeatureDirection.withSymmetric(10,false)==8,"editing symmetry retains operation");
        check(NativeCadEngine.evaluateFeatures(80,48,32,0,null,separate,args[0]).length>0,"separate solids export STEP");
        check(NativeCadEngine.tessellateStep(args[0]).length>0,"separate solids STEP reload");
        System.out.println("ALL BOOLEAN MODE TESTS PASSED");
    }
}
