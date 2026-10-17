import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import pl.tabletcad.app.CadWorkspaceView;
import pl.tabletcad.app.CadMesh;
import pl.tabletcad.app.SketchTransactions;
import pl.tabletcad.app.model.CadProject;
import pl.tabletcad.app.model.SketchPrimitive;
import pl.tabletcad.app.storage.ProjectRepository;

/** Android integration test with an isolated repository; never opens user documents. */
public final class WorkspaceHistoryProbe {
    static Object field(Object view, String name) throws Exception {
        Field f = CadWorkspaceView.class.getDeclaredField(name); f.setAccessible(true); return f.get(view);
    }
    static void call(Object view, String name) throws Exception {
        Method m = CadWorkspaceView.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(view);
    }
    static Object invoke(Object view, String name, Class<?> type, Object argument) throws Exception {
        Method m=CadWorkspaceView.class.getDeclaredMethod(name,type);m.setAccessible(true);return m.invoke(view,argument);
    }
    static void set(Object view,String name,Object value)throws Exception {
        Field f=CadWorkspaceView.class.getDeclaredField(name);f.setAccessible(true);f.set(view,value);
    }
    static boolean rebuild(Object view)throws Exception {
        Method m=CadWorkspaceView.class.getDeclaredMethod("updateNativeModel");m.setAccessible(true);return (boolean)m.invoke(view);
    }
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> thread = Class.forName("android.app.ActivityThread");
        Object instance = thread.getMethod("systemMain").invoke(null);
        Context system = (Context) thread.getMethod("getSystemContext").invoke(instance);
        Context base = system.createPackageContext("pl.tabletcad.app", Context.CONTEXT_IGNORE_SECURITY);
        File root = new File(args[0], "history-" + UUID.randomUUID());
        check(root.mkdirs(), "isolated test storage created");
        Context isolated = new ContextWrapper(base) {
            @Override public File getFilesDir() { return root; }
        };
        ProjectRepository repository = new ProjectRepository(root);
        CadProject original = new CadProject(); repository.save(original);
        CadWorkspaceView view = new CadWorkspaceView(isolated, null);
        Object document = field(view, "project"), mesh = field(view, "nativeMesh");
        check(((double[])mesh).length > 0, "actual workspace renders initial OCCT solid");
        Deque<String> undo = (Deque<String>)field(view, "undoStack");
        Deque<String> redo = (Deque<String>)field(view, "redoStack");
        CadProject broken = new CadProject(); broken.importedStep = true;
        broken.importedStepSource = "missing-source.step";
        String invalid = broken.toJson().toString();
        undo.push(invalid); call(view, "undo");
        check(field(view,"project") == document && field(view,"nativeMesh") == mesh,
            "failed native undo retains exact document and rendered mesh");
        check(undo.size() == 1 && undo.peek().equals(invalid) && redo.isEmpty(),
            "failed native undo retains both history stacks");
        check(repository.loadOrCreate().id.equals(original.id), "failed undo does not overwrite autosave");
        undo.clear(); undo.push(CadProject.blank().toJson().toString()); call(view,"undo");
        check(!((CadProject)field(view,"project")).hasSolid() && ((double[])field(view,"nativeMesh")).length == 0,
            "valid undo to empty document clears solid deliberately");
        call(view,"redo");
        check(((CadProject)field(view,"project")).id.equals(original.id) && ((double[])field(view,"nativeMesh")).length > 0,
            "actual redo rebuilds original OCCT solid");
        redo.push(invalid); Object validMesh=field(view,"nativeMesh"); call(view,"redo");
        check(field(view,"nativeMesh") == validMesh && redo.peek().equals(invalid),
            "failed redo preserves solid and retry entry");
        CadProject stable=(CadProject)field(view,"project");
        Object stablePrism=field(view,"nativePrism"),stableInfo=field(view,"modelInfo");
        CadProject invalidRadius=CadProject.fromJson(stable.toJson());invalidRadius.filletRadius=1000;
        set(view,"project",invalidRadius);
        check(!rebuild(view)&&field(view,"nativeMesh")==validMesh&&field(view,"nativePrism")==stablePrism
            &&field(view,"modelInfo")==stableInfo,"failed rebuild never publishes an empty render buffer");
        CadProject missing=CadProject.fromJson(stable.toJson());missing.sketchExtruded=true;missing.featureProfileId="missing";
        missing.sketch.add(new SketchPrimitive("other","Prostokąt",0,0,5,5));set(view,"project",missing);
        check(!rebuild(view)&&field(view,"nativeMesh")==validMesh,"missing source does not fall back to unrelated profile or default box");
        set(view,"project",stable);undo.clear();redo.clear();redo.push("retry");
        byte[] before=java.nio.file.Files.readAllBytes(new File(root,"projects/autosave.tcad").toPath());
        Runnable invalidEdit=()->{try{((CadProject)field(view,"project")).filletRadius=1000;}catch(Exception e){throw new RuntimeException(e);}};
        check(!(boolean)invoke(view,"commitModelChange",Runnable.class,invalidEdit),"invalid solid edit is rejected");
        check(field(view,"project")==stable&&field(view,"nativeMesh")==validMesh&&undo.isEmpty()&&redo.peek().equals("retry"),
            "rejected edit preserves document identity mesh and redo branch");
        check(Arrays.equals(before,java.nio.file.Files.readAllBytes(new File(root,"projects/autosave.tcad").toPath())),
            "rejected geometry leaves persisted model unchanged");
        Runnable depthEdit=()->{try{((CadProject)field(view,"project")).depth+=5;}catch(Exception e){throw new RuntimeException(e);}};
        File blocker=new File(root,"projects/document-"+stable.id+".tcad.tmp");check(blocker.mkdir(),"isolated save failure injected");
        check(!(boolean)invoke(view,"commitModelChange",Runnable.class,depthEdit),"save failure rejects an otherwise valid OCCT edit");
        check(field(view,"project")==stable&&field(view,"nativeMesh")==validMesh&&undo.isEmpty()&&redo.peek().equals("retry")
            &&Arrays.equals(before,java.nio.file.Files.readAllBytes(new File(root,"projects/autosave.tcad").toPath())),
            "save failure rolls back geometry document history and autosave");
        check(blocker.delete(),"save failure fixture removed");
        check((boolean)invoke(view,"commitModelChange",Runnable.class,depthEdit),"valid isolated edit commits");
        CadProject changed=(CadProject)field(view,"project");
        check(changed!=stable&&changed.depth==stable.depth+5&&stable.depth==original.depth&&undo.size()==1&&redo.isEmpty(),
            "success edits a copy and creates exactly one undo entry");
        check(repository.loadOrCreate().depth==changed.depth,"committed geometry parameters survive reload");
        call(view,"undo");check(((CadProject)field(view,"project")).depth==stable.depth,"undo reverts committed edit");
        call(view,"redo");check(((CadProject)field(view,"project")).depth==changed.depth,"redo restores committed edit");
        call(view,"beginSketchWork");
        CadProject draft=(CadProject)field(view,"project");Object baselineMesh=field(view,"nativeMesh");
        int historyBefore=undo.size();
        File sessionBlocker=new File(root,"projects/document-"+draft.id+".tcad.tmp");
        check(sessionBlocker.mkdir(),"session save failure injected");call(view,"cancelSketchWork");
        check(field(view,"project")==draft&&SketchTransactions.active(draft)&&undo.size()==historyBefore
            &&field(view,"nativeMesh")==baselineMesh,"failed sketch cancellation preserves draft geometry and history");
        check(sessionBlocker.delete(),"session save failure removed");call(view,"cancelSketchWork");
        check(!SketchTransactions.active((CadProject)field(view,"project"))&&undo.size()==historyBefore+1,
            "successful sketch cancellation creates one reversible transaction");
        call(view,"undo");check(SketchTransactions.active((CadProject)field(view,"project")),"undo cancellation restores sketch session");
        call(view,"finishSketchWork");
        check(!SketchTransactions.active((CadProject)field(view,"project"))
            &&((double[])field(view,"nativeMesh")).length>0,"finishing restored sketch session commits valid solid");
        check(!CadMesh.isValid(null,false)&&!CadMesh.isValid(new double[0],false)
            &&CadMesh.isValid(new double[0],true)&&!CadMesh.isValid(new double[12],false),"mesh validator rejects absent or incomplete solid buffers");
        double[] triangle={0,0,0,1,0,0,0,1,0,0,0,1,1};
        check(CadMesh.isValid(triangle,false),"finite triangle buffer accepted");
        triangle[0]=Double.NaN;check(!CadMesh.isValid(triangle,false),"nonfinite coordinate rejected");
        triangle[0]=0;triangle[12]=1.5;check(!CadMesh.isValid(triangle,false),"fractional face reference rejected");
        System.out.println("ALL WORKSPACE HISTORY TESTS PASSED");
        System.exit(0);
    }
}
