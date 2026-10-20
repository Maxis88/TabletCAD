import pl.tabletcad.app.CompactPlacement;
import pl.tabletcad.app.ToolbarCatalog;

/** 1.0.0-rc2: small property cards next to the edited geometry. */
public final class CompactPlacementHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    public static void main(String[] args){
        // Work area: tree 224 px on the left, strip+bar 108 px on top, history from 1100.
        float L=224,T=110,R=2000,B=1100,W=496,H=300,M=16,G=56;
        float[] a=CompactPlacement.place(800,500,W,H,L,T,R,B,M,G);
        check(a[0]==856&&!CompactPlacement.covers(a,W,H,800,500),"card to the right of the touch, the touched edge stays visible");
        float[] b=CompactPlacement.place(1800,500,W,H,L,T,R,B,M,G);
        check(b[0]+W<=1800-G+1e-3&&!CompactPlacement.covers(b,W,H,1800,500),"near the right border the card goes to the left");
        float[] c=CompactPlacement.place(900,1080,W,H,L,T,R,B,M,G);
        check(c[1]+H<=B-M+1e-3,"never under the history bar");
        float[] d=CompactPlacement.place(900,115,W,H,L,T,R,B,M,G);
        check(d[1]>=T+M,"never under the options strip");
        float[] e=CompactPlacement.place(Float.NaN,Float.NaN,W,H,L,T,R,B,M,G);
        check(e[0]==R-M-W&&e[1]==T+M,"no touch: top-right corner of the work area");
        float[] f=CompactPlacement.place(300,500,W,H,L,T,R,B,M,G);
        check(f[0]>=L+M,"never under the model tree");
        float[] g=CompactPlacement.place(600,500,W,H,L,T,700,B,M,G);
        check(g[0]>=L+M&&g[0]+W<=700-M+1e-3||g[0]==L+M,"narrow area: clamped inside");
        check(ToolbarCatalog.allows(true,"Punkt")&&ToolbarCatalog.allows(false,"Wyciągnij"),"toolbar groups unchanged");
        // Extrusion options never cover the arrow (base 1000,800 → tip 1000,500).
        float[] o=CompactPlacement.beside(1000,800,1000,500,480,212,224,110,2000,1100,68);
        boolean hits=false;for(int i=0;i<=20;i++){float y=800-300*i/20f;if(1000>=o[0]&&1000<=o[0]+480&&y>=o[1]&&y<=o[1]+212)hits=true;}
        check(!hits&&!CompactPlacement.covers(o,480,212,1000,500),"options group beside the arrow, not on its shaft or tip");
        float[] edge=CompactPlacement.beside(1900,800,1960,500,480,212,224,110,2000,1100,68);
        check(edge[0]>=224&&edge[0]+480<=2000,"near the screen edge the group moves to the other side");
        // Regular polygon: dragging a vertex scales and turns it; it stays regular.
        double[] hex=pl.tabletcad.app.SketchShapes.drag("h","Wielokąt",0,0,10,0,6).vertices;
        double[] moved=pl.tabletcad.app.SketchShapes.dragRegularVertex(hex,0,0,20);
        check(moved!=null&&Math.abs(moved[0])<1e-9&&Math.abs(moved[1]-20)<1e-9,"dragged vertex lands where the finger is");
        double[] cc=pl.tabletcad.app.SketchShapes.regularCentre(moved);
        check(cc!=null&&Math.abs(cc[0])<1e-9&&Math.abs(cc[1])<1e-9,"still a regular hexagon about the same centre (radius 20, turned 90°)");
        check(pl.tabletcad.app.SketchShapes.regularCentre(new double[]{0,0,10,0,10,5,0,7})==null,"an irregular contour keeps per-vertex editing");
        // Dimension offsets are saved with the project.
        pl.tabletcad.app.model.CadProject p=new pl.tabletcad.app.model.CadProject();p.dimensionOffsets.put("e:length:0",7.5);
        check(pl.tabletcad.app.model.CadProject.fromJson(new org.json.JSONObject(p.toJson().toString())).dimensionOffsets.get("e:length:0")==7.5,"dimension moved away from the edge is saved");
        System.out.println("PASS CompactPlacementHostTest: "+checks+" checks");
    }
}
