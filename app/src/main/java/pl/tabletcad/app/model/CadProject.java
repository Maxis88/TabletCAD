package pl.tabletcad.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Versioned, geometry-independent source of truth for a TabletCAD document. */
public final class CadProject {
    public static final int CURRENT_SCHEMA = 29;
    public String sketchEditBaseline;
    public final java.util.Map<String,String> sketchLineage=new java.util.LinkedHashMap<>();
    public final java.util.Map<String,String> sketchBindings=new java.util.LinkedHashMap<>();
    public boolean emptyDocument;
    public final List<pl.tabletcad.app.SketchConstraints.Link> coincidences = new ArrayList<>();
    public final List<double[]> polylineDraft = new ArrayList<>();
    public final List<double[]> threePointDraft = new ArrayList<>();
    public String threePointTool="";
    public int polygonSides=6;
    public String drawingNumber = "", drawingAuthor = "", drawingMaterial = "", drawingRevision = "A";
    public double drawingSectionFraction = .5;

    public final String id;
    public String name;
    public String units;
    public double width;
    public double height;
    public double depth;
    public String sketchPlane;
    public boolean sketchExtruded;
    public boolean importedStep;
    public String importedStepSource = "imported.step";
    public String importedMeshSource;
    public double filletRadius;
    public double sketchOriginX, sketchOriginY, sketchOriginZ;
    public double sketchUx = 1.0, sketchUy, sketchUz;
    public double sketchVx, sketchVy = 1.0, sketchVz;
    public String featureOperation = "none";
    public double featureDepth = 10.0;
    public boolean featureSymmetric;
    public String featureProfileId;
    /** Committed Boolean features: type, cut, bounds, depth, origin, U, V. */
    public final List<double[]> committedFeatures = new ArrayList<>();
    public final List<String> committedProfileIds = new ArrayList<>();
    public double[] featureBase;
    public String featureBaseProfileId;
    public final List<SketchPrimitive> sketch = new ArrayList<>();
    public String sketchId = UUID.randomUUID().toString();
    public final List<JSONObject> savedSketches = new ArrayList<>();

    public CadProject() {
        this(UUID.randomUUID().toString(), "Bez nazwy", "mm", 80.0, 48.0, 32.0, "XY");
    }

    public static CadProject blank() {
        CadProject project=new CadProject();project.emptyDocument=true;return project;
    }

    public boolean hasSolid() {
        return !emptyDocument||importedStep||importedMeshSource!=null||featureBase!=null
            ||sketchExtruded||!committedFeatures.isEmpty()||!featureOperation.equals("none");
    }

