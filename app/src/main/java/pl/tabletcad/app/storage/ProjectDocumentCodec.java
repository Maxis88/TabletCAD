package pl.tabletcad.app.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import pl.tabletcad.app.model.CadProject;
import pl.tabletcad.app.SketchTransactions;

/** Uses the same schema reader as opening and saving .tcad documents. */
public final class ProjectDocumentCodec implements ProjectLibraryFiles.Codec {
    private CadProject read(byte[] data)throws IOException {
        try{return CadProject.fromJson(new JSONObject(new String(data,StandardCharsets.UTF_8)));}
        catch(JSONException|IllegalArgumentException error){throw new IOException("Nie można odczytać dokumentu CAD",error);}
    }
    @Override public ProjectLibraryFiles.Info inspect(byte[] data)throws IOException {
        CadProject p=read(data);List<String> assets=new ArrayList<>();if(p.importedStep)assets.add(p.importedStepSource);if(p.importedMeshSource!=null)assets.add(p.importedMeshSource);
        return new ProjectLibraryFiles.Info(p.id,p.name,assets);
    }
    @Override public byte[] copy(byte[] data,String id,String name,Map<String,String> assets)throws IOException {
        CadProject p=read(data);if(SketchTransactions.active(p))throw new IOException("Najpierw zakończ lub anuluj aktywną edycję szkicu");
        try {
            JSONObject json=p.toJson();json.put("id",id);json.put("name",name);
            if(p.importedStep)json.put("importedStepSource",assets.get(p.importedStepSource));
            if(p.importedMeshSource!=null)json.put("importedMeshSource",assets.get(p.importedMeshSource));
            return CadProject.fromJson(json).toJson().toString(2).getBytes(StandardCharsets.UTF_8);
        }catch(JSONException|IllegalArgumentException error){throw new IOException("Nie można utworzyć kopii dokumentu",error);}
    }
}
