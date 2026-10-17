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

    public ProjectRepository(Context context) {
        this(context.getFilesDir());
    }

    public ProjectRepository(File filesDirectory) {
        folder = new File(filesDirectory, "projects");
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create projects folder");
        document = new File(folder, "autosave.tcad");
        recovery = new File(folder, "autosave.tcad.bak");
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

    public String loadNotice(){return loadNotice;}

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

    public synchronized void save(CadProject project) {
        try {
            String serialized = project.toJson().toString(2);
            // A snapshot must be readable by the current schema before either copy is replaced.
            CadProject validated = CadProject.fromJson(new JSONObject(serialized));
            projectFile(validated.id);
            byte[] data = serialized.getBytes(StandardCharsets.UTF_8);
            if(data.length>16*1024*1024)throw new IOException("Projekt przekracza limit 16 MiB");
            atomicWrite(projectFile(project.id),data);
            atomicWrite(document,data);
        } catch (Exception error) {
            throw new IllegalStateException("Project autosave failed", error);
        }
    }

    private void atomicWrite(File target,byte[] data)throws IOException {
        File temporary=new File(target.getPath()+".tmp");
        try(FileOutputStream output=new FileOutputStream(temporary)) {
            output.write(data);output.flush();output.getFD().sync();
        }
        if(target.exists()) {
            CadProject previous=tryLoad(target);
            boolean valid=previous!=null&&(target.equals(document)||target.equals(projectFile(previous.id)));
            File copy=valid?new File(target.getPath()+".bak")
                :new File(folder,"unreadable-"+UUID.randomUUID()+".tcad");
            Files.copy(target.toPath(),copy.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
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
