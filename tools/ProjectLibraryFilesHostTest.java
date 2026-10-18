import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.SketchTransactions;
import pl.tabletcad.app.storage.*;

/** Real .tcad schema and file operations on the host; no Android stubs are executed. */
public final class ProjectLibraryFilesHostTest {
    interface Work{void run()throws Exception;}
    static int checks;static long stamp=1000;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static void rejects(Work work,String label)throws Exception{try{work.run();}catch(IOException expected){check(true,label);return;}throw new AssertionError(label);}
    static byte[] bytes(CadProject p)throws Exception{return p.toJson().toString(2).getBytes(StandardCharsets.UTF_8);}
    static CadProject read(File file)throws Exception{return CadProject.fromJson(new JSONObject(Files.readString(file.toPath())));}
    static File doc(File root,String id){return new File(root,"document-"+id+".tcad");}
    static CadProject sample(String name){CadProject p=CadProject.blank();p.name=name;p.sketch.add(new SketchPrimitive("entity-preserved","Okrąg",10,10,15,10));return p;}
    static String archive(File root,CadProject p)throws Exception{File trash=new File(root,"trash");trash.mkdirs();String token=(stamp++)+"-document-"+p.id+".tcad";Files.write(new File(trash,token).toPath(),bytes(p));return token;}
    static long assetCount(File root){File[] files=root.listFiles((d,n)->n.startsWith("source-copy-")||n.startsWith("pending-"));return files==null?0:files.length;}
    public static void main(String[] args)throws Exception {
        File root=new File(args[0],"library-host-"+UUID.randomUUID());check(root.mkdirs(),"isolated host fixture");
        ProjectDocumentCodec codec=new ProjectDocumentCodec();ProjectLibraryFiles library=new ProjectLibraryFiles(root,codec);
        CadProject a=sample("Żółta część");byte[] source=bytes(a);Files.write(doc(root,a.id).toPath(),source);
        byte[] active=bytes(sample("Aktywny"));File autosave=new File(root,"autosave.tcad"),autosaveBackup=new File(root,"autosave.tcad.bak");Files.write(autosave.toPath(),active);Files.write(autosaveBackup.toPath(),active);
        check(library.listTrash().isEmpty(),"empty trash is readable");
        ProjectLibraryFiles.Info copy=library.duplicate(a.id,"  Żółta część — kopia  ");CadProject cloned=read(doc(root,copy.id));
        check(!cloned.id.equals(a.id)&&cloned.name.equals("Żółta część — kopia"),"duplicate has a new UUID and trimmed Unicode name");
        check(cloned.sketchId.equals(a.sketchId)&&cloned.sketch.get(0).id.equals(a.sketch.get(0).id),"copy retains internal sketch and entity references");
        check(Arrays.equals(source,Files.readAllBytes(doc(root,a.id).toPath())),"copy leaves source bytes unchanged");
        check(!library.duplicate(a.id,"Druga kopia").id.equals(copy.id),"repeated copies have different document UUIDs");
        rejects(()->library.duplicate(a.id,"  "),"empty copy name rejected");rejects(()->library.duplicate(a.id,"x".repeat(81)),"overlong copy name rejected");
        rejects(()->library.duplicate("../outside","copy"),"source traversal rejected");

        String conflict=archive(root,a);rejects(()->library.restore(conflict,false,null),"restore never overwrites an existing UUID");
        ProjectLibraryFiles.Info recovered=library.restore(conflict,true,"Odzyskana kopia");
        check(!recovered.id.equals(a.id)&&read(doc(root,recovered.id)).name.equals("Odzyskana kopia"),"conflicting archive restores as an independent copy");
        check(Files.exists(new File(root,"trash/"+conflict).toPath()),"archive bytes retained after recovery");
        check(library.listTrash().isEmpty(),"successful restore is removed from the visible trash list");
        rejects(()->library.restore(conflict,false,null),"repeated restore is rejected");

        CadProject original=sample("Do przywrócenia");String token=archive(root,original);ProjectLibraryFiles.Info restored=library.restore(token,false,null);
        check(restored.id.equals(original.id)&&Arrays.equals(bytes(original),Files.readAllBytes(doc(root,original.id).toPath())),"normal restore preserves UUID and exact serialized document");
        rejects(()->library.restore("../"+token,false,null),"trash token traversal rejected");
        CadProject backup=sample("Z kopii zapasowej");String backupToken=archive(root,backup);File backupMain=new File(root,"trash/"+backupToken);
        Files.move(backupMain.toPath(),new File(backupMain.getPath()+".bak").toPath());
        check(library.listTrash().stream().anyMatch(e->e.token.equals(backupToken)&&e.backup),"backup-only archive is discoverable");library.restore(backupToken,false,null);
        check(read(doc(root,backup.id)).id.equals(backup.id),"backup-only archive is recoverable");
        CadProject corrupt=sample("Uszkodzony główny zapis");String corruptToken=archive(root,corrupt);File corruptFile=new File(root,"trash/"+corruptToken);
        Files.copy(corruptFile.toPath(),new File(corruptFile.getPath()+".bak").toPath());Files.writeString(corruptFile.toPath(),"corrupt");library.restore(corruptToken,false,null);
        check(read(doc(root,corrupt.id)).name.equals(corrupt.name),"corrupt main archive falls back to its matching backup");
        CadProject foreign=sample("Inna tożsamość");String foreignToken=archive(root,foreign);Files.writeString(new File(root,"trash/"+foreignToken).toPath(),a.toJson().toString());
        rejects(()->library.restore(foreignToken,false,null),"foreign identity in an archive is rejected");
        check(!doc(root,foreign.id).exists(),"foreign archive creates no destination");
        CadProject occupied=sample("Istnieje tylko backup");String occupiedToken=archive(root,occupied);File occupiedBackup=new File(doc(root,occupied.id).getPath()+".bak");Files.write(occupiedBackup.toPath(),bytes(occupied));
        rejects(()->library.restore(occupiedToken,false,null),"existing backup also prevents overwriting a project identity");
        check(!doc(root,occupied.id).exists()&&Arrays.equals(bytes(occupied),Files.readAllBytes(occupiedBackup.toPath())),"backup collision preserves all existing bytes");

        for(boolean mesh:new boolean[]{false,true}) {
            CadProject imported=sample(mesh?"Import STL":"Import STEP");String asset=mesh?"mesh.stl":"source.step";byte[] payload={1,2,3,4,5};Files.write(new File(root,asset).toPath(),payload);
            if(mesh)imported.importedMeshSource=asset;else {imported.importedStep=true;imported.importedStepSource=asset;}
            Files.write(doc(root,imported.id).toPath(),bytes(imported));ProjectLibraryFiles.Info importedCopy=library.duplicate(imported.id,imported.name+" kopia");CadProject copyDoc=read(doc(root,importedCopy.id));
            String copyAsset=mesh?copyDoc.importedMeshSource:copyDoc.importedStepSource;
            check(!copyAsset.equals(asset)&&Arrays.equals(payload,Files.readAllBytes(new File(root,copyAsset).toPath())),"independent source asset copied for "+(mesh?"STL":"STEP"));
            Files.write(new File(root,copyAsset).toPath(),new byte[]{9});check(Arrays.equals(payload,Files.readAllBytes(new File(root,asset).toPath())),"editing a copied asset leaves original bytes unchanged");
            long count=assetCount(root);ProjectLibraryFiles failing=new ProjectLibraryFiles(root,codec,()->{throw new IOException("injected before publication");});
            rejects(()->failing.duplicate(imported.id,"failed copy"),"copy publication failure reported");check(assetCount(root)==count,"failed copy cleans only its own new assets");
            SketchTransactions.begin(imported);Files.write(doc(root,imported.id).toPath(),bytes(imported));
            rejects(()->library.duplicate(imported.id,"draft copy"),"active sketch transaction cannot be cloned with stale baseline identity");check(assetCount(root)==count,"rejected active sketch leaves no copied assets");
        }
        CadProject missing=sample("Brak źródła");missing.importedStep=true;missing.importedStepSource="missing.step";String missingToken=archive(root,missing);
        rejects(()->library.restore(missingToken,false,null),"missing source blocks restore before publication");check(!doc(root,missing.id).exists(),"missing source leaves archive unpublished");
        CadProject unsafe=sample("Niebezpieczna ścieżka");unsafe.importedStep=true;unsafe.importedStepSource="..";String unsafeToken=archive(root,unsafe);
        rejects(()->library.restore(unsafeToken,false,null),"unsafe source path rejected");
        CadProject interrupted=sample("Przerwany zapis");String interruptedToken=archive(root,interrupted);
        ProjectLibraryFiles failing=new ProjectLibraryFiles(root,codec,()->{throw new IOException("injected restore failure");});
        rejects(()->failing.restore(interruptedToken,false,null),"restore failure before publication reported");
        check(!doc(root,interrupted.id).exists()&&library.listTrash().stream().anyMatch(e->e.token.equals(interruptedToken)),"failed restore retains visible recoverable archive");

        CadProject concurrent=sample("Równoczesne przywrócenie");String concurrentToken=archive(root,concurrent);CountDownLatch start=new CountDownLatch(1);AtomicInteger ok=new AtomicInteger(),rejected=new AtomicInteger();List<Throwable> failures=Collections.synchronizedList(new ArrayList<>());
        Runnable restore=()->{try{start.await();new ProjectLibraryFiles(root,codec).restore(concurrentToken,false,null);ok.incrementAndGet();}catch(IOException expected){rejected.incrementAndGet();}catch(Throwable error){failures.add(error);}};
        Thread one=new Thread(restore),two=new Thread(restore);one.start();two.start();start.countDown();one.join();two.join();
        check(failures.isEmpty()&&ok.get()==1&&rejected.get()==1,"concurrent restores publish one document without replacing it");
        check(Arrays.equals(bytes(concurrent),Files.readAllBytes(doc(root,concurrent.id).toPath())),"concurrent restore retains exact valid document");
        CountDownLatch publicationEntered=new CountDownLatch(1),releasePublication=new CountDownLatch(1),recoveryAttempted=new CountDownLatch(1),recoveryDone=new CountDownLatch(1);
        ProjectLibraryFiles blocked=new ProjectLibraryFiles(root,codec,()->{publicationEntered.countDown();try{if(!releasePublication.await(5,TimeUnit.SECONDS))throw new IOException("release timeout");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException(error);}});
        Thread publication=new Thread(()->{try{blocked.duplicate(a.id,"Kopia podczas odtwarzania");}catch(Throwable error){failures.add(error);}});
        Thread recovery=new Thread(()->{recoveryAttempted.countDown();try{new AtomicSavePair(root,i->{}).recover();}catch(Throwable error){failures.add(error);}finally{recoveryDone.countDown();}});
        publication.start();check(publicationEntered.await(3,TimeUnit.SECONDS),"library publication checkpoint entered");recovery.start();recoveryAttempted.await();
        check(!recoveryDone.await(100,TimeUnit.MILLISECONDS),"autosave recovery waits for the same library publication lock");
        releasePublication.countDown();publication.join();recovery.join();check(failures.isEmpty()&&recoveryDone.getCount()==0,"autosave recovery completes after publication without overlapping file locks");
        check(Arrays.equals(active,Files.readAllBytes(autosave.toPath()))&&Arrays.equals(active,Files.readAllBytes(autosaveBackup.toPath())),"all operations preserve current autosave and its backup");
        check(Arrays.equals(source,Files.readAllBytes(doc(root,a.id).toPath())),"all collision paths preserve original project bytes");
        check(Arrays.stream(root.listFiles()).noneMatch(f->f.getName().startsWith("pending-")),"no pending publication files remain");
        System.out.println("ALL "+checks+" HOST LIBRARY CHECKS PASSED");
    }
}
