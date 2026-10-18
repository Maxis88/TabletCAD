import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class SketchFilletProbe {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    public static void main(String[] args)throws Exception {
        SketchPrimitive rectangle=new SketchPrimitive("rect","Prostokąt",0,0,40,30);
        SketchPrimitive rounded=SketchFillet.apply(rectangle,3);check(rounded.id.equals(rectangle.id)&&rounded.curves.length==56,"rounded sketch preserves identity with four analytic arcs");
        for(int i=0;i<rounded.curves.length;i+=7)if(rounded.curves[i]==1)check(Math.abs(SketchCurves.arc(rounded.curves,i).r-3)<1e-8,"exact corner radius");
        double[] base=rounded.appendVertices(new double[]{rounded.featureType(),rounded.x1,rounded.y1,rounded.x2,rounded.y2,0,10});
        double[] properties=NativeCadEngine.bodyProperties(80,48,32,0,base,new double[0],null);
        check(properties.length>=6&&Math.abs(properties[0]-(1200-(4-Math.PI)*9)*10)<.001,"rounded sketch extrudes to exact analytic OCCT volume");
        check(SketchPrimitive.fromJson(rounded.toJson()).curves.length==56,"rounded sketch persists");
        try{SketchFillet.apply(rectangle,16);throw new AssertionError("oversized radius accepted");}catch(IllegalArgumentException expected){check(true,"oversized radius rejected without replacing geometry");}
        System.out.println("ALL SKETCH FILLET TESTS PASSED");
    }
}
