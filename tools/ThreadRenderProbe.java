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

/** CPU render timings and real window orbit on isolated threaded B-Rep. */
public final class ThreadRenderProbe {
    public static Context applicationContext;
    public static android.app.Instrumentation instrumentation;
    static void ui(TabletFlowProbe.Step s)throws Exception{TabletFlowProbe.ui(s);}
    static void run(String[] args)throws Exception {
        TabletFlowProbe.instrumentation=instrumentation;CadScenarioProbe.instrumentation=instrumentation;
        CadProject p=new CadProject();p.width=40;p.height=30;p.depth=10;
        for(double x:new double[]{10,30}){p.committedFeatures.add(new double[]{1,1,x,15,x+2.5,15,10,0,0,10,1,0,0,0,1,0});p.committedProfileIds.add("");}
        double[] raw=new double[32];for(int i=0;i<2;i++)System.arraycopy(p.committedFeatures.get(i),0,raw,i*16,16);
        CadModelResult holes=new CadModelResult(NativeCadEngine.evaluateModel(40,30,10,0,null,raw,null));
        TabletFlowProbe.check(holes.holes.size()==2,"render fixture has two actual holes");
        BodyGraph.begin(p);
        for(HoleThread.Cylinder hole:holes.holes){p.committedFeatures.add(HoleThread.create(hole,HoleThread.nearest(5).get(0),10,false,false));p.committedProfileIds.add("");p.featureBodyTargets.add(hole.body);p.featureBodyOutputs.add(hole.body);}
        File root=new File(applicationContext.getCacheDir(),"render-"+UUID.randomUUID());root.mkdirs();
        Activity a=instrumentation.startActivitySync(new Intent().setClassName(applicationContext,"pl.tabletcad.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        CadWorkspaceView[] ref={null};
        ui(()->{CadWorkspaceView old=(CadWorkspaceView)TabletFlowProbe.field(a,"workspace");AlertDialog chooser=(AlertDialog)TabletFlowProbe.field(old,"projectChooser");if(chooser!=null)chooser.dismiss();
            Context c=new ContextWrapper(a){@Override public File getFilesDir(){return root;}};new ProjectRepository(root).save(p);
            CadWorkspaceView v=new CadWorkspaceView(c,null);ref[0]=v;FrameLayout host=new FrameLayout(a);host.addView(v,new FrameLayout.LayoutParams(-1,-1));a.setContentView(host);
            TabletFlowProbe.set(v,"cameraYaw",-28f);TabletFlowProbe.set(v,"cameraPitch",22f);
        });instrumentation.waitForIdleSync();CadWorkspaceView v=ref[0];
        String[] document={null};ui(()->document[0]=((CadProject)TabletFlowProbe.field(v,"project")).toJson().toString());
        ui(()->{double[] mesh=(double[])TabletFlowProbe.field(v,"nativeMesh");TabletFlowProbe.check(mesh.length>0,"threaded B-Rep has display data");
            Bitmap bitmap=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
            for(int i=0;i<4;i++){TabletFlowProbe.set(v,"cameraYaw",-28f+i*6);long start=System.nanoTime();v.draw(canvas);double ms=(System.nanoTime()-start)/1e6;
                System.out.println("RENDER triangles="+mesh.length/13+" visible="+((List<?>)TabletFlowProbe.field(v,"projectedTriangles")).size()+" frame="+i+" cpuMs="+ms);
                if(i==0)checkDisplayEdges(v);}
            bitmap.recycle();});
        ui(()->{TabletFlowProbe.call(v,"fitView",new Class<?>[0]);v.invalidate();});instrumentation.waitForIdleSync();
        float[] at=new float[2];ui(()->{int[] o=new int[2];v.getLocationOnScreen(o);at[0]=o[0]+v.getWidth()*.75f;at[1]=o[1]+v.getHeight()*.25f;});
        android.os.HandlerThread metricsThread=new android.os.HandlerThread("RenderProbeMetrics");metricsThread.start();
        List<Double> metrics=Collections.synchronizedList(new ArrayList<>());
        Window.OnFrameMetricsAvailableListener listener=(window,frame,dropped)->metrics.add(frame.getMetric(FrameMetrics.TOTAL_DURATION)/1e6);
        ui(()->a.getWindow().addOnFrameMetricsAvailableListener(listener,new android.os.Handler(metricsThread.getLooper())));
        for(int i=0;i<3;i++)CadScenarioProbe.drag(at[0],at[1],at[0]-180,at[1]+60);
        Thread.sleep(200);ui(()->a.getWindow().removeOnFrameMetricsAvailableListener(listener));metricsThread.quitSafely();
        List<Double> sorted; synchronized(metrics){sorted=new ArrayList<>(metrics);}Collections.sort(sorted);
        TabletFlowProbe.check(!sorted.isEmpty(),"real orbit produced Android frame metrics");
        System.out.println("ORBIT frames="+sorted.size()+" p50Ms="+sorted.get(sorted.size()/2)+" p95Ms="+sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*.95))));
        ui(()->{TabletFlowProbe.check(document[0].equals(((CadProject)TabletFlowProbe.field(v,"project")).toJson().toString()),"orbit leaves the parametric document unchanged");
            TabletFlowProbe.set(v,"cameraYaw",-28f);TabletFlowProbe.set(v,"cameraPitch",22f);v.invalidate();});
        TabletFlowProbe.screenshot(a,"thread-render-orbit");
        ui(a::finish);System.out.println("THREAD RENDER PROBE COMPLETE");
    }
    static void checkDisplayEdges(CadWorkspaceView view)throws Exception {
        List<?> triangles=(List<?>)TabletFlowProbe.field(view,"projectedTriangles");int n=triangles.size();
        double[][] x=new double[n][],y=new double[n][],z=new double[n][];int[] faces=new int[n];
        for(int i=0;i<n;i++){Object t=triangles.get(i);x[i]=(double[])TabletFlowProbe.field(t,"wx");y[i]=(double[])TabletFlowProbe.field(t,"wy");z[i]=(double[])TabletFlowProbe.field(t,"wz");faces[i]=(int)TabletFlowProbe.field(t,"faceId");}
        for(int a=0;a<n;a+=Math.max(1,n/60))for(int e=0;e<3;e++) {
            boolean expected=true;int end=(e+1)%3;
            for(int b=0;b<n&&expected;b++)if(a!=b&&faces[a]==faces[b])for(int j=0;j<3;j++) {
                int next=(j+1)%3;
                if((same(x,y,z,a,e,b,j)&&same(x,y,z,a,end,b,next))||(same(x,y,z,a,e,b,next)&&same(x,y,z,a,end,b,j))){expected=false;break;}
            }
            boolean actual=(boolean)TabletFlowProbe.call(view,"isVisibleCadEdge",new Class<?>[]{triangles.get(a).getClass(),int.class},triangles.get(a),e);
            if(actual!=expected)throw new AssertionError("display adjacency differs from exhaustive edge check, face="+faces[a]);
        }
        TabletFlowProbe.check(true,"cached display edges match exhaustive checks on the threaded B-Rep");
    }
    static boolean same(double[][] x,double[][] y,double[][] z,int a,int av,int b,int bv){return Math.abs(x[a][av]-x[b][bv])<1e-7&&Math.abs(y[a][av]-y[b][bv])<1e-7&&Math.abs(z[a][av]-z[b][bv])<1e-7;}
}
