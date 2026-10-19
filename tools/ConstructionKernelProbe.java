import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** OCCT results of 0.75: exact face analysis, sketches and splits on construction planes. */
public final class ConstructionKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static void close(String what,double actual,double expected,double tolerance){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(what+": "+actual+" != "+expected);checks++;System.out.println("PASS "+what+" = "+actual);}
    static CadProject plate(){CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=20;p.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(p);return p;}
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] raw=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,raw,at,f.length);at+=f.length;}BodyGraph.validate(p);return BodyGraph.pack(p,raw);}
    static double[] properties(CadProject p){double[] r=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,packed(p),null);if(r.length!=7)throw new AssertionError("Evaluation failed: "+NativeCadEngine.lastFailure().getMessage());return r;}
    static CadProject framed(double[] f){CadProject p=new CadProject();p.sketchOriginX=f[0];p.sketchOriginY=f[1];p.sketchOriginZ=f[2];p.sketchUx=f[3];p.sketchUy=f[4];p.sketchUz=f[5];p.sketchVx=f[6];p.sketchVy=f[7];p.sketchVz=f[8];return p;}
    public static void main(String[] args)throws Exception {
        final double plate=120000;
        CadProject h=plate();double[] cut=ExtrusionPlan.feature(framed(new double[]{0,0,20,1,0,0,0,1,0}),new SketchPrimitive("h","Okrąg",30,30,35,30),"cut",20,0,false,false);
        h.committedFeatures.add(cut);h.committedProfileIds.add("h");h.featureBodyTargets.add(1);h.featureBodyOutputs.add(1);
        double[] faces=NativeCadEngine.faceAnalysis(100,60,20,0,h.featureBase,packed(h),null);
        check(faces.length>0&&faces.length%GeometryMeasure.FACE_STRIDE==0,"face analysis rows: "+faces.length/12);
        double total=0;int planes=0,cylinders=0;double[] topRow=null,cylinderRow=null;
        for(int i=0;i<faces.length;i+=12){total+=faces[i+2];if(faces[i+3]==0){planes++;if(Math.abs(faces[i+9]-1)<1e-12&&Math.abs(faces[i+6]-20)<1e-9)topRow=Arrays.copyOfRange(faces,i,i+12);}if(faces[i+3]==1){cylinders++;cylinderRow=Arrays.copyOfRange(faces,i,i+12);}}
        check(planes==6&&cylinders>=1,"plate with a hole: 6 planar faces and a cylinder ("+planes+"/"+cylinders+")");
        close("top face area (100×60 − π·5²)",topRow[2],6000-Math.PI*25,1e-6);
        close("hole wall radius",cylinderRow[10],5,1e-9);close("hole wall area",Arrays.stream(faces).count()>0?areaOfType(faces,1):0,2*Math.PI*5*20,1e-6);
        double sum=2*(6000-Math.PI*25)+2*(100*20)+2*(60*20)+2*Math.PI*5*20;close("sum of exact face areas = total surface",total,sum,1e-6);
        close("total surface agrees with body properties",properties(h)[1],sum,1e-6);
        GeometryMeasure.Direction top=GeometryMeasure.faceDirection(topRow),axis=GeometryMeasure.faceDirection(cylinderRow);
        close("hole axis is perpendicular to the top face (angle line–plane)",GeometryMeasure.angle(axis,top),90,1e-9);
        double[] sideRow=null;for(int i=0;i<faces.length;i+=12)if(faces[i+3]==0&&Math.abs(faces[i+7]-1)<1e-12)sideRow=Arrays.copyOfRange(faces,i,i+12);
        close("top and side faces are perpendicular",GeometryMeasure.angle(top,GeometryMeasure.faceDirection(sideRow)),90,1e-9);

        // Sketch on an angled construction plane: Ø10 × 15 boss as a new body on a plane through the top edge, turned 30°.
        Datum angled=Datum.create(Datum.PLANE,"angle","XY",new double[]{0,0,40,1,0,0,0,0,1,30},null);
        CadProject b=plate();double[] boss=ExtrusionPlan.feature(framed(angled.sketchFrame()),new SketchPrimitive("c","Okrąg",50,10,55,10),"new",15,0,false,false);
        b.committedFeatures.add(boss);b.committedProfileIds.add("c");b.featureBodyTargets.add(0);b.featureBodyOutputs.add(BodyGraph.allocate(b));
        double[] bp=properties(b);close("boss on an angled datum plane is an exact cylinder",bp[0]-plate,Math.PI*25*15,1e-6);check(bp[5]==2,"boss is a separate body above the plate");
        double[] fa=NativeCadEngine.faceAnalysis(100,60,20,0,b.featureBase,packed(b),null);boolean tilted=false;
        for(int i=0;i<fa.length;i+=12)if(fa[i+3]==1&&Math.abs(Math.abs(fa[i+8])-Math.sin(Math.toRadians(30)))<1e-9&&Math.abs(Math.abs(fa[i+9])-Math.cos(Math.toRadians(30)))<1e-9)tilted=true;
        check(tilted,"boss axis follows the 30° plane normal");

        // Split by an offset construction plane 5 mm below the top face.
        Datum offset=Datum.create(Datum.PLANE,"offset","XY",new double[]{0,0,20,0,0,1,-5},null);
        CadProject s=plate();double[] split=BodySplit.create(BodyGraph.allocate(s),offset.origin,offset.direction);s.committedFeatures.add(split);s.committedProfileIds.add("");s.featureBodyTargets.add(1);s.featureBodyOutputs.add(1);
        double[] sp=properties(s);check(sp[5]==2,"offset plane splits the plate into two bodies");close("split keeps the volume",sp[0],plate,1e-6);
        System.out.println("PASS ConstructionKernelProbe: "+checks+" checks");
    }
    static double areaOfType(double[] f,int type){double a=0;for(int i=0;i<f.length;i+=12)if(f[i+3]==type)a+=f[i+2];return a;}
}
