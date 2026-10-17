package pl.tabletcad.test;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.*;

/** Test APK only. Production application has no test intents or storage overrides. */
public final class WorkspaceInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();PrintStream capture=new PrintStream(bytes);
        PrintStream previous=System.out;final Throwable[] failure={null};System.setOut(capture);
        try {
            for(String name:new String[]{"WorkspaceUiProbe","ExtrusionManipulatorProbe"}) {
                Runnable execute=()-> {
                    try {
                        Class<?> probe=Class.forName(name);
                        probe.getField("applicationContext").set(null,getTargetContext());
                        if(name.equals("ExtrusionManipulatorProbe"))probe.getField("instrumentation").set(null,this);
                        Method run=probe.getDeclaredMethod("run",String[].class);run.setAccessible(true);
                        run.invoke(null,(Object)new String[]{getTargetContext().getCacheDir().getAbsolutePath()});
                    }catch(Throwable error){failure[0]=error instanceof InvocationTargetException?error.getCause():error;}
                };
                if(name.equals("WorkspaceUiProbe"))runOnMainSync(execute);else execute.run();
                if(failure[0]!=null)break;
            }
        }catch(Throwable error){failure[0]=error;}
        finally{System.setOut(previous);}
        if(failure[0]!=null)failure[0].printStackTrace(capture);
        capture.flush();Bundle results=new Bundle();results.putString("stream",bytes.toString());
        results.putString("result",failure[0]==null?"PASS":"FAIL");finish(failure[0]==null?-1:0,results);
    }
}
