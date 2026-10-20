import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** 1.0.0-rc4: a closed spline is a smooth profile: extrude, cut, save, reload and edit its source. */
public final class SplineKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    public static void main(String[] args)throws Exception {
        double[] pts={50,20, 62,30, 50,40, 38,30, 50,20};
        SketchPrimitive s=new SketchPrimitive("s",pts,"Splajn");
        check(SketchSpline.closed(pts)&&s.closedProfile(),"spline ending at its first point is a closed profile");
        check(s.featureType()==1004,"4 Bezier spans in the record");
        double[] smooth=SketchSpline.sample(pts,64);
        double a=Math.abs(SketchContours.area(java.util.Arrays.copyOf(smooth,smooth.length-2)));
        check(a>240&&a<380,"smooth closed curve area "+a);
        // Tangent continuity at the joint (periodic): first and last span tangents are parallel.
        double[] sp=SketchSpline.spans(pts);int last=sp.length-8;
        double t0x=sp[2]-sp[0],t0y=sp[3]-sp[1],t1x=sp[last+6]-sp[last+4],t1y=sp[last+7]-sp[last+5];
        check(Math.abs(t0x*t1y-t0y*t1x)<1e-9&&t0x*t1x+t0y*t1y>0,"no corner where the spline closes");
        CadProject p=HistoryKernelProbe.plate();double plate=HistoryKernelProbe.volume(p);
        HistoryKernelProbe.add(p,ExtrusionPlan.feature(HistoryKernelProbe.top(),s,"add",10,0,false,false),"s",1);
        double boss=HistoryKernelProbe.volume(p)-plate;
        check(Math.abs(boss-a*10)<a*10*2e-3,"extruded boss volume = area × 10 ("+boss+" vs "+a*10+")");
        CadProject cut=HistoryKernelProbe.plate();
        HistoryKernelProbe.add(cut,ExtrusionPlan.feature(HistoryKernelProbe.top(),s,"cut",5,0,false,false),"s",1);
        double pocket=plate-HistoryKernelProbe.volume(cut);
        check(Math.abs(pocket-a*5)<a*5*2e-3,"spline pocket volume = area × 5");
        CadProject loaded=CadProject.fromJson(new org.json.JSONObject(p.toJson().toString()));
        check(Math.abs(HistoryKernelProbe.volume(loaded)-plate-boss)<1e-6,"saved and reloaded: same volume");
        double[] f=loaded.committedFeatures.get(0);
        SketchPrimitive back=SketchPrimitive.fromFeature("s","Splajn",f,2,16);
        check(SketchSpline.is(back)&&back.vertices.length==pts.length&&Math.abs(back.vertices[2]-62)<1e-9,"source spline rebuilt from the operation (editable)");
        SketchPrimitive open=new SketchPrimitive("o",new double[]{0,0,10,5,20,0},"Splajn");
        check(!open.closedProfile(),"an open spline is not a profile");
        try{open.featureType();check(false,"open refused");}catch(IllegalArgumentException e){check(true,"open spline refused with a hint: "+e.getMessage());}
        System.out.println("PASS SplineKernelProbe: "+checks+" checks");
    }
}