    private CadProject(String id, String name, String units, double width, double height,
                       double depth, String sketchPlane) {
        this.id = id;
        this.name = name;
        this.units = units;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.sketchPlane = sketchPlane;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schema", CURRENT_SCHEMA);
        if(sketchEditBaseline!=null) {
            root.put("sketchEditBaseline",sketchEditBaseline);
            root.put("sketchLineage",new JSONObject(sketchLineage));root.put("sketchBindings",new JSONObject(sketchBindings));
        }
        root.put("emptyDocument",emptyDocument);
        root.put("coincidences",pl.tabletcad.app.SketchConstraints.toJson(coincidences));
        JSONArray draft=new JSONArray();
        for(double[] point:polylineDraft)draft.put(new JSONArray().put(point[0]).put(point[1]));
        root.put("polylineDraft",draft);
        try{pl.tabletcad.app.ThreePointDraft.validate(threePointTool,threePointDraft);}
        catch(IllegalArgumentException error){throw new JSONException(error.getMessage());}
        root.put("polygonSides",polygonSides);
        if(!threePointDraft.isEmpty()) {
            JSONArray points=new JSONArray();for(double[] p:threePointDraft)points.put(new JSONArray().put(p[0]).put(p[1]));
            root.put("threePointTool",threePointTool);root.put("threePointDraft",points);
        }
        root.put("drawingSectionFraction", drawingSectionFraction);
        JSONObject drawingData = new JSONObject();
        drawingData.put("number",drawingNumber).put("author",drawingAuthor)
            .put("material",drawingMaterial).put("revision",drawingRevision);
        root.put("drawingData",drawingData);
        root.put("id", id);
        root.put("name", name);
        root.put("units", units);
        root.put("sketchPlane", sketchPlane);
        root.put("sketchId", sketchId);
        JSONArray saved = new JSONArray();
        for (JSONObject snapshot : savedSketches) saved.put(snapshot);
        root.put("savedSketches", saved);
        root.put("sketchExtruded", sketchExtruded);
        root.put("importedStep", importedStep);
        root.put("importedStepSource", importedStepSource);
        root.put("importedMeshSource", importedMeshSource);
        root.put("filletRadius", filletRadius);
        JSONObject planeFrame = new JSONObject();
        planeFrame.put("ox", sketchOriginX).put("oy", sketchOriginY).put("oz", sketchOriginZ);
        planeFrame.put("ux", sketchUx).put("uy", sketchUy).put("uz", sketchUz);
        planeFrame.put("vx", sketchVx).put("vy", sketchVy).put("vz", sketchVz);
        root.put("sketchFrame", planeFrame);
        root.put("featureOperation", featureOperation);
        root.put("featureDepth", featureDepth);
        root.put("featureSymmetric",featureSymmetric);
        root.put("featureProfileId", featureProfileId);
        JSONArray history = new JSONArray();
        for (double[] values : committedFeatures) {
            JSONArray item = new JSONArray();
            for (double value : values) item.put(value);
            history.put(item);
        }
        root.put("committedFeatures", history);
        JSONArray profileIds = new JSONArray();
        for (String profileId : committedProfileIds) profileIds.put(profileId);
        root.put("committedProfileIds", profileIds);
        if (featureBase != null) {
            root.put("featureBaseProfileId", featureBaseProfileId);
            JSONArray base = new JSONArray();
            for (double value : featureBase) base.put(value);
            root.put("featureBase", base);
        }
        JSONObject feature = new JSONObject();
        feature.put("type", "extrude");
        feature.put("width", width);
        feature.put("height", height);
        feature.put("depth", depth);
        root.put("baseFeature", feature);
        JSONArray entities = new JSONArray();
        for (SketchPrimitive primitive : sketch) entities.put(primitive.toJson());
        root.put("sketch", entities);
        return root;
    }

