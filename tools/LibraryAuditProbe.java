import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.*;

/**
 * Report-only audit of the user's project library on the device: the app's files are copied to
 * the cache first and every project is opened and rebuilt from the copy. Never fails the suite.
 */
public final class LibraryAuditProbe {
    public static Context applicationContext;
    static void copy(File from,File to)throws IOException {
        if(from.isDirectory()){to.mkdirs();File[] children=from.listFiles();if(children!=null)for(File c:children)copy(c,new File(to,c.getName()));}
        else Files.copy(from.toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
    static void run(String[] args)throws Exception {
        File source=applicationContext.getFilesDir();File root=new File(args[0],"library-audit-"+UUID.randomUUID());
        try{copy(source,root);}catch(IOException e){System.out.println("AUDIT copy failed: "+e.getMessage());return;}
        Context context=new ContextWrapper(applicationContext){@Override public File getFilesDir(){return root;}};
        ProjectRepository repository=new ProjectRepository(root);List<CadProject> projects=repository.listProjects();
        System.out.println("AUDIT projects="+projects.size()+" readOnly="+repository.readOnly());
        CadWorkspaceView view=new CadWorkspaceView(context,null);FrameLayout host=new FrameLayout(context);host.addView(view,new FrameLayout.LayoutParams(-1,-1));
        host.measure(View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1500,View.MeasureSpec.EXACTLY));host.layout(0,0,2400,1500);
        Canvas canvas=new Canvas(Bitmap.createBitmap(2400,1500,Bitmap.Config.ARGB_8888));int ok=0,failed=0;
        for(CadProject listed:projects) {
            String line;
            try {
                CadProject p=repository.loadProject(listed.id);long start=System.nanoTime();
                InteractionFixProbe.call(view,"activateProject",new Class<?>[]{CadProject.class},p);view.draw(canvas);
                long ms=(System.nanoTime()-start)/1000000;double[] mesh=(double[])InteractionFixProbe.field(view,"nativeMesh");
                String info=String.valueOf(InteractionFixProbe.field(view,"modelInfo")),message=String.valueOf(InteractionFixProbe.field(view,"operationMessage"));
                boolean broken=p.hasSolid()&&p.importedMeshSource==null&&mesh.length==0;
                if(broken)failed++;else ok++;
                line=(broken?"BROKEN ":"OK ")+p.name+" features="+p.committedFeatures.size()+" sketch="+p.sketch.size()+" triangles="+mesh.length/13+" "+ms+" ms | "+info+" | "+message;
            }catch(Throwable error){failed++;Throwable cause=error instanceof java.lang.reflect.InvocationTargetException?error.getCause():error;line="ERROR "+listed.name+": "+cause;}
            System.out.println("AUDIT "+line);
        }
        System.out.println("AUDIT summary ok="+ok+" failed="+failed);
    }
}
