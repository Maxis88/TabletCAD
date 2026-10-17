import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import pl.tabletcad.app.CadWorkspaceView;
import pl.tabletcad.app.model.CadProject;
import pl.tabletcad.app.storage.ProjectRepository;

/** Android integration test with an isolated repository; never opens user documents. */
public final class WorkspaceHistoryProbe {
    static Object field(Object view, String name) throws Exception {
        Field f = CadWorkspaceView.class.getDeclaredField(name); f.setAccessible(true); return f.get(view);
    }
    static void call(Object view, String name) throws Exception {
        Method m = CadWorkspaceView.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(view);
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
        System.out.println("ALL WORKSPACE HISTORY TESTS PASSED");
        System.exit(0);
    }
}