    public static CadProject fromJson(JSONObject root) throws JSONException {
        int schema = root.getInt("schema");
        if (schema < 1 || schema > CURRENT_SCHEMA) throw new JSONException("Unsupported schema: " + schema);
        JSONObject feature = root.getJSONObject("baseFeature");
        CadProject project = new CadProject(
            root.getString("id"), root.optString("name", "Bez nazwy"),
            root.optString("units", "mm"), feature.getDouble("width"),
            feature.getDouble("height"), feature.getDouble("depth"),
            root.optString("sketchPlane", "XY"));
        project.emptyDocument=root.optBoolean("emptyDocument",false);
        JSONArray pending=root.optJSONArray("polylineDraft");
        if(pending!=null)for(int i=0;i<pending.length();i++) {
            JSONArray point=pending.getJSONArray(i);if(point.length()!=2)throw new JSONException("Invalid draft point");
            try{pl.tabletcad.app.PolylineDraft.add(project.polylineDraft,point.getDouble(0),point.getDouble(1));}
            catch(IllegalArgumentException error){throw new JSONException(error.getMessage());}
        }
        project.threePointTool=root.optString("threePointTool","");
        JSONArray curvePending=root.optJSONArray("threePointDraft");
        if(curvePending!=null)for(int i=0;i<curvePending.length();i++) {
            JSONArray p=curvePending.getJSONArray(i);if(p.length()!=2)throw new JSONException("Niepoprawny punkt konstrukcji");
            project.threePointDraft.add(new double[]{p.getDouble(0),p.getDouble(1)});
        }
        try{pl.tabletcad.app.ThreePointDraft.validate(project.threePointTool,project.threePointDraft);}
        catch(IllegalArgumentException error){throw new JSONException(error.getMessage());}
        if(!project.polylineDraft.isEmpty()&&!project.threePointDraft.isEmpty())throw new JSONException("Dwie różne niedokończone konstrukcje");
        double sides=root.optDouble("polygonSides",6);
        if(!Double.isFinite(sides)||sides!=Math.floor(sides)||sides<3||sides>128)throw new JSONException("Liczba boków: 3–128");
        project.polygonSides=(int)sides;
        project.sketchExtruded = schema >= 2 && root.optBoolean("sketchExtruded", false);
        project.drawingSectionFraction=root.optDouble("drawingSectionFraction",.5);
        JSONObject drawingData=root.optJSONObject("drawingData");
        if(drawingData!=null) {
            project.drawingNumber=drawingData.optString("number","");
            project.drawingAuthor=drawingData.optString("author","");
            project.drawingMaterial=drawingData.optString("material","");
            project.drawingRevision=drawingData.optString("revision","A");
        }
        if(project.drawingSectionFraction<=0||project.drawingSectionFraction>=1)
            project.drawingSectionFraction=.5;
        project.sketchId = root.optString("sketchId", project.sketchId);
        JSONArray saved = root.optJSONArray("savedSketches");
        if (saved != null) for (int i = 0; i < saved.length(); i++) {
            project.savedSketches.add(saved.getJSONObject(i));
        }
        project.importedStep = schema >= 3 && root.optBoolean("importedStep", false);
        project.importedStepSource = root.optString("importedStepSource", "imported.step");
        project.importedMeshSource = root.optString("importedMeshSource", null);
        if (project.importedMeshSource != null && !project.importedMeshSource.matches("[A-Za-z0-9_.-]+"))
            throw new JSONException("Invalid mesh source name");
        if (!project.importedStepSource.matches("[A-Za-z0-9_.-]+")) {
            throw new JSONException("Invalid STEP source name");
        }
        project.filletRadius = schema >= 4 ? root.optDouble("filletRadius", 0.0) : 0.0;
        if (schema >= 5 && root.has("sketchFrame")) {
            JSONObject frame = root.getJSONObject("sketchFrame");
            project.sketchOriginX = frame.optDouble("ox", 0.0);
            project.sketchOriginY = frame.optDouble("oy", 0.0);
            project.sketchOriginZ = frame.optDouble("oz", 0.0);
            project.sketchUx = frame.optDouble("ux", 1.0);
            project.sketchUy = frame.optDouble("uy", 0.0);
            project.sketchUz = frame.optDouble("uz", 0.0);
            project.sketchVx = frame.optDouble("vx", 0.0);
            project.sketchVy = frame.optDouble("vy", 1.0);
            project.sketchVz = frame.optDouble("vz", 0.0);
        } else {
            project.setPrincipalSketchFrame(project.sketchPlane);
        }
        project.featureOperation = schema >= 6 ? root.optString("featureOperation", "none") : "none";
        project.featureDepth = schema >= 6 ? root.optDouble("featureDepth", 10.0) : 10.0;
        project.featureSymmetric=root.optBoolean("featureSymmetric",false);
        JSONArray history = root.optJSONArray("committedFeatures");
        JSONArray base = root.optJSONArray("featureBase");
        project.featureBaseProfileId = root.optString("featureBaseProfileId", null);
        if (base != null) {
            validateFeatureArray(base,7);
            project.featureBase = new double[base.length()];
            for (int j = 0; j < base.length(); j++) project.featureBase[j] = base.getDouble(j);
        }
        if (history != null) {
            for (int i = 0; i < history.length(); i++) {
                JSONArray item = history.getJSONArray(i);
                validateFeatureArray(item,16);
                double[] values = new double[item.length()];
                for (int j = 0; j < values.length; j++) values[j] = item.getDouble(j);
                project.committedFeatures.add(values);
                JSONArray profileIds = root.optJSONArray("committedProfileIds");
                project.committedProfileIds.add(profileIds == null ? "" : profileIds.optString(i, ""));
            }
        }
        JSONArray entities = root.optJSONArray("sketch");
        if (entities != null) {
            for (int i = 0; i < entities.length(); i++) {
                project.sketch.add(SketchPrimitive.fromJson(entities.getJSONObject(i)));
            }
        }
        project.coincidences.addAll(pl.tabletcad.app.SketchConstraints.fromJson(root.optJSONArray("coincidences"),project.sketch));
        project.featureProfileId = root.optString("featureProfileId", null);
        if (project.featureProfileId == null && project.sketchExtruded) {
            for (int i = project.sketch.size() - 1; i >= 0; i--) {
                SketchPrimitive primitive = project.sketch.get(i);
                if (primitive.type.equals("Prostokąt") || primitive.type.equals("Okrąg")) {
                    project.featureProfileId = primitive.id;
                    break;
                }
            }
        }
        project.sketchEditBaseline=root.optString("sketchEditBaseline",null);
        for(String field:new String[]{"sketchLineage","sketchBindings"}) {
            JSONObject data=root.optJSONObject(field);if(data==null)continue;
            java.util.Map<String,String> map=field.equals("sketchLineage")?project.sketchLineage:project.sketchBindings;
            java.util.Iterator<String> keys=data.keys();while(keys.hasNext()){String key=keys.next();map.put(key,data.getString(key));}
        }
        pl.tabletcad.app.SketchTransactions.validateLoaded(project);
        return project;
    }

