import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Trimmed ellipses (elliptic arcs): trim, profile with lines, extrusion volume, transforms, JSON and DXF. */
public final class EllipseArcKernelProbe {
    static int checks;
    static void check(boolean ok,String t){if(!ok)throw new AssertionError(t);checks++;System.out.println("PASS "+t);}
    static void close(String w,double a,double e,double tol){if(Math.abs(a-e)>tol)throw new AssertionError(w+": "+a+" != "+e);checks++;System.out.println("PASS "+w+" = "+a);}
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] r=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,r,at,f.length);at+=f.length;}return r;}
    public static void main(String[] args)throws Exception {
        // Ellipse a=30, b=20 at the origin cut by the vertical line x=0 → right half kept when the left half is tapped.
        SketchPrimitive e=SketchPrimitive.ellipse("e",0,0,30,0,20),cut=new SketchPrimitive("l","Linia",0,-40,0,40);
        List<SketchPrimitive> g=new ArrayList<>(Arrays.asList(e,cut));
        boolean single=false;try{SketchTrimmer.trim(Arrays.asList(e,new SketchPrimitive("t","Linia",30,-40,30,40)),e,-30,0);}catch(IllegalArgumentException x){single=true;}
        check(single,"a closed ellipse with a single (tangent) intersection is not trimmed");
        List<SketchPrimitive> r=SketchTrimmer.trim(g,e,-30,0);check(r.size()==1&&r.get(0).isEllipseArc()&&r.get(0).id.equals("e"),"trimmed ellipse is one elliptic arc keeping the id");
        SketchPrimitive half=r.get(0);double[] d=half.ellipseArc();close("arc sweep is half a turn",d[6]-d[5],Math.PI,1e-9);
        close("arc starts on the line (x)",half.x1,0,1e-9);close("arc ends on the line (x)",half.x2,0,1e-9);check(Math.abs(Math.abs(half.y1)-20)<1e-9&&Math.abs(Math.abs(half.y2)-20)<1e-9,"arc ends at (0, ±20)");
        double[] mid=EllipseArc.point(d,(d[5]+d[6])/2);close("kept half is the right one",mid[0],30,1e-9);
        // Trim the arc again with a horizontal line: two pieces.
        List<SketchPrimitive> two=SketchTrimmer.trim(Arrays.asList(half,new SketchPrimitive("h","Linia",-5,0,40,0)),half,28,-5);
        check(two.size()==1,"trimming an end piece of the arc leaves one arc");
        List<SketchPrimitive> split=SketchTrimmer.trim(Arrays.asList(half,new SketchPrimitive("a","Linia",-5,-12,40,-12),new SketchPrimitive("b","Linia",-5,12,40,12)),half,30,0);
        check(split.size()==2&&split.get(0).isEllipseArc()&&split.get(1).isEllipseArc(),"trimming the middle splits the arc in two");
        // D-shaped profile: half ellipse closed by the line, joined and extruded 10 mm → (π·30·20/2)·10.
        SketchPrimitive line=new SketchPrimitive("l","Linia",0,20,0,-20);
        SketchContours.Result joined=SketchContours.join(Arrays.asList(half,line),"l");
        check(joined.consumed.size()==2&&joined.profile.type.equals("Kontur krzywy"),"half ellipse and line join into a closed curved contour");
        double area=Math.abs(SketchContours.area(SketchCurves.samples(joined.profile.curves,64)));close("contour area equals the half ellipse",area,Math.PI*30*20/2,.01);
        CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=20;p.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(p);
        CadProject top=new CadProject();top.sketchOriginX=50;top.sketchOriginY=30;top.sketchOriginZ=20;
        p.committedFeatures.add(ExtrusionPlan.feature(top,joined.profile,"join",10,0,false,false));p.committedProfileIds.add(joined.profile.id);p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);
        double[] props=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,BodyGraph.pack(p,packed(p)),null);
        check(props.length==7,"D-shaped boss evaluates");close("boss volume within 0.01% of the exact half-ellipse prism",props[0],120000+Math.PI*30*20/2*10,Math.PI*300*10*1e-4);
        SketchPrimitive stub=new SketchPrimitive("s","Linia",50,5,40,5);SketchPrimitive reached=SketchExtend.extend(stub,Arrays.asList(stub,half),SketchExtend.End.END);
        double ex=30*Math.sqrt(1-25.0/400);close("line extended to the elliptic arc",reached.x2,ex,1e-4);
        // JSON, transforms, mirror, offset.
        SketchPrimitive back=SketchPrimitive.fromJson(new org.json.JSONObject(half.toJson().toString()));check(back.isEllipseArc()&&Arrays.equals(back.ellipseArc(),half.ellipseArc()),"elliptic arc saved and read back");
        SketchPrimitive moved=SketchTransform.move(half,5,-2);close("moved start x",moved.x1,half.x1+5,1e-9);
        SketchPrimitive turned=SketchTransform.rotate(half,90,0,0);double[] tm=EllipseArc.point(turned.ellipseArc(),(d[5]+d[6])/2);close("rotated middle (x)",tm[0],0,1e-9);close("rotated middle (y)",tm[1],30,1e-9);
        SketchPrimitive mirrored=SketchMirror.copy(half,"m",0,-1,0,1);double[] mm=mirrored.ellipseArc();double[] mmid=EllipseArc.point(mm,(mm[5]+mm[6])/2);close("mirrored half lies on the left",mmid[0],-30,1e-9);
        SketchPrimitive off=SketchOffset.offset(half,"o",2);check(SketchSpline.is(off)&&!SketchSpline.closed(off.vertices),"arc outline is an open spline");
        // DXF: arc and full ellipse.
        java.io.StringWriter w=new java.io.StringWriter();w.write("0\nSECTION\n2\nENTITIES\n");EllipseArc.writeDxf(w,half.ellipseArc());
        w.write("0\nELLIPSE\n8\n0\n10\n5\n20\n6\n30\n0\n11\n10\n21\n0\n31\n0\n40\n0.5\n41\n0\n42\n6.283185307179586\n");
        w.write("0\nPOINT\n8\n0\n10\n3\n20\n4\n30\n0\n");
        // Bulged polyline: square 0..10 with the top edge a semicircle bulging up (bulge −1 from (10,10) to (0,10) is clockwise… use +1 for counter-clockwise).
        w.write("0\nLWPOLYLINE\n8\n0\n90\n4\n70\n1\n10\n0\n20\n0\n10\n10\n20\n0\n10\n10\n20\n10\n42\n1\n10\n0\n20\n10\n");
        java.io.StringWriter sp=new java.io.StringWriter();SketchSpline.writeDxf(sp,new double[]{0,0,10,8,20,0,30,8});w.write(sp.toString());
        w.write("0\nENDSEC\n0\nEOF\n");
        List<SketchPrimitive> in=CadExchange.readDxf(new java.io.ByteArrayInputStream(w.toString().getBytes("UTF-8")));
        check(in.get(0).isEllipseArc()&&Math.abs(in.get(0).ellipseArc()[6]-in.get(0).ellipseArc()[5]-Math.PI)<1e-9,"DXF elliptic arc read back");
        check(in.get(1).isEllipse()&&Math.abs(in.get(1).minor-5)<1e-9,"DXF full ellipse read back");check(in.get(2).isPoint(),"DXF point read");
        SketchPrimitive bulge=null;for(SketchPrimitive q:in)if(q.curves!=null)bulge=q;check(bulge!=null,"bulged polyline edge becomes an arc");
        double[] bm=SketchCurves.arc(bulge.curves,0).point(.5);close("bulge +1 is a semicircle bulging up (mid y)",bm[1],15,1e-9);
        SketchPrimitive spl=in.get(in.size()-1);check(SketchSpline.is(spl)&&Arrays.equals(spl.vertices,new double[]{0,0,10,8,20,0,30,8}),"TabletCAD spline DXF round trip is exact");
        System.out.println("PASS EllipseArcKernelProbe: "+checks+" checks");
    }
}
