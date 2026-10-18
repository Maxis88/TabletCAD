package pl.tabletcad.app.storage;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.*;

import pl.tabletcad.app.model.CadProject;

/** Atomic local persistence with a last-known-good recovery copy. */
public final class ProjectRepository {
    private final File folder;
    private final File document;
    private final File recovery;
    private String loadNotice="";
    private final AtomicSavePair savePair;
    private IOException recoveryFailure;

    public ProjectRepository(Context context) {
        this(context.getFilesDir());
    }

    public ProjectRepository(File filesDirectory) {
        this(filesDirectory,index -> {});
    }

    public ProjectRepository(File filesDirectory,AtomicSavePair.Checkpoint checkpoint) {
        folder = new File(filesDirectory, "projects");
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create projects folder");
        document = new File(folder, "autosave.tcad");
        recovery = new File(folder, "autosave.tcad.bak");
        savePair=new AtomicSavePair(folder,checkpoint);
        try{savePair.recover();}catch(IOException error){recoveryFailure=error;}
    }

    public File importedStepFile() {
        return new File(folder, "imported.step");
    }

    public synchronized CadProject loadOrCreate() {
        CadProject loaded = tryLoad(document);
        if (loaded != null) return loaded;
        loaded = tryLoad(recovery);
        if(loaded!=null){loadNotice="Odtworzono dokument z kopii odzyskiwania";return loaded;}
        List<CadProject> projects=listProjects();
        if(!projects.isEmpty()){loadNotice="Odtworzono ostatni projekt z biblioteki";return projects.get(0);}
        if(document.exists()||recovery.exists())loadNotice="Nie można odczytać zapisu. Uszkodzone pliki zostaną zachowane osobno.";
        return CadProject.blank();
    }

    public boolean readOnly(){return recoveryFailure!=null;}
    public String loadNotice(){return readOnly()?"Nie można bezpiecznie odzyskać przerwanego zapisu. Otworzono tylko do odczytu; pliki zachowano.":loadNotice;}

    private File projectFile(String id) {
        if(id==null||!id.matches("[A-Za-z0-9_-]{1,100}"))throw new IllegalArgumentException("Niepoprawny identyfikator projektu");
        return new File(folder,"document-"+id+".tcad");
    }

    public synchronized CadProject loadProject(String id)throws IOException {
        File file=projectFile(id);CadProject loaded=tryLoad(file);
        if(loaded==null||!loaded.id.equals(id))loaded=tryLoad(new File(file.getPath()+".bak"));
        if(loaded==null||!loaded.id.equals(id))throw new IOException("Nie można odczytać projektu ani jego kopii");
        return loaded;
    }

    /** Last modification time of a stored project document (0 when unknown). */
    public synchronized long modifiedAt(String id) {
        File file=new File(folder,"document-"+id+".tcad");return file.isFile()?file.lastModified():0;
    }

    /** PNG preview of a project's solid (may not exist yet). */
    public File thumbnailFile(String id) {
        projectFile(id);File folder=new File(this.folder,"thumbnails");folder.mkdirs();return new File(folder,id+".png");
    }

