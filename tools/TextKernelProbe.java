import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Sketch text: flat and circular layout, letters with islands, embossing, engraving, saving and following edits. */
public final class TextKernelProbe {
    static int checks;
    static void check(boolean ok,String t){if(!ok)throw new AssertionError(t);checks++;System.out.println("PASS "+t);}
    static void close(String what,double a,double e,double tol){if(Math.abs(a-e)>tol)throw new AssertionError(what+": "+a+" != "+e);checks++;System.out.println("PASS "+what+" = "+a);}
    /** Synthetic font (capital height 1): O with an island, I, L and space. */
    static final SketchText.Font FONT=(c,font)->{
        switch(c){
            case "O":return new SketchText.Glyph(1.0,Arrays.asList(new double[]{0,0,.8,0,.8,1,0,1},new double[]{.2,.2,.2,.8,.6,.8,.6,.2}));
            case "I":return new SketchText.Glyph(.4,Collections.singletonList(new double[]{0,0,.2,0,.2,1,0,1}));
            case "L":return new SketchText.Glyph(.8,Collections.singletonList(new double[]{0,0,.6,0,.6,.2,.2,.2,.2,1,0,1}));
            case " ":return new SketchText.Glyph(.4,Collections.emptyList());
            default:return null;
        }
    };
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] r=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,r,at,f.length);at+=f.length;}return r;}
    static double volume(CadProject p){double[] r=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,BodyGraph.pack(p,packed(p)),null);if(r.length!=7)throw new AssertionError("Evaluation failed: "+NativeCadEngine.lastFailure().getMessage());return r[0];}
    static String failure(CadProject p){double[] r=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,BodyGraph.pack(p,packed(p)),null);return r.length==7?null:NativeCadEngine.lastFailure().getMessage();}
    static CadProject plate(){CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=20;p.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(p);p.sketchOriginZ=20;return p;}
    static void add(CadProject p,SketchPrimitive t,String op,double depth){
        p.committedFeatures.add(SketchText.feature(p,t,op,depth,false,false));p.committedProfileIds.add(SketchText.REF+p.sketchId+"/"+t.id);p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);}
    public static void main(String[] args)throws Exception {
        // Layout.
        SketchPrimitive flat=SketchText.create("t","OIL",SketchText.params(10,20,5,SketchText.FLAT,0,0,0,0),FONT);
        check(SketchText.loopCount(flat.textLoops())==4,"O has an island: 4 loops for OIL");
        close("flat text starts at the anchor",flat.x1,10,1e-9);close("capital height",flat.y2-flat.y1,5,1e-9);close("text width (advances 1+0.4, L 0.6)",flat.x2-flat.x1,(1+.4+.6)*5,1e-9);
        SketchPrimitive turned=SketchText.create("t","I",SketchText.params(0,0,5,SketchText.FLAT,90,0,0,0),FONT);
        close("rotated 90°: letter lies along −X",turned.x1,-5,1e-9);close("rotated 90°: width along Y",turned.y2,1,1e-9);
        SketchPrimitive spaced=SketchText.create("t","II",SketchText.params(0,0,5,SketchText.FLAT,0,0,0,1),FONT);close("extra spacing 1 mm",spaced.x2,(.4*5+1)+.2*5,1e-9);
        SketchPrimitive arc=SketchText.create("a","OIL",SketchText.params(50,30,5,SketchText.OUTSIDE,90,20,0,0),FONT);
        double rmin=1e9,rmax=0;for(double[] l:SketchText.loops(arc.textLoops()))for(int i=0;i<l.length;i+=2){double r=Math.hypot(l[i]-50,l[i+1]-30);rmin=Math.min(rmin,r);rmax=Math.max(rmax,r);}
        close("letters stand on the circle (inner radius)",rmin,20,1e-6);close("letters reach the radius plus height",rmax,25,1e-6);
        check(arc.y1>30,"text centred at the top of the circle");close("text centred about the vertical through the centre",(arc.x1+arc.x2)/2,50,1.5);
        SketchPrimitive in=SketchText.create("a","OIL",SketchText.params(50,30,5,SketchText.INSIDE,-90,20,0,0),FONT);
        rmin=1e9;rmax=0;for(double[] l:SketchText.loops(in.textLoops()))for(int i=0;i<l.length;i+=2){double r=Math.hypot(l[i]-50,l[i+1]-30);rmin=Math.min(rmin,r);rmax=Math.max(rmax,r);}
        close("inside text hangs from the circle",rmax,20,1e-6);close("inside text bottom",rmin,15,1e-6);check(in.y2<30,"inside text at the bottom");
        boolean tooLong=false;try{SketchText.create("a","OOOOOOOOOOOOOOOOOOOOOOOOOOO",SketchText.params(0,0,5,SketchText.OUTSIDE,90,10,0,0),FONT);}catch(IllegalArgumentException e){tooLong=true;}check(tooLong,"text longer than the circle is rejected");
        boolean missing=false;try{SketchText.create("t","OX",SketchText.params(0,0,5,0,0,0,0,0),FONT);}catch(IllegalArgumentException e){missing=e.getMessage().contains("X");}check(missing,"missing glyph is named");
        // JSON.
        SketchPrimitive back=SketchPrimitive.fromJson(new org.json.JSONObject(flat.toJson().toString()));
        check(back.isText()&&back.text().equals("OIL")&&Arrays.equals(back.textLoops(),flat.textLoops())&&Arrays.equals(back.textParams(),flat.textParams()),"text saved and read back");
        check(back.closedProfile(),"text is an extrudable profile");
        boolean noRect=false;try{flat.featureType();}catch(IllegalArgumentException e){noRect=true;}check(noRect,"text is never extruded as its bounding rectangle");
        // Emboss: OIL 5 mm high, 2 mm on the top face. O = 0.8−0.24, I = 0.2, L = 0.6·0.2+0.2·0.8 → ×25 mm².
        double letters=((.8-.24)+.2+(.12+.16))*25;
        CadProject p=plate();p.sketch.add(flat);add(p,flat,"join",2);
        close("embossed text adds letter area × height (island of O stays open)",volume(p),120000+letters*2,1e-6);
        CadProject saved=CadProject.fromJson(new org.json.JSONObject(p.toJson().toString()));close("embossed text after reopening",volume(saved),120000+letters*2,1e-6);
        // Engrave 1 mm.
        CadProject c=plate();c.sketch.add(flat);add(c,flat,"cut",1);close("engraved text removes letter area × depth",volume(c),120000-letters,1e-6);
        // The extrusion follows an edited text (the record is refreshed from the sketch).
        int at=c.sketch.indexOf(flat);c.sketch.set(at,SketchText.with(flat,"II",flat.textParams(),FONT));
        close("engraving follows the edited text",volume(c),120000-2*.2*25,1e-6);
        c.sketch.set(at,SketchText.moved(c.sketch.get(at),5,5));close("moved text keeps its volume",volume(c),120000-2*.2*25,1e-6);
        // Circular engraving.
        CadProject r=plate();r.sketch.add(arc);add(r,arc,"cut",1);double v=volume(r);check(v<120000&&v>120000-letters*1.2,"text along a circle is engraved ("+(120000-v)+" mm³)");
        // Letters floating beside the plate do not join silently.
        CadProject f=plate();SketchPrimitive far=SketchText.create("t","II",SketchText.params(150,20,5,0,0,0,0,2),FONT);f.sketch.add(far);add(f,far,"join",2);
        String why=failure(f);check(why!=null&&why.contains("nie styka"),"letters off the body are reported: "+why);
        // Free-standing letters as a new body beside the plate.
        CadProject e=plate();SketchPrimitive free=SketchText.create("t","OIL",SketchText.params(150,20,5,0,0,0,0,0),FONT);e.sketch.add(free);
        e.committedFeatures.add(SketchText.feature(e,free,"new",3,false,false));e.committedProfileIds.add(SketchText.REF+e.sketchId+"/"+free.id);e.featureBodyTargets.add(0);e.featureBodyOutputs.add(BodyGraph.allocate(e));
        close("letters as a new body beside the plate",volume(e),120000+letters*3,1e-6);
        System.out.println("PASS TextKernelProbe: "+checks+" checks");
    }
}
