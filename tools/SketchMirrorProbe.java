import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class SketchMirrorProbe {
    static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);System.out.println("PASS "+s);}
    static void close(double a,double b){if(Math.abs(a-b)>1e-7)throw new AssertionError(a+" != "+b);}
    static void reject(Runnable r,String s){boolean bad=false;try{r.run();}catch(IllegalArgumentException e){bad=true;}check(bad,s);}
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
    static void run(String[] args)throws Exception {
        SketchPrimitive line=new SketchPrimitive("line","Linia",2,3,8,5,new double[]{Math.hypot(6,2),Math.toDegrees(Math.atan2(2,6))});
        SketchPrimitive copy=SketchMirror.copy(line,"copy",0,0,1,0);
        close(copy.x1,2);close(copy.y1,-3);close(copy.x2,8);close(copy.y2,-5);
        check(copy.driving==null&&line.driving!=null,"copy is independent and leaves source dimensions intact");
        copy=SketchMirror.copy(line,"copy",0,0,0,1);close(copy.x1,-2);close(copy.x2,-8);close(copy.y1,3);
        check(true,"principal X and Y axes reflect exact coordinates");
        SketchPrimitive rect=new SketchPrimitive("rect","Prostokąt",2,3,8,5);
        copy=SketchMirror.copy(rect,"copy",0,0,1,0);check(copy.type.equals("Prostokąt"),"axis-aligned rectangle retains rectangle editing");
        copy=SketchMirror.copy(rect,"copy",0,0,2,1);check(copy.vertices!=null&&copy.closedProfile(),"oblique-axis rectangle becomes exact closed polygon");
        double area=0;for(int i=0;i<copy.vertices.length;i+=2){int j=(i+2)%copy.vertices.length;area+=copy.vertices[i]*copy.vertices[j+1]-copy.vertices[i+1]*copy.vertices[j];}
        close(Math.abs(area/2),12);check(true,"oblique reflection preserves rectangle area");
        SketchPrimitive circle=new SketchPrimitive("circle","Okrąg",4,5,7,5);
        copy=SketchMirror.copy(circle,"copy",1,0,1,1);close(copy.x1,-2);close(copy.y1,5);close(Math.hypot(copy.x2-copy.x1,copy.y2-copy.y1),3);
        check(copy.closedProfile(),"circle reflection preserves analytic radius and closed profile");
        SketchPrimitive arc=new SketchPrimitive("arc",SketchCurves.arc(-10,0,0,10,10,0),false);
        copy=SketchMirror.copy(arc,"copy",0,0,1,0);
        SketchCurves.Arc a=SketchCurves.arc(arc.curves,0),b=SketchCurves.arc(copy.curves,0);
        close(a.r,b.r);close(a.sweep,-b.sweep);check(b.contains(0,-10)&&!b.contains(0,10),"arc reflects analytically and reverses orientation");
        double[] wire=Arrays.copyOf(arc.curves,14);System.arraycopy(new double[]{0,10,0,0,0,-10,0},0,wire,7,7);
        SketchPrimitive profile=new SketchPrimitive("profile",wire,true);
        SketchPrimitive reflected=SketchMirror.copy(profile,"copy",0,0,1,0);
        check(reflected.closedProfile()&&reflected.curves.length==14,"mixed line/arc wire remains a closed exact profile");
        SketchPrimitive roundTrip=SketchMirror.copy(reflected,"back",0,0,1,0);
        check(Arrays.equals(profile.curves,roundTrip.curves),"two principal reflections restore all analytic control points");
        Random random=new Random(42);
        for(int i=0;i<300;i++) {
            double ax=random.nextDouble()*20,ay=random.nextDouble()*20,angle=random.nextDouble()*Math.PI*2;
            double bx=ax+Math.cos(angle),by=ay+Math.sin(angle);
            SketchPrimitive result=SketchMirror.copy(line,"random",ax,ay,bx,by);
            close(Math.hypot(result.x2-result.x1,result.y2-result.y1),Math.hypot(6,2));
            SketchPrimitive back=SketchMirror.copy(result,"back",ax,ay,bx,by);
            close(back.x1,line.x1);close(back.y1,line.y1);close(back.x2,line.x2);close(back.y2,line.y2);
        }
        check(true,"300 seeded oblique reflections preserve length and are involutions");
        reject(()->SketchMirror.copy(line,"copy",0,0,0,0),"zero axis rejected");
        reject(()->SketchMirror.copy(line,"line",0,0,1,0),"source identity cannot be reused");
        reject(()->SketchMirror.copy(line,"copy",Double.NaN,0,1,0),"nonfinite axis rejected");
        reject(()->SketchMirror.copy(line,"copy",5000,0,5000,1),"out-of-range reflected geometry rejected");
        CadProject p=CadProject.blank();p.sketch.add(reflected);CadProject saved=CadProject.fromJson(p.toJson());
        check(Arrays.equals(saved.sketch.get(0).curves,reflected.curves),"mirrored curved profile survives project JSON");
        if(args.length>0) {
            double[] base=reflected.appendVertices(new double[]{reflected.featureType(),reflected.x1,reflected.y1,reflected.x2,reflected.y2,0,8});
            double volume=CurveTrimProbe.volume(NativeCadEngine.evaluateFeatures(40,30,20,0,base,new double[0],args[0]));
            check(Math.abs(volume-Math.PI*100*4)<8,"mirrored analytic profile extrudes as native half-cylinder");
            check(NativeCadEngine.tessellateStep(args[0]).length>0,"mirrored native profile survives STEP round trip");
        }
        System.out.println("ALL SKETCH MIRROR TESTS PASSED");
    }
}
