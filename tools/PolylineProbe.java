import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class PolylineProbe {
    static void require(boolean good,String label){if(!good)throw new AssertionError(label);System.out.println("PASS "+label);}
    public static void main(String[] args)throws Exception {
        CadProject doc=new CadProject();doc.setPrincipalSketchFrame("XZ");doc.sketchPlane="XZ";
        PolylineDraft.add(doc.polylineDraft,0,0);PolylineDraft.add(doc.polylineDraft,20,0);PolylineDraft.add(doc.polylineDraft,0,20);
        CadProject saved=CadProject.fromJson(doc.toJson());
        require(saved.polylineDraft.size()==3&&saved.sketchPlane.equals("XZ")&&saved.sketchVz==1,"schema17 preserves unfinished points and work plane");
        try{saved.archiveCurrentSketch();throw new AssertionError();}catch(org.json.JSONException expected){System.out.println("PASS draft cannot be silently archived away");}
        List<SketchPrimitive> open=PolylineDraft.open(saved.polylineDraft);
        require(open.size()==2&&!open.get(0).id.equals(open.get(1).id),"open chain becomes separate editable segments");
        SketchPrimitive closed=PolylineDraft.closed(saved.polylineDraft);
        require(closed.vertices.length==6&&closed.closedProfile(),"closed chain is immediately a model profile");
        double[] mesh=NativeCadEngine.evaluateFeatures(80,48,32,0,closed.appendVertices(new double[]{closed.featureType(),0,0,20,20,1,10}),new double[0],null);
        require(mesh.length>0,"point-by-point contour extrudes on XZ in OCCT");
        double volume=0;
        for(int i=0;i<mesh.length;i+=13)volume+=mesh[i]*(mesh[i+4]*mesh[i+8]-mesh[i+5]*mesh[i+7])
            +mesh[i+1]*(mesh[i+5]*mesh[i+6]-mesh[i+3]*mesh[i+8])+mesh[i+2]*(mesh[i+3]*mesh[i+7]-mesh[i+4]*mesh[i+6]);
        require(Math.abs(Math.abs(volume/6)-2000)<.01,"draft profile volume 2000 mm3");
        try{PolylineDraft.add(saved.polylineDraft,0,20);throw new AssertionError();}catch(IllegalArgumentException expected){System.out.println("PASS duplicate consecutive point rejected");}
        try{PolylineDraft.add(saved.polylineDraft,Double.NaN,0);throw new AssertionError();}catch(IllegalArgumentException expected){System.out.println("PASS invalid point rejected");}
        List<double[]> crossed=Arrays.asList(new double[]{0,0},new double[]{20,20},new double[]{0,20},new double[]{20,0});
        try{PolylineDraft.closed(crossed);throw new AssertionError();}catch(IllegalArgumentException expected){System.out.println("PASS self-crossing closure rejected");}
        List<double[]> one=new ArrayList<>();PolylineDraft.add(one,0,0);
        try{PolylineDraft.open(one);throw new AssertionError();}catch(IllegalArgumentException expected){System.out.println("PASS incomplete open chain rejected");}
        System.out.println("ALL POLYLINE TESTS PASSED");
    }
}
