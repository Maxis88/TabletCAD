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
        System.out.println("PASS CompactPlacementHostTest: "+checks+" checks");
    }
}
