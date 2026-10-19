import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** OCCT results of 0.74 tools: ellipse profiles, loft, sweep, body patterns, parameters. */
public final class CadToolsKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static void close(String what,double actual,double expected,double tolerance){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(what+": "+actual+" != "+expected);checks++;System.out.println("PASS "+what+" = "+actual);}
    static CadProject plate() {
        CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=20;
        p.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(p);return p;
    }
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] raw=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,raw,at,f.length);at+=f.length;}BodyGraph.validate(p);return BodyGraph.pack(p,raw);}
    static double[] properties(CadProject p){double[] r=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,packed(p),null);if(r.length!=7)throw new AssertionError("Evaluation failed: "+NativeCadEngine.lastFailure().getMessage());return r;}
    static double volume(CadProject p){return properties(p)[0];}
    static void add(CadProject p,double[] f,String ref,int target){p.committedFeatures.add(f);p.committedProfileIds.add(ref);if(target==0){p.featureBodyTargets.add(0);p.featureBodyOutputs.add(BodyGraph.allocate(p));}else{p.featureBodyTargets.add(target);p.featureBodyOutputs.add(target);}}
    /** Stores a saved sketch with the given frame and elements. */
    static void sketch(CadProject p,String id,double[] frame,SketchPrimitive... elements)throws Exception {
        CadProject s=new CadProject();s.sketchId=id;s.sketchName=id;
        s.sketchOriginX=frame[0];s.sketchOriginY=frame[1];s.sketchOriginZ=frame[2];s.sketchUx=frame[3];s.sketchUy=frame[4];s.sketchUz=frame[5];s.sketchVx=frame[6];s.sketchVy=frame[7];s.sketchVz=frame[8];
        s.sketch.addAll(Arrays.asList(elements));p.savedSketches.add(s.captureSketch());
    }
    static SweepLoft.Placed find(CadProject p,String sketch,String id){for(SweepLoft.Placed e:SweepLoft.elements(p))if(e.sketchId.equals(sketch)&&e.shape.id.equals(id))return e;throw new AssertionError("no element "+id);}
    static double[] xy(double z){return new double[]{0,0,z,1,0,0,0,1,0};}

    public static void main(String[] args)throws Exception {
        final double plate=120000;
        // Ellipse as an exact cut and as a boss.
        SketchPrimitive e=SketchPrimitive.ellipse("e",50,30,65,30,8);
        double[] cut=ExtrusionPlan.feature(withFrame(xy(20)),e,"cut",20,0,false,false);
        CadProject ce=plate();add(ce,cut,"",1);close("elliptical through cut a=15 b=8",volume(ce),plate-Math.PI*15*8*20,1e-6);
        SketchPrimitive tilted=SketchPrimitive.ellipse("t",50,30,50+10*Math.cos(.5),30+10*Math.sin(.5),4);
        CadProject ct=plate();add(ct,ExtrusionPlan.feature(withFrame(xy(20)),tilted,"add",5,0,false,false),"",1);close("rotated elliptical boss",volume(ct),plate+Math.PI*10*4*5,1e-6);
        CadProject be=new CadProject();be.emptyDocument=false;be.width=100;be.height=60;be.depth=10;be.featureBase=ExtrusionPlan.base(be,SketchPrimitive.ellipse("b",0,0,20,0,10),10,0,false,false);
        close("elliptical base body",NativeCadEngine.bodyProperties(100,60,10,0,be.featureBase,new double[0],null)[0],Math.PI*20*10*10,1e-6);
        CadProject json=CadProject.fromJson(new JSONObject(ce.toJson().toString()));check(Arrays.equals(json.committedFeatures.get(0),cut),"ellipse feature survives save/load");

        // Ruled loft between a 20×20 and a 10×10 square 30 mm apart: square frustum.
        CadProject l=plate();
        sketch(l,"low",xy(20),new SketchPrimitive("s1","Prostokąt",40,20,60,40));
        sketch(l,"high",xy(50),new SketchPrimitive("s2","Prostokąt",45,25,55,35));
        List<SweepLoft.Placed> sections=Arrays.asList(find(l,"low","s1"),find(l,"high","s2"));
        add(l,SweepLoft.loftRecord(sections,0,true),SweepLoft.loftRef(sections),1);
        close("ruled loft frustum joined on top",volume(l),plate+30/3.0*(400+100+200),1e-6);
        // Associativity: edit the upper square in its saved sketch, the loft follows.
        JSONObject high=l.savedSketches.get(1);high.getJSONArray("entities").put(0,new SketchPrimitive("s2","Prostokąt",40,20,60,40).toJson());
        close("loft follows the edited section",volume(l),plate+30*400,1e-6);
        CadProject lc=plate();sketch(lc,"c0",xy(20),new SketchPrimitive("c","Okrąg",50,30,60,30));sketch(lc,"c1",xy(40),new SketchPrimitive("d","Okrąg",50,30,55,30));
        List<SweepLoft.Placed> cones=Arrays.asList(find(lc,"c0","c"),find(lc,"c1","d"));add(lc,SweepLoft.loftRecord(cones,0,false),SweepLoft.loftRef(cones),1);
        close("smooth loft between circles is a cone frustum",volume(lc),plate+Math.PI*20/3*(100+25+50),1e-3);
        CadProject lost=plate();sketch(lost,"low",xy(20),new SketchPrimitive("s1","Prostokąt",40,20,60,40));sketch(lost,"high",xy(50),new SketchPrimitive("s2","Prostokąt",45,25,55,35));
        List<SweepLoft.Placed> ls=Arrays.asList(find(lost,"low","s1"),find(lost,"high","s2"));add(lost,SweepLoft.loftRecord(ls,0,true),SweepLoft.loftRef(ls),1);
        lost.savedSketches.remove(1);boolean refused=false;try{packed(lost);}catch(IllegalArgumentException ex){refused=ex.getMessage().contains("Nie odnaleziono");}check(refused,"loft with a deleted section is reported, not guessed");

        // Sweep a Ø4 circle along a straight and an L-shaped path (line + quarter arc).
        double[] xz={0,0,0,1,0,0,0,0,1};// path sketch on XZ: u = X, v = Z
        CadProject sw=plate();
        // Profile: circle r 2 in the plane x = 10 centred at (10,30,30); path in the plane y = 30 (u = X, v = Z).
        double[] profileFrame={10,30,30,0,1,0,0,0,1},pathFrame={0,30,0,1,0,0,0,0,1};
        sketch(sw,"profile",profileFrame,new SketchPrimitive("p","Okrąg",0,0,2,0));
        sketch(sw,"path",pathFrame,new SketchPrimitive("l1","Linia",10,30,40,30));
        List<SweepLoft.Placed> path=Collections.singletonList(find(sw,"path","l1"));
        SweepLoft.Placed prof=find(sw,"profile","p");
        add(sw,SweepLoft.sweepRecord(prof,path,4),SweepLoft.sweepRef(prof,path),0);
        close("straight sweep Ø4 × 30 as a new body",volume(sw),plate+Math.PI*4*30,1e-3);
        CadProject sl=plate();
        sketch(sl,"profile",profileFrame,new SketchPrimitive("p","Okrąg",0,0,2,0));
        double r=10;double[] arc={1,30,30,30+r*Math.sin(Math.PI/4),40-r*Math.cos(Math.PI/4),40,40};
        sketch(sl,"path",pathFrame,new SketchPrimitive("l1","Linia",10,30,30,30),new SketchPrimitive("a1",arc,false));
        List<SweepLoft.Placed> lpath=Arrays.asList(find(sl,"path","l1"),find(sl,"path","a1"));SweepLoft.Placed lp=find(sl,"profile","p");
        add(sl,SweepLoft.sweepRecord(lp,lpath,4),SweepLoft.sweepRef(lp,lpath),0);
        double[] sp=properties(sl);close("sweep along line + quarter arc (Pappus)",sp[0],plate+Math.PI*4*(20+Math.PI*r/2),1e-3);check(sp[5]==2,"swept new body is a separate solid");

        // Whole-body patterns.
        CadProject bm=plate();add(bm,SweepLoft.bodyPatternRecord(1,new double[]{120,0,0},new double[]{1,0,0},0,0),"",1);
        double[] bp=properties(bm);close("body mirror doubles the volume",bp[0],2*plate,1e-6);check(bp[5]==2,"mirrored body is disjoint");close("mirror centroid",bp[2],120,1e-9);
        CadProject bl=plate();add(bl,SweepLoft.bodyPatternRecord(2,new double[3],new double[]{0,0,1},3,20),"",1);
        bp=properties(bl);close("stacked linear body pattern fuses touching copies",bp[0],3*plate,1e-6);check(bp[5]==1,"touching copies become one solid");
        CadProject bc=plate();add(bc,SweepLoft.bodyPatternRecord(3,new double[]{-10,0,0},new double[]{0,0,1},4,360),"",1);
        close("circular body pattern of four disjoint copies",volume(bc),4*plate,1e-5);
        CadProject same=plate();add(same,SweepLoft.bodyPatternRecord(1,new double[]{50,0,0},new double[]{1,0,0},0,0),"",1);
        try{properties(same);throw new AssertionError("symmetric mirror accepted");}catch(AssertionError ex){check(ex.getMessage().contains("Evaluation failed"),"mirror onto itself is reported");}

        // User parameters drive a depth and a dimension expression.
        CadProject pp=plate();pp.parameters.add(new UserParameters.Parameter("t","20",true));pp.parameters.add(new UserParameters.Parameter("d","t/2",true));
        double[] hole=ExtrusionPlan.feature(withFrame(xy(20)),new SketchPrimitive("h","Okrąg",50,30,55,30),"cut",20,0,false,false);
        add(pp,hole,"",1);UserParameters.bind(pp,UserParameters.depthKey(0),"t");
        UserParameters.apply(pp);close("parameter-driven cut depth",volume(pp),plate-Math.PI*25*20,1e-6);
        pp.parameters.get(0).expression="10";UserParameters.apply(pp);close("changing a parameter re-drives the depth",volume(pp),plate-Math.PI*25*10,1e-6);
        // Edge projection onto the top face sketch: rectangle outline + Ø10 hole circle; side edges become points.
        CadProject pr=plate();add(pr,ExtrusionPlan.feature(withFrame(xy(20)),new SketchPrimitive("h","Okrąg",30,30,35,30),"cut",20,0,false,false),"",1);
        double[] ed=NativeCadEngine.modelEdges(100,60,20,0,pr.featureBase,packed(pr),null),em=NativeCadEngine.edgeMeasurements(100,60,20,0,pr.featureBase,packed(pr),null);
        CadProject top=withFrame(xy(20));List<Integer> ids=EdgeProjection.inPlane(top,ed);
        EdgeProjection.Result res=EdgeProjection.project(top,ed,em,ids);int lines=0,circles=0;double len=0;
        for(SketchPrimitive q:res.created){if(q.type.equals("Linia")){lines++;len+=Math.hypot(q.x2-q.x1,q.y2-q.y1);}else if(q.type.equals("Okrąg")){circles++;close("projected hole radius",Math.hypot(q.x2-q.x1,q.y2-q.y1),5,1e-9);close("projected hole centre X",q.x1,30,1e-9);}}
        check(lines==4&&circles==1,"top face projects to 4 lines and 1 circle ("+lines+"/"+circles+")");close("projected outline length",len,320,1e-9);
        check(NonlinearConstraints.status(res.created,res.links).full(),"projected geometry is fully constrained");
        Set<Integer> all=new LinkedHashSet<>();for(int i=0;i+15<ed.length;i+=16)all.add((int)ed[i+15]);
        CadProject bottom=withFrame(xy(0));EdgeProjection.Result every=EdgeProjection.project(bottom,ed,em,all);
        check(every.skipped>=4&&every.reason.length()>0,"vertical edges are skipped, not projected to points ("+every.skipped+")");check(every.created.size()==5,"top and bottom outlines coincide in the projection ("+every.created.size()+")");
        CadProject dup=withFrame(xy(20));dup.sketch.addAll(res.created);check(EdgeProjection.project(dup,ed,em,ids).created.isEmpty(),"repeated projection adds no duplicates");
        // Hole placement dimensioned from the top-face edges of the plate.
        CadProject hp=plate();double[] props=NativeCadEngine.edgeMeasurements(100,60,20,0,hp.featureBase,packed(hp),null);
        HolePlacement place=HolePlacement.find(new double[]{20,15,20},new double[]{0,0,1},props);
        check(place!=null&&place.hasB(),"top face offers two perpendicular reference edges");close("distance from edge A (y = 0)",place.distanceA,15,1e-12);close("distance from edge B (x = 0)",place.distanceB,20,1e-12);
        double[] at=place.at(25,10);close("placed X",at[0],10,1e-12);close("placed Y",at[1],25,1e-12);close("placed Z stays on the face",at[2],20,1e-12);
        double[] hr=HoleFeature.create(at,new double[]{0,0,1},-1,6.5,true,0,0,0,0,0);add(hp,hr,"",1);close("hole Ø6.5 at the dimensioned position",volume(hp),plate-Math.PI*3.25*3.25*20,1e-6);
        check(HolePlacement.find(new double[]{50,30,20},new double[]{0,0,1},new double[0])==null,"no straight edge → no edge dimensions offered");
        // Associative placement: centre 20 mm from the right edge and 15 mm from the front edge follows a wider plate.
        CadProject as=plate();double[] asProps=NativeCadEngine.edgeMeasurements(100,60,20,0,as.featureBase,packed(as),null),asFaces=NativeCadEngine.faceAnalysis(100,60,20,0,as.featureBase,packed(as),null);
        double[] centre={80,15,20};HolePlacement ap=HolePlacement.find(centre,new double[]{0,0,1},asProps);check(ap!=null&&ap.hasB(),"two reference edges for the hole");
        double[] refs=HoleAssociation.references(ap,centre,asFaces);check(refs!=null&&!Double.isNaN(refs[5]),"both edges resolve to their adjacent faces");
        add(as,HoleFeature.create(centre,new double[]{0,0,1},-1,6,true,0,0,0,0,0),"",1);as.holeReferences.put(0,refs);
        check(HoleAssociation.update(as,null).isEmpty()&&as.committedFeatures.get(0)[2]==80,"unchanged model keeps the centre");
        as.featureBase[3]=120;as.width=120;List<String> lostRefs=HoleAssociation.update(as,null);check(lostRefs.isEmpty(),"reference faces found after the change");
        close("centre stays 20 mm from the moved right edge",as.committedFeatures.get(0)[2],100,1e-9);close("and 15 mm from the front edge",as.committedFeatures.get(0)[3],15,1e-9);
        CadProject asj=CadProject.fromJson(new JSONObject(as.toJson().toString()));check(asj.holeReferences.containsKey(0)&&asj.holeReferences.get(0).length==10,"hole references persist");
        as.featureBase[4]=80;as.height=80;HoleAssociation.update(as,null);close("front-edge distance unaffected by a deeper plate",as.committedFeatures.get(0)[3],15,1e-9);
        CadProject sw2=CadProject.fromJson(new JSONObject(as.toJson().toString()));add(sw2,ExtrusionPlan.feature(withFrame(xy(20)),new SketchPrimitive("x","Okrąg",10,10,12,10),"cut",20,0,false,false),"",1);FeatureHistory.swap(sw2,0);
        check(sw2.holeReferences.containsKey(1)&&!sw2.holeReferences.containsKey(0),"hole references move with the hole when reordered");
        FeatureRemoval.apply(sw2,FeatureRemoval.plan(sw2,0));check(sw2.holeReferences.containsKey(0),"and shift when an earlier operation is removed");
        System.out.println("PASS CadToolsKernelProbe: "+checks+" checks");
    }
    static CadProject withFrame(double[] f){CadProject p=new CadProject();p.sketchOriginX=f[0];p.sketchOriginY=f[1];p.sketchOriginZ=f[2];p.sketchUx=f[3];p.sketchUy=f[4];p.sketchUz=f[5];p.sketchVx=f[6];p.sketchVy=f[7];p.sketchVz=f[8];return p;}
}
