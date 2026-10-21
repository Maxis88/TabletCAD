import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** 0.74 domain checks without OCCT: ellipse, user parameters, loft/sweep records. */
public final class CadToolsHostTest {
    interface Work{void run()throws Exception;}
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static void near(double a,double b,String label){check(Math.abs(a-b)<1e-9,label+" ("+a+")");}
    static void rejects(Work w,String label)throws Exception{try{w.run();}catch(IllegalArgumentException|JSONException e){check(true,label+" → "+e.getMessage());return;}throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        // Ellipse primitive.
        SketchPrimitive e=SketchPrimitive.ellipse("e",10,20,30,20,5);
        check(e.isEllipse()&&e.closedProfile()&&e.featureType()==2000,"ellipse is a closed profile with type 2000");near(e.major(),20,"semi-major");
        double[] tail=e.appendVertices(new double[]{1});check(tail.length==2&&tail[1]==5,"profile tail carries the semi-minor axis");
        SketchPrimitive back=SketchPrimitive.fromJson(e.toJson());check(back.isEllipse()&&back.minor==5&&back.x2==30,"ellipse JSON round trip");
        rejects(()->SketchPrimitive.ellipse("x",0,0,10,0,11),"minor longer than major refused");
        rejects(()->new SketchPrimitive("x","Elipsa",0,0,1,1),"ellipse needs its minor axis");
        SketchPrimitive moved=SketchTransform.move(e,5,-5);check(moved.isEllipse()&&moved.minor==5&&moved.x1==15&&moved.y1==15,"ellipse moves exactly");
        SketchPrimitive rotated=SketchTransform.rotate(e,90,10,20);near(rotated.x2,10,"ellipse rotates its major axis X");near(rotated.y2,40,"ellipse rotates its major axis Y");
        SketchPrimitive scaled=SketchTransform.scale(e,2,10,20);near(scaled.minor,10,"ellipse scales both axes");
        SketchPrimitive mirrored=SketchMirror.copy(e,"m",0,0,0,1);check(mirrored.isEllipse()&&mirrored.x1==-10&&mirrored.minor==5,"ellipse mirror");
        check(SketchShapes.threePoint("t","Elipsa",new double[]{0,0},new double[]{10,0},new double[]{3,4}).minor==4,"three-point ellipse uses the distance from the major axis");
        rejects(()->SketchShapes.threePoint("t","Elipsa",new double[]{0,0},new double[]{3,0},new double[]{1,5}),"three-point ellipse with minor > major refused");
        check(SketchSpline.is(SketchOffset.offset(e,"o",1)),"offset of an ellipse is a closed spline through exact offset points (SketchOffsetCurvesHostTest)");
        check(Revolve.is(Revolve.create(e,new CadProject(),new double[]{-100,0,-100,1},90,false)),"an ellipse beside the axis can be revolved (EllipseArcKernelProbe)");
        double[] feature=ExtrusionPlan.feature(new CadProject(),e,"add",10,0,false,false);check(feature.length==17&&feature[0]==2000&&feature[16]==5,"ellipse extrusion record");
        check(SketchPrimitive.fromFeature("e","Prostokąt",feature,2,16).isEllipse(),"profile is restored from the record as an ellipse");
        check(SketchConstraints.resolve(Arrays.asList(e,new SketchPrimitive("l","Linia",0,0,10,0)),new ArrayList<>()).get(0).isEllipse(),"solver keeps ellipses intact");
        check(ToolbarCatalog.allows(true,"Elipsa"),"sketch toolbar exposes the ellipse");

        // User parameters: evaluation, units, cycles, names.
        CadProject p=new CadProject();p.units="cm";
        p.parameters.add(new UserParameters.Parameter("szer","10",true));
        p.parameters.add(new UserParameters.Parameter("pol","szer/2+1mm",true));
        p.parameters.add(new UserParameters.Parameter("n","3",false));
        Map<String,double[]> v=UserParameters.evaluate(p);near(v.get("szer")[0],100,"length parameter in project unit (10 cm)");near(v.get("pol")[0],51,"expression with another parameter and units");near(v.get("n")[0],3,"number parameter");
        near(Units.parseLength("2*szer",p.units,v),200,"field expression uses parameters");
        rejects(()->Units.parseLength("x+1","mm",v),"unknown parameter reported");
        CadProject cyc=new CadProject();cyc.parameters.add(new UserParameters.Parameter("a","b+1",true));cyc.parameters.add(new UserParameters.Parameter("b","a+1",true));
        rejects(()->UserParameters.evaluate(cyc),"dependency cycle reported");
        rejects(()->UserParameters.validateName("mm"),"unit names cannot be parameters");rejects(()->UserParameters.validateName("2a"),"name must start with a letter");
        UserParameters.validateName("grubość_1");check(true,"Polish letters allowed in names");
        check(UserParameters.names("10mm+szer*2").equals(new LinkedHashSet<>(Collections.singletonList("szer"))),"units after numbers are not names");
        CadProject saved=CadProject.fromJson(new JSONObject(p.toJson().toString()));check(saved.parameters.size()==3&&saved.parameters.get(1).expression.equals("szer/2+1mm"),"parameters persist");
        JSONObject broken=new JSONObject(cyc.toJson().toString());rejects(()->CadProject.fromJson(broken),"document with a parameter cycle refused");

        // Bindings: depth and sketch dimension (re-solved and propagated to the operation).
        CadProject b=new CadProject();b.emptyDocument=false;b.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(b);
        SketchPrimitive rect=new SketchPrimitive("r","Prostokąt",10,10,30,20,new double[]{20,10});b.sketch.add(rect);
        b.committedFeatures.add(ExtrusionPlan.feature(b,rect,"add",5,0,false,false));b.committedProfileIds.add("r");b.featureBodyTargets.add(1);b.featureBodyOutputs.add(1);
        b.parameters.add(new UserParameters.Parameter("w","35",true));b.parameters.add(new UserParameters.Parameter("h","w/5",true));
        UserParameters.bind(b,UserParameters.dimKey(b.sketchId,"r",0),"w");UserParameters.bind(b,UserParameters.depthKey(0),"h");
        UserParameters.apply(b);
        near(b.sketch.get(0).x2-b.sketch.get(0).x1,35,"bound width re-drives the sketch");near(b.committedFeatures.get(0)[4],45,"operation profile follows the sketch dimension");
        near(b.committedFeatures.get(0)[6],7,"bound depth follows the expression");
        b.parameters.get(0).expression="50";UserParameters.apply(b);near(b.committedFeatures.get(0)[4],60,"changing the parameter updates profile");near(b.committedFeatures.get(0)[6],10,"and depth");
        CadProject rt=CadProject.fromJson(new JSONObject(b.toJson().toString()));check(rt.parameterBindings.size()==2,"bindings persist");
        UserParameters.removed(rt,0);check(rt.parameterBindings.size()==1,"binding of a removed operation is dropped");
        CadProject shift=CadProject.fromJson(new JSONObject(b.toJson().toString()));shift.parameterBindings.put("depth:3","h");UserParameters.removed(shift,1);check(shift.parameterBindings.containsKey("depth:2"),"later operation bindings are renumbered");
        CadProject badDepth=CadProject.fromJson(new JSONObject(b.toJson().toString()));badDepth.parameters.get(1).expression="0";rejects(()->UserParameters.apply(badDepth),"out-of-range parameter value refused with the bound item");

        // Loft/sweep record validation and toolbar.
        double[] loft=new double[16+14+14];loft[0]=SweepLoft.LOFT;loft[2]=2;loft[16+3]=1;loft[16+7]=1;loft[16+9]=0;loft[16+12]=1;loft[16+13]=1;loft[30+3]=1;loft[30+7]=1;loft[30+12]=1;loft[30+13]=1;loft[30+2]=10;
        SweepLoft.validate(loft);check(true,"loft record structure");
        double[] bad=loft.clone();bad[2]=3;rejects(()->SweepLoft.validate(bad),"loft section count must match");
        check(SweepLoft.creates(new double[]{SweepLoft.SWEEP,4,1,0,0,0,0,0,0,0,0,0,0,0,0,0})&&BodyGraph.creates(new double[]{SweepLoft.LOFT,4,2,0,0,0,0,0,0,0,0,0,0,0,0,0}),"loft/sweep may create a new body");
        double[] bp=SweepLoft.bodyPatternRecord(2,new double[3],new double[]{0,0,2},3,20);check(bp[8]==1&&SweepLoft.label(bp).equals("Szyk liniowy bryły"),"body pattern record");
        for(String c:new String[]{"Przejście","Przeciągnij","Lustro bryły","Szyk liniowy bryły","Szyk kołowy bryły"})check(ToolbarCatalog.allows(false,c),"solid toolbar exposes "+c);
        // Constraint status (degrees of freedom).
        SketchPrimitive free=new SketchPrimitive("l","Linia",0,0,10,0);
        NonlinearConstraints.Status st=NonlinearConstraints.status(Collections.singletonList(free),new ArrayList<>());check(st.freedoms==4&&st.label().contains("niedowiązany"),"free line has 4 freedoms: "+st.label());
        List<SketchConstraints.Link> fix=new ArrayList<>(Arrays.asList(SketchConstraints.Link.fixed(free,0),SketchConstraints.Link.fixed(free,1)));
        st=NonlinearConstraints.status(Collections.singletonList(free),fix);check(st.full()&&st.label().equals("Szkic w pełni związany"),"line with both ends fixed is fully constrained");
        SketchPrimitive dimmed=new SketchPrimitive("l","Linia",0,0,10,0,new double[]{10,0});
        st=NonlinearConstraints.status(Collections.singletonList(dimmed),fix);check(st.redundant==2&&st.label().contains("nadmiernie"),"length+angle on a fixed line is redundant: "+st.label());
        st=NonlinearConstraints.status(Collections.singletonList(dimmed),Collections.singletonList(SketchConstraints.Link.fixed(dimmed,0)));check(st.full(),"fixed start + length + angle = fully constrained");
        SketchPrimitive c=new SketchPrimitive("c","Okrąg",5,5,8,5);
        st=NonlinearConstraints.status(Collections.singletonList(c),Collections.singletonList(SketchConstraints.Link.fixed(c,0)));check(st.freedoms==1,"circle with fixed centre keeps only its radius free");
        SketchPrimitive cd=new SketchPrimitive("c","Okrąg",5,5,8,5,new double[]{3});
        st=NonlinearConstraints.status(Collections.singletonList(cd),Collections.singletonList(SketchConstraints.Link.fixed(cd,0)));check(st.full(),"fixed centre + radius is fully constrained");
        SketchPrimitive rr=new SketchPrimitive("r","Prostokąt",0,0,30,20,new double[]{30,20});
        List<SketchConstraints.Link> pos=Arrays.asList(new SketchConstraints.Link("dimX","r",0,"r",0,0,0),new SketchConstraints.Link("dimY","r",0,"r",0,0,0));
        st=NonlinearConstraints.status(Collections.singletonList(rr),pos);check(st.full(),"rectangle 30×20 positioned from the origin is fully constrained: "+st.label());
        st=NonlinearConstraints.status(Collections.singletonList(dimmed),Arrays.asList(SketchConstraints.Link.fixed(dimmed,0),new SketchConstraints.Link("fixed","l",1,"l",1,12,0)));check(st.contradictory,"violated constraints are reported as contradictory");
        SketchPrimitive arc=new SketchPrimitive("a",SketchCurves.arc(0,0,5,5,10,0),false);
        st=NonlinearConstraints.status(Collections.singletonList(arc),new ArrayList<>());check(st.freedoms==5&&st.label().contains("łukami"),"a free 3-point arc has 5 freedoms: "+st.label());
        st=NonlinearConstraints.status(Arrays.asList(arc,dimmed),Collections.singletonList(SketchConstraints.Link.fixed(dimmed,0)));check(st.freedoms==5&&!st.full(),"arc freedoms add to a fully constrained line");
        SketchPrimitive l1=new SketchPrimitive("a","Linia",0,0,10,0),l2=new SketchPrimitive("b","Linia",20,3,30,5);
        SketchConstraints.Link col=new SketchConstraints.Link("collinear","a",0,"b",0,1,0);
        List<SketchPrimitive> solved=SketchConstraints.connect(Arrays.asList(l1,l2),new ArrayList<>(),col);
        near(solved.get(1).y1,0,"collinear moves the second line onto the first (start)");near(solved.get(1).y2,0,"collinear (end)");check(solved.get(0).x2==10,"first line stays");
        st=NonlinearConstraints.status(solved,Collections.singletonList(col));check(st.freedoms==6,"collinear removes two freedoms: "+st.label());
        JSONObject doc36=new JSONObject(p.toJson().toString());check(doc36.getInt("schema")==37,"0.74 documents use schema 37");doc36.put("schema",36);doc36.remove("parameters");doc36.remove("parameterBindings");
        check(CadProject.fromJson(doc36).parameters.isEmpty(),"schema 36 documents load without parameters");
        System.out.println("PASS CadToolsHostTest: "+checks+" checks");
    }
}
