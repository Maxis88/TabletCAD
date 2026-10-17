import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class SymmetricProbe {
    static void require(boolean v,String label){if(!v)throw new AssertionError(label);System.out.println("PASS "+label);}
    static void close(double a,double b,String label){require(Math.abs(a-b)<.001,label+" "+a+" / "+b);}
    static double volume(double[] m){require(m.length>0,"nonempty symmetric B-Rep");double v=0;for(int i=0;i<m.length;i+=13)v+=m[i]*(m[i+4]*m[i+8]-m[i+5]*m[i+7])+m[i+1]*(m[i+5]*m[i+6]-m[i+3]*m[i+8])+m[i+2]*(m[i+3]*m[i+7]-m[i+4]*m[i+6]);return Math.abs(v/6);}
    static double bound(double[] m,int axis,boolean max){double r=max?-Double.MAX_VALUE:Double.MAX_VALUE;for(int i=0;i<m.length;i+=13)for(int j=0;j<9;j+=3)r=max?Math.max(r,m[i+j+axis]):Math.min(r,m[i+j+axis]);return r;}
    static double[] operation(int code,double depth,double z){return new double[]{0,code,10,10,20,20,depth,0,0,z,1,0,0,0,1,0};}
    public static void main(String[] args)throws Exception {
        for(int plane=0;plane<3;plane++)for(int type=0;type<3;type++) {
            SketchPrimitive p=type==0?new SketchPrimitive("rect","Prostokąt",0,0,10,10):type==1?new SketchPrimitive("circle","Okrąg",0,0,5,0):new SketchPrimitive("poly",new double[]{0,0,10,0,0,10});
            double[] base=p.appendVertices(new double[]{p.featureType(),p.x1,p.y1,p.x2,p.y2,FeatureDirection.baseCode(plane,true),12});
            double[] mesh=NativeCadEngine.evaluateFeatures(80,48,32,0,base,new double[0],null);
            int axis=plane==0?2:plane==1?1:0;
            close(bound(mesh,axis,false),-6,"base lower half type="+type+" plane="+plane);
            close(bound(mesh,axis,true),6,"base upper half type="+type+" plane="+plane);
            double[] oneSide=base.clone();oneSide[5]=plane;
            close(volume(mesh),volume(NativeCadEngine.evaluateFeatures(80,48,32,0,oneSide,new double[0],null)),"symmetric total length does not double volume");
            base[6]=-12;
            close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,base,new double[0],null)),volume(mesh),"symmetric geometry independent of direction sign");
        }
        double[] add=operation(2,10,32),cut=operation(3,4,35);
        cut[2]=12;cut[3]=12;cut[4]=17;cut[5]=17;
        double[] mesh=NativeCadEngine.evaluateFeatures(80,48,32,0,null,add,null);
        close(volume(mesh),123380,"centered addition overlaps inside and adds outside by half");close(bound(mesh,2,true),37,"top add ends at center plus half");
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,operation(3,10,0),null)),122380,"boundary symmetric cut removes only inward half");
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,operation(3,10,16),null)),121880,"internal symmetric cut removes full total depth");
        double[] chain=new double[32];System.arraycopy(add,0,chain,0,16);System.arraycopy(cut,0,chain,16,16);
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,chain,null)),123280,"symmetric add and cut history chain");
        double[] side={0,2,10,10,20,20,10,80,0,0,0,1,0,0,0,1};
        double[] sideMesh=NativeCadEngine.evaluateFeatures(80,48,32,0,null,side,null);
        close(volume(sideMesh),123380,"symmetric operation in YZ local frame");close(bound(sideMesh,0,true),85,"local-frame half extent");
        double[] circle=operation(2,10,32);circle[0]=1;circle[2]=15;circle[3]=15;circle[4]=20;circle[5]=15;
        double[] ordinary=circle.clone();ordinary[1]=0;ordinary[6]=5;
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,circle,null)),volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,ordinary,null)),"symmetric circular addition matches actual exposed half");
        SketchPrimitive triangle=new SketchPrimitive("triangle",new double[]{10,10,20,10,10,20});
        double[] polygon=triangle.appendVertices(new double[]{triangle.featureType(),2,10,10,20,20,6,0,0,32,1,0,0,0,1,0});
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,polygon,null)),123030,"symmetric polygon addition");
        double[] base={0,0,0,40,20,4,12};
        double[] exported=NativeCadEngine.evaluateFeatures(80,48,32,0,base,new double[0],args[0]);
        double[] roundTrip=NativeCadEngine.tessellateStep(args[0]);
        close(volume(roundTrip),9600,"symmetric STEP volume");close(bound(roundTrip,2,false),-6,"symmetric STEP retains negative half");close(bound(roundTrip,2,true),6,"symmetric STEP retains positive half");
        CadProject doc=CadProject.blank();doc.featureBase=base;doc.committedFeatures.add(add);doc.committedFeatures.add(cut);doc.committedProfileIds.add("add");doc.committedProfileIds.add("cut");doc.featureSymmetric=true;doc.featureOperation="cut";
        CadProject restored=CadProject.fromJson(doc.toJson());
        require(restored.featureSymmetric&&FeatureDirection.baseSymmetric(restored.featureBase[5])&&FeatureDirection.isCut(restored.committedFeatures.get(1)[1]),"schema22 retains base active and committed symmetry");
        require(CadTimeline.entries(restored).stream().anyMatch(item->item.kind==CadTimeline.Kind.FEATURE&&item.featureIndex==1&&item.title.equals("Wycięcie")),"symmetric cut is not mislabeled as addition");
        close(FeatureDirection.signed(12,true,true),12,"symmetric UI ignores reverse direction");
        for(double bad:new double[]{-1,3,7,1.5,Double.NaN})try{FeatureDirection.basePlane(bad);throw new AssertionError();}catch(IllegalArgumentException expected){System.out.println("PASS invalid base mode rejected "+bad);}
        for(double bad:new double[]{-1,4,1.5,Double.NaN})try{FeatureDirection.validateOperation(bad);throw new AssertionError();}catch(IllegalArgumentException expected){System.out.println("PASS invalid operation mode rejected "+bad);}
        double[] invalid=base.clone();invalid[5]=3;require(NativeCadEngine.evaluateFeatures(80,48,32,0,invalid,new double[0],null).length==0,"native invalid base mode rejected");
        invalid=add.clone();invalid[1]=4;require(NativeCadEngine.evaluateFeatures(80,48,32,0,null,invalid,null).length==0,"native invalid operation mode rejected");
        try{CadProject.fromJson(doc.toJson().put("featureBase",new JSONArray().put(0).put(0).put(0).put(40).put(20).put(3).put(12)));throw new AssertionError();}catch(JSONException expected){System.out.println("PASS invalid persisted base mode rejected");}
        System.out.println("ALL SYMMETRIC EXTRUSION TESTS PASSED");
    }
}
