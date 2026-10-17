import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class DimensionProbe {
    static void require(boolean good,String label){if(!good)throw new AssertionError(label);System.out.println("PASS "+label);}
    static void close(double a,double b,String label){require(Math.abs(a-b)<1e-6,label);}
    static double volume(double[] mesh) {
        require(mesh.length>0,"nonempty B-Rep mesh");double volume=0;
        for(int i=0;i<mesh.length;i+=13)volume+=mesh[i]*(mesh[i+4]*mesh[i+8]-mesh[i+5]*mesh[i+7])
            +mesh[i+1]*(mesh[i+5]*mesh[i+6]-mesh[i+3]*mesh[i+8])+mesh[i+2]*(mesh[i+3]*mesh[i+7]-mesh[i+4]*mesh[i+6]);
        return Math.abs(volume/6);
    }
    public static void main(String[] args)throws Exception {
        SketchPrimitive line=SketchDimensions.apply(new SketchPrimitive("line","Linia",10,20,20,20),25,30,true);
        close(SketchDimensions.length(line),25,"line driving length");close(SketchDimensions.angle(line),30,"line driving angle");
        SketchPrimitive rect=SketchDimensions.apply(new SketchPrimitive("rect","Prostokąt",20,20,0,0),30,10,true);
        close(rect.x2,-10,"reversed rectangle width");close(rect.y2,10,"reversed rectangle height");
        SketchPrimitive circle=SketchDimensions.apply(new SketchPrimitive("circle","Okrąg",10,20,15,25),12.5,0,true);
        close(SketchDimensions.length(circle),12.5,"circle driving radius");
        SketchPrimitive moved=SketchDimensions.constrainDrag(rect,new SketchPrimitive(rect.id,rect.type,25,26,100,100),0);
        close(Math.abs(moved.x2-moved.x1),30,"translation preserves locked width");
        close(Math.abs(moved.y2-moved.y1),10,"translation preserves locked height");
        close(moved.x1,25,"translation moves anchor");
        require(SketchDimensions.constrainDrag(rect,new SketchPrimitive(rect.id,rect.type,20,20,100,100),2)==rect,"resize cannot violate lock");
        require(SketchDimensions.unlocked(rect).driving==null,"explicit unlock");
        SketchPrimitive roundTrip=SketchPrimitive.fromJson(circle.toJson());
        require(roundTrip.driving!=null&&roundTrip.driving.length==1,"circle dimension JSON persistence");
        CadProject doc=new CadProject();doc.sketch.add(rect);doc.featureBaseProfileId=rect.id;
        doc.featureBase=new double[]{0,rect.x1,rect.y1,rect.x2,rect.y2,0,10};
        doc.sketchExtruded=true;doc.featureProfileId=rect.id;
        org.json.JSONObject snapshot=doc.captureSketch();doc.archiveCurrentSketch();doc.sketch.clear();doc.sketchId="other";
        require(doc.isProfileDimensioned(rect.id),"archived profile lock discoverable from history");
        doc.restoreSketch(snapshot);require(doc.sketch.get(0).driving!=null,"restored sketch retains driving constraints");
        CadProject restored=CadProject.fromJson(doc.toJson());require(restored.sketch.get(0).driving!=null,"schema16 preserves dimensions");
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,restored.featureBase,new double[0],null)),3000,"dimensioned rectangle OCCT volume");
        SketchPrimitive grown=SketchDimensions.apply(rect,40,20,true);
        double[] grownBase={0,grown.x1,grown.y1,grown.x2,grown.y2,0,10};
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,grownBase,new double[0],null)),8000,"changed driving dimensions rebuild solid");
        try{SketchPrimitive.fromJson(rect.toJson().put("driving",new org.json.JSONArray().put(99).put(10)));throw new AssertionError();}
        catch(org.json.JSONException expected){System.out.println("PASS inconsistent persisted constraint rejected");}
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,10001}) {
            try{SketchDimensions.apply(rect,invalid,20,true);throw new AssertionError();}
            catch(IllegalArgumentException expected){System.out.println("PASS invalid dimension rejected "+invalid);}
        }
        try{SketchTrimmer.trim(Arrays.asList(line,new SketchPrimitive("cross","Linia",15,0,15,50)),line,16,20);throw new AssertionError();}
        catch(IllegalArgumentException expected){System.out.println("PASS trim does not silently erase constraints");}
        SketchPrimitive lockedEdge=SketchDimensions.apply(new SketchPrimitive("a","Linia",0,0,10,0),10,0,true);
        try{SketchContours.join(Arrays.asList(lockedEdge,new SketchPrimitive("b","Linia",10,0,0,10),
            new SketchPrimitive("c","Linia",0,10,0,0)),null);throw new AssertionError();}
        catch(IllegalArgumentException expected){System.out.println("PASS join does not silently erase constraints");}
        System.out.println("ALL DIMENSION TESTS PASSED");
    }
}
