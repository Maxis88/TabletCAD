import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class CurveTrimProbe {
    static void require(boolean v,String s){if(!v)throw new AssertionError(s);System.out.println("PASS "+s);}
    static void close(double a,double b){if(Math.abs(a-b)>1e-6)throw new AssertionError(a+" != "+b);}
    static void reject(Runnable r,String s){boolean bad=false;try{r.run();}catch(IllegalArgumentException e){bad=true;}require(bad,s);}
    static SketchPrimitive line(String id,double x,double y,double u,double v){return new SketchPrimitive(id,"Linia",x,y,u,v);}
    static SketchPrimitive circle(String id,double x,double y,double r){return new SketchPrimitive(id,"Okrąg",x,y,x+r,y);}
    static SketchPrimitive arc(String id,double ax,double ay,double mx,double my,double bx,double by){return new SketchPrimitive(id,SketchCurves.arc(ax,ay,mx,my,bx,by),false);}
    static SketchCurves.Arc curve(SketchPrimitive p){require(p.curves!=null&&!p.closedProfile(),"trim returns open analytic arc");return SketchCurves.arc(p.curves,0);}
    static double[] concat(double[]... parts){int n=0;for(double[] p:parts)n+=p.length;double[] r=new double[n];int i=0;for(double[] p:parts){System.arraycopy(p,0,r,i,p.length);i+=p.length;}return r;}
    static double[] segment(SketchPrimitive p){return p.curves!=null?p.curves:new double[]{0,p.x1,p.y1,0,0,p.x2,p.y2};}
    static double volume(double[] m){require(m.length>0,"nonempty trimmed OCCT solid");double v=0;for(int i=0;i<m.length;i+=13)v+=m[i]*(m[i+4]*m[i+8]-m[i+5]*m[i+7])+m[i+1]*(m[i+5]*m[i+6]-m[i+3]*m[i+8])+m[i+2]*(m[i+3]*m[i+7]-m[i+4]*m[i+6]);return Math.abs(v/6);}
    public static void main(String[] args)throws Exception {
        SketchPrimitive c=circle("circle",0,0,10),diameter=line("diameter",10,0,-10,0);
        double[] snapped=SketchSnapper.snap(-10.02,.01,Collections.singletonList(c),null,.3,null,null);
        close(snapped[0],-10);close(snapped[1],0);require(true,"circle cardinal endpoint snaps exactly for later contour joining");
        snapped=SketchSnapper.snap(7.1,7.1,Collections.singletonList(c),null,.3,null,null);
        close(Math.hypot(snapped[0],snapped[1]),10);require(true,"arbitrary circle circumference projection is analytic");
        List<SketchPrimitive> lower=SketchTrimmer.trim(Arrays.asList(c,diameter),c,0,10);
        require(lower.size()==1&&lower.get(0).id.equals(c.id),"circle upper half removed retaining identity");
        SketchCurves.Arc a=curve(lower.get(0));close(a.r,10);close(Math.abs(a.sweep),Math.PI);require(a.contains(0,-10)&&!a.contains(0,10),"lower half remains across angular seam");
        List<SketchPrimitive> upper=SketchTrimmer.trim(Arrays.asList(c,diameter),c,0,-10);a=curve(upper.get(0));require(a.contains(0,10)&&!a.contains(0,-10),"upper half remains after cyclic interval removal");
        List<SketchPrimitive> left=SketchTrimmer.trim(Arrays.asList(c,line("axis",0,-20,0,20)),c,10,0);a=curve(left.get(0));require(a.contains(-10,0)&&!a.contains(10,0),"removal wraps zero angle without a fabricated seam");
        SketchPrimitive other=circle("other",10,0,10);a=curve(SketchTrimmer.trim(Arrays.asList(c,other),c,10,0).get(0));close(a.sweep,4*Math.PI/3);close(a.r,10);require(a.contains(-10,0),"circle-circle intersections preserve major arc");
        SketchPrimitive arch=arc("arch",-10,0,0,10,10,0);
        List<SketchPrimitive> cutters=Arrays.asList(arch,line("a",-5,-20,-5,20),line("b",5,-20,5,20));
        List<SketchPrimitive> pieces=SketchTrimmer.trim(cutters,arch,0,10);require(pieces.size()==2&&!pieces.get(0).id.equals(pieces.get(1).id),"arc middle removed with unique piece identities");
        for(SketchPrimitive piece:pieces){a=curve(piece);close(a.r,10);close(Math.abs(a.sweep),Math.PI/3);}
        pieces=SketchTrimmer.trim(cutters,arch,-9,Math.sqrt(19));require(pieces.size()==1,"arc end interval removed");close(Math.abs(curve(pieces.get(0)).sweep),2*Math.PI/3);
        SketchPrimitive major=arc("major",10,0,-10,0,0,10);a=SketchCurves.arc(major.curves,0);require(a.sweep< -Math.PI,"CW reflex fixture");
        pieces=SketchTrimmer.trim(Arrays.asList(major,line("cut",-20,-5,20,-5)),major,0,-10);require(pieces.size()==2,"CW reflex arc trims in path parameter order");for(SketchPrimitive piece:pieces)close(curve(piece).r,10);
        SketchPrimitive wire=new SketchPrimitive("wire",concat(arch.curves,segment(line("chord",10,0,-10,0))),true);
        pieces=SketchTrimmer.trim(Arrays.asList(wire,line("cut",-5,1,-5,20),line("cut2",5,1,5,20)),wire,0,10);
        require(pieces.size()==3&&pieces.stream().filter(p->p.curves!=null).count()==2,"unused mixed wire opens into remaining line and true arcs");
        SketchPrimitive offCenter=arc("off",-10,0,-7,Math.sqrt(51),10,0);
        SketchPrimitive outline=new SketchPrimitive("outline",concat(offCenter.curves,segment(diameter)),true);
        pieces=SketchTrimmer.trim(Arrays.asList(outline,line("short1",-5,-1,-5,1),line("short2",5,-1,5,1)),outline,0,0);
        require(pieces.size()==3&&Arrays.equals(pieces.get(0).curves,offCenter.curves),"trimming line preserves untouched arc control data exactly");
        reject(()->SketchTrimmer.trim(Collections.singletonList(c),c,10,0),"circle without intersections is not deleted");
        reject(()->SketchTrimmer.trim(Arrays.asList(c,line("tangent",-20,10,20,10)),c,10,0),"single tangency cannot delete entire circle");
        reject(()->SketchTrimmer.trim(Arrays.asList(c,diameter),c,10,0),"ambiguous tap at circle intersection rejected");
        reject(()->SketchTrimmer.trim(Arrays.asList(c,diameter),c,0,0),"undefined circle-center tap rejected");
        reject(()->SketchTrimmer.trim(Arrays.asList(c,diameter),c,Double.NaN,1),"nonfinite pointer rejected");
        reject(()->SketchTrimmer.trim(Arrays.asList(c,circle("same",0,0,10)),c,0,10),"coincident curves rejected without guessing");
        SketchPrimitive locked=new SketchPrimitive("locked","Okrąg",0,0,10,0,new double[]{10});
        reject(()->SketchTrimmer.trim(Arrays.asList(locked,diameter),locked,0,10),"driving circle dimension protected");
        Random random=new Random(40024);
        for(int i=0;i<100;i++){
            double angle=random.nextDouble()*Math.PI*2,dx=10*Math.cos(angle),dy=10*Math.sin(angle);
            SketchPrimitive axis=line("axis",-dx,-dy,dx,dy);double x=10*Math.cos(angle+Math.PI/2),y=10*Math.sin(angle+Math.PI/2);
            SketchCurves.Arc result=SketchCurves.arc(SketchTrimmer.trim(Arrays.asList(c,axis),c,x,y).get(0).curves,0);
            close(result.r,10);close(result.sweep,Math.PI);if(result.contains(x,y)||!result.contains(-x,-y))throw new AssertionError("cyclic circle interval wrong");
        }
        require(true,"100 seeded diameter directions trim the selected semicircle");
        if(args.length>0) {
            CadProject doc=CadProject.blank();doc.sketch.add(lower.get(0));doc.sketch.add(diameter);CadProject restored=CadProject.fromJson(doc.toJson());
            require(restored.sketch.get(0).curves!=null,"trimmed arc persists in project JSON");
            SketchPrimitive profile=SketchContours.join(restored.sketch,null).profile;
            double[] base=profile.appendVertices(new double[]{profile.featureType(),profile.x1,profile.y1,profile.x2,profile.y2,0,8});
            double[] mesh=NativeCadEngine.evaluateFeatures(40,30,20,0,base,new double[0],args[0]);
            require(Math.abs(volume(mesh)-Math.PI*100*4)<8,"trimmed circle and chord extrude in OCCT");
            double[] properties=NativeCadEngine.edgeMeasurements(40,30,20,0,base,new double[0],null);int rims=0;
            for(int i=0;i<properties.length;i+=16)if(properties[i+14]==1){rims++;close(properties[i+10],10);close(properties[i+9],10*Math.PI);}
            require(rims==2,"trimmed analytic semicircle retained by native solid");
            require(NativeCadEngine.tessellateStep(args[0]).length>0,"trimmed curved solid STEP round trip");
        }
        System.out.println("ALL CURVE TRIM TESTS PASSED");
    }
}
