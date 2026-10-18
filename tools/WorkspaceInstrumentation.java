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
        int executed=0;
        try {
            for(String name:new String[]{"VisibilityProbe","KernelDiagnosticsProbe","SketchOffsetProbe","SketchExtendProbe","ExtrudeToFaceProbe","SketchPatternProbe","LibraryThumbnailProbe","ExtrusionPlanProbe","ThreadProbe","BodyGraphProbe","RelationProbe","ConstructionAndSnapProbe","BooleanModeProbe","SplineProbe","SketchFilletProbe","DirectionProbe","SymmetricProbe","SketchWorkflowProbe","SketchTransactionProbe","TimelineProbe","ToolbarProbe","SketchDesignKernelProbe","SolidPatternKernelProbe"}) {
                if(onlyProbe!=null&&!onlyProbe.equals(name))continue;executed++;
                File scratch=new File(getTargetContext().getCacheDir(),"workflow-"+java.util.UUID.randomUUID()+".step");
                try {Class.forName(name).getMethod("main",String[].class).invoke(null,(Object)new String[]{scratch.getAbsolutePath()});}
                finally {scratch.delete();}
            }
            for(String name:new String[]{"WorkspaceUiProbe","InteractionFixProbe","ViewCameraProbe","SketchOffsetUiProbe","LibraryAuditProbe","ExtrusionManipulatorProbe","EdgeGroupUiProbe","RecoveryUiProbe","TabletFlowProbe","CadScenarioProbe","ThreadRenderProbe"}) {
                if(onlyProbe!=null&&!onlyProbe.equals(name))continue;executed++;
                Runnable execute=()-> {
                    try {
                        Class<?> probe=Class.forName(name);
                        probe.getField("applicationContext").set(null,getTargetContext());
                        if(!name.equals("WorkspaceUiProbe")&&!name.equals("InteractionFixProbe")&&!name.equals("ViewCameraProbe")&&!name.equals("SketchOffsetUiProbe")&&!name.equals("LibraryAuditProbe"))probe.getField("instrumentation").set(null,this);
                        Method run=probe.getDeclaredMethod("run",String[].class);run.setAccessible(true);
                        run.invoke(null,(Object)new String[]{getTargetContext().getCacheDir().getAbsolutePath()});
                    }catch(Throwable error){failure[0]=error instanceof InvocationTargetException?error.getCause():error;}
                };
                if(name.equals("WorkspaceUiProbe")||name.equals("InteractionFixProbe")||name.equals("ViewCameraProbe")||name.equals("SketchOffsetUiProbe")||name.equals("LibraryAuditProbe"))runOnMainSync(execute);else execute.run();
                if(failure[0]!=null)break;
            }
            if(executed==0)throw new IllegalArgumentException("Unknown probe: "+onlyProbe);
        }catch(Throwable error){failure[0]=error;}
        finally{System.setOut(previous);}
        if(failure[0]!=null)failure[0].printStackTrace(capture);
        capture.flush();Bundle results=new Bundle();results.putString("stream",bytes.toString());
        results.putString("result",failure[0]==null?"PASS":"FAIL");finish(failure[0]==null?-1:0,results);
    }
}
