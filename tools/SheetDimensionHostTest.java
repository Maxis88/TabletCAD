import org.json.JSONObject;
import pl.tabletcad.app.TechnicalSheet;
import pl.tabletcad.app.model.CadProject;

/** 0.90 sheet dimensions: kinds, true values, document persistence and validation. */
public final class SheetDimensionHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    public static void main(String[] args)throws Exception {
        check(TechnicalSheet.autoKind(0,0,100,0)==TechnicalSheet.HORIZONTAL,"horizontal pair → horizontal dimension");
        check(TechnicalSheet.autoKind(10,0,10,60)==TechnicalSheet.VERTICAL,"vertical pair → vertical dimension");
        check(TechnicalSheet.autoKind(0,0,30,40)==TechnicalSheet.ALIGNED,"oblique pair → aligned dimension");
        double[] d=TechnicalSheet.dimension(0,0,0,30,40,TechnicalSheet.ALIGNED,8);
        check(Math.abs(TechnicalSheet.value(d)-50)<1e-12,"aligned value is the true distance");
        d[5]=TechnicalSheet.HORIZONTAL;check(Math.abs(TechnicalSheet.value(d)-30)<1e-12,"horizontal value");
        d[5]=TechnicalSheet.VERTICAL;check(Math.abs(TechnicalSheet.value(d)-40)<1e-12,"vertical value");
        float[] p=TechnicalSheet.paper(0,0,594,420,297,210);
        check(Math.abs(p[0]-148.5)<1e-4&&Math.abs(p[1]-105)<1e-4,"screen → paper mapping at double scale");
        CadProject project=new CadProject();project.drawingDimensions.add(TechnicalSheet.dimension(2,1.5,2,101.5,2,TechnicalSheet.HORIZONTAL,-6));
        CadProject loaded=CadProject.fromJson(new JSONObject(project.toJson().toString()));
        check(loaded.drawingDimensions.size()==1&&loaded.drawingDimensions.get(0)[0]==2&&loaded.drawingDimensions.get(0)[6]==-6,"sheet dimensions are saved in the document");
        check(CadProject.fromJson(new JSONObject(new CadProject().toJson().toString())).drawingDimensions.isEmpty(),"documents without sheet dimensions load");
        JSONObject broken=project.toJson();broken.getJSONArray("drawingDimensions").getJSONArray(0).put(0,7);
        try{CadProject.fromJson(broken);check(false,"bad view rejected");}catch(org.json.JSONException e){check(true,"a dimension in an unknown view is rejected");}
        try{TechnicalSheet.validate(new double[]{0,0,0,1,1,5,0});check(false,"bad kind");}catch(IllegalArgumentException e){check(true,"unknown kind rejected");}
        // Circles in a view: a hole Ø10 at (25,30) drawn as 36 segments, plus the plate outline 100×60.
        java.util.List<Double> seg=new java.util.ArrayList<>();
        double[][] plate={{0,0},{100,0},{100,60},{0,60}};
        for(int i=0;i<4;i++){double[] a=plate[i],b=plate[(i+1)%4];for(double v:new double[]{0,0,a[0],a[1],b[0],b[1]})seg.add(v);}
        for(int i=0;i<36;i++){double t0=2*Math.PI*i/36,t1=2*Math.PI*(i+1)/36;for(double v:new double[]{0,0,25+5*Math.cos(t0),30+5*Math.sin(t0),25+5*Math.cos(t1),30+5*Math.sin(t1)})seg.add(v);}
        double[] data=new double[seg.size()];for(int i=0;i<data.length;i++)data[i]=seg.get(i);
        TechnicalSheet sheet=new TechnicalSheet(data,.5);
        java.util.List<double[]> circles=sheet.circles();
        check(circles.size()==1&&Math.abs(circles.get(0)[1]-25)<1e-9&&Math.abs(circles.get(0)[2]-30)<1e-9&&Math.abs(circles.get(0)[3]-5)<1e-9&&circles.get(0)[4]==1,"hole found as a closed circle Ø10 at (25,30); the plate is not a circle");
        float[] centre=sheet.paperOf(0,25,30);double[] c=sheet.centreAt(centre[0],centre[1],2);
        check(c!=null&&Math.abs(c[1]-25)<1e-9&&Math.abs(c[2]-30)<1e-9,"tap at the hole centre snaps to the centre");
        float[] edge=sheet.paperOf(0,30,30);double[] onEdge=sheet.circleAt(edge[0],edge[1],2);
        check(onEdge!=null&&Math.abs(onEdge[3]-5)<1e-9,"tap on the outline finds the circle");
        double[] dia=TechnicalSheet.circleDimension(0,25,30,5,true,45);
        check(Math.abs(TechnicalSheet.value(dia)-10)<1e-9,"diameter value Ø10");
        check(sheet.onCircle(0,25,30,5)&&!sheet.onCircle(0,25,30,6),"circle dimension is current only while the hole exists");
        double[] corner=sheet.snap(sheet.paperOf(0,100,60)[0],sheet.paperOf(0,100,60)[1],2);
        check(corner!=null&&corner[1]==100&&corner[2]==60,"corner snapping");
        check(sheet.onVertex(0,25,30),"a circle centre counts as a dimension point");
        // 0.94: legacy 7-value records, follow a changed model, angles, tolerances.
        JSONObject legacy=new CadProject().toJson();legacy.put("drawingDimensions",new org.json.JSONArray().put(new org.json.JSONArray(new double[]{0,0,0,100,0,1,8})));
        CadProject old=CadProject.fromJson(legacy);
        check(old.drawingDimensions.get(0).length==TechnicalSheet.LENGTH&&old.drawingDimensions.get(0)[11]==TechnicalSheet.NO_ANCHOR,"0.90 records load padded to the current layout");
        double[] width=sheet.withAnchors(TechnicalSheet.dimension(0,0,0,100,0,TechnicalSheet.HORIZONTAL,8));
        double[] hole=sheet.withAnchors(TechnicalSheet.circleDimension(0,25,30,5,true,45));
        java.util.List<Double> wide=new java.util.ArrayList<>();
        double[][] plate2={{0,0},{120,0},{120,60},{0,60}};
        for(int i=0;i<4;i++){double[] a=plate2[i],b=plate2[(i+1)%4];for(double v:new double[]{0,0,a[0],a[1],b[0],b[1]})wide.add(v);}
        for(int i=0;i<36;i++){double t0=2*Math.PI*i/36,t1=2*Math.PI*(i+1)/36;for(double v:new double[]{0,0,30+5*Math.cos(t0),30+5*Math.sin(t0),30+5*Math.cos(t1),30+5*Math.sin(t1)})wide.add(v);}
        double[] wideData=new double[wide.size()];for(int i=0;i<wideData.length;i++)wideData[i]=wide.get(i);
        TechnicalSheet wider=new TechnicalSheet(wideData,.5);
        double[] followed=wider.resolve(width);
        check(followed!=null&&Math.abs(TechnicalSheet.value(followed)-120)<1e-9,"width dimension follows the plate widened to 120 mm");
        double[] holeFollowed=wider.resolve(hole);
        check(holeFollowed!=null&&Math.abs(holeFollowed[1]-30)<1e-6&&Math.abs(TechnicalSheet.value(holeFollowed)-10)<1e-6,"hole diameter follows the moved hole");
        check(wider.resolve(TechnicalSheet.dimension(0,0,0,100,0,TechnicalSheet.HORIZONTAL,8))==null,"a record without anchors cannot be re-attached (shown stale)");
        double[] angle=TechnicalSheet.angle(0,0,0,100,0,0,60);
        check(Math.abs(TechnicalSheet.value(angle)-90)<1e-9&&TechnicalSheet.label(angle).equals("90.0°"),"angle at the plate corner is 90°");
        double[] tol=width.clone();tol[9]=.1;tol[10]=.1;check(TechnicalSheet.label(tol).equals("100.00 ±0.1"),"symmetric tolerance printed as ±");
        tol[10]=.05;check(TechnicalSheet.label(tol).equals("100.00 +0.1/−0.05"),"different deviations printed as +/−");
        TechnicalSheet.validate(tol);check(true,"tolerances validate");
        System.out.println("PASS SheetDimensionHostTest: "+checks+" checks");
    }
}
