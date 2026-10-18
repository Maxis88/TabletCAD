import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.SketchConstraints.Link;

public final class RelationProbe {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    static SketchPrimitive line(String id,double x,double y,double bx,double by){return new SketchPrimitive(id,"Linia",x,y,bx,by);}
    static SketchPrimitive circle(String id,double x,double y,double radius){return new SketchPrimitive(id,"Okrąg",x,y,x+radius,y);}
    static void run(String kind,SketchPrimitive a,SketchPrimitive b,double side,String axis,SketchPrimitive... extra)throws Exception {
        List<SketchPrimitive> geometry=new ArrayList<>(Arrays.asList(a,b));geometry.addAll(Arrays.asList(extra));Link link=new Link(kind,a.id,0,b.id,0,side,0,axis);
        List<SketchPrimitive> solved=SketchConstraints.connect(geometry,new ArrayList<>(),link);SketchConstraints.validate(solved,Arrays.asList(link));
        check(solved.get(0).id.equals(a.id)&&solved.get(1).id.equals(b.id),"relation retains entity identities "+kind);
        CadProject project=CadProject.blank();project.sketch.addAll(solved);project.coincidences.add(link);CadProject restored=CadProject.fromJson(project.toJson());check(restored.coincidences.get(0).kind.equals(kind),"relation persists and validates "+kind);
        SketchPrimitive first=solved.get(0);if(kind.equals("parallel")||kind.equals("perpendicular")) {
            SketchPrimitive changed=line(first.id,first.x1+3,first.y1+2,first.x2+4,first.y2+6);
            List<SketchPrimitive> edited=SketchConstraints.edit(solved,Arrays.asList(link),first.id,changed);SketchConstraints.validate(edited,Arrays.asList(link));check(Math.abs(edited.get(0).x2-changed.x2)<1e-6,"related geometry follows edited line "+kind);
        }
    }
    public static void main(String[] args)throws Exception {
        run("parallel",line("a",0,0,20,0),line("b",0,10,0,20),0,"");
        run("perpendicular",line("a",0,0,20,0),line("b",0,10,20,10),0,"");
        run("equal",line("a",0,0,20,0),line("b",0,10,8,15),0,"");
        run("equal",circle("a",0,0,5),circle("b",20,0,2),0,"");
        run("concentric",circle("a",0,0,5),circle("b",20,10,2),0,"");
        run("tangent",line("a",0,0,20,0),circle("b",10,10,3),-1,"");
        run("tangent",circle("a",0,0,5),circle("b",20,10,2),1,"");
        run("tangent",circle("a",0,0,5),circle("b",20,10,2),-1,"");
        run("pointOn",line("a",5,10,15,15),line("b",0,0,20,0),0,"");
        run("pointOn",line("a",5,10,15,15),circle("b",0,0,5),0,"");
        run("symmetric",line("a",3,5,10,12),line("b",2,-7,8,-9),0,"@X");
        run("symmetric",line("a",3,5,10,12),line("b",2,-7,8,-9),0,"axis",line("axis",0,0,10,10));
        List<SketchPrimitive> locked=Arrays.asList(SketchDimensions.apply(line("a",0,0,20,0),20,0,true),SketchDimensions.apply(line("b",0,10,20,10),20,0,true));
        try{SketchConstraints.connect(locked,new ArrayList<>(),new Link("perpendicular","a",0,"b",0,0,0));throw new AssertionError("inconsistent locks accepted");}catch(IllegalArgumentException expected){check(locked.get(1).y2==10,"conflicting relation preserves original geometry");}
        List<SketchPrimitive> parallel=Arrays.asList(line("a",0,0,20,0),line("b",0,10,20,10));Link relation=new Link("parallel","a",0,"b",0,0,0);
        List<SketchPrimitive> rotated=SketchConstraints.edit(parallel,Arrays.asList(relation),"a",line("a",0,0,0,20));SketchConstraints.validate(rotated,Arrays.asList(relation));check(Math.abs(rotated.get(1).x2-rotated.get(1).x1)<1e-6,"parallel relation follows exact 90 degree edit without singularity");
        Link fixed=new Link("fixed","a",0,"a",0,0,0);
        try{SketchConstraints.edit(parallel,Arrays.asList(relation,fixed),"a",line("a",2,0,20,0));throw new AssertionError("fixed point edit accepted");}catch(IllegalArgumentException expected){check(parallel.get(0).x1==0,"fixed nonlinear component rejects explicit conflicting point edit");}
        System.out.println("ALL RELATION TESTS PASSED");
    }
}
