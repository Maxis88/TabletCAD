import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.*;

/** UI thread, isolated files: standard views, real fit and large-model camera safety. */
public final class ViewCameraProbe {
    public static Context applicationContext;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    static Object field(Object o,String name)throws Exception {return InteractionFixProbe.field(o,name);}
    static Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception {return InteractionFixProbe.call(o,name,types,args);}
    /** minX,maxX,minY,maxY of projected triangles; asserts every vertex is in front of the eye. */
    static float[] extents(CadWorkspaceView view,Canvas canvas)throws Exception {
        view.draw(canvas);float[] e={Float.MAX_VALUE,-Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE};int count=0;
        for(Object t:(List<?>)field(view,"projectedTriangles")){double[] q=(double[])field(t,"q");float[] sx=(float[])field(t,"sx"),sy=(float[])field(t,"sy");
            for(int i=0;i<3;i++){if(!(q[i]>0)||!Float.isFinite(sx[i])||!Float.isFinite(sy[i]))throw new AssertionError("vertex behind camera or invalid: q="+q[i]);
                e[0]=Math.min(e[0],sx[i]);e[1]=Math.max(e[1],sx[i]);e[2]=Math.min(e[2],sy[i]);e[3]=Math.max(e[3],sy[i]);count++;}}
        if(count==0)throw new AssertionError("no visible triangles");return e;
    }
    static void tool(CadWorkspaceView view,String name)throws Exception {call(view,"activateTopTool",new Class<?>[]{String.class},name);}
    static void run(String[] args)throws Exception {
        File root=new File(args[0],"camera-"+UUID.randomUUID());check(root.mkdirs(),"isolated camera fixture");
        Context context=new ContextWrapper(applicationContext){@Override public File getFilesDir(){return root;}};
        new ProjectRepository(root).save(CadProject.blank());
        CadWorkspaceView view=new CadWorkspaceView(context,null);FrameLayout host=new FrameLayout(context);host.addView(view,new FrameLayout.LayoutParams(-1,-1));
        host.measure(View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1500,View.MeasureSpec.EXACTLY));host.layout(0,0,2400,1500);
        Canvas canvas=new Canvas(Bitmap.createBitmap(2400,1500,Bitmap.Config.ARGB_8888));float dp=context.getResources().getDisplayMetrics().density;
        CadProject big=new CadProject();big.width=2000;big.height=1000;big.depth=500;
        call(view,"activateProject",new Class<?>[]{CadProject.class},big);
        double radius=(double)field(view,"modelRadius");check(Math.abs(radius-0.5*Math.sqrt(2000*2000+1000*1000+500*500))<1e-6,"model radius from B-Rep mesh bounds");
        check((double)field(view,"cameraDistance")>=4*radius-1e-6,"large model moves the perspective camera back");
        tool(view,"Dopasuj");float[] e=extents(view,canvas);float right=(float)call(view,"viewportRight",new Class<?>[0]);
        check(e[0]>=224*dp-1&&e[1]<=right+1&&e[2]>=110*dp-1&&e[3]<=1500,"fit keeps a 2 m model inside the free viewport: "+Arrays.toString(e));
        tool(view,"Rzut prostokątny");check((boolean)field(view,"orthographicView"),"orthographic projection toggles on");
        tool(view,"Przód");e=extents(view,canvas);check((float)field(view,"cameraYaw")==0f&&(float)field(view,"cameraPitch")==0f,"front view orientation");
        check(Math.abs((e[1]-e[0])/(e[3]-e[2])-2.0)<0.004,"orthographic front shows exact X:Y = 2000:1000");
        tool(view,"Góra");e=extents(view,canvas);check(Math.abs((e[1]-e[0])/(e[3]-e[2])-4.0)<0.008,"orthographic top shows exact X:Z = 2000:500");
        tool(view,"Prawy bok");e=extents(view,canvas);check(Math.abs((e[1]-e[0])/(e[3]-e[2])-0.5)<0.001,"orthographic right side shows exact Z:Y = 500:1000");
        tool(view,"Izometryczny");e=extents(view,canvas);check(Math.abs((float)field(view,"cameraYaw")+45f)<1e-4&&Math.abs((float)field(view,"cameraPitch")-35.26439f)<1e-3,"isometric orientation");
        check(e[0]>=224*dp-1&&e[1]<=right+1,"isometric view fits the viewport");
        tool(view,"Rzut prostokątny");check(!(boolean)field(view,"orthographicView"),"perspective projection restored");
        CadProject small=new CadProject();small.width=40;small.height=30;small.depth=10;call(view,"activateProject",new Class<?>[]{CadProject.class},small);
        check((double)field(view,"cameraDistance")==420.0,"small parts keep the original camera distance");
        tool(view,"Dopasuj");extents(view,canvas);check((float)field(view,"zoom")>1f,"fit enlarges a small part");
        // Axis manipulator math uses the same camera distance as the renderer.
        double[] origin={0,0,0},axis={0,0,1},center={0,0,0};
        for(double distance:new double[]{420,5000}){
            double scale=4,yaw=-30,pitch=25,t=37.5;double[] p={origin[0]+axis[0]*t,origin[1]+axis[1]*t,origin[2]+axis[2]*t};
            double y=Math.toRadians(yaw),x=Math.toRadians(pitch);double rx=p[0]*Math.cos(y)+p[2]*Math.sin(y),rz=-p[0]*Math.sin(y)+p[2]*Math.cos(y);
            double ry=p[1]*Math.cos(x)-rz*Math.sin(x),fz=p[1]*Math.sin(x)+rz*Math.cos(x),k=distance/(distance-fz);
            double back=AxisDrag.parameter(origin,axis,center,yaw,pitch,scale,900,600,900+rx*scale*k,600-ry*scale*k,distance);
            check(Math.abs(back-t)<1e-6,"axis drag inverts projection at camera distance "+distance);
        }
        System.out.println("ALL CAMERA VIEW TESTS PASSED");
    }
}
