package pl.tabletcad.app.storage;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import pl.tabletcad.app.model.CadProject;

/** Atomic local persistence with a last-known-good recovery copy. */
public final class ProjectRepository {
    private final File folder;
    private final File document;
    private final File temporary;
    private final File recovery;

    public ProjectRepository(Context context) {
        folder = new File(context.getFilesDir(), "projects");
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create projects folder");
        document = new File(folder, "autosave.tcad");
        temporary = new File(folder, "autosave.tcad.tmp");
        recovery = new File(folder, "autosave.tcad.bak");
    }

    public File importedStepFile() {
        return new File(folder, "imported.step");
    }

    public CadProject loadOrCreate() {
        CadProject loaded = tryLoad(document);
        if (loaded != null) return loaded;
        loaded = tryLoad(recovery);
        return loaded != null ? loaded : new CadProject();
    }

    public synchronized void save(CadProject project) {
        try {
            byte[] data = project.toJson().toString(2).getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(data);
                output.flush();
                output.getFD().sync();
            }
            if (document.exists()) {
                Files.copy(document.toPath(), recovery.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporary.toPath(), document.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception error) {
            throw new IllegalStateException("Project autosave failed", error);
        }
    }

    private CadProject tryLoad(File file) {
        if (!file.isFile()) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset != data.length) return null;
            return CadProject.fromJson(new JSONObject(new String(data, StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            return null;
        }
    }
}
