import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.ProjectRepository;

public final class EdgeGroupUiProbe {
    public static Context applicationContext;
    public static android.app.Instrumentation instrumentation;
    static Object field(Object view,String name)throws Exception{return WorkspaceHistoryProbe.field(view,name);}
    static void check(boolean ok,String label){WorkspaceHistoryProbe.check(ok,label);}
    static void ui(ExtrusionManipulatorProbe.Step step)throws Exception{ExtrusionManipulatorProbe.instrumentation=instrumentation;ExtrusionManipulatorProbe.ui(step);}
    static CadProject box(){CadProject p=new CadProject();p.width=40;p.height=30;p.depth=20;return p;}
    static void select(CadWorkspaceView view,double x,double y)throws Exception {
        double[] edges=(double[])field(view,"filletEdges");
        for(int i=0;i<edges.length;i+=16)if(Math.abs(edges[i]-x)<1e-6&&Math.abs(edges[i+1]-y)<1e-6
                &&Math.abs(edges[i+6]-x)<1e-6&&Math.abs(edges[i+7]-y)<1e-6&&Math.abs(edges[i+2]-edges[i+8])>19) {
            double[] screen=ExtrusionManipulatorProbe.point(view,new double[]{edges[i+3],edges[i+4],edges[i+5]});
            WorkspaceUiProbe.tap(view,(float)screen[0],(float)screen[1]);return;
        }throw new AssertionError("selection fixture edge absent");
    }
    static void run(String[] args)throws Exception {
        File root=new File(args[0],"edge-group-ui-"+UUID.randomUUID());check(root.mkdirs(),"isolated multi-edge UI fixture");
        Context context=new ContextWrapper(applicationContext){@Override public File getFilesDir(){return root;}};
        ProjectRepository repository=new ProjectRepository(root);repository.save(box());CadWorkspaceView[] created={null};
        ui(()->{CadWorkspaceView view=new CadWorkspaceView(context,null);FrameLayout host=new FrameLayout(context);host.addView(view,new FrameLayout.LayoutParams(-1,-1));
            host.measure(View.MeasureSpec.makeMeasureSpec(2600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1700,View.MeasureSpec.EXACTLY));host.layout(0,0,2600,1700);created[0]=view;});
        CadWorkspaceView view=created[0];Bitmap bitmap=Bitmap.createBitmap(2600,1700,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
        for(boolean chamfer:new boolean[]{false,true}) {
            ui(()->{WorkspaceHistoryProbe.invoke(view,"activateProject",CadProject.class,box());
                Method open=CadWorkspaceView.class.getDeclaredMethod("showEdgeTreatmentEditor",int.class,boolean.class);open.setAccessible(true);open.invoke(view,-1,chamfer);view.draw(canvas);
                select(view,40,30);check(((Map<?,?>)field(view,"filletSelections")).size()==1,"first edge selected by real touch event");
                select(view,40,30);check(((Map<?,?>)field(view,"filletSelections")).isEmpty(),"repeated touch deselects same edge");
                select(view,40,30);select(view,40,0);check(((Map<?,?>)field(view,"filletSelections")).size()==2,"two distinct visible edges selected together");});
            ExtrusionManipulatorProbe.awaitPreview(view);
            ui(()->{double expected=24000-2*(chamfer?.5:1-Math.PI/4)*9*20;
                double actual=CurveTrimProbe.volume((double[])field(view,"featurePreview"));
                // Render tessellation approximates the two circular quarters; exact B-Rep is checked below.
                check(Math.abs(actual-expected)<2,"multi-edge "+chamfer+" background preview volume: "+actual+" expected "+expected);
                check(((CadProject)field(view,"project")).committedFeatures.isEmpty(),"preview adds no premature history features");
                WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Zastosuj").performClick();
                CadProject p=(CadProject)field(view,"project");check(p.committedFeatures.size()==1&&EdgeGroup.is(p.committedFeatures.get(0))&&p.committedFeatures.get(0)[1]==2,
                    "two edges commit as exactly one grouped history feature");
                double[] initialProperties=NativeCadEngine.bodyProperties(p.width,p.height,p.depth,0,p.featureBase,p.committedFeatures.get(0),null);
                check(initialProperties.length==7&&Math.abs(initialProperties[0]-expected)<1e-5,"committed multi-edge B-Rep has exact analytic volume");
                check(repository.loadOrCreate().committedFeatures.size()==1,"group persists in native project");
                WorkspaceHistoryProbe.call(view,"undo");check(((CadProject)field(view,"project")).committedFeatures.isEmpty(),"one undo removes entire edge group");
                WorkspaceHistoryProbe.call(view,"redo");check(((CadProject)field(view,"project")).committedFeatures.size()==1,"one redo restores entire edge group");
                WorkspaceHistoryProbe.invoke(view,"showEdgeFilletEditor",int.class,0);
                List<EditText> fields=new ArrayList<>();WorkspaceUiProbe.editors((View)field(view,"propertyEditor"),fields);fields.get(0).setText("2");
                WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Zastosuj").performClick();
                double[] group=((CadProject)field(view,"project")).committedFeatures.get(0);
                check(group[11]==2&&EdgeGroup.members(group).stream().allMatch(edge->edge[11]==2),"history editor updates shared dimension of every selected edge");
                double exact=24000-2*(chamfer?.5:1-Math.PI/4)*4*20;
                CadProject saved=repository.loadOrCreate();double[] props=NativeCadEngine.bodyProperties(saved.width,saved.height,saved.depth,0,saved.featureBase,group,null);
                check(props.length==7&&Math.abs(props[0]-exact)<1e-5,"reloaded group retains exact analytic B-Rep volume");});
        }
        bitmap.recycle();System.out.println("ALL EDGE GROUP UI TESTS PASSED");
    }
}
