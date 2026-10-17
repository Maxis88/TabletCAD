import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class TrimProbe {
    static SketchPrimitive line(String id,double x,double y,double u,double v){return new SketchPrimitive(id,"Linia",x,y,u,v);}
    static void require(boolean good,String label){if(!good)throw new AssertionError(label);System.out.println("PASS "+label);}
    static boolean close(double a,double b){return Math.abs(a-b)<1e-7;}
    static void replace(List<SketchPrimitive> all,String id,double x,double y) {
        SketchPrimitive target=null;for(SketchPrimitive p:all)if(p.id.equals(id))target=p;
        List<SketchPrimitive> pieces=SketchTrimmer.trim(all,target,x,y);int index=all.indexOf(target);
        all.remove(index);all.addAll(index,pieces);
    }
    public static void main(String[] args)throws Exception {
        SketchPrimitive target=line("main",0,0,30,0);
        List<SketchPrimitive> all=Arrays.asList(target,line("a",10,-5,10,5),line("b",20,-5,20,5));
        List<SketchPrimitive> parts=SketchTrimmer.trim(all,target,15,0);
        require(parts.size()==2&&close(parts.get(0).x2,10)&&close(parts.get(1).x1,20),"middle interval removed");
        require(parts.get(0).id.equals("main")&&!parts.get(1).id.equals("main"),"unique persistent piece identities");
        parts=SketchTrimmer.trim(all,target,2,0);
        require(parts.size()==1&&close(parts.get(0).x1,10)&&close(parts.get(0).x2,30),"end interval removed");
        SketchPrimitive circle=new SketchPrimitive("c","Okrąg",15,0,20,0);
        parts=SketchTrimmer.trim(Arrays.asList(target,circle),target,15,0);
        require(parts.size()==2&&close(parts.get(0).x2,10)&&close(parts.get(1).x1,20),"circle defines trim boundaries");
        SketchPrimitive rectangle=new SketchPrimitive("r","Prostokąt",0,0,30,20);
        parts=SketchTrimmer.trim(Arrays.asList(rectangle,line("a",10,-5,10,5),line("b",20,-5,20,5)),rectangle,15,0);
        require(parts.size()==5&&parts.stream().allMatch(p->p.type.equals("Linia")),"unused rectangle opens into five edges");
        try{SketchTrimmer.trim(Collections.singletonList(target),target,15,0);throw new AssertionError();}
        catch(IllegalArgumentException expected){System.out.println("PASS no intersection rejected without deleting line");}
        try{SketchTrimmer.trim(all,target,10,0);throw new AssertionError();}
        catch(IllegalArgumentException expected){System.out.println("PASS ambiguous intersection tap rejected");}
        List<SketchPrimitive> frame=new ArrayList<>(Arrays.asList(line("top",-5,0,25,0),line("right",20,-5,20,25),
            line("bottom",25,20,-5,20),line("left",0,25,0,-5)));
        replace(frame,"top",-3,0);replace(frame,"top",23,0);
        replace(frame,"right",20,-3);replace(frame,"right",20,23);
        replace(frame,"bottom",23,20);replace(frame,"bottom",-3,20);
        replace(frame,"left",0,23);replace(frame,"left",0,-3);
        SketchPrimitive profile=SketchContours.join(frame,null).profile;
        require(profile.vertices.length==8,"trimmed extended lines form closed profile");
        CadProject doc=new CadProject();doc.sketch.addAll(frame);
        require(CadProject.fromJson(doc.toJson()).sketch.size()==4,"trimmed geometry persists");
        double[] mesh=NativeCadEngine.evaluateFeatures(80,48,32,0,profile.appendVertices(
            new double[]{profile.featureType(),0,0,20,20,0,10}),new double[0],null);
        require(mesh.length>0,"trimmed profile extrudes in real OCCT");
        double volume=0;
        for(int i=0;i<mesh.length;i+=13)volume+=mesh[i]*(mesh[i+4]*mesh[i+8]-mesh[i+5]*mesh[i+7])
            +mesh[i+1]*(mesh[i+5]*mesh[i+6]-mesh[i+3]*mesh[i+8])+mesh[i+2]*(mesh[i+3]*mesh[i+7]-mesh[i+4]*mesh[i+6]);
        require(Math.abs(Math.abs(volume/6)-4000)<.01,"trimmed profile volume 4000 mm3");
        System.out.println("ALL TRIM TESTS PASSED");
    }
}
