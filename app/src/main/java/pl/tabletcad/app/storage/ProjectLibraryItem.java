package pl.tabletcad.app.storage;

import java.text.Normalizer;
import java.util.*;
import pl.tabletcad.app.model.CadProject;

/** One validated document snapshot and its file metadata, independent of Android UI. */
public final class ProjectLibraryItem {
    public enum Sort { RECENT, OLDEST, NAME_ASC, NAME_DESC }
    public enum Kind { ALL, NATIVE, STEP, STL, SKETCH, EMPTY }
    public final CadProject project;
    public final long modifiedAt;
    public final boolean recovered;
    private final String searchText,sortName;

    public ProjectLibraryItem(CadProject project,long modifiedAt,boolean recovered) {
        this.project=project;this.modifiedAt=modifiedAt;this.recovered=recovered;
        sortName=fold(project.name);
        searchText=fold(project.name+" "+project.drawingNumber+" "+project.drawingAuthor+" "+project.drawingMaterial);
    }
    public Kind kind() {
        if(project.importedMeshSource!=null)return Kind.STL;
        if(project.importedStep)return Kind.STEP;
        if(project.hasSolid())return Kind.NATIVE;
        return project.sketch.isEmpty()&&project.savedSketches.isEmpty()?Kind.EMPTY:Kind.SKETCH;
    }
    private static String fold(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
            .toLowerCase(Locale.ROOT).replace('ł','l');
    }
    public static List<ProjectLibraryItem> select(List<ProjectLibraryItem> source,String query,Kind kind,Sort sort) {
        String normalized=fold(query==null?"":query).trim();
        String[] tokens=normalized.isEmpty()?new String[0]:normalized.split("\\s+");
        List<ProjectLibraryItem> result=new ArrayList<>();
        for(ProjectLibraryItem item:source) {
            if(kind!=Kind.ALL&&item.kind()!=kind)continue;
            boolean matches=true;for(String token:tokens)if(!item.searchText.contains(token)){matches=false;break;}
            if(matches)result.add(item);
        }
        Comparator<ProjectLibraryItem> byName=Comparator.comparing(item->item.sortName);
        Comparator<ProjectLibraryItem> order=sort==Sort.NAME_ASC?byName:sort==Sort.NAME_DESC?byName.reversed():
            sort==Sort.OLDEST?Comparator.comparingLong(item->item.modifiedAt):Comparator.<ProjectLibraryItem>comparingLong(item->item.modifiedAt).reversed();
        result.sort(order.thenComparing(item->item.project.id));
        return result;
    }
}
