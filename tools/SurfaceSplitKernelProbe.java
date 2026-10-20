import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** 1.0.0-rc4: split a body by a wavy surface made from an open sketch spline (and by a line). */
public final class SurfaceSplitKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static double[] packed(CadProject p){BodyGraph.validate(p);return BodyGraph.pack(p,HistoryKernelProbe.raw(p));}
    static double[] inertia(CadProject p){double[] r=NativeCadEngine.bodyInertia(p.width,p.height,p.depth,0,p.featureBase,packed(p),null);if(r.length==0)throw new AssertionError("Evaluation failed: "+NativeCadEngine.lastFailure().getMessage());return r;}
    static double[] body(double[] r,int id){for(int i=0;i+15<r.length;i+=16)if(r[i]==id)return Arrays.copyOfRange(r,i,i+16);throw new AssertionError("no body "+id);}
    static CadProject split(SketchPrimitive curve,boolean flip)throws Exception {
        CadProject p=HistoryKernelProbe.plate();p.sketch.add(curve);p.sketchPlane="XY";
        SweepLoft.Placed e=null;for(SweepLoft.Placed x:SweepLoft.elements(p))if(x.shape.id.equals(curve.id))e=x;
        List<SweepLoft.Placed> list=Collections.singletonList(e);
        double[] f=SurfaceSplit.create(BodyGraph.allocate(p),list,400,flip);
        p.committedFeatures.add(f);p.committedProfileIds.add(SurfaceSplit.ref(list));p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);
        return p;
    }
    public static void main(String[] args)throws Exception {
        double plate=100*60*20;
        // Wave across the plate from x=-10 to x=110 around y=30.
        double[] pts={-10,30, 15,38, 40,22, 65,38, 90,22, 110,30};
        CadProject p=split(new SketchPrimitive("wave",pts,"Splajn"),false);
        double[] r=inertia(p),a=body(r,1),b=body(r,SurfaceSplit.created(p.committedFeatures.get(0)));
        check(Math.abs(a[1]+b[1]-plate)<1e-3,"two parts keep the plate volume ("+a[1]+" + "+b[1]+")");
        check(a[1]>plate*.3&&b[1]>plate*.3,"the wave cuts the plate roughly in half");
        check(a[3]>b[3],"left of the curve direction (+Y side) keeps the original body");
        CadProject q=split(new SketchPrimitive("wave",pts,"Splajn"),true);
        double[] rq=inertia(q);check(body(rq,1)[3]<body(rq,SurfaceSplit.created(q.committedFeatures.get(0)))[3],"flip swaps the sides");
        // The split surface is wavy: the cut face area is larger than a flat 60×20 section... measured via total area.
        double flatArea=2*(100*60+100*20+60*20)+2*100*20;// both halves with a flat cut along X
        double[] props=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,packed(p),null);
        check(props[5]==2&&props[1]>flatArea,"curved cut faces: total area "+props[1]+" > flat cut "+flatArea);
        // Persistence and editing: reload, then move a spline point → the split follows the curve.
        CadProject loaded=CadProject.fromJson(new org.json.JSONObject(p.toJson().toString()));
        check(Math.abs(body(inertia(loaded),1)[1]-a[1])<1e-6,"saved and reloaded: same parts");
        double[] moved=pts.clone();for(int i=1;i<moved.length;i+=2)moved[i]+=10;
        loaded.sketch.clear();loaded.sketch.add(new SketchPrimitive("wave",moved,"Splajn"));
        check(body(inertia(loaded),1)[1]<a[1]-plate*.1,"curve moved 10 mm up: the kept part shrinks");
        // A straight line works too; a curve that does not cross the body is refused.
        CadProject line=split(new SketchPrimitive("l","Linia",-5,-5,105,65),false);
        double[] rl=inertia(line);check(Math.abs(body(rl,1)[1]-plate/2)<1e-3,"diagonal line splits the plate in half");
        CadProject shortCurve=split(new SketchPrimitive("s","Linia",20,30,80,30),false);
        double[] bad=NativeCadEngine.bodyInertia(shortCurve.width,shortCurve.height,shortCurve.depth,0,shortCurve.featureBase,packed(shortCurve),null);
        check(bad.length==0&&NativeCadEngine.lastFailure().getMessage().length()>0,"a curve inside the body is refused: "+NativeCadEngine.lastFailure().getMessage());
        // Surface through two curves in different sketches (below and above the plate): a twisted split.
        CadProject lf=HistoryKernelProbe.plate();lf.sketchOriginZ=-10;lf.sketch.add(new SketchPrimitive("low",new double[]{-10,20, 50,35, 110,20},"Splajn"));lf.archiveCurrentSketch();
        lf.sketchId=java.util.UUID.randomUUID().toString();lf.sketch.clear();lf.sketchOriginZ=30;lf.sketch.add(new SketchPrimitive("high","Linia",-10,40,110,40));
        List<SweepLoft.Placed> two=new ArrayList<>();for(SweepLoft.Placed x:SweepLoft.elements(lf))two.add(x);
        check(two.size()==2,"two curves in two sketches");
        double[] lrec=SurfaceSplit.createLoft(BodyGraph.allocate(lf),two,false);
        lf.committedFeatures.add(lrec);lf.committedProfileIds.add(SurfaceSplit.loftRef(two));lf.featureBodyTargets.add(1);lf.featureBodyOutputs.add(1);
        double[] lr=inertia(lf),la=body(lr,1),lb=body(lr,(int)lrec[1]);
        check(Math.abs(la[1]+lb[1]-plate)<1e-3&&la[1]>plate*.2&&lb[1]>plate*.2,"surface through two curves splits the plate ("+la[1]+" + "+lb[1]+")");
        CadProject lfl=CadProject.fromJson(new org.json.JSONObject(lf.toJson().toString()));
        check(Math.abs(body(inertia(lfl),1)[1]-la[1])<1e-6,"loft split saved and reloaded");
        try{SurfaceSplit.create(9,Collections.emptyList(),100,false);check(false,"empty");}catch(IllegalArgumentException e){check(true,"a curve is required");}
        System.out.println("PASS SurfaceSplitKernelProbe: "+checks+" checks");
    }
}
