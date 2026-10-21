import java.io.File;
import java.nio.file.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/**
 * Device only: every project document in the diagnostics copy of the library
 * (Android/data/pl.tabletcad.app/files/projects-copy) opens and evaluates; times are printed.
 */
public final class DeviceDocumentsProbe {
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] r=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,r,at,f.length);at+=f.length;}return r;}
    public static void main(String[] a)throws Exception {
        File dir=new File("/sdcard/Android/data/pl.tabletcad.app/files/projects-copy");File[] files=dir.listFiles();
        if(files==null){System.out.println("PASS DeviceDocumentsProbe: no diagnostics copy");return;}
        StringBuilder bad=new StringBuilder();
        for(File f:files){if(!f.getName().startsWith("document-")||!f.getName().endsWith(".tcad"))continue;
            CadProject p=CadProject.fromJson(new org.json.JSONObject(new String(Files.readAllBytes(f.toPath()),"UTF-8")));
            CadProject e=SketchTransactions.active(p)?SketchTransactions.baseline(p):p;if(!e.hasSolid())continue;
            long t=System.nanoTime();double[] r=NativeCadEngine.evaluateModel(e.width,e.height,e.depth,e.importedStep?0:e.filletRadius,e.featureBase,BodyGraph.pack(e,packed(e)),null);
            System.out.println("DOC "+f.getName()+" features "+p.committedFeatures.size()+" evaluated in "+(System.nanoTime()-t)/1000000+" ms result "+r.length);
            if(r.length==0)bad.append(' ').append(f.getName()).append(": ").append(NativeCadEngine.lastFailure().getMessage());}
        if(bad.length()>0)throw new AssertionError("documents fail to evaluate:"+bad);
        System.out.println("PASS DeviceDocumentsProbe");
    }
}
