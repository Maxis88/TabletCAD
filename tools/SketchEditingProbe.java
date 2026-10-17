import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class SketchEditingProbe {
    static void require(boolean value,String label){if(!value)throw new AssertionError(label);System.out.println("PASS "+label);}
    static double area(double[] vertices){double a=0;for(int i=0;i<vertices.length;i+=2){int n=(i+2)%vertices.length;a+=vertices[i]*vertices[n+1]-vertices[n]*vertices[i+1];}return Math.abs(a/2);}
    static void rejected(Runnable action,String label){try{action.run();throw new AssertionError(label);}catch(IllegalArgumentException expected){System.out.println("PASS "+label);}}
    public static void main(String[] args)throws Exception {
        SketchPrimitive locked=SketchDimensions.apply(new SketchPrimitive("guide","Linia",10,20,40,20),30,0,true);
        SketchPrimitive guide=SketchEditing.toggleConstruction(locked);
        require(guide.type.equals("Pomocnicza")&&guide.id.equals(locked.id),"construction conversion retains stable identity");
        require(guide.driving!=null&&guide.driving[0]==30&&guide.x1==10&&guide.x2==40,"conversion retains geometry and driving dimensions");
        SketchPrimitive ordinary=SketchEditing.toggleConstruction(guide);
        require(ordinary.type.equals("Linia")&&ordinary.driving!=null,"construction conversion reversible without unlock");
        require(SketchPrimitive.fromJson(guide.toJson()).type.equals("Pomocnicza"),"construction conversion persists JSON");
        SketchPrimitive rect=new SketchPrimitive("rect","Prostokąt",20,10,-10,-10);
        List<SketchPrimitive> edges=SketchEditing.explode(rect);
        require(edges.size()==4&&edges.get(0).id.equals(rect.id),"reversed rectangle split preserves first identity");
        Set<String> ids=new HashSet<>();for(SketchPrimitive edge:edges)ids.add(edge.id);
        require(ids.size()==4,"split generates unique segment identities");
        require(Math.abs(area(SketchContours.join(edges,rect.id).profile.vertices)-600)<1e-8,"split and rejoin preserve rectangle area");
        SketchPrimitive polygon=new SketchPrimitive("poly",new double[]{0,0,20,0,15,10,0,10});
        List<SketchPrimitive> split=SketchEditing.explode(polygon);
        SketchPrimitive rejoined=SketchContours.join(split,polygon.id).profile;
        require(Arrays.equals(polygon.vertices,rejoined.vertices),"polygon split and rejoin preserve all vertices");
        SketchPrimitive helper=SketchEditing.toggleConstruction(split.get(1));split.set(1,helper);
        rejected(()->SketchContours.join(split,polygon.id),"helper excluded from solid contour");
        split.set(1,SketchEditing.toggleConstruction(helper));
        require(SketchContours.join(split,polygon.id).profile.closedProfile(),"restored contour line closes profile again");
        CadProject project=new CadProject();project.sketch.addAll(split);project.archiveCurrentSketch();
        CadProject restored=CadProject.fromJson(project.toJson());
        require(restored.sketch.size()==4&&restored.savedSketches.size()==1,"split geometry survives project and archive serialization");
        rejected(()->SketchEditing.explode(locked),"dimension locks not silently lost");
        rejected(()->SketchEditing.explode(SketchDimensions.apply(rect,30,20,true)),"locked rectangle cannot be split");
        rejected(()->SketchEditing.explode(new SketchPrimitive("c","Okrąg",0,0,10,0)),"circle not approximated destructively as lines");
        rejected(()->SketchEditing.toggleConstruction(polygon),"unsupported construction conversion explicit");
        double[] base=SketchContours.join(restored.sketch,polygon.id).profile.appendVertices(new double[]{6,0,0,20,10,0,8});
        double[] mesh=NativeCadEngine.evaluateFeatures(80,48,32,0,base,new double[0],null);
        double volume=0;for(int i=0;i<mesh.length;i+=13)volume+=mesh[i]*(mesh[i+4]*mesh[i+8]-mesh[i+5]*mesh[i+7])
            +mesh[i+1]*(mesh[i+5]*mesh[i+6]-mesh[i+3]*mesh[i+8])+mesh[i+2]*(mesh[i+3]*mesh[i+7]-mesh[i+4]*mesh[i+6]);
        require(mesh.length>0&&Math.abs(Math.abs(volume/6)-1400)<1e-5,"converted and rejoined sketch creates exact OCCT volume");
        System.out.println("ALL SKETCH EDITING TESTS PASSED");
    }
}
