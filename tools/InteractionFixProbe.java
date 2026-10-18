import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.*;

/** Runs on Android's UI thread with isolated files; verifies actual dimension tap dispatch. */
public final class InteractionFixProbe {
    public static Context applicationContext;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    static Object field(Object o,String name)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String name,Object value)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception {Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    static EditText input(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++){EditText found=input(((android.view.ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    static Object dimension(CadWorkspaceView v,String kind)throws Exception {for(Object hit:(List<?>)field(v,"dimensionHits"))if(field(hit,"kind").equals(kind))return hit;throw new AssertionError("missing dimension "+kind);}
    static void edit(CadWorkspaceView view,String kind,String value)throws Exception {
        RectF bounds=(RectF)field(dimension(view,kind),"bounds");
        WorkspaceUiProbe.tap(view,bounds.centerX(),bounds.centerY());check(field(view,"propertyEditor")==null,"single dimension tap selects without editing");
        WorkspaceUiProbe.tap(view,bounds.centerX(),bounds.centerY());View panel=(View)field(view,"propertyEditor");check(panel!=null,"dimension double tap opens numeric editor "+kind);
        input(panel).setText(value);Button apply=WorkspaceUiProbe.button(panel,"Zastosuj");check(apply!=null,"numeric dimension has apply action");apply.performClick();
    }
    static void run(String[] args)throws Exception {
        File root=new File(args[0],"interaction-"+UUID.randomUUID());check(root.mkdirs(),"isolated interaction fixture");
        Context context=new ContextWrapper(applicationContext){@Override public File getFilesDir(){return root;}};
        CadProject fixture=CadProject.blank();fixture.sketch.add(new SketchPrimitive("line","Linia",-20,-10,10,10));new ProjectRepository(root).save(fixture);
        CadWorkspaceView view=new CadWorkspaceView(context,null);FrameLayout host=new FrameLayout(context);host.addView(view,new FrameLayout.LayoutParams(-1,-1));host.measure(View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1500,View.MeasureSpec.EXACTLY));host.layout(0,0,2400,1500);
        call(view,"enterSketchEditor",new Class<?>[0]);Bitmap bitmap=Bitmap.createBitmap(2400,1500,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);view.draw(canvas);
        edit(view,"length","50");CadProject edited=(CadProject)field(view,"project");check(Math.abs(SketchDimensions.length(edited.sketch.get(0))-50)<1e-6,"double-tapped length changes actual sketch geometry: "+SketchDimensions.length(edited.sketch.get(0))+" / "+field(view,"operationMessage"));
        view.draw(canvas);edit(view,"angle","45");edited=(CadProject)field(view,"project");check(Math.abs(SketchDimensions.angle(edited.sketch.get(0))-45)<1e-6,"double-tapped angle changes actual sketch geometry");
        check(Math.abs(SketchDimensions.length(edited.sketch.get(0))-50)<1e-6,"angle edit retains line length");
        float old=(float)field(view,"zoom");call(view,"applyZoom",new Class<?>[]{float.class,float.class,float.class},20f,1100f,700f);check((float)field(view,"zoom")>3.5f,"zoom exceeds old upper limit");
        call(view,"applyZoom",new Class<?>[]{float.class,float.class,float.class},.001f,1100f,700f);check((float)field(view,"zoom")<.45f,"zoom extends below old lower limit");set(view,"zoom",old);
        call(view,"closePropertyEditor",new Class<?>[0]);String oldId=edited.id;call(view,"showNewProject",new Class<?>[0]);CadProject blank=(CadProject)field(view,"project");
        check(!blank.id.equals(oldId)&&!blank.hasSolid()&&blank.sketch.isEmpty(),"new project immediately creates a distinct blank document");
        check(((double[])field(view,"nativeMesh")).length==0,"new project removes old rendered geometry");
        check(!new ProjectRepository(root).loadProject(oldId).sketch.isEmpty(),"new project preserves previous document");
        check(new ProjectRepository(root).loadOrCreate().id.equals(blank.id),"new blank document is active after reload");bitmap.recycle();
        System.out.println("ALL INTERACTION FIX TESTS PASSED");
    }
}
