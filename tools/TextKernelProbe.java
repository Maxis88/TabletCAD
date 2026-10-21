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
            // Overlapping contours like real fonts: L stem plus a slanted stroke crossing it.
            case "Ł":return new SketchText.Glyph(.8,Arrays.asList(new double[]{0,0,.6,0,.6,.2,.3,.2,.3,1,.1,1,.1,.2,0,.2},new double[]{-.05,.35,.45,.6,.45,.75,-.05,.5}));
            // Bold "S" outline from the tablet's variable system font, with its tiny bow-tie crossing.
            case "S":return new SketchText.Glyph(.9,Collections.singletonList(new double[]{0.112604,0.400257,0.137017,0.395774,0.163093,0.382321,0.184019,0.362225,0.192323,0.337146,0.19332,0.332829,0.195976,0.323196,0.200627,0.309909,0.19988,0.303349,0.192323,0.298947,0.216239,0.269717,0.233511,0.245138,0.257427,0.223546,0.291973,0.199962,0.320206,0.186344,0.336649,0.180614,0.347444,0.178704,0.378999,0.173721,0.404907,0.1704,0.419855,0.173721,0.442443,0.177376,0.487949,0.186344,0.529138,0.208267,0.5587,0.241484,0.56717,0.261745,0.569994,0.284,0.563101,0.309909,0.542424,0.331832,0.509955,0.35143,0.467023,0.369699,0.415953,0.387138,0.358405,0.404244,0.240486,0.444767,0.186427,0.471257,0.137848,0.504225,0.099484,0.545496,0.075401,0.596899,0.064772,0.638751,0.062031,0.65918,0.061119,0.686584,0.061782,0.747369,0.065354,0.779007,0.074737,0.806826,0.07864,0.825013,0.090349,0.847683,0.126886,0.894517,0.1704,0.934377,0.190994,0.949658,0.206939,0.958294,0.218647,0.965435,0.237165,0.972909,0.282671,0.985531,0.328179,0.993171,0.347942,0.995413,0.362724,0.99616,0.38531,0.993502,0.402583,0.990845,0.44975,0.986029,0.494925,0.974237,0.537608,0.9563,0.577301,0.933049,0.613507,0.904898,0.645728,0.872263,0.673296,0.835974,0.694887,0.796198,0.702527,0.779258,0.712492,0.755674,0.72146,0.730761,0.725446,0.704852,0.720131,0.693559,0.717391,0.676701,0.70917,0.662667,0.686251,0.641741,0.659346,0.628123,0.632109,0.62314,0.604456,0.626649,0.581951,0.637174,0.564596,0.654716,0.552389,0.679276,0.559696,0.682431,0.557039,0.685255,0.547406,0.694222,0.543089,0.706512,0.540764,0.712824,0.532127,0.731094,0.519836,0.74903,0.495256,0.7766,0.464365,0.800848,0.431481,0.817124,0.399594,0.823102,0.381656,0.82576,0.369699,0.828418,0.364467,0.827919,0.354087,0.826424,0.344205,0.827337,0.339804,0.834064,0.336898,0.825511,0.32685,0.819781,0.306255,0.814134,0.285329,0.800184,0.262078,0.772946,0.244141,0.74139,0.237165,0.715149,0.24248,0.689573,0.253774,0.663,0.272375,0.636177,0.294961,0.616829,0.344786,0.59391,0.393946,0.582616,0.413876,0.577301,0.425835,0.569329,0.429654,0.568416,0.441779,0.565011,0.476655,0.554714,0.508211,0.545414,0.518425,0.542673,0.52183,0.54176,0.542424,0.536445,0.571322,0.524487,0.598893,0.511532,0.617492,0.501568,0.657021,0.471341,0.694887,0.435799,0.724782,0.392618,0.741722,0.341797,0.746372,0.329839,0.745376,0.321203,0.734415,0.311238,0.742055,0.307833,0.741722,0.306255,0.737736,0.308249,0.746954,0.297038,0.750026,0.285329,0.748947,0.272458,0.745709,0.26706,0.74604,0.263406,0.736657,0.261496,0.739065,0.26241,0.745294,0.260749,0.747369,0.250451,0.736076,0.212918,0.730513,0.19498,0.720464,0.173721,0.700202,0.142499,0.672632,0.106292,0.640413,0.07208,0.62422,0.059458,0.610185,0.052814,0.59939,0.044843,0.58162,0.035541,0.53711,0.017936,0.490939,0.004982,0.47076,0.001245,0.455398,0.0,0.349105,0.0,0.314891,0.006644,0.290644,0.013619,0.260416,0.020594,0.235504,0.031887,0.174885,0.06801,0.120575,0.111275,0.0759,0.16384,0.043514,0.227865,0.02757,0.266396,0.018934,0.311238,0.025743,0.348274,0.046171,0.376343,0.076564,0.394279}));
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
        // Sketch transforms keep the text editable.
        SketchPrimitive mv=SketchTransform.move(flat,3,4);close("moved text anchor X",mv.textParams()[0],13,1e-9);check(mv.text().equals("OIL")&&Math.abs(mv.x1-13)<1e-9,"moved text keeps content and outline");
        SketchPrimitive rt=SketchTransform.rotate(flat,90,10,20);close("rotated text angle",rt.textParams()[4],90,1e-9);
        SketchPrimitive rebuilt=SketchText.with(rt,"OIL",rt.textParams(),FONT);double dev=0;for(int i=0;i<rt.textLoops().length;i++)dev=Math.max(dev,Math.abs(rt.textLoops()[i]-rebuilt.textLoops()[i]));check(dev<1e-9,"rotated outline equals the text rebuilt at the new angle");
        SketchPrimitive sc=SketchTransform.scale(flat,2,10,20);close("scaled letter height",sc.textParams()[2],10,1e-9);
        check(SketchPattern.transform(flat,"copy",0,0,0,0,30).isText(),"pattern copy of a text is a text");
        boolean mirrored=false;try{SketchMirror.copy(flat,"m",0,0,0,1);}catch(IllegalArgumentException x){mirrored=true;}check(mirrored,"mirroring a text is refused with a reason");
        // Circular engraving.
        CadProject r=plate();r.sketch.add(arc);add(r,arc,"cut",1);double v=volume(r);check(v<120000&&v>120000-letters*1.2,"text along a circle is engraved ("+(120000-v)+" mm³)");
        // Letters floating beside the plate do not join silently.
        CadProject f=plate();SketchPrimitive far=SketchText.create("t","II",SketchText.params(150,20,5,0,0,0,0,2),FONT);f.sketch.add(far);add(f,far,"join",2);
        String why=failure(f);check(why!=null&&why.contains("nie styka"),"letters off the body are reported: "+why);
        // Overlapping outlines (non-zero winding) are united, not rejected.
        SketchPrimitive ol=SketchText.create("t","Ł",SketchText.params(20,20,10,SketchText.FLAT,0,0,0,0),FONT);CadProject op=plate();op.sketch.add(ol);add(op,ol,"cut",1);
        String olWhy=failure(op);check(olWhy==null,"overlapping glyph contours engrave"+(olWhy==null?"":": "+olWhy));double removed=120000-volume(op);check(removed>0&&removed<(.6*.2+.2*.8+.5*.15)*100,"overlap counted once ("+removed+" mm³)");
        SketchPrimitive bs=SketchText.create("t","S",SketchText.params(20,20,8,SketchText.FLAT,0,0,0,0),FONT);CadProject sp=plate();sp.sketch.add(bs);add(sp,bs,"cut",.5);
        String sWhy=failure(sp);check(sWhy==null,"bold S with a self-crossing outline engraves"+(sWhy==null?"":": "+sWhy));
        // Free-standing letters as a new body beside the plate.
        CadProject e=plate();SketchPrimitive free=SketchText.create("t","OIL",SketchText.params(150,20,5,0,0,0,0,0),FONT);e.sketch.add(free);
        e.committedFeatures.add(SketchText.feature(e,free,"new",3,false,false));e.committedProfileIds.add(SketchText.REF+e.sketchId+"/"+free.id);e.featureBodyTargets.add(0);e.featureBodyOutputs.add(BodyGraph.allocate(e));
        close("letters as a new body beside the plate",volume(e),120000+letters*3,1e-6);
        System.out.println("PASS TextKernelProbe: "+checks+" checks");
    }
}
