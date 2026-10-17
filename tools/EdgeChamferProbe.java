import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class EdgeChamferProbe {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    static double[] eval(double[] features,String path){return NativeCadEngine.evaluateFeatures(40,30,20,0,null,features,path);}
    static double volume(double[] mesh){return EdgeFilletProbe.volume(mesh);}
    static double[] edge(double[] edges,double x,double y,double distance){double[] f=EdgeFilletProbe.edge(edges,x,y);f[0]=-2;f[11]=distance;EdgeChamfer.validate(f);return f;}
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
    static void run(String[] args)throws Exception {
        double[] edges=NativeCadEngine.modelEdges(40,30,20,0,null,new double[0],null);
        double[] f=edge(edges,0,0,3),mesh=eval(f,args[0]);double v=volume(mesh);
        check(Math.abs(v-(24000-3*3*20/2.0))<.001,"equal-distance chamfer removes exact triangular prism");
        double[] reversed=f.clone();for(int i=0;i<3;i++){reversed[2+i]=f[8+i];reversed[8+i]=f[2+i];}
        check(Math.abs(volume(eval(reversed,null))-v)<.001,"chamfer identity independent of edge orientation");
        check(Math.abs(volume(NativeCadEngine.tessellateStep(args[0]))-v)<.001,"chamfer STEP round trip preserves geometry");
        double[] second=edge(edges,40,30,2);
        check(Math.abs(volume(eval(EdgeFilletProbe.join(f,second),null))-(v-40))<.001,"ordered chamfers on two different edges");
        double[] cut={0,1,10,10,20,20,5,0,0,20,1,0,0,0,1,0};
        check(Math.abs(volume(eval(EdgeFilletProbe.join(f,cut),null))-(v-500))<.001,"Boolean cut after chamfer");
        check(Math.abs(volume(eval(EdgeFilletProbe.join(cut,f),null))-(v-500))<.001,"chamfer after Boolean cut");
        check(volume(eval(EdgeFilletProbe.join(f,EdgeFilletProbe.edge(edges,40,30)),null))<v,"fillet follows chamfer in same history");
        check(NativeCadEngine.evaluateFeatures(40,30,20,0,null,second,null,args[0]).length>0,"chamfer works on imported STEP");
        double[] changed=f.clone();changed[11]=2;
        check(Math.abs(volume(eval(changed,null))-(24000-40))<.001,"editing chamfer parameter regenerates exact shape");
        changed=f.clone();changed[11]=1000;check(eval(changed,null).length==0,"impossible chamfer rejected");
        changed=f.clone();changed[2]+=2;check(eval(changed,null).length==0,"missing edge never substituted silently");
        changed=f.clone();changed[11]=Double.NaN;check(eval(changed,null).length==0,"nonfinite chamfer rejected natively");
        changed=f.clone();changed[12]=1;check(eval(changed,null).length==0,"unknown native flags rejected");
        double[] cylinder={1,0,0,10,0,0,20};
        double[] rims=NativeCadEngine.modelEdges(40,30,20,0,cylinder,new double[0],null);double[] rim=null;
        for(int i=0;i<rims.length;i+=16)if(Math.abs(rims[i]-rims[i+6])<1e-6&&Math.abs(rims[i+1]-rims[i+7])<1e-6&&Math.abs(rims[i+2]-rims[i+8])<1e-6){rim=EdgeChamfer.create(rims,i,2);break;}
        check(rim!=null,"circular edge selected by exact signature");
        double before=volume(NativeCadEngine.evaluateFeatures(40,30,20,0,cylinder,new double[0],null));
        double after=volume(NativeCadEngine.evaluateFeatures(40,30,20,0,cylinder,rim,null));
        check(after<before-80&&after>before-160,"circular rim receives real conical chamfer");
        CadProject p=CadProject.blank();p.featureBase=new double[]{0,0,0,40,30,0,20};p.committedFeatures.add(f);p.committedProfileIds.add("");
        CadProject restored=CadProject.fromJson(p.toJson());
        check(EdgeChamfer.is(restored.committedFeatures.get(0))&&restored.committedFeatures.get(0)[11]==3,"schema persists chamfer and parameter");
        check(CadTimeline.entries(restored).get(2).title.equals("Fazowanie"),"timeline identifies chamfer separately from fillet");
        JSONObject bad=p.toJson();bad.getJSONArray("committedFeatures").getJSONArray(0).put(11,-2);
        boolean rejected=false;try{CadProject.fromJson(bad);}catch(JSONException expected){rejected=true;}
        check(rejected,"corrupted chamfer parameter rejected on load");
        System.out.println("ALL EDGE CHAMFER TESTS PASSED");
    }
}
