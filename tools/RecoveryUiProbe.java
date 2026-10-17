import android.content.*;
import android.graphics.*;
import android.view.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.*;

public final class RecoveryUiProbe {
    public static Context applicationContext;
    public static android.app.Instrumentation instrumentation;
    static void check(boolean ok,String label){WorkspaceHistoryProbe.check(ok,label);}
    static Object field(Object view,String name)throws Exception{return WorkspaceHistoryProbe.field(view,name);}
    static void run(String[] args)throws Exception {
        File root=new File(args[0],"recovery-ui-"+UUID.randomUUID());check(root.mkdirs(),"isolated recovery UI fixture");
        ProjectRepository repository=new ProjectRepository(root);CadProject p=new CadProject();repository.save(p);repository.save(p);
        Map<String,byte[]> before=AtomicSaveProbe.snapshot(root);
        File journal=new File(root,"projects/save-transaction.bin");Files.write(journal.toPath(),new byte[]{1,2,3});
        Context context=new ContextWrapper(applicationContext){@Override public File getFilesDir(){return root;}};
        ExtrusionManipulatorProbe.instrumentation=instrumentation;
        ExtrusionManipulatorProbe.ui(()->{
            CadWorkspaceView view=new CadWorkspaceView(context,null);android.widget.FrameLayout host=new android.widget.FrameLayout(context);host.addView(view,new android.widget.FrameLayout.LayoutParams(-1,-1));
            host.measure(View.MeasureSpec.makeMeasureSpec(2600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1700,View.MeasureSpec.EXACTLY));host.layout(0,0,2600,1700);
            Bitmap image=Bitmap.createBitmap(2600,1700,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));float density=(float)field(view,"density");
            check(((ProjectRepository)field(view,"projectRepository")).readOnly()&&((double[])field(view,"nativeMesh")).length>0,"damaged journal opens readable solid without startup crash");
            Object document=field(view,"project"),mesh=field(view,"nativeMesh");
            WorkspaceUiProbe.tap(view,320*density,80*density);
            check(field(view,"propertyEditor")==null&&field(view,"project")==document&&field(view,"mode").equals("Wybierz"),"read-only mode blocks modeling and sketch commands");
            WorkspaceHistoryProbe.invoke(view,"activateTopTool",String.class,"Szkic");
            check(field(view,"propertyEditor")==null&&field(view,"mode").equals("Wybierz"),"native toolbar command entry also respects read-only protection");
            float yaw=(float)field(view,"cameraYaw");WorkspaceUiProbe.swipe(view,1600,1400,800);
            check((float)field(view,"cameraYaw")!=yaw,"read-only model remains orbitable");
            WorkspaceUiProbe.tap(view,2600-50*density,40*density);check(field(view,"propertyEditor")!=null,"read-only mode retains export picker");
            WorkspaceUiProbe.button((View)field(view,"propertyEditor"),"Anuluj").performClick();
            ByteArrayOutputStream output=new ByteArrayOutputStream();view.writeBinaryStl(output);
            check(CadExchange.readStl(new ByteArrayInputStream(output.toByteArray())).length>0,"read-only model can be exported as real STL");
            check(field(view,"project")==document&&field(view,"nativeMesh")==mesh,"read-only interactions never replace document geometry");
            AtomicSaveProbe.same(before,root,"read-only view preserves every original project and recovery file");
            check(Arrays.equals(Files.readAllBytes(journal.toPath()),new byte[]{1,2,3}),"read-only view retains damaged journal for recovery");image.recycle();
        });
        System.out.println("ALL RECOVERY UI TESTS PASSED");
    }
}
