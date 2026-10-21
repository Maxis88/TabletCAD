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
 * Visible window, real touches: two nested rectangles drawn with the finger, one tap between them
 * selects the frame (not either rectangle), Apply extrudes exactly the frame; Options dialog opens.
 */
public final class RegionUiProbe {
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
        File root=new File(applicationContext.getCacheDir(),"region-"+UUID.randomUUID());check(root.mkdirs(),"isolated region storage");
        CadWorkspaceView[] reference={null};
        ui(()->{java.lang.reflect.Field original=activity.getClass().getDeclaredField("workspace");original.setAccessible(true);CadWorkspaceView old=(CadWorkspaceView)original.get(activity);
            AlertDialog chooser=(AlertDialog)field(old,"projectChooser");if(chooser!=null)chooser.dismiss();
            Context context=new ContextWrapper(activity){@Override public File getFilesDir(){return root;}};
            CadProject blank=CadProject.blank();blank.name="Obszar-ramka";new ProjectRepository(root).save(blank);
            CadWorkspaceView view=new CadWorkspaceView(context,null);reference[0]=view;FrameLayout host=new FrameLayout(activity);host.addView(view,new FrameLayout.LayoutParams(-1,-1));activity.setContentView(host);
            AlertDialog second=(AlertDialog)field(view,"projectChooser");if(second!=null)second.dismiss();});
        instrumentation.waitForIdleSync();CadWorkspaceView view=reference[0];
        CadScenarioProbe.tool(view,"Szkic");ui(()->call(view,"chooseSketchPlane",new Class<?>[]{String.class},"XY"));instrumentation.waitForIdleSync();
        ui(()->{TabletFlowProbe.set(view,"zoom",2.5f);view.invalidate();});instrumentation.waitForIdleSync();
        CadScenarioProbe.tool(view,"Prostokąt");ui(()->{View panel=(View)field(view,"propertyEditor");if(panel!=null){Button b=WorkspaceUiProbe.button(panel,"Anuluj");if(b!=null)b.performClick();}});instrumentation.waitForIdleSync();
        float[] a=CadScenarioProbe.screen(view,3,3),b=CadScenarioProbe.screen(view,83,63);CadScenarioProbe.drag(a[0],a[1],b[0],b[1]);Thread.sleep(300);
        float[] c=CadScenarioProbe.screen(view,23,18),d=CadScenarioProbe.screen(view,63,48);CadScenarioProbe.drag(c[0],c[1],d[0],d[1]);Thread.sleep(300);
        List<SketchPrimitive> rects=new ArrayList<>();ui(()->{for(SketchPrimitive p:((CadProject)field(view,"project")).sketch)if(p.type.equals("Prostokąt"))rects.add(p);});
        check(rects.size()==2,"two nested rectangles drawn with the finger ("+CadScenarioProbe.state(view)+")");
        SketchPrimitive outer=Math.abs((rects.get(0).x2-rects.get(0).x1)*(rects.get(0).y2-rects.get(0).y1))>Math.abs((rects.get(1).x2-rects.get(1).x1)*(rects.get(1).y2-rects.get(1).y1))?rects.get(0):rects.get(1),inner=outer==rects.get(0)?rects.get(1):rects.get(0);
        double area=Math.abs((outer.x2-outer.x1)*(outer.y2-outer.y1))-Math.abs((inner.x2-inner.x1)*(inner.y2-inner.y1));
        CadScenarioProbe.tool(view,"Wyciągnij");
        ui(()->{View panel=(View)field(view,"propertyEditor");check(panel!=null,"extrusion panel opens");List<EditText> inputs=new ArrayList<>();WorkspaceUiProbe.editors(panel,inputs);inputs.get(0).setText("10");});
        Thread.sleep(400);ui(()->check(((Set<?>)field(view,"extrusionSelections")).isEmpty(),"nested profiles: nothing is picked automatically"));
        double gx=Math.min(outer.x1,outer.x2)+(Math.min(inner.x1,inner.x2)-Math.min(outer.x1,outer.x2))/2,gy=(Math.min(outer.y1,outer.y2)+Math.max(outer.y1,outer.y2))/2;
        float[] gap=CadScenarioProbe.screen(view,gx,gy);TabletFlowProbe.tap(gap[0],gap[1]);Thread.sleep(500);
        String expected="region:"+outer.id+"|"+inner.id;
        ui(()->{Set<?> s=(Set<?>)field(view,"extrusionSelections");check(s.size()==1&&s.contains(expected),"tap between the rectangles selects the frame: "+s);});
        TabletFlowProbe.screenshot(activity,"region-01-frame-selected");
        View[] apply={null};ui(()->apply[0]=WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Zastosuj"));
        long until=System.currentTimeMillis()+20000;boolean[] ready={false};while(System.currentTimeMillis()<until&&!ready[0]){ui(()->ready[0]=apply[0].isEnabled());Thread.sleep(100);}
        check(ready[0],"preview of the frame finishes and Apply is enabled");
        TabletFlowProbe.click(apply[0]);TabletFlowProbe.pending(view);instrumentation.waitForIdleSync();
        double v=CadScenarioProbe.volume(view);check(Math.abs(v-area*10)<1e-6,"extruded frame volume = (outer − inner) × 10 = "+area*10+" ("+v+")");
        TabletFlowProbe.screenshot(activity,"region-02-frame-extruded");
        // Saved document reopens with the region operation.
        CadProject saved=(CadProject)field(view,"project");CadProject back=CadProject.fromJson(new org.json.JSONObject(saved.toJson().toString()));
        check(expected.equals(back.featureBaseProfileId),"region reference saved with the document");
        // Options dialog.
        ui(()->call(view,"showOptions",new Class<?>[0]));instrumentation.waitForIdleSync();Thread.sleep(300);
        ui(()->{View panel=(View)field(view,"propertyEditor");check(panel!=null,"Opcje open as a panel");check(find(panel,"Pokazuj gwinty modelowane")!=null,"thread display option present");Button cancel=WorkspaceUiProbe.button(panel,"Anuluj");check(cancel!=null,"options can be cancelled");cancel.performClick();});
        TabletFlowProbe.screenshot(activity,"region-03-after-options");
        System.out.println("PASS RegionUiProbe");
    }
    static CheckBox find(View v,String text){if(v instanceof CheckBox&&((CheckBox)v).getText().toString().equals(text))return (CheckBox)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){CheckBox f=find(((ViewGroup)v).getChildAt(i),text);if(f!=null)return f;}return null;}
}
