import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import pl.tabletcad.app.MaterialDensity;
import pl.tabletcad.app.SnapshotHistory;
import pl.tabletcad.app.model.CadProject;
import pl.tabletcad.app.storage.*;

/** Host checks for mass units and actual document/library codecs. No JNI or Android UI. */
public final class BodyMassHostTest {
    interface Work { void run() throws Exception; }
    static int checks;
    static void check(boolean value,String label) {
        if(!value) throw new AssertionError(label);
        checks++;System.out.println("PASS "+label);
    }
    static void near(double actual,double expected,String label) {
        check(Math.abs(actual-expected)<=Math.max(1e-18,Math.abs(expected)*1e-12),label);
    }
    static void rejects(Work action,String label)throws Exception {
        try { action.run(); }
        catch(IllegalArgumentException|JSONException expected){check(true,label);return;}
        throw new AssertionError(label);
    }
    static CadProject read(File file)throws Exception {
        return CadProject.fromJson(new JSONObject(Files.readString(file.toPath())));
    }
    public static void main(String[] args)throws Exception {
        near(MaterialDensity.massKg(1_000_000_000,1000),1000,"one cubic metre at 1000 kg/m3");
        near(MaterialDensity.massKg(1000,1000),.001,"one cubic centimetre at 1000 kg/m3");
        near(MaterialDensity.massKg(1,7850),.00000785,"one cubic millimetre retains small mass");
        near(MaterialDensity.massKg(80*48*32,7850),.964608,"80 x 48 x 32 mm reference block");
        near(MaterialDensity.massKg(600,1500),.0009,"mass follows net cut volume");
        near(MaterialDensity.massKg(0,1500),0,"empty volume has zero mass");
        near(MaterialDensity.massKg(1000+2000,1500),MaterialDensity.massKg(1000,1500)+MaterialDensity.massKg(2000,1500),"homogeneous disconnected solids are additive");
        for(double volume:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY})
            rejects(()->MaterialDensity.massKg(volume,1000),"invalid volume rejected: "+volume);
        for(double density:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            rejects(()->MaterialDensity.massKg(1000,density),"invalid or missing density rejected: "+density);
        rejects(()->MaterialDensity.massKg(Double.MAX_VALUE,Double.MAX_VALUE),"overflow rejected");
        rejects(()->MaterialDensity.massKg(Double.MIN_VALUE,1),"underflow rejected");
        near(MaterialDensity.parse(" 7850,25 "),7850.25,"Polish decimal comma");
        near(MaterialDensity.parse("7850.25"),7850.25,"decimal point");
        near(MaterialDensity.parse(",5"),.5,"fraction below one");
        near(MaterialDensity.parse("  "),0,"blank input clears assignment");
        for(String value:new String[]{"0","-1","NaN","Infinity","1e3","7 850","1,000.5","1.2.3","0,0","1."})
            rejects(()->MaterialDensity.parse(value),"invalid input rejected: "+value);

        CadProject p=new CadProject();p.materialDensityKgM3=7850.25;
        JSONObject serialized=p.toJson();CadProject loaded=CadProject.fromJson(serialized);
        check(loaded.id.equals(p.id)&&loaded.sketchId.equals(p.sketchId),"document and sketch UUID preserved");
        near(loaded.materialDensityKgM3,p.materialDensityKgM3,"density survives schema 35 round trip");
        check(serialized.getInt("schema")==CadProject.CURRENT_SCHEMA&&CadProject.CURRENT_SCHEMA>=35,"new format is versioned");
        for(int schema:new int[]{1,29,34}) {
            JSONObject old=new JSONObject(serialized.toString());old.put("schema",schema);old.remove("materialDensityKgM3");
            near(CadProject.fromJson(old).materialDensityKgM3,0,"legacy schema "+schema+" opens without inventing density");
        }
        for(Object value:new Object[]{-1,"7850",JSONObject.NULL,Double.POSITIVE_INFINITY}) {
            rejects(()->{JSONObject bad=new JSONObject(serialized.toString());bad.put("materialDensityKgM3",value);CadProject.fromJson(bad);},"invalid serialized density rejected: "+value);
        }
        for(double value:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            rejects(()->{CadProject bad=new CadProject();bad.materialDensityKgM3=value;bad.toJson();},"invalid in-memory density not serialized: "+value);
        }
        CadProject before=CadProject.fromJson(serialized);before.materialDensityKgM3=0;
        String baseline=before.toJson().toString(),current=serialized.toString();
        Deque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();undo.push(baseline);
        final CadProject[] active={loaded};
        SnapshotHistory.move(undo,redo,current,s->active[0]=CadProject.fromJson(new JSONObject(s)));
        near(active[0].materialDensityKgM3,0,"undo restores missing density");
        SnapshotHistory.move(redo,undo,baseline,s->active[0]=CadProject.fromJson(new JSONObject(s)));
        near(active[0].materialDensityKgM3,7850.25,"redo restores assigned density");
        try {SnapshotHistory.move(undo,redo,current,s->{throw new IOException("injected save failure");});throw new AssertionError("failure missing");}
        catch(IOException expected){check(undo.size()==1&&redo.isEmpty()&&active[0].materialDensityKgM3==7850.25,"failed history restore retains current density and stacks");}

        File root=new File(args[0],"mass-host-"+UUID.randomUUID());check(root.mkdirs(),"isolated library fixture");
        Files.writeString(new File(root,"document-"+p.id+".tcad").toPath(),serialized.toString(),StandardCharsets.UTF_8);
        ProjectLibraryFiles library=new ProjectLibraryFiles(root,new ProjectDocumentCodec());
        ProjectLibraryFiles.Info copy=library.duplicate(p.id,"Kopia z gęstością");
        near(read(new File(root,"document-"+copy.id+".tcad")).materialDensityKgM3,p.materialDensityKgM3,"library copy retains density");
        File trash=new File(root,"trash");trash.mkdirs();String token="12345-document-"+p.id+".tcad";
        Files.move(new File(root,"document-"+p.id+".tcad").toPath(),new File(trash,token).toPath());
        library.restore(token,false,null);
        near(read(new File(root,"document-"+p.id+".tcad")).materialDensityKgM3,p.materialDensityKgM3,"trash restore retains density");
        System.out.println("PASS BodyMassHostTest: "+checks+" checks");
    }
}
