import java.util.*;
import pl.tabletcad.app.SketchSnapper;
import pl.tabletcad.app.model.SketchPrimitive;

public final class SketchWorkflowProbe {
    static void check(double[] value,double x,double y,String label) {
        if(Math.hypot(value[0]-x,value[1]-y)>1e-8) throw new AssertionError(label+Arrays.toString(value));
        System.out.println("PASS "+label);
    }
    public static void main(String[] args) throws Exception {
        List<SketchPrimitive> lines=Arrays.asList(
            new SketchPrimitive("a","Pomocnicza",0,10,20,10),
            new SketchPrimitive("b","Pomocnicza",7,0,7,20));
        check(SketchSnapper.snap(7.2,10.2,lines,null,1,null,null),7,10,"guide intersection");
        check(SketchSnapper.snap(19.8,10.3,lines,null,1,null,null),20,10,"guide endpoint");
        check(SketchSnapper.snap(10.2,10.1,lines,null,1,null,null),10,10,"guide midpoint");
        check(SketchSnapper.snap(13,10.3,lines,null,1,null,null),13,10,"point on guide");
        check(SketchSnapper.snap(30.2,40,lines,null,1,30.0,25.0),30,40,"vertical inference");
        check(SketchSnapper.snap(33,25.2,lines,null,1,30.0,25.0),33,25,"horizontal inference");
        check(SketchSnapper.snap(13,10.3,lines,"a",1,null,null),13,10.3,"exclude dragged geometry");
        check(SketchSnapper.snap(.2,.3,lines,null,1,null,null),0,0,"sketch origin");
        SketchPrimitive restored=SketchPrimitive.fromJson(lines.get(0).toJson());
        if(!restored.type.equals("Pomocnicza")) throw new AssertionError("construction persistence");
        System.out.println("PASS construction persistence");
        System.out.println("ALL SKETCH WORKFLOW TESTS PASSED");
    }
}
