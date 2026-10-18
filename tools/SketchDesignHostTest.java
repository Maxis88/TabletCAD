import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Actual drawing, constraint and feature-link code; does not substitute for OCCT execution. */
public final class SketchDesignHostTest {
    interface Work{void run()throws Exception;}
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static void near(double a,double b,String label){check(Math.abs(a-b)<1e-7,label);}
    static void rejects(Work work,String label)throws Exception{try{work.run();}catch(IllegalArgumentException|JSONException expected){check(true,label);return;}throw new AssertionError(label);}
    static String json(CadProject p)throws Exception{return p.toJson().toString();}
    static SketchPrimitive rect(){return new SketchPrimitive("profile","Prostokąt",10,10,20,16);}
    static CadProject linked(SketchPrimitive s) {
        CadProject p=new CadProject();p.sketch.add(s);p.featureBase=ExtrusionPlan.base(p,s,8,0,false,false);p.featureBaseProfileId=s.id;
        p.committedFeatures.add(ExtrusionPlan.feature(p,s,"new",4,0,false,false));p.committedProfileIds.add(s.id);
        p.committedFeatures.add(ExtrusionPlan.feature(p,s,"cut",2,0,true,false));p.committedProfileIds.add(s.id);
        return p;
    }
    public static void main(String[] args)throws Exception {
        SketchPrimitive line=new SketchPrimitive("line","Linia",1,2,4,6,new double[]{5,Math.toDegrees(Math.atan2(4,3))});
        SketchPrimitive shifted=SketchTransform.move(line,7,-2);near(shifted.x1,8,"exact translation X");near(shifted.y1,0,"exact translation Y");
        check(shifted.id.equals(line.id)&&Arrays.equals(shifted.driving,line.driving),"translation retains identity and driving dimensions");
        SketchPrimitive rotated=SketchTransform.rotate(line,90,1,2);near(rotated.x2,-3,"rotation around specified centre X");near(rotated.y2,5,"rotation around specified centre Y");SketchDimensions.validate(rotated);
        near(rotated.driving[0],5,"rotation retains locked length");near(Math.IEEEremainder(rotated.driving[1]-line.driving[1],360),90,"rotation updates explicit angle dimension");
        SketchPrimitive scaled=SketchTransform.scale(line,2,1,2);near(scaled.x2,7,"uniform scale X");near(scaled.y2,10,"uniform scale Y");near(scaled.driving[0],10,"explicit scale updates locked length");
        SketchPrimitive rectangle=rect(),polygon=SketchTransform.rotate(rectangle,37,0,0);
        check(polygon.id.equals(rectangle.id)&&polygon.vertices!=null&&polygon.vertices.length==8&&polygon.closedProfile(),"arbitrary rectangle rotation becomes a closed four-edge contour with same identity");
        SketchPrimitive locked=new SketchPrimitive("locked","Prostokąt",0,0,10,6,new double[]{10,6});
        rejects(()->SketchTransform.rotate(locked,45,0,0),"rotation cannot silently drop rectangle driving dimensions");
        SketchPrimitive quarter=SketchTransform.rotate(locked,90,0,0);near(quarter.driving[0],6,"quarter-turn swaps locked width");near(quarter.driving[1],10,"quarter-turn swaps locked height");
        SketchPrimitive circle=new SketchPrimitive("circle","Okrąg",0,0,3,4,new double[]{5});
        SketchPrimitive circleScaled=SketchTransform.scale(circle,3,0,0);near(SketchDimensions.length(circleScaled),15,"circle scale preserves analytic radius");check(circleScaled.curves==null&&circleScaled.vertices==null,"circle is not polygonized");
        SketchPrimitive arc=SketchShapes.threePoint("arc","Łuk",new double[]{0,0},new double[]{10,0},new double[]{5,5});
        SketchPrimitive arcMoved=SketchTransform.move(arc,4,8);check(arcMoved.curves.length==7&&arcMoved.curves[0]==1,"arc stays analytic under translation");
        near(SketchCurves.arc(arcMoved.curves,0).r,5,"translated arc radius");
        SketchPrimitive spline=new SketchPrimitive("spline",new double[]{0,0,5,4,10,0},"Splajn");
        check(SketchSpline.is(SketchTransform.rotate(spline,20,0,0)),"spline remains a spline");
        check(SketchTransform.move(line.withConstruction(true),1,2).construction,"construction status preserved");
        SketchPrimitive legacyAux=new SketchPrimitive("aux","Pomocnicza",0,0,10,0,new double[]{10,0});
        check(SketchTransform.move(legacyAux,1,2).construction,"legacy dimensioned auxiliary line can be moved");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,0,.0001,1001})rejects(()->SketchTransform.scale(rectangle,bad,0,0),"invalid scale rejected "+bad);
        rejects(()->SketchTransform.move(rectangle,5000,0),"translation checks sketch bounds");rejects(()->SketchTransform.rotate(rectangle,Double.NaN,0,0),"invalid rotation rejected");
        rejects(()->SketchTransform.scale(circle,1000,5000,0),"full circle extent checks bounds");
        rejects(()->SketchTransform.scale(new SketchPrimitive("thin","Prostokąt",0,0,.01,100),.5,0,0),"scaled rectangle cannot have a degenerate side");
        SketchConstraints.Link fixed=SketchConstraints.Link.fixed(line,0);
        rejects(()->SketchConstraints.edit(Arrays.asList(line),Arrays.asList(fixed),line.id,shifted),"transformation respects fixed point constraints");
        check(line.x1==1&&line.x2==4&&line.driving[0]==5,"transforms leave source object unchanged");

        for(String tool:new String[]{"Okrąg 3 pkt","Szczelina"}) {
            CadProject draft=CadProject.blank();SketchTransactions.begin(draft);String initial=json(draft);
            check(ThreePointDraft.append(draft,tool,new double[]{0,0},"new")==null,"first point persists for "+tool);
            draft=CadProject.fromJson(draft.toJson());check(draft.threePointDraft.size()==1&&draft.threePointTool.equals(tool),"first point survives JSON for "+tool);
            ThreePointDraft.append(draft,tool,new double[]{10,0},"new");CadProject two=CadProject.fromJson(draft.toJson());String before=json(two);
            rejects(()->ThreePointDraft.append(two,tool,new double[]{5,0},"new"),"collinear or zero-width third point rejected for "+tool);
            check(json(two).equals(before),"invalid third point preserves draft and sketch for "+tool);
            SketchPrimitive created=ThreePointDraft.append(two,tool,new double[]{5,5},"new");
            check(created.closedProfile()&&two.threePointDraft.isEmpty()&&two.threePointTool.isEmpty(),"third point creates closed profile and clears draft for "+tool);
            check(SketchTransactions.candidate(two).sketch.size()==1,"completed construction can finish sketch for "+tool);
            check(CadProject.fromJson(new JSONObject(initial)).sketch.isEmpty(),"baseline for cancel remains empty for "+tool);
            if(tool.equals("Okrąg 3 pkt")){near(created.x1,5,"three-point circle centre X");near(created.y1,0,"three-point circle centre Y");near(SketchDimensions.length(created),5,"three-point circle radius");}
            else {check(created.curves.length==28&&created.curves[0]==0&&created.curves[7]==1,"slot has two exact lines and two arcs");near(SketchCurves.arc(created.curves,7).r,5,"slot end radius");}
        }
        CadProject pending=CadProject.blank();SketchTransactions.begin(pending);ThreePointDraft.append(pending,"Szczelina",new double[]{0,0},"s");
        rejects(()->ThreePointDraft.append(pending,"Okrąg 3 pkt",new double[]{1,0},"s"),"cannot switch tools mid-construction");
        rejects(()->ThreePointDraft.append(pending,"Szczelina",new double[]{0,0},"s"),"duplicate construction point rejected");
        rejects(()->SketchTransactions.candidate(pending),"unfinished construction cannot be committed to model");

        CadProject model=linked(rectangle);String original=json(model);double[][] old={model.featureBase.clone(),model.committedFeatures.get(0).clone(),model.committedFeatures.get(1).clone()};
        ProfileDependencies.propagate(model,rectangle.id,polygon);
        check(model.featureBase[0]==6&&model.featureBase.length==15,"base changes feature type and contour payload after rotation");
        for(int i=0;i<2;i++) {double[] f=model.committedFeatures.get(i);check(f[0]==6&&f.length==24,"all consumer contours updated "+i);for(int j:new int[]{1,6,7,8,9,10,11,12,13,14,15})near(f[j],old[i+1][j],"consumer frame/depth/direction retained "+i+":"+j);}
        near(model.featureBase[6],old[0][6],"base depth retained");check(ProfileDependencies.consumers(model,rectangle.id).size()==3,"dependency report includes every consumer");
        ProfileDependencies.propagate(model,rectangle.id,rectangle);
        check(model.featureBase.length==7&&model.committedFeatures.get(0).length==16&&model.featureBase[0]==0,"return to rectangle removes obsolete contour tails");
        rejects(()->ProfileDependencies.propagate(model,rectangle.id,line),"used profile cannot become an open line");
        CadProject activeOnly=new CadProject();activeOnly.featureOperation="cut";activeOnly.featureProfileId=rectangle.id;
        rejects(()->ProfileDependencies.propagate(activeOnly,rectangle.id,rectangle.withConstruction(true)),"active-only operation cannot silently lose a profile converted to construction geometry");
        CadProject placed=new CadProject();placed.sketchPlane="Ściana 7";placed.sketchOriginX=3;placed.sketchOriginY=4;placed.sketchOriginZ=20;
        placed.featureBase=ExtrusionPlan.base(placed,rectangle,8,2,true,false);placed.featureBaseProfileId=rectangle.id;double[] placedOld=placed.featureBase.clone();
        ProfileDependencies.propagate(placed,rectangle.id,polygon);ExtrusionPlan.validateBase(placed.featureBase);
        check(placed.featureBase[0]==-10&&placed.featureBase[7]==6&&placed.featureBase.length==31,"placed two-direction base updates nested profile header");
        for(int j:new int[]{1,2,3,4,5,6,8,13,14,15,16,17,18,19,20,21,22})near(placed.featureBase[j],placedOld[j],"placed base invariant "+j);
        check(ProfileDependencies.restore(placed,rectangle).vertices!=null,"placed base restores actual linked contour");
        CadProject revolve=new CadProject();SketchPrimitive offAxis=new SketchPrimitive("revolved","Prostokąt",10,0,14,4);
        revolve.featureBase=Revolve.create(offAxis,revolve,new double[]{0,-20,0,20},180,false);revolve.featureBaseProfileId=offAxis.id;
        double[] revolveOld=revolve.featureBase.clone();ProfileDependencies.propagate(revolve,offAxis.id,SketchTransform.move(offAxis,2,0));Revolve.validate(revolve.featureBase);
        for(int j:new int[]{1,6,7,8,9,10,11,12,13,14,15,16,17,18,19,21})near(revolve.featureBase[j],revolveOld[j],"revolve axis/frame/angle invariant "+j);

        CadProject draft=CadProject.fromJson(new JSONObject(original));SketchTransactions.begin(draft);String baseline=json(draft);
        draft.sketch.set(0,polygon);CadProject rebuilt=SketchTransactions.candidate(draft);
        check(rebuilt.featureBase[0]==6&&rebuilt.committedFeatures.get(0)[0]==6&&rebuilt.committedFeatures.get(1)[0]==6,"finish sketch updates base and all dependent features together");
        check(draft.featureBase[0]==0&&draft.committedFeatures.get(0)[0]==0&&SketchTransactions.active(draft),"candidate rebuild leaves live draft and old model untouched");
        check(CadProject.fromJson(rebuilt.toJson()).featureBase[0]==6,"updated model survives schema round trip");
        CadProject deleted=CadProject.fromJson(new JSONObject(baseline));deleted.sketch.clear();SketchTransactions.prune(deleted);String deletedBefore=json(deleted);
        rejects(()->SketchTransactions.candidate(deleted),"missing linked profile blocks regeneration");check(json(deleted).equals(deletedBefore),"failed regeneration preserves draft bytes");
        CadProject rebound=CadProject.fromJson(new JSONObject(baseline));rebound.sketch.clear();SketchPrimitive replacement=new SketchPrimitive("replacement","Okrąg",15,13,18,13);rebound.sketch.add(replacement);SketchTransactions.prune(rebound);SketchTransactions.bind(rebound,rectangle.id,replacement.id);
        CadProject bound=SketchTransactions.candidate(rebound);check(bound.featureBaseProfileId.equals("replacement")&&bound.committedProfileIds.stream().allMatch("replacement"::equals),"explicit profile replacement rebinds every consumer UUID");
        check(bound.featureBase[0]==1&&bound.featureBase.length==7&&bound.committedFeatures.get(0)[0]==1,"explicit replacement changes profile kind without stale payload");
        CadProject inconsistent=linked(rectangle);inconsistent.archiveCurrentSketch();inconsistent.committedFeatures.get(0)[4]+=1;String corruptBefore=json(inconsistent);
        rejects(()->inconsistent.restoreSketch(inconsistent.savedSketches.get(0)),"contradictory consumers cannot silently choose last profile");check(json(inconsistent).equals(corruptBefore),"ambiguous restore leaves current sketch unchanged");
        check(ToolbarCatalog.allows(true,"Szczelina")&&ToolbarCatalog.allows(true,"Okrąg 3 pkt")&&ToolbarCatalog.allows(true,"Obróć szkic")&&ToolbarCatalog.allows(false,"Edytuj profil źródłowy"),"new design commands are exposed in native tool groups");
        System.out.println("PASS SketchDesignHostTest: "+checks+" checks");
    }
}
