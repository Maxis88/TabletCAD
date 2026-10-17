import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class EdgeFilletProbe {
    static void require(boolean v,String s){if(!v)throw new AssertionError(s);System.out.println("PASS "+s);}
    static double volume(double[] m){require(m.length>0,"nonempty valid fillet mesh");double v=0;for(int i=0;i<m.length;i+=13)v+=m[i]*(m[i+4]*m[i+8]-m[i+5]*m[i+7])+m[i+1]*(m[i+5]*m[i+6]-m[i+3]*m[i+8])+m[i+2]*(m[i+3]*m[i+7]-m[i+4]*m[i+6]);return Math.abs(v/6);}
    static double[] eval(double[] f,String path){return NativeCadEngine.evaluateFeatures(40,30,20,0,null,f,path);}
    static double[] join(double[] a,double[] b){double[] r=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,r,a.length,b.length);return r;}
    static double[] edge(double[] segments,double x,double y) {
        for(int i=0;i<segments.length;i+=16)if(Math.abs(segments[i]-x)<1e-6&&Math.abs(segments[i+1]-y)<1e-6&&Math.abs(segments[i+6]-x)<1e-6&&Math.abs(segments[i+7]-y)<1e-6&&Math.abs(segments[i+2]-segments[i+8])>19)
            return EdgeFillet.create(segments,i,3);
        throw new AssertionError("vertical edge not found");
    }
    public static void main(String[] args)throws Exception {
        double[] edges=NativeCadEngine.modelEdges(40,30,20,0,null,new double[0],null);
        require(edges.length==12*16,"twelve unique exact box edges");
        double[] f=edge(edges,0,0);double[] mesh=eval(f,args[0]);double v=volume(mesh);
        require(Math.abs(v-(24000-(1-Math.PI/4)*9*20))<1,"only selected vertical corner removed");
        double[] reversed=f.clone();for(int i=0;i<3;i++){reversed[2+i]=f[8+i];reversed[8+i]=f[2+i];}
        require(Math.abs(volume(eval(reversed,null))-v)<.001,"orientation independent edge signature");
        require(Math.abs(volume(NativeCadEngine.tessellateStep(args[0]))-v)<.001,"selected fillet survives STEP round trip");
        double[] imported=NativeCadEngine.modelEdges(40,30,20,0,null,new double[0],args[0]);
        require(imported.length>edges.length,"curved B-Rep edges sampled after STEP import");
        double[] other=edge(imported,40,30);
        require(NativeCadEngine.evaluateFeatures(40,30,20,0,null,other,null,args[0]).length>0,"selected fillet on imported STEP");
        require(volume(eval(join(f,edge(edges,40,30)),null))<v,"two ordered independent edge fillets");
        double[] cut={0,1,10,10,20,20,5,0,0,20,1,0,0,0,1,0};
        require(Math.abs(volume(eval(join(f,cut),null))-(v-500))<.01,"cut after edge fillet keeps operation order");
        require(Math.abs(volume(eval(join(cut,f),null))-(v-500))<.01,"fillet after Boolean cut resolves surviving edge");
        double[] changed=f.clone();changed[2]+=2;
        require(eval(changed,null).length==0,"missing edge rejected instead of rounding unrelated edge");
        changed=f.clone();changed[11]=1000;
        require(eval(changed,null).length==0,"impossible radius rejected");
        changed=f.clone();changed[11]=Double.NaN;
        require(eval(changed,null).length==0,"nonfinite radius rejected natively");
        double[] cylinder={1,0,0,10,0,0,20};
        double[] rimSegments=NativeCadEngine.modelEdges(40,30,20,0,cylinder,new double[0],null),rim=null;
        for(int i=0;i<rimSegments.length;i+=16)if(Math.abs(rimSegments[i]-rimSegments[i+6])<1e-5&&Math.abs(rimSegments[i+1]-rimSegments[i+7])<1e-5&&Math.abs(rimSegments[i+2]-rimSegments[i+8])<1e-5){rim=EdgeFillet.create(rimSegments,i,2);break;}
        require(rim!=null,"circular rim identity available");
        double before=volume(NativeCadEngine.evaluateFeatures(40,30,20,0,cylinder,new double[0],null));
        double after=volume(NativeCadEngine.evaluateFeatures(40,30,20,0,cylinder,rim,null));
        require(after<before&&after>before-200,"fillet selected circular rim");
        CadProject p=CadProject.blank();p.featureBase=new double[]{0,0,0,40,30,0,20};p.committedFeatures.add(f);p.committedProfileIds.add("");
        CadProject loaded=CadProject.fromJson(p.toJson());
        require(EdgeFillet.is(loaded.committedFeatures.get(0))&&loaded.committedFeatures.get(0)[11]==3,"schema23 persists selected edge and radius");
        require(CadTimeline.entries(loaded).get(2).title.equals("Zaokrąglenie"),"timeline identifies edge fillet");
        JSONObject bad=p.toJson();bad.getJSONArray("committedFeatures").getJSONArray(0).put(11,-1);
        boolean rejected=false;try{CadProject.fromJson(bad);}catch(JSONException e){rejected=true;}require(rejected,"invalid saved radius rejected");
        System.out.println("ALL EDGE FILLET TESTS PASSED");
    }
}
