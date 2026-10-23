import java.util.*;
import pl.tabletcad.app.SketchCenters;
import pl.tabletcad.app.SketchShapes;
import pl.tabletcad.app.SketchSnapper;
import pl.tabletcad.app.model.SketchPrimitive;

/** Surface centres shown while sketching and offered as a snap point ("Środek obszaru"). */
public final class SketchCentersHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static boolean at(double[] c,double x,double y){return c!=null&&Math.abs(c[0]-x)<1e-9&&Math.abs(c[1]-y)<1e-9;}
    public static void main(String[] args){
        check(at(SketchCenters.center(new SketchPrimitive("r","Prostokąt",10,20,110,80)),60,50),"rectangle 100×60: centre (60,50)");
        check(at(SketchCenters.center(new SketchPrimitive("c","Okrąg",7,-3,12,-3)),7,-3),"circle: its centre");
        check(at(SketchCenters.center(SketchPrimitive.ellipse("e",4,5,14,5,3)),4,5),"ellipse: its centre");
        check(at(SketchCenters.center(SketchShapes.drag("p","Wielokąt",15,-8,25,-8,6)),15,-8),"hexagon: centre of the polygon");
        check(at(SketchCenters.center(SketchShapes.drag("q","Prostokąt od środka",3,4,13,9,6)),3,4),"centre rectangle: the dragged-from point");
        // L shape: 20×10 + 10×10 above the left part → area 300, centroid (25/3, 20/3).
        double[] l={0,0,20,0,20,10,10,10,10,20,0,20};
        check(at(SketchCenters.center(new SketchPrimitive("l",l)),25.0/3,25.0/3),"L contour: area centroid, not the bounding-box centre");
        double[] cw={0,20,10,20,10,10,20,10,20,0,0,0};
        check(at(SketchCenters.polygon(cw),25.0/3,25.0/3),"clockwise contour gives the same centroid");
        double r=10;double[] half={0,-r,0,0,0,r,0, 1,r,0,0,r,-r,0};
        check(at(SketchCenters.curves(half),0,4*r/(3*Math.PI)),"half disc (line + arc): centroid 4r/3π above the chord, exact");
        double[] slot={0,-10,-5,0,-5,10,-5, 1,10,-5,15,0,10,5, 0,10,5,0,5,-10,5, 1,-10,5,-15,0,-10,-5};
        check(at(SketchCenters.curves(slot),0,0),"symmetric slot (two arcs): centre (0,0)");
        check(SketchCenters.center(new SketchPrimitive("x","Linia",0,0,5,5))==null,"open line has no surface centre");
        check(SketchCenters.center(SketchPrimitive.point("pt",1,1))==null,"point has no surface centre");
        List<SketchPrimitive> g=new ArrayList<>(Collections.singletonList(new SketchPrimitive("r","Prostokąt",10,20,110,80)));
        double[] hit=SketchSnapper.snap(60.4,49.7,g,null,1,null,null);
        check(at(hit,60,50)&&SketchSnapper.lastKind.equals("Środek obszaru"),"rectangle centre attracts the pen: kind Środek obszaru");
        SketchSnapper.snap(60.4,49.7,g,"r",1,null,null);check(!SketchSnapper.lastKind.equals("Środek obszaru"),"the moved shape's own centre is excluded");
        List<SketchPrimitive> target=new ArrayList<>(Collections.singletonList(SketchPrimitive.point("t",203,317)));
        double[] moved=SketchSnapper.translation(g.get(0),143.3,266.8,target,1);
        check(Math.abs(moved[0]-143)<1e-9&&Math.abs(moved[1]-267)<1e-9,"dragging a rectangle centres it on a sketch point");
        System.out.println("PASS SketchCentersHostTest: "+checks+" checks");
    }
}