    /** Writes the preview atomically (temporary file + rename). */
    public void saveThumbnail(String id,byte[] png)throws IOException {
        File target=thumbnailFile(id),temporary=new File(target.getPath()+".tmp");
        try(FileOutputStream output=new FileOutputStream(temporary)){output.write(png);output.getFD().sync();}
        Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Removes a project from the library without destroying data: its document, backup and preview
     * are moved to projects/trash (timestamped). The open document cannot be deleted.
     */
    public synchronized void deleteProject(String id,String openProjectId)throws IOException {
        if(recoveryFailure!=null)throw new IOException("Biblioteka jest tylko do odczytu");
        if(id.equals(openProjectId))throw new IOException("Nie można usunąć otwartego projektu — najpierw otwórz inny");
        File file=projectFile(id);if(!file.isFile())throw new IOException("Projekt nie istnieje");
        File trash=new File(folder,"trash");if(!trash.isDirectory()&&!trash.mkdirs())throw new IOException("Nie można utworzyć kosza");
        String stamp=String.valueOf(System.currentTimeMillis());
        for(File source:new File[]{file,new File(file.getPath()+".bak"),thumbnailFile(id)})
            if(source.isFile())Files.move(source.toPath(),new File(trash,stamp+"-"+source.getName()).toPath(),StandardCopyOption.REPLACE_EXISTING);
    }

    public synchronized List<CadProject> listProjects() {
        File[] files=folder.listFiles((dir,name)->name.startsWith("document-")&&name.endsWith(".tcad"));
        List<CadProject> result=new ArrayList<>();if(files==null)return result;
        Arrays.sort(files,(a,b)->{int order=Long.compare(b.lastModified(),a.lastModified());return order!=0?order:a.getName().compareTo(b.getName());});
        for(File file:files) {
            String id=file.getName().substring(9,file.getName().length()-5);
            try{result.add(loadProject(id));}catch(IOException|IllegalArgumentException ignored){}
        }
        return result;
    }

    public synchronized List<ProjectLibraryFiles.TrashEntry> listDeletedProjects()throws IOException {
        return new ProjectLibraryFiles(folder,new ProjectDocumentCodec()).listTrash();
    }
    public synchronized boolean hasProjectFiles(String id) {
        File file=projectFile(id);return file.exists()||new File(file.getPath()+".bak").exists();
    }
    public synchronized CadProject restoreProject(String token,boolean asCopy,String name)throws IOException {
        if(readOnly())throw new IOException("Biblioteka jest tylko do odczytu");
        ProjectLibraryFiles.Info restored=new ProjectLibraryFiles(folder,new ProjectDocumentCodec()).restore(token,asCopy,name);
        return loadProject(restored.id);
    }
    public synchronized CadProject duplicateProject(String id,String name)throws IOException {
        if(readOnly())throw new IOException("Biblioteka jest tylko do odczytu");
        ProjectLibraryFiles.Info copy=new ProjectLibraryFiles(folder,new ProjectDocumentCodec()).duplicate(id,name);
        return loadProject(copy.id);
    }

    public synchronized void save(CadProject project) {
        try {
            if(recoveryFailure!=null)throw new IOException("Unresolved document transaction; writing disabled",recoveryFailure);
            String serialized = project.toJson().toString(2);
            // A snapshot must be readable by the current schema before either copy is replaced.
            CadProject validated = CadProject.fromJson(new JSONObject(serialized));
            projectFile(validated.id);
            byte[] data = serialized.getBytes(StandardCharsets.UTF_8);
            if(data.length>16*1024*1024)throw new IOException("Projekt przekracza limit 16 MiB");
            savePair.save(() -> {
                LinkedHashMap<File,byte[]> changes=new LinkedHashMap<>();
                File individual=projectFile(project.id);changes.put(individual,data);changes.put(document,data);
                prepareBackup(individual,changes);prepareBackup(document,changes);return changes;
            });
        } catch (Exception error) {
            if(new File(folder,"save-transaction.bin").exists()) {
                try{savePair.recover();}catch(IOException unresolved){recoveryFailure=unresolved;}
            }
            throw new IllegalStateException("Project autosave failed", error);
        }
    }

    private void prepareBackup(File target,LinkedHashMap<File,byte[]> changes)throws IOException {
        if(target.exists()) {
            if(!target.isFile())throw new IOException("Document target is not a regular file");
            CadProject previous=tryLoad(target);
            boolean valid=previous!=null&&(target.equals(document)||target.equals(projectFile(previous.id)));
            File copy=valid?new File(target.getPath()+".bak")
                :new File(folder,"unreadable-"+UUID.randomUUID()+".tcad");
            if(valid)changes.put(copy,Files.readAllBytes(target.toPath()));
            else Files.copy(target.toPath(),copy.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private CadProject tryLoad(File file) {
        if (!file.isFile()||file.length()<2||file.length()>16*1024*1024) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset != data.length) return null;
            CadProject loaded=CadProject.fromJson(new JSONObject(new String(data, StandardCharsets.UTF_8)));
            projectFile(loaded.id);return loaded;
        } catch (Exception ignored) {
            return null;
        }
    }
}
