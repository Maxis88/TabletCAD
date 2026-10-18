import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.*;

/**
 * Mandatory CAD workflow (project instructions 16.3) in the visible window with real touch
 * events: dimensioned 100×60 sketch, 20 mm extrusion, Ø10 hole on the top face, fillet, save,
 * reopen, parametric edit of the first sketch, STEP export and re-import. Isolated storage.
 */
public final class CadScenarioProbe {
    public static Context applicationContext;
    public static Instrumentation instrumentation;
    static void ui(TabletFlowProbe.Step step)throws Exception{TabletFlowProbe.ui(step);}
    static void check(boolean ok,String text){TabletFlowProbe.check(ok,text);}
    static Object field(Object o,String name)throws Exception{return TabletFlowProbe.field(o,name);}
    static Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception{return TabletFlowProbe.call(o,name,types,args);}
    static void tool(CadWorkspaceView view,String name)throws Exception{ui(()->call(view,"activateTopTool",new Class<?>[]{String.class},name));instrumentation.waitForIdleSync();}
    static float[] screen(CadWorkspaceView view,double u,double v)throws Exception {
        float[] xy=new float[2];ui(()->{PointF p=(PointF)call(view,"projectSketchPoint",new Class<?>[]{double.class,double.class},u,v);int[] o=new int[2];view.getLocationOnScreen(o);xy[0]=p.x+o[0];xy[1]=p.y+o[1];});return xy;
    }
    static void drag(float x1,float y1,float x2,float y2)throws Exception {
        long t=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x1,y1,0);instrumentation.sendPointerSync(e);e.recycle();
        for(int i=1;i<=12;i++){SystemClock.sleep(16);e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x1+(x2-x1)*i/12,y1+(y2-y1)*i/12,0);instrumentation.sendPointerSync(e);e.recycle();}
        e=MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x2,y2,0);instrumentation.sendPointerSync(e);e.recycle();instrumentation.waitForIdleSync();
    }
    static double volume(CadWorkspaceView view)throws Exception{double[] p=TabletFlowProbe.parameters(view);check(p.length==7,"model has exact B-Rep properties");return p[0];}
    static String describe(CadWorkspaceView view)throws Exception{CadProject p=(CadProject)field(view,"project");return "mode="+field(view,"mode")+" msg="+field(view,"operationMessage")+" sketch="+p.sketch.size()+" features="+p.committedFeatures.size()+" solid="+p.hasSolid();}
    static String state(CadWorkspaceView view)throws Exception{if(Looper.myLooper()==Looper.getMainLooper())return describe(view);String[] s={null};ui(()->s[0]=describe(view));return s[0];}
    static Object dimension(CadWorkspaceView view,String entity,String kind)throws Exception {
        Object[] found={null};ui(()->{for(Object hit:(List<?>)field(view,"dimensionHits"))if((entity==null||field(hit,"entityId").equals(entity))&&field(hit,"kind").equals(kind))found[0]=hit;});return found[0];
    }
    static void editDimension(CadWorkspaceView view,String entity,String kind,String value)throws Exception {
        ui(()->{view.invalidate();});instrumentation.waitForIdleSync();Thread.sleep(150);
        Object hit=dimension(view,entity,kind);check(hit!=null,"dimension label "+kind+" visible on sketch ("+state(view)+")");
        RectF b=(RectF)field(hit,"bounds");int[] o=new int[2];ui(()->view.getLocationOnScreen(o));
        TabletFlowProbe.tap(b.centerX()+o[0],b.centerY()+o[1]);Thread.sleep(80);TabletFlowProbe.tap(b.centerX()+o[0],b.centerY()+o[1]);Thread.sleep(300);
        View[] apply={null};ui(()->{View panel=(View)field(view,"propertyEditor");check(panel!=null,"double tap on "+kind+" opens value editor");InteractionFixProbe.input(panel).setText(value);apply[0]=WorkspaceUiProbe.button(panel,"Zastosuj");});
        TabletFlowProbe.click(apply[0]);instrumentation.waitForIdleSync();Thread.sleep(200);
    }
    static Spinner spinner(View v){if(v instanceof Spinner)return (Spinner)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Spinner f=spinner(((ViewGroup)v).getChildAt(i));if(f!=null)return f;}return null;}
    static float[] world(CadWorkspaceView view,double x,double y,double z)throws Exception {
        float[] xy=new float[2];ui(()->{double[] p=(double[])call(view,"projectWorldPoint",new Class<?>[]{double.class,double.class,double.class},x,y,z);int[] o=new int[2];view.getLocationOnScreen(o);xy[0]=(float)p[0]+o[0];xy[1]=(float)p[1]+o[1];});return xy;
    }
    static SketchPrimitive entity(CadWorkspaceView view,String id)throws Exception{SketchPrimitive[] r={null};ui(()->{for(SketchPrimitive p:((CadProject)field(view,"project")).sketch)if(p.id.equals(id))r[0]=p;});return r[0];}
    static SketchConstraints.Link positionLink(CadWorkspaceView view,String id,String kind)throws Exception{SketchConstraints.Link[] r={null};ui(()->{for(SketchConstraints.Link l:((CadProject)field(view,"project")).coincidences)if(l.kind.equals(kind)&&l.a.equals(id)&&SketchConstraints.fromOrigin(l))r[0]=l;});return r[0];}
    static void editPosition(CadWorkspaceView view,String id,String kind,String value)throws Exception {
        int[] index={-1};ui(()->{List<SketchConstraints.Link> links=((CadProject)field(view,"project")).coincidences;for(int i=0;i<links.size();i++){SketchConstraints.Link l=links.get(i);if(l.kind.equals(kind)&&l.a.equals(id))index[0]=i;}});
        check(index[0]>=0,"position dimension "+kind+" exists for "+id);editDimension(view,"link:"+index[0],kind,value);
    }
    static void run(String[] args)throws Exception {
        TabletFlowProbe.instrumentation=instrumentation;TabletFlowProbe.applicationContext=applicationContext;
        Intent launch=new Intent().setClassName(applicationContext,"pl.tabletcad.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity=instrumentation.startActivitySync(launch);instrumentation.waitForIdleSync();
        File root=new File(applicationContext.getCacheDir(),"scenario-"+UUID.randomUUID());check(root.mkdirs(),"isolated scenario storage");
        CadWorkspaceView[] reference={null};
        ui(()->{java.lang.reflect.Field original=activity.getClass().getDeclaredField("workspace");original.setAccessible(true);CadWorkspaceView old=(CadWorkspaceView)original.get(activity);
            AlertDialog chooser=(AlertDialog)field(old,"projectChooser");if(chooser!=null)chooser.dismiss();
            Context context=new ContextWrapper(activity){@Override public File getFilesDir(){return root;}};
            CadProject blank=CadProject.blank();blank.name="Scenariusz-16-3";new ProjectRepository(root).save(blank);
            CadWorkspaceView view=new CadWorkspaceView(context,null);reference[0]=view;FrameLayout host=new FrameLayout(activity);host.addView(view,new FrameLayout.LayoutParams(-1,-1));activity.setContentView(host);
            AlertDialog second=(AlertDialog)field(view,"projectChooser");if(second!=null)second.dismiss();});
        instrumentation.waitForIdleSync();CadWorkspaceView view=reference[0];
        ui(()->check(!((CadProject)field(view,"project")).hasSolid(),"1-2 new empty project"));
        // 3. Sketch on XY.
        tool(view,"Szkic");ui(()->call(view,"chooseSketchPlane",new Class<?>[]{String.class},"XY"));instrumentation.waitForIdleSync();
        ui(()->{TabletFlowProbe.set(view,"zoom",2.5f);view.invalidate();});instrumentation.waitForIdleSync();
        // 4. Rectangle drawn with a finger drag.
        tool(view,"Prostokąt");ui(()->{View panel=(View)field(view,"propertyEditor");if(panel!=null){Button b=WorkspaceUiProbe.button(panel,"Anuluj");if(b!=null)b.performClick();}});instrumentation.waitForIdleSync();
        float[] a=screen(view,3,3),b=screen(view,83,47);drag(a[0],a[1],b[0],b[1]);Thread.sleep(300);
        SketchPrimitive[] rect={null};ui(()->{for(SketchPrimitive p:((CadProject)field(view,"project")).sketch)if(p.type.equals("Prostokąt"))rect[0]=p;});
        check(rect[0]!=null,"4 finger drag draws a rectangle ("+state(view)+")");System.out.println("DRAWN "+rect[0].x1+","+rect[0].y1+" "+rect[0].x2+","+rect[0].y2);
        // 5. Dimensions 100 × 60 typed into on-sketch labels.
        tool(view,"Edytuj");
        editDimension(view,rect[0].id,"width","100");editDimension(view,rect[0].id,"height","60");
        ui(()->{for(SketchPrimitive p:((CadProject)field(view,"project")).sketch)if(p.id.equals(rect[0].id))rect[0]=p;});
        check(Math.abs(Math.abs(rect[0].x2-rect[0].x1)-100)<1e-9&&Math.abs(Math.abs(rect[0].y2-rect[0].y1)-60)<1e-9,"5 sketch dimensions are exactly 100 × 60 mm");
        // Position the rectangle corner on the origin with driving X/Y dimensions (Geometria menu action).
        ui(()->call(view,"addOriginPositionDimensions",new Class<?>[]{SketchPrimitive.class},rect[0]));instrumentation.waitForIdleSync();
        check(positionLink(view,rect[0].id,"dimX")!=null&&positionLink(view,rect[0].id,"dimY")!=null,"5 corner position dimensions X/Y created ("+state(view)+")");
        editPosition(view,rect[0].id,"dimX","0");editPosition(view,rect[0].id,"dimY","0");
        rect[0]=entity(view,rect[0].id);
        check(Math.abs(Math.min(rect[0].x1,rect[0].x2))<1e-9&&Math.abs(Math.min(rect[0].y1,rect[0].y2))<1e-9&&Math.abs(Math.abs(rect[0].x2-rect[0].x1)-100)<1e-9,"5 rectangle fully dimensioned at origin: "+rect[0].x1+","+rect[0].y1+" "+rect[0].x2+","+rect[0].y2);
        TabletFlowProbe.screenshot(activity,"scenario-01-sketch");
        // 6-7. Finish sketch and extrude 20 mm.
        tool(view,"Wyciągnij");
        ui(()->{View panel=(View)field(view,"propertyEditor");check(panel!=null,"extrusion panel opens ("+state(view)+")");List<EditText> inputs=new ArrayList<>();WorkspaceUiProbe.editors(panel,inputs);inputs.get(0).setText("20");});
        Thread.sleep(400);float[] inside=screen(view,(rect[0].x1+rect[0].x2)/2,(rect[0].y1+rect[0].y2)/2);TabletFlowProbe.tap(inside[0],inside[1]);Thread.sleep(400);
        View[] apply={null};ui(()->{check(((Set<?>)field(view,"extrusionSelections")).size()==1,"7 tap selects the profile to extrude");apply[0]=WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Zastosuj");});
        TabletFlowProbe.click(apply[0]);TabletFlowProbe.pending(view);instrumentation.waitForIdleSync();
        double v=volume(view);check(Math.abs(v-120000)<1e-6,"7 extrusion volume 100×60×20 = 120000 mm³ ("+v+", "+state(view)+")");
        TabletFlowProbe.screenshot(activity,"scenario-02-extruded");
        // 8. Second sketch on the top face, picked with a real tap.
        ui(()->{TabletFlowProbe.set(view,"cameraYaw",-28f);TabletFlowProbe.set(view,"cameraPitch",22f);call(view,"fitView",new Class<?>[0]);view.invalidate();});instrumentation.waitForIdleSync();
        tool(view,"Wybierz");float[] top=world(view,70,40,20);TabletFlowProbe.tap(top[0],top[1]);Thread.sleep(300);
        ui(()->check((int)field(view,"selectedFace")>=0&&Math.abs((double)field(view,"selectedNormalZ")-1)<1e-6,"8 tap selects the top face (normal +Z): "+describe(view)));
        tool(view,"Szkic");
        ui(()->{CadProject p=(CadProject)field(view,"project");check(Math.abs(p.sketchOriginZ-20)<1e-9&&Math.abs(p.sketchOriginX)<1e-9&&Math.abs(p.sketchOriginY)<1e-9&&Math.abs(p.sketchUx-1)<1e-9&&Math.abs(p.sketchVy-1)<1e-9,
            "8 face sketch frame: origin is the model origin projected on the face ("+p.sketchOriginX+","+p.sketchOriginY+","+p.sketchOriginZ+")");});
        // 9. Circle drawn with a finger, diameter typed into its Ø label.
        tool(view,"Okrąg");ui(()->{View panel=(View)field(view,"propertyEditor");if(panel!=null){Button cancel=WorkspaceUiProbe.button(panel,"Anuluj");if(cancel!=null)cancel.performClick();}});instrumentation.waitForIdleSync();
        float[] c1=screen(view,24,31),c2=screen(view,28,31);drag(c1[0],c1[1],c2[0],c2[1]);Thread.sleep(300);
        SketchPrimitive[] hole={null};ui(()->{for(SketchPrimitive p:((CadProject)field(view,"project")).sketch)if(p.type.equals("Okrąg"))hole[0]=p;});
        check(hole[0]!=null,"9 finger drag draws a circle on the face sketch ("+state(view)+")");
        tool(view,"Edytuj");editDimension(view,hole[0].id,"diameter","10");hole[0]=entity(view,hole[0].id);
        check(Math.abs(2*Math.hypot(hole[0].x2-hole[0].x1,hole[0].y2-hole[0].y1)-10)<1e-9,"9 circle diameter is exactly Ø10");
        // 10. Centre position from the origin (= from the plate edges) X 25, Y 30.
        ui(()->call(view,"addOriginPositionDimensions",new Class<?>[]{SketchPrimitive.class},hole[0]));instrumentation.waitForIdleSync();
        editPosition(view,hole[0].id,"dimX","25");editPosition(view,hole[0].id,"dimY","30");hole[0]=entity(view,hole[0].id);
        check(Math.abs(hole[0].x1-25)<1e-9&&Math.abs(hole[0].y1-30)<1e-9&&Math.abs(2*Math.hypot(hole[0].x2-hole[0].x1,hole[0].y2-hole[0].y1)-10)<1e-9,"10 hole centre dimensioned 25/30 mm from the plate edges, Ø kept");
        TabletFlowProbe.screenshot(activity,"scenario-03-hole-sketch");
        // 11. Cut through the whole body.
        tool(view,"Wyciągnij");
        ui(()->{View panel=(View)field(view,"propertyEditor");check(panel!=null,"11 extrusion panel opens on face sketch ("+describe(view)+")");spinner(panel).setSelection(2);List<EditText> inputs=new ArrayList<>();WorkspaceUiProbe.editors(panel,inputs);inputs.get(0).setText("20");});
        Thread.sleep(400);float[] holeCentre=screen(view,25,30);TabletFlowProbe.tap(holeCentre[0],holeCentre[1]);Thread.sleep(400);
        ui(()->{check(((Set<?>)field(view,"extrusionSelections")).size()==1,"11 tap selects the circle profile");apply[0]=WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Zastosuj");});
        TabletFlowProbe.click(apply[0]);TabletFlowProbe.pending(view);instrumentation.waitForIdleSync();
        double drilled=120000-Math.PI*25*20;v=volume(view);System.out.println("CUT "+v+" "+state(view));
        check(Math.abs(v-drilled)<1e-6,"11 through hole: volume 120000 − 500π = "+drilled+" ("+v+")");
        TabletFlowProbe.screenshot(activity,"scenario-04-hole");
        ui(activity::finish);System.out.println("SCENARIO STAGE 2 PASSED");
    }
}
