import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/**
 * 0.86 sketch moving: grabbing a circle by its interior moves it, a drag on empty sketch space
 * pans instead of orbiting, a long press while drawing grabs an element, and "Położenie"
 * places it at exact distances with driving dimensions. Real pointer events on the device.
 */
public final class SketchMoveUiProbe {
    public static Context applicationContext;
    public static Instrumentation instrumentation;
    interface Step {void run()throws Exception;}
    static void ui(Step step)throws Exception{TabletFlowProbe.ui(step::run);}
    static Object field(Object v,String name)throws Exception{return TabletFlowProbe.field(v,name);}
    static Object call(Object v,String name,Class<?>[] types,Object... args)throws Exception{return TabletFlowProbe.call(v,name,types,args);}
    static void check(boolean ok,String label){TabletFlowProbe.check(ok,label);}
    static float[] screen(CadWorkspaceView view,double u,double v)throws Exception {
        float[] xy=new float[2];
        ui(()->{PointF p=(PointF)call(view,"projectSketchPoint",new Class<?>[]{double.class,double.class},u,v);int[] o=new int[2];view.getLocationOnScreen(o);xy[0]=p.x+o[0];xy[1]=p.y+o[1];});
        return xy;
    }
    static void drag(float x1,float y1,float x2,float y2,long holdMs)throws Exception {
        long start=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,x1,y1,0);instrumentation.sendPointerSync(down);down.recycle();
        if(holdMs>0)SystemClock.sleep(holdMs);
        for(int i=1;i<=10;i++){long t=SystemClock.uptimeMillis();MotionEvent m=MotionEvent.obtain(start,t,MotionEvent.ACTION_MOVE,x1+(x2-x1)*i/10,y1+(y2-y1)*i/10,0);instrumentation.sendPointerSync(m);m.recycle();SystemClock.sleep(16);}
        long t=SystemClock.uptimeMillis();MotionEvent up=MotionEvent.obtain(start,t,MotionEvent.ACTION_UP,x2,y2,0);instrumentation.sendPointerSync(up);up.recycle();
        instrumentation.waitForIdleSync();
    }
    static String diag(CadWorkspaceView view)throws Exception{String[] r={""};ui(()->{r[0]="mode="+field(view,"mode")+" tool="+field(view,"sketchTool")+" msg="+field(view,"operationMessage")+" finger="+field(view,"fingerDraws")+" plane="+field(view,"sketchPlaneChosen")+" sel="+field(view,"selectedSketchId");});return r[0];}
    static SketchPrimitive circle(CadWorkspaceView view)throws Exception {
        SketchPrimitive[] out={null};ui(()->{for(SketchPrimitive p:((CadProject)field(view,"project")).sketch)if(p.id.equals("probe-circle"))out[0]=p;});return out[0];
    }
    static void run(String[] args)throws Exception {
        TabletFlowProbe.instrumentation=instrumentation;TabletFlowProbe.applicationContext=applicationContext;
        Activity activity=instrumentation.startActivitySync(new Intent().setClassName(applicationContext,"pl.tabletcad.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.waitForIdleSync();File root=new File(applicationContext.getCacheDir(),"sketchmove-"+UUID.randomUUID());check(root.mkdirs(),"isolated sketch move fixture");
        CadWorkspaceView[] ref={null};
        ui(()->{
            java.lang.reflect.Field original=activity.getClass().getDeclaredField("workspace");original.setAccessible(true);CadWorkspaceView old=(CadWorkspaceView)original.get(activity);
            AlertDialog chooser=(AlertDialog)field(old,"projectChooser");if(chooser!=null)chooser.dismiss();
            Context context=new ContextWrapper(activity){@Override public File getFilesDir(){return root;}};
            CadWorkspaceView view=new CadWorkspaceView(context,null);ref[0]=view;FrameLayout host=new FrameLayout(activity);host.addView(view,new FrameLayout.LayoutParams(-1,-1));activity.setContentView(host);
        });instrumentation.waitForIdleSync();CadWorkspaceView view=ref[0];
        ui(()->{call(view,"activateTopTool",new Class<?>[]{String.class},"Szkic");call(view,"chooseSketchPlane",new Class<?>[]{String.class},"XY");
            CadProject p=(CadProject)field(view,"project");p.sketch.add(new SketchPrimitive("probe-circle","Okrąg",0,0,8,0));
            call(view,"activateTopTool",new Class<?>[]{String.class},"Edytuj");});
        instrumentation.waitForIdleSync();Thread.sleep(300);
        // 1. Interior grab moves the circle; the camera does not turn.
        float[] c=screen(view,0,0);float[] yaw={0},pitch={0};
        ui(()->{yaw[0]=(float)field(view,"cameraYaw");pitch[0]=(float)field(view,"cameraPitch");});
        drag(c[0]+6,c[1]+6,c[0]+206,c[1]+6,0);
        SketchPrimitive moved=circle(view);
        check(moved!=null&&moved.x1>5&&Math.abs(Math.hypot(moved.x2-moved.x1,moved.y2-moved.y1)-8)<1e-6,"drag inside the circle moves it with its radius ("+(moved==null?"none":moved.x1+","+moved.y1)+") "+diag(view));
        ui(()->check((float)field(view,"cameraYaw")==yaw[0]&&(float)field(view,"cameraPitch")==pitch[0],"moving sketch geometry never orbits the view"));
        // 2. Empty sketch space pans the view and keeps the orientation.
        float[] pan={0};ui(()->pan[0]=(float)field(view,"panX"));
        float[] empty=screen(view,-60,-40);drag(empty[0],empty[1],empty[0]+150,empty[1],0);
        ui(()->{check((float)field(view,"cameraYaw")==yaw[0],"one-finger drag on empty sketch space does not orbit");check((float)field(view,"panX")!=pan[0],"one-finger drag on empty sketch space pans");});
        // 3. Long press while drawing grabs the element instead of drawing a new one.
        ui(()->call(view,"activateTopTool",new Class<?>[]{String.class},"Okrąg"));instrumentation.waitForIdleSync();
        SketchPrimitive before=circle(view);int[] count={0};ui(()->count[0]=((CadProject)field(view,"project")).sketch.size());
        float[] grab=screen(view,before.x1,before.y1);
        drag(grab[0]+4,grab[1]+4,grab[0]+4,grab[1]+124,ViewConfiguration.getLongPressTimeout()+250);
        SketchPrimitive after=circle(view);
        ui(()->{check(((CadProject)field(view,"project")).sketch.size()==count[0],"long press while drawing adds no element");check("Edytuj szkic".equals(field(view,"mode")),"long press switches to editing");});
        check(Math.abs(after.y1-before.y1)>3,"long press and drag moves the grabbed circle");
        // 4. Position panel: exact distances from the origin with driving dimensions.
        ui(()->call(view,"showSketchPosition",new Class<?>[]{String.class,int.class,String.class,int.class},"probe-circle",0,null,0));instrumentation.waitForIdleSync();
        ui(()->{List<EditText> fields=new ArrayList<>();WorkspaceUiProbe.editors((View)field(view,"propertyEditor"),fields);
            check(fields.size()>=2,"position panel has X and Y fields");fields.get(0).setText("25");fields.get(1).setText("40");});
        instrumentation.waitForIdleSync();
        ui(()->((Button)field(view,"panelConfirm")).performClick());instrumentation.waitForIdleSync();
        SketchPrimitive placed=circle(view);
        check(Math.abs(placed.x1-25)<1e-6&&Math.abs(placed.y1-40)<1e-6,"Położenie places the centre at X25 Y40");
        ui(()->{int dims=0;for(SketchConstraints.Link l:((CadProject)field(view,"project")).coincidences)if(SketchConstraints.position(l.kind)&&l.a.equals("probe-circle"))dims++;check(dims==2,"two driving position dimensions on the sketch");});
        // 5. Multi-selection (0.88): two elements selected by taps move together with one drag.
        ui(()->{CadProject p=(CadProject)field(view,"project");p.sketch.add(new SketchPrimitive("probe-second","Okrąg",-40,-20,-35,-20));
            p.coincidences.clear();TabletFlowProbe.set(view,"multiSelect",true);((Set<?>)field(view,"sketchMulti")).clear();view.invalidate();});
        instrumentation.waitForIdleSync();
        SketchPrimitive first=circle(view);float[] a=screen(view,first.x1,first.y1),b=screen(view,-40,-20);
        TabletFlowProbe.tap(a[0]+3,a[1]+3);TabletFlowProbe.tap(b[0]+2,b[1]+2);
        ui(()->check(((Set<?>)field(view,"sketchMulti")).size()==2,"two taps select two elements"));
        drag(b[0]+2,b[1]+2,b[0]+122,b[1]+2,0);
        SketchPrimitive firstAfter=circle(view);SketchPrimitive[] second={null};
        ui(()->{for(SketchPrimitive p:((CadProject)field(view,"project")).sketch)if(p.id.equals("probe-second"))second[0]=p;});
        check(firstAfter.x1-first.x1>3&&Math.abs((firstAfter.x1-first.x1)-(second[0].x1+40))<1e-6,"dragging one selected element moves both by the same distance");
        System.out.println("PASS SketchMoveUiProbe");
    }
}
