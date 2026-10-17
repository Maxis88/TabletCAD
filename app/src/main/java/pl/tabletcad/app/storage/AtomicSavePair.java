package pl.tabletcad.app.storage;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Recoverable multi-file save. A durable journal keeps old bytes until a durable commit marker. */
public final class AtomicSavePair {
    public interface Checkpoint {
        void afterReplace(int index) throws IOException;
        default void afterCommit() {}
    }
    public interface ChangeFactory { LinkedHashMap<File,byte[]> prepare() throws IOException; }
    private interface Work { void run() throws IOException; }
    private static final int MAGIC=0x54435031;
    private static final Map<String,Object> LOCKS=new ConcurrentHashMap<>();
    private final File folder,journal;
    private final Checkpoint checkpoint;
    private static final class Entry {
        String name;boolean existed;long oldSize,newSize;byte[] oldHash,newHash;
    }
    public AtomicSavePair(File folder,Checkpoint checkpoint) {
        this.folder=folder;this.journal=new File(folder,"save-transaction.bin");this.checkpoint=checkpoint;
    }
    public void recover()throws IOException {locked(this::recoverLocked);}
    private void locked(Work work)throws IOException {
        Object lock=LOCKS.computeIfAbsent(folder.getCanonicalPath(),key->new Object());
        synchronized(lock) {
            try(FileChannel channel=FileChannel.open(new File(folder,".document-save.lock").toPath(),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                    FileLock ignored=channel.lock()){work.run();}
        }
    }
    private File target(String name)throws IOException {
        if(name==null||!name.matches("(?:document-[A-Za-z0-9_-]{1,100}|autosave)\\.tcad(?:\\.bak)?"))throw new IOException("Unsafe transaction target");
        File result=new File(folder,name);if(!result.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile()))throw new IOException("Transaction outside project directory");
        return result;
    }
    private static byte[] hash(File file)throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[16384];
            try(InputStream input=new FileInputStream(file)){int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);}return digest.digest();
        }catch(NoSuchAlgorithmException error){throw new IOException(error);}
    }
    private static byte[] hash(byte[] bytes)throws IOException {
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException error){throw new IOException(error);}
    }
    private static void syncDirectory(File directory)throws IOException {
        FileDescriptor descriptor=null;
        try{descriptor=android.system.Os.open(directory.getAbsolutePath(),android.system.OsConstants.O_RDONLY,0);android.system.Os.fsync(descriptor);}
        catch(android.system.ErrnoException error){throw new IOException("Cannot sync project directory",error);}
        finally{if(descriptor!=null)try{android.system.Os.close(descriptor);}catch(android.system.ErrnoException ignored){}}
    }
    private static void write(File file,byte[] bytes)throws IOException {
        try(FileOutputStream output=new FileOutputStream(file)){output.write(bytes);output.flush();output.getFD().sync();}
    }
    private static void copy(File source,File destination)throws IOException {
        try(InputStream input=new FileInputStream(source);FileOutputStream output=new FileOutputStream(destination)) {
            byte[] buffer=new byte[16384];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);output.flush();output.getFD().sync();
        }
    }
    private static void move(File source,File destination)throws IOException {
        Files.move(source.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    private static void removeFile(File file)throws IOException {if(file.isFile())Files.delete(file.toPath());}
    private static void verify(File file,long size,byte[] digest)throws IOException {
        if(!file.isFile()||file.length()!=size||!MessageDigest.isEqual(hash(file),digest))throw new IOException("Transaction copy is damaged: "+file.getName());
    }
    public void save(LinkedHashMap<File,byte[]> changes)throws IOException {
        locked(()->saveLocked(changes));
    }
    public void save(ChangeFactory factory)throws IOException {
        locked(()->{recoverLocked();saveLocked(factory.prepare());});
    }
    private void saveLocked(LinkedHashMap<File,byte[]> changes)throws IOException {
        recoverLocked();if(changes.size()<2||changes.size()>4)throw new IOException("Invalid save transaction size");
        File transaction=new File(folder,"save-txn-"+UUID.randomUUID());
        if(!transaction.mkdir())throw new IOException("Cannot prepare document transaction");
        List<Entry> entries=new ArrayList<>();boolean journalReady=false,committed=false;
        try {
            int index=0;
            for(Map.Entry<File,byte[]> change:changes.entrySet()) {
                File destination=target(change.getKey().getName());
                if(!destination.equals(change.getKey()))throw new IOException("Invalid document transaction path");
                if(destination.exists()&&!destination.isFile())throw new IOException("Document target is not a file");
                Entry entry=new Entry();entry.name=destination.getName();entry.existed=destination.exists();entry.newSize=change.getValue().length;entry.newHash=hash(change.getValue());
                entries.add(entry);write(new File(destination.getPath()+".tmp"),change.getValue());
                if(entry.existed){entry.oldSize=destination.length();entry.oldHash=hash(destination);copy(destination,new File(transaction,"old-"+index));}
                else entry.oldHash=new byte[32];index++;
            }
            syncDirectory(transaction);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(DataOutputStream output=new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);output.writeUTF(transaction.getName());output.writeInt(entries.size());
                for(Entry entry:entries){output.writeUTF(entry.name);output.writeBoolean(entry.existed);output.writeLong(entry.oldSize);output.write(entry.oldHash);output.writeLong(entry.newSize);output.write(entry.newHash);}
            }
            File pending=new File(journal.getPath()+".tmp");write(pending,bytes.toByteArray());move(pending,journal);journalReady=true;syncDirectory(folder);
            index=0;
            for(Entry entry:entries){move(new File(target(entry.name).getPath()+".tmp"),target(entry.name));checkpoint.afterReplace(index++);}
            syncDirectory(folder);
            File marker=new File(transaction,"committed.tmp");write(marker,hash(journal));move(marker,new File(transaction,"committed"));syncDirectory(transaction);committed=true;checkpoint.afterCommit();
        }catch(IOException|RuntimeException failure) {
            if(journalReady) {
                try{removeFile(new File(transaction,"committed"));syncDirectory(transaction);recoverLocked();}
                catch(IOException recovery){failure.addSuppressed(recovery);}
            }else cleanup(transaction,entries,false);
            throw failure;
        }
        if(committed)try{cleanup(transaction,entries,true);}catch(IOException ignored){ /* Durable commit is recovered on the next access. */ }
    }
    private void recoverLocked()throws IOException {
        if(!journal.exists())return;
        if(!journal.isFile()||journal.length()>8192)throw new IOException("Invalid document transaction journal");
        File transaction;List<Entry> entries=new ArrayList<>();Set<String> names=new HashSet<>();
        try(DataInputStream input=new DataInputStream(new FileInputStream(journal))) {
            if(input.readInt()!=MAGIC)throw new IOException("Unknown document transaction format");
            String name=input.readUTF();if(!name.matches("save-txn-[0-9a-f-]{36}"))throw new IOException("Unsafe recovery directory");transaction=new File(folder,name);
            if(!transaction.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile())||!transaction.isDirectory())throw new IOException("Missing transaction recovery copies");
            int count=input.readInt();if(count<2||count>4)throw new IOException("Invalid recovery target count");
            for(int i=0;i<count;i++) {
                Entry entry=new Entry();entry.name=input.readUTF();target(entry.name);if(!names.add(entry.name))throw new IOException("Duplicate recovery target");
                entry.existed=input.readBoolean();entry.oldSize=input.readLong();entry.oldHash=new byte[32];input.readFully(entry.oldHash);entry.newSize=input.readLong();entry.newHash=new byte[32];input.readFully(entry.newHash);
                if(entry.oldSize<0||entry.newSize<0||entry.newSize>16L*1024*1024)throw new IOException("Invalid recovery length");entries.add(entry);
            }
            if(input.read()!=-1)throw new IOException("Trailing recovery journal data");
        }
        File marker=new File(transaction,"committed");
        if(marker.exists()) {
            verify(marker,32,hash(hash(journal)));
            for(Entry entry:entries)verify(target(entry.name),entry.newSize,entry.newHash);
        }else {
            // Validate every recovery copy before changing any target.
            for(int i=0;i<entries.size();i++)if(entries.get(i).existed)verify(new File(transaction,"old-"+i),entries.get(i).oldSize,entries.get(i).oldHash);
            for(int i=0;i<entries.size();i++)if(entries.get(i).existed)copy(new File(transaction,"old-"+i),new File(target(entries.get(i).name).getPath()+".rollback"));
            for(Entry entry:entries)if(entry.existed)move(new File(target(entry.name).getPath()+".rollback"),target(entry.name));else removeFile(target(entry.name));
            syncDirectory(folder);
        }
        cleanup(transaction,entries,true);
    }
    private void cleanup(File transaction,List<Entry> entries,boolean removeJournal)throws IOException {
        if(removeJournal){Files.deleteIfExists(journal.toPath());syncDirectory(folder);}
        removeFile(new File(journal.getPath()+".tmp"));
        for(Entry entry:entries){removeFile(new File(target(entry.name).getPath()+".tmp"));removeFile(new File(target(entry.name).getPath()+".rollback"));}
        for(int i=0;i<entries.size();i++)removeFile(new File(transaction,"old-"+i));
        removeFile(new File(transaction,"committed"));removeFile(new File(transaction,"committed.tmp"));
        Files.deleteIfExists(transaction.toPath());
    }
}