    public void setPrincipalSketchFrame(String plane) {
        sketchOriginX = sketchOriginY = sketchOriginZ = 0.0;
        if ("XZ".equals(plane)) {
            sketchUx = 1.0; sketchUy = 0.0; sketchUz = 0.0;
            sketchVx = 0.0; sketchVy = 0.0; sketchVz = 1.0;
        } else if ("YZ".equals(plane)) {
            sketchUx = 0.0; sketchUy = 1.0; sketchUz = 0.0;
            sketchVx = 0.0; sketchVy = 0.0; sketchVz = 1.0;
        } else {
            sketchUx = 1.0; sketchUy = 0.0; sketchUz = 0.0;
            sketchVx = 0.0; sketchVy = 1.0; sketchVz = 0.0;
        }
    }

    public JSONObject captureSketch() throws JSONException {
        JSONObject snapshot = new JSONObject();
        snapshot.put("id", sketchId).put("plane", sketchPlane);
        JSONArray frame = new JSONArray();
        double[] values = {sketchOriginX,sketchOriginY,sketchOriginZ,
            sketchUx,sketchUy,sketchUz,sketchVx,sketchVy,sketchVz};
        for (double value : values) frame.put(value);
        snapshot.put("frame", frame);
        JSONArray entities = new JSONArray();
        for (SketchPrimitive p : sketch) entities.put(p.toJson());
        snapshot.put("entities", entities);
        snapshot.put("coincidences",pl.tabletcad.app.SketchConstraints.toJson(coincidences));
        return snapshot;
    }

    public void archiveCurrentSketch() throws JSONException {
        if(!threePointDraft.isEmpty())throw new JSONException("Zakończ konstrukcję trzypunktową przed zmianą szkicu");
        if(!polylineDraft.isEmpty())throw new JSONException("Zakończ polilinię przed zmianą szkicu");
        if (sketch.isEmpty()) return;
        JSONObject snapshot = captureSketch();
        for (int i = 0; i < savedSketches.size(); i++) {
            if (sketchId.equals(savedSketches.get(i).optString("id"))) {
                savedSketches.set(i, snapshot);
                return;
            }
        }
        savedSketches.add(snapshot);
    }

    public void restoreSketch(JSONObject snapshot) throws JSONException {
        if(!threePointDraft.isEmpty())throw new JSONException("Zakończ konstrukcję trzypunktową przed zmianą szkicu");
        if(!polylineDraft.isEmpty())throw new JSONException("Zakończ polilinię przed zmianą szkicu");
        JSONArray frame = snapshot.getJSONArray("frame");
        if (frame.length() != 9) throw new JSONException("Invalid sketch frame");
        List<SketchPrimitive> restored = new ArrayList<>();
        JSONArray entities = snapshot.getJSONArray("entities");
        for (int i = 0; i < entities.length(); i++) {
            SketchPrimitive p = SketchPrimitive.fromJson(entities.getJSONObject(i));
            if (p.id.equals(featureBaseProfileId) && featureBase != null) {
                p = p.restoreFeature(featureBase,1,7);
            }
            // A feature edited from the timeline is authoritative for its linked profile.
            for (int j = 0; j < committedProfileIds.size(); j++) {
                if (p.id.equals(committedProfileIds.get(j))) {
                    double[] f = committedFeatures.get(j);
                    p = p.restoreFeature(f,2,16);
                }
            }
            restored.add(p);
        }
        List<pl.tabletcad.app.SketchConstraints.Link> restoredLinks=pl.tabletcad.app.SketchConstraints.fromJson(snapshot.optJSONArray("coincidences"),restored);
        sketchId = snapshot.getString("id"); sketchPlane = snapshot.getString("plane");
        sketchOriginX=frame.getDouble(0); sketchOriginY=frame.getDouble(1); sketchOriginZ=frame.getDouble(2);
        sketchUx=frame.getDouble(3); sketchUy=frame.getDouble(4); sketchUz=frame.getDouble(5);
        sketchVx=frame.getDouble(6); sketchVy=frame.getDouble(7); sketchVz=frame.getDouble(8);
        sketch.clear(); sketch.addAll(restored);
        coincidences.clear();coincidences.addAll(restoredLinks);
    }

