import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** 0.77 analysis: per-body inertia, per-body densities and minimum distance / overlap between bodies. */
public final class AnalysisKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static void close(String what,double actual,double expected,double tolerance){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(what+": "+actual+" != "+expected);checks++;System.out.println("PASS "+what+" = "+actual);}
    static CadProject plate(){CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=20;p.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(p);return p;}
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] raw=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,raw,at,f.length);at+=f.length;}BodyGraph.validate(p);return BodyGraph.pack(p,raw);}
    static CadProject at(double z){CadProject p=new CadProject();p.sketchOriginZ=z;return p;}
    public static void main(String[] args)throws Exception {
        CadProject p=plate();
        List<BodyAnalysis.Body> bodies=BodyAnalysis.read(p,NativeCadEngine.bodyInertia(100,60,20,0,p.featureBase,packed(p),null));
        check(bodies.size()==1,"one body");BodyAnalysis.Body b=bodies.get(0);
        close("plate volume",b.volume,120000,1e-6);close("centroid X",b.centroid[0],50,1e-9);close("centroid Z",b.centroid[2],10,1e-9);
        close("Ixx = V(b²+c²)/12",b.tensor[0],120000*(3600+400)/12.0,1e-3);close("Iyy",b.tensor[1],120000*(10000+400)/12.0,1e-3);close("Izz",b.tensor[2],120000*(10000+3600)/12.0,1e-3);
        close("product Ixy of a box is zero",b.tensor[3],0,1e-3);close("smallest principal moment = Ixx",b.principal[0],b.tensor[0],1e-3);close("surface area",b.area,2*(6000+2000+1200),1e-6);
        check(!b.hasMass(),"no density → no mass");
        p.materialDensityKgM3=7850;b=BodyAnalysis.read(p,NativeCadEngine.bodyInertia(100,60,20,0,p.featureBase,packed(p),null)).get(0);
        close("steel plate mass [kg]",b.mass(),120000*7850e-9,1e-12);close("Izz [kg·mm²]",b.kgmm2(b.tensor[2]),120000*13600/12.0*7850e-9,1e-9);
        // Second body 10 mm above the plate, aluminium.
        double[] box=ExtrusionPlan.feature(at(30),new SketchPrimitive("n","Prostokąt",0,0,10,10),"new",5,0,false,false);
        p.committedFeatures.add(box);p.committedProfileIds.add("n");p.featureBodyTargets.add(0);int second=BodyGraph.allocate(p);p.featureBodyOutputs.add(second);
        p.bodyDensities.put(second,2700.0);
        bodies=BodyAnalysis.read(p,NativeCadEngine.bodyInertia(100,60,20,0,p.featureBase,packed(p),null));check(bodies.size()==2,"two bodies analysed separately");
        BodyAnalysis.Body upper=bodies.get(0).id==second?bodies.get(0):bodies.get(1);close("aluminium box mass uses its own density",upper.mass(),500*2700e-9,1e-15);close("box centroid Z",upper.centroid[2],32.5,1e-9);
        double[] c=BodyAnalysis.centre(bodies);check(c[3]==1,"mass-weighted centre when all bodies have a density");
        close("combined centre Z",c[2],(120000*7850e-9*10+500*2700e-9*32.5)/(120000*7850e-9+500*2700e-9),1e-9);
        CadProject saved=CadProject.fromJson(new JSONObject(p.toJson().toString()));check(saved.bodyDensities.get(second)==2700.0,"body densities persist");
        JSONObject bad=new JSONObject(p.toJson().toString());bad.getJSONObject("bodyDensities").put(String.valueOf(second),-1);boolean refused=false;try{CadProject.fromJson(bad);}catch(JSONException e){refused=true;}check(refused,"negative body density refused on load");
        double[] d=NativeCadEngine.bodyDistance(100,60,20,0,p.featureBase,packed(p),null,1,second);
        check(d.length==8,"distance query result");close("minimum distance plate ↔ box",d[0],10,1e-9);close("closest point on the plate lies on its top",d[3],20,1e-9);close("no overlap",d[7],0,1e-9);
        CadProject o=plate();double[] inside=ExtrusionPlan.feature(at(15),new SketchPrimitive("n","Prostokąt",0,0,10,10),"new",10,0,false,false);
        o.committedFeatures.add(inside);o.committedProfileIds.add("n");o.featureBodyTargets.add(0);int third=BodyGraph.allocate(o);o.featureBodyOutputs.add(third);
        d=NativeCadEngine.bodyDistance(100,60,20,0,o.featureBase,packed(o),null,1,third);close("interfering bodies touch (distance 0)",d[0],0,1e-9);close("overlap volume 10×10×5",d[7],500,1e-6);
        check(NativeCadEngine.bodyDistance(100,60,20,0,o.featureBase,packed(o),null,1,99).length==0,"unknown body reported");
        check(Math.abs(MaterialDensity.VALUES[0]-7850)<1e-9&&MaterialDensity.NAMES.length==MaterialDensity.VALUES.length,"material catalog");
        System.out.println("PASS AnalysisKernelProbe: "+checks+" checks");
    }
}
