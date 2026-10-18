import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import pl.tabletcad.app.LatestTaskRunner;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.*;

/** Real catalog, query, trash and delivery-generation tests; no Android UI or JNI. */
public final class LibraryWorkspaceHostTest {
    interface Work{void run()throws Exception;}
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static void rejects(Work action,String label)throws Exception{try{action.run();}catch(IOException expected){check(true,label);return;}throw new AssertionError(label);}
    static void await(CountDownLatch latch)throws Exception{if(!latch.await(3,TimeUnit.SECONDS))throw new AssertionError("Timed out");}
    static void deliver(BlockingQueue<Runnable> queue)throws Exception{Runnable callback=queue.poll(3,TimeUnit.SECONDS);if(callback==null)throw new AssertionError("No callback");callback.run();}
    static File doc(File root,String id){return new File(root,"document-"+id+".tcad");}
    static byte[] bytes(CadProject p)throws Exception{return p.toJson().toString().getBytes(StandardCharsets.UTF_8);}
    static CadProject decode(byte[] data)throws Exception{return CadProject.fromJson(new JSONObject(new String(data,StandardCharsets.UTF_8)));}
    static CadProject saved(File root,String name,long time)throws Exception{CadProject p=new CadProject();p.name=name;File file=doc(root,p.id);Files.write(file.toPath(),bytes(p));if(!file.setLastModified(time))throw new IOException("Cannot set fixture time");return p;}
    public static void main(String[] args)throws Exception {
        File files=new File(args[0],"workspace-host-"+UUID.randomUUID()),root=new File(files,"projects");check(root.mkdirs(),"isolated library fixture");
        ProjectLibraryFiles library=new ProjectLibraryFiles(root,new ProjectDocumentCodec());
        CadProject a=saved(root,"Śruba łóżka",3000),b=saved(root,"Żółty uchwyt",1000);
        byte[] originalA=Files.readAllBytes(doc(root,a.id).toPath());
        File backupB=new File(doc(root,b.id)+".bak");Files.move(doc(root,b.id).toPath(),backupB.toPath());
        List<ProjectLibraryFiles.DocumentEntry> entries=library.listDocuments();
        check(entries.size()==2,"backup-only project is discoverable");
        check(decode(entries.get(0).data).id.equals(a.id)&&!entries.get(0).backup,"healthy main preferred and sorted by time");
        check(decode(entries.get(1).data).id.equals(b.id)&&entries.get(1).backup&&entries.get(1).modifiedAt==1000,"backup metadata comes from the chosen recovery file");
        Files.writeString(doc(root,b.id).toPath(),"broken");
        check(library.listDocuments().stream().filter(e->e.backup).count()==1,"corrupt main falls back to backup");
        Files.write(doc(root,b.id).toPath(),bytes(a));
        check(library.listDocuments().stream().filter(e->e.backup).count()==1,"foreign UUID in main falls back only to matching backup");
        CadProject missing=saved(root,"Uszkodzony",4000);Files.writeString(doc(root,missing.id).toPath(),"broken");
        Files.writeString(new File(root,"document-invalid!.tcad").toPath(),"ignored");
        Files.writeString(new File(root,"autosave.tcad").toPath(),"preserve autosave");
        try(RandomAccessFile oversized=new RandomAccessFile(new File(root,"document-oversized.tcad"),"rw")){oversized.setLength(16*1024*1024+1);}
        check(library.listDocuments().size()==2,"malformed, oversized and unreadable documents excluded without phantom rows");
        AtomicInteger visited=new AtomicInteger();library.visitDocuments(entry->visited.incrementAndGet());
        check(visited.get()==2,"streaming scan visits each valid identity once");
        rejects(()->library.visitDocuments(entry->{throw new IOException("consumer failure");}),"streaming consumer failure is not silently swallowed");
        ProjectRepository repository=new ProjectRepository(files);
        List<ProjectLibraryItem> catalog=repository.listProjectItems();
        check(catalog.size()==2&&catalog.get(0).project.id.equals(a.id),"repository uses validated recent catalog");
        check(repository.listProjects().stream().anyMatch(p->p.id.equals(b.id)),"legacy list API includes backup-only documents");
        check(repository.loadProject(b.id).name.equals(b.name),"backup row can actually be opened");
        check(Arrays.equals(Files.readAllBytes(doc(root,a.id).toPath()),originalA)&&Files.readString(new File(root,"autosave.tcad").toPath()).equals("preserve autosave"),"catalog scan leaves originals and autosave unchanged");

        a.drawingNumber="RYS-42";a.drawingAuthor="Łukasz";a.drawingMaterial="Stal";
        CadProject step=new CadProject();step.importedStep=true;step.name="Część STEP";
        CadProject stl=new CadProject();stl.importedMeshSource="mesh.stl";stl.name="Skan";
        CadProject sketch=CadProject.blank();sketch.name="Szkic";sketch.sketch.add(new SketchPrimitive("line","Linia",0,0,10,10));
        CadProject empty=CadProject.blank();empty.name="Pusty";
        List<ProjectLibraryItem> source=new ArrayList<>(Arrays.asList(new ProjectLibraryItem(a,30,false),new ProjectLibraryItem(b,10,true),new ProjectLibraryItem(step,20,false),new ProjectLibraryItem(stl,40,false),new ProjectLibraryItem(sketch,50,false),new ProjectLibraryItem(empty,60,false)));
        List<ProjectLibraryItem> originalOrder=new ArrayList<>(source);
        check(ProjectLibraryItem.select(source,"sruba LOZKA",ProjectLibraryItem.Kind.ALL,ProjectLibraryItem.Sort.RECENT).get(0).project.id.equals(a.id),"Polish accents, l-stroke and case do not obstruct search");
        check(ProjectLibraryItem.select(source," rys-42  LUKASZ stal ",ProjectLibraryItem.Kind.ALL,ProjectLibraryItem.Sort.RECENT).size()==1,"search matches all words across drawing number, author and material");
        check(ProjectLibraryItem.select(source,"sruba absent",ProjectLibraryItem.Kind.ALL,ProjectLibraryItem.Sort.RECENT).isEmpty(),"every search token is required");
        check(ProjectLibraryItem.select(source,"",ProjectLibraryItem.Kind.ALL,ProjectLibraryItem.Sort.RECENT).get(0).project.id.equals(empty.id),"recent sort");
        check(ProjectLibraryItem.select(source,"",ProjectLibraryItem.Kind.ALL,ProjectLibraryItem.Sort.OLDEST).get(0).project.id.equals(b.id),"oldest sort");
        List<ProjectLibraryItem> az=ProjectLibraryItem.select(source,"",ProjectLibraryItem.Kind.ALL,ProjectLibraryItem.Sort.NAME_ASC),za=ProjectLibraryItem.select(source,"",ProjectLibraryItem.Kind.ALL,ProjectLibraryItem.Sort.NAME_DESC);
        check(az.get(0).project.id.equals(step.id)&&za.get(0).project.id.equals(b.id),"name ascending and descending use normalized names");
        for(ProjectLibraryItem.Kind kind:ProjectLibraryItem.Kind.values()) {
            int expected=kind==ProjectLibraryItem.Kind.ALL?6:kind==ProjectLibraryItem.Kind.NATIVE?2:1;
            List<ProjectLibraryItem> found=ProjectLibraryItem.select(source,null,kind,ProjectLibraryItem.Sort.RECENT);
            check(found.size()==expected,"type filter "+kind);
        }
        check(ProjectLibraryItem.select(source,"sruba",ProjectLibraryItem.Kind.STL,ProjectLibraryItem.Sort.RECENT).isEmpty(),"search and type filter combine");
        check(source.equals(originalOrder),"queries never reorder source snapshots");
        ProjectLibraryItem tie1=new ProjectLibraryItem(a,1,false),tie2=new ProjectLibraryItem(b,1,false);
        check(ProjectLibraryItem.select(Arrays.asList(tie1,tie2),"",ProjectLibraryItem.Kind.ALL,ProjectLibraryItem.Sort.RECENT).get(0).project.id.equals(a.id.compareTo(b.id)<0?a.id:b.id),"equal timestamps have deterministic UUID order");
        List<ProjectLibraryItem> many=new ArrayList<>();for(int i=0;i<5000;i++)many.add(new ProjectLibraryItem(i%2==0?a:stl,i,false));
        check(ProjectLibraryItem.select(many,"lozka",ProjectLibraryItem.Kind.NATIVE,ProjectLibraryItem.Sort.OLDEST).size()==2500,"large catalog search/filter/sort retains exact matches");

        CadProject removable=saved(root,"Do kosza",5000);byte[] mainBytes=bytes(removable),backupBytes=bytes(a);
        File main=doc(root,removable.id),backup=new File(main+".bak"),thumbs=new File(root,"thumbnails");thumbs.mkdirs();File thumbnail=new File(thumbs,removable.id+".png");
        Files.write(backup.toPath(),backupBytes);Files.write(thumbnail.toPath(),new byte[]{9,8,7});
        AtomicInteger moves=new AtomicInteger();ProjectLibraryFiles failing=new ProjectLibraryFiles(root,new ProjectDocumentCodec(),()->{if(moves.incrementAndGet()==2)throw new IOException("injected archive failure");});
        rejects(()->failing.trash(removable.id,a.id),"archive failure after first move reported");
        check(Arrays.equals(Files.readAllBytes(main.toPath()),mainBytes)&&Arrays.equals(Files.readAllBytes(backup.toPath()),backupBytes)&&thumbnail.isFile(),"archive failure rolls back moved companions and retains main");
        rejects(()->library.trash(a.id,a.id),"open project cannot be trashed");rejects(()->library.trash("../escape",a.id),"trash identity traversal rejected");
        String token=library.trash(removable.id,a.id);
        check(!main.exists()&&!backup.exists()&&!thumbnail.exists(),"successful trash moves main, backup and thumbnail");
        check(Arrays.equals(Files.readAllBytes(new File(root,"trash/"+token).toPath()),mainBytes)&&Arrays.equals(Files.readAllBytes(new File(root,"trash/"+token+".bak").toPath()),backupBytes),"archive retains both original byte streams");
        check(library.listTrash().stream().anyMatch(e->e.token.equals(token)),"archived project is discoverable by restore UI");
        library.restore(token,false,null);check(Arrays.equals(Files.readAllBytes(main.toPath()),mainBytes),"archive is compatible with original restore protocol");
        CadProject backupOnly=saved(root,"Tylko backup",6000);File onlyBackup=new File(doc(root,backupOnly.id)+".bak");Files.move(doc(root,backupOnly.id).toPath(),onlyBackup.toPath());
        String backupToken=library.trash(backupOnly.id,a.id);check(!onlyBackup.exists()&&library.listTrash().stream().anyMatch(e->e.token.equals(backupToken)&&e.backup),"backup-only project can be trashed and remains recoverable");
        library.restore(backupToken,false,null);check(doc(root,backupOnly.id).isFile(),"backup-only trashed project restores as a main document");

        CadProject rollbackConflict=saved(root,"Rollback collision",7000);File conflictBackup=new File(doc(root,rollbackConflict.id)+".bak");
        byte[] oldBackup=bytes(rollbackConflict);Files.write(conflictBackup.toPath(),oldBackup);AtomicInteger conflictMoves=new AtomicInteger();
        ProjectLibraryFiles conflicting=new ProjectLibraryFiles(root,new ProjectDocumentCodec(),()->{
            if(conflictMoves.incrementAndGet()==2){Files.writeString(conflictBackup.toPath(),"new external data");throw new IOException("injected rollback collision");}
        });
        rejects(()->conflicting.trash(rollbackConflict.id,a.id),"rollback collision reported");
        check(Files.readString(conflictBackup.toPath()).equals("new external data")&&doc(root,rollbackConflict.id).isFile(),"rollback never replaces a new file at original path");
        File[] conflictArchives=new File(root,"trash").listFiles((dir,name)->name.endsWith("document-"+rollbackConflict.id+".tcad.bak"));
        check(conflictArchives!=null&&conflictArchives.length==1&&Arrays.equals(Files.readAllBytes(conflictArchives[0].toPath()),oldBackup),"unrestorable original backup remains preserved in archive");

        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);ProjectLibraryFiles blocking=new ProjectLibraryFiles(root,new ProjectDocumentCodec(),()->{locked.countDown();try{await(release);}catch(Exception e){throw new IOException(e);}});
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            Future<String> archiving=workers.submit(()->blocking.trash(backupOnly.id,a.id));await(locked);
            Future<List<ProjectLibraryFiles.DocumentEntry>> scanning=workers.submit(library::listDocuments);
            try{scanning.get(100,TimeUnit.MILLISECONDS);throw new AssertionError("scan overlapped archive");}catch(TimeoutException expected){check(true,"catalog scan waits for archive publication lock");}
            release.countDown();archiving.get(3,TimeUnit.SECONDS);check(scanning.get(3,TimeUnit.SECONDS).stream().noneMatch(e->{try{return decode(e.data).id.equals(backupOnly.id);}catch(Exception error){throw new RuntimeException(error);}}),"scan observes completed archive without partial identity");
        }finally{release.countDown();workers.shutdownNow();}

        BlockingQueue<Runnable> delivery=new LinkedBlockingQueue<>();LatestTaskRunner runner=new LatestTaskRunner(delivery::add);
        CountDownLatch started=new CountDownLatch(1),finish=new CountDownLatch(1);List<String> results=new ArrayList<>();AtomicInteger skipped=new AtomicInteger();
        runner.submit(()->{started.countDown();await(finish);return "old";},results::add,e->{throw new AssertionError(e);});await(started);
        runner.submit(()->{skipped.incrementAndGet();return "skipped";},results::add,e->{throw new AssertionError(e);});
        runner.submit(()->"latest",results::add,e->{throw new AssertionError(e);});finish.countDown();
        deliver(delivery);deliver(delivery);
        check(results.equals(Collections.singletonList("latest"))&&skipped.get()==0,"obsolete results discarded and queued obsolete reads skipped");
        runner.submit(()->"closed",results::add,e->{throw new AssertionError(e);});Runnable late=delivery.poll(3,TimeUnit.SECONDS);check(late!=null,"result queued before closing");runner.close();late.run();
        check(results.equals(Collections.singletonList("latest")),"queued completion cannot update a closed view");
        runner.submit(()->{skipped.incrementAndGet();return "after close";},results::add,e->{throw new AssertionError(e);});
        check(skipped.get()==0,"closed reader refuses new work");
        LatestTaskRunner errors=new LatestTaskRunner(delivery::add);AtomicInteger failures=new AtomicInteger();
        errors.submit(()->{throw new IOException("read error");},value->{throw new AssertionError("unexpected result");},e->failures.incrementAndGet());deliver(delivery);
        check(failures.get()==1,"current read failure is delivered");
        errors.submit(()->"retry",results::add,e->{throw new AssertionError(e);});deliver(delivery);
        check(results.contains("retry"),"read failure does not kill retry worker");errors.close();
        LatestTaskRunner staleFailure=new LatestTaskRunner(delivery::add);
        staleFailure.submit(()->{throw new IOException("stale failure");},value->{},e->failures.incrementAndGet());
        Runnable oldError=delivery.poll(3,TimeUnit.SECONDS);check(oldError!=null,"error queued for generation check");
        staleFailure.submit(()->"replaced",results::add,e->{throw new AssertionError(e);});oldError.run();deliver(delivery);
        check(failures.get()==1&&results.contains("replaced"),"obsolete errors cannot replace a newer result");staleFailure.close();
        System.out.println("PASS LibraryWorkspaceHostTest: "+checks+" checks");
    }
}
