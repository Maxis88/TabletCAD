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
        try{TechnicalSheet.validate(new double[]{0,0,0,1,1,3,0});check(false,"bad kind");}catch(IllegalArgumentException e){check(true,"unknown kind rejected");}
        System.out.println("PASS SheetDimensionHostTest: "+checks+" checks");
    }
}
