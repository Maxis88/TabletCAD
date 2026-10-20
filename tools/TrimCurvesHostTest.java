import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Trimming lines and arcs at ellipses and splines (exact line–ellipse intersections). */
public final class TrimCurvesHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    public static void main(String[] args){
        SketchPrimitive line=new SketchPrimitive("l","Linia",-20,0,20,0),e=SketchPrimitive.ellipse("e",0,0,10,0,5);
        List<SketchPrimitive> r=SketchTrimmer.trim(new ArrayList<>(Arrays.asList(line,e)),line,0,0);
        check(r.size()==2&&Math.abs(r.get(0).x2+10)<1e-12&&Math.abs(r.get(1).x1-10)<1e-12,"line trimmed exactly at the ellipse (±10)");
        SketchPrimitive slanted=new SketchPrimitive("s","Linia",0,-20,0,20);SketchPrimitive turned=SketchPrimitive.ellipse("t",0,0,6,8,4);
        List<SketchPrimitive> v=SketchTrimmer.trim(new ArrayList<>(Arrays.asList(slanted,turned)),slanted,0,0);
        double y=v.get(0).y2;double pu=(0*0.6+y*0.8)/10,pv=(-0*0.8+y*0.6)/4;
        check(Math.abs(pu*pu+pv*pv-1)<1e-12,"cut point lies on a rotated ellipse");
        SketchPrimitive wave=new SketchPrimitive("w",new double[]{-5,-20, 2,0, -3,20},"Splajn");
        List<SketchPrimitive> w=SketchTrimmer.trim(new ArrayList<>(Arrays.asList(line,wave)),line,15,0);
        check(w.size()==1&&w.get(0).x2>-2&&w.get(0).x2<4,"line trimmed at a spline it crosses ("+w.get(0).x2+")");
        SketchPrimitive circle=new SketchPrimitive("c","Okrąg",0,0,8,0);
        List<SketchPrimitive> c=CurveTrimmer.trim(new ArrayList<>(Arrays.asList(circle,SketchPrimitive.ellipse("e2",0,0,12,0,4))),circle,0,8);
        check(!c.isEmpty()&&c.get(0).curves!=null,"circle trimmed at an ellipse becomes an arc");
        try{SketchTrimmer.trim(new ArrayList<>(Arrays.asList(line,e)),e,0,5);check(false,"ellipse trim");}catch(IllegalArgumentException x){check(true,"trimming the ellipse itself is reported as not supported");}
        System.out.println("PASS TrimCurvesHostTest: "+checks+" checks");
    }
}
