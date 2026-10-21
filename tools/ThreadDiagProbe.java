import java.io.File;
import java.nio.file.*;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Device diagnostics: every thread of the owner's documents (diagnostics copy) — removed volume and mesh. Never fails. */
public final class ThreadDiagProbe {
    public static void main(String[] a)throws Exception {
        File[] files=new File("/sdcard/Android/data/pl.tabletcad.app/files/projects-copy").listFiles();if(files==null){System.out.println("PASS ThreadDiagProbe: no copy");return;}
        for(File f:files){if(!f.getName().startsWith("document-")||!f.getName().endsWith(".tcad"))continue;
            CadProject p=CadProject.fromJson(new org.json.JSONObject(new String(Files.readAllBytes(f.toPath()),"UTF-8")));if(SketchTransactions.active(p))p=SketchTransactions.baseline(p);
            for(int i=0;i<p.committedFeatures.size();i++){double[] t=p.committedFeatures.get(i);if(!HoleThread.is(t))continue;
                CadProject q=CadProject.fromJson(new org.json.JSONObject(p.toJson().toString()));
                List<double[]> keep=new ArrayList<>(q.committedFeatures.subList(0,i+1));
                double[] with=vol(q,keep);keep.set(i,FeatureHistory.placeholder());double[] without=vol(q,keep);
                double h=t[9]-t[8],exp=h*h/Math.sqrt(3)*2*Math.PI*(t[8]+h/2)*t[11]/t[10];
                System.out.println("THREAD "+f.getName()+" #"+i+" r="+t[8]+" R="+t[9]+" P="+t[10]+" L="+t[11]+" ext="+t[13]+" at "+t[2]+","+t[3]+","+t[4]+" dir "+t[5]+","+t[6]+","+t[7]
                    +" removed="+(with.length==7&&without.length==7?without[0]-with[0]:Double.NaN)+" ideal≈"+exp+(with.length==7?"":" FAIL "+NativeCadEngine.lastFailure().getMessage()));
            }}
        System.out.println("PASS ThreadDiagProbe");
    }
    static double[] vol(CadProject q,List<double[]> features){
        q.committedFeatures.clear();q.committedFeatures.addAll(features);while(q.committedProfileIds.size()>features.size())q.committedProfileIds.remove(q.committedProfileIds.size()-1);BodyGraph.reconcile(q);
        int n=0;for(double[] f:features)n+=f.length;double[] r=new double[n];int at=0;for(double[] f:features){System.arraycopy(f,0,r,at,f.length);at+=f.length;}
        return NativeCadEngine.bodyProperties(q.width,q.height,q.depth,q.importedStep?0:q.filletRadius,q.featureBase,BodyGraph.pack(q,r,true),null);
    }
}
