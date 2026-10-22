import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Ellipse relations: a line tangent to an ellipse (both sides), a point on an ellipse, and following edits. */
public final class EllipseConstraintHostTest {
    static int checks;
    static void check(boolean ok,String t){if(!ok)throw new AssertionError(t);checks++;System.out.println("PASS "+t);}
    static SketchPrimitive get(List<SketchPrimitive> g,String id){for(SketchPrimitive p:g)if(p.id.equals(id))return p;throw new AssertionError(id);}
    /** Smallest distance from the ellipse outline to the infinite line. */
    static double[] gap(SketchPrimitive e,SketchPrimitive l){double a=e.major(),ux=(e.x2-e.x1)/a,uy=(e.y2-e.y1)/a,len=Math.hypot(l.x2-l.x1,l.y2-l.y1),nx=(l.y2-l.y1)/len,ny=-(l.x2-l.x1)/len;
        double min=1e9,max=-1e9;for(int i=0;i<20000;i++){double t=2*Math.PI*i/20000,x=e.x1+a*Math.cos(t)*ux-e.minor*Math.sin(t)*uy,y=e.y1+a*Math.cos(t)*uy+e.minor*Math.sin(t)*ux,d=(x-l.x1)*nx+(y-l.y1)*ny;min=Math.min(min,d);max=Math.max(max,d);}return new double[]{min,max};}
    public static void main(String[] args) {
        SketchPrimitive e=SketchPrimitive.ellipse("e",0,0,30,10,12),line=new SketchPrimitive("l","Linia",-50,40,50,45);
        List<SketchPrimitive> g=new ArrayList<>(Arrays.asList(e,line));
        double side=Math.copySign(1,(e.x1-line.x1)*(line.y2-line.y1)-(e.y1-line.y1)*(line.x2-line.x1));
        SketchConstraints.Link t=new SketchConstraints.Link("tangent","e",0,"l",0,side,0);
        List<SketchPrimitive> solved=SketchConstraints.connect(g,new ArrayList<>(),t);
        double[] r=gap(get(solved,"e"),get(solved,"l"));
        check(Math.abs(Math.min(Math.abs(r[0]),Math.abs(r[1])))<1e-4&&r[0]*r[1]>=-1e-6,"line touches the ellipse without crossing it ("+r[0]+".."+r[1]+")");
        check(Math.abs(get(solved,"e").minor-12)<1e-12,"semi-minor axis unchanged");
        // Rotating the ellipse later keeps the line tangent.
        List<SketchConstraints.Link> links=new ArrayList<>(Collections.singletonList(t));
        SketchPrimitive turned=SketchPrimitive.ellipse("e",0,0,0,30,12);
        List<SketchPrimitive> edited=SketchConstraints.edit(solved,links,"e",turned);
        double[] r2=gap(get(edited,"e"),get(edited,"l"));check(Math.min(Math.abs(r2[0]),Math.abs(r2[1]))<1e-4,"tangency kept after the ellipse is turned ("+r2[0]+".."+r2[1]+")");
        // Point of a line end on the ellipse.
        SketchPrimitive seg=new SketchPrimitive("s","Linia",5,5,60,60);
        List<SketchPrimitive> g2=new ArrayList<>(Arrays.asList(SketchPrimitive.ellipse("e2",0,0,30,0,10),seg));
        List<SketchPrimitive> on=SketchConstraints.connect(g2,new ArrayList<>(),new SketchConstraints.Link("pointOn","s",0,"e2",0,0,0));
        SketchPrimitive s2=get(on,"s");check(Math.abs(Math.hypot(s2.x1/30,s2.y1/10)-1)<1e-6,"line end lies on the ellipse");
        System.out.println("PASS EllipseConstraintHostTest: "+checks+" checks");
    }
}
