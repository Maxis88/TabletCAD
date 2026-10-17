import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class ArcProbe {
    static void require(boolean b,String s){if(!b)throw new AssertionError(s);System.out.println("PASS "+s);}
    static void close(double a,double b,double tolerance,String s){require(Math.abs(a-b)<tolerance,s+" "+a+" / "+b);}
    static double volume(double[] m){require(m.length>0,"nonempty analytic profile mesh");double v=0;for(int i=0;i<m.length;i+=13)v+=m[i]*(m[i+4]*m[i+8]-m[i+5]*m[i+7])+m[i+1]*(m[i+5]*m[i+6]-m[i+3]*m[i+8])+m[i+2]*(m[i+3]*m[i+7]-m[i+4]*m[i+6]);return Math.abs(v/6);}
    static double bound(double[] m,int axis,boolean max){double v=max?-1e10:1e10;for(int i=0;i<m.length;i+=13)for(int j=0;j<9;j+=3)v=max?Math.max(v,m[i+j+axis]):Math.min(v,m[i+j+axis]);return v;}
    static SketchPrimitive upper(){return new SketchPrimitive("arc",SketchCurves.arc(0,0,10,10,20,0),false);}
    static SketchPrimitive profile(){return SketchContours.join(Arrays.asList(upper(),new SketchPrimitive("line","Linia",20,0,0,0)),"arc").profile;}
    static double[] base(SketchPrimitive p,int plane,double depth){return p.appendVertices(new double[]{p.featureType(),p.x1,p.y1,p.x2,p.y2,plane,depth});}
    static void reject(Runnable r,String s){boolean bad=false;try{r.run();}catch(IllegalArgumentException e){bad=true;}require(bad,s);}
    public static void main(String[] args)throws Exception {
        SketchPrimitive arc=upper();SketchCurves.Arc a=SketchCurves.arc(arc.curves,0);
        close(a.r,10,1e-8,"three-point radius");close(a.x,10,1e-8,"three-point center");close(a.sweep,-Math.PI,1e-8,"arc passes through supplied middle point");
        require(!arc.closedProfile(),"open arc is not an extrudable profile");
        require(a.contains(10,10)&&!a.contains(10,-10),"hit geometry only includes selected half");
        SketchPrimitive p=profile();require(p.curves.length==14&&p.closedProfile(),"arc and chord form exact mixed contour");
        for(int plane=0;plane<3;plane++)for(boolean symmetric:new boolean[]{false,true}) {
            double[] b=base(p,plane+(symmetric?4:0),12),mesh=NativeCadEngine.evaluateFeatures(40,30,20,0,b,new double[0],null);
            close(volume(mesh),Math.PI*100*6,12,"half-cylinder volume on plane "+plane+" symmetric="+symmetric);
            int axis=plane==0?2:plane==1?1:0;close(bound(mesh,axis,false),symmetric?-6:0,.001,"base minimum axis");close(bound(mesh,axis,true),symmetric?6:12,.001,"base maximum axis");
            double[] properties=NativeCadEngine.edgeMeasurements(40,30,20,0,b,new double[0],null);int arcs=0;
            for(int i=0;i<properties.length;i+=16)if(properties[i+14]==1){arcs++;close(properties[i+10],10,1e-6,"kernel retains analytic radius");close(properties[i+9],10*Math.PI,1e-6,"kernel retains analytic arc length");}
            require(arcs==2,"two true circular rim arcs, not faceted polygon");
        }
        double[] addition=p.appendVertices(new double[]{p.featureType(),0,p.x1,p.y1,p.x2,p.y2,5,0,0,20,1,0,0,0,1,0});
        close(volume(NativeCadEngine.evaluateFeatures(40,30,20,0,null,addition,args[0])),24000+Math.PI*100*2.5,6,"curved profile Boolean addition");
        addition[1]=1;close(volume(NativeCadEngine.evaluateFeatures(40,30,20,0,null,addition,null)),24000-Math.PI*100*2.5,6,"curved profile Boolean cut");
        double[] step=NativeCadEngine.tessellateStep(args[0]);close(volume(step),24000+Math.PI*100*2.5,6,"STEP keeps curved addition");
        String profilePath=args[0]+".profile.step";
        require(NativeCadEngine.evaluateFeatures(40,30,20,0,base(p,0,12),new double[0],profilePath).length>0,"curved base exports STEP");
        double[] drawing=NativeCadEngine.technicalDrawing(profilePath,.5);int[] views=new int[4];
        require(drawing.length>0&&drawing.length%6==0,"curved solid technical drawing segments");
        boolean finiteDrawing=true;
        for(int i=0;i<drawing.length;i+=6){views[(int)drawing[i]]++;for(int j=2;j<6;j++)finiteDrawing&=Double.isFinite(drawing[i+j]);}
        require(finiteDrawing,"finite curved drawing coordinates");
        for(int i=0;i<4;i++)require(views[i]>0,"curved solid projection/section "+i);
        List<SketchPrimitive> exploded=SketchEditing.explode(p);require(exploded.size()==2&&exploded.get(0).curves!=null,"explode retains analytic arc");
        require(SketchContours.join(exploded,exploded.get(1).id).profile.closedProfile(),"join can reverse segment orientation");
        SketchPrimitive moved=SketchCurves.drag(p,0,4,-3);close(moved.curves[1],4,1e-8,"whole contour translation preserves arc data");
        List<SketchPrimitive> solved=SketchConstraints.edit(Arrays.asList(arc,new SketchPrimitive("other","Linia",30,0,40,0)),Collections.emptyList(),"other",new SketchPrimitive("other","Linia",30,0,45,0));
        require(solved.get(0).curves!=null,"line solver does not flatten unrelated arc");
        double[] snap=SketchSnapper.snap(10,10.1,Arrays.asList(arc),null,.3,null,null);close(snap[1],10,1e-8,"snapping onto arc midpoint");
        CadProject doc=CadProject.blank();doc.featureBase=base(p,0,12);doc.featureBaseProfileId=p.id;doc.sketch.add(p);doc.archiveCurrentSketch();
        CadProject loaded=CadProject.fromJson(doc.toJson());require(loaded.sketch.get(0).curves!=null,"schema24 persists curve contour");loaded.restoreSketch(loaded.savedSketches.get(0));require(loaded.sketch.get(0).curves.length==14,"archived source restores linked curved feature");
        require(SketchPrimitive.fromJson(arc.toJson()).curves!=null,"standalone arc JSON round trip");
        StringWriter dxf=new StringWriter();dxf.write("0\nSECTION\n2\nENTITIES\n");SketchCurves.writeDxf(dxf,p.curves);dxf.write("0\nENDSEC\n0\nEOF\n");
        List<SketchPrimitive> imported=CadExchange.readDxf(new ByteArrayInputStream(dxf.toString().getBytes(StandardCharsets.US_ASCII)));
        require(imported.size()==2&&SketchContours.join(imported,null).profile.closedProfile(),"DXF ARC plus LINE round trip forms contour");
        reject(()->SketchCurves.arc(0,0,5,0,10,0),"collinear points rejected");
        reject(()->SketchContours.join(Arrays.asList(arc),null),"open arc cannot auto-close");
        reject(()->SketchContours.join(Arrays.asList(arc,new SketchPrimitive("line","Linia",20,0,0,0),new SketchPrimitive("branch","Linia",0,0,-5,-5)),null),"branch at curved contour start rejected");
        reject(()->new SketchPrimitive("bad",new double[]{0,0,0,0,0,10,10,0,10,10,0,0,0,10,0,0,10,0,0,10,0,0,10,0,0,0,0,0},true),"self-intersecting wire rejected");
        double[] invalid=base(p,0,12);invalid[7]=9;require(NativeCadEngine.evaluateFeatures(40,30,20,0,invalid,new double[0],null).length==0,"native kernel rejects unknown curve segment type");
        System.out.println("ALL ANALYTIC ARC TESTS PASSED");
    }
}
