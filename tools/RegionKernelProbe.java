import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Area between nested profiles (frame between squares, plate with holes): picking, exact B-Rep volumes, edits, saving. */
public final class RegionKernelProbe {
    static int checks;
    static void check(boolean ok,String t){if(!ok)throw new AssertionError(t);checks++;System.out.println("PASS "+t);}
    static void close(String what,double a,double e,double tol){if(Math.abs(a-e)>tol)throw new AssertionError(what+": "+a+" != "+e);checks++;System.out.println("PASS "+what+" = "+a);}
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] r=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,r,at,f.length);at+=f.length;}return r;}
    static double volume(CadProject p){double[] r=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,BodyGraph.pack(p,packed(p)),null);if(r.length!=7)throw new AssertionError("Evaluation failed: "+NativeCadEngine.lastFailure().getMessage());return r[0];}
    static CadProject plate(){CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=20;p.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(p);p.sketchOriginZ=20;return p;}
    static SketchPrimitive rect(String id,double a,double b,double c,double d){return new SketchPrimitive(id,"Prostokąt",a,b,c,d);}
    static SketchPrimitive circle(String id,double x,double y,double r){return new SketchPrimitive(id,"Okrąg",x,y,x+r,y);}
    static void add(CadProject p,String ref,String op,double depth){
        p.committedFeatures.add(SketchRegion.record(p,SketchRegion.resolve(p.sketch,ref),op,depth,0,false,false));p.committedProfileIds.add(ref);p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);}
    public static void main(String[] args)throws Exception {
        // Picking: the frame between two squares is one region, the inner square another.
        List<SketchPrimitive> squares=Arrays.asList(rect("o",0,0,100,100),rect("i",30,30,70,70));
        check("i".equals(SketchRegion.pick(squares,50,50)),"tap inside the inner square picks the inner square");
        String ring=SketchRegion.pick(squares,10,10);check("region:o|i".equals(ring),"tap between the squares picks the frame: "+ring);
        check(SketchRegion.pick(squares,150,150)==null,"tap outside every profile picks nothing");
        check(Collections.singletonList("o").equals(SketchRegion.merge(Arrays.asList(ring,"i"))),"frame and inner square together are the whole outer square");
        // Three nested levels: the middle frame has one island; the outer frame only the middle square as island.
        List<SketchPrimitive> nested=Arrays.asList(rect("a",0,0,100,100),rect("b",20,20,80,80),rect("c",40,40,60,60));
        check("region:a|b".equals(SketchRegion.pick(nested,10,10)),"outer frame ignores islands of islands");
        check("region:b|c".equals(SketchRegion.pick(nested,30,30)),"middle frame");
        check("c".equals(SketchRegion.pick(nested,50,50)),"innermost square");
        check(Arrays.asList("region:a|c").equals(SketchRegion.merge(Arrays.asList("region:a|b","region:b|c"))),"outer and middle frames merge into one region with the inner hole");
        List<SketchPrimitive> construction=Arrays.asList(rect("o",0,0,100,100),rect("h",30,30,70,70).withConstruction(true));
        check("o".equals(SketchRegion.pick(construction,10,10)),"construction profiles are no islands");

        // Empty document: the first solid is the frame (placed base). 100² − 40² = 8400 mm² × 10 mm.
        CadProject e=CadProject.blank();e.setPrincipalSketchFrame("XY");e.sketch.addAll(squares);BodyGraph.begin(e);
        e.featureBase=SketchRegion.base(e,SketchRegion.resolve(e.sketch,ring),10,0,false,false);e.featureBaseProfileId=ring;
        close("frame between squares as the first solid",volume(e),8400*10,1e-6);
        CadProject reopened=CadProject.fromJson(new org.json.JSONObject(e.toJson().toString()));close("first-solid frame after reopening",volume(reopened),84000,1e-6);
        ProfileDependencies.propagate(e,"i",rect("i",20,20,80,80));close("base frame follows the edited inner square",volume(e),(10000-3600)*10,1e-6);
        check(e.featureBaseProfileId.equals(ring),"region reference kept after the edit");
        double[] symmetricBase=SketchRegion.base(e,SketchRegion.resolve(squares,ring),6,4,true,false);CadProject s2=CadProject.blank();s2.setPrincipalSketchFrame("XY");s2.featureBase=symmetricBase;s2.featureBaseProfileId=ring;
        close("two-direction frame (6 + 4 mm)",volume(s2),84000,1e-6);

        // Exact circle hole in the first solid: (100·60 − π·5²)·20.
        CadProject h=CadProject.blank();h.setPrincipalSketchFrame("XY");h.sketch.add(rect("r",0,0,100,60));h.sketch.add(circle("c",50,30,5));
        String plateRef=SketchRegion.pick(h.sketch,5,5);check("region:r|c".equals(plateRef),"plate with a hole is one region");
        h.featureBase=SketchRegion.base(h,SketchRegion.resolve(h.sketch,plateRef),20,0,false,false);h.featureBaseProfileId=plateRef;
        close("plate with an exact Ø10 hole",volume(h),(6000-Math.PI*25)*20,1e-6);

        // Join and cut the frame on an existing plate (sketch on the top face).
        CadProject j=plate();j.sketch.add(rect("o",10,10,90,50));j.sketch.add(rect("i",30,20,70,40));String frame=SketchRegion.pick(j.sketch,15,15);
        add(j,frame,"join",5);close("joined frame adds (80·40 − 40·20)·5",volume(j),120000+12000,1e-6);
        ProfileDependencies.propagate(j,"i",rect("i",20,20,80,40));close("joined frame follows the edited island",volume(j),120000+(3200-1200)*5,1e-6);
        CadProject js=CadProject.fromJson(new org.json.JSONObject(j.toJson().toString()));close("joined frame after reopening",volume(js),130000,1e-6);
        check(ProfileDependencies.uses(j,"i")&&ProfileDependencies.uses(j,"o"),"both profiles are reported as used (no silent deletion)");
        CadProject c=plate();c.sketch.add(rect("o",10,10,90,50));c.sketch.add(rect("i",30,20,70,40));add(c,SketchRegion.pick(c.sketch,15,15),"cut",5);
        close("cut frame leaves the island standing",volume(c),120000-12000,1e-6);

        // Two islands, clockwise polygon island and an ellipse island stay exact.
        CadProject t=plate();t.sketch.add(rect("o",0,0,100,60));t.sketch.add(circle("a",25,30,8));t.sketch.add(circle("b",75,30,8));
        String two=SketchRegion.pick(t.sketch,50,5);check(SketchRegion.islands(two).size()==2,"two holes in one region");
        add(t,two,"cut",4);close("cut around two round islands",volume(t),120000-(6000-2*Math.PI*64)*4,1e-6);
        CadProject w=plate();w.sketch.add(rect("o",0,0,100,60));w.sketch.add(new SketchPrimitive("p",new double[]{40,20,40,40,60,40,60,20}));
        add(w,SketchRegion.pick(w.sketch,5,5),"join",3);close("clockwise polygon island",volume(w),120000+(6000-400)*3,1e-6);
        CadProject el=plate();el.sketch.add(rect("o",0,0,100,60));el.sketch.add(SketchPrimitive.ellipse("e",50,30,70,30,10));
        add(el,SketchRegion.pick(el.sketch,5,5),"cut",2);close("exact ellipse island",volume(el),120000-(6000-Math.PI*20*10)*2,1e-6);

        // Island touching nothing outside: an island outside the outer boundary is never picked.
        List<SketchPrimitive> apart=Arrays.asList(rect("o",0,0,50,50),rect("x",60,0,90,30));check("o".equals(SketchRegion.pick(apart,10,10)),"profiles beside the outer one are no islands");
        // Display option: packing without threads equals the normal packing when there are none.
        check(Arrays.equals(BodyGraph.pack(j,packed(j),false),BodyGraph.pack(j,packed(j),true)),"thread display option leaves other operations unchanged");
        System.out.println("PASS RegionKernelProbe: "+checks+" checks");
    }
}