    private static void validateFeatureArray(JSONArray data,int prefix) throws JSONException {
        if(data.length()<prefix) throw new JSONException("Invalid feature length");
        if(data.getDouble(0)==-3) {
            double[] f=new double[data.length()];for(int i=0;i<f.length;i++)f[i]=data.getDouble(i);
            try{pl.tabletcad.app.Revolve.validate(f);if(prefix==7&&f[1]!=0)throw new IllegalArgumentException("Bryła bazowa nie może być wycięciem");}
            catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}return;
        }
        if(prefix==16&&(data.getDouble(0)==-1||data.getDouble(0)==-2||data.getDouble(0)==-4||data.getDouble(0)==-5)) {
            double[] feature=new double[data.length()];for(int i=0;i<feature.length;i++)feature[i]=data.getDouble(i);
            try{if(feature[0]==-2||feature[0]==-5)pl.tabletcad.app.EdgeChamfer.validate(feature);else pl.tabletcad.app.EdgeFillet.validate(feature);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
            return;
        }
        double type=data.getDouble(0);int tail=0;
        if(type>=1001&&type<=1128&&type==Math.floor(type))tail=((int)type-1000)*7;
        else if(type>=5&&type<=258&&type==Math.floor(type)) tail=((int)type-2)*2;
        else if(type!=0&&type!=1) throw new JSONException("Invalid feature type");
        if(data.length()!=prefix+tail) throw new JSONException("Invalid polygon length");
        for(int i=0;i<data.length();i++) if(!Double.isFinite(data.getDouble(i))) throw new JSONException("Non-finite feature");
        try {
            if(prefix==7)pl.tabletcad.app.FeatureDirection.basePlane(data.getDouble(5));
            else pl.tabletcad.app.FeatureDirection.validateOperation(data.getDouble(1));
        }catch(IllegalArgumentException error){throw new JSONException(error.getMessage());}
        if(tail>0) {
            double[] vertices=new double[tail];for(int i=0;i<tail;i++) vertices[i]=data.getDouble(prefix+i);
            try{if(type>=1001)pl.tabletcad.app.SketchCurves.validate(vertices,true);else pl.tabletcad.app.SketchContours.validate(vertices);}
            catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
        }
    }

    public boolean isProfileDimensioned(String id) {
        if(id==null)return false;
        for(pl.tabletcad.app.SketchConstraints.Link link:coincidences)if(link.uses(id))return true;
        for(SketchPrimitive p:sketch)if(p.id.equals(id))return p.driving!=null;
        for(JSONObject snapshot:savedSketches) {
            JSONArray links=snapshot.optJSONArray("coincidences");
            if(links!=null)for(int i=0;i<links.length();i++) {
                JSONObject link=links.optJSONObject(i);
                if(link!=null&&(id.equals(link.optString("a"))||id.equals(link.optString("b"))))return true;
            }
            JSONArray entities=snapshot.optJSONArray("entities");if(entities==null)continue;
            for(int i=0;i<entities.length();i++) {
                JSONObject entity=entities.optJSONObject(i);
                if(entity!=null&&id.equals(entity.optString("id")))return entity.has("driving");
            }
        }
        return false;
    }

    public boolean hasCoincidence(String id) {
        for(pl.tabletcad.app.SketchConstraints.Link link:coincidences)if(link.uses(id))return true;
        return false;
    }
    public void pruneCoincidences() {
        java.util.Set<String> ids=new java.util.HashSet<>();for(SketchPrimitive p:sketch)ids.add(p.id);
        coincidences.removeIf(link->!ids.contains(link.a)||!ids.contains(link.b));
    }
}
