import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** 0.78: associative sketch patterns and mirror copies ("rigid" relation). */
public final class LinkedCopiesHostTest {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static void near(double a,double b,String label){check(Math.abs(a-b)<1e-7,label+" ("+a+")");}
    static SketchPrimitive by(List<SketchPrimitive> g,String id){for(SketchPrimitive p:g)if(p.id.equals(id))return p;throw new AssertionError(id);}
    public static void main(String[] args)throws Exception {
        // Linear pattern of a Ø10 circle, 3 copies 20 mm apart, linked by translation.
        SketchPrimitive hole=new SketchPrimitive("h","Okrąg",10,10,15,10);
        List<SketchPrimitive> g=new ArrayList<>();g.add(hole);List<SketchConstraints.Link> links=new ArrayList<>();
        List<SketchPrimitive> copies=SketchPattern.linear(hole,4,20,0);g.addAll(copies);
        for(SketchPrimitive c:copies){SketchConstraints.Link l=new SketchConstraints.Link("rigid","h",0,c.id,0,0,0,NonlinearConstraints.translation(c.x1-hole.x1,c.y1-hole.y1));NonlinearConstraints.validate(g,l);links.add(l);}
        check(links.size()==3,"three linked copies");SketchConstraints.validate(g,links);
        // Editing the source (move + radius) moves and resizes every copy.
        SketchPrimitive moved=new SketchPrimitive("h","Okrąg",12,15,18,15);
        List<SketchPrimitive> solved=SketchConstraints.edit(g,links,"h",moved);
        SketchPrimitive third=by(solved,copies.get(2).id);near(third.x1,72,"third copy follows the source X (12 + 3·20)");near(third.y1,15,"and Y");near(Math.hypot(third.x2-third.x1,third.y2-third.y1),6,"and the new radius");
        // Driving radius on the source propagates.
        SketchPrimitive driven=new SketchPrimitive("h","Okrąg",10,10,13.25,10,new double[]{3.25});
        solved=SketchConstraints.edit(g,links,"h",driven);near(Math.hypot(by(solved,copies.get(0).id).x2-by(solved,copies.get(0).id).x1,0),3.25,"driving R3.25 reaches the copies");
        // Copies are fully determined: status freedoms = source only.
        NonlinearConstraints.Status st=NonlinearConstraints.status(g,links);check(st.freedoms==3&&st.redundant==0,"linked copies add no freedoms (only the source circle's 3): "+st.label());
        // Circular pattern of a line about (0,0), 4 instances over 360°.
        SketchPrimitive spoke=new SketchPrimitive("s","Linia",5,0,15,0);List<SketchPrimitive> ring=SketchPattern.circular(spoke,4,0,0,360);
        List<SketchPrimitive> rg=new ArrayList<>(Collections.singletonList(spoke));rg.addAll(ring);List<SketchConstraints.Link> rl=new ArrayList<>();
        for(SketchPrimitive c:ring){double a=Math.toDegrees(Math.atan2(c.y1,c.x1)-Math.atan2(spoke.y1,spoke.x1));SketchConstraints.Link l=new SketchConstraints.Link("rigid","s",0,c.id,0,0,0,NonlinearConstraints.rotation(0,0,a));NonlinearConstraints.validate(rg,l);rl.add(l);}
        List<SketchPrimitive> longer=SketchConstraints.edit(rg,rl,"s",new SketchPrimitive("s","Linia",5,0,25,0));
        SketchPrimitive opposite=by(longer,ring.get(1).id);near(opposite.x2,-25,"rotated copy at 180° follows the longer spoke");near(opposite.y2,0,"exactly on the axis");
        // Mirror of a rectangle about X = 50 (min/max normalised).
        SketchPrimitive rect=new SketchPrimitive("r","Prostokąt",10,10,30,20);SketchPrimitive image=SketchMirror.copy(rect,"m",50,0,50,1);
        List<SketchPrimitive> mg=new ArrayList<>(Arrays.asList(rect,image));SketchConstraints.Link ml=new SketchConstraints.Link("rigid","r",0,"m",0,0,0,NonlinearConstraints.mirror(50,0,50,1));
        NonlinearConstraints.validate(mg,ml);List<SketchPrimitive> mirrored=SketchConstraints.edit(mg,Collections.singletonList(ml),"r",new SketchPrimitive("r","Prostokąt",0,10,30,25));
        SketchPrimitive mi=by(mirrored,"m");near(Math.min(mi.x1,mi.x2),70,"mirrored rectangle min X");near(Math.max(mi.x1,mi.x2),100,"mirrored rectangle max X");near(Math.max(mi.y1,mi.y2),25,"height follows");
        // Mirror of a line about a slanted axis.
        SketchPrimitive line=new SketchPrimitive("l","Linia",0,5,10,5);SketchPrimitive lm=SketchMirror.copy(line,"lm",0,0,1,1);
        SketchConstraints.Link ll=new SketchConstraints.Link("rigid","l",0,"lm",0,0,0,NonlinearConstraints.mirror(0,0,1,1));
        NonlinearConstraints.validate(Arrays.asList(line,lm),ll);check(true,"mirror copy of a line satisfies the relation at creation");
        // Persistence and mismatched kinds.
        CadProject p=new CadProject();p.sketch.addAll(g);p.coincidences.addAll(links);CadProject back=CadProject.fromJson(new JSONObject(p.toJson().toString()));
        check(back.coincidences.size()==3&&back.coincidences.get(0).axis.startsWith("@T:"),"linked copies persist");
        back.sketch.set(1,new SketchPrimitive(copies.get(0).id,"Linia",0,0,1,1));back.pruneCoincidences();check(back.coincidences.size()==2,"a copy that changed kind loses its link");
        boolean refused=false;try{NonlinearConstraints.validate(g,new SketchConstraints.Link("rigid","h",0,copies.get(0).id,0,0,0,NonlinearConstraints.translation(5,0)));}catch(IllegalArgumentException e){refused=true;}
        check(refused,"a relation that the geometry does not satisfy is refused");
        refused=false;try{NonlinearConstraints.validate(g,new SketchConstraints.Link("rigid","h",0,copies.get(0).id,0,0,0,"@T:1,2"));}catch(IllegalArgumentException e){refused=true;}check(refused,"malformed transform refused");
        // Pattern metadata: transform with group and parameters still parses; meta values are readable.
        String tagged=NonlinearConstraints.withGroup(NonlinearConstraints.translation(20,0),"grp","L,4.0,20.0,1.0,20.0");
        check("grp".equals(NonlinearConstraints.meta(tagged,"g"))&&NonlinearConstraints.meta(tagged,"p").startsWith("L,4.0"),"pattern group and parameters are stored with the link");
        NonlinearConstraints.validate(g,new SketchConstraints.Link("rigid","h",0,copies.get(0).id,0,0,0,tagged));check(true,"tagged transform validates like the plain one");
        // Symmetry of circles about a sketch line follows a moved axis.
        SketchPrimitive axisLine=new SketchPrimitive("ax","Linia",50,0,50,10),c1=new SketchPrimitive("c1","Okrąg",40,5,43,5),c2=new SketchPrimitive("c2","Okrąg",60,5,63,5);
        SketchConstraints.Link sym=new SketchConstraints.Link("symmetric","c1",0,"c2",0,0,0,"ax");List<SketchPrimitive> sg=Arrays.asList(axisLine,c1,c2);NonlinearConstraints.validate(sg,sym);check(true,"mirrored circle satisfies symmetry about the axis line");
        List<SketchPrimitive> sm=SketchConstraints.edit(sg,Collections.singletonList(sym),"ax",new SketchPrimitive("ax","Linia",55,0,55,10));
        near(by(sm,"c1").x1+by(sm,"c2").x1,110,"after moving the axis line the circles stay symmetric about X = 55");near(by(sm,"c2").y1,by(sm,"c1").y1,"at the same height");near(Math.hypot(by(sm,"c2").x2-by(sm,"c2").x1,by(sm,"c2").y2-by(sm,"c2").y1),Math.hypot(by(sm,"c1").x2-by(sm,"c1").x1,by(sm,"c1").y2-by(sm,"c1").y1),"radii stay equal");near(Math.hypot(by(sm,"c2").x2-by(sm,"c2").x1,by(sm,"c2").y2-by(sm,"c2").y1),3,"moving the axis keeps the radius (centre carries the radius point)");
        List<SketchPrimitive> sr=SketchConstraints.edit(sg,Collections.singletonList(sym),"c1",new SketchPrimitive("c1","Okrąg",40,5,45,5));near(Math.hypot(by(sr,"c2").x2-by(sr,"c2").x1,by(sr,"c2").y2-by(sr,"c2").y1),5,"source radius change reaches the mirrored circle");
        System.out.println("PASS LinkedCopiesHostTest: "+checks+" checks");
    }
}
