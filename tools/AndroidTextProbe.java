import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Device only: glyph outlines of the system fonts (islands, Polish letters) and engraving them. */
public final class AndroidTextProbe {
    static void check(boolean ok,String t){if(!ok)throw new AssertionError(t);System.out.println("PASS "+t);}
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] r=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,r,at,f.length);at+=f.length;}return r;}
    public static void main(String[] args) {
        AndroidTextFont font=new AndroidTextFont();
        for(int f=0;f<SketchText.FONTS.length;f++){SketchText.Glyph o=font.glyph("O",f);check(o!=null&&o.loops.size()==2,"font "+f+": O has an outer loop and an island");
            SketchText.Glyph b=font.glyph("B",f);check(b.loops.size()==3,"font "+f+": B has two islands");check(font.glyph("Ł",f)!=null&&font.glyph("ą",f)!=null,"font "+f+": Polish letters");}
        SketchPrimitive t=SketchText.create("t","TabletCAD 2026 ŻÓŁW",SketchText.params(5,25,6,SketchText.FLAT,0,0,0,0),font);
        check(Math.abs((t.y2-t.y1)-6)<1.5,"capital height close to 6 mm ("+(t.y2-t.y1)+")");
        CadProject p=new CadProject();p.emptyDocument=false;p.width=150;p.height=60;p.depth=10;p.featureBase=new double[]{0,0,0,150,60,0,10};BodyGraph.begin(p);p.sketchOriginZ=10;p.sketch.add(t);
        p.committedFeatures.add(SketchText.feature(p,t,"cut",.6,true,false));p.committedProfileIds.add(SketchText.REF+p.sketchId+"/"+t.id);p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);
        double[] r=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,BodyGraph.pack(p,packed(p)),null);
        check(r.length==7,"real-font text engraved: "+(r.length==7?"":NativeCadEngine.lastFailure().getMessage()));check(r[0]<90000&&r[0]>90000-150*60*.6,"engraving removed material ("+(90000-r[0])+" mm³)");
        SketchPrimitive ring=SketchText.create("r","TABLETCAD",SketchText.params(75,30,5,SketchText.OUTSIDE,90,18,1,0),font);
        CadProject q=new CadProject();q.emptyDocument=false;q.width=150;q.height=60;q.depth=10;q.featureBase=new double[]{0,0,0,150,60,0,10};BodyGraph.begin(q);q.sketchOriginZ=10;q.sketch.add(ring);
        q.committedFeatures.add(SketchText.feature(q,ring,"join",1,false,false));q.committedProfileIds.add(SketchText.REF+q.sketchId+"/"+ring.id);q.featureBodyTargets.add(1);q.featureBodyOutputs.add(1);
        r=NativeCadEngine.bodyProperties(q.width,q.height,q.depth,0,q.featureBase,BodyGraph.pack(q,packed(q)),null);
        check(r.length==7&&r[0]>90000,"bold text along a circle embossed");
        System.out.println("PASS AndroidTextProbe");
    }
}
