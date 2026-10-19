import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Records, placement math, routing and persistence of 0.73 patterns and shells (no OCCT). */
public final class SolidPatternHostTest {
    interface Work{void run()throws Exception;}
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static void near(double a,double b,String label){check(Math.abs(a-b)<1e-9,label+" ("+a+")");}
    static void rejects(Work work,String label)throws Exception{try{work.run();}catch(IllegalArgumentException|JSONException expected){check(true,label+" → "+expected.getMessage());return;}throw new AssertionError(label);}
    static final double[] HOLE={1,1,25,30,30,30,20,0,0,20,1,0,0,0,1,0};
    static final double[] BOSS={0,0,0,0,10,10,8,0,0,20,1,0,0,0,1,0};
    static final double[] FILLET={-1,0,0,0,0,0,0,10,0,0,20,3,0,0,0,0};

    static CadProject plate() {
        CadProject p=new CadProject();p.emptyDocument=false;
        p.featureBase=new double[]{0,0,0,100,60,0,20};
        BodyGraph.begin(p);p.committedFeatures.add(HOLE.clone());p.committedProfileIds.add("");p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);return p;
    }
    static double[] seg(double[] packed,int offset,int length){return Arrays.copyOfRange(packed,offset,offset+length);}

    public static void main(String[] args)throws Exception {
        // Linear placement is an exact translation of the profile frame.
        double[] linear=FeaturePattern.create(FeaturePattern.LINEAR,new int[]{0},new double[]{0,0,0},new double[]{2,0,0},3,25);
        near(linear[6],1,"direction is normalised");check(linear.length==17&&linear[16]==0,"single source index stored after prefix");
        List<double[]> features=new ArrayList<>();features.add(HOLE.clone());features.add(linear);
        double[] expanded=FeaturePattern.expand(features,1);check(expanded.length==32,"three instances give two additional copies");
        near(expanded[7],25,"first copy origin X");near(expanded[16+7],50,"second copy origin X");
        check(Arrays.equals(seg(expanded,0,7),seg(HOLE,0,7)),"profile, flags and depth unchanged by translation");
        near(expanded[10],1,"U unchanged");near(expanded[14],1,"V unchanged");

        // Circular: 4 × 360° about Z through (50,30,0) moves the hole centre to exact quarter positions.
        double[] circular=FeaturePattern.create(FeaturePattern.CIRCULAR,new int[]{0},new double[]{50,30,0},new double[]{0,0,1},4,360);
        features.set(1,circular);expanded=FeaturePattern.expand(features,1);check(expanded.length==48,"four instances give three copies");
        // frame origin (0,0,20) rotates 90° about (50,30): (50+30, 30-50) = (80,-20)
        near(expanded[7],80,"90° copy origin X");near(expanded[8],-20,"90° copy origin Y");near(expanded[9],20,"rotation keeps Z");
        near(expanded[10],0,"U rotated to Y (x)");near(expanded[11],1,"U rotated to Y (y)");near(expanded[13],-1,"V rotated to -X");
        double[] partial=FeaturePattern.create(FeaturePattern.CIRCULAR,new int[]{0},new double[]{0,0,0},new double[]{0,0,1},3,90);
        features.set(1,partial);expanded=FeaturePattern.expand(features,1);
        near(expanded[10],Math.cos(Math.PI/4),"partial angle spacing 45° (U.x)");near(expanded[16+11],1,"last instance at the full 90° (U.y)");

        // Mirror about the YZ plane at x=50 reflects the frame and negates depth (handedness).
        double[] mirror=FeaturePattern.create(FeaturePattern.MIRROR,new int[]{0},new double[]{50,0,0},new double[]{1,0,0},0,0);
        features.set(1,mirror);expanded=FeaturePattern.expand(features,1);check(expanded.length==16,"mirror gives one copy");
        near(expanded[7],100,"mirrored origin X");near(expanded[10],-1,"mirrored U");near(expanded[14],1,"V unchanged by YZ mirror");
        near(expanded[6],-HOLE[6],"reflection negates extrusion depth");
        // mapped profile point (u=25,v=30): original world X 25 → mirrored 100-25 = 75
        near(expanded[7]+expanded[10]*25,75,"profile point maps to its mirror image");
        // Mirror of a right-hand thread becomes left-hand.
        double[] thread={-8,1,25,30,20,0,0,-1,2.5,3,1,20,20,0,0,0};HoleThread.validate(thread);
        features.set(0,thread);expanded=FeaturePattern.expand(features,1);near(expanded[1],-1,"mirrored thread handedness");near(expanded[2],75,"mirrored thread entry");near(expanded[7],-1,"thread axis unchanged");
        features.set(0,HOLE.clone());

        // Revolution: reflection negates the angle, axis stays in local coordinates.
        SketchPrimitive ring=new SketchPrimitive("ring","Prostokąt",10,0,14,8);
        CadProject rp=new CadProject();double[] revolve=Revolve.create(ring,rp,new double[]{0,0,0,1},270,false);
        features.set(0,revolve);expanded=FeaturePattern.expand(features,1);near(expanded[6],-270,"mirrored revolution angle");check(Revolve.is(expanded),"copy is still a revolution");Revolve.validate(expanded);
        features.set(0,HOLE.clone());

        // Ineligible sources are rejected with reasons instead of being guessed.
        rejects(()->FeaturePattern.validateSources(Arrays.asList(FILLET,FeaturePattern.create(FeaturePattern.LINEAR,new int[]{0},new double[3],new double[]{1,0,0},2,5)),1),"edge fillet cannot be patterned");
        double[] newBody=BOSS.clone();newBody[1]=4;rejects(()->FeaturePattern.validateSources(Arrays.asList(newBody,FeaturePattern.create(FeaturePattern.LINEAR,new int[]{0},new double[3],new double[]{1,0,0},2,5)),1),"new-body source rejected");
        rejects(()->FeaturePattern.validateSources(Arrays.asList(HOLE,FeaturePattern.create(FeaturePattern.LINEAR,new int[]{1},new double[3],new double[]{1,0,0},2,5)),1),"pattern cannot reference itself or later operations");
        rejects(()->FeaturePattern.create(FeaturePattern.LINEAR,new int[]{0},new double[3],new double[]{1,0,0},1,5),"count below two rejected");
        rejects(()->FeaturePattern.create(FeaturePattern.LINEAR,new int[]{0},new double[3],new double[]{1,0,0},101,5),"count above limit rejected");
        rejects(()->FeaturePattern.create(FeaturePattern.CIRCULAR,new int[]{0},new double[3],new double[]{0,0,1},4,400),"angle above 360 rejected");
        rejects(()->FeaturePattern.create(FeaturePattern.MIRROR,new int[]{0},new double[3],new double[]{0,0,0},0,0),"zero mirror normal rejected");
        rejects(()->FeaturePattern.validateSources(Arrays.asList(HOLE,linear,FeaturePattern.create(FeaturePattern.LINEAR,new int[]{1},new double[3],new double[]{1,0,0},2,5)),2),"pattern of a pattern rejected");
        double[] corrupt=linear.clone();corrupt[12]=1;rejects(()->FeaturePattern.validate(corrupt),"reserved fields enforced");

        // Routing: pattern is one -9 record on the source body, expanded from the CURRENT source.
        CadProject p=plate();p.committedFeatures.add(FeaturePattern.create(FeaturePattern.LINEAR,new int[]{0},new double[3],new double[]{1,0,0},3,25));
        p.committedProfileIds.add("");p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);BodyGraph.validate(p);
        double[] raw=new double[0];for(double[] f:p.committedFeatures){double[] n=Arrays.copyOf(raw,raw.length+f.length);System.arraycopy(f,0,n,raw.length,f.length);raw=n;}
        double[] packed=BodyGraph.pack(p,raw);
        check(packed[0]==-9&&packed[16]==1,"source keeps its own routed record");
        int second=16+16;check(packed[second]==-9&&packed[second+1]==1&&packed[second+2]==1&&packed[second+3]==32,"pattern packed as one routed record with two copies");
        near(packed[second+16+7],25,"packed copy placement");
        p.committedFeatures.get(0)[2]=35;p.committedFeatures.get(0)[4]=35;// edit the source
        packed=BodyGraph.pack(p,concat(p.committedFeatures));near(packed[second+16+2],35,"copies follow source edits (associative)");

        // Persistence: schema 36 round trip keeps the record; corrupt references are refused on load.
        String text=p.toJson().toString();CadProject loaded=CadProject.fromJson(new JSONObject(text));
        check(new JSONObject(text).getInt("schema")==CadProject.CURRENT_SCHEMA&&CadProject.CURRENT_SCHEMA>=36,"documents are written with the current schema (≥ 36)");
        check(Arrays.equals(loaded.committedFeatures.get(1),p.committedFeatures.get(1)),"pattern survives save/load");
        JSONObject broken=new JSONObject(text);broken.getJSONArray("committedFeatures").getJSONArray(1).put(16,5);
        rejects(()->CadProject.fromJson(broken),"load rejects pattern referencing missing operation");
        CadProject legacy=new CadProject();legacy.emptyDocument=false;legacy.committedFeatures.add(HOLE.clone());legacy.committedProfileIds.add("");
        legacy.committedFeatures.add(FeaturePattern.create(FeaturePattern.LINEAR,new int[]{0},new double[3],new double[]{1,0,0},2,5));legacy.committedProfileIds.add("");
        rejects(()->BodyGraph.pack(legacy,concat(legacy.committedFeatures)),"pattern outside body graph refused");
        check(FeaturePattern.dependents(p.committedFeatures,0).equals(Collections.singletonList(1)),"dependents of a source are listed");

        // Shell record and routing.
        List<ShellFeature.Face> faces=new ArrayList<>();faces.add(new ShellFeature.Face(new double[]{50,30,20},new double[]{0,0,3},-1));
        double[] shell=ShellFeature.create(faces,2.5,false);check(shell.length==23&&shell[1]==1&&shell[2]==2.5,"shell record layout");near(shell[16+5],1,"stored normal normalised");near(shell[22],-1,"origin tag stored");
        rejects(()->ShellFeature.create(faces,0,false),"zero thickness rejected");
        List<ShellFeature.Face> twice=new ArrayList<>(faces);twice.add(faces.get(0));rejects(()->ShellFeature.create(twice,1,false),"same face twice rejected");
        check(ShellFeature.create(new ArrayList<>(),1,true).length==16,"closed hollow shell without removed faces");
        CadProject s=plate();s.committedFeatures.add(shell);s.committedProfileIds.add("");s.featureBodyTargets.add(1);s.featureBodyOutputs.add(1);BodyGraph.validate(s);
        packed=BodyGraph.pack(s,concat(s.committedFeatures));check(packed[32]==-9&&packed[35]==23&&packed[48]==-12,"shell routed with its face table");
        CadProject sl=CadProject.fromJson(new JSONObject(s.toJson().toString()));check(Arrays.equals(sl.committedFeatures.get(1),shell),"shell survives save/load");
        double[] badShell=shell.clone();badShell[3]=2;rejects(()->ShellFeature.validate(badShell),"invalid shell side rejected");
        CadProject wrongRoute=plate();wrongRoute.committedFeatures.add(shell);wrongRoute.committedProfileIds.add("");wrongRoute.featureBodyTargets.add(0);wrongRoute.featureBodyOutputs.add(5);wrongRoute.bodyUuids.put(5,UUID.randomUUID().toString());wrongRoute.nextBodyIndex=6;
        rejects(()->BodyGraph.validate(wrongRoute),"shell cannot create a new body");

        // Timeline names and toolbar exposure.
        List<CadTimeline.Item> items=CadTimeline.entries(p);check(items.stream().anyMatch(i->i.title.equals("Szyk liniowy")),"timeline names the pattern");
        check(CadTimeline.entries(s).stream().anyMatch(i->i.title.equals("Powłoka")),"timeline names the shell");
        for(String c:new String[]{"Lustro operacji","Szyk liniowy operacji","Szyk kołowy operacji","Powłoka"})check(ToolbarCatalog.allows(false,c)&&!ToolbarCatalog.allows(true,c),"solid toolbar exposes "+c);
        // Removing any operation renumbers later references and removes orphaned patterns.
        CadProject r=plate();add(r,BOSS.clone());
        add(r,FeaturePattern.create(FeaturePattern.LINEAR,new int[]{0,1},new double[3],new double[]{1,0,0},2,30));
        add(r,ShellFeature.create(Collections.singletonList(new ShellFeature.Face(new double[]{50,30,20},new double[]{0,0,1},1)),2,false));
        add(r,FeaturePattern.create(FeaturePattern.MIRROR,new int[]{1},new double[]{50,0,0},new double[]{1,0,0},0,0));
        FeatureRemoval.Plan plan=FeatureRemoval.plan(r,0);check(plan.removed.equals(Collections.singletonList(0)),"pattern with another source survives");
        check(plan.consequences.stream().anyMatch(c->c.contains("przestanie powielać")),"consequence lists the affected pattern");
        CadProject r0=CadProject.fromJson(new JSONObject(r.toJson().toString()));FeatureRemoval.apply(r0,plan);BodyGraph.validate(r0);
        check(r0.committedFeatures.size()==4&&Arrays.equals(FeaturePattern.sources(r0.committedFeatures.get(1)),new int[]{0}),"pattern source renumbered and removed one dropped");
        check(r0.committedFeatures.get(2)[22]==0,"shell face creator renumbered");check(FeaturePattern.sources(r0.committedFeatures.get(3))[0]==0,"mirror source renumbered");
        FeatureRemoval.Plan bossPlan=FeatureRemoval.plan(r,1);check(bossPlan.removed.equals(Arrays.asList(1,4)),"mirror with its only source is removed together");
        check(bossPlan.consequences.stream().anyMatch(c->c.contains("utraci")),"shell losing its face is announced");
        CadProject r1=CadProject.fromJson(new JSONObject(r.toJson().toString()));FeatureRemoval.apply(r1,bossPlan);BodyGraph.validate(r1);
        check(r1.committedFeatures.size()==3&&r1.committedFeatures.get(2)[22]>1e8,"unresolved shell creator is never mapped to another operation");
        CadProject.fromJson(new JSONObject(r1.toJson().toString()));check(true,"document after removal stays loadable");
        CadProject bodies=plate();double[] created=BOSS.clone();created[1]=4;bodies.committedFeatures.add(created);bodies.committedProfileIds.add("");bodies.featureBodyTargets.add(0);int id=BodyGraph.allocate(bodies);bodies.featureBodyOutputs.add(id);
        double[] onNew=BOSS.clone();bodies.committedFeatures.add(onNew);bodies.committedProfileIds.add("");bodies.featureBodyTargets.add(id);bodies.featureBodyOutputs.add(id);BodyGraph.validate(bodies);
        rejects(()->FeatureRemoval.plan(bodies,1),"body creator used later cannot be removed");
        // Body Boolean routing and the bodies that remain listed.
        CadProject cb=plate();double[] nb=BOSS.clone();nb[1]=4;cb.committedFeatures.add(nb);cb.committedProfileIds.add("");cb.featureBodyTargets.add(0);int tool=BodyGraph.allocate(cb);cb.featureBodyOutputs.add(tool);
        check(BodyGraph.bodies(cb).contains(tool),"tool body listed before the Boolean");
        add(cb,BodyCombine.create(BodyCombine.CUT,tool,false));BodyGraph.validate(cb);
        check(!BodyGraph.bodies(cb).contains(tool)&&BodyGraph.bodies(cb).contains(1),"consumed tool body is no longer offered");
        packed=BodyGraph.pack(cb,concat(cb.committedFeatures));int at=16+16+16+16;check(packed[at]==-9&&packed[at+1]==1&&packed[at+16]==-15&&packed[at+18]==tool,"Boolean routed to the target with the tool id");
        CadProject cbk=CadProject.fromJson(new JSONObject(cb.toJson().toString()));check(Arrays.equals(cbk.committedFeatures.get(2),cb.committedFeatures.get(2)),"body Boolean survives save/load");
        CadProject self2=plate();add(self2,BodyCombine.create(BodyCombine.JOIN,1,false));rejects(()->BodyGraph.validate(self2),"body cannot be combined with itself");
        CadProject missing=plate();add(missing,BodyCombine.create(BodyCombine.JOIN,7,false));rejects(()->BodyGraph.validate(missing),"missing tool body refused");
        rejects(()->FeatureRemoval.plan(cb,1),"tool body creator cannot be removed while a Boolean uses it");
        check(FeatureRemoval.plan(cb,2).removed.size()==1,"the Boolean itself can be removed");
        rejects(()->BodyCombine.create(3,2,false),"unknown body operation refused");
        // Draft and body transform records.
        double[] draft=DraftFeature.create(faces,3,new double[3],new double[]{0,0,2});check(draft.length==23&&draft[8]==1,"draft record with normalised pull direction");
        rejects(()->DraftFeature.create(faces,0,new double[3],new double[]{0,0,1}),"zero draft angle rejected");
        rejects(()->DraftFeature.create(faces,61,new double[3],new double[]{0,0,1}),"draft above 60° rejected");
        rejects(()->DraftFeature.create(new ArrayList<>(),3,new double[3],new double[]{0,0,1}),"draft needs a face");
        CadProject dp=plate();add(dp,draft);BodyGraph.validate(dp);packed=BodyGraph.pack(dp,concat(dp.committedFeatures));check(packed[32+3]==23&&packed[48]==-16,"draft routed with its face table");
        check(Arrays.equals(CadProject.fromJson(new JSONObject(dp.toJson().toString())).committedFeatures.get(1),draft),"draft survives save/load");
        double[] mv=BodyTransform.move(1,2,3);check(BodyTransform.is(mv)&&mv[4]==3,"move record");
        rejects(()->BodyTransform.move(0,0,0),"zero move rejected");rejects(()->BodyTransform.scale(new double[3],1),"identity scale rejected");
        rejects(()->BodyTransform.rotate(new double[3],new double[3],10),"zero rotation axis rejected");
        double[] rot=BodyTransform.rotate(new double[]{1,1,1},new double[]{0,0,5},45);check(rot[7]==1&&rot[8]==45,"rotation axis normalised");
        CadProject tp=plate();add(tp,rot);check(Arrays.equals(CadProject.fromJson(new JSONObject(tp.toJson().toString())).committedFeatures.get(1),rot),"transform survives save/load");
        check(CadTimeline.entries(tp).stream().anyMatch(i->i.title.equals("Obrót bryły"))&&CadTimeline.entries(dp).stream().anyMatch(i->i.title.equals("Pochylenie")),"timeline names draft and transforms");
        for(String c:new String[]{"Otwór","Pochyl ściany","Połącz bryły","Odejmij bryły","Część wspólna brył","Przesuń bryłę","Obróć bryłę","Skaluj bryłę","Cieniowanie","Krawędzie","Przezroczystość","Przekrój"})check(ToolbarCatalog.allows(false,c),"solid toolbar exposes "+c);
        check(FeaturePattern.eligible(HoleFeature.create(new double[]{1,1,20},new double[]{0,0,1},-1,5,true,0,0,0,0,0)),"holes can be patterned");
        check(!FeaturePattern.eligible(rot)&&!FeaturePattern.eligible(draft),"body transforms and drafts are not patterned");
        CadProject sp=plate();int splitId=BodyGraph.allocate(sp);add(sp,BodySplit.create(splitId,new double[]{40,0,0},new double[]{1,0,0}));BodyGraph.validate(sp);
        check(BodyGraph.bodies(sp).contains(splitId),"split adds the new body to the body list");
        add(sp,HOLE.clone());sp.featureBodyTargets.set(2,splitId);sp.featureBodyOutputs.set(2,splitId);BodyGraph.validate(sp);check(true,"operations may target the split-off body");
        rejects(()->FeatureRemoval.plan(sp,1),"split cannot be removed while its new body is used");
        rejects(()->BodySplit.create(1,new double[3],new double[]{1,0,0}),"split cannot create body 1");
        CadProject dup=plate();int existing=BodyGraph.allocate(dup);add(dup,BodySplit.create(existing,new double[3],new double[]{1,0,0}));add(dup,BodySplit.create(existing,new double[3],new double[]{0,1,0}));
        rejects(()->BodyGraph.validate(dup),"split cannot reuse an existing body id");
        check(Arrays.equals(CadProject.fromJson(new JSONObject(sp.toJson().toString())).committedFeatures.get(1),sp.committedFeatures.get(1)),"split survives save/load");
        System.out.println("PASS SolidPatternHostTest: "+checks+" checks");
    }
    static void add(CadProject p,double[] f){p.committedFeatures.add(f);p.committedProfileIds.add("");p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);}
    static double[] concat(List<double[]> parts){int n=0;for(double[] p:parts)n+=p.length;double[] r=new double[n];int at=0;for(double[] p:parts){System.arraycopy(p,0,r,at,p.length);at+=p.length;}return r;}
}
