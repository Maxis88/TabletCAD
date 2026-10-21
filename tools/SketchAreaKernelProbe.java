import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Areas bounded by overlapping profiles and lines (Fusion-style regions) and external threads on shafts. */
public final class SketchAreaKernelProbe {
    static int checks;
    static void check(boolean ok,String t){if(!ok)throw new AssertionError(t);checks++;System.out.println("PASS "+t);}
    static void close(String what,double a,double e,double tol){if(Math.abs(a-e)>tol)throw new AssertionError(what+": "+a+" != "+e);checks++;System.out.println("PASS "+what+" = "+a);}
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] r=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,r,at,f.length);at+=f.length;}return r;}
    static double[] props(CadProject p){return NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,BodyGraph.pack(p,packed(p)),null);}
    static double volume(CadProject p){double[] r=props(p);if(r.length!=7)throw new AssertionError("Evaluation failed: "+NativeCadEngine.lastFailure().getMessage());return r[0];}
    static CadProject blank(List<SketchPrimitive> sketch){CadProject p=CadProject.blank();p.setPrincipalSketchFrame("XY");p.sketch.addAll(sketch);BodyGraph.begin(p);return p;}
    static List<SketchArea.Area> areas(List<SketchPrimitive> sketch){List<SketchPrimitive> e=SketchArea.elements(sketch);return SketchArea.areas(NativeCadEngine.sketchAreas(SketchArea.pack(e),e.size()));}
    static void base(CadProject p,double depth,double[]... seeds){List<SketchPrimitive> e=SketchArea.elements(p.sketch);p.featureBase=SketchArea.base(p,e,Arrays.asList(seeds),depth,0,false,false);p.featureBaseProfileId=SketchArea.ref(e);}
    public static void main(String[] args)throws Exception {
        // Two overlapping circles r = 10, centres 10 mm apart: three areas; the lens is exact.
        List<SketchPrimitive> circles=Arrays.asList(new SketchPrimitive("a","Okrąg",0,0,10,0),new SketchPrimitive("b","Okrąg",10,0,20,0));
        List<SketchArea.Area> found=areas(circles);check(found.size()==3,"two overlapping circles make three areas: "+found.size());
        double lens=200*Math.acos(.5)-5*Math.sqrt(300);
        close("lens area from the kernel",SketchArea.at(found,5,0).area,lens,1e-6);
        close("crescent area",SketchArea.at(found,-5,0).area,Math.PI*100-lens,1e-6);
        CadProject l=blank(circles);base(l,10,new double[]{5,0});close("lens extruded 10 mm (exact arcs)",volume(l),lens*10,1e-6);
        CadProject both=blank(circles);base(both,10,new double[]{5,0},new double[]{-5,0});close("lens and crescent together = whole circle",volume(both),Math.PI*100*10,1e-6);

        // A line across a rectangle splits it in two; both halves together form one solid.
        List<SketchPrimitive> split=new ArrayList<>(Arrays.asList(new SketchPrimitive("r","Prostokąt",0,0,100,60),new SketchPrimitive("l","Linia",50,-10,50,70)));
        check(areas(split).size()==2,"line across a rectangle gives two areas");
        CadProject half=blank(split);base(half,10,new double[]{25,30});close("left half extruded",volume(half),3000*10,1e-6);
        CadProject reopened=CadProject.fromJson(new org.json.JSONObject(half.toJson().toString()));close("area after reopening",volume(reopened),30000,1e-6);
        ProfileDependencies.propagate(half,"l",new SketchPrimitive("l","Linia",40,-10,40,70));close("area follows the moved line",volume(half),40*60*10,1e-6);
        check(ProfileDependencies.uses(half,"l")&&ProfileDependencies.uses(half,"r"),"line and rectangle are reported as used");
        CadProject halves=blank(split);base(halves,10,new double[]{25,30},new double[]{75,30});double[] p2=props(halves);
        check(p2.length==7&&Math.abs(p2[0]-60000)<1e-6&&p2[5]==1,"two adjacent halves become one solid ("+(p2.length==7?p2[0]+" / solids "+p2[5]:"failed")+")");
        List<SketchPrimitive> inner=Arrays.asList(new SketchPrimitive("r","Prostokąt",0,0,100,60),new SketchPrimitive("l","Linia",30,0,30,60));
        check(areas(inner).size()==2,"line touching both edges splits the rectangle");
        List<SketchPrimitive> dangling=Arrays.asList(new SketchPrimitive("r","Prostokąt",0,0,100,60),new SketchPrimitive("l","Linia",30,0,30,30));
        check(areas(dangling).size()==1,"a line ending inside does not split");

        // Cut a lens into a plate (sketch on the top face).
        CadProject plate=new CadProject();plate.emptyDocument=false;plate.width=100;plate.height=60;plate.depth=20;plate.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(plate);plate.sketchOriginZ=20;
        plate.sketch.add(new SketchPrimitive("a","Okrąg",40,30,50,30));plate.sketch.add(new SketchPrimitive("b","Okrąg",50,30,60,30));
        List<SketchPrimitive> e=SketchArea.elements(plate.sketch);
        plate.committedFeatures.add(SketchArea.record(plate,e,Collections.singletonList(new double[]{45,30}),"cut",5,0,false,false));plate.committedProfileIds.add(SketchArea.ref(e));plate.featureBodyTargets.add(1);plate.featureBodyOutputs.add(1);
        close("lens cut 5 mm into the plate",volume(plate),120000-lens*5,1e-6);
        // A seed that ends outside every area is reported.
        ProfileDependencies.propagate(plate,"b",new SketchPrimitive("b","Okrąg",80,30,90,30));ProfileDependencies.propagate(plate,"a",new SketchPrimitive("a","Okrąg",85,30,95,30));
        check(props(plate).length!=7,"lost area is reported instead of guessed: "+NativeCadEngine.lastFailure().getMessage());

        // External thread M8 × 1.25 on an Ø8 shaft standing on a block.
        double[] shaft={1,0,15,15,19,15,20,0,0,10,1,0,0,0,1,0};
        CadModelResult r=new CadModelResult(NativeCadEngine.evaluateModel(30,30,10,0,null,shaft,null));
        HoleThread.Cylinder c=null;for(HoleThread.Cylinder x:r.cylinders)if(x.external)c=x;
        check(c!=null&&Math.abs(c.radius-4)<1e-6&&r.holes.isEmpty(),"kernel reports the shaft as an external cylinder (not a hole)");
        List<HoleThread.Preset> presets=HoleThread.nearestExternal(8);check(!presets.isEmpty()&&presets.get(0).diameter==8&&presets.get(0).pitch==1.25,"Ø8 shaft offers M8 × 1.25 first");
        CadProject t=new CadProject();t.width=30;t.height=30;t.depth=10;t.committedFeatures.add(shaft);t.committedProfileIds.add("");BodyGraph.begin(t);
        double[] thread=HoleThread.create(c,presets.get(0),15,true,false);check(HoleThread.external(thread)&&Math.abs(thread[9]-4)<1e-9,"external thread keeps the shaft as major diameter");
        t.committedFeatures.add(thread);t.committedProfileIds.add("");BodyGraph.reconcile(t);
        double before=NativeCadEngine.bodyProperties(30,30,10,0,null,shaft,null)[0];double[] after=NativeCadEngine.bodyProperties(30,30,10,0,null,BodyGraph.pack(t,packed(t),true),null);
        check(after.length==7&&after[0]<before-15&&after[5]==1,"helical groove cut into the shaft ("+(after.length==7?before-after[0]+" mm³ removed":NativeCadEngine.lastFailure().getMessage())+")");
        check(after[0]>before-Math.PI*(16-Math.pow(4-HoleThread.EXTERNAL_DEPTH*1.25,2))*15,"groove removes less than the whole thread ring");
        check(HoleThread.threaded(c,t.committedFeatures),"threaded shaft is recognised (no second thread)");
        check(CadProject.fromJson(t.toJson()).committedFeatures.get(1)[13]==1,"external flag persists");
        System.out.println("PASS SketchAreaKernelProbe: "+checks+" checks");
    }
}
