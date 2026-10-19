import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class SourceSketchKernelProbe {
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("PASS "+text);}
    static void near(double actual,double expected,String text){check(Math.abs(actual-expected)<1e-6,text+" ("+actual+")");}
    static void add(CadProject p,double[] f,int target,int output,String profile){p.committedFeatures.add(f);p.committedProfileIds.add(profile);p.featureBodyTargets.add(target);p.featureBodyOutputs.add(output);}
    static CadProject source(){CadProject p=new CadProject();p.sketch.clear();SketchPrimitive s=new SketchPrimitive("source","Prostokąt",0,0,24,12);p.sketch.add(s);p.featureBase=ExtrusionPlan.base(p,s,8,0,false,false);p.featureBaseProfileId=s.id;p.sketchExtruded=true;BodyGraph.begin(p);return p;}
    static double volume(CadProject p){double[] result=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,p.filletRadius,p.featureBase,AutoExtrusion.packed(p),null);check(result.length==7,"valid exact B-Rep");return result[0];}
    static CadProject tool(double z,boolean reverse){CadProject p=new CadProject();p.width=40;p.height=30;p.depth=10;BodyGraph.begin(p);CadProject frame=CadProject.blank();frame.sketchOriginZ=z;
        add(p,ExtrusionPlan.feature(frame,new SketchPrimitive("tool","Prostokąt",10,8,20,18),"new",15,0,reverse,false),0,BodyGraph.allocate(p),"tool");return p;}
    public static void main(String[] args)throws Exception {
        CadProject p=source();add(p,BodyTransform.move(30,4,0),1,1,"");add(p,BodyTransform.rotate(new double[3],new double[]{0,0,1},90),1,1,"");add(p,BodyTransform.scale(new double[3],2),1,1,"");
        String before=p.toJson().toString();double[] m=SketchPlacement.of(p,"source",1),point=SketchPlacement.point(m,10,0,0);
        near(point[0],-8,"source follows composed move/rotation/scale X");near(point[1],80,"source follows composed move/rotation/scale Y");
        ModelTree.Row row=null;for(ModelTree.Row r:ModelTree.rows(p,Collections.emptySet()))if(r.sketch()&&r.body==1)row=r;
        check(row!=null,"source is assigned to explicit body");CadProject display=ModelTree.display(p,row,"source");
        near(display.sketchOriginX,-8,"display origin follows body");near(display.sketchOriginY,60,"display origin rotated and scaled");near(display.sketchUy,2,"display U axis includes scale and rotation");
        check(before.equals(p.toJson().toString()),"display placement leaves source geometry and document unchanged");
        CadProject reload=CadProject.fromJson(p.toJson());near(SketchPlacement.point(SketchPlacement.of(reload,"source",1),10,0,0)[1],80,"placement survives document round trip");
        ProfileDependencies.propagate(p,"source",new SketchPrimitive("source","Prostokąt",0,0,30,12));near(volume(p),30*12*8*8,"editing source does not apply body scale twice");
        p.suppressedFeatures.add(0);near(SketchPlacement.point(SketchPlacement.of(p,"source",1),10,0,0)[1],20,"suppressed move is excluded from source placement");p.suppressedFeatures.clear();
        p.historyMarker=1;p.historyMarkerCount=3;near(SketchPlacement.point(SketchPlacement.of(p,"source",1),10,0,0)[0],40,"rollback excludes later transforms");
        CadProject outward=tool(10,false);check(AutoExtrusion.resolve(outward,0,1,null).startsWith("Połącz"),"outward extrusion defaults to join");near(volume(outward),13500,"outward automatic join volume");
        CadProject inward=tool(10,true);check(AutoExtrusion.resolve(inward,0,1,null).equals("Wytnij"),"inward through extrusion defaults to cut");near(volume(inward),11000,"automatic cut retains requested physical direction");
        boolean rejected=false;try{AutoExtrusion.resolve(tool(30,false),0,1,null);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("Nowa bryła");}check(rejected,"detached auto tool requires explicit new body");
        // A joined rectangular stem protrudes through both sides of a plate.
        CadProject join=new CadProject();join.width=60;join.height=40;join.depth=10;BodyGraph.begin(join);CadProject frame=CadProject.blank();frame.sketchOriginZ=-10;
        add(join,ExtrusionPlan.feature(frame,new SketchPrimitive("stem","Prostokąt",20,15,40,25),"add",30,0,false,false),1,1,"stem");near(volume(join),28000,"through-joined stem has one exact union");
        double[] edges=NativeCadEngine.modelEdges(join.width,join.height,join.depth,0,null,AutoExtrusion.packed(join),null);
        for(double z:new double[]{0,10}) {
            List<Integer> roots=new ArrayList<>();for(int i=0;i<edges.length;i+=16)if(Math.abs(edges[i+2]-z)<1e-7&&Math.abs(edges[i+5]-z)<1e-7&&Math.abs(edges[i+8]-z)<1e-7&&edges[i+3]>=19.9&&edges[i+3]<=40.1&&edges[i+4]>=14.9&&edges[i+4]<=25.1)roots.add(i);
            check(roots.size()>=4,"four real stem root edges on plate side Z="+z);
            for(boolean chamfer:new boolean[]{false,true}) {
                CadProject treated=CadProject.fromJson(join.toJson());double[] f=chamfer?EdgeChamfer.create(edges,roots.get(0),1):EdgeFillet.create(edges,roots.get(0),1);
                add(treated,f,1,1,"");check(volume(treated)>28000,"concave "+(chamfer?"chamfer":"fillet")+" works at protruding root Z="+z);
            }
        }
        System.out.println("ALL SOURCE SKETCH / AUTO EXTRUSION / THROUGH JOIN TESTS PASSED");
    }
}
