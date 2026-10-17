import pl.tabletcad.app.NativeCadEngine;
import pl.tabletcad.app.model.CadProject;
import pl.tabletcad.app.model.SketchPrimitive;
import org.json.JSONObject;

/** Runs on the connected Android runtime against the packaged JNI implementation. */
public final class NativeSequenceProbe {
    private static double volume(double[] mesh) {
        if (mesh.length == 0) throw new AssertionError("Empty mesh");
        double sum = 0;
        for (int i = 0; i < mesh.length; i += 13) {
            sum += mesh[i] * (mesh[i+4]*mesh[i+8]-mesh[i+5]*mesh[i+7])
                + mesh[i+1] * (mesh[i+5]*mesh[i+6]-mesh[i+3]*mesh[i+8])
                + mesh[i+2] * (mesh[i+3]*mesh[i+7]-mesh[i+4]*mesh[i+6]);
        }
        return Math.abs(sum / 6);
    }
    private static void close(double actual, double expected) {
        if (Math.abs(actual - expected) > 0.001) {
            throw new AssertionError("Volume " + actual + " != " + expected);
        }
    }
    public static void main(String[] args) throws Exception {
        double[] add = {0,0,10,10,20,20,10,0,0,32,1,0,0,0,1,0};
        double[] cut = {0,1,12,12,17,17,5,0,0,42,1,0,0,0,1,0};
        double[] chain = new double[32];
        System.arraycopy(add,0,chain,0,16);
        System.arraycopy(cut,0,chain,16,16);
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,add,null)),123880);
        double result = volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,chain,args[0]));
        close(result,123755);
        close(volume(NativeCadEngine.tessellateStep(args[0])),123755);
        double[] drawing=NativeCadEngine.technicalDrawing(args[0]);
        int[] views=new int[4];int hidden=0;
        if(drawing.length%6!=0) throw new AssertionError("Invalid drawing segments");
        for(int i=0;i<drawing.length;i+=6) {
            views[(int)drawing[i]]++;
            if(drawing[i+1]==1) hidden++;
            for(int j=2;j<6;j++) if(!Double.isFinite(drawing[i+j])) throw new AssertionError("Invalid drawing coordinate");
        }
        for(int count:views) if(count==0) throw new AssertionError("Missing projection/section");
        if(hidden==0) throw new AssertionError("Missing hidden lines");
        double[] offsetSection=NativeCadEngine.technicalDrawing(args[0],.1875);
        int offsetCount=0;
        for(int i=0;i<offsetSection.length;i+=6) if(offsetSection[i]==3) offsetCount++;
        if(offsetCount<=views[3]) throw new AssertionError("Section offset did not expose feature/hole");
        if(NativeCadEngine.technicalDrawing(args[0],0).length!=0) throw new AssertionError("Invalid section accepted");
        System.out.println("PASS technical drawing: views="+java.util.Arrays.toString(views)+" hidden="+hidden);
        double[] importedCut = {0,1,30,20,35,25,5,0,0,32,1,0,0,0,1,0};
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,importedCut,
            args[0]+".edited.step",args[0])),123630);
        close(volume(NativeCadEngine.tessellateStep(args[0]+".edited.step")),123630);
        double[] importedAdd = {0,0,40,20,45,25,5,0,0,32,1,0,0,0,1,0};
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,importedAdd,
            null,args[0])),123880);
        CadProject project = new CadProject();
        project.name="Wspornik-A";project.drawingNumber="TC-001";
        project.drawingAuthor="Test";project.drawingMaterial="Aluminium";project.drawingRevision="B";
        project.committedFeatures.add(add);
        project.committedFeatures.add(cut);
        project.committedProfileIds.add("profile-add");
        project.committedProfileIds.add("profile-cut");
        CadProject restored = CadProject.fromJson(new JSONObject(project.toJson().toString()));
        if (restored.committedFeatures.size()!=2) throw new AssertionError("Lost feature history");
        if(!restored.drawingNumber.equals("TC-001")||!restored.drawingMaterial.equals("Aluminium")
                ||!restored.drawingRevision.equals("B")||!restored.name.equals("Wspornik-A"))
            throw new AssertionError("Drawing data lost");
        if (!restored.committedProfileIds.get(1).equals("profile-cut")) {
            throw new AssertionError("Lost profile association");
        }
        close(restored.committedFeatures.get(1)[6],5);
        project.sketch.add(new SketchPrimitive("sketch-profile", "Prostokąt", 1,2,11,12));
        project.archiveCurrentSketch();
        String originalSketchId = project.sketchId;
        project.sketchId = "second-sketch";
        project.sketch.clear();
        project.sketchPlane = "XZ";
        project.setPrincipalSketchFrame("XZ");
        project.sketch.add(new SketchPrimitive("other-profile", "Okrąg", 3,4,8,4));
        project.archiveCurrentSketch();
        CadProject sketches = CadProject.fromJson(new JSONObject(project.toJson().toString()));
        sketches.restoreSketch(sketches.savedSketches.get(0));
        if (!originalSketchId.equals(sketches.sketchId) || !sketches.sketchPlane.equals("XY")
                || !sketches.sketch.get(0).id.equals("sketch-profile")) {
            throw new AssertionError("Archived sketch lost");
        }
        sketches.restoreSketch(sketches.savedSketches.get(1));
        close(sketches.sketchVz,1);
        close(sketches.sketch.get(0).x2,8);
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,chain,null)),123755);
        double[] base = {0,0,0,40,30,0,32};
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,base,chain,null)),39275);
        double[] edited = chain.clone();
        edited[4] = 25;
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,edited,null)),124255);
        double[] disjoint = add.clone();
        disjoint[7] = 1000;
        if (NativeCadEngine.evaluateFeatures(80,48,32,0,null,disjoint,null).length != 0) {
            throw new AssertionError("Disconnected addition accepted");
        }
        disjoint[1] = 1;
        if (NativeCadEngine.evaluateFeatures(80,48,32,0,null,disjoint,null).length != 0) {
            throw new AssertionError("No-op cut accepted");
        }
        System.out.println("PASS: add volume=123880; add+cut volume="+result
            + "; STEP round-trip; schema history round-trip");
    }
}
