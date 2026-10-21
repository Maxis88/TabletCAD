import java.nio.file.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Diagnostics: opens the project document named by DOC, evaluates it like the app and prints timings. */
public final class OpenDocumentProbe {
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] r=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,r,at,f.length);at+=f.length;}return r;}
    public static void main(String[] a)throws Exception {
        String doc=System.getenv("DOC");if(doc==null){System.out.println("PASS OpenDocumentProbe (no DOC)");return;}
        long t0=System.nanoTime();CadProject p=CadProject.fromJson(new org.json.JSONObject(new String(Files.readAllBytes(Paths.get(doc)),"UTF-8")));
        System.out.println("parsed in "+(System.nanoTime()-t0)/1e6+" ms; features "+p.committedFeatures.size()+" hasSolid "+p.hasSolid()+" active tx "+SketchTransactions.active(p));
        CadProject e=SketchTransactions.active(p)?SketchTransactions.baseline(p):p;
        long t1=System.nanoTime();double[] packed=BodyGraph.pack(e,packed(e));System.out.println("packed in "+(System.nanoTime()-t1)/1e6+" ms, "+packed.length+" values");
        long t2=System.nanoTime();double[] r=NativeCadEngine.evaluateModel(e.width,e.height,e.depth,e.importedStep?0:e.filletRadius,e.featureBase,packed,null);
        System.out.println("evaluated in "+(System.nanoTime()-t2)/1e6+" ms, result "+r.length+(r.length==0?" failure: "+NativeCadEngine.lastFailure().getMessage():""));
        System.out.println("PASS OpenDocumentProbe");
    }
}
