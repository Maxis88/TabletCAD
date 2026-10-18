import pl.tabletcad.app.NativeCadEngine;
import pl.tabletcad.app.SketchCurves;
import pl.tabletcad.app.SketchOffset;
import pl.tabletcad.app.model.SketchPrimitive;

/** Exact offset of sketch lines, circles, rectangles, polygons and line/arc contours. */
public final class SketchOffsetProbe {
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("PASS "+text);}
    static void close(double a,double b,double tol,String text){check(Math.abs(a-b)<=tol,text+" ("+a+" vs "+b+")");}
    static void rejects(SketchPrimitive p,double d,String text){try{SketchOffset.offset(p,"x",d);}catch(IllegalArgumentException e){System.out.println("PASS "+text+": "+e.getMessage());return;}throw new AssertionError(text);}
    static double area(double[] v){double a=0;for(int i=0;i+3<v.length;i+=2)a+=v[i]*v[i+3]-v[i+2]*v[i+1];return Math.abs(a)/2;}
    static double polygonArea(double[] v){double a=0;int n=v.length/2;for(int i=0;i<n;i++){int j=(i+1)%n;a+=v[2*i]*v[2*j+1]-v[2*j]*v[2*i+1];}return Math.abs(a)/2;}
    public static void main(String[] args) {
        SketchPrimitive rect=new SketchPrimitive("r","Prostokąt",0,0,100,60);
        SketchPrimitive out=SketchOffset.offset(rect,"o",5);close(out.x1,-5,1e-12,"rectangle outward x1");close(out.y2,65,1e-12,"rectangle outward y2");
        SketchPrimitive in=SketchOffset.offset(rect,"i",-5);close(in.x2-in.x1,90,1e-12,"rectangle inward width");close(in.y2-in.y1,50,1e-12,"rectangle inward height");
        rejects(rect,-31,"rectangle collapse rejected");
        SketchPrimitive circle=new SketchPrimitive("c","Okrąg",10,10,20,10);
        SketchPrimitive bigger=SketchOffset.offset(circle,"c2",2.5);close(Math.hypot(bigger.x2-bigger.x1,bigger.y2-bigger.y1),12.5,1e-12,"circle radius +2.5");
        check(bigger.x1==10&&bigger.y1==10,"circle offset is concentric");rejects(circle,-10,"circle collapse rejected");
        SketchPrimitive line=SketchOffset.offset(new SketchPrimitive("l","Linia",0,0,10,0),"l2",3);
        check(line.y1==3&&line.y2==3&&line.x1==0&&line.x2==10,"line moves to its left side");
        check(SketchOffset.offset(new SketchPrimitive("h","Linia",0,0,10,0).withConstruction(true),"h2",1).construction,"construction state kept");
        double[] cwSquare={0,0,0,10,10,10,10,0};
        SketchPrimitive sq=SketchOffset.offset(new SketchPrimitive("s",cwSquare),"s2",1);
        close(polygonArea(sq.vertices),144,1e-9,"clockwise polygon still offsets outward");
        double[] l={0,0,20,0,20,10,10,10,10,20,0,20};
        SketchPrimitive lo=SketchOffset.offset(new SketchPrimitive("L",l),"L2",1);
        double[] expected={-1,-1,21,-1,21,11,11,11,11,21,-1,21};
        for(int i=0;i<expected.length;i++)close(lo.vertices[i],expected[i],1e-9,"concave L vertex "+i);
        SketchPrimitive li=SketchOffset.offset(new SketchPrimitive("L",l),"L3",-1);close(polygonArea(li.vertices),18*8+8*10,1e-9,"concave L inward area");
        rejects(new SketchPrimitive("L",l),-5.5,"concave L collapse rejected");
        double c=Math.cos(Math.PI/4)*5;
        double[] rounded={
            0,5,0,20,0,35,0,  1,35,0,35+c,5-c,40,5,
            0,40,5,40,10,40,15, 1,40,15,35+c,15+c,35,20,
            0,35,20,20,20,5,20, 1,5,20,5-c,15+c,0,15,
            0,0,15,0,10,0,5,  1,0,5,5-c,5-c,5,0};
        SketchPrimitive rr=new SketchPrimitive("rr",rounded,true);
        double base=800-(4-Math.PI)*25,perimeter=80+Math.PI*10;
        close(area(SketchCurves.samples(rr.curves,4000)),base,0.01,"rounded rectangle reference area");
        SketchPrimitive rro=SketchOffset.offset(rr,"rro",2);
        close(area(SketchCurves.samples(rro.curves,4000)),base+perimeter*2+Math.PI*4,0.01,"rounded rectangle outward area (A+P·d+πd²)");
        close(SketchCurves.arc(rro.curves,7).r,7,1e-9,"outward fillet radius 5+2");
        double[] profile=new double[7+rro.curves.length];profile[0]=1000+rro.curves.length/7;profile[6]=10;System.arraycopy(rro.curves,0,profile,7,rro.curves.length);
        double[] solid=NativeCadEngine.bodyProperties(1,1,1,0,profile,new double[0],null);
        check(solid.length==7,"offset contour extrudes as OCCT solid");close(solid[0],(base+perimeter*2+Math.PI*4)*10,1e-6,"exact B-Rep volume of extruded offset profile");
        SketchPrimitive rri=SketchOffset.offset(rr,"rri",-2);
        close(area(SketchCurves.samples(rri.curves,4000)),base-perimeter*2+Math.PI*4,0.01,"rounded rectangle inward area (A−P·d+πd²)");
        close(SketchCurves.arc(rri.curves,7).r,3,1e-9,"inward fillet radius 5−2");
        rejects(rr,-6,"inward offset larger than fillet radius rejected");
        double[] d={0,0,0,10,0,20,0,  1,20,0,25,10,20,20,  0,20,20,10,20,0,20,  0,0,20,0,10,0,0};
        SketchPrimitive dOut=SketchOffset.offset(new SketchPrimitive("d",d,true),"d2",1);
        SketchCurves.Arc arc=SketchCurves.arc(dOut.curves,7);close(arc.r,13.5,1e-9,"non-tangent arc offset radius");
        close(dOut.curves[6],-1,1e-9,"bottom line moved down");
        close(dOut.curves[5],12.5+Math.sqrt(13.5*13.5-11*11),1e-9,"line/arc corner is their exact intersection");
        close(Math.hypot(dOut.curves[8]-12.5,dOut.curves[9]-10),13.5,1e-9,"arc start lies on offset circle");
        SketchPrimitive arcOnly=new SketchPrimitive("a",SketchCurves.arc(10,0,0,10,-10,0),false);
        close(SketchCurves.arc(SketchOffset.offset(arcOnly,"a2",4).curves,0).r,14,1e-9,"open arc concentric radius +4");
        System.out.println("ALL SKETCH OFFSET TESTS PASSED");
    }
}
