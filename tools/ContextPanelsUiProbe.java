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

/** Visible isolated source-edit, stable-camera and pinned-panel regression. */
public final class ContextPanelsUiProbe {
    public static Context applicationContext;
    public static Instrumentation instrumentation;
    interface Step {void run()throws Exception;}
    static void ui(Step step)throws Exception{TabletFlowProbe.ui(step::run);}
    static Object field(Object v,String name)throws Exception{return TabletFlowProbe.field(v,name);}
    static Object call(Object v,String name,Class<?>[] types,Object... args)throws Exception{return TabletFlowProbe.call(v,name,types,args);}
    static void check(boolean ok,String label){TabletFlowProbe.check(ok,label);}
    static void waitPreview(CadWorkspaceView view,double[] previous)throws Exception {
        long until=System.currentTimeMillis()+30000;
        while(System.currentTimeMillis()<until){boolean[] ready={false};ui(()->{double[] mesh=(double[])field(view,"featurePreview");ready[0]=mesh!=null&&mesh.length>0&&mesh!=previous;});if(ready[0])return;Thread.sleep(40);}
        throw new AssertionError("preview timeout");
    }
    static boolean text(View v,String part){if(v instanceof TextView&&((TextView)v).getText().toString().contains(part))return true;if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(text(g.getChildAt(i),part))return true;}return false;}
    static void ready(CadWorkspaceView view,String recognized)throws Exception {
        long until=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<until){boolean[] done={false};ui(()->{Button b=(Button)field(view,"panelConfirm");View panel=(View)field(view,"propertyEditor");done[0]=b!=null&&b.isEnabled()&&panel!=null&&text(panel,recognized);});if(done[0])return;Thread.sleep(50);}throw new AssertionError("automatic preview not ready: "+recognized);
    }
    static CadProject faceProfile(){CadProject p=new CadProject();p.width=40;p.height=30;p.depth=10;p.sketch.clear();p.sketchPlane="Ściana 1";p.sketchOriginZ=10;p.sketch.add(new SketchPrimitive("face-profile","Prostokąt",10,8,20,18));return p;}
    static void menuTap(String label)throws Exception {
        instrumentation.waitForIdleSync();Thread.sleep(120);
        android.app.UiAutomation automation=instrumentation.getUiAutomation();android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);Thread.sleep(150);
        Rect bounds=new Rect();boolean found=false;
        for(android.view.accessibility.AccessibilityWindowInfo window:automation.getWindows()) {
            android.view.accessibility.AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
            for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))if(label.contentEquals(node.getText())){node.getBoundsInScreen(bounds);found=true;break;}
            if(found)break;
        }
        check(found&&!bounds.isEmpty(),"native menu exposes "+label);TabletFlowProbe.tap(bounds.centerX(),bounds.centerY());
    }
    static void run(String[] args)throws Exception {
        TabletFlowProbe.instrumentation=instrumentation;TabletFlowProbe.applicationContext=applicationContext;
        Activity activity=instrumentation.startActivitySync(new Intent().setClassName(applicationContext,"pl.tabletcad.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.waitForIdleSync();File root=new File(applicationContext.getCacheDir(),"contexts-"+UUID.randomUUID());check(root.mkdirs(),"isolated modern panel fixture");
        CadWorkspaceView[] ref={null};
        ui(()->{
            java.lang.reflect.Field original=activity.getClass().getDeclaredField("workspace");original.setAccessible(true);CadWorkspaceView old=(CadWorkspaceView)original.get(activity);
            AlertDialog chooser=(AlertDialog)field(old,"projectChooser");if(chooser!=null)chooser.dismiss();
            Context context=new ContextWrapper(activity){@Override public File getFilesDir(){return root;}};
            CadProject p=SourceSketchKernelProbe.source();SourceSketchKernelProbe.add(p,BodyTransform.move(30,4,0),1,1,"");SourceSketchKernelProbe.add(p,BodyTransform.rotate(new double[3],new double[]{0,1,0},35),1,1,"");SourceSketchKernelProbe.add(p,BodyTransform.scale(new double[3],1.5),1,1,"");new ProjectRepository(root).save(p);
            CadWorkspaceView view=new CadWorkspaceView(context,null);ref[0]=view;FrameLayout host=new FrameLayout(activity);host.addView(view,new FrameLayout.LayoutParams(-1,-1));activity.setContentView(host);
        });instrumentation.waitForIdleSync();CadWorkspaceView view=ref[0];
        ui(()->call(view,"activateTopTool",new Class<?>[]{String.class},"Dopasuj"));instrumentation.waitForIdleSync();
        final float[] zoom={0},pan={0};final double[] distance={0};
        ui(()->{
            zoom[0]=(float)field(view,"zoom");pan[0]=(float)field(view,"panX");distance[0]=(double)field(view,"cameraDistance");
            CadProject p=(CadProject)field(view,"project");ModelTree.Row row=null;for(ModelTree.Row r:ModelTree.rows(p,Collections.emptySet()))if(r.sketch()&&r.body==1)row=r;
            check(row!=null,"transformed body has a source sketch row");call(view,"editTreeSketch",new Class<?>[]{ModelTree.Row.class},row);
            double[] expected=SketchPlacement.point(SketchPlacement.of(p,"source",1),24,12,0),actual=(double[])call(view,"sketchToWorld",new Class<?>[]{double.class,double.class},24.0,12.0);
            check(Math.abs(expected[0]-actual[0])+Math.abs(expected[1]-actual[1])+Math.abs(expected[2]-actual[2])<1e-7,"editing source uses moved/rotated/scaled body frame");
            PointF screen=(PointF)call(view,"projectSketchPoint",new Class<?>[]{double.class,double.class},12.0,6.0);
            double[] back=(double[])call(view,"screenToSketch",new Class<?>[]{float.class,float.class},screen.x,screen.y);
            check(Math.abs(back[0]-12)+Math.abs(back[1]-6)<.001,"touch projection inverts transformed sketch placement");
            check((float)field(view,"zoom")==zoom[0]&&(float)field(view,"panX")==pan[0],"opening source sketch does not zoom out or pan");
        });instrumentation.waitForIdleSync();Thread.sleep(180);
        ui(()->{check(field(view,"contextEditor")==null&&!((String)field(view,"stripTitle")).isEmpty(),"rc2: sketch context is in the options strip, no right panel");});
        TabletFlowProbe.screenshot(activity,"084-01-source-follow");
        ui(()->call(view,"cancelSketchWork",new Class<?>[0]));instrumentation.waitForIdleSync();
        ui(()->{
            call(view,"showDepthEditor",new Class<?>[]{boolean.class},true);
            CadProject p=(CadProject)field(view,"project");double[] expected=SketchPlacement.point(SketchPlacement.of(p,"source",1),12,6,0),origin=(double[])field(view,"extrusionOrigin");
            check(Math.abs(expected[0]-origin[0])+Math.abs(expected[1]-origin[1])+Math.abs(expected[2]-origin[2])<1e-7,"history depth handle follows transformed source sketch");
            check(Math.abs((double)field(view,"extrusionWorldScale")-1.5)<1e-7,"history depth handle includes body scale");
            double[] world=SketchPlacement.point(SketchPlacement.of(p,"source",1),12,6,4),screen=(double[])call(view,"projectWorldPoint",new Class<?>[]{double.class,double.class,double.class},world[0],world[1],world[2]);
            double value=(double)call(view,"extrusionParameter",new Class<?>[]{float.class,float.class},(float)screen[0],(float)screen[1]);check(Math.abs(value-4)<.001,"drag on scaled body maps back to source depth units");
        });
        ui(()->{call(view,"activateTopTool",new Class<?>[]{String.class},"Obróć bryłę");List<EditText> fields=new ArrayList<>();WorkspaceUiProbe.editors((View)field(view,"propertyEditor"),fields);fields.get(fields.size()-1).setText("5");});instrumentation.waitForIdleSync();waitPreview(view,null);
        View[] panel={null};Button[] confirm={null};Rect before=new Rect();
        ui(()->{
            panel[0]=(View)field(view,"propertyEditor");confirm[0]=(Button)field(view,"panelConfirm");check(confirm[0]!=null&&confirm[0].getGlobalVisibleRect(before),"parameter confirm visible without scrolling");
            ScrollView bodyScroll=(ScrollView)((LinearLayout)((ScrollView)panel[0]).getChildAt(0)).getChildAt(1);bodyScroll.scrollTo(0,10000);
        });instrumentation.waitForIdleSync();
        ui(()->{Rect after=new Rect();check(confirm[0].getGlobalVisibleRect(after)&&after.equals(before),"confirm stays fixed while form contents scroll");check(after.bottom<=((Number)call(view,"timelineTop",new Class<?>[0])).floatValue()+1,"confirm remains above history");
            check((float)field(view,"zoom")==zoom[0]&&(double)field(view,"cameraDistance")==distance[0],"operation preview preserves user zoom and perspective distance");
            CadProject draft=(CadProject)field(view,"featurePreviewDocument"),committed=(CadProject)field(view,"project");
            check(draft!=null&&draft.committedFeatures.size()==committed.committedFeatures.size()+1,"source rendering shares the current transformation preview document");
            double[] a=SketchPlacement.point(SketchPlacement.of(draft,"source",1),12,6,0),b=SketchPlacement.point(SketchPlacement.of(committed,"source",1),12,6,0);
            check(Math.abs(a[0]-b[0])+Math.abs(a[1]-b[1])>.1,"source sketch follows uncommitted rotation preview as well as committed geometry");});
        TabletFlowProbe.screenshot(activity,"084-02-pinned-actions");
        final double[][] previous={null};final String[] document={null};
        ui(()->{previous[0]=(double[])field(view,"featurePreview");document[0]=((CadProject)field(view,"project")).toJson().toString();call(view,"activateTopTool",new Class<?>[]{String.class},"Przekrój");});
        TabletFlowProbe.screenshot(activity,"084-03-native-view-menu");menuTap("Przekrój prostopadły do X");waitPreview(view,previous[0]);
        ui(()->{check(field(view,"propertyEditor")==panel[0],"section keeps current parameter editor open");check(document[0].equals(((CadProject)field(view,"project")).toJson().toString()),"section does not change document or history");call(view,"activateTopTool",new Class<?>[]{String.class},"Bryła z liniami niewidocznymi");check((int)field(view,"displayMode")==3,"shaded solid with hidden lines is available during edit");});
        TabletFlowProbe.screenshot(activity,"084-03-section-during-edit");
        ui(()->{check((float)field(view,"zoom")==zoom[0],"hidden lines and section do not change zoom");check(field(view,"propertyEditor")==panel[0],"toolbar hidden-line mode preserves parameter panel and preview");});
        ui(()->{TabletFlowProbe.set(view,"sectionPlane",null);call(view,"closePropertyEditor",new Class<?>[0]);call(view,"activateProject",new Class<?>[]{CadProject.class},faceProfile());call(view,"activateTopTool",new Class<?>[]{String.class},"Wyciągnij");});
        ready(view,"Rozpoznano: Połącz");
        ui(()->{View editor=(View)field(view,"propertyEditor");check(CadScenarioProbe.spinner(editor).getSelectedItemPosition()==0,"automatic operation is the default in the real extrusion form");List<EditText> fields=new ArrayList<>();WorkspaceUiProbe.editors(editor,fields);fields.get(0).setText("15");TabletFlowProbe.findCheck(editor,"Odwróć kierunek").setChecked(true);});
        ready(view,"Rozpoznano: Wytnij");TabletFlowProbe.screenshot(activity,"084-04-auto-cut-preview");
        Button[] apply={null};ui(()->apply[0]=(Button)field(view,"panelConfirm"));TabletFlowProbe.click(apply[0]);TabletFlowProbe.pending(view);
        ui(()->{CadProject p=(CadProject)field(view,"project");check(p.committedFeatures.size()==1&&FeatureDirection.isCut(p.committedFeatures.get(0)[1]),"automatic inward preview commits a cut rather than a new body");});
        check(Math.abs(TabletFlowProbe.parameters(view)[0]-11000)<1e-6,"automatic through cut has exact 11000 mm³ volume");
        ui(()->{call(view,"activateProject",new Class<?>[]{CadProject.class},faceProfile());call(view,"activateTopTool",new Class<?>[]{String.class},"Wyciągnij");});
        ready(view,"Rozpoznano: Połącz");
        ui(()->CadScenarioProbe.spinner((View)field(view,"propertyEditor")).setSelection(1));ready(view,"Tryb: Nowa bryła");
        ui(()->apply[0]=(Button)field(view,"panelConfirm"));TabletFlowProbe.click(apply[0]);TabletFlowProbe.pending(view);
        ui(()->check(BodyGraph.bodies((CadProject)field(view,"project")).size()==2,"manual New body remains available and creates a distinct body"));
        ui(activity::finish);System.out.println("ALL CONTEXT PANEL / SOURCE PLACEMENT / STABLE VIEW UI TESTS PASSED");
    }
}
