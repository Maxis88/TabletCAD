import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class AxisConstraintProbe {
    static void require(boolean good,String label){if(!good)throw new AssertionError(label);System.out.println("PASS "+label);}
    static void close(double a,double b,String label){require(Math.abs(a-b)<1e-6,label);}
    static SketchPrimitive get(List<SketchPrimitive> g,String id){for(SketchPrimitive p:g)if(p.id.equals(id))return p;throw new AssertionError(id);}
    static void reject(Runnable r,String label){try{r.run();throw new AssertionError(label);}catch(IllegalArgumentException expected){System.out.println("PASS "+label);}}
    static double volume(double[] mesh){double sum=0;require(mesh.length>0,"nonempty axis-constrained OCCT mesh");for(int i=0;i<mesh.length;i+=13)sum+=mesh[i]*(mesh[i+4]*mesh[i+8]-mesh[i+5]*mesh[i+7])+mesh[i+1]*(mesh[i+5]*mesh[i+6]-mesh[i+3]*mesh[i+8])+mesh[i+2]*(mesh[i+3]*mesh[i+7]-mesh[i+4]*mesh[i+6]);return Math.abs(sum/6);}
    public static void main(String[] args)throws Exception {
        SketchPrimitive line=new SketchPrimitive("guide","Pomocnicza",5,10,8,14);
        SketchConstraints.Link h=SketchConstraints.Link.axis(line.id,0,1,true);
        List<SketchPrimitive> horizontal=SketchConstraints.connect(Arrays.asList(line),Collections.emptyList(),h);
        SketchPrimitive aligned=horizontal.get(0);
        close(aligned.x1,5,"H keeps start X");close(aligned.y1,10,"H keeps start Y");close(aligned.x2,10,"H rotates free line preserving length");close(aligned.y2,10,"H aligns endpoint");
        SketchConstraints.Link fixed=SketchConstraints.Link.fixed(aligned,0);
        List<SketchConstraints.Link> rules=Arrays.asList(h,fixed);
        List<SketchPrimitive> anchored=SketchConstraints.connect(horizontal,Arrays.asList(h),fixed);
        List<SketchPrimitive> dragged=SketchConstraints.drag(anchored,rules,aligned,new SketchPrimitive(line.id,line.type,5,10,20,18),2);
        close(dragged.get(0).x2,20,"H endpoint can extend");close(dragged.get(0).y2,10,"fixed H projects pointer onto horizontal axis");
        reject(()->SketchConstraints.edit(anchored,rules,line.id,new SketchPrimitive(line.id,line.type,6,11,11,11)),"fixed start rejects numeric translation");
        reject(()->SketchConstraints.drag(anchored,rules,aligned,new SketchPrimitive(line.id,line.type,6,11,11,11),0),"fixed start rejects whole-line drag");
        List<SketchPrimitive> fixedDrag=SketchConstraints.drag(anchored,rules,aligned,new SketchPrimitive(line.id,line.type,7,15,10,10),1);
        close(fixedDrag.get(0).x1,5,"drag cannot move fixed point X");close(fixedDrag.get(0).y1,10,"drag cannot move fixed point Y");
        SketchPrimitive verticalStart=new SketchPrimitive("v","Linia",0,0,10,0);
        SketchConstraints.Link v=SketchConstraints.Link.axis("v",0,1,false);
        SketchPrimitive vertical=SketchConstraints.connect(Arrays.asList(verticalStart),Collections.emptyList(),v).get(0);
        close(vertical.x2,0,"V rotates horizontal line");close(Math.abs(vertical.y2),10,"V preserves free length at 90 degrees");
        SketchPrimitive locked=SketchDimensions.apply(line,5,30,true);
        reject(()->SketchConstraints.connect(Arrays.asList(locked),Collections.emptyList(),h),"H does not erase locked angle");
        reject(()->SketchConstraints.connect(horizontal,Arrays.asList(h),SketchConstraints.Link.axis(line.id,0,1,false)),"contradictory H and V cannot collapse line");
        SketchPrimitive polygon=new SketchPrimitive("poly",new double[]{0,0,10,0,10,10,0,10});
        List<SketchConstraints.Link> boxRules=Arrays.asList(SketchConstraints.Link.axis("poly",0,1,true),SketchConstraints.Link.axis("poly",1,2,false),SketchConstraints.Link.axis("poly",2,3,true),SketchConstraints.Link.axis("poly",3,0,false),SketchConstraints.Link.fixed(polygon,0));
        SketchConstraints.validate(Arrays.asList(polygon),boxRules);
        SketchPrimitive candidate=new SketchPrimitive("poly",new double[]{0,0,14,0,10,10,0,10});
        List<SketchPrimitive> resized=SketchConstraints.drag(Arrays.asList(polygon),boxRules,polygon,candidate,2);
        close(resized.get(0).vertices[2],14,"axis-constrained polygon corner moves");close(resized.get(0).vertices[4],14,"adjacent vertex follows same-entity V constraint");close(resized.get(0).vertices[1],0,"fixed polygon corner remains anchored");
        SketchPrimitive shape=resized.get(0);double[] base=shape.appendVertices(new double[]{shape.featureType(),shape.x1,shape.y1,shape.x2,shape.y2,0,5});
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,base,new double[0],null)),700,"axis-constrained contour builds exact OCCT volume");
        CadProject doc=new CadProject();doc.sketch.addAll(anchored);doc.coincidences.addAll(rules);doc.archiveCurrentSketch();
        CadProject restored=CadProject.fromJson(doc.toJson());require(restored.coincidences.size()==2&&restored.coincidences.get(1).kind.equals("fixed"),"schema19 persists H and fixed rules");
        close(restored.coincidences.get(1).x,5,"fixed coordinate survives JSON");restored.sketch.clear();restored.coincidences.clear();restored.restoreSketch(doc.captureSketch());
        require(restored.coincidences.size()==2,"archived H and fixed restore");
        JSONObject legacy=doc.toJson();legacy.put("schema",18);legacy.put("coincidences",new JSONArray());require(CadProject.fromJson(legacy).coincidences.isEmpty(),"schema18 projects still load");
        List<SketchPrimitive> triangle=Arrays.asList(new SketchPrimitive("a","Linia",0,0,10,0),new SketchPrimitive("b","Linia",10,0,0,10),new SketchPrimitive("c","Linia",0,10,0,0));
        List<SketchConstraints.Link> triangleRules=Arrays.asList(SketchConstraints.Link.axis("a",0,1,true),SketchConstraints.Link.fixed(triangle.get(0),0));
        SketchContours.Result joined=SketchContours.join(triangle,"a");List<SketchConstraints.Link> rebound=SketchConstraints.rebindContour(triangle,triangleRules,joined);
        require(rebound.size()==2&&rebound.get(1).kind.equals("fixed")&&rebound.get(0).ai==0&&rebound.get(0).bi==1,"contour join preserves axis and fixed rules");
        try{CadProject.fromJson(doc.toJson().put("coincidences",new JSONArray().put(new JSONObject().put("kind","fixed").put("a",line.id).put("ai",0).put("b",line.id).put("bi",0).put("x",99).put("y",10))));throw new AssertionError();}
        catch(JSONException expected){System.out.println("PASS inconsistent fixed coordinates rejected on load");}
        reject(()->SketchConstraints.connect(horizontal,Arrays.asList(h),h),"duplicate axis rule rejected");
        reject(()->SketchConstraints.connect(horizontal,Collections.emptyList(),new SketchConstraints.Link("unknown",line.id,0,line.id,1,0,0)),"unknown rule rejected");
        System.out.println("ALL AXIS/FIXED CONSTRAINT TESTS PASSED");
    }
}
