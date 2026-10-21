import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Regression (1.0.0-rc5): full-length M3/M4 threads were silently dropped by OCCT ("nothing to cut"). Threads added in one commit to several holes of various kinds (counterbore, countersink, side holes, mixed sizes). */
public final class ThreadVariantsProbe {
    static int checks;
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] r=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,r,at,f.length);at+=f.length;}return r;}
    static CadModelResult eval(CadProject p){double[] r=NativeCadEngine.evaluateModel(p.width,p.height,p.depth,0,p.featureBase,BodyGraph.pack(p,packed(p)),null);if(r.length==0)throw new AssertionError("eval: "+NativeCadEngine.lastFailure().getMessage());return new CadModelResult(r);}
    static void add(CadProject p,double[] f){p.committedFeatures.add(f);p.committedProfileIds.add("");p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);}
    public static void main(String[] a)throws Exception{
        String[] variants=System.getenv("TV")!=null?System.getenv("TV").split(","):new String[]{"counterbore","countersink","side","mixed","blind","d2.5","d3.3","d4.2","d6.8"};
        for(String v:variants){
            CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=20;p.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(p);
            for(double x:new double[]{20,50,80}){
                double d=v.startsWith("d")?Double.parseDouble(v.substring(1)):v.equals("mixed")?(x==20?3.3:x==50?5:8.5):5;
                if(v.equals("side"))add(p,HoleFeature.create(new double[]{0,30,x/5+2},new double[]{-1,0,0},-1,d,false,30,HoleFeature.SIMPLE,0,0,118));
                else add(p,HoleFeature.create(new double[]{x,30,20},new double[]{0,0,1},-1,d,!v.equals("blind"),12,v.equals("counterbore")?HoleFeature.COUNTERBORE:v.equals("countersink")?HoleFeature.COUNTERSINK:HoleFeature.SIMPLE,v.equals("counterbore")?9:v.equals("countersink")?10:0,v.equals("counterbore")?4:v.equals("countersink")?90:0,118));
            }
            CadModelResult r=eval(p);List<HoleThread.Cylinder> free=new ArrayList<>();for(HoleThread.Cylinder h:r.holes)if(!HoleThread.threaded(h,p.committedFeatures))free.add(h);
            List<HoleThread.Cylinder> u=HoleThread.unique(free);
            System.out.println("INFO "+v+": cylinders "+free.size()+" unique "+u.size());for(HoleThread.Cylinder h:u)System.out.println("INFO cyl r="+h.radius+" o="+Arrays.toString(h.origin)+" ax="+Arrays.toString(h.axis)+" "+h.min+".."+h.max);
            for(HoleThread.Cylinder h:u){List<HoleThread.Preset> n=HoleThread.nearest(h.radius*2);if(n.isEmpty()){System.out.println("INFO no preset for Ø"+h.radius*2);continue;}
                CadProject one=CadProject.fromJson(new org.json.JSONObject(p.toJson().toString()));BodyGraph.begin(one);add(one,HoleThread.create(h,n.get(0),System.getenv("TD")!=null?Double.parseDouble(System.getenv("TD")):h.max-h.min,true,false));
                eval(one);checks++;System.out.println("PASS "+v+": single thread on Ø"+h.radius*2);}
            CadProject next=CadProject.fromJson(new org.json.JSONObject(p.toJson().toString()));BodyGraph.begin(next);
            for(HoleThread.Cylinder h:u){List<HoleThread.Preset> n=HoleThread.nearest(h.radius*2);if(n.isEmpty())continue;double[] f=HoleThread.create(h,n.get(0),h.max-h.min,true,false);next.committedFeatures.add(f);next.committedProfileIds.add("");next.featureBodyTargets.add(h.body);next.featureBodyOutputs.add(h.body);}
            BodyGraph.reconcile(next);
            eval(next);checks++;System.out.println("PASS "+v+": group threads");
        }
        System.out.println("PASS ThreadVariantsProbe: "+checks+" checks");
    }
}
