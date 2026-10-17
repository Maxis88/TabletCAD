import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class ConstraintProbe {
    static void require(boolean v,String label){if(!v)throw new AssertionError(label);System.out.println("PASS "+label);}
    static SketchPrimitive get(List<SketchPrimitive> geometry,String id){for(SketchPrimitive p:geometry)if(p.id.equals(id))return p;throw new AssertionError(id);}
    static void close(double a,double b,String label){require(Math.abs(a-b)<1e-6,label);}
    static void rejected(Runnable action,String label){try{action.run();throw new AssertionError(label);}catch(IllegalArgumentException expected){System.out.println("PASS "+label);}}
    static double volume(double[] mesh){double v=0;require(mesh.length>0,"nonempty constrained OCCT solid");for(int i=0;i<mesh.length;i+=13)v+=mesh[i]*(mesh[i+4]*mesh[i+8]-mesh[i+5]*mesh[i+7])+mesh[i+1]*(mesh[i+5]*mesh[i+6]-mesh[i+3]*mesh[i+8])+mesh[i+2]*(mesh[i+3]*mesh[i+7]-mesh[i+4]*mesh[i+6]);return Math.abs(v/6);}
    public static void main(String[] args)throws Exception {
        SketchPrimitive a=SketchDimensions.apply(new SketchPrimitive("a","Linia",0,0,10,0),10,0,true);
        SketchPrimitive b=SketchDimensions.apply(new SketchPrimitive("b","Linia",20,10,30,10),10,0,true);
        List<SketchPrimitive> initial=Arrays.asList(a,b);List<SketchConstraints.Link> links=new ArrayList<>();
        SketchConstraints.Link first=new SketchConstraints.Link("a",1,"b",0);
        List<SketchPrimitive> joined=SketchConstraints.connect(initial,links,first);links.add(first);
        close(get(joined,"a").x2,10,"first anchor stays fixed");close(get(joined,"b").x1,10,"second line joins anchor");
        close(get(joined,"b").x2,20,"locked neighbor translates without resizing");close(get(joined,"b").y1,0,"neighbor moves in both axes");
        List<SketchPrimitive> moved=SketchConstraints.edit(joined,links,"a",new SketchPrimitive("a","Linia",5,7,15,7,a.driving));
        close(get(moved,"b").x1,15,"editing propagates linked endpoint");close(get(moved,"b").y2,7,"editing preserves neighbor direction");
        require(initial.get(1)==b&&b.x1==20,"solver leaves input immutable");
        List<SketchPrimitive> rectangle=Arrays.asList(new SketchPrimitive("rect","Prostokąt",0,0,10,10),new SketchPrimitive("line","Linia",30,0,40,0));
        SketchConstraints.Link corner=new SketchConstraints.Link("rect",1,"line",0);
        List<SketchPrimitive> cornerJoined=SketchConstraints.connect(rectangle,Collections.emptyList(),corner);
        List<SketchPrimitive> resized=SketchConstraints.edit(cornerJoined,Arrays.asList(corner),"line",new SketchPrimitive("line","Linia",15,5,40,0));
        SketchPrimitive rect=get(resized,"rect");close(rect.x2,15,"rectangle corner responds to linked point");close(rect.y1,5,"rectangle remains axis aligned");
        double[] base={0,rect.x1,rect.y1,rect.x2,rect.y2,0,2};
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,base,new double[0],null)),150,"constrained source rebuilds exact OCCT solid");
        SketchPrimitive circle=new SketchPrimitive("circle","Okrąg",50,30,55,30);
        SketchConstraints.Link center=new SketchConstraints.Link("rect",0,"circle",0);
        List<SketchPrimitive> withCircle=SketchConstraints.connect(Arrays.asList(rect,circle),Collections.emptyList(),center);
        close(get(withCircle,"circle").x1,rect.x1,"circle center anchor joins");close(SketchDimensions.length(get(withCircle,"circle")),5,"circle retains radius while center follows");
        SketchPrimitive polygon=new SketchPrimitive("poly",new double[]{30,0,40,0,40,10});
        SketchConstraints.Link vertex=new SketchConstraints.Link("rect",2,"poly",0);
        List<SketchPrimitive> polygonJoined=SketchConstraints.connect(Arrays.asList(rect,polygon),Collections.emptyList(),vertex);
        close(get(polygonJoined,"poly").vertices[0],rect.x2,"polygon vertex coincidence");
        SketchPrimitive c=SketchDimensions.apply(new SketchPrimitive("c","Linia",0,5,10,5),10,0,true);
        List<SketchPrimitive> parallel=SketchConstraints.connect(Arrays.asList(a,c),Collections.emptyList(),new SketchConstraints.Link("a",0,"c",0));
        List<SketchConstraints.Link> pair=new ArrayList<>();pair.add(new SketchConstraints.Link("a",0,"c",0));
        parallel=SketchConstraints.connect(parallel,pair,new SketchConstraints.Link("a",1,"c",1));pair.add(new SketchConstraints.Link("a",1,"c",1));
        final List<SketchPrimitive> lockedPair=parallel;
        rejected(()->SketchConstraints.edit(lockedPair,pair,"a",SketchDimensions.apply(a,20,0,true)),"conflicting locked cycle rejected atomically");
        rejected(()->SketchConstraints.connect(joined,links,first),"duplicate coincidence rejected");
        rejected(()->SketchConstraints.connect(initial,Collections.emptyList(),new SketchConstraints.Link("a",0,"a",1)),"self connection rejected");
        rejected(()->SketchConstraints.connect(initial,Collections.emptyList(),new SketchConstraints.Link("a",9,"b",0)),"invalid anchor rejected");
        CadProject doc=new CadProject();doc.sketch.addAll(moved);doc.coincidences.addAll(links);
        doc.archiveCurrentSketch();JSONObject archive=doc.captureSketch();
        CadProject restored=CadProject.fromJson(doc.toJson());
        require(restored.coincidences.size()==1,"schema18 preserves coincidences");require(restored.isProfileDimensioned("b"),"history guards constrained geometry");
        restored.sketch.clear();restored.coincidences.clear();restored.restoreSketch(archive);
        require(restored.coincidences.size()==1,"archive restoration retains point constraints");
        restored.sketch.remove(1);restored.pruneCoincidences();require(restored.coincidences.isEmpty(),"deletion prunes dangling constraints");
        try{CadProject.fromJson(doc.toJson().put("coincidences",new JSONArray().put(new JSONObject().put("a","a").put("ai",0).put("b","b").put("bi",0))));throw new AssertionError();}
        catch(JSONException expected){System.out.println("PASS inconsistent saved coincidence rejected");}
        List<SketchPrimitive> chain=new ArrayList<>();List<SketchConstraints.Link> chainLinks=new ArrayList<>();
        for(int i=0;i<256;i++){chain.add(new SketchPrimitive("n"+i,"Linia",i,0,i+1,0,new double[]{1,0}));if(i>0)chainLinks.add(new SketchConstraints.Link("n"+(i-1),1,"n"+i,0));}
        List<SketchPrimitive> shifted=SketchConstraints.edit(chain,chainLinks,"n0",new SketchPrimitive("n0","Linia",100,30,101,30,new double[]{1,0}));
        close(get(shifted,"n255").x2,356,"long constraint chain propagation");close(get(shifted,"n255").y2,30,"long chain direction preserved");
        List<SketchPrimitive> triangle=Arrays.asList(new SketchPrimitive("t1","Linia",0,0,10,0),new SketchPrimitive("t2","Linia",10,0,0,10),new SketchPrimitive("t3","Linia",0,10,0,0),new SketchPrimitive("external","Pomocnicza",10,0,20,0));
        List<SketchConstraints.Link> triangleLinks=Arrays.asList(new SketchConstraints.Link("t1",1,"t2",0),new SketchConstraints.Link("t2",1,"t3",0),new SketchConstraints.Link("t3",1,"t1",0),new SketchConstraints.Link("t2",0,"external",0));
        SketchContours.Result contour=SketchContours.join(triangle,"t1");
        List<SketchConstraints.Link> rebound=SketchConstraints.rebindContour(triangle,triangleLinks,contour);
        require(rebound.size()==1&&rebound.get(0).a.equals("t1")&&rebound.get(0).ai==1,"joining contour turns internal links into topology and retains external anchors");
        List<SketchPrimitive> contourMove=SketchConstraints.edit(Arrays.asList(contour.profile,triangle.get(3)),rebound,"external",new SketchPrimitive("external","Pomocnicza",12,0,20,0));
        close(get(contourMove,"t1").vertices[2],12,"external guide remains linked after contour join");
        System.out.println("ALL CONSTRAINT TESTS PASSED");
    }
}
