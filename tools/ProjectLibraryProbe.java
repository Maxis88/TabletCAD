import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.*;

public final class ProjectLibraryProbe {
    static void require(boolean v,String label){if(!v)throw new AssertionError(label);System.out.println("PASS "+label);}
    static void broken(File f)throws Exception{Files.write(f.toPath(),"broken JSON".getBytes(StandardCharsets.UTF_8));}
    public static void main(String[] args)throws Exception {
        File root=new File(args[0],"project-library-"+UUID.randomUUID());require(root.mkdirs(),"isolated repository fixture");
        ProjectRepository repository=new ProjectRepository(root);CadProject a=repository.loadOrCreate();a.name="Projekt A — ąę";
        require(a.emptyDocument&&!a.hasSolid(),"new document has no phantom solid");
        SketchPrimitive guide=new SketchPrimitive("guide","Pomocnicza",0,0,10,0);
        a.sketch.add(guide);a.coincidences.add(SketchConstraints.Link.axis("guide",0,1,true));a.coincidences.add(SketchConstraints.Link.fixed(guide,0));
        PolylineDraft.add(a.polylineDraft,1,2);PolylineDraft.add(a.polylineDraft,4,5);repository.save(a);
        require(!a.hasSolid(),"sketch and unfinished draft do not create solid");
        CadProject b=CadProject.blank();b.name="Projekt B";b.featureBase=new double[]{0,0,0,10,10,0,-5};b.featureBaseProfileId="base";
        b.sketch.add(new SketchPrimitive("base","Prostokąt",0,0,10,10));
        require(b.hasSolid(),"first extrusion marks blank document as solid");repository.save(b);
        require(repository.listProjects().size()==2,"separate project snapshots retained");
        require(repository.loadOrCreate().id.equals(b.id),"autosave selects last active document");
        CadProject reopened=repository.loadProject(a.id);
        require(reopened.name.equals(a.name)&&reopened.polylineDraft.size()==2&&reopened.coincidences.size()==2,"switch restores Unicode name draft and constraints");
        require(repository.loadProject(b.id).featureBase[6]==-5,"other project retains reversed base");
        File folder=new File(root,"projects"),autosave=new File(folder,"autosave.tcad");
        broken(autosave);require(repository.loadOrCreate().id.equals(a.id),"corrupt autosave falls back to valid recovery copy");
        b.name="Projekt B2";repository.save(b);
        require(folder.listFiles((d,n)->n.startsWith("unreadable-")).length>0,"unreadable autosave preserved before replacement");
        File source=new File(folder,"source-retained.step");Files.write(source.toPath(),new byte[]{1,2,3,4});
        a.importedStep=true;a.importedStepSource=source.getName();repository.save(a);repository.save(b);
        require(Arrays.equals(Files.readAllBytes(source.toPath()),new byte[]{1,2,3,4}),"switching never overwrites imported source asset");
        require(repository.loadProject(a.id).importedStepSource.equals(source.getName()),"source reference stays with its document");
        broken(new File(folder,"document-"+b.id+".tcad"));
        require(repository.loadProject(b.id).id.equals(b.id),"individual document recovers from its own backup");
        Files.write(new File(folder,"document-"+b.id+".tcad").toPath(),a.toJson().toString().getBytes(StandardCharsets.UTF_8));
        require(repository.loadProject(b.id).id.equals(b.id),"mismatched document identity cannot open another project");
        repository.save(b);broken(new File(folder,"document-"+b.id+".tcad"));
        require(repository.loadProject(b.id).id.equals(b.id),"foreign identity does not replace last valid recovery snapshot");
        broken(autosave);broken(new File(folder,"autosave.tcad.bak"));
        require(repository.loadOrCreate().hasSolid(),"library fallback recovers when both active pointers corrupt");
        try{repository.loadProject("../outside");throw new AssertionError();}catch(IllegalArgumentException expected){System.out.println("PASS path traversal rejected");}
        File oversized=new File(folder,"document-oversized.tcad");try(RandomAccessFile f=new RandomAccessFile(oversized,"rw")){f.setLength(17L*1024*1024);}
        require(repository.listProjects().size()==2,"oversized snapshot rejected without loading arbitrary bytes");
        CadProject legacy=new CadProject();require(legacy.hasSolid(),"legacy default cube semantics retained");
        File legacyRoot=new File(root,"legacy"),legacyFolder=new File(legacyRoot,"projects");require(legacyFolder.mkdirs(),"legacy fixture folder");
        org.json.JSONObject legacyJson=legacy.toJson();legacyJson.put("schema",20);legacyJson.remove("emptyDocument");
        Files.write(new File(legacyFolder,"autosave.tcad").toPath(),legacyJson.toString().getBytes(StandardCharsets.UTF_8));
        ProjectRepository migrated=new ProjectRepository(legacyRoot);CadProject old=migrated.loadOrCreate();migrated.save(old);
        require(old.id.equals(legacy.id)&&old.hasSolid()&&migrated.listProjects().size()==1,"legacy autosave migrates to library without replacing document");
        File activeFile=new File(legacyFolder,"autosave.tcad");
        File savedFile=new File(legacyFolder,"document-"+old.id+".tcad");
        byte[] activeBefore=Files.readAllBytes(activeFile.toPath()),savedBefore=Files.readAllBytes(savedFile.toPath());
        File activeBackup=new File(legacyFolder,"autosave.tcad.bak");
        byte[] backupBefore=Files.readAllBytes(activeBackup.toPath());
        old.featureBase=new double[]{0,1,2};
        boolean rejected=false;
        try{migrated.save(old);}catch(IllegalStateException expected){rejected=true;}
        require(rejected,"unreadable in-memory feature graph rejected before save");
        require(Arrays.equals(activeBefore,Files.readAllBytes(activeFile.toPath()))
            &&Arrays.equals(savedBefore,Files.readAllBytes(savedFile.toPath()))
            &&Arrays.equals(backupBefore,Files.readAllBytes(activeBackup.toPath())),
            "rejected save preserves active document library snapshot and recovery bytes");
        require(migrated.loadOrCreate().id.equals(old.id),"last readable model still opens after rejected save");
        System.out.println("ALL PROJECT LIBRARY TESTS PASSED");
    }
}
