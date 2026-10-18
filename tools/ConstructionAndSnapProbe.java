import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class ConstructionAndSnapProbe {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    public static void main(String[] args)throws Exception {
        SketchPrimitive[] shapes={new SketchPrimitive("r","Prostokąt",0,0,20,10),new SketchPrimitive("c","Okrąg",0,0,5,0),new SketchPrimitive("p",new double[]{0,0,20,0,0,10}),new SketchPrimitive("a",SketchCurves.arc(0,0,5,5,10,0),false),new SketchPrimitive("s",new double[]{0,0,5,8,10,0},"Splajn")};
        for(SketchPrimitive shape:shapes){SketchPrimitive helper=SketchEditing.toggleConstruction(shape);check(helper.construction&&!helper.closedProfile(),"construction excludes profile "+shape.type);check(SketchPrimitive.fromJson(helper.toJson()).construction,"construction survives JSON "+shape.type);check(SketchMirror.copy(helper,"copy",0,0,1,0).construction,"mirror preserves construction "+shape.type);check(!SketchEditing.toggleConstruction(helper).construction,"construction can be disabled "+shape.type);}
        SketchPrimitive line=new SketchPrimitive("line","Linia",0,0,10,0).withConstruction(true);check(SketchDimensions.apply(line,20,45,true).construction,"dimension edit preserves construction flag");
        check(SketchConstraints.edit(Arrays.asList(line),new ArrayList<>(),line.id,line).get(0).construction,"solver preserves construction flag");
        SketchPrimitive source=new SketchPrimitive("moving","Prostokąt",10,80,20,90),target=new SketchPrimitive("target","Linia",50,0,50,100);
        double[] translation=SketchSnapper.translation(source,29.7,0,Arrays.asList(source,target),1);check(Math.abs(translation[0]-30)<1e-8&&translation[1]==0,"rectangle snaps by real edge independent of grab position");
        double[] snapped=SketchSnapper.snap(49.8,80,Arrays.asList(target),null,1,null,null);check(Math.abs(snapped[0]-50)<1e-8,"point snaps to target edge interior");
        check(!ToolbarCatalog.allows(true,"Pomocnicza"),"construction is no longer a separate drawing command");
        check(SketchShapes.drag("square","Kwadrat",0,0,10,7,6).x2==10&&SketchShapes.drag("square","Kwadrat",0,0,10,7,6).y2==10,"square creates equal sides");
        System.out.println("ALL CONSTRUCTION AND SNAP TESTS PASSED");
    }
}
