package pl.tabletcad.app.storage;

import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** New library documents are published last; existing documents and autosave are never replaced. */
public final class ProjectLibraryFiles {
    public interface Codec {
        Info inspect(byte[] data)throws IOException;
        byte[] copy(byte[] data,String id,String name,Map<String,String> assets)throws IOException;
    }
    public interface Checkpoint {void beforePublish()throws IOException;}
    public static final class Info {
        public final String id,name;public final List<String> assets;
        public Info(String id,String name,List<String> assets){this.id=id;this.name=name;this.assets=new ArrayList<>(assets);}
    }
    public static final class TrashEntry {
        public final String token,id,name;public final long deletedAt;public final boolean backup;
        TrashEntry(String token,Info info,long time,boolean backup){this.token=token;id=info.id;name=info.name;deletedAt=time;this.backup=backup;}
    }
    private interface Action<T>{T run()throws IOException;}
    private static final Pattern ARCHIVE=Pattern.compile("([0-9]{1,19})-document-([A-Za-z0-9_-]{1,100})\\.tcad");
    private final File folder;private final Codec codec;private final Checkpoint checkpoint;
    public ProjectLibraryFiles(File folder,Codec codec)throws IOException{this(folder,codec,()->{});}
    public ProjectLibraryFiles(File folder,Codec codec,Checkpoint checkpoint)throws IOException{
        this.folder=folder.getCanonicalFile();this.codec=codec;this.checkpoint=checkpoint;
        if(!this.folder.isDirectory())throw new IOException("Brak katalogu projektów");
    }
    private <T>T locked(Action<T> action)throws IOException {
        synchronized(ProjectFileLocks.forFolder(folder)) {
            try(FileChannel channel=FileChannel.open(new File(folder,".document-save.lock").toPath(),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=channel.lock()) {return action.run();}
            catch(OverlappingFileLockException error){throw new IOException("Biblioteka jest zajęta zapisem — spróbuj ponownie",error);}
        }
    }
    private File child(File parent,String name)throws IOException {
        if(name==null||!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,180}")||name.equals(".")||name.equals(".."))throw new IOException("Niepoprawna nazwa pliku");
        File result=new File(parent,name);
        if(Files.isSymbolicLink(result.toPath())||!result.getCanonicalFile().getParentFile().equals(parent.getCanonicalFile()))throw new IOException("Plik poza biblioteką projektów");
        return result;
    }
    private File directory(String name)throws IOException {
        File result=child(folder,name);if(!result.isDirectory()&&!result.mkdir())throw new IOException("Nie można otworzyć katalogu "+name);return result;
    }
    private File document(String id)throws IOException {
        if(id==null||!id.matches("[A-Za-z0-9_-]{1,100}"))throw new IOException("Niepoprawny identyfikator projektu");return child(folder,"document-"+id+".tcad");
    }
    private byte[] read(File file,String id)throws IOException {
        if(!file.isFile()||file.length()<2||file.length()>16L*1024*1024)throw new IOException("Nieczytelny dokument lub przekroczony limit 16 MiB");
        byte[] data=Files.readAllBytes(file.toPath());if(data.length>16*1024*1024||!codec.inspect(data).id.equals(id))throw new IOException("Niezgodny identyfikator dokumentu");return data;
    }
    private byte[] snapshot(File main,String id)throws IOException {
        try{return read(main,id);}catch(IOException error){try{return read(child(main.getParentFile(),main.getName()+".bak"),id);}catch(IOException backup){error.addSuppressed(backup);throw error;}}
    }
    public List<TrashEntry> listTrash()throws IOException {
        return locked(()->{
            File trash=directory("trash");File[] files=trash.listFiles();List<TrashEntry> result=new ArrayList<>();Set<String> seen=new HashSet<>();
            if(files!=null)for(File file:files) {
                String token=file.getName().endsWith(".bak")?file.getName().substring(0,file.getName().length()-4):file.getName();Matcher m=ARCHIVE.matcher(token);
                if(!m.matches()||!seen.add(token)||child(trash,token+".restored").isFile())continue;
                try {
                    long time=Long.parseLong(m.group(1));String id=m.group(2);File main=child(trash,token);byte[] data=snapshot(main,id);boolean backup;
                    try{read(main,id);backup=false;}catch(IOException error){backup=true;}
                    result.add(new TrashEntry(token,codec.inspect(data),time,backup));
                }catch(IOException|NumberFormatException ignored){/* Preserve unreadable archives for manual recovery. */}
            }
            result.sort((a,b)->{int order=Long.compare(b.deletedAt,a.deletedAt);return order!=0?order:a.token.compareTo(b.token);});return result;
        });
    }
    public Info duplicate(String id,String name)throws IOException {
        return locked(()->publishCopy(snapshot(document(id),id),checkedName(name),null));
    }
    public Info restore(String token,boolean asCopy,String name)throws IOException {
        return locked(()->{
            Matcher m=ARCHIVE.matcher(token==null?"":token);if(!m.matches())throw new IOException("Niepoprawny wpis kosza");
            File trash=directory("trash");File marker=child(trash,token+".restored");if(marker.exists())throw new IOException("Ten zapis został już przywrócony");
            byte[] data=snapshot(child(trash,token),m.group(2));Info info;
            if(asCopy)info=publishCopy(data,checkedName(name),trash);
            else {info=codec.inspect(data);checkAssets(info);checkpoint.beforePublish();publishNew(document(info.id),data);restoreThumbnail(trash,token,info.id);}
            // Archive bytes are retained. If writing the marker fails, the library document
            // remains valid and the repeated operation is blocked by the existing UUID.
            try{publishNew(marker,(info.id+"\n").getBytes(StandardCharsets.UTF_8));}catch(IOException ignored){}
            return info;
        });
    }
    private static String checkedName(String name)throws IOException {
        if(name==null||name.trim().isEmpty()||name.trim().length()>80)throw new IOException("Nazwa projektu: od 1 do 80 znaków");return name.trim();
    }
    private void checkAssets(Info info)throws IOException {
        for(String asset:info.assets)if(!child(folder,asset).isFile())throw new IOException("Brak pliku źródłowego: "+asset);
    }
    private Info publishCopy(byte[] source,String name,File archive)throws IOException {
        Info original=codec.inspect(source);checkAssets(original);String id=UUID.randomUUID().toString();File target=document(id);
        List<File> created=new ArrayList<>();boolean published=false;
        try {
            Map<String,String> renamed=new LinkedHashMap<>();
            for(String asset:original.assets)if(!renamed.containsKey(asset)) {
                int dot=asset.lastIndexOf('.');String extension=dot>=0?asset.substring(dot):".bin";
                String copyName="source-copy-"+UUID.randomUUID()+extension;File copy=child(folder,copyName);
                copyNew(child(folder,asset),copy);created.add(copy);renamed.put(asset,copyName);
            }
            byte[] data=codec.copy(source,id,name,renamed);Info result=codec.inspect(data);
            if(!result.id.equals(id))throw new IOException("Niepoprawny identyfikator kopii");
            for(String asset:result.assets)if(!renamed.containsValue(asset))throw new IOException("Kopia nadal wskazuje źródłowy projekt");
            checkpoint.beforePublish();publishNew(target,data);published=true;
            if(archive==null)try{File thumbs=directory("thumbnails"),from=child(thumbs,original.id+".png");if(from.isFile())copyNew(from,child(thumbs,id+".png"));}catch(IOException ignored){}
            return result;
        } finally {if(!published)for(File file:created)Files.deleteIfExists(file.toPath());}
    }
    private void restoreThumbnail(File trash,String token,String id) {
        try{String stamp=token.substring(0,token.indexOf("-document-"));File source=child(trash,stamp+"-"+id+".png"),target=child(directory("thumbnails"),id+".png");if(source.isFile()&&!target.exists())copyNew(source,target);}catch(IOException ignored){}
    }
    private void publishNew(File target,byte[] data)throws IOException {
        if(data.length>16*1024*1024)throw new IOException("Projekt przekracza limit 16 MiB");
        if(target.exists()||child(target.getParentFile(),target.getName()+".bak").exists())throw new IOException("Projekt o tym identyfikatorze już istnieje — przywróć jako kopię");
        File temporary=child(target.getParentFile(),"pending-"+UUID.randomUUID()+".tmp");
        try {
            try(FileOutputStream out=new FileOutputStream(temporary)){out.write(data);out.getFD().sync();}
            // No REPLACE_EXISTING: a collision must preserve the destination.
            Files.move(temporary.toPath(),target.toPath());
        }finally{Files.deleteIfExists(temporary.toPath());}
    }
    private void copyNew(File source,File target)throws IOException {
        File temporary=child(target.getParentFile(),"pending-"+UUID.randomUUID()+".tmp");
        try {
            try(InputStream input=new FileInputStream(source);FileOutputStream output=new FileOutputStream(temporary)) {byte[] buffer=new byte[16384];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);output.getFD().sync();}
            Files.move(temporary.toPath(),target.toPath());
        }finally{Files.deleteIfExists(temporary.toPath());}
    }
}
