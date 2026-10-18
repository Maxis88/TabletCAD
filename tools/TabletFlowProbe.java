import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.*;

/** Visible Android-window flow; only the replacement workspace uses isolated test storage. */
public final class TabletFlowProbe {
    public static Context applicationContext;
    public static Instrumentation instrumentation;
    interface Step {void run()throws Exception;}
    static void ui(Step step)throws Exception {Throwable[] error={null};instrumentation.runOnMainSync(()->{try{step.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError("tablet window step",error[0]);}
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("PASS "+text);}
    static Object field(Object o,String name)throws Exception{Class<?> c=o.getClass();while(c!=null){try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(name);}
    static void set(Object o,String name,Object value)throws Exception{Field f=CadWorkspaceView.class.getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception{Method m=CadWorkspaceView.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    static void tap(float x,float y)throws Exception {long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+40,MotionEvent.ACTION_UP,x,y,0);instrumentation.sendPointerSync(down);SystemClock.sleep(45);instrumentation.sendPointerSync(up);down.recycle();up.recycle();instrumentation.waitForIdleSync();}
    static void click(View view)throws Exception {Thread.sleep(400);int[] origin=new int[2];Rect visible=new Rect();ui(()->{check(view.getGlobalVisibleRect(visible),"native action is visible");view.getLocationOnScreen(origin);});tap(origin[0]+view.getWidth()/2f,origin[1]+view.getHeight()/2f);}
    static void point(CadWorkspaceView view,double u,double v)throws Exception {float[] xy=new float[2];ui(()->{PointF p=(PointF)call(view,"projectSketchPoint",new Class<?>[]{double.class,double.class},u,v);int[] origin=new int[2];view.getLocationOnScreen(origin);xy[0]=p.x+origin[0];xy[1]=p.y+origin[1];});System.out.println("TAP "+u+","+v+" at "+xy[0]+","+xy[1]);tap(xy[0],xy[1]);ui(()->System.out.println("AFTER "+field(view,"extrusionSelections")));Thread.sleep(360);}
    static void screenshot(Activity activity,String name)throws Exception {instrumentation.waitForIdleSync();Thread.sleep(180);Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();check(bitmap!=null,"window screenshot "+name);File folder=new File(activity.getExternalCacheDir(),"regression056");folder.mkdirs();try(OutputStream stream=new FileOutputStream(new File(folder,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();}
    static void pending(CadWorkspaceView view)throws Exception {long until=SystemClock.uptimeMillis()+60000;while(SystemClock.uptimeMillis()<until){boolean[] busy={true};ui(()->busy[0]=(boolean)field(view,"modelOperationPending"));if(!busy[0])return;Thread.sleep(30);}throw new AssertionError("model operation timed out");}
    static double[] parameters(CadWorkspaceView view)throws Exception {CadProject[] p={null};ui(()->p[0]=(CadProject)field(view,"project"));double[] raw=(double[])call(view,"kernelFeatures",new Class<?>[0]);return NativeCadEngine.bodyProperties(p[0].width,p[0].height,p[0].depth,p[0].filletRadius,p[0].featureBase,raw,null);}
    static void run(String[] args)throws Exception {
        Intent launch=new Intent().setClassName(applicationContext,"pl.tabletcad.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity=instrumentation.startActivitySync(launch);instrumentation.waitForIdleSync();final CadWorkspaceView[] reference={null};final File root=new File(applicationContext.getCacheDir(),"tablet-flow-"+UUID.randomUUID());check(root.mkdirs(),"isolated visible tablet fixture");
        ui(()->{
            Field original=activity.getClass().getDeclaredField("workspace");original.setAccessible(true);CadWorkspaceView old=(CadWorkspaceView)original.get(activity);AlertDialog chooser=(AlertDialog)field(old,"projectChooser");if(chooser!=null)chooser.dismiss();Context context=new ContextWrapper(activity){@Override public File getFilesDir(){return root;}};CadProject p=CadProject.blank();
            p.sketch.add(new SketchPrimitive("left","Prostokąt",-35,-10,-15,10));p.sketch.add(new SketchPrimitive("right","Prostokąt",15,-10,35,10));p.sketch.add(new SketchPrimitive("guide","Okrąg",0,25,4,25).withConstruction(true));new ProjectRepository(root).save(p);
            CadWorkspaceView view=new CadWorkspaceView(context,null);reference[0]=view;FrameLayout host=new FrameLayout(activity);host.addView(view,new FrameLayout.LayoutParams(-1,-1));activity.setContentView(host);set(view,"zoom",3f);
        });instrumentation.waitForIdleSync();CadWorkspaceView view=reference[0];
        ui(()->call(view,"activateTopTool",new Class<?>[]{String.class},"Wyciągnij"));instrumentation.waitForIdleSync();
        ui(()->{View panel=(View)field(view,"propertyEditor");List<EditText> inputs=new ArrayList<>();WorkspaceUiProbe.editors(panel,inputs);check(inputs.size()==2,"two independent extrusion inputs are available");inputs.get(0).setText("4");inputs.get(1).setText("2");Button both=WorkspaceUiProbe.button(panel,"Dwa kierunki · niezależne długości");if(both!=null)both.performClick();else {CheckBox box=findCheck(panel,"Dwa kierunki · niezależne długości");check(box!=null,"two-direction toggle exists");box.setChecked(true);}});
        Thread.sleep(500);point(view,-25,0);point(view,25,0);point(view,0,25);
        ui(()->check(((Set<?>)field(view,"extrusionSelections")).size()==2,"window taps select two ordinary profiles and exclude helper circle"));screenshot(activity,"01-multi-profile-selection");
        View[] apply={null};ui(()->apply[0]=WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Zastosuj"));click(apply[0]);pending(view);instrumentation.waitForIdleSync();
        ui(()->{CadProject p=(CadProject)field(view,"project");check(p.hasSolid()&&BodyGraph.bodies(p).size()==2,"single apply creates two explicit bodies");check(p.extrusionExtents.get(-1)[1]==2&&p.extrusionExtents.get(0)[1]==2,"both source-plane extents are persisted per body");set(view,"cameraYaw",-28f);set(view,"cameraPitch",22f);view.invalidate();});
        double[] result=parameters(view);check(result.length==7&&Math.abs(result[0]-4800)<.001&&result[5]==2,"two-direction batch has exact OCCT volume and solid count");screenshot(activity,"02-two-bodies-tree");
        String[] bodyUuid={null};ui(()->{CadProject p=(CadProject)field(view,"project");bodyUuid[0]=p.bodyUuids.get(2);call(view,"undo",new Class<?>[0]);check(!((CadProject)field(view,"project")).hasSolid(),"one undo removes complete multi-profile transaction");call(view,"redo",new Class<?>[0]);check(BodyGraph.bodies((CadProject)field(view,"project")).size()==2,"one redo restores both bodies");});
        // Select the second body's source sketch from the real tree.
        float[] treePoint=new float[2];ui(()->{List<?> rows=(List<?>)field(view,"modelTreeRows");int index=-1;for(int i=0;i<rows.size();i++){ModelTree.Row row=(ModelTree.Row)rows.get(i);if(row.body==2&&row.sketch())index=i;}check(index>=0,"tree contains scoped sketch under second body");float density=(float)field(view,"density");treePoint[0]=80*density;treePoint[1]=(184+index*36+18)*density;});tap(treePoint[0],treePoint[1]);
        ui(()->check(field(view,"propertyEditor")==null,"single tree tap highlights without opening editor"));tap(treePoint[0],treePoint[1]);instrumentation.waitForIdleSync();
        ui(()->check(field(view,"mode").equals("Edytuj szkic"),"tree double tap enters actual sketch editor"));screenshot(activity,"03-sketch-dimensions");
        float[] dimensionPoint=new float[2];ui(()->{Object selected=null;for(Object hit:(List<?>)field(view,"dimensionHits"))if(field(hit,"entityId").equals("right")&&field(hit,"kind").equals("width"))selected=hit;check(selected!=null,"right body source width label is visible");RectF bounds=(RectF)field(selected,"bounds");int[] origin=new int[2];view.getLocationOnScreen(origin);dimensionPoint[0]=bounds.centerX()+origin[0];dimensionPoint[1]=bounds.centerY()+origin[1];});tap(dimensionPoint[0],dimensionPoint[1]);tap(dimensionPoint[0],dimensionPoint[1]);
        ui(()->{View panel=(View)field(view,"propertyEditor");check(panel!=null,"window double tap on width opens value editor");InteractionFixProbe.input(panel).setText("25");apply[0]=WorkspaceUiProbe.button(panel,"Zastosuj");});click(apply[0]);ui(()->call(view,"activateTopTool",new Class<?>[]{String.class},"Zakończ"));instrumentation.waitForIdleSync();
        result=parameters(view);check(result.length==7&&Math.abs(result[0]-5400)<.001,"dimension edit rebuilds the corresponding body through its source sketch");ui(()->check(((CadProject)field(view,"project")).bodyUuids.get(2).equals(bodyUuid[0]),"source edit retains body UUID"));
        // The actual startup dialog uses this isolated repository, never the user's project library.
        ui(()->view.showStartupProjects());instrumentation.waitForIdleSync();screenshot(activity,"04-project-library");View[] newProject={null};ui(()->newProject[0]=((AlertDialog)field(view,"projectChooser")).getButton(AlertDialog.BUTTON_POSITIVE));click(newProject[0]);
        ui(()->{CadProject p=(CadProject)field(view,"project");System.out.println("NEW state "+p.name+" solid="+p.hasSolid()+" sketch="+p.sketch.size()+" mesh="+((double[])field(view,"nativeMesh")).length+" message="+field(view,"operationMessage"));check(!p.hasSolid()&&p.sketch.isEmpty()&&((double[])field(view,"nativeMesh")).length==0,"startup button immediately opens new empty project without old geometry");});screenshot(activity,"05-new-empty-project");
                ui(()->{CadProject drilled=new CadProject();drilled.width=40;drilled.height=30;drilled.depth=10;for(double x:new double[]{10,30}){drilled.committedFeatures.add(new double[]{1,1,x,15,x+2.5,15,10,0,0,10,1,0,0,0,1,0});drilled.committedProfileIds.add("");}call(view,"activateProject",new Class<?>[]{CadProject.class},drilled);set(view,"zoom",5f);set(view,"cameraYaw",-10f);set(view,"cameraPitch",20f);call(view,"beginThreadTool",new Class<?>[0]);view.invalidate();});instrumentation.waitForIdleSync();
        double drilledVolume=parameters(view)[0];
        List<HoleThread.Cylinder> holes=new ArrayList<>();ui(()->holes.addAll((List<HoleThread.Cylinder>)field(view,"holeCylinders")));check(holes.size()==2,"two real cylindrical holes available in visible model");
        for(HoleThread.Cylinder hole:holes){float[] location={Float.NaN,Float.NaN};ui(()->{for(Object triangle:(List<?>)field(view,"projectedTriangles")){if((int)field(triangle,"faceId")!=hole.face)continue;float[] x=(float[])field(triangle,"sx"),y=(float[])field(triangle,"sy");float cx=(x[0]+x[1]+x[2])/3,cy=(y[0]+y[1]+y[2])/3;if((int)call(view,"findFaceAt",new Class<?>[]{float.class,float.class},cx,cy)==hole.face){location[0]=cx;location[1]=cy;break;}}});check(Float.isFinite(location[0]),"visible cylinder wall has a selectable point");tap(location[0],location[1]);Thread.sleep(360);}
        ui(()->check(((Set<?>)field(view,"threadSelections")).size()==2,"physical taps select both cylindrical hole walls"));screenshot(activity,"06-thread-presets");
        ui(()->{View panel=(View)field(view,"propertyEditor");findCheck(panel,"Pełna długość każdego otworu").setChecked(false);InteractionFixProbe.input(panel).setText("3");apply[0]=WorkspaceUiProbe.button(panel,"Utwórz gwinty");});click(apply[0]);pending(view);instrumentation.waitForIdleSync();
        result=parameters(view);check(result.length==7&&result[0]<drilledVolume-2&&result[5]==1,"one apply cuts two real helical threads in the target body");screenshot(activity,"07-real-threads");
        ui(()->{call(view,"undo",new Class<?>[0]);check(((CadProject)field(view,"project")).committedFeatures.size()==2,"one undo removes both threads together");});check(Math.abs(parameters(view)[0]-drilledVolume)<.001,"thread undo restores exact pre-thread volume");
        ui(activity::finish);System.out.println("ALL VISIBLE TABLET FLOW TESTS PASSED");
    }
    static CheckBox findCheck(View view,String label){if(view instanceof CheckBox&&((CheckBox)view).getText().toString().equals(label))return (CheckBox)view;if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++){CheckBox found=findCheck(((android.view.ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
}
