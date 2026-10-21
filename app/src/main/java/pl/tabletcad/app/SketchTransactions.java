package pl.tabletcad.app;

import java.util.*;
import org.json.*;
import pl.tabletcad.app.model.*;

/** Persisted sketch draft, its unchanged solid graph and explicit profile lineage. */
public final class SketchTransactions {
    private SketchTransactions() {}
    public static boolean active(CadProject p){return p.sketchEditBaseline!=null;}
    public static CadProject baseline(CadProject p) {
        if(!active(p))throw new IllegalArgumentException("Brak rozpoczętej edycji szkicu");
        try{return CadProject.fromJson(new JSONObject(p.sketchEditBaseline));}
        catch(JSONException e){throw new IllegalArgumentException("Nie można odtworzyć stanu sprzed edycji",e);}
    }
    private static Set<String> origins(CadProject baseline) {
        Set<String> used=new HashSet<>(baseline.committedProfileIds);used.add(baseline.featureBaseProfileId);
        if(!baseline.featureOperation.equals("none"))used.add(baseline.featureProfileId);
        for(String ref:new ArrayList<>(used))if(SketchRegion.isRef(ref))used.addAll(SketchRegion.ids(ref));
        Set<String> result=new LinkedHashSet<>();for(SketchPrimitive p:baseline.sketch)if(used.contains(p.id))result.add(p.id);return result;
    }
    public static void begin(CadProject p) {
        if(active(p))return;
        try {
            CadProject before=CadProject.fromJson(p.toJson());before.polylineDraft.clear();before.threePointDraft.clear();before.threePointTool="";
            p.sketchEditBaseline=before.toJson().toString();p.sketchLineage.clear();p.sketchBindings.clear();
            for(String id:origins(before))p.sketchLineage.put(id,id);
        }catch(JSONException e){throw new IllegalArgumentException("Nie można rozpocząć edycji szkicu",e);}
    }
    public static void validateLoaded(CadProject p)throws JSONException {
        if(!active(p)) {
            if(!p.sketchLineage.isEmpty()||!p.sketchBindings.isEmpty())throw new JSONException("Powiązania szkicu bez sesji edycji");return;
        }
        JSONObject data=new JSONObject(p.sketchEditBaseline);
        if(data.has("sketchEditBaseline"))throw new JSONException("Zagnieżdżona edycja szkicu");
        CadProject before=CadProject.fromJson(data);
        if(!before.id.equals(p.id)||!before.sketchId.equals(p.sketchId)||!before.sketchPlane.equals(p.sketchPlane))throw new JSONException("Niezgodna tożsamość edytowanego szkicu");
        double[] frame={p.sketchOriginX,p.sketchOriginY,p.sketchOriginZ,p.sketchUx,p.sketchUy,p.sketchUz,p.sketchVx,p.sketchVy,p.sketchVz};
        double[] original={before.sketchOriginX,before.sketchOriginY,before.sketchOriginZ,before.sketchUx,before.sketchUy,before.sketchUz,before.sketchVx,before.sketchVy,before.sketchVz};
        if(!Arrays.equals(frame,original))throw new JSONException("Płaszczyzna zmieniła się podczas edycji");
        Set<String> ids=origins(before),geometry=new HashSet<>();for(SketchPrimitive entity:p.sketch)geometry.add(entity.id);
        for(Map.Entry<String,String> entry:p.sketchLineage.entrySet())if(!geometry.contains(entry.getKey())||!ids.contains(entry.getValue()))throw new JSONException("Niepoprawne pochodzenie geometrii szkicu");
        for(Map.Entry<String,String> entry:p.sketchBindings.entrySet())if(!ids.contains(entry.getKey())||entry.getValue().isEmpty())throw new JSONException("Niepoprawne przypisanie profilu");
    }
    public static void replace(CadProject p,String id,List<SketchPrimitive> pieces) {
        if(!active(p))return;String origin=p.sketchLineage.remove(id);
        if(origin!=null)for(SketchPrimitive piece:pieces)p.sketchLineage.put(piece.id,origin);
    }
    public static void join(CadProject p,SketchContours.Result result) {
        if(!active(p))return;Set<String> parents=new HashSet<>();
        for(String id:result.consumed){String origin=p.sketchLineage.remove(id);if(origin!=null)parents.add(origin);}
        if(parents.size()==1)p.sketchLineage.put(result.profile.id,parents.iterator().next());
        // A merge of different source regions must be explicitly assigned at Finish.
    }
    public static void prune(CadProject p) {
        Set<String> ids=new HashSet<>();for(SketchPrimitive entity:p.sketch)ids.add(entity.id);p.sketchLineage.keySet().retainAll(ids);
    }
    private static Set<String> plainOrigins(CadProject baseline) {
        Set<String> used=new HashSet<>();for(String r:baseline.committedProfileIds)if(!SketchRegion.isRef(r))used.add(r);
        if(!SketchRegion.isRef(baseline.featureBaseProfileId))used.add(baseline.featureBaseProfileId);
        if(!baseline.featureOperation.equals("none"))used.add(baseline.featureProfileId);
        for(String r:baseline.committedProfileIds)if(SketchRegion.isRef(r)&&!SketchArea.isRef(r))used.addAll(SketchRegion.ids(r));
        if(SketchRegion.isRef(baseline.featureBaseProfileId)&&!SketchArea.isRef(baseline.featureBaseProfileId))used.addAll(SketchRegion.ids(baseline.featureBaseProfileId));
        return used;
    }
    private static SketchPrimitive resolved(CadProject p,String origin) {
        String explicit=p.sketchBindings.get(origin);SketchPrimitive found=null;
        // Elements used only by sketch areas may be open lines/arcs; profiles of plain operations must stay closed.
        boolean areaOnly=!plainOrigins(baseline(p)).contains(origin);
        for(SketchPrimitive entity:p.sketch)if(entity.closedProfile()||areaOnly&&SketchArea.element(entity)) {
            if(explicit!=null){if(entity.id.equals(explicit))return entity;}
            else if(origin.equals(p.sketchLineage.get(entity.id))){if(found!=null)return null;found=entity;}
        }
        return explicit==null?found:null;
    }
    public static List<String> unresolved(CadProject p) {
        List<String> result=new ArrayList<>();for(String id:origins(baseline(p)))if(resolved(p,id)==null)result.add(id);return result;
    }
    public static void bind(CadProject p,String origin,String profile) {
        if(!origins(baseline(p)).contains(origin))throw new IllegalArgumentException("Nieznany profil źródłowy");
        for(SketchPrimitive entity:p.sketch)if(entity.id.equals(profile)&&entity.closedProfile()){p.sketchBindings.put(origin,profile);return;}
        throw new IllegalArgumentException("Wybierz zamknięty profil");
    }
    private static double[] profile(double[] old,SketchPrimitive shape,int prefix,int offset) {
        return ProfileDependencies.replace(old,shape,prefix==7);
    }
    public static CadProject candidate(CadProject draft) {
        if(!draft.threePointDraft.isEmpty())throw new IllegalArgumentException("Najpierw zakończ konstrukcję trzypunktową");
        if(!draft.polylineDraft.isEmpty())throw new IllegalArgumentException("Najpierw zakończ polilinię");
        List<String> missing=unresolved(draft);if(!missing.isEmpty())throw new IllegalArgumentException("Domknij i połącz profil źródłowy lub wskaż jego zamiennik");
        try {
            CadProject result=CadProject.fromJson(draft.toJson()),before=baseline(draft);
            String oldBase=result.featureBaseProfileId,oldActive=result.featureProfileId;
            List<String> oldIds=new ArrayList<>(result.committedProfileIds);
            for(String origin:origins(before)) {
                SketchPrimitive shape=resolved(result,origin);
                if(origin.equals(oldBase)&&result.featureBase!=null){result.featureBase=profile(result.featureBase,shape,7,1);result.featureBaseProfileId=shape.id;}
                for(int i=0;i<oldIds.size();i++)if(origin.equals(oldIds.get(i))){result.committedFeatures.set(i,profile(result.committedFeatures.get(i),shape,16,2));result.committedProfileIds.set(i,shape.id);}
                if(origin.equals(oldActive))result.featureProfileId=shape.id;
                // Regions keep their element order; each origin is swapped for its successor inside the record.
                if(SketchRegion.isRef(result.featureBaseProfileId)&&SketchRegion.mentions(SketchRegion.isRef(oldBase)?oldBase:"",origin)&&result.featureBase!=null&&SketchRegion.mentions(result.featureBaseProfileId,origin)){
                    result.featureBase=SketchRegion.replace(result.featureBase,result.featureBaseProfileId,origin,shape);result.featureBaseProfileId=SketchRegion.rename(result.featureBaseProfileId,origin,shape.id);}
                for(int i=0;i<oldIds.size();i++){String ref=result.committedProfileIds.get(i);if(SketchRegion.isRef(oldIds.get(i))&&SketchRegion.mentions(oldIds.get(i),origin)&&SketchRegion.mentions(ref,origin)){
                    result.committedFeatures.set(i,SketchRegion.replace(result.committedFeatures.get(i),ref,origin,shape));result.committedProfileIds.set(i,SketchRegion.rename(ref,origin,shape.id));}}
            }
            result.sketchExtruded=before.sketchExtruded;
            result.sketchEditBaseline=null;result.sketchLineage.clear();result.sketchBindings.clear();
            result.savedSketches.removeIf(s->result.sketchId.equals(s.optString("id")));result.archiveCurrentSketch();return result;
        }catch(JSONException e){throw new IllegalArgumentException("Nie można zatwierdzić szkicu",e);}
    }
}
