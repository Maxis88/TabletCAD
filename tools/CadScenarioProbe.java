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
    static float[] visibleFace(CadWorkspaceView view,double nx,double ny,double nz)throws Exception {
        float[] point={Float.NaN,Float.NaN};
        ui(()->{Object preview=field(view,"featurePreview");TabletFlowProbe.set(view,"featurePreview",null);
            try {call(view,"projectNativeMesh",new Class<?>[]{float.class,float.class},field(view,"workCenterX"),field(view,"workCenterY"));
                for(Object triangle:(List<?>)field(view,"projectedTriangles")) {
                    if(Math.abs((double)field(triangle,"nx")-nx)>1e-6||Math.abs((double)field(triangle,"ny")-ny)>1e-6||Math.abs((double)field(triangle,"nz")-nz)>1e-6)continue;
                    float[] x=(float[])field(triangle,"sx"),y=(float[])field(triangle,"sy");float cx=(x[0]+x[1]+x[2])/3,cy=(y[0]+y[1]+y[2])/3;
                    if((boolean)call(view,"isInsideViewport",new Class<?>[]{float.class,float.class},cx,cy)
                        &&(int)call(view,"findFaceAt",new Class<?>[]{float.class,float.class},cx,cy)==(int)field(triangle,"faceId")) {
                        int[] origin=new int[2];view.getLocationOnScreen(origin);point[0]=cx+origin[0];point[1]=cy+origin[1];break;
                    }
                }
            } finally {TabletFlowProbe.set(view,"featurePreview",preview);}
            check(Float.isFinite(point[0]),"requested target face is visible and selectable; camera="+field(view,"cameraYaw")+","+field(view,"cameraPitch"));
        });return point;
    }
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
        instrumentation.waitForIdleSync();float[] arrow=new float[4];float[] arrowYaw={0};
        ui(()->{PointF h=(PointF)field(view,"extrusionHandle");check(h!=null,"7 current profile selection shows the extrusion arrow");int[] o=new int[2];view.getLocationOnScreen(o);
            double start=(double)call(view,"extrusionParameter",new Class<?>[]{float.class,float.class},h.x,h.y);double[] origin=(double[])field(view,"extrusionOrigin"),axis=(double[])field(view,"extrusionAxis");
            double[] end=(double[])call(view,"projectWorldPoint",new Class<?>[]{double.class,double.class,double.class},origin[0]+axis[0]*(start+5),origin[1]+axis[1]*(start+5),origin[2]+axis[2]*(start+5));
            arrow[0]=h.x+o[0];arrow[1]=h.y+o[1];arrow[2]=(float)end[0]+o[0];arrow[3]=(float)end[1]+o[1];arrowYaw[0]=(float)field(view,"cameraYaw");});
        drag(arrow[0],arrow[1],arrow[2],arrow[3]);
        ui(()->{check(Math.abs(Double.parseDouble(((EditText)field(view,"extrusionDimension")).getText().toString())-25)<.01,"7 real window arrow drag changes extrusion from 20 to 25 mm");check((float)field(view,"cameraYaw")==arrowYaw[0],"7 arrow drag does not rotate the camera");((EditText)field(view,"extrusionDimension")).setText("20");});
        Thread.sleep(400);TabletFlowProbe.screenshot(activity,"scenario-extrusion-arrow");
        TabletFlowProbe.click(apply[0]);TabletFlowProbe.pending(view);instrumentation.waitForIdleSync();
        double v=volume(view);check(Math.abs(v-120000)<1e-6,"7 extrusion volume 100×60×20 = 120000 mm³ ("+v+", "+state(view)+")");
        TabletFlowProbe.screenshot(activity,"scenario-02-extruded");
        // Orbit right after the extrusion, without choosing Wybierz: one finger on empty space.
        float[] yaw0={0};int[] loc=new int[2];ui(()->{yaw0[0]=(float)field(view,"cameraYaw");view.getLocationOnScreen(loc);});
        float ex=loc[0]+view.getWidth()*0.80f,ey=loc[1]+view.getHeight()*0.30f;drag(ex,ey,ex-160,ey+40);
        ui(()->check(Math.abs((float)field(view,"cameraYaw")-yaw0[0])>5,"7 one-finger drag orbits right after the extrusion ("+describe(view)+")"));
        // 8. Second sketch on the top face, picked with a real tap.
        ui(()->{TabletFlowProbe.set(view,"cameraYaw",-28f);TabletFlowProbe.set(view,"cameraPitch",22f);call(view,"fitView",new Class<?>[0]);view.invalidate();});instrumentation.waitForIdleSync();
        tool(view,"Wybierz");float[] top=world(view,70,40,20);TabletFlowProbe.tap(top[0],top[1]);Thread.sleep(300);
        ui(()->check((int)field(view,"selectedFace")>=0&&Math.abs((double)field(view,"selectedNormalZ")-1)<1e-6,"8 tap selects the top face (normal +Z): "+describe(view)));
        ui(()->check(field(view,"propertyEditor")==null,"8 tapping a face only selects it, no editor opens ("+describe(view)+")"));
        ui(()->check((int)call(view,"findFaceAt",new Class<?>[]{float.class,float.class},ex-loc[0],ey-loc[1])<0,"8 deselection tap lies outside the solid"));
        TabletFlowProbe.tap(ex,ey);Thread.sleep(250);
        ui(()->check((int)field(view,"selectedFace")==-1&&(int)field(view,"highlightBody")==0
            &&(int)field(view,"highlightFeature")==Integer.MIN_VALUE&&!(boolean)field(view,"highlightSketch")
            &&field(view,"selectedSketchId")==null&&field(view,"selectedTreeKey").equals("")
            &&(int)field(view,"selectedTimelineIndex")==-1,"8 empty viewport tap clears all model highlights"));
        TabletFlowProbe.tap(top[0],top[1]);Thread.sleep(250);
        // Long press on the face edits the operation that created it; Cancel keeps the selection.
        long down=SystemClock.uptimeMillis();MotionEvent press=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,top[0],top[1],0);instrumentation.sendPointerSync(press);press.recycle();
        SystemClock.sleep(900);press=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,top[0],top[1],0);instrumentation.sendPointerSync(press);press.recycle();instrumentation.waitForIdleSync();Thread.sleep(200);
        ui(()->{View panel=(View)field(view,"propertyEditor");check(panel!=null,"8 long press on the face opens the parameters of its extrusion ("+describe(view)+")");
            Button cancel=WorkspaceUiProbe.button(panel,"Anuluj");if(cancel!=null)cancel.performClick();check((int)field(view,"selectedFace")>=0,"8 face stays selected after closing the editor");});
        tool(view,"Szkic");
        ui(()->{CadProject p=(CadProject)field(view,"project");check(Math.abs(p.sketchOriginZ-20)<1e-9&&Math.abs(p.sketchOriginX)<1e-9&&Math.abs(p.sketchOriginY)<1e-9&&Math.abs(p.sketchUx-1)<1e-9&&Math.abs(p.sketchVy-1)<1e-9,
            "8 face sketch frame: origin is the model origin projected on the face ("+p.sketchOriginX+","+p.sketchOriginY+","+p.sketchOriginZ+")");});
        // Sketch on a body: Obróć widok orbits with one finger without drawing; touches stay exact on the plane.
        int[] count={0};ui(()->{yaw0[0]=(float)field(view,"cameraYaw");count[0]=((CadProject)field(view,"project")).sketch.size();});
        tool(view,"Obróć widok");drag(ex,ey,ex-160,ey+60);
        ui(()->{check(Math.abs((float)field(view,"cameraYaw")-yaw0[0])>5&&((CadProject)field(view,"project")).sketch.size()==count[0],"9 Obróć widok: a finger drag orbits the face sketch and draws nothing ("+describe(view)+")");
            PointF at=(PointF)call(view,"projectSketchPoint",new Class<?>[]{double.class,double.class},37.5,12.25);double[] back=(double[])call(view,"screenToSketch",new Class<?>[]{float.class,float.class},at.x,at.y);
            check(Math.abs(back[0]-37.5)<1e-3&&Math.abs(back[1]-12.25)<1e-3,"9 rotated perspective view maps a touch exactly onto the sketch plane ("+back[0]+","+back[1]+")");});
        tool(view,"Widok na szkic");
        ui(()->check(Math.abs((float)field(view,"cameraYaw"))<1e-3&&Math.abs((float)field(view,"cameraPitch"))<1e-3&&!(boolean)field(view,"sketchOrbit"),"9 Widok na szkic re-aligns the view square to the face and resumes drawing"));
        // 9. Circle drawn with a finger, diameter typed into its Ø label.
        tool(view,"Obróć widok");
        tool(view,"Okrąg");ui(()->{View panel=(View)field(view,"propertyEditor");if(panel!=null){Button cancel=WorkspaceUiProbe.button(panel,"Anuluj");if(cancel!=null)cancel.performClick();}});instrumentation.waitForIdleSync();
        ui(()->check(!(boolean)field(view,"sketchOrbit"),"9 choosing a drawing tool exits orbit mode and resumes drawing"));
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
        // "Do powierzchni…": rotate during the extrusion (one finger), tap the bottom face → length 20 mm.
        ui(()->{View panel=(View)field(view,"propertyEditor");List<EditText> inputs=new ArrayList<>();WorkspaceUiProbe.editors(panel,inputs);inputs.get(0).setText("5");WorkspaceUiProbe.button(panel,"Do powierzchni…").performClick();});
        ui(()->yaw0[0]=(float)field(view,"cameraYaw"));drag(ex,ey,ex-120,ey);
        ui(()->check(Math.abs((float)field(view,"cameraYaw")-yaw0[0])>5,"11 the view orbits while choosing extrusion profiles ("+describe(view)+")"));
        ui(()->{TabletFlowProbe.set(view,"cameraYaw",152f);TabletFlowProbe.set(view,"cameraPitch",22f);call(view,"fitView",new Class<?>[0]);view.invalidate();});instrumentation.waitForIdleSync();
        float[] wall=visibleFace(view,0,1,0);TabletFlowProbe.tap(wall[0],wall[1]);Thread.sleep(300);
        ui(()->{View panel=(View)field(view,"propertyEditor");List<EditText> inputs=new ArrayList<>();WorkspaceUiProbe.editors(panel,inputs);
            check(field(view,"extrusionFacePick")!=null&&inputs.get(0).getText().toString().equals("5"),"11 a side wall (not parallel) is refused: "+field(view,"operationMessage"));});
        // Turn underneath the plate: at yaw -28 the top occludes this bottom point.
        // A screen tap must select the visible face, never a hidden face through the solid.
        ui(()->{TabletFlowProbe.set(view,"cameraYaw",152f);call(view,"fitView",new Class<?>[0]);view.invalidate();});instrumentation.waitForIdleSync();
        float[] floor=world(view,80,45,0);TabletFlowProbe.tap(floor[0],floor[1]);Thread.sleep(500);
        ui(()->{View panel=(View)field(view,"propertyEditor");List<EditText> inputs=new ArrayList<>();WorkspaceUiProbe.editors(panel,inputs);
            check(field(view,"extrusionFacePick")==null&&inputs.get(0).getText().toString().equals("20")&&!((CompoundButton)WorkspaceUiProbe.button(panel,"Odwróć kierunek")).isChecked()&&((Set<?>)field(view,"extrusionSelections")).size()==1,
                "11 tapping the bottom face sets the cut length to exactly 20 mm ("+inputs.get(0).getText()+", "+describe(view)+")");});
        TabletFlowProbe.click(apply[0]);TabletFlowProbe.pending(view);instrumentation.waitForIdleSync();
        double drilled=120000-Math.PI*25*20;v=volume(view);System.out.println("CUT "+v+" "+state(view));
        check(Math.abs(v-drilled)<1e-6,"11 through hole: volume 120000 − 500π = "+drilled+" ("+v+")");
        TabletFlowProbe.screenshot(activity,"scenario-04-hole");
        // 12. Fillet R3 on the vertical edge at (100,60): real tap on the edge.
        tool(view,"Wybierz");ui(()->{TabletFlowProbe.set(view,"cameraYaw",-28f);TabletFlowProbe.set(view,"cameraPitch",22f);call(view,"fitView",new Class<?>[0]);view.invalidate();});instrumentation.waitForIdleSync();
        tool(view,"Zaokrąglij");Thread.sleep(300);float[] edge=world(view,100,60,10);TabletFlowProbe.tap(edge[0],edge[1]);Thread.sleep(500);
        ui(()->{View panel=(View)field(view,"propertyEditor");check(panel!=null,"12 fillet panel open ("+describe(view)+")");InteractionFixProbe.input(panel).setText("3");apply[0]=WorkspaceUiProbe.button(panel,"Zastosuj");});
        TabletFlowProbe.click(apply[0]);instrumentation.waitForIdleSync();Thread.sleep(300);
        double filleted=drilled-(9-Math.PI*9/4)*20;v=volume(view);System.out.println("FILLET "+v+" "+state(view));
        check(Math.abs(v-filleted)<1e-6,"12 R3 on the tapped vertical edge: exact volume "+filleted+" ("+v+")");
        TabletFlowProbe.screenshot(activity,"scenario-05-fillet");
        // 13-16. Save, close the workspace, open it again from storage.
        String[] projectId={null};ui(()->{CadProject p=(CadProject)field(view,"project");projectId[0]=p.id;((ProjectRepository)field(view,"projectRepository")).save(p);});
        CadWorkspaceView[] reopened={null};
        ui(()->{Context context=new ContextWrapper(activity){@Override public File getFilesDir(){return root;}};
            CadWorkspaceView next=new CadWorkspaceView(context,null);FrameLayout host=new FrameLayout(activity);host.addView(next,new FrameLayout.LayoutParams(-1,-1));activity.setContentView(host);
            AlertDialog chooser=(AlertDialog)field(next,"projectChooser");if(chooser!=null)chooser.dismiss();
            call(next,"activateProject",new Class<?>[]{CadProject.class},new ProjectRepository(root).loadProject(projectId[0]));reopened[0]=next;});
        instrumentation.waitForIdleSync();CadWorkspaceView again=reopened[0];
        ui(()->{CadProject p=(CadProject)field(again,"project");check(p.name.equals("Scenariusz-16-3")&&p.committedFeatures.size()==2,"15 reopened project keeps history ("+describe(again)+")");});
        v=volume(again);check(Math.abs(v-filleted)<1e-6,"16 reopened geometry is exact ("+v+")");
        // 17-19. Edit the first sketch width 100 → 120 from the model tree; hole and fillet follow.
        ui(()->{List<?> rows=(List<?>)field(again,"modelTreeRows");int index=-1;for(int i=0;i<rows.size();i++){ModelTree.Row r=(ModelTree.Row)rows.get(i);if(r.sketch()&&r.entities.contains(rect[0].id))index=i;}
            check(index>=0,"17 model tree lists the base sketch");});
        // Select = edit: the base sketch lies on the bottom face, so turn the model to see it (only visible
        // sketch geometry is selectable), then one tap on its edge opens it with the rectangle selected.
        ui(()->{TabletFlowProbe.set(again,"cameraYaw",152f);TabletFlowProbe.set(again,"cameraPitch",22f);call(again,"fitView",new Class<?>[0]);again.invalidate();});instrumentation.waitForIdleSync();
        float[] edgePoint=world(again,50,60,0);
        TabletFlowProbe.tap(edgePoint[0],edgePoint[1]);Thread.sleep(400);
        ui(()->check(field(again,"mode").equals("Edytuj szkic")&&rect[0].id.equals(field(again,"selectedSketchId"))&&field(again,"propertyEditor")!=null,
            "17 one tap on the sketch in the model view opens it for editing with the element selected ("+describe(again)+")"));
        editDimension(again,rect[0].id,"width","120");
        tool(again,"Zakończ");Thread.sleep(300);
        double edited=filleted+20*60*20;v=volume(again);System.out.println("EDITED "+v+" "+state(again));
        check(Math.abs(v-edited)<1e-6,"18-19 base edit rebuilds plate, hole and R3 exactly ("+v+" vs "+edited+")");
        TabletFlowProbe.screenshot(activity,"scenario-06-edited");
        // 20-21. STEP export and re-import keep the exact B-Rep.
        File step=new File(root,"scenario.step");boolean[] exported={false};ui(()->exported[0]=again.exportStep(step.getAbsolutePath()));
        check(exported[0]&&step.length()>0,"20 STEP export written ("+step.length()+" B)");
        double[] stepProps=NativeCadEngine.bodyProperties(1,1,1,0,null,new double[0],step.getAbsolutePath());
        check(stepProps.length==7&&Math.abs(stepProps[0]-edited)<1e-4,"20 exported STEP volume equals the model ("+(stepProps.length==7?stepProps[0]:-1)+")");
        ui(()->again.importStep(step));instrumentation.waitForIdleSync();
        String[] source={null};ui(()->{CadProject p=(CadProject)field(again,"project");check(p.importedStep,"21 STEP re-imported as B-Rep ("+describe(again)+")");source[0]=((File)call(again,"importedSourceFile",new Class<?>[0])).getAbsolutePath();});
        double[] imported=NativeCadEngine.bodyProperties(1,1,1,0,null,new double[0],source[0]);
        check(imported.length==7&&Math.abs(imported[0]-edited)<1e-4&&imported[5]==1,"21 re-imported solid volume and solid count verified ("+(imported.length==7?imported[0]:-1)+")");
        TabletFlowProbe.screenshot(activity,"scenario-07-reimported");
        ui(activity::finish);System.out.println("SCENARIO 16.3 COMPLETE");
    }
}
