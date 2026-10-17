import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.SketchPrimitive;

/** Pure geometry regression; runnable on the host without a device or JNI. */
public final class CurveGeometryProbe {
    static void require(boolean b,String s){if(!b)throw new AssertionError(s);System.out.println("PASS "+s);}
    static void close(double a,double b,double tolerance){if(Math.abs(a-b)>tolerance)throw new AssertionError(a+" != "+b);}
    static void reject(Runnable action,String s){boolean rejected=false;try{action.run();}catch(IllegalArgumentException e){rejected=true;}require(rejected,s);}
    static double[] concat(double[]... parts){int count=0;for(double[] p:parts)count+=p.length;double[] result=new double[count];int i=0;for(double[] p:parts){System.arraycopy(p,0,result,i,p.length);i+=p.length;}return result;}
    static double[] line(double x,double y,double u,double v){return new double[]{0,x,y,(x+u)/2,(y+v)/2,u,v};}
    static List<SketchPrimitive> read(double[] s)throws Exception {StringWriter w=new StringWriter();w.write("0\nSECTION\n2\nENTITIES\n");SketchCurves.writeDxf(w,s);w.write("0\nENDSEC\n0\nEOF\n");return CadExchange.readDxf(new ByteArrayInputStream(w.toString().getBytes(StandardCharsets.US_ASCII)));}
    public static void main(String[] args)throws Exception {
        Random random=new Random(39024);int checks=0;
        for(int i=0;i<300;i++) {
            double cx=random.nextDouble()*200-100,cy=random.nextDouble()*200-100,r=1+random.nextDouble()*100,start=random.nextDouble()*Math.PI*2,sweep=(.1+random.nextDouble()*6)*(i%2==0?1:-1);
            double[] s=SketchCurves.arc(cx+r*Math.cos(start),cy+r*Math.sin(start),cx+r*Math.cos(start+sweep*.4),cy+r*Math.sin(start+sweep*.4),cx+r*Math.cos(start+sweep),cy+r*Math.sin(start+sweep));
            SketchCurves.Arc a=SketchCurves.arc(s,0),reversed=SketchCurves.arc(SketchCurves.reverse(s),0);
            close(a.r,r,1e-7);close(a.sweep,sweep,1e-7);close(reversed.sweep,-sweep,1e-7);
            for(int j=0;j<=20;j++){double[] p=a.point(j/20.0),q=reversed.point(1-j/20.0);close(p[0],q[0],1e-6);close(p[1],q[1],1e-6);if(!a.contains(p[0],p[1]))throw new AssertionError("arc rejects its own points");checks++;}
            SketchCurves.Arc round=SketchCurves.arc(read(s).get(0).curves,0);close(round.r,r,1e-6);close(Math.abs(round.sweep),Math.abs(sweep),1e-7);
            double[] bounds=SketchCurves.bounds(s);
            for(int j=0;j<=100;j++){double[] p=a.point(j/100.0);if(p[0]<bounds[0]-1e-6||p[1]<bounds[1]-1e-6||p[0]>bounds[2]+1e-6||p[1]>bounds[3]+1e-6)throw new AssertionError("analytic bounds miss curve");}
        }
        require(checks==6300,"300 seeded CW/CCW arcs: radius, sweep, reversal, DXF, exact bounds and 6300 point checks");
        double[] upper=SketchCurves.arc(0,0,5,5,10,0),lower=SketchCurves.arc(10,0,5,-5,0,0),circle=concat(upper,lower);
        SketchCurves.validate(circle,true);require(new SketchPrimitive("circle-path",circle,true).closedProfile(),"two complementary arcs form circle without overlap");
        double[] capsule=concat(line(0,0,20,0),SketchCurves.arc(20,0,25,5,20,10),line(20,10,0,10),SketchCurves.arc(0,10,-5,5,0,0));
        SketchPrimitive p=new SketchPrimitive("capsule",capsule,true);close(p.x1,-5,1e-8);close(p.x2,25,1e-8);require(p.curves.length==28,"four-segment capsule validates with analytic bounds");
        SketchPrimitive node=SketchCurves.drag(p,2,1,0);close(node.curves[5],node.curves[8],1e-8);close(node.curves[6],node.curves[9],1e-8);require(node.closedProfile(),"vertex drag keeps neighboring endpoints connected");
        SketchPrimitive mid=SketchCurves.drag(p,1001,1,0);close(mid.curves[10],26,1e-8);require(mid.closedProfile(),"arc control drag preserves closed path");
        List<double[]> hits=SketchCurves.lineHits(new double[]{22,-5,22,15},capsule);require(hits.size()==2,"analytic line intersections with curved contour");
        close(hits.get(0)[1],5-Math.sqrt(21),1e-7);close(hits.get(1)[1],5+Math.sqrt(21),1e-7);
        SketchPrimitive arc=new SketchPrimitive("arc",upper,false),cut=new SketchPrimitive("cut","Linia",-1,2,11,2);
        List<SketchPrimitive> trimmed=SketchTrimmer.trim(Arrays.asList(arc,cut),cut,5,2);require(trimmed.size()==2,"straight segment trims at analytic arc intersections");
        reject(()->SketchCurves.validate(concat(upper,SketchCurves.reverse(upper)),true),"overlapping reversed arcs rejected");
        reject(()->SketchCurves.validate(concat(upper,line(10,0,5,8),line(5,8,0,0)),true),"line crossing arc interior rejected");
        reject(()->SketchCurves.samples(upper,0),"invalid preview resolution rejected");
        reject(()->SketchCurves.arc(0,0,5,0,10,0),"collinear arc rejected");
        System.out.println("ALL HOST CURVE GEOMETRY TESTS PASSED");
    }
}
