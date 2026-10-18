import java.util.*;
import pl.tabletcad.app.SketchCurves;
import pl.tabletcad.app.SketchPattern;
import pl.tabletcad.app.model.SketchPrimitive;

/** Exact linear, grid and circular sketch patterns. */
public final class SketchPatternProbe {
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("PASS "+text);}
    static void close(double a,double b,String text){check(Math.abs(a-b)<=1e-9,text+" ("+a+" vs "+b+")");}
    static void rejects(Runnable r,String text){try{r.run();}catch(IllegalArgumentException e){System.out.println("PASS "+text+": "+e.getMessage());return;}throw new AssertionError(text);}
    public static void main(String[] args) {
        SketchPrimitive hole=new SketchPrimitive("h","Okrąg",10,10,13,10);
        List<SketchPrimitive> row=SketchPattern.linear(hole,4,25,0);check(row.size()==3,"4 instances = source + 3 copies");
        close(row.get(2).x1,85,"third copy centre at 10+3·25");close(Math.hypot(row.get(2).x2-row.get(2).x1,row.get(2).y2-row.get(2).y1),3,"copies keep radius");
        check(new HashSet<>(Arrays.asList(row.get(0).id,row.get(1).id,row.get(2).id,"h")).size()==4,"every copy has a new identity");
        List<SketchPrimitive> grid=SketchPattern.grid(hole,3,2,20,15);check(grid.size()==5,"3×2 grid adds 5 copies");
        close(grid.get(4).x1,50,"last grid column");close(grid.get(4).y1,25,"last grid row");
        List<SketchPrimitive> ring=SketchPattern.circular(hole,6,0,0,360);check(ring.size()==5,"6 around 360° = 5 copies");
        for(SketchPrimitive p:ring)close(Math.hypot(p.x1,p.y1),Math.hypot(10,10),"copy stays on pitch circle");
        close(Math.toDegrees(Math.atan2(ring.get(0).y1,ring.get(0).x1)),45+60,"360°/6 = 60° step");
        List<SketchPrimitive> arc=SketchPattern.circular(hole,3,0,0,90);close(Math.toDegrees(Math.atan2(arc.get(1).y1,arc.get(1).x1)),45+90,"partial angle spans first to last");
        SketchPrimitive rect=new SketchPrimitive("r","Prostokąt",10,-2,20,2);
        List<SketchPrimitive> spokes=SketchPattern.circular(rect,4,0,0,360);
        check(spokes.get(0).type.equals("Prostokąt"),"90° rotation keeps an axis-aligned rectangle");close(spokes.get(0).y2,20,"rotated rectangle extent");
        List<SketchPrimitive> tri=SketchPattern.circular(rect,3,0,0,360);check(tri.get(0).type.equals("Kontur")&&tri.get(0).vertices.length==8,"120° rotation turns rectangle into exact polygon");
        double[] v=tri.get(0).vertices;close(Math.hypot(v[0]-v[2],v[1]-v[3]),10,"rotated polygon edge length kept");
        SketchPrimitive curved=new SketchPrimitive("a",SketchCurves.arc(10,0,0,10,-10,0),false);
        close(SketchCurves.arc(SketchPattern.circular(curved,2,0,0,360).get(0).curves,0).r,10,"arc rotation keeps radius");
        check(SketchPattern.linear(new SketchPrimitive("c","Linia",0,0,1,1).withConstruction(true),2,5,0).get(0).construction,"construction kept");
        rejects(()->SketchPattern.linear(hole,1,5,0),"single instance rejected");
        rejects(()->SketchPattern.linear(hole,3,0,0),"zero spacing rejected");
        rejects(()->SketchPattern.linear(hole,3,3000,0),"pattern outside ±5000 mm rejected");
        rejects(()->SketchPattern.grid(hole,20,20,5,5),"more than 200 instances rejected");
        System.out.println("ALL SKETCH PATTERN TESTS PASSED");
    }
}
