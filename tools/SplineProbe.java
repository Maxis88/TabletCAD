import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import java.util.*;
import java.io.*;

public final class SplineProbe {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    static void close(double a,double b,String label){check(Math.abs(a-b)<1e-8,label);}
    public static void main(String[] args)throws Exception {
        double[] points={0,0,10,20,30,-10,40,0};double[] spans=SketchSpline.spans(points);
        for(int i=0;i<3;i++)for(int k=0;k<2;k++){
            close(spans[i*8+k],points[i*2+k],"span starts at interpolation point");
            close(spans[i*8+6+k],points[i*2+2+k],"span ends at interpolation point");
            if(i<2){close(spans[i*8+6+k]-spans[i*8+4+k],spans[i*8+10+k]-spans[i*8+8+k],"first derivative continuous");
                close(spans[i*8+6+k]-2*spans[i*8+4+k]+spans[i*8+2+k],spans[i*8+12+k]-2*spans[i*8+10+k]+spans[i*8+8+k],"second derivative continuous");}
        }
        SketchPrimitive spline=new SketchPrimitive("spline",points,"Splajn");
        check(!spline.closedProfile(),"open spline cannot become a polygon extrusion");
        try{spline.featureType();throw new AssertionError("spline interpreted as polygon");}catch(IllegalArgumentException expected){check(true,"open spline rejected at modeling boundary");}
        SketchPrimitive restored=SketchPrimitive.fromJson(spline.toJson());check(restored.type.equals("Splajn")&&Arrays.equals(restored.vertices,points),"spline source points round trip");
        List<SketchPrimitive> geometry=Arrays.asList(spline);
        List<SketchPrimitive> solved=SketchConstraints.connect(geometry,new ArrayList<>(),SketchConstraints.Link.fixed(spline,0));
        check(SketchSpline.is(solved.get(0)),"constraint solving preserves spline identity and type");
        SketchPrimitive mirrored=SketchMirror.copy(spline,"mirror",0,0,1,0);for(int i=0;i<points.length;i+=2){close(mirrored.vertices[i],points[i],"mirror X");close(mirrored.vertices[i+1],-points[i+1],"mirror Y");}
        check(SketchSpline.is(mirrored),"mirror preserves spline type");
        StringWriter writer=new StringWriter();SketchSpline.writeDxf(writer,points);String dxf=writer.toString();
        check(dxf.contains("0\nSPLINE\n")&&dxf.contains("71\n3\n")&&!dxf.contains("0\nLINE\n"),"DXF contains exact cubic spline entity");
        CadProject project=CadProject.blank();project.sketch.add(spline);project.archiveCurrentSketch();project.splineDraft=true;
        CadProject copy=CadProject.fromJson(project.toJson());check(copy.splineDraft&&SketchSpline.is(copy.sketch.get(0)),"project preserves spline and unfinished tool");
        try{SketchSpline.spans(new double[]{0,0,0,0,10,10});throw new AssertionError("duplicate accepted");}catch(IllegalArgumentException expected){check(true,"duplicate points rejected");}
        System.out.println("ALL SPLINE TESTS PASSED");
    }
}
