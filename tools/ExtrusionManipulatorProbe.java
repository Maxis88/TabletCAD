import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.ProjectRepository;

/** Instrumented renderer + real gesture + OCCT preview/commit tests, on isolated documents. */
public final class ExtrusionManipulatorProbe {
    public static Context applicationContext;
    public static android.app.Instrumentation instrumentation;
    interface Step { void run() throws Exception; }
    static void ui(Step step)throws Exception {
        Throwable[] failed={null};instrumentation.runOnMainSync(()->{try{step.run();}catch(Throwable error){failed[0]=error;}});
        if(failed[0]!=null)throw new AssertionError("UI step failed",failed[0]);
    }
    static void awaitPreview(CadWorkspaceView view)throws Exception {
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        while(System.nanoTime()<end){boolean[] ready={false};ui(()->ready[0]=field(view,"featurePreview")!=null);if(ready[0])return;Thread.sleep(10);}
        throw new AssertionError("Background preview timeout");
    }
    static Object field(Object view,String name)throws Exception{return WorkspaceHistoryProbe.field(view,name);}
    static void set(Object view,String name,Object value)throws Exception{WorkspaceHistoryProbe.set(view,name,value);}
    static void check(boolean ok,String label){WorkspaceHistoryProbe.check(ok,label);}
    static void call(Object view,String name)throws Exception{WorkspaceHistoryProbe.call(view,name);}
    static void open(CadWorkspaceView view,double[] base,String id)throws Exception {
        Method m=CadWorkspaceView.class.getDeclaredMethod("showBaseExtrusionEditor",double[].class,String.class);m.setAccessible(true);m.invoke(view,base,id);
    }
    static double[] point(CadWorkspaceView view,double[] p)throws Exception {
        Method m=CadWorkspaceView.class.getDeclaredMethod("projectWorldPoint",double.class,double.class,double.class);m.setAccessible(true);return (double[])m.invoke(view,p[0],p[1],p[2]);
    }
    static void drag(CadWorkspaceView view,Canvas canvas,double desiredDelta,boolean cancel)throws Exception {
        view.draw(canvas);PointF handle=(PointF)field(view,"extrusionHandle");check(handle!=null,"visible extrusion arrow");
        Method parameter=CadWorkspaceView.class.getDeclaredMethod("extrusionParameter",float.class,float.class);parameter.setAccessible(true);
        double start=(double)parameter.invoke(view,handle.x,handle.y),factor=((CheckBox)field(view,"extrusionSymmetric")).isChecked()?2:1;
        double[] origin=(double[])field(view,"extrusionOrigin"),axis=(double[])field(view,"extrusionAxis");
        double t=start+desiredDelta/factor;double[] target=point(view,new double[]{origin[0]+axis[0]*t,origin[1]+axis[1]*t,origin[2]+axis[2]*t});
        long now=android.os.SystemClock.uptimeMillis();WorkspaceUiProbe.event(view,now,now,MotionEvent.ACTION_DOWN,handle.x,handle.y);
        for(int i=1;i<=6;i++)WorkspaceUiProbe.event(view,now,now+i*20,MotionEvent.ACTION_MOVE,
            (float)(handle.x+(target[0]-handle.x)*i/6),(float)(handle.y+(target[1]-handle.y)*i/6));
        WorkspaceUiProbe.event(view,now,now+140,cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,(float)target[0],(float)target[1]);
    }
    static double depth(CadWorkspaceView view)throws Exception {
        EditText input=(EditText)field(view,"extrusionDimension");double value=Double.parseDouble(input.getText().toString());
        return ((CheckBox)field(view,"extrusionReverse")).isChecked()?-value:value;
    }
    static CadProject document(int plane) {
        CadProject p=CadProject.blank();p.sketchPlane=plane==1?"XZ":plane==2?"YZ":"XY";p.setPrincipalSketchFrame(p.sketchPlane);
        SketchPrimitive profile=new SketchPrimitive("source","Prostokąt",0,0,40,30);p.sketch.add(profile);
        p.featureBase=new double[]{0,0,0,40,30,plane,20};p.featureBaseProfileId=profile.id;p.featureProfileId=profile.id;p.sketchExtruded=true;p.depth=20;
        return p;
    }
    static void run(String[] args)throws Exception {
        if(applicationContext==null||instrumentation==null)throw new IllegalStateException("Instrumentation required");
        File root=new File(args[0],"extrusion-"+UUID.randomUUID());check(root.mkdirs(),"isolated extrusion fixture");
        Context isolated=new ContextWrapper(applicationContext){@Override public File getFilesDir(){return root;}};
        ProjectRepository repository=new ProjectRepository(root);repository.save(document(0));
        CadWorkspaceView[] created={null};
        ui(()->{CadWorkspaceView view=new CadWorkspaceView(isolated,null);FrameLayout host=new FrameLayout(isolated);host.addView(view,new FrameLayout.LayoutParams(-1,-1));
            host.measure(View.MeasureSpec.makeMeasureSpec(2600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1700,View.MeasureSpec.EXACTLY));host.layout(0,0,2600,1700);created[0]=view;});
        CadWorkspaceView view=created[0];Bitmap bitmap=Bitmap.createBitmap(2600,1700,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
        java.util.concurrent.Callable<?>[] snapshot={null};
        ui(()->{double[] base={0,0,0,40,30,0,20};
            Method query=CadWorkspaceView.class.getDeclaredMethod("meshPreviewQuery",double[].class,double[].class,double.class,double.class);query.setAccessible(true);
            snapshot[0]=(java.util.concurrent.Callable<?>)query.invoke(view,base,new double[0],0.0,20.0);base[3]=400;});
        check(Math.abs(CurveTrimProbe.volume((double[])snapshot[0].call())-24000)<.01,"preview captures independent geometry arrays before background execution");
        for(int plane=0;plane<3;plane++)for(float[] camera:new float[][]{{-28,22},{45,-20},{0,0},{90,0}}) {
            final int selectedPlane=plane;Object[] before=new Object[4];
            ui(()->{CadProject p=document(selectedPlane);WorkspaceHistoryProbe.invoke(view,"activateProject",CadProject.class,p);
                set(view,"cameraYaw",camera[0]);set(view,"cameraPitch",camera[1]);view.draw(canvas);
                before[0]=field(view,"project");before[1]=((CadProject)before[0]).toJson().toString();before[2]=field(view,"nativeMesh");
                before[3]=Files.readAllBytes(new File(root,"projects/autosave.tcad").toPath());
                open(view,p.featureBase,p.featureBaseProfileId);float yaw=(float)field(view,"cameraYaw"),pitch=(float)field(view,"cameraPitch");
                drag(view,canvas,8,false);
                check(Math.abs(depth(view)-28)<.002,"axis gesture recovers 8 mm on plane "+selectedPlane+" camera "+Arrays.toString(camera));
                check((float)field(view,"cameraYaw")==yaw&&(float)field(view,"cameraPitch")==pitch,"arrow gesture never orbits camera");});
            awaitPreview(view);
            ui(()->{check(Math.abs(CurveTrimProbe.volume((double[])field(view,"featurePreview"))-40*30*28)<.01,"background gesture preview has correct OCCT volume");
                check(field(view,"project")==before[0]&&((CadProject)before[0]).toJson().toString().equals(before[1])&&field(view,"nativeMesh")==before[2]
                    &&Arrays.equals((byte[])before[3],Files.readAllBytes(new File(root,"projects/autosave.tcad").toPath())),"preview preserves document committed mesh and autosave");
                WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Anuluj").performClick();
                check(field(view,"extrusionDimension")==null&&field(view,"featurePreview")==null,"cancel removes manipulator and preview");});
        }
        ui(()->{CadProject p=document(0);WorkspaceHistoryProbe.invoke(view,"activateProject",CadProject.class,p);
            open(view,p.featureBase,p.featureBaseProfileId);drag(view,canvas,-25,false);check(Math.abs(depth(view)+5)<.002,"drag crosses sketch plane and reverses extrusion");});
        awaitPreview(view);
        ui(()->{WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Zastosuj").performClick();
            check(((CadProject)field(view,"project")).featureBase[6]==-5&&repository.loadOrCreate().featureBase[6]==-5,"reverse drag commits and reloads signed parameter");
            call(view,"undo");check(((CadProject)field(view,"project")).featureBase[6]==20,"undo reverses committed drag");
            call(view,"redo");check(((CadProject)field(view,"project")).featureBase[6]==-5,"redo restores committed drag");
            CadProject p=document(0);WorkspaceHistoryProbe.invoke(view,"activateProject",CadProject.class,p);open(view,p.featureBase,p.featureBaseProfileId);
            ((CheckBox)field(view,"extrusionSymmetric")).setChecked(true);drag(view,canvas,8,false);
            check(Math.abs(depth(view)-28)<.002,"symmetric gesture edits total length by twice endpoint motion");
            drag(view,canvas,12,true);check(Math.abs(depth(view)-28)<.002,"cancelled gesture restores its starting dimension");
            view.draw(canvas);RectF label=(RectF)field(view,"extrusionValueBounds");WorkspaceUiProbe.tap(view,label.centerX(),label.centerY());
            check(((EditText)field(view,"extrusionDimension")).isFocused(),"touching dimension focuses exact numeric entry");
            WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Anuluj").performClick();});
        for(String operation:new String[]{"add","cut"}) {
            ui(()->{CadProject face=new CadProject();face.sketchOriginZ=32;SketchPrimitive profile=new SketchPrimitive("face","Prostokąt",10,10,20,20);face.sketch.add(profile);
                WorkspaceHistoryProbe.invoke(view,"activateProject",CadProject.class,face);
                Method edit=CadWorkspaceView.class.getDeclaredMethod("showFaceFeatureDepth",SketchPrimitive.class,String.class);edit.setAccessible(true);edit.invoke(view,profile,operation);
                check(((double[])field(view,"extrusionAxis"))[2]==(operation.equals("cut")?-1:1),"feature arrow matches native "+operation+" direction");
                drag(view,canvas,3,false);check(Math.abs(depth(view)-13)<.002,"face feature gesture updates dimension");});
            awaitPreview(view);
            ui(()->{double expected=80*48*32+(operation.equals("cut")?-1:1)*100*13;
                check(Math.abs(CurveTrimProbe.volume((double[])field(view,"featurePreview"))-expected)<.01,"face "+operation+" background preview matches exact volume");
                WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Zastosuj").performClick();
                check(((CadProject)field(view,"project")).featureDepth==13,"face "+operation+" commits gesture parameter");});
        }
        java.util.concurrent.CountDownLatch started=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1),finished=new java.util.concurrent.CountDownLatch(1);
        boolean[] displayed={false};
        ui(()->{CadProject p=document(0);WorkspaceHistoryProbe.invoke(view,"activateProject",CadProject.class,p);open(view,p.featureBase,p.featureBaseProfileId);
            Method submit=CadWorkspaceView.class.getDeclaredMethod("submitGeometryPreview",ScrollView.class,java.util.concurrent.Callable.class,java.util.function.Consumer.class);submit.setAccessible(true);
            java.util.concurrent.Callable<double[]> blocked=()->{started.countDown();release.await();finished.countDown();return new double[]{0,0,0,1,0,0,0,1,0,0,0,1,1};};
            java.util.function.Consumer<double[]> display=mesh->displayed[0]=true;
            submit.invoke(view,field(view,"propertyEditor"),blocked,display);});
        check(started.await(5,java.util.concurrent.TimeUnit.SECONDS),"background preview started");
        ui(()->{call(view,"closePropertyEditor");check(release.getCount()==1,"UI cancels panel while geometry remains blocked on worker");});
        release.countDown();check(finished.await(5,java.util.concurrent.TimeUnit.SECONDS),"abandoned calculation finishes safely");
        instrumentation.waitForIdleSync();ui(()->check(!displayed[0]&&field(view,"featurePreview")==null,"closed tool never receives late geometry result"));
        for(int plane=0;plane<3;plane++)for(double second:new double[]{0,5}) {
            final int selectedPlane=plane;
            ui(()->{CadProject p=document(selectedPlane);p.featureBase=ExtrusionPlan.base(p,p.sketch.get(0),20,second,false,false);p.depth=20+second;p.extrusionExtents.put(-1,new double[]{20,second,0});
                WorkspaceHistoryProbe.invoke(view,"activateProject",CadProject.class,p);open(view,p.featureBase,p.featureBaseProfileId);
                drag(view,canvas,8,false);check(Math.abs(depth(view)-28)<.002,"current extent editor arrow changes first length on plane "+selectedPlane+", second="+second);});
            awaitPreview(view);
            ui(()->{check(Math.abs(CurveTrimProbe.volume((double[])field(view,"featurePreview"))-40*30*(28+second))<.02,"extent arrow preview preserves independent second length");WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Anuluj").performClick();});
        }
        for(String operation:new String[]{"add","cut"}) {
            ui(()->{CadProject p=document(0),plane=CadProject.blank();plane.sketchOriginZ=20;
                p.committedFeatures.add(ExtrusionPlan.feature(plane,new SketchPrimitive("detail","Prostokąt",10,10,20,20),operation,5,2,false,false));p.committedProfileIds.add("");p.extrusionExtents.put(0,new double[]{5,2,0});
                WorkspaceHistoryProbe.invoke(view,"activateProject",CadProject.class,p);
                Method editor=CadWorkspaceView.class.getDeclaredMethod("showExtrusionExtentEditor",int.class);editor.setAccessible(true);editor.invoke(view,0);
                check(Math.abs(((double[])field(view,"extrusionOrigin"))[2]-20)<1e-9,"history arrow restores source plane before independent second extent");
                check(((double[])field(view,"extrusionAxis"))[2]==(operation.equals("cut")?-1:1),"history extent arrow follows "+operation+" direction");drag(view,canvas,2,false);});
            awaitPreview(view);
            ui(()->{double expected=40*30*20+(operation.equals("cut")?-1:1)*100*7;
                check(Math.abs(CurveTrimProbe.volume((double[])field(view,"featurePreview"))-expected)<.02,"history "+operation+" arrow preview has correct volume");WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Anuluj").performClick();});
        }
        bitmap.recycle();System.out.println("ALL EXTRUSION MANIPULATOR TESTS PASSED");
    }
}
