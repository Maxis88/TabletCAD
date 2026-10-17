import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class RootEdgeReferenceProbe {
    static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);System.out.println("PASS "+s);}
    static void near(double a,double b,String s){check(Math.abs(a-b)<1e-5,s+" actual="+a+" expected="+b);}
    static double[] edges(double[] base,double[] history){return NativeCadEngine.modelEdges(40,30,20,0,base,history,null);}
    static double[] eval(double[] base,double[] history){return NativeCadEngine.evaluateFeatures(40,30,20,0,base,history,null);}
    static double volume(double[] base,double[] history){double[] p=NativeCadEngine.bodyProperties(40,30,20,0,base,history,null);check(p.length==7,"valid exact B-Rep properties");return p[0];}
    static double[] rim(double[] edges,int axis,double position,double distance) {
        for(int i=0;i<edges.length;i+=16)if(Math.abs(edges[i+axis]-position)<1e-6&&Math.abs(edges[i+3+axis]-position)<1e-6
                &&Math.abs(edges[i]-edges[i+6])<1e-6&&Math.abs(edges[i+1]-edges[i+7])<1e-6&&Math.abs(edges[i+2]-edges[i+8])<1e-6) {
            double[] f=EdgeFillet.create(edges,i,distance);f[0]=-2;return f;
        }
        throw new AssertionError("circular rim not found");
    }
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
    static void run(String[] args)throws Exception {
        double[] box={0,0,0,40,30,0,20},rootEdges=edges(box,new double[0]);
        double[] original=EdgeFilletProbe.edge(rootEdges,40,30),fillet=RootEdgeReference.anchor(original,box,rootEdges);
        check(fillet.length==24&&fillet[0]==-4&&EdgeFillet.is(fillet),"root corner gets parametric fillet reference");EdgeFillet.validate(fillet);
        near(volume(box,fillet),volume(box,original),"anchoring does not change original geometry");
        double[] opposite=EdgeFilletProbe.edge(rootEdges,0,0),oppositeAnchor=RootEdgeReference.anchor(opposite,box,rootEdges);
        double twoCorners=24000-2*(1-Math.PI/4)*9*20;
        near(volume(box,EdgeFilletProbe.join(fillet,opposite)),twoCorners,"new variable-length reference followed by legacy operation");
        near(volume(box,EdgeFilletProbe.join(opposite,fillet)),twoCorners,"legacy operation followed by new variable-length reference");
        near(volume(box,EdgeFilletProbe.join(oppositeAnchor,fillet)),twoCorners,"two anchored operations preserve packing boundaries");
        double[] changed={0,0,0,60,45,0,30};
        check(eval(changed,original).length==0,"legacy absolute edge would be lost after resize");
        double expected=60*45*30-(1-Math.PI/4)*9*30;
        near(volume(changed,fillet),expected,"root fillet follows width height and extrusion depth");
        changed[6]=-30;near(volume(changed,fillet),expected,"root fillet follows reversed extrusion depth");
        changed[6]=30;changed[5]=4;near(volume(changed,fillet),expected,"root fillet follows symmetric extrusion origin");
        changed[5]=1;near(volume(changed,fillet),expected,"root fillet maps XY source into XZ plane");
        changed[5]=2;near(volume(changed,fillet),expected,"root fillet maps XY source into YZ plane");
        double[] cylindrical={1,5,7,15,7,0,20},cylinderEdges=edges(cylindrical,new double[0]);
        double[] bevel=RootEdgeReference.anchor(rim(cylinderEdges,2,20,2),cylindrical,cylinderEdges);
        check(bevel[0]==-5&&EdgeChamfer.is(bevel),"cylinder rim gets parametric chamfer reference");EdgeChamfer.validate(bevel);
        double[] cylinderChanged={1,9,11,21,11,0,30};
        near(volume(cylinderChanged,bevel),Math.PI*144*30-Math.PI*4*(12-2.0/3),"cylinder chamfer follows radius center and length");
        CadProject p=CadProject.blank();SketchPrimitive shaft=new SketchPrimitive("shaft","Prostokąt",0,0,10,40);
        double[] axis={0,0,0,1},base=Revolve.create(shaft,p,axis,360,false);
        SketchPrimitive groove=new SketchPrimitive("groove","Prostokąt",8,10,14,15);
        double[] cut=Revolve.create(groove,p,axis,360,true),revolveEdges=edges(base,new double[0]),cutEdges=edges(base,cut);
        double[] chamfer=RootEdgeReference.anchor(rim(cutEdges,1,40,1),base,revolveEdges);
        check(chamfer.length==39&&chamfer[0]==-5,"surviving root rim anchored through preceding groove cut");
        double[] history=EdgeFilletProbe.join(cut,chamfer);
        near(volume(base,history),Math.PI*100*40-Math.PI*(100-64)*5-Math.PI*(10-1.0/3),"shaft groove and chamfer initial exact geometry");
        double[] enlarged=Revolve.withProfile(base,new SketchPrimitive(shaft.id,"Prostokąt",0,0,12,50));
        near(volume(enlarged,history),Math.PI*144*50-Math.PI*(144-64)*5-Math.PI*(12-1.0/3),"shaft radius and length change preserves groove then chamfer");
        double[] resizedChamfer=chamfer.clone();resizedChamfer[11]=2;
        near(volume(enlarged,EdgeFilletProbe.join(cut,resizedChamfer)),Math.PI*144*50-Math.PI*(144-64)*5-Math.PI*4*(12-2.0/3),"anchored chamfer remains parametrically editable");
        check(NativeCadEngine.evaluateFeatures(40,30,20,0,enlarged,history,args[0]).length>0,"resized referenced model exports STEP");
        near(NativeCadEngine.bodyProperties(40,30,20,0,null,new double[0],args[0])[0],volume(enlarged,history),"resized STEP preserves exact geometry");
        double[] alteredAngle=base.clone();alteredAngle[6]=180;
        check(eval(alteredAngle,history).length==0,"unsupported change of revolution extent never guesses a different edge");
        double[] removeCorner={0,1,35,25,45,35,100,0,0,50,1,0,0,0,1,0};
        check(eval(box,EdgeFilletProbe.join(removeCorner,fillet)).length==0,"edge deleted by earlier Boolean does not bind a nearby edge");
        double[] bogus=original.clone();bogus[2]+=1;
        check(!RootEdgeReference.is(RootEdgeReference.anchor(bogus,box,rootEdges)),"edge absent from root stays strict absolute reference");
        double[] duplicated=Arrays.copyOf(rootEdges,rootEdges.length*2);System.arraycopy(rootEdges,0,duplicated,rootEdges.length,rootEdges.length);
        for(int i=rootEdges.length+15;i<duplicated.length;i+=16)duplicated[i]+=100;
        check(!RootEdgeReference.is(RootEdgeReference.anchor(original,box,duplicated)),"ambiguous root edge cannot be anchored");
        p.sketch.add(shaft);p.sketch.add(groove);p.featureBase=base;p.featureBaseProfileId=shaft.id;
        p.committedFeatures.add(cut);p.committedProfileIds.add(groove.id);p.committedFeatures.add(chamfer);p.committedProfileIds.add("");
        p.archiveCurrentSketch();CadProject loaded=CadProject.fromJson(p.toJson());
        check(EdgeChamfer.is(loaded.committedFeatures.get(1))&&loaded.committedFeatures.get(1).length==39,"project preserves full source edge reference");
        SketchTransactions.begin(loaded);loaded.sketch.set(0,new SketchPrimitive(shaft.id,"Prostokąt",0,0,12,50));
        CadProject candidate=SketchTransactions.candidate(loaded);
        double[] packed=EdgeFilletProbe.join(candidate.committedFeatures.get(0),candidate.committedFeatures.get(1));
        near(volume(candidate.featureBase,packed),volume(enlarged,history),"sketch transaction regenerates referenced groove and chamfer");
        check(SketchTransactions.baseline(loaded).featureBase[4]==10,"transaction cancel still retains old shaft radius");
        JSONObject invalid=p.toJson();invalid.getJSONArray("committedFeatures").getJSONArray(1).put(16,6);boolean rejected=false;
        try{CadProject.fromJson(invalid);}catch(JSONException expectedError){rejected=true;}check(rejected,"corrupted reference payload rejected on load");
        double[] invalidNative=chamfer.clone();invalidNative[16]=6;check(eval(base,invalidNative).length==0,"corrupted reference payload rejected by kernel");
        System.out.println("ALL ROOT EDGE REFERENCE TESTS PASSED");
    }
}
