import java.util.*;
import org.json.JSONArray;
import pl.tabletcad.app.NonlinearConstraints;
import pl.tabletcad.app.SketchConstraints;
import pl.tabletcad.app.model.SketchPrimitive;

/** 0.89 driving angle between two sketch lines (relation "angle"): solve, edit, persistence, status. */
public final class SketchAngleHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static SketchPrimitive get(List<SketchPrimitive> s,String id){for(SketchPrimitive p:s)if(p.id.equals(id))return p;throw new AssertionError(id);}
    public static void main(String[] args)throws Exception {
        SketchPrimitive base=new SketchPrimitive("base","Linia",0,0,50,0),arm=new SketchPrimitive("arm","Linia",0,0,30,20);
        List<SketchPrimitive> sketch=new ArrayList<>(Arrays.asList(base,arm));
        double current=NonlinearConstraints.angle(base,arm);
        check(current>33&&current<34,"current oriented angle measured ("+current+")");
        double value=NonlinearConstraints.angleValue(base,arm,45);
        check(value==45,"value keeps the side of the lines");
        List<SketchConstraints.Link> links=new ArrayList<>();
        SketchConstraints.Link link=new SketchConstraints.Link("angle","base",0,"arm",0,value,0);
        // The base stays, the arm turns about its first point and keeps its length.
        List<SketchPrimitive> solved=SketchConstraints.connect(sketch,links,link);links.add(link);
        SketchPrimitive a=get(solved,"arm"),b=get(solved,"base");
        check(Math.abs(NonlinearConstraints.angle(b,a)-45)<1e-6,"arm at 45° to the base");
        check(Math.abs(Math.hypot(a.x2-a.x1,a.y2-a.y1)-Math.hypot(30,20))<1e-6,"arm keeps its length");
        check(Math.abs(b.x2-50)<1e-9&&Math.abs(b.y2)<1e-9,"base unchanged");
        // Editing the value re-solves.
        List<SketchConstraints.Link> edited=new ArrayList<>(links);edited.set(0,SketchConstraints.withValue(link,120));
        List<SketchPrimitive> wide=SketchConstraints.resolve(solved,edited);
        check(Math.abs(Math.abs(NonlinearConstraints.angle(get(wide,"base"),get(wide,"arm")))-120)<1e-5,"edited angle 120°");
        // Rotating the base drags the arm along (the relation is relative).
        SketchPrimitive turned=new SketchPrimitive("base","Linia",0,0,0,50);
        List<SketchPrimitive> follow=SketchConstraints.edit(solved,links,"base",turned);
        check(Math.abs(NonlinearConstraints.angle(get(follow,"base"),get(follow,"arm"))-45)<1e-5,"angle kept when the base turns");
        // Persistence.
        JSONArray json=SketchConstraints.toJson(links);List<SketchConstraints.Link> loaded=SketchConstraints.fromJson(json,solved);
        check(loaded.size()==1&&loaded.get(0).kind.equals("angle")&&loaded.get(0).x==45,"angle relation round-trips through JSON");
        SketchConstraints.validate(solved,loaded);check(true,"loaded sketch satisfies the angle");
        // Status: an angle removes one freedom.
        int before=NonlinearConstraints.status(sketch,new ArrayList<>()).freedoms,after=NonlinearConstraints.status(solved,links).freedoms;
        check(before-after==1,"angle removes one degree of freedom ("+before+" → "+after+")");
        check(NonlinearConstraints.symbol("angle").equals("∠"),"angle symbol");
        try{NonlinearConstraints.angleValue(base,arm,180);check(false,"180 refused");}catch(IllegalArgumentException e){check(true,"angle outside 0–180° refused");}
        try{SketchConstraints.connect(Arrays.asList(base,new SketchPrimitive("c","Okrąg",0,0,5,0)),new ArrayList<>(),new SketchConstraints.Link("angle","base",0,"c",0,30,0));check(false,"circle refused");}
        catch(IllegalArgumentException e){check(true,"angle needs two lines");}
        // Pattern along a path (same release).
        SketchPrimitive dot=new SketchPrimitive("dot","Okrąg",0,0,2,0),rail=new SketchPrimitive("rail","Linia",0,0,100,0);
        List<SketchPrimitive> along=pl.tabletcad.app.SketchPathPattern.copies(dot,rail,5,false);
        check(along.size()==4&&Math.abs(along.get(3).x1-100)<1e-9&&Math.abs(along.get(1).x1-50)<1e-9,"open path: 5 elements from start to end every 25 mm");
        check(Math.abs(Math.hypot(along.get(0).x2-along.get(0).x1,along.get(0).y2-along.get(0).y1)-2)<1e-9,"copies keep the radius");
        SketchPrimitive ring=new SketchPrimitive("ring","Okrąg",0,0,50,0);
        SketchPrimitive slot=new SketchPrimitive("slot","Prostokąt",48,-1,52,1);
        List<SketchPrimitive> around=pl.tabletcad.app.SketchPathPattern.copies(slot,ring,4,true);
        double[] c=pl.tabletcad.app.SketchPosition.point(around.get(0),pl.tabletcad.app.SketchPosition.CENTRE);
        check(around.size()==3&&Math.abs(Math.hypot(c[0],c[1])-50)<0.1,"closed path: copies spread around the circle");
        check(around.get(0).vertices==null&&Math.abs(Math.abs(around.get(0).x2-around.get(0).x1)-2)<1e-6&&Math.abs(Math.abs(around.get(0).y2-around.get(0).y1)-4)<1e-6,"quarter turn: the slot turns with the path (4×2 → 2×4)");
        check(pl.tabletcad.app.SketchPathPattern.copies(slot,ring,5,true).get(0).vertices!=null,"other angles: the rotated rectangle becomes an exact polygon");
        double[] shift=pl.tabletcad.app.SketchPathPattern.snapToStart(new SketchPrimitive("d2","Okrąg",7,7,9,7),rail);
        check(Math.abs(shift[0]+7)<1e-9&&Math.abs(shift[1]+7)<1e-9,"element moved to the start of the path");
        try{pl.tabletcad.app.SketchPathPattern.copies(dot,dot,3,false);check(false,"self path refused");}catch(IllegalArgumentException e){check(true,"path must be another element");}
        // Driving distances (1.0): a circle centre 15 mm from a line, two points 40 mm apart.
        SketchPrimitive edge=new SketchPrimitive("edge","Linia",0,0,100,0),hole=new SketchPrimitive("hole","Okrąg",30,10,33,10);
        List<SketchPrimitive> part=new ArrayList<>(Arrays.asList(edge,hole));
        SketchConstraints.Link dist=new SketchConstraints.Link("distance","hole",0,"edge",0,15,0);
        List<SketchPrimitive> placed=SketchConstraints.connect(part,new ArrayList<>(),dist);
        SketchPrimitive h=get(placed,"hole");
        check(Math.abs(Math.abs(h.y1)-15)<1e-6&&Math.abs(Math.hypot(h.x2-h.x1,h.y2-h.y1)-3)<1e-6&&Math.abs(get(placed,"edge").y2)<1e-9,"hole centre 15 mm from the line, radius kept, the line stays");
        check(Math.abs(Math.abs(NonlinearConstraints.distance(placed,dist))-15)<1e-6,"measured distance 15");
        List<SketchConstraints.Link> dl=new ArrayList<>(Collections.singletonList(dist));
        List<SketchPrimitive> farther=SketchConstraints.edit(placed,Collections.singletonList(SketchConstraints.withValue(dist,22.5)),"edge",get(placed,"edge"));
        check(Math.abs(Math.abs(get(farther,"hole").y1)-22.5)<1e-6&&Math.abs(get(farther,"edge").y1)<1e-9,"edited distance 22.5, the line stays");
        SketchPrimitive other=new SketchPrimitive("o","Okrąg",80,30,82,30);
        SketchConstraints.Link pp=new SketchConstraints.Link("distance","o",0,"hole",0,40,1);
        List<SketchPrimitive> pair=SketchConstraints.connect(new ArrayList<>(Arrays.asList(hole,other)),new ArrayList<>(),pp);
        double[] c1={get(pair,"hole").x1,get(pair,"hole").y1},c2={get(pair,"o").x1,get(pair,"o").y1};
        check(Math.abs(Math.hypot(c1[0]-c2[0],c1[1]-c2[1])-40)<1e-6,"two centres 40 mm apart");
        check(NonlinearConstraints.status(part,new ArrayList<>()).freedoms-NonlinearConstraints.status(placed,dl).freedoms==1,"a distance removes one degree of freedom");
        check(SketchConstraints.fromJson(SketchConstraints.toJson(dl),placed).get(0).kind.equals("distance"),"distance round-trips through JSON");
        try{SketchConstraints.connect(part,new ArrayList<>(),new SketchConstraints.Link("distance","hole",0,"edge",0,0,1));check(false,"zero point distance");}catch(IllegalArgumentException e){check(true,"a zero point–point distance is refused");}
        System.out.println("PASS SketchAngleHostTest: "+checks+" checks");
    }
}
