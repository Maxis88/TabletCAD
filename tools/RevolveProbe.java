import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class RevolveProbe {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    static double[] eval(double[] base,double[] features,String path){return NativeCadEngine.evaluateFeatures(40,30,20,0,base,features,path);}
    static double volume(double[] mesh){return CurveTrimProbe.volume(mesh);}
    static double volume(double[] base,double[] features){double[] p=NativeCadEngine.bodyProperties(40,30,20,0,base,features,null);check(p.length==7&&p[0]>0,"exact B-Rep properties available");return p[0];}
    static void near(double a,double b,double tolerance,String label){check(Math.abs(a-b)<tolerance,label+" actual="+a+" expected="+b);}
    static SketchPrimitive rect(String id,double a,double b,double c,double d){return new SketchPrimitive(id,"Prostokąt",a,b,c,d);}
    static void reject(Runnable action,String label){boolean bad=false;try{action.run();}catch(IllegalArgumentException e){bad=true;}check(bad,label);}
    static double[] zRange(double[] mesh){double lo=1e99,hi=-lo;for(int i=0;i<mesh.length;i+=13)for(int j=2;j<9;j+=3){lo=Math.min(lo,mesh[i+j]);hi=Math.max(hi,mesh[i+j]);}return new double[]{lo,hi};}
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
    static void run(String[] args)throws Exception {
        CadProject p=CadProject.blank();SketchPrimitive source=rect("shaft",0,0,10,20);double[] axis={0,0,0,1};
        double[] f=Revolve.create(source,p,axis,360,false),mesh=eval(f,new double[0],args[0]);
        double cylinder=Math.PI*100*20,v=volume(f,new double[0]);
        near(v,cylinder,1e-5,"full revolution is a solid cylinder with exact volume");
        double[] properties=NativeCadEngine.bodyProperties(40,30,20,0,f,new double[0],null);
        near(properties[1],600*Math.PI,1e-5,"cylinder surface area comes from exact B-Rep");
        near(properties[2],0,1e-8,"cylinder centroid X");near(properties[3],10,1e-8,"cylinder centroid Y");near(properties[4],0,1e-8,"cylinder centroid Z");
        check(properties[5]==1&&properties[6]==3,"cylinder reports one solid and three faces");
        near(volume(mesh),v,45,"render mesh approximates actual cylinder without replacing exact geometry");
        near(volume(NativeCadEngine.tessellateStep(args[0])),volume(mesh),.001,"revolved render mesh STEP round trip");
        near(NativeCadEngine.bodyProperties(40,30,20,0,null,new double[0],args[0])[0],v,1e-5,"STEP preserves exact analytic cylinder volume");
        double[] half=f.clone();half[6]=180;double[] positive=eval(half,new double[0],null);
        near(volume(half,new double[0]),cylinder/2,1e-5,"partial 180 degree revolution is closed half-cylinder");
        half[6]=-180;double[] negative=eval(half,new double[0],null);
        near(volume(negative),volume(positive),.001,"reversed direction preserves half-cylinder volume");
        check(zRange(positive)[1]<1e-5&&zRange(negative)[0]>-1e-5,"reversing angle creates material on opposite side");
        double[] quarter=f.clone();quarter[6]=90;near(volume(quarter,new double[0]),cylinder/4,1e-5,"90 degree sector has exact volume");
        for(String plane:new String[]{"XZ","YZ"}) {
            CadProject framed=CadProject.blank();framed.setPrincipalSketchFrame(plane);framed.sketchOriginX=3;framed.sketchOriginY=4;framed.sketchOriginZ=5;
            near(volume(Revolve.create(source,framed,axis,360,false),new double[0]),v,1e-5,"translated "+plane+" frame preserves exact geometry");
        }
        SketchPrimitive horizontal=rect("horizontal",0,0,20,10);
        near(volume(Revolve.create(horizontal,p,new double[]{0,0,1,0},360,false),new double[0]),v,1e-5,"principal X axis supported");
        SketchPrimitive oblique=new SketchPrimitive("oblique",new double[]{0,0,8,-6,20,10,12,16});
        near(volume(Revolve.create(oblique,p,new double[]{0,0,.6,.8},360,false),new double[0]),v,1e-5,"oblique construction axis creates same cylinder");
        SketchPrimitive circle=new SketchPrimitive("torus","Okrąg",15,0,18,0);
        double[] torus=Revolve.create(circle,p,axis,360,false);
        near(volume(torus,new double[0]),2*Math.PI*Math.PI*15*9,1e-5,"circle profile creates true torus");
        double[] curves=Arrays.copyOf(SketchCurves.arc(0,-10,10,0,0,10),14);
        System.arraycopy(new double[]{0,0,10,0,0,0,-10},0,curves,7,7);
        SketchPrimitive semicircle=new SketchPrimitive("sphere",curves,true);
        near(volume(Revolve.create(semicircle,p,axis,360,false),new double[0]),4*Math.PI*1000/3,1e-5,"mixed analytic arc profile creates sphere");
        SketchPrimitive ring=rect("groove",8,8,12,12);
        double[] cut=Revolve.create(ring,p,axis,360,true),add=Revolve.create(ring,p,axis,360,false);
        near(volume(f,cut),v-Math.PI*(100-64)*4,1e-5,"revolved cut produces shaft groove");
        near(volume(f,add),v+Math.PI*(144-100)*4,1e-5,"revolved addition produces shaft collar");
        double[] disjoint=Revolve.create(rect("far",20,0,25,20),p,axis,360,false);
        check(eval(f,disjoint,null).length==0,"disconnected revolution addition rejected");
        disjoint[1]=1;check(eval(f,disjoint,null).length==0,"cut with no intersection rejected");
        reject(()->Revolve.create(rect("cross",-2,0,2,20),p,axis,360,false),"axis crossing profile rejected before kernel");
        reject(()->Revolve.create(new SketchPrimitive("crossCircle","Okrąg",0,0,3,0),p,axis,360,false),"axis crossing circle rejected analytically");
        reject(()->Revolve.create(source,p,new double[]{0,0,0,0},360,false),"zero axis rejected");
        reject(()->Revolve.create(source,p,axis,361,false),"angle exceeding full turn rejected");
        double[] bad=f.clone();bad[2]=-2;check(eval(bad,new double[0],null).length==0,"kernel also rejects axis crossing profile");
        bad=f.clone();bad[6]=Double.NaN;check(eval(bad,new double[0],null).length==0,"nonfinite angle rejected natively");
        bad=f.clone();bad[1]=1;check(eval(bad,new double[0],null).length==0,"root revolution cannot be subtraction");
        bad=f.clone();bad[21]=1;check(eval(bad,new double[0],null).length==0,"unknown revolution flags rejected");
        p.sketch.add(source);p.featureBase=f;p.featureBaseProfileId=source.id;p.archiveCurrentSketch();
        CadProject saved=CadProject.fromJson(p.toJson());check(Arrays.equals(saved.featureBase,f),"root revolution persists exact parameter array");
        check(CadTimeline.entries(saved).get(1).title.equals("Obrót profilu"),"base timeline identifies revolution");
        SketchTransactions.begin(saved);saved.sketch.set(0,rect(source.id,0,0,12,20));
        CadProject changed=SketchTransactions.candidate(saved);
        check(changed.featureBase[20]==0&&changed.featureBase[4]==12&&changed.featureBase[6]==360,"sketch transaction updates source without corrupting axis or angle");
        near(volume(changed.featureBase,new double[0]),Math.PI*144*20,1e-5,"source radius edit regenerates revolved solid");
        check(SketchTransactions.baseline(saved).featureBase[4]==10,"cancel baseline retains original shaft radius");
        saved=CadProject.fromJson(saved.toJson());check(SketchTransactions.active(saved),"working revolution source edit survives restart");
        p.sketch.add(ring);p.committedFeatures.add(cut);p.committedProfileIds.add(ring.id);p.archiveCurrentSketch();
        CadProject loaded=CadProject.fromJson(p.toJson());loaded.restoreSketch(loaded.savedSketches.get(0));
        check(loaded.sketch.get(1).x1==8&&loaded.sketch.get(1).x2==12,"archived revolution profile restores correct header coordinates");
        check(CadTimeline.entries(loaded).get(2).title.equals("Wycięcie obrotowe"),"history identifies revolved cut");
        JSONObject invalid=p.toJson();invalid.getJSONArray("featureBase").put(6,0);boolean rejected=false;
        try{CadProject.fromJson(invalid);}catch(JSONException expected){rejected=true;}check(rejected,"invalid saved angle rejected");
        System.out.println("ALL REVOLVE TESTS PASSED");
    }
}
