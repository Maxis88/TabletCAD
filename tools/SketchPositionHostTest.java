import java.util.*;
import pl.tabletcad.app.SketchConstraints;
import pl.tabletcad.app.SketchHit;
import pl.tabletcad.app.SketchPosition;
import pl.tabletcad.app.model.SketchPrimitive;

/** 0.86 sketch moving and positioning: interior picking, placement from the origin or another element, alignment. */
public final class SketchPositionHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static boolean near(double a,double b){return Math.abs(a-b)<1e-6;}
    static SketchPrimitive get(List<SketchPrimitive> s,String id){for(SketchPrimitive p:s)if(p.id.equals(id))return p;throw new AssertionError(id);}
    public static void main(String[] args) {
        SketchPrimitive plate=new SketchPrimitive("plate","Prostokąt",0,0,100,60);
        SketchPrimitive hole=new SketchPrimitive("hole","Okrąg",30,30,35,30);
        SketchPrimitive line=new SketchPrimitive("edge","Linia",120,-10,120,80);
        List<SketchPrimitive> sketch=new ArrayList<>(Arrays.asList(plate,hole,line));
        // Interior picking: a finger inside the circle grabs the circle, not the plate around it.
        check(SketchHit.inside(hole,31,31),"point inside the circle is inside");
        check(!SketchHit.inside(hole,40,30),"point outside the circle is outside");
        check(SketchHit.smallestContaining(sketch,31,31,null)==hole,"nested outlines: the smallest (hole) is picked");
        check(SketchHit.smallestContaining(sketch,80,10,null)==plate,"inside the plate only: the plate is picked");
        check(SketchHit.smallestContaining(sketch,150,10,null)==null,"outside everything: nothing is picked");
        check(SketchHit.smallestContaining(sketch,120,10,null)==null,"open line has no interior");
        // Placement from the origin with driving dimensions.
        SketchPosition.Result r=SketchPosition.place(sketch,new ArrayList<>(),"hole",0,null,0,25,40,true,true);
        SketchPrimitive moved=get(r.sketch,"hole");
        check(near(moved.x1,25)&&near(moved.y1,40),"circle centre placed at X25 Y40 from the origin");
        check(near(Math.hypot(moved.x2-moved.x1,moved.y2-moved.y1),5),"radius unchanged by placement");
        check(r.added==2&&r.links.size()==2,"two driving position dimensions added");
        for(SketchConstraints.Link l:r.links)check(SketchConstraints.fromOrigin(l),"dimension measured from the origin: "+l.kind);
        // Replacing: placing again changes the value instead of adding conflicting dimensions.
        SketchPosition.Result again=SketchPosition.place(r.sketch,r.links,"hole",0,null,0,50,30,true,true);
        check(again.links.size()==2&&again.removed==2,"re-placing replaces the old position dimensions");
        check(near(get(again.sketch,"hole").x1,50),"new X position applied");
        // Placement from a point of another element (corner of the plate).
        SketchPosition.Result rel=SketchPosition.place(sketch,new ArrayList<>(),"hole",0,"plate",2,-15,-12,true,true);
        SketchPrimitive h=get(rel.sketch,"hole");
        check(near(h.x1,85)&&near(h.y1,48),"centre 15 mm left and 12 mm below the plate corner (100,60)");
        double[] off=SketchPosition.offset(rel.sketch,"hole",0,"plate",2);
        check(near(off[0],-15)&&near(off[1],-12),"offset reported relative to the reference");
        for(SketchConstraints.Link l:rel.links)check(!SketchConstraints.fromOrigin(l)&&l.a.equals("plate"),"dimension measured from the plate: "+l.kind);
        // The dimension follows the reference: moving the plate corner moves the hole.
        List<SketchPrimitive> edited=SketchConstraints.edit(rel.sketch,rel.links,"plate",new SketchPrimitive("plate","Prostokąt",0,0,120,60));
        SketchPrimitive follow=get(edited,"hole");System.out.println("follow "+follow.x1+","+follow.y1+" r="+Math.hypot(follow.x2-follow.x1,follow.y2-follow.y1)+" plate x2="+get(edited,"plate").x2);
        check(near(follow.x1,105)&&near(Math.hypot(follow.x2-follow.x1,follow.y2-follow.y1),5),"widening the plate moves the dimensioned hole with its corner, radius kept");
        // Rectangle centre placement keeps size and lands exactly.
        SketchPosition.Result centre=SketchPosition.place(sketch,new ArrayList<>(),"plate",SketchPosition.CENTRE,null,0,0,0,true,true);
        SketchPrimitive p=get(centre.sketch,"plate");
        check(near((p.x1+p.x2)/2,0)&&near((p.y1+p.y2)/2,0)&&near(Math.abs(p.x2-p.x1),100),"rectangle centred on the origin with unchanged size");
        // Alignment to a reference element.
        SketchPosition.Result right=SketchPosition.align(sketch,new ArrayList<>(),"plate","edge",4,true);
        p=get(right.sketch,"plate");
        check(near(Math.max(p.x1,p.x2),120)&&near(p.y1,0),"right edge of the plate aligned with the vertical line, Y unchanged");
        check(right.links.size()==1&&right.links.get(0).kind.equals("dimX"),"alignment on X kept by one driving X dimension");
        SketchPrimitive ring=new SketchPrimitive("ring","Okrąg",70,20,80,20);
        List<SketchPrimitive> two=new ArrayList<>(Arrays.asList(hole,ring));
        SketchPosition.Result conc=SketchPosition.align(two,new ArrayList<>(),"hole","ring",0,true);
        h=get(conc.sketch,"hole");
        check(near(h.x1,70)&&near(h.y1,20),"centre-to-centre alignment makes the circles concentric");
        check(near(Math.hypot(h.x2-h.x1,h.y2-h.y1),5),"aligned circle keeps its radius");
        SketchPosition.Result loose=SketchPosition.align(two,new ArrayList<>(),"hole","ring",0,false);
        check(loose.links.isEmpty()&&near(get(loose.sketch,"hole").x1,70),"alignment without lock only moves");
        try{SketchPosition.align(two,new ArrayList<>(),"hole","hole",0,true);check(false,"self reference rejected");}
        catch(IllegalArgumentException e){check(true,"self reference rejected");}
        check(SketchPosition.pointNames(plate).get(0).startsWith("Środek"),"rectangle offers its centre first");
        System.out.println("PASS SketchPositionHostTest: "+checks+" checks");
    }
}
