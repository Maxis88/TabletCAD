import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class DirectionProbe {
    static void require(boolean v,String label){if(!v)throw new AssertionError(label);System.out.println("PASS "+label);}
    static void close(double a,double b,String label){require(Math.abs(a-b)<.001,label+" "+a+" / "+b);}
    static double volume(double[] mesh){require(mesh.length>0,"nonempty directed B-Rep");double v=0;for(int i=0;i<mesh.length;i+=13)v+=mesh[i]*(mesh[i+4]*mesh[i+8]-mesh[i+5]*mesh[i+7])+mesh[i+1]*(mesh[i+5]*mesh[i+6]-mesh[i+3]*mesh[i+8])+mesh[i+2]*(mesh[i+3]*mesh[i+7]-mesh[i+4]*mesh[i+6]);return Math.abs(v/6);}
    static double bound(double[] mesh,int axis,boolean max){double result=max?-Double.MAX_VALUE:Double.MAX_VALUE;for(int i=0;i<mesh.length;i+=13)for(int j=0;j<9;j+=3)result=max?Math.max(result,mesh[i+j+axis]):Math.min(result,mesh[i+j+axis]);return result;}
    static double[] feature(boolean cut,double depth,double origin){return new double[]{0,cut?1:0,10,10,20,20,depth,0,0,origin,1,0,0,0,1,0};}
    public static void main(String[] args)throws Exception {
        for(int plane=0;plane<3;plane++)for(int type=0;type<3;type++) {
            SketchPrimitive profile=type==0?new SketchPrimitive("rect","Prostokąt",0,0,10,10):type==1?new SketchPrimitive("circle","Okrąg",0,0,5,0):new SketchPrimitive("poly",new double[]{0,0,10,0,0,10});
            double[] base=profile.appendVertices(new double[]{profile.featureType(),profile.x1,profile.y1,profile.x2,profile.y2,plane,-5});
            double[] mesh=NativeCadEngine.evaluateFeatures(80,48,32,0,base,new double[0],null);
            double[] forward=base.clone();forward[6]=5;
            double expected=type==1?volume(NativeCadEngine.evaluateFeatures(80,48,32,0,forward,new double[0],null)):type==0?500:250;
            close(volume(mesh),expected,"reverse base volume type="+type+" plane="+plane);
            int axis=plane==0?2:plane==1?1:0;
            close(bound(mesh,axis,false),-5,"reverse base negative limit");close(bound(mesh,axis,true),0,"reverse base starts on original plane");
        }
        double[] reverseAdd=feature(false,-5,0),reverseCut=feature(true,-5,0);
        double[] added=NativeCadEngine.evaluateFeatures(80,48,32,0,null,reverseAdd,null);
        close(volume(added),123380,"reverse add joins lower face");close(bound(added,2,false),-5,"reverse add points below zero");
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,reverseCut,null)),122380,"reverse cut goes inside from lower face");
        require(NativeCadEngine.evaluateFeatures(80,48,32,0,null,feature(true,5,0),null).length==0,"outward cut rejected without epsilon sliver");
        require(NativeCadEngine.evaluateFeatures(80,48,32,0,null,feature(false,5,0),null).length==0,"buried add rejected without epsilon material");
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,feature(false,5,32),null)),123380,"positive add unchanged");
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,feature(true,5,32),null)),122380,"positive cut unchanged");
        SketchPrimitive triangle=new SketchPrimitive("triangle",new double[]{10,10,20,10,10,20});
        double[] polygon=triangle.appendVertices(new double[]{triangle.featureType(),0,10,10,20,20,-5,0,0,0,1,0,0,0,1,0});
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,polygon,null)),123130,"reverse polygon add");
        polygon[1]=1;close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,polygon,null)),122630,"reverse polygon cut");
        double[] circle=feature(false,-5,0);circle[0]=1;circle[2]=15;circle[3]=15;circle[4]=20;circle[5]=15;
        double[] forwardCircle=circle.clone();forwardCircle[6]=5;forwardCircle[9]=32;
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,circle,null)),
            volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,forwardCircle,null)),"reverse circular add matches forward curved tessellation");
        double[] exported=NativeCadEngine.evaluateFeatures(80,48,32,0,null,reverseAdd,args[0]);
        close(volume(NativeCadEngine.tessellateStep(args[0])),volume(exported),"directed STEP round trip");
        CadProject doc=new CadProject();doc.featureBase=new double[]{0,0,0,10,10,0,-5};doc.featureDepth=-5;
        doc.committedFeatures.add(reverseCut);doc.committedProfileIds.add("cut");
        CadProject restored=CadProject.fromJson(doc.toJson());
        require(restored.featureBase[6]==-5&&restored.featureDepth==-5&&restored.committedFeatures.get(0)[6]==-5,"schema20 retains all direction signs");
        close(FeatureDirection.signed(5,true),-5,"UI reverse value");close(FeatureDirection.signed(5,false),5,"UI forward value");
        for(double bad:new double[]{0,-5,Double.NaN,Double.POSITIVE_INFINITY,5001})try{FeatureDirection.signed(bad,true);throw new AssertionError();}catch(IllegalArgumentException expected){System.out.println("PASS invalid depth rejected "+bad);}
        System.out.println("ALL DIRECTION TESTS PASSED");
    }
}
