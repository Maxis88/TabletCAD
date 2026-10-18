import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/**
 * OCCT results of 0.73 patterns, mirrors and shells, checked against analytic volumes.
 * Runs headless (host JVM with a desktop build of libtabletcad, or app_process on Android).
 */
public final class SolidPatternKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static void close(String what,double actual,double expected,double tolerance){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(what+": "+actual+" != "+expected);checks++;System.out.println("PASS "+what+" = "+actual);}
    static double[] hole(double x,double y,double r){return new double[]{1,1,x,y,x+r,y,20,0,0,20,1,0,0,0,1,0};}
    static double[] boss(double x1,double y1,double x2,double y2,double h){return new double[]{0,0,x1,y1,x2,y2,h,0,0,20,1,0,0,0,1,0};}

    static CadProject plate(double depth) {
        CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=depth;
        p.featureBase=new double[]{0,0,0,100,60,0,depth};BodyGraph.begin(p);return p;
    }
    static void add(CadProject p,double[] f){p.committedFeatures.add(f);p.committedProfileIds.add("");p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);}
    static double[] packed(CadProject p){
        int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] raw=new double[n];int at=0;
        for(double[] f:p.committedFeatures){System.arraycopy(f,0,raw,at,f.length);at+=f.length;}
        BodyGraph.validate(p);return BodyGraph.pack(p,raw);
    }
    static double[] properties(CadProject p) {
        double[] r=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,packed(p),null);
        if(r.length!=7)throw new AssertionError("Evaluation failed: "+NativeCadEngine.lastFailure().getMessage());return r;
    }
    static double volume(CadProject p){return properties(p)[0];}
    static void fails(String what,CadProject p,int index,String fragment) {
        try{NativeCadEngine.evaluateModelChecked(p.width,p.height,p.depth,0,p.featureBase,packed(p),null);}
        catch(NativeCadEngine.KernelException e) {
            if(e.feature!=index)throw new AssertionError(what+": index "+e.feature+" != "+index+" ("+e.reason+")");
            if(!e.reason.contains(fragment))throw new AssertionError(what+": '"+e.reason+"' lacks '"+fragment+"'");
            checks++;System.out.println("PASS "+what+": #"+e.feature+" "+e.reason);return;
        }
        throw new AssertionError(what+": invalid model accepted");
    }
    static CadProject withRecord(CadProject p,double[] f){add(p,f);return p;}
    static double meshVolume(double[] mesh) {
        double v=0;for(int i=0;i+12<mesh.length;i+=13)v+=(mesh[i]*(mesh[i+4]*mesh[i+8]-mesh[i+5]*mesh[i+7])-mesh[i+1]*(mesh[i+3]*mesh[i+8]-mesh[i+5]*mesh[i+6])+mesh[i+2]*(mesh[i+3]*mesh[i+7]-mesh[i+4]*mesh[i+6]))/6;
        return Math.abs(v);
    }
    static CadProject bodies(double[] created,double[] combine) {
        CadProject p=plate(20);p.committedFeatures.add(created);p.committedProfileIds.add("");p.featureBodyTargets.add(0);p.featureBodyOutputs.add(BodyGraph.allocate(p));
        p.committedFeatures.add(combine);p.committedProfileIds.add("");p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);return p;
    }
    static ShellFeature.Face top(double z,int origin){return new ShellFeature.Face(new double[]{50,30,z},new double[]{0,0,1},origin);}

    public static void main(String[] args) {
        final double plate=100*60*20,hole=Math.PI*25*20;
        CadProject p=plate(20);close("plate",volume(p),plate,1e-6);
        add(p,hole(25,30,5));close("plate with one hole",volume(p),plate-hole,1e-6);

        add(p,FeaturePattern.create(FeaturePattern.LINEAR,new int[]{0},new double[3],new double[]{1,0,0},3,25));
        close("linear pattern 3 × 25 mm",volume(p),plate-3*hole,1e-6);
        double[] props=properties(p);check(props[5]==1,"linear pattern keeps one solid");
        close("pattern centroid X shifts to the middle hole",props[2],(plate*50-hole*(25+50+75))/(plate-3*hole),1e-6);

        // Associativity: the source radius drives every instance.
        p.committedFeatures.set(0,hole(25,30,4));close("source edit propagates to copies",volume(p),plate-3*Math.PI*16*20,1e-6);
        p.committedFeatures.set(0,hole(25,30,5));

        // Copies that leave the body are reported on the pattern operation, not silently dropped.
        p.committedFeatures.set(1,FeaturePattern.create(FeaturePattern.LINEAR,new int[]{0},new double[3],new double[]{1,0,0},3,60));
        fails("pattern copy outside the body",p,1,"przecina");

        CadProject c=plate(20);add(c,hole(35,30,5));
        add(c,FeaturePattern.create(FeaturePattern.CIRCULAR,new int[]{0},new double[]{50,30,0},new double[]{0,0,1},4,360));
        close("circular pattern 4 × 360°",volume(c),plate-4*hole,1e-6);
        props=properties(c);close("circular pattern centroid X",props[2],50,1e-6);close("circular pattern centroid Y",props[3],30,1e-6);

        CadProject m=plate(20);add(m,boss(10,10,20,20,8));add(m,FeaturePattern.create(FeaturePattern.MIRROR,new int[]{0},new double[]{50,0,0},new double[]{1,0,0},0,0));
        props=properties(m);close("mirrored boss volume",props[0],plate+2*800,1e-6);close("mirror restores X symmetry",props[2],50,1e-6);
        CadProject mh=plate(20);add(mh,hole(20,30,5));add(mh,FeaturePattern.create(FeaturePattern.MIRROR,new int[]{0},new double[]{50,0,0},new double[]{1,0,0},0,0));
        props=properties(mh);close("mirrored hole volume",props[0],plate-2*hole,1e-6);close("mirrored hole centroid X",props[2],50,1e-6);
        CadProject self=plate(20);add(self,hole(50,30,5));add(self,FeaturePattern.create(FeaturePattern.MIRROR,new int[]{0},new double[]{50,0,0},new double[]{1,0,0},0,0));
        fails("mirror copy coinciding with its source",self,1,"nie przecina");

        // Shell: open top, inward 2 mm.
        CadProject s=plate(20);add(s,ShellFeature.create(Collections.singletonList(top(20,-1)),2,false));
        close("shell open top 2 mm",volume(s),plate-96*56*18,1e-6);
        s.featureBase=new double[]{0,0,0,100,60,0,30};s.depth=30;
        close("shell follows edited base depth",volume(s),100*60*30-96*56*28,1e-6);
        CadProject closed=plate(20);add(closed,ShellFeature.create(new ArrayList<>(),2,false));
        props=properties(closed);close("closed hollow shell",props[0],plate-96*56*16,1e-6);check(props[5]==1,"closed hollow shell is one solid");
        CadProject outside=plate(20);add(outside,ShellFeature.create(Collections.singletonList(top(20,-1)),2,true));
        // Outward walls with rolling-ball (arc) joins on the convex edges below the open top:
        // Minkowski sum of the box with a 2 mm ball, minus the box and the part above the top face.
        double a=100,b=60,h=20,r=2;
        double walls=2*r*(a*b+b*h+h*a)+Math.PI*r*r*(a+b+h)+4.0/3*Math.PI*r*r*r-(a*b*r+Math.PI*r*r/4*(2*a+2*b)+2.0/3*Math.PI*r*r*r);
        close("outward shell walls with arc joins",volume(outside),walls,walls*1e-6);
        CadProject lost=plate(20);add(lost,ShellFeature.create(Collections.singletonList(top(20,7)),2,false));
        fails("shell face with unknown creator",lost,0,"Nie odnaleziono");
        CadProject thick=plate(20);add(thick,ShellFeature.create(Collections.singletonList(top(20,-1)),40,false));
        fails("shell thicker than the part",thick,0,"owłok");
        CadProject drilled=plate(20);add(drilled,hole(25,30,5));add(drilled,ShellFeature.create(Collections.singletonList(top(20,0)),2,false));
        // The hole wall becomes a tube r 5..7 mm joined sharply to the 2 mm bottom wall.
        close("shell of a drilled plate (top face created by the cut)",volume(drilled),plate-hole-(96*56*18-Math.PI*49*18),1e-5);
        // Dedicated Hole feature on the top face (creator -1 = base body).
        double[] up={0,0,1};
        double[] through=HoleFeature.create(new double[]{25,30,20},up,-1,10,true,0,0,0,0,0);
        CadProject hh=plate(20);add(hh,through);close("through hole Ø10",volume(hh),plate-hole,1e-6);
        hh.featureBase=new double[]{0,0,0,100,60,0,30};hh.depth=30;close("through hole follows base depth 20→30",volume(hh),100*60*30-Math.PI*25*30,1e-6);
        double[] blind=HoleFeature.create(new double[]{40,30,20},up,-1,6,false,10,0,0,0,0);
        CadProject hb=plate(20);add(hb,blind);close("blind flat hole Ø6 × 10",volume(hb),plate-Math.PI*9*10,1e-6);
        hb.featureBase=new double[]{0,0,0,100,60,0,30};hb.depth=30;close("blind hole entry follows the moved face",volume(hb),100*60*30-Math.PI*9*10,1e-6);
        double[] drill=HoleFeature.create(new double[]{40,30,20},up,-1,6,false,10,0,0,0,118);
        CadProject hd=plate(20);add(hd,drill);close("blind hole with 118° drill point",volume(hd),plate-HoleFeature.toolVolume(drill,0),1e-6);
        double[] bore=HoleFeature.create(new double[]{50,30,20},up,-1,6.5,true,0,HoleFeature.COUNTERBORE,11,6,0);
        CadProject hc=plate(20);add(hc,bore);close("counterbored hole Ø6,5 / Ø11 × 6",volume(hc),plate-HoleFeature.toolVolume(bore,20),1e-6);
        double[] sink=HoleFeature.create(new double[]{50,30,20},up,-1,6.5,true,0,HoleFeature.COUNTERSINK,12,90,0);
        CadProject hs=plate(20);add(hs,sink);close("countersunk hole Ø6,5 / Ø12 × 90°",volume(hs),plate-HoleFeature.toolVolume(sink,20),1e-6);
        CadProject side=plate(20);add(side,HoleFeature.create(new double[]{100,30,10},new double[]{1,0,0},-1,8,false,15,0,0,0,0));
        close("hole in a side face",volume(side),plate-Math.PI*16*15,1e-6);
        CadProject hp=plate(20);add(hp,through);add(hp,FeaturePattern.create(FeaturePattern.LINEAR,new int[]{0},new double[3],new double[]{1,0,0},3,25));
        close("linear pattern of a hole on a face already trimmed by the source",volume(hp),plate-3*hole,1e-6);
        hp.featureBase=new double[]{0,0,0,100,60,0,30};hp.depth=30;close("patterned holes follow base depth",volume(hp),100*60*30-3*Math.PI*25*30,1e-6);
        CadProject hm=plate(20);add(hm,bore);add(hm,FeaturePattern.create(FeaturePattern.MIRROR,new int[]{0},new double[]{30,0,0},new double[]{1,0,0},0,0));
        close("mirrored counterbored hole",volume(hm),plate-2*HoleFeature.toolVolume(bore,20),1e-6);
        CadProject ht=plate(20);add(ht,HoleFeature.create(new double[]{25,30,20},up,-1,5,true,0,0,0,0,0));
        CadModelResult drilled5=new CadModelResult(NativeCadEngine.evaluateModelChecked(100,60,20,0,ht.featureBase,packed(ht),null));
        check(drilled5.holes.size()==1&&Math.abs(drilled5.holes.get(0).radius-2.5)<1e-9,"kernel reports the Hole bore as a threadable cylinder");
        HoleThread.Cylinder bore5=drilled5.holes.get(0);System.out.println("bore axis "+java.util.Arrays.toString(bore5.axis)+" origin "+java.util.Arrays.toString(bore5.origin)+" "+bore5.min+".."+bore5.max);
        add(ht,HoleThread.create(bore5,HoleThread.nearest(5).get(0),bore5.max-bore5.min,true,false));
        double threaded=volume(ht);check(threaded<plate-Math.PI*6.25*20&&threaded>plate-Math.PI*9*20,"thread cut into a Hole feature bore");
        CadProject hl=plate(20);add(hl,HoleFeature.create(new double[]{25,30,20},up,9,10,true,0,0,0,0,0));fails("hole on a face with unknown creator",hl,0,"Nie odnaleziono ściany otworu");
        CadProject ho=plate(20);add(ho,HoleFeature.create(new double[]{150,30,20},up,-1,10,true,0,0,0,0,0));fails("hole centre outside the face",ho,0,"poza");
        // Shell lineage: shell on a top face first trimmed by a hole still resolves by the base tag.
        CadProject sh=plate(20);add(sh,through);add(sh,ShellFeature.create(Collections.singletonList(top(20,-1)),2,false));
        close("shell resolves a trimmed face through its lineage",volume(sh),plate-hole-(96*56*18-Math.PI*49*18),1e-5);
        // Combine: Boolean between document bodies.
        double[] cube={0,4,10,10,20,20,-10,0,0,25,1,0,0,0,1,0};// new body z 15..25, overlaps the plate by 5 mm
        CadProject cj=bodies(cube,BodyCombine.create(BodyCombine.JOIN,2,false));props=properties(cj);
        close("join bodies",props[0],plate+500,1e-6);check(props[5]==1,"joined bodies form one solid");
        CadProject cc=bodies(cube,BodyCombine.create(BodyCombine.CUT,2,false));close("cut tool body from target",volume(cc),plate-500,1e-6);
        CadProject ck=bodies(cube,BodyCombine.create(BodyCombine.CUT,2,true));props=properties(ck);close("cut keeping the tool",props[0],plate-500+1000,1e-6);check(props[5]==2,"kept tool remains a separate solid");
        CadProject ci=bodies(cube,BodyCombine.create(BodyCombine.INTERSECT,2,false));close("intersect bodies",volume(ci),500,1e-6);
        double[] away={0,4,200,200,210,210,-10,0,0,25,1,0,0,0,1,0};
        fails("cut with a body that does not touch",bodies(away,BodyCombine.create(BodyCombine.CUT,2,false)),1,"nie przecina");
        fails("intersect of disjoint bodies",bodies(away,BodyCombine.create(BodyCombine.INTERSECT,2,false)),1,"wspólnej");
        CadProject after=bodies(cube,BodyCombine.create(BodyCombine.JOIN,2,false));after.committedFeatures.add(hole(50,30,5));after.committedProfileIds.add("");after.featureBodyTargets.add(2);after.featureBodyOutputs.add(2);
        boolean refused=false;try{BodyGraph.validate(after);}catch(IllegalArgumentException e){refused=true;}check(refused,"operations on a consumed body are refused before OCCT");
        // Draft of the +X side face about the bottom plane, pulled along +Z.
        ShellFeature.Face sideX=new ShellFeature.Face(new double[]{100,30,10},new double[]{1,0,0},-1);
        CadProject dr=plate(20);add(dr,DraftFeature.create(Collections.singletonList(sideX),5,new double[3],new double[]{0,0,1}));
        close("draft 5° of one side wall",volume(dr),plate-0.5*20*20*Math.tan(Math.toRadians(5))*60,1e-6);
        dr.committedFeatures.set(0,DraftFeature.withAngle(dr.committedFeatures.get(0),-5));
        close("negative draft adds material",volume(dr),plate+0.5*20*20*Math.tan(Math.toRadians(5))*60,1e-6);
        CadProject drTop=plate(20);fails("draft of a face parallel to the neutral plane",drTop==null?null:withRecord(drTop,DraftFeature.create(Collections.singletonList(top(20,-1)),5,new double[3],new double[]{0,0,1})),0,"równoległa");
        // Move / rotate / scale.
        CadProject mv=plate(20);add(mv,BodyTransform.move(10,0,0));props=properties(mv);close("moved body centroid X",props[2],60,1e-9);close("move keeps volume",props[0],plate,1e-6);
        CadProject rt=plate(20);add(rt,BodyTransform.rotate(new double[3],new double[]{0,0,1},90));props=properties(rt);close("rotated centroid X",props[2],-30,1e-9);close("rotated centroid Y",props[3],50,1e-9);
        CadProject sc=plate(20);add(sc,BodyTransform.scale(new double[3],2));props=properties(sc);close("scaled ×2 volume",props[0],plate*8,1e-5);close("scaled centroid Z",props[4],20,1e-9);
        CadProject mvh=plate(20);add(mvh,BodyTransform.move(0,0,5));add(mvh,hole(25,30,5));
        close("operations after a move apply to the moved body",volume(mvh),plate-Math.PI*25*15,1e-6);

        // Split by the plane X = 40: two bodies, the new one can be modified separately.
        CadProject sp=plate(20);int part=BodyGraph.allocate(sp);add(sp,BodySplit.create(part,new double[]{40,0,0},new double[]{1,0,0}));
        props=properties(sp);close("split keeps total volume",props[0],plate,1e-6);check(props[5]==2,"split creates two solids");
        close("split centroid X",props[2],50,1e-9);
        sp.committedFeatures.add(hole(70,30,5));sp.committedProfileIds.add("");sp.featureBodyTargets.add(part);sp.featureBodyOutputs.add(part);
        close("hole on the new body after split",volume(sp),plate-hole,1e-6);
        CadProject miss=plate(20);int missing=BodyGraph.allocate(miss);add(miss,BodySplit.create(missing,new double[]{200,0,0},new double[]{1,0,0}));
        fails("split plane outside the body",miss,0,"nie przecina");

        // Display-only section: half of the plate behind the plane X = 50.
        CadProject sec=plate(20);double[] sectionPacked=NativeCadEngine.sectionModel(100,60,20,0,sec.featureBase,packed(sec),null,new double[]{50,0,0,1,0,0});
        CadModelResult section=new CadModelResult(sectionPacked);close("section mesh volume behind X=50",meshVolume(section.mesh),60000,1e-6);
        check(section.bodies.values().stream().allMatch(id->id==1),"section faces keep their body identity");
        double[] flipped=NativeCadEngine.sectionModel(100,60,20,0,sec.featureBase,packed(sec),null,new double[]{30,0,0,-1,0,0});
        close("flipped section keeps the other side",meshVolume(new CadModelResult(flipped).mesh),70*60*20,1e-6);
        check(NativeCadEngine.evaluateModel(100,60,20,0,sec.featureBase,packed(sec),null).length>0,"normal evaluation unaffected after a section query");
        System.out.println("PASS SolidPatternKernelProbe: "+checks+" checks");
    }
}
