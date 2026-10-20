import java.util.*;
import org.json.JSONObject;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.SketchPrimitive;

/** 0.95 sketch point: construction reference, constraints, position dimensions, persistence, snapping. */
public final class SketchPointHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static SketchPrimitive get(List<SketchPrimitive> s,String id){for(SketchPrimitive p:s)if(p.id.equals(id))return p;throw new AssertionError(id);}
    public static void main(String[] args)throws Exception {
        SketchPrimitive point=SketchPrimitive.point("pt",10,5);
        check(point.isPoint()&&point.construction&&!point.closedProfile(),"a point is construction geometry, never a profile");
        check(point.withConstruction(false).construction,"a point cannot become profile geometry");
        check(SketchConstraints.anchorCount(point)==1,"one constraint anchor");
        try{new SketchPrimitive("bad",SketchPrimitive.POINT,0,0,1,0);check(false,"two positions");}catch(IllegalArgumentException e){check(true,"a point has exactly one position");}
        SketchPrimitive line=new SketchPrimitive("ln","Linia",0,0,40,0);
        List<SketchPrimitive> sketch=new ArrayList<>(Arrays.asList(line,point));
        // Coincident: the point snaps onto the end of the line (linear solver).
        SketchConstraints.Link join=new SketchConstraints.Link("ln",1,"pt",0);
        List<SketchPrimitive> joined=SketchConstraints.connect(sketch,new ArrayList<>(),join);
        SketchPrimitive moved=get(joined,"pt");
        check(Math.abs(moved.x1-40)<1e-9&&Math.abs(moved.y1)<1e-9&&moved.isPoint(),"point joined to the line end stays a point");
        // Point on a line (nonlinear solver).
        SketchConstraints.Link on=new SketchConstraints.Link("pointOn","pt",0,"ln",0,0,0);
        List<SketchPrimitive> onLine=SketchConstraints.connect(sketch,new ArrayList<>(),on);
        check(Math.abs(get(onLine,"pt").y1)<1e-9&&get(onLine,"pt").isPoint(),"point on the line (nonlinear solver)");
        // Position from the origin.
        SketchPosition.Result placed=SketchPosition.place(sketch,new ArrayList<>(),"pt",0,null,0,25,30,true,true);
        check(Math.abs(get(placed.sketch,"pt").x1-25)<1e-9&&placed.links.size()==2,"point placed X25 Y30 with two driving dimensions");
        check(SketchPosition.pointNames(point).equals(Collections.singletonList("Punkt")),"position panel names the point");
        // Status: a free point has two freedoms.
        int free=NonlinearConstraints.status(Collections.singletonList(point),new ArrayList<>()).freedoms;
        check(free==2,"free point has 2 degrees of freedom ("+free+")");
        // Persistence and snapping.
        SketchPrimitive loaded=SketchPrimitive.fromJson(new JSONObject(point.toJson().toString()));
        check(loaded.isPoint()&&loaded.x1==10&&loaded.y1==5&&loaded.construction,"point round-trips through JSON");
        double[] snapped=SketchSnapper.snap(10.3,5.2,Collections.singletonList(point),null,1,null,null);
        check(Math.abs(snapped[0]-10)<1e-9&&Math.abs(snapped[1]-5)<1e-9,"drawing snaps to the point");
        SketchPrimitive copy=SketchTransform.move(point,3,4);check(copy.isPoint()&&copy.x1==13&&copy.y1==9,"point moves rigidly");
        System.out.println("PASS SketchPointHostTest: "+checks+" checks");
    }
}
