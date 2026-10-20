import java.util.*;
import pl.tabletcad.app.SketchSnapper;
import pl.tabletcad.app.model.SketchPrimitive;

/** 1.0.0-rc3 snapping: circle intersections, tangent from the stroke start, perpendicular foot. */
public final class SketchSnapHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    public static void main(String[] args){
        List<SketchPrimitive> g=new ArrayList<>(Arrays.asList(new SketchPrimitive("a","Okrąg",0,0,10,0),new SketchPrimitive("b","Okrąg",12,0,22,0)));
        double[] hit=SketchSnapper.snap(6.2,7.8,g,null,1,null,null);
        check(Math.abs(hit[0]-6)<1e-9&&Math.abs(hit[1]-8)<1e-9,"two circles R10, centres 12 apart, intersect at (6,8)");
        List<SketchPrimitive> c=new ArrayList<>(Collections.singletonList(new SketchPrimitive("c","Okrąg",0,0,5,0)));
        // Tangent from (13,0): touch point (25/13, 60/13).
        double[] t=SketchSnapper.snap(2.2,4.4,c,null,1,13.0,0.0);
        check(Math.abs(t[0]-25.0/13)<1e-9&&Math.abs(t[1]-60.0/13)<1e-9,"tangent point from the line start");
        double tx=t[0]-13,ty=t[1];check(Math.abs(tx*t[0]+ty*t[1])<1e-9,"the line is perpendicular to the radius (tangent)");
        List<SketchPrimitive> l=new ArrayList<>(Collections.singletonList(new SketchPrimitive("l","Linia",0,0,40,40)));
        double[] f=SketchSnapper.snap(14.7,15.6,l,null,1,30.0,0.0);
        check(Math.abs(f[0]-15)<1e-9&&Math.abs(f[1]-15)<1e-9,"perpendicular foot from the start on a line");
        check(SketchSnapper.tangentPoints(1,0,0,0,5).isEmpty(),"no tangent from inside the circle");
        check(SketchSnapper.circleHits(0,0,1,5,0,1).isEmpty(),"separate circles do not intersect");
        double[] mid=SketchSnapper.snap(20.4,19.6,l,null,1,null,null);
        check(Math.abs(mid[0]-20)<1e-9&&Math.abs(mid[1]-20)<1e-9,"midpoint snapping kept");
        SketchSnapper.snap(6.2,7.8,g,null,1,null,null);check(SketchSnapper.lastKind.equals("Przecięcie"),"kind: intersection");
        SketchSnapper.snap(2.2,4.4,c,null,1,13.0,0.0);check(SketchSnapper.lastKind.equals("Styczna"),"kind: tangent");
        SketchSnapper.snap(14.7,15.6,l,null,1,30.0,0.0);check(SketchSnapper.lastKind.equals("Prostopadła"),"kind: perpendicular");
        SketchSnapper.snap(20.4,19.6,l,null,1,null,null);check(SketchSnapper.lastKind.equals("Środek"),"kind: midpoint");
        SketchSnapper.snap(39.6,40.3,l,null,1,null,null);check(SketchSnapper.lastKind.equals("Punkt"),"kind: end point");
        SketchSnapper.snap(70,-55,l,null,1,null,null);check(SketchSnapper.lastKind.isEmpty(),"free point: no kind");
        System.out.println("PASS SketchSnapHostTest: "+checks+" checks");
    }
}
