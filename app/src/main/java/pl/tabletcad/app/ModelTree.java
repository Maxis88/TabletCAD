package pl.tabletcad.app;

import java.util.*;
import org.json.*;
import pl.tabletcad.app.model.*;

/** A sketch part is an explicit set of source entity UUIDs used by one body. */
public final class ModelTree {
    private ModelTree() {}
    /** Pseudo body id of the construction geometry group. */
    public static final int CONSTRUCTION=-1;
    public static final class Row {
        public final int body;public final String sketchId,name,key,datum;public final JSONObject snapshot;public final Set<String> entities;
        Row(int body,String id,String name,JSONObject snapshot,Set<String> ids){this(body,id,name,snapshot,ids,null);}
        Row(int body,String id,String name,JSONObject snapshot,Set<String> ids,String datum){this.body=body;this.sketchId=id;this.name=name;this.snapshot=snapshot;this.entities=ids;this.datum=datum;key=datum!=null?"datum:"+datum:body+":"+(id==null?"body":id);}
        public boolean sketch(){return snapshot!=null;}
        public boolean datum(){return datum!=null;}
    }
    private static Set<Integer> uses(CadProject p,String id) {
        Set<Integer> result=new LinkedHashSet<>();if(id.equals(p.featureBaseProfileId))result.add(1);
        for(int i=0;i<p.committedProfileIds.size();i++)if(id.equals(p.committedProfileIds.get(i)))result.add(i<p.featureBodyOutputs.size()?p.featureBodyOutputs.get(i):1);
        if(id.equals(p.featureProfileId)&&!p.featureOperation.equals("none"))result.add(p.activeBodyOutput>0?p.activeBodyOutput:1);
        return result;
    }
    public static List<Row> rows(CadProject p,Set<Integer> collapsed) {
        try {
            List<JSONObject> snapshots=new ArrayList<>();for(JSONObject snapshot:p.savedSketches)if(!p.sketchId.equals(snapshot.optString("id")))snapshots.add(snapshot);snapshots.add(p.captureSketch());
            Map<Integer,List<Row>> parts=new LinkedHashMap<>();for(Integer id:BodyGraph.bodies(p))parts.put(id,new ArrayList<>());parts.put(0,new ArrayList<>());
            for(JSONObject snapshot:snapshots) {
                Map<Integer,Set<String>> scope=new LinkedHashMap<>();JSONArray geometry=snapshot.getJSONArray("entities");
                for(int i=0;i<geometry.length();i++){String id=geometry.getJSONObject(i).getString("id");Set<Integer> used=uses(p,id);if(used.isEmpty())used.add(0);for(Integer body:used)scope.computeIfAbsent(body,k->new LinkedHashSet<>()).add(id);}
                if(geometry.length()==0&&snapshot.getString("id").equals(p.sketchId))scope.put(0,new LinkedHashSet<>());
                for(Map.Entry<Integer,Set<String>> entry:scope.entrySet())parts.computeIfAbsent(entry.getKey(),k->new ArrayList<>()).add(new Row(entry.getKey(),snapshot.getString("id"),snapshot.optString("name","Szkic"),snapshot,entry.getValue()));
            }
            List<Row> result=new ArrayList<>();for(Map.Entry<Integer,List<Row>> entry:parts.entrySet()) {
                int body=entry.getKey();if(body==0&&entry.getValue().isEmpty())continue;
                result.add(new Row(body,null,body==0?"Szkice nieprzypisane":BodyGraph.name(p,body),null,new LinkedHashSet<>()));
                if(!collapsed.contains(body))result.addAll(entry.getValue());
            }
            if(!p.datums.isEmpty()) {
                result.add(new Row(CONSTRUCTION,null,"Konstrukcja",null,new LinkedHashSet<>()));
                if(!collapsed.contains(CONSTRUCTION))for(Datum d:p.datums)result.add(new Row(CONSTRUCTION,null,d.name,null,new LinkedHashSet<>(),d.id));
            }
            return result;
        }catch(JSONException error){throw new IllegalArgumentException("Nie można odczytać drzewa szkiców",error);}
    }
    public static CadProject display(CadProject model,Row row) {
        try {
            CadProject display=CadProject.blank();display.restoreSketch(row.snapshot);display.sketch.removeIf(p->!row.entities.contains(p.id));
            for(int i=0;i<display.sketch.size();i++) {
                SketchPrimitive p=display.sketch.get(i);
                if(p.id.equals(model.featureBaseProfileId)&&model.featureBase!=null)p=p.restoreFeature(model.featureBase,1,7);
                for(int j=0;j<model.committedProfileIds.size();j++)if(p.id.equals(model.committedProfileIds.get(j)))p=p.restoreFeature(model.committedFeatures.get(j),2,16);
                display.sketch.set(i,p);
            }return display;
        }catch(JSONException error){throw new IllegalArgumentException("Nie można odtworzyć widoku szkicu",error);}
    }
}
