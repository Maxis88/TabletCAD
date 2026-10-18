import android.graphics.*;
import java.io.*;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.*;

/** Project library: solid previews and non-destructive deletion into the trash folder. */
public final class LibraryThumbnailProbe {
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("PASS "+text);}
    public static void main(String[] args)throws Exception {
        File root=new File(new File(args[0]).getParentFile(),"library-"+UUID.randomUUID());check(root.mkdirs(),"isolated library");
        ProjectRepository repository=new ProjectRepository(root);
        CadProject open=new CadProject();open.name="Otwarty";repository.save(open);
        CadProject other=new CadProject();other.name="Do usunięcia";other.width=50;other.height=30;other.depth=10;repository.save(other);
        double[] mesh=NativeCadEngine.evaluateModelChecked(50,30,10,0,null,new double[0],null);
        double[] triangles=new CadModelResult(mesh).mesh;
        Bitmap preview=ThumbnailRenderer.render(triangles,320,220);
        int background=preview.getPixel(2,2),centre=preview.getPixel(160,110);
        check(background!=centre,"thumbnail draws the shaded solid over the background");
        int distinct=0;Set<Integer> colours=new HashSet<>();for(int x=0;x<320;x+=4)for(int y=0;y<220;y+=4)colours.add(preview.getPixel(x,y));distinct=colours.size();
        check(distinct>=3,"thumbnail shows several shaded faces ("+distinct+" colours)");
        ByteArrayOutputStream png=new ByteArrayOutputStream();preview.compress(Bitmap.CompressFormat.PNG,100,png);
        repository.saveThumbnail(other.id,png.toByteArray());check(repository.thumbnailFile(other.id).length()>100,"thumbnail stored next to the project");
        try{repository.deleteProject(open.id,open.id);throw new AssertionError("open project deleted");}catch(IOException expected){System.out.println("PASS open project cannot be deleted: "+expected.getMessage());}
        repository.deleteProject(other.id,open.id);
        boolean listed=false;for(CadProject p:repository.listProjects())if(p.id.equals(other.id))listed=true;
        check(!listed,"deleted project disappears from the library");
        File[] trash=new File(root,"projects/trash").listFiles();
        check(trash!=null&&trash.length>=2,"document and preview moved to trash, not destroyed ("+(trash==null?0:trash.length)+" files)");
        check(!repository.thumbnailFile(other.id).isFile(),"preview removed with the project");
        System.out.println("ALL LIBRARY THUMBNAIL TESTS PASSED");
    }
}
