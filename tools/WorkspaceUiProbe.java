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
import pl.tabletcad.app.storage.ProjectRepository;

/** Android view rendering and real MotionEvent dispatch with isolated documents. */
public final class WorkspaceUiProbe {
    public static Context applicationContext;
    static Object field(Object view,String name)throws Exception{return WorkspaceHistoryProbe.field(view,name);}
    static void set(Object view,String name,Object value)throws Exception{WorkspaceHistoryProbe.set(view,name,value);}
    static void call(Object view,String name)throws Exception{WorkspaceHistoryProbe.call(view,name);}
    static void check(boolean ok,String label){WorkspaceHistoryProbe.check(ok,label);}
    static float result(Object view,String name)throws Exception {
        Method m=CadWorkspaceView.class.getDeclaredMethod(name);m.setAccessible(true);return ((Number)m.invoke(view)).floatValue();
    }
    static boolean visible(Object view)throws Exception {
        Method m=CadWorkspaceView.class.getDeclaredMethod("propertiesVisible");m.setAccessible(true);return (boolean)m.invoke(view);
    }
    static void event(CadWorkspaceView view,long start,long time,int action,float x,float y) {
        MotionEvent e=MotionEvent.obtain(start,time,action,x,y,0);view.onTouchEvent(e);e.recycle();
    }
    static void tap(CadWorkspaceView view,float x,float y) {
        long now=SystemClock.uptimeMillis();event(view,now,now,MotionEvent.ACTION_DOWN,x,y);event(view,now,now+40,MotionEvent.ACTION_UP,x,y);
    }
    static void swipe(CadWorkspaceView view,float x1,float x2,float y) {
        long now=SystemClock.uptimeMillis();event(view,now,now,MotionEvent.ACTION_DOWN,x1,y);
        for(int i=1;i<=8;i++)event(view,now,now+i*20,MotionEvent.ACTION_MOVE,x1+(x2-x1)*i/8,y);
        event(view,now,now+180,MotionEvent.ACTION_UP,x2,y);
    }
    static void editors(View view,List<EditText> result) {
        if(view instanceof EditText)result.add((EditText)view);
        if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++)editors(((android.view.ViewGroup)view).getChildAt(i),result);
    }
    static Button button(View view,String label) {
        if(view instanceof Button&&((Button)view).getText().toString().equals(label))return (Button)view;
        if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++) {
            Button found=button(((android.view.ViewGroup)view).getChildAt(i),label);if(found!=null)return found;
        }return null;
    }
    public static void main(String[] args)throws Exception {
        try{run(args);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}System.exit(0);
    }
    static void run(String[] args)throws Exception {
        if(applicationContext==null)throw new IllegalStateException("Run WorkspaceUiProbe through WorkspaceInstrumentation, not app_process");
        Context base=applicationContext;
        File root=new File(args[0],"ui-"+UUID.randomUUID());check(root.mkdirs(),"isolated view fixture");
        Context isolated=new ContextWrapper(base){@Override public File getFilesDir(){return root;}};
        new ProjectRepository(root).save(new CadProject());CadWorkspaceView view=new CadWorkspaceView(isolated,null);
        FrameLayout host=new FrameLayout(isolated);host.addView(view,new FrameLayout.LayoutParams(-1,-1));
        host.measure(View.MeasureSpec.makeMeasureSpec(2200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1400,View.MeasureSpec.EXACTLY));host.layout(0,0,2200,1400);
        float density=(float)field(view,"density");
        check(!visible(view)&&result(view,"viewportRight")==2200,"idle model uses full width without property sidebar");
        Method inside=CadWorkspaceView.class.getDeclaredMethod("isInsideViewport",float.class,float.class);inside.setAccessible(true);
        check((boolean)inside.invoke(view,2190f,500f),"freed right region accepts model gestures");
        set(view,"mode","Edytuj szkic");check(visible(view)&&Math.abs(result(view,"viewportRight")-(2200-272*density))<.01,"sketch context reserves panel width");
        check(!(boolean)inside.invoke(view,2190f,500f),"sketch panel excluded from geometry hit-testing");set(view,"mode","Wybierz");
        CadProject project=(CadProject)field(view,"project");for(int i=0;i<30;i++) {
            project.committedFeatures.add(new double[]{0,0,0,0,5,5,i+1,0,0,32,1,0,0,0,1,0});project.committedProfileIds.add("");
        }
        Bitmap bitmap=Bitmap.createBitmap(2200,1400,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);int saves=canvas.getSaveCount();view.draw(canvas);
        check(canvas.getSaveCount()==saves,"drawing restores all canvas clipping state");
        float historyTop=result(view,"timelineTop"),historyRange=result(view,"maxTimelineScroll");
        int chrome=Color.rgb(20,26,33);
        check(bitmap.getPixel(1,1399)==chrome&&bitmap.getPixel(2198,1399)==chrome,"history reaches both bottom corners without a grey footer");
        set(view,"treeCollapsed",true);set(view,"mode","Edytuj szkic");view.draw(canvas);
        check(result(view,"timelineTop")==historyTop&&result(view,"maxTimelineScroll")==historyRange,"history bounds and scroll range are independent of both side panels");
        set(view,"treeCollapsed",false);set(view,"mode","Wybierz");
        call(view,"showProjectMenu");view.draw(canvas);
        android.widget.FrameLayout.LayoutParams panel=(android.widget.FrameLayout.LayoutParams)((View)field(view,"propertyEditor")).getLayoutParams();
        check(panel.topMargin+panel.height<=historyTop+1,"native menu ends above history");
        check(result(view,"maxTimelineScroll")==historyRange,"native menu does not shrink history");
        call(view,"closePropertyEditor");
        float treeOffset=(float)field(view,"treeScroll");
        swipe(view,210*density,120*density,1400-40*density);
        check((float)field(view,"timelineScroll")>0&&(float)field(view,"treeScroll")==treeOffset,"history swipe below tree scrolls history rather than tree");
        set(view,"timelineScroll",0f);
        float yaw=(float)field(view,"cameraYaw"),pitch=(float)field(view,"cameraPitch");
        float y=1400-40*density;swipe(view,2000,800,y);float offset=(float)field(view,"timelineScroll");
        check(offset>0&&(float)field(view,"cameraYaw")==yaw&&(float)field(view,"cameraPitch")==pitch,"timeline swipe scrolls features without orbiting camera");
        float x=400*density;int index=(int)((x-116*density+offset)/(64*density));
        CadTimeline.Item selected=CadTimeline.entries(project).get(index);check(selected.kind==CadTimeline.Kind.FEATURE,"scrolled test item is a model feature");
        tap(view,x,y);check(field(view,"propertyEditor")!=null&&visible(view),"single history tap selects and opens the native parameter editor");
        List<EditText> fields=new ArrayList<>();editors((View)field(view,"propertyEditor"),fields);
        check(fields.size()==5&&Math.abs(Double.parseDouble(fields.get(4).getText().toString().replace(',','.'))-project.committedFeatures.get(selected.featureIndex)[6])<.001,
            "scrolled icon edits correct feature rather than unscrolled index");
        Button cancel=button((View)field(view,"propertyEditor"),"Anuluj");check(cancel!=null,"native cancel action available");cancel.performClick();
        check(!visible(view)&&result(view,"viewportRight")==2200,"cancel hides editor and restores full viewport");
        swipe(view,800,22000,y);check((float)field(view,"timelineScroll")==0,"timeline clamps at first entry");
        swipe(view,2000,-20000,y);check(Math.abs((float)field(view,"timelineScroll")-result(view,"maxTimelineScroll"))<.01,"timeline clamps at last entry");
        set(view,"operationMessage","Testowy komunikat pozostaje widoczny po zamknięciu panelu.");view.draw(canvas);
        try(OutputStream output=new FileOutputStream(new File(root,"view.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}bitmap.recycle();
        File sketchRoot=new File(root,"spline");check(sketchRoot.mkdirs(),"isolated spline gesture fixture");
        Context sketchContext=new ContextWrapper(base){@Override public File getFilesDir(){return sketchRoot;}};
        new ProjectRepository(sketchRoot).save(CadProject.blank());
        CadWorkspaceView sketchView=new CadWorkspaceView(sketchContext,null);FrameLayout sketchHost=new FrameLayout(sketchContext);sketchHost.addView(sketchView);
        sketchHost.measure(View.MeasureSpec.makeMeasureSpec(2200,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1400,View.MeasureSpec.EXACTLY));sketchHost.layout(0,0,2200,1400);
        Method tool=CadWorkspaceView.class.getDeclaredMethod("activateTopTool",String.class);tool.setAccessible(true);tool.invoke(sketchView,"Szkic");
        Method plane=CadWorkspaceView.class.getDeclaredMethod("chooseSketchPlane",String.class);plane.setAccessible(true);plane.invoke(sketchView,"XY");tool.invoke(sketchView,"Splajn");
        Bitmap sketchBitmap=Bitmap.createBitmap(2200,1400,Bitmap.Config.ARGB_8888);Canvas sketchCanvas=new Canvas(sketchBitmap);sketchView.draw(sketchCanvas);
        Method projectPoint=CadWorkspaceView.class.getDeclaredMethod("projectSketchPoint",double.class,double.class);projectPoint.setAccessible(true);
        for(double[] uv:new double[][]{{-20,0},{0,20},{20,0}}){PointF point=(PointF)projectPoint.invoke(sketchView,uv[0],uv[1]);tap(sketchView,point.x,point.y);}
        CadProject draft=(CadProject)field(sketchView,"project");check(draft.splineDraft&&draft.polylineDraft.size()==3,"three real taps create persisted spline draft");
        Method finish=CadWorkspaceView.class.getDeclaredMethod("finishPolyline",boolean.class);finish.setAccessible(true);check((boolean)finish.invoke(sketchView,false),"spline saved through sketch transaction");
        check(draft.sketch.size()==1&&SketchSpline.is(draft.sketch.get(0)),"gesture produces spline rather than straight lines");
        sketchView.draw(sketchCanvas);call(sketchView,"finishSketchWork");check(!SketchTransactions.active((CadProject)field(sketchView,"project")),"spline sketch commits successfully");
        check(SketchSpline.is(new ProjectRepository(sketchRoot).loadOrCreate().sketch.get(0)),"spline survives actual autosave reload");
        call(sketchView,"showProjectMenu");View editor=(View)field(sketchView,"propertyEditor");check(Math.abs(editor.getLayoutParams().height-(result(sketchView,"timelineTop")-result(sketchView,"viewTop")))<1,"property shell fills column above history");call(sketchView,"closePropertyEditor");
        sketchView.draw(sketchCanvas);int sketchRow=-1;List<?> treeRows=(List<?>)field(sketchView,"modelTreeRows");
        for(int i=0;i<treeRows.size();i++)if(((ModelTree.Row)treeRows.get(i)).sketch())sketchRow=i;check(sketchRow>=0,"tree lists the committed sketch");
        java.lang.reflect.Method rowY=CadWorkspaceView.class.getDeclaredMethod("treeRowCenterY",int.class);rowY.setAccessible(true);
        tap(sketchView,80*density,(float)rowY.invoke(sketchView,sketchRow));
        check(field(sketchView,"mode").equals("Edytuj szkic"),"single sketch tree tap opens the sketch for editing");
        sketchBitmap.recycle();
        System.out.println("ALL WORKSPACE UI TESTS PASSED");
    }
}
