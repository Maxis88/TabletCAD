package pl.tabletcad.test;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.*;

/** Test APK only. Production application has no test intents or storage overrides. */
public final class WorkspaceInstrumentation extends Instrumentation {
    private String onlyProbe;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments);onlyProbe=arguments==null?null:arguments.getString("probe"); start(); }
    @Override public void onStart() {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();PrintStream capture=new PrintStream(bytes);
        PrintStream previous=System.out;final Throwable[] failure={null};System.setOut(capture);
        int executed=0;final java.util.List<String> failed=new java.util.ArrayList<>();
        try {
            for(String name:new String[]{"VisibilityProbe","KernelDiagnosticsProbe","SketchOffsetProbe","SketchExtendProbe","ExtrudeToFaceProbe","SketchPatternProbe","LibraryThumbnailProbe","ExtrusionPlanProbe","ThreadProbe","BodyGraphProbe","RelationProbe","ConstructionAndSnapProbe","BooleanModeProbe","SplineProbe","SketchFilletProbe","DirectionProbe","SymmetricProbe","SketchWorkflowProbe","SketchTransactionProbe","TimelineProbe","ToolbarProbe","SketchDesignKernelProbe","SolidPatternKernelProbe","CadToolsKernelProbe","ConstructionKernelProbe","HistoryKernelProbe","AnalysisKernelProbe","PrintKernelProbe","AssemblyKernelProbe","FaceOffsetKernelProbe","MateKernelProbe","AcceptanceAKernelProbe","SourceSketchKernelProbe","BodyPropertiesProbe","EdgeChamferProbe","SketchEditingProbe","EdgeRepairKernelProbe","SplineKernelProbe","SurfaceSplitKernelProbe","AutoExtrusionKernelProbe","PreviewSpeedProbe","ThreadVariantsProbe","TextKernelProbe","AndroidTextProbe","EllipseArcKernelProbe","DeviceDocumentsProbe","ThreadPitchProbe","ThreadDiagProbe","RegionKernelProbe","SketchAreaKernelProbe"}) {
                if(onlyProbe!=null&&!onlyProbe.equals(name))continue;executed++;pl.tabletcad.app.BodyGraph.displayThreads=true;android.util.Log.i("TabletCADProbe","START "+name);
                File scratch=new File(getTargetContext().getCacheDir(),"workflow-"+java.util.UUID.randomUUID()+".step");
                // Every probe runs; failures are collected so one stale check does not hide the rest.
                try {
                    // Probes whose main() ends the process with System.exit(1) on failure are run through run() so the failure is reported.
                    Class<?> k=Class.forName(name);Method direct=null;boolean noArgs=false;
                    try{direct=k.getDeclaredMethod("run",String[].class);}catch(NoSuchMethodException none){try{direct=k.getDeclaredMethod("run");noArgs=true;}catch(NoSuchMethodException none2){direct=null;}}
                    if(direct!=null&&Modifier.isStatic(direct.getModifiers())){direct.setAccessible(true);if(noArgs)direct.invoke(null);else direct.invoke(null,(Object)new String[]{scratch.getAbsolutePath()});}
                    else k.getMethod("main",String[].class).invoke(null,(Object)new String[]{scratch.getAbsolutePath()});
                    System.out.println("PROBE PASS "+name);}
                catch(Throwable error){Throwable cause=error instanceof InvocationTargetException?error.getCause():error;failed.add(name);System.out.println("PROBE FAIL "+name+": "+cause);cause.printStackTrace(System.out);if(failure[0]==null)failure[0]=cause;}
                finally {scratch.delete();}
            }
            for(String name:new String[]{"WorkspaceUiProbe","InteractionFixProbe","ViewCameraProbe","SketchOffsetUiProbe","LibraryAuditProbe","ExtrusionManipulatorProbe","EdgeGroupUiProbe","RecoveryUiProbe","TabletFlowProbe","CadScenarioProbe","ThreadRenderProbe","ContextPanelsUiProbe","CadToolsUiProbe","SketchMoveUiProbe","RegionUiProbe","AreaUiProbe"}) {
                if(onlyProbe!=null&&!onlyProbe.equals(name))continue;executed++;pl.tabletcad.app.BodyGraph.displayThreads=true;android.util.Log.i("TabletCADProbe","START "+name);
                final Throwable[] err={null};
                Runnable execute=()-> {
                    try {
                        Class<?> probe=Class.forName(name);
                        probe.getField("applicationContext").set(null,getTargetContext());
                        if(!name.equals("WorkspaceUiProbe")&&!name.equals("InteractionFixProbe")&&!name.equals("ViewCameraProbe")&&!name.equals("SketchOffsetUiProbe")&&!name.equals("LibraryAuditProbe")&&!name.equals("CadToolsUiProbe"))probe.getField("instrumentation").set(null,this);
                        Method run=probe.getDeclaredMethod("run",String[].class);run.setAccessible(true);
                        run.invoke(null,(Object)new String[]{getTargetContext().getCacheDir().getAbsolutePath()});
                    }catch(Throwable error){err[0]=error instanceof InvocationTargetException?error.getCause():error;}
                };
                if(name.equals("WorkspaceUiProbe")||name.equals("InteractionFixProbe")||name.equals("ViewCameraProbe")||name.equals("SketchOffsetUiProbe")||name.equals("LibraryAuditProbe")||name.equals("CadToolsUiProbe"))runOnMainSync(execute);else execute.run();
                if(err[0]!=null){failed.add(name);System.out.println("PROBE FAIL "+name+": "+err[0]);err[0].printStackTrace(System.out);if(failure[0]==null)failure[0]=err[0];}
                else System.out.println("PROBE PASS "+name);
            }
            if(executed==0)throw new IllegalArgumentException("Unknown probe: "+onlyProbe);
        }catch(Throwable error){failure[0]=error;}
        finally{System.setOut(previous);}
        if(!failed.isEmpty())capture.println("FAILED PROBES: "+failed);
        capture.flush();Bundle results=new Bundle();results.putString("stream",bytes.toString());
        results.putString("result",failure[0]==null?"PASS":"FAIL");finish(failure[0]==null?-1:0,results);
    }
}
