import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.*;

/** UI thread, isolated files: 0.74 sketch status pill, constraint menu entry and edge projection guard. */
public final class CadToolsUiProbe {
    public static Context applicationContext;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    static Object field(Object o,String name)throws Exception {return InteractionFixProbe.field(o,name);}
    static Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception {return InteractionFixProbe.call(o,name,types,args);}
    static void run(String[] args)throws Exception {
        File root=new File(args[0],"tools-"+UUID.randomUUID());check(root.mkdirs(),"isolated 0.74 fixture");
        Context context=new ContextWrapper(applicationContext){@Override public File getFilesDir(){return root;}};
        CadProject fixture=CadProject.blank();fixture.sketch.add(new SketchPrimitive("plate","Prostokąt",0,0,100,60,new double[]{100,60}));new ProjectRepository(root).save(fixture);
        CadWorkspaceView view=new CadWorkspaceView(context,null);FrameLayout host=new FrameLayout(context);host.addView(view,new FrameLayout.LayoutParams(-1,-1));
        host.measure(View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1500,View.MeasureSpec.EXACTLY));host.layout(0,0,2400,1500);
        call(view,"enterSketchEditor",new Class<?>[0]);Bitmap bitmap=Bitmap.createBitmap(2400,1500,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
        Object status=field(view,"constraintStatus");check(status!=null&&((NonlinearConstraints.Status)status).label().contains("niedowiązany: 2"),"dimensioned rectangle shows 2 free position freedoms: "+status);
        CadProject p=(CadProject)field(view,"project");
        p.coincidences.add(new SketchConstraints.Link("dimX","plate",0,"plate",0,0,0));p.coincidences.add(new SketchConstraints.Link("dimY","plate",0,"plate",0,0,0));
        view.draw(new Canvas(bitmap));check(((NonlinearConstraints.Status)field(view,"constraintStatus")).full(),"positioned rectangle is reported fully constrained");
        check(ToolbarCatalog.allows(true,"Rzutuj")&&ToolbarCatalog.allows(true,"Elipsa"),"projection and ellipse are sketch commands");
        call(view,"activateTopTool",new Class<?>[]{String.class},"Rzutuj");
        check(String.valueOf(field(view,"operationMessage")).contains("B-Rep")||String.valueOf(field(view,"operationMessage")).contains("płaszczyzn"),"projection without a solid is refused with a reason: "+field(view,"operationMessage"));
        System.out.println("ALL 0.74 UI TESTS PASSED");
    }
}
