import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/**
 * Acceptance test A (kernel level): plate with four Ø6.5 through holes, each dimensioned 10 mm from its two
 * nearest edges; the plate is then made wider, deeper and thicker, the holes keep their edge distances,
 * stay through holes and the document survives save/reload. Volumes are checked analytically.
 */
public final class AcceptanceAKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static void close(String what,double actual,double expected,double tolerance){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(what+": "+actual+" != "+expected);checks++;System.out.println("PASS "+what+" = "+actual);}
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] raw=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,raw,at,f.length);at+=f.length;}BodyGraph.validate(p);return BodyGraph.pack(p,raw);}
    static double volume(CadProject p){double[] r=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,packed(p),null);if(r.length!=7)throw new AssertionError("Evaluation failed: "+NativeCadEngine.lastFailure().getMessage());return r[0];}
    static void resize(CadProject p,double w,double h,double d){p.featureBase[3]=w;p.featureBase[4]=h;p.featureBase[6]=d;p.width=w;p.height=h;p.depth=d;}
    public static void main(String[] args)throws Exception {
        CadProject p=new CadProject();p.emptyDocument=false;p.featureBase=new double[]{0,0,0,100,60,0,20};resize(p,100,60,20);BodyGraph.begin(p);
        double r=3.25;double[][] corners={{10,10},{90,10},{90,50},{10,50}};
        double[] edges=NativeCadEngine.edgeMeasurements(100,60,20,0,p.featureBase,packed(p),null),faces=NativeCadEngine.faceAnalysis(100,60,20,0,p.featureBase,packed(p),null);
        for(int i=0;i<4;i++) {
            double[] c={corners[i][0],corners[i][1],20};HolePlacement place=HolePlacement.find(c,new double[]{0,0,1},edges);
            check(place!=null&&place.hasB(),"hole "+(i+1)+": two edges offered for dimensions");
            close("hole "+(i+1)+": first edge distance",Math.min(place.distanceA,place.distanceB),10,1e-9);
            double[] refs=HoleAssociation.references(place,c,faces);check(refs!=null&&!Double.isNaN(refs[5]),"hole "+(i+1)+": both edges bound to faces");
            p.committedFeatures.add(HoleFeature.create(c,new double[]{0,0,1},-1,2*r,true,0,0,0,0,0));p.committedProfileIds.add("");p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);
            p.holeReferences.put(i,refs);
        }
        close("plate with four through holes",volume(p),100*60*20-4*Math.PI*r*r*20,1e-6);
        // Wider, deeper and thicker plate.
        resize(p,140,80,30);check(HoleAssociation.update(p,null).isEmpty(),"all hole references found after the change");
        double[][] expected={{10,10},{130,10},{130,70},{10,70}};
        for(int i=0;i<4;i++){double[] f=p.committedFeatures.get(i);close("hole "+(i+1)+" X keeps 10 mm from its edge",f[2],expected[i][0],1e-9);close("hole "+(i+1)+" Y keeps 10 mm from its edge",f[3],expected[i][1],1e-9);}
        close("holes still go through the thicker plate",volume(p),140*80*30-4*Math.PI*r*r*30,1e-6);
        // Persistence and a second, smaller change after reload.
        CadProject loaded=CadProject.fromJson(new JSONObject(p.toJson().toString()));check(loaded.holeReferences.size()==4,"four hole references persist");
        resize(loaded,120,70,25);check(HoleAssociation.update(loaded,null).isEmpty(),"references resolve after reload");
        close("reloaded hole 3 X",loaded.committedFeatures.get(2)[2],110,1e-9);close("reloaded hole 3 Y",loaded.committedFeatures.get(2)[3],60,1e-9);
        close("volume after reload and resize",volume(loaded),120*70*25-4*Math.PI*r*r*25,1e-6);
        System.out.println("PASS AcceptanceAKernelProbe: "+checks+" checks");
    }
}
