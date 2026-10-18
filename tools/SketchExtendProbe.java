import java.util.*;
import pl.tabletcad.app.SketchCurves;
import pl.tabletcad.app.SketchExtend;
import pl.tabletcad.app.model.SketchPrimitive;

/** Exact extension of sketch lines to other geometry. */
public final class SketchExtendProbe {
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("PASS "+text);}
    static void close(double a,double b,String text){check(Math.abs(a-b)<=1e-9,text+" ("+a+" vs "+b+")");}
    static void rejects(SketchPrimitive line,List<SketchPrimitive> s,SketchExtend.End end,String text){try{SketchExtend.extend(line,s,end);}catch(IllegalArgumentException e){System.out.println("PASS "+text+": "+e.getMessage());return;}throw new AssertionError(text);}
    public static void main(String[] args) {
        SketchPrimitive line=new SketchPrimitive("l","Linia",0,0,10,0);
        List<SketchPrimitive> s=new ArrayList<>(Arrays.asList(line,new SketchPrimitive("w","Linia",30,-10,30,10),new SketchPrimitive("near","Linia",-3,-5,-3,5)));
        SketchPrimitive e=SketchExtend.extend(line,s,SketchExtend.End.END);close(e.x2,30,"end reaches vertical line");check(e.x1==0&&e.y2==0&&e.id.equals("l"),"start and identity unchanged");
        e=SketchExtend.extend(line,s,SketchExtend.End.AUTO);close(e.x1,-3,"auto picks the shorter extension (start)");close(e.x2,10,"auto keeps other end");
        e=SketchExtend.extend(line,Arrays.asList(line,new SketchPrimitive("c","Okrąg",50,0,55,0)),SketchExtend.End.END);close(e.x2,45,"stops at near side of circle");
        e=SketchExtend.extend(line,Arrays.asList(line,new SketchPrimitive("r","Prostokąt",20,-5,40,5)),SketchExtend.End.END);close(e.x2,20,"stops at rectangle edge");
        e=SketchExtend.extend(line,Arrays.asList(line,new SketchPrimitive("k",new double[]{25,-5,35,-5,30,5})),SketchExtend.End.END);close(e.x2,27.5,"stops at polygon edge");
        SketchPrimitive arc=new SketchPrimitive("a",SketchCurves.arc(60,-10,70,0,60,10),false);
        e=SketchExtend.extend(line,Arrays.asList(line,arc),SketchExtend.End.END);close(e.x2,70,"arc: only the existing arc portion counts");
        SketchPrimitive diag=new SketchPrimitive("d","Linia",0,0,3,4);
        e=SketchExtend.extend(diag,Arrays.asList(diag,new SketchPrimitive("h","Linia",-100,20,100,20)),SketchExtend.End.END);close(e.x2,15,"diagonal keeps direction (x)");close(e.y2,20,"diagonal keeps direction (y)");
        SketchPrimitive spline=new SketchPrimitive("s",new double[]{40,-10,45,0,40,10},"Splajn");
        e=SketchExtend.extend(line,Arrays.asList(line,spline),SketchExtend.End.END);check(e.x2>40&&e.x2<46&&e.y2==0,"spline crossing found: "+e.x2);
        check(SketchExtend.extend(line.withConstruction(true),s,SketchExtend.End.END).construction,"construction state kept");
        rejects(line,Arrays.asList(line),SketchExtend.End.AUTO,"nothing to extend to");
        rejects(line,Arrays.asList(line,new SketchPrimitive("p","Linia",0,5,10,5)),SketchExtend.End.AUTO,"parallel line is not a boundary");
        rejects(new SketchPrimitive("x","Linia",0,0,10,0,new double[]{10,0}),s,SketchExtend.End.END,"locked length rejected");
        rejects(new SketchPrimitive("o","Okrąg",0,0,5,0),s,SketchExtend.End.END,"only lines extend");
        System.out.println("ALL SKETCH EXTEND TESTS PASSED");
    }
}
