import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.*;

/**
 * Visible window, real touches: a rectangle and a line across it drawn with the finger; a tap on the
 * left part selects that area (kernel split of the sketch), Apply extrudes exactly that area.
 */
public final class AreaUiProbe {
    public static Context applicationContext;
    public static Instrumentation instrumentation;
    static void ui(TabletFlowProbe.Step step)throws Exception{TabletFlowProbe.ui(step);}
    static void check(boolean ok,String text){TabletFlowProbe.check(ok,text);}
    static Object field(Object o,String name)throws Exception{return TabletFlowProbe.field(o,name);}
    static Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception{return TabletFlowProbe.call(o,name,types,args);}
    static void run(String[] args)throws Exception {
        TabletFlowProbe.instrumentation=instrumentation;TabletFlowProbe.applicationContext=applicationContext;
        CadScenarioProbe.instrumentation=instrumentation;CadScenarioProbe.applicationContext=applicationContext;
        Intent launch=new Intent().setClassName(applicationContext,"pl.tabletcad.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity=instrumentation.startActivitySync(launch);instrumentation.waitForIdleSync();
        File root=new File(applicationContext.getCacheDir(),"area-"+UUID.randomUUID());check(root.mkdirs(),"isolated region storage");
        CadWorkspaceView[] reference={null};
        ui(()->{java.lang.reflect.Field original=activity.getClass().getDeclaredField("workspace");original.setAccessible(true);CadWorkspaceView old=(CadWorkspaceView)original.get(activity);
            AlertDialog chooser=(AlertDialog)field(old,"projectChooser");if(chooser!=null)chooser.dismiss();
            Context context=new ContextWrapper(activity){@Override public File getFilesDir(){return root;}};
            CadProject blank=CadProject.blank();blank.name="Obszar-linia";new ProjectRepository(root).save(blank);
            CadWorkspaceView view=new CadWorkspaceView(context,null);reference[0]=view;FrameLayout host=new FrameLayout(activity);host.addView(view,new FrameLayout.LayoutParams(-1,-1));activity.setContentView(host);
            AlertDialog second=(AlertDialog)field(view,"projectChooser");if(second!=null)second.dismiss();});
        instrumentation.waitForIdleSync();CadWorkspaceView view=reference[0];
        CadScenarioProbe.tool(view,"Szkic");ui(()->call(view,"chooseSketchPlane",new Class<?>[]{String.class},"XY"));instrumentation.waitForIdleSync();
        ui(()->{TabletFlowProbe.set(view,"zoom",2.5f);view.invalidate();});instrumentation.waitForIdleSync();
        CadScenarioProbe.tool(view,"Prostokąt");ui(()->{View panel=(View)field(view,"propertyEditor");if(panel!=null){Button b=WorkspaceUiProbe.button(panel,"Anuluj");if(b!=null)b.performClick();}});instrumentation.waitForIdleSync();
        float[] a=CadScenarioProbe.screen(view,3,3),b=CadScenarioProbe.screen(view,83,63);CadScenarioProbe.drag(a[0],a[1],b[0],b[1]);Thread.sleep(300);
        CadScenarioProbe.tool(view,"Linia");ui(()->{View panel=(View)field(view,"propertyEditor");if(panel!=null){Button x=WorkspaceUiProbe.button(panel,"Anuluj");if(x!=null)x.performClick();}});instrumentation.waitForIdleSync();
        float[] c=CadScenarioProbe.screen(view,33,-12),d=CadScenarioProbe.screen(view,33,78);CadScenarioProbe.drag(c[0],c[1],d[0],d[1]);Thread.sleep(300);
        SketchPrimitive[] rect={null},line={null};ui(()->{for(SketchPrimitive p:((CadProject)field(view,"project")).sketch){if(p.type.equals("Prostokąt"))rect[0]=p;if(p.type.equals("Linia"))line[0]=p;}});
        check(rect[0]!=null&&line[0]!=null,"rectangle and a crossing line drawn with the finger ("+CadScenarioProbe.state(view)+")");
        double x0=Math.min(rect[0].x1,rect[0].x2),x1=Math.max(rect[0].x1,rect[0].x2),y0=Math.min(rect[0].y1,rect[0].y2),y1=Math.max(rect[0].y1,rect[0].y2);
        check(Math.abs(line[0].x1-line[0].x2)<1e-6&&line[0].x1>x0&&line[0].x1<x1,"line is vertical and crosses the rectangle");
        double area=(line[0].x1-x0)*(y1-y0);
        CadScenarioProbe.tool(view,"Wyciągnij");
        ui(()->{View panel=(View)field(view,"propertyEditor");check(panel!=null,"extrusion panel opens");List<EditText> inputs=new ArrayList<>();WorkspaceUiProbe.editors(panel,inputs);inputs.get(0).setText("10");});
        Thread.sleep(400);
        float[] left=CadScenarioProbe.screen(view,(x0+line[0].x1)/2,(y0+y1)/2);TabletFlowProbe.tap(left[0],left[1]);Thread.sleep(600);
        ui(()->{Set<?> s=(Set<?>)field(view,"extrusionSelections");check(s.size()==1&&s.iterator().next().toString().startsWith("seed:"),"tap on the left part selects that area: "+s);});
        TabletFlowProbe.screenshot(activity,"area-01-left-part");
        View[] apply={null};ui(()->apply[0]=WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Zastosuj"));
        long until=System.currentTimeMillis()+20000;boolean[] ready={false};while(System.currentTimeMillis()<until&&!ready[0]){ui(()->ready[0]=apply[0].isEnabled());Thread.sleep(100);}
        check(ready[0],"preview of the area finishes and Apply is enabled");
        TabletFlowProbe.click(apply[0]);TabletFlowProbe.pending(view);instrumentation.waitForIdleSync();
        double v=CadScenarioProbe.volume(view);check(Math.abs(v-area*10)<1e-6,"extruded area volume = "+area*10+" ("+v+")");
        TabletFlowProbe.screenshot(activity,"area-02-extruded");
        CadProject saved=(CadProject)field(view,"project");check(saved.featureBaseProfileId!=null&&saved.featureBaseProfileId.startsWith("area:"),"area reference saved with the document");
        System.out.println("PASS AreaUiProbe");
    }
}
