import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import java.util.*;

public final class ExtrusionPlanProbe {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    static double bound(double[] mesh,int axis,boolean upper){double value=upper?-Double.MAX_VALUE:Double.MAX_VALUE;for(int i=0;i<mesh.length;i+=13)for(int j=0;j<9;j+=3)value=upper?Math.max(value,mesh[i+j+axis]):Math.min(value,mesh[i+j+axis]);return value;}
    public static void main(String[] args)throws Exception {
        for(String plane:new String[]{"XY","XZ","YZ"})for(boolean reverse:new boolean[]{false,true}) {
            CadProject p=CadProject.blank();p.sketchPlane=plane;p.setPrincipalSketchFrame(plane);SketchPrimitive source=new SketchPrimitive("source","Prostokąt",0,0,10,5);p.sketch.add(source);
            p.featureBase=ExtrusionPlan.base(p,source,4,2,reverse,false);p.featureBaseProfileId=source.id;p.depth=6;p.extrusionExtents.put(-1,new double[]{reverse?-4:4,2,0});
            CadProject restored=CadProject.fromJson(p.toJson());double[] mesh=NativeCadEngine.evaluateFeatures(80,48,6,0,restored.featureBase,new double[0],null);
            check(mesh.length>0,"two-direction root B-Rep "+plane+" reverse="+reverse);int axis=plane.equals("XY")?2:plane.equals("XZ")?1:0;
            check(Math.abs(bound(mesh,axis,false)-(reverse?-4:-2))<1e-6&&Math.abs(bound(mesh,axis,true)-(reverse?2:4))<1e-6,"independent root extents around original plane "+plane+" reverse="+reverse);
            double[] properties=NativeCadEngine.bodyProperties(80,48,6,0,p.featureBase,new double[0],null);check(Math.abs(properties[0]-300)<1e-6,"exact two-direction root volume");
            SketchTransactions.begin(p);p.sketch.set(0,new SketchPrimitive("source","Prostokąt",0,0,12,5));CadProject candidate=SketchTransactions.candidate(p);
            properties=NativeCadEngine.bodyProperties(80,48,6,0,candidate.featureBase,new double[0],null);check(properties.length==7&&Math.abs(properties[0]-360)<1e-6,"source sketch edit rebuilds placed base preserving both extents");
        }
        CadProject frame=CadProject.blank();frame.setPrincipalSketchFrame("XY");SketchPrimitive source=new SketchPrimitive("source","Prostokąt",0,0,10,5);
        double[] feature=ExtrusionPlan.feature(frame,source,"new",4,2,false,false);check(feature[9]==-2&&feature[6]==6,"packed tool starts at opposite extent");
        double[] resized=ExtrusionPlan.resizeFeature(feature,2,5,1,false,false);check(resized[9]==-1&&resized[6]==6,"history resize retains source plane rather than accumulating offsets");
        List<SketchPrimitive> corner=SketchFillet.pair(new SketchPrimitive("a","Linia",-10,0,0,0),new SketchPrimitive("b","Linia",0,0,0,10),2);
        check(corner.size()==3&&Math.abs(corner.get(0).x2+2)<1e-8&&Math.abs(corner.get(1).y1-2)<1e-8,"two-line fillet shortens actual adjacent edges");check(Math.abs(SketchCurves.arc(corner.get(2).curves,0).r-2)<1e-8,"two-line fillet produces exact tangent arc");
        System.out.println("ALL EXTRUSION PLAN TESTS PASSED");
    }
}
