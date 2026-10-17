import android.content.*;
import android.os.Looper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.ProjectRepository;

/** Exercises the public import paths with real OCCT, isolated files and injected I/O failures. */
public final class WorkspaceImportProbe {
    static Object field(Object view,String name)throws Exception{return WorkspaceHistoryProbe.field(view,name);}
    static void call(Object view,String name)throws Exception{WorkspaceHistoryProbe.call(view,name);}
    static Object invoke(Object view,String name,Class<?> type,Object argument)throws Exception{return WorkspaceHistoryProbe.invoke(view,name,type,argument);}
    static void check(boolean ok,String label){WorkspaceHistoryProbe.check(ok,label);}
    interface Attempt { void run() throws Exception; }
    static final class State {
        final Object document,mesh;final String json;
        final List<String> undo,redo,assets;final byte[] saved;
        State(CadWorkspaceView view,File root)throws Exception {
            document=field(view,"project");mesh=field(view,"nativeMesh");json=((CadProject)document).toJson().toString();
            undo=new ArrayList<>((Deque<String>)field(view,"undoStack"));redo=new ArrayList<>((Deque<String>)field(view,"redoStack"));
            saved=Files.readAllBytes(new File(root,"projects/autosave.tcad").toPath());assets=assets(root);
        }
        void unchanged(CadWorkspaceView view,File root,String label)throws Exception {
            check(field(view,"project")==document&&field(view,"nativeMesh")==mesh
                &&json.equals(((CadProject)document).toJson().toString())
                &&undo.equals(new ArrayList<>((Deque<String>)field(view,"undoStack")))
                &&redo.equals(new ArrayList<>((Deque<String>)field(view,"redoStack")))
                &&Arrays.equals(saved,Files.readAllBytes(new File(root,"projects/autosave.tcad").toPath()))
                &&assets.equals(assets(root)),label);
        }
    }
    static List<String> assets(File root) {
        String[] names=new File(root,"projects").list((dir,name)->name.startsWith("source-")||name.startsWith("mesh-"));
        Arrays.sort(names);return Arrays.asList(names);
    }
    static void rejected(Attempt action,String label)throws Exception {
        boolean rejected=false;try{action.run();}catch(IOException expected){rejected=true;}
        check(rejected,label);
    }
    static ByteArrayInputStream dxf() {
        return new ByteArrayInputStream(("0\nSECTION\n2\nENTITIES\n0\nCIRCLE\n10\n4\n20\n5\n40\n2\n0\nENDSEC\n0\nEOF\n").getBytes(StandardCharsets.US_ASCII));
    }
    public static void main(String[] args)throws Exception {
        try{run(args);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}
        System.exit(0);
    }
    @SuppressWarnings("unchecked")
    static void run(String[] args)throws Exception {
        Looper.prepareMainLooper();Class<?> thread=Class.forName("android.app.ActivityThread");
        Object instance=thread.getMethod("systemMain").invoke(null);
        Context system=(Context)thread.getMethod("getSystemContext").invoke(instance);
        Context base=system.createPackageContext("pl.tabletcad.app",Context.CONTEXT_IGNORE_SECURITY);
        File root=new File(args[0],"imports-"+UUID.randomUUID());check(root.mkdirs(),"isolated import fixture");
        Context isolated=new ContextWrapper(base){@Override public File getFilesDir(){return root;}};
        ProjectRepository repository=new ProjectRepository(root);CadProject initial=new CadProject();repository.save(initial);
        CadWorkspaceView view=new CadWorkspaceView(isolated,null);
        File step=new File(root,"input.step"),stl=new File(root,"input.stl");
        check(view.exportStep(step.getAbsolutePath()),"real STEP input created");
        try(OutputStream output=new FileOutputStream(stl)){view.writeBinaryStl(output);}
        Deque<String> undo=(Deque<String>)field(view,"undoStack"),redo=(Deque<String>)field(view,"redoStack");redo.push("retained branch");
        File invalid=new File(root,"invalid.step");Files.write(invalid.toPath(),"invalid".getBytes(StandardCharsets.US_ASCII));
        State baseline=new State(view,root);
        rejected(()->view.importStep(invalid),"invalid STEP rejected");baseline.unchanged(view,root,"invalid STEP preserves model history autosave and assets");
        rejected(()->view.importStl(invalid),"invalid STL rejected");baseline.unchanged(view,root,"invalid STL preserves model history autosave and assets");
        File blocker=new File(root,"projects/document-"+initial.id+".tcad.tmp");check(blocker.mkdir(),"import save failure injected");
        rejected(()->view.importStep(step),"STEP save failure reported");baseline.unchanged(view,root,"failed STEP leaves no retained source or partial document");
        rejected(()->view.importStl(stl),"STL save failure reported");baseline.unchanged(view,root,"failed STL leaves no retained mesh or partial document");
        rejected(()->view.importDxf(dxf()),"DXF save failure reported");baseline.unchanged(view,root,"failed DXF preserves prior sketch history and model");
        check(blocker.delete(),"import save failure removed");
        call(view,"beginSketchWork");CadProject draft=(CadProject)field(view,"project");String draftJson=draft.toJson().toString();
        State editing=new State(view,root);rejected(()->view.importDxf(dxf()),"DXF requires finishing current sketch");editing.unchanged(view,root,"DXF never silently discards draft");
        int historySize=undo.size();view.importStep(step);CadProject imported=(CadProject)field(view,"project");
        check(imported.importedStep&&!SketchTransactions.active(imported)&&imported.polylineDraft.isEmpty()
            &&imported.sketchLineage.isEmpty()&&undo.size()==historySize+1&&redo.isEmpty(),"STEP replacement clears obsolete session in one transaction");
        String source=imported.importedStepSource;File sourceFile=new File(root,"projects/"+source);
        check(sourceFile.isFile()&&repository.loadOrCreate().importedStepSource.equals(source),"STEP retained under immutable unique source name");
        call(view,"undo");check(((CadProject)field(view,"project")).toJson().toString().equals(draftJson),"undo STEP restores exact unfinished sketch session");
        call(view,"redo");check(((CadProject)field(view,"project")).importedStepSource.equals(source),"redo STEP restores same retained source");
        view.importStl(stl);CadProject meshDoc=(CadProject)field(view,"project");String meshName=meshDoc.importedMeshSource;
        check(meshName!=null&&!meshDoc.importedStep&&!SketchTransactions.active(meshDoc)&&sourceFile.isFile(),"STL transaction retains earlier STEP asset for undo");
        call(view,"undo");check(((CadProject)field(view,"project")).importedStepSource.equals(source),"undo STL restores B-Rep source");
        call(view,"redo");check(((CadProject)field(view,"project")).importedMeshSource.equals(meshName),"redo STL restores retained mesh");
        call(view,"undo");view.importDxf(dxf());CadProject drawing=(CadProject)field(view,"project");
        check(drawing.importedStep&&drawing.sketch.size()==1&&SketchTransactions.active(drawing)
            &&drawing.sketch.get(0).type.equals("Okrąg"),"DXF opens independent editable sketch while retaining B-Rep");
        call(view,"undo");check(((CadProject)field(view,"project")).sketch.isEmpty(),"undo DXF restores previous sketch");
        call(view,"redo");check(((CadProject)field(view,"project")).sketch.size()==1,"redo DXF restores imported sketch session");
        call(view,"cancelSketchWork");
        CadProject previous=(CadProject)field(view,"project");Object previousMesh=field(view,"nativeMesh");
        CadProject next=new CadProject();File nextBlock=new File(root,"projects/document-"+next.id+".tcad.tmp");check(nextBlock.mkdir(),"project-switch save failure injected");
        File held=new File(root,"projects/held.step");Files.move(sourceFile.toPath(),held.toPath());
        State beforeSwitch=new State(view,root);invoke(view,"activateProject",CadProject.class,next);
        beforeSwitch.unchanged(view,root,"failed project switch restores cached geometry without rereading missing source");
        check(field(view,"project")==previous&&field(view,"nativeMesh")==previousMesh,"project identity retained after failed switch");
        Files.move(held.toPath(),sourceFile.toPath());check(nextBlock.delete(),"project-switch failure fixture removed");
        System.out.println("ALL WORKSPACE IMPORT TESTS PASSED");
    }
}
