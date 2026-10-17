import java.io.*;
import java.nio.file.Files;
import java.util.*;
import pl.tabletcad.app.storage.*;
import pl.tabletcad.app.model.*;

/** Android filesystem tests: exceptions and simulated process death at every replacement boundary. */
public final class AtomicSaveProbe {
    public static final class CrashProcess {
        public static void main(String[] args) {
            int phase=Integer.parseInt(args[1]);File root=new File(args[0]);CadProject p=new ProjectRepository(root).loadOrCreate();p.name="child-committed-revision";
            new ProjectRepository(root,new AtomicSavePair.Checkpoint(){
                public void afterReplace(int index){if(phase==0&&index==0)Runtime.getRuntime().halt(91);}
                public void afterCommit(){if(phase==1)Runtime.getRuntime().halt(92);}
            }).save(p);
            throw new AssertionError("Process death was not injected");
        }
    }
    static final class Crash extends Error {}
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    static Map<String,byte[]> snapshot(File root)throws Exception {
        Map<String,byte[]> result=new TreeMap<>();for(File file:new File(root,"projects").listFiles())if(file.getName().endsWith(".tcad")||file.getName().endsWith(".tcad.bak"))result.put(file.getName(),Files.readAllBytes(file.toPath()));return result;
    }
    static void same(Map<String,byte[]> expected,File root,String label)throws Exception {
        Map<String,byte[]> actual=snapshot(root);boolean matches=expected.keySet().equals(actual.keySet());
        for(String name:expected.keySet())matches&=Arrays.equals(expected.get(name),actual.get(name));check(matches,label);
    }
    static CadProject fixture(File root)throws Exception {
        ProjectRepository repository=new ProjectRepository(root);CadProject p=new CadProject();p.name="revision-one";repository.save(p);p.name="revision-two";repository.save(p);return p;
    }
    static File transaction(File root) {
        File[] dirs=new File(root,"projects").listFiles((dir,name)->name.startsWith("save-txn-")&&new File(dir,name).isDirectory());
        if(dirs==null||dirs.length!=1)throw new AssertionError("Expected one interrupted transaction");return dirs[0];
    }
    public static void main(String[] args)throws Exception {
        try{run(args);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    static void run(String[] args)throws Exception {
        File parent=new File(args[0],"atomic-save-"+UUID.randomUUID());check(parent.mkdirs(),"isolated durable-save fixture");
        for(int boundary=0;boundary<4;boundary++) {
            File root=new File(parent,"io-"+boundary);CadProject p=fixture(root);Map<String,byte[]> before=snapshot(root);final int index=boundary;
            ProjectRepository failing=new ProjectRepository(root,step->{if(step==index)throw new IOException("injected save failure");});
            p.name="rejected-revision";boolean rejected=false;try{failing.save(p);}catch(IllegalStateException expected){rejected=true;}
            check(rejected,"I/O error reported after replacement "+boundary);same(before,root,"all documents and backups roll back at boundary "+boundary);
            check(!new File(root,"projects/save-transaction.bin").exists(),"rolled-back journal removed");
            check(new ProjectRepository(root).loadOrCreate().name.equals("revision-two"),"reopening rejected save restores previous active model");
        }
        for(int boundary=0;boundary<4;boundary++) {
            File root=new File(parent,"crash-"+boundary);CadProject p=fixture(root);Map<String,byte[]> before=snapshot(root);final int index=boundary;
            ProjectRepository crashing=new ProjectRepository(root,step->{if(step==index)throw new Crash();});
            p.name="interrupted-revision";try{crashing.save(p);throw new AssertionError("Crash not injected");}catch(Crash expected){}
            check(new File(root,"projects/save-transaction.bin").isFile(),"interrupted save retains durable journal");
            ProjectRepository reopened=new ProjectRepository(root);check(!reopened.readOnly(),"uncommitted transaction recovers automatically");
            same(before,root,"restart rolls back every document and backup at boundary "+boundary);
            check(reopened.loadOrCreate().name.equals("revision-two"),"restart retains previous active revision");
        }
        File committedRoot=new File(parent,"committed");CadProject committed=fixture(committedRoot);
        ProjectRepository crashingAfterCommit=new ProjectRepository(committedRoot,new AtomicSavePair.Checkpoint(){
            public void afterReplace(int index){}public void afterCommit(){throw new Crash();}
        });
        committed.name="committed-revision";try{crashingAfterCommit.save(committed);throw new AssertionError();}catch(Crash expected){}
        check(new File(transaction(committedRoot),"committed").isFile(),"durable commit marker survives interruption before cleanup");
        ProjectRepository accepted=new ProjectRepository(committedRoot);
        check(!accepted.readOnly()&&accepted.loadOrCreate().name.equals("committed-revision")&&accepted.loadProject(committed.id).name.equals("committed-revision"),"restart preserves committed new revision in both destinations");
        File corruptRoot=new File(parent,"corrupt");CadProject corrupt=fixture(corruptRoot);
        ProjectRepository crashing=new ProjectRepository(corruptRoot,step->{if(step==0)throw new Crash();});corrupt.name="half-written";
        try{crashing.save(corrupt);throw new AssertionError();}catch(Crash expected){}
        File copy=new File(transaction(corruptRoot),"old-0");Files.write(copy.toPath(),new byte[]{1,2,3});Map<String,byte[]> evidence=snapshot(corruptRoot);
        ProjectRepository protectedRepository=new ProjectRepository(corruptRoot);check(protectedRepository.readOnly(),"corrupt recovery hash opens repository read-only");
        same(evidence,corruptRoot,"failed recovery preserves all original evidence without overwriting files");
        boolean blocked=false;try{protectedRepository.save(corrupt);}catch(IllegalStateException expected){blocked=true;}
        check(blocked&&new File(corruptRoot,"projects/save-transaction.bin").isFile(),"writes blocked while recovery remains unresolved");
        File invalidJournalRoot=new File(parent,"invalid-journal");fixture(invalidJournalRoot);Map<String,byte[]> original=snapshot(invalidJournalRoot);
        Files.write(new File(invalidJournalRoot,"projects/save-transaction.bin").toPath(),new byte[]{0,1,2});
        check(new ProjectRepository(invalidJournalRoot).readOnly(),"damaged journal fails closed");same(original,invalidJournalRoot,"damaged journal never overwrites readable projects");
        for(int phase=0;phase<2;phase++) {
            File root=new File(parent,"real-process-"+phase);CadProject p=fixture(root);Map<String,byte[]> before=snapshot(root);
            Process process=new ProcessBuilder("/system/bin/app_process","/system/bin","AtomicSaveProbe$CrashProcess",root.getAbsolutePath(),Integer.toString(phase)).redirectErrorStream(true).start();
            if(!process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Crash child timeout");}
            check(process.exitValue()==91+phase,"real Android process terminates at transaction phase "+phase);
            ProjectRepository recovered=new ProjectRepository(root);check(!recovered.readOnly(),"real process death recovers without write protection");
            if(phase==0)same(before,root,"real process death before commit rolls back all four files");
            else check(recovered.loadOrCreate().name.equals("child-committed-revision")&&recovered.loadProject(p.id).name.equals("child-committed-revision"),"real process death after commit retains matching new revisions");
        }
        File concurrentRoot=new File(parent,"concurrent");CadProject shared=fixture(concurrentRoot);
        java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);List<Throwable> errors=Collections.synchronizedList(new ArrayList<>());List<Thread> writers=new ArrayList<>();
        for(int writer=0;writer<3;writer++){final int id=writer;Thread thread=new Thread(()->{
            try{ProjectRepository repo=new ProjectRepository(concurrentRoot);CadProject writerProject=CadProject.fromJson(shared.toJson());start.await();
                for(int revision=0;revision<4;revision++){writerProject.name="writer-"+id+"-"+revision;repo.save(writerProject);}
            }catch(Throwable error){errors.add(error);}
        });writers.add(thread);thread.start();}
        start.countDown();for(Thread thread:writers){thread.join(15000);if(thread.isAlive())throw new AssertionError("Concurrent writer timeout");}
        check(errors.isEmpty(),"independent repository instances serialize concurrent saves without overlapping locks");
        File folder=new File(concurrentRoot,"projects");
        check(Arrays.equals(Files.readAllBytes(new File(folder,"autosave.tcad").toPath()),Files.readAllBytes(new File(folder,"document-"+shared.id+".tcad").toPath()))
            &&Arrays.equals(Files.readAllBytes(new File(folder,"autosave.tcad.bak").toPath()),Files.readAllBytes(new File(folder,"document-"+shared.id+".tcad.bak").toPath())),"concurrent writers leave matching documents and matching recovery revisions");
        System.out.println("ALL ATOMIC SAVE TESTS PASSED");
    }
}
