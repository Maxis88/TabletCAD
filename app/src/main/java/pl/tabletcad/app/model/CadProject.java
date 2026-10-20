package pl.tabletcad.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Versioned, geometry-independent source of truth for a TabletCAD document. */
public final class CadProject {
    public static final int CURRENT_SCHEMA = 37;
    public String sketchEditBaseline;
    public final java.util.Map<String,String> sketchLineage=new java.util.LinkedHashMap<>();
    public final java.util.Map<String,String> sketchBindings=new java.util.LinkedHashMap<>();
    public boolean emptyDocument;
    public final List<pl.tabletcad.app.SketchConstraints.Link> coincidences = new ArrayList<>();
    public final List<double[]> polylineDraft = new ArrayList<>();
    public final List<double[]> threePointDraft = new ArrayList<>();
    public String threePointTool="";
    public int polygonSides=6;
    public boolean splineDraft;
    public boolean drawingConstruction;
    public int bodyGraphStart=-1,nextBodyIndex=2,selectedBody=1,activeBodyTarget,activeBodyOutput;
    public final List<Integer> featureBodyTargets=new ArrayList<>(),featureBodyOutputs=new ArrayList<>();
    public final java.util.Map<Integer,String> bodyUuids=new java.util.LinkedHashMap<>(),bodyNames=new java.util.LinkedHashMap<>();
    public final java.util.Set<Integer> hiddenBodies=new java.util.LinkedHashSet<>();
    public final java.util.Map<Integer,double[]> extrusionExtents=new java.util.LinkedHashMap<>();
    public final java.util.Set<String> hiddenSketchParts=new java.util.LinkedHashSet<>();
    public String sketchName="Szkic";
    public String drawingNumber = "", drawingAuthor = "", drawingMaterial = "", drawingRevision = "A";
    /** User dimensions on the technical sheet (see TechnicalSheet.dimension). */
    public final java.util.List<double[]> drawingDimensions = new java.util.ArrayList<>();
    public double drawingSectionFraction = .5;
    /** Uniform density for all solids; 0 means unspecified. */
    public double materialDensityKgM3;
    /** Densities of individual bodies [kg/m³]; bodies without one use materialDensityKgM3. */
    public final java.util.Map<Integer,Double> bodyDensities=new java.util.TreeMap<>();
    /** Decimal places of lengths shown on the canvas (values are stored exactly in mm). */
    public int displayDecimals=3;
    /** Mesh export (STL/OBJ/3MF) chordal deviation [mm] and angular deviation [°]. */
    public double exportDeflection=.02,exportAngle=10;
    /** 3D printing overhang check: maximum overhang angle from vertical [°]. */
    public double overhangAngle=45;
    /** Named user parameters and the model values driven by them (see UserParameters). */
    public final List<pl.tabletcad.app.UserParameters.Parameter> parameters=new ArrayList<>();
    public final java.util.Map<String,String> parameterBindings=new java.util.LinkedHashMap<>();
    /** Construction planes, axes and points (see Datum). */
    public final List<Datum> datums=new ArrayList<>();
    /** Assembly components (groups of bodies, instances). */
    public final List<Component> components=new ArrayList<>();
    /** History indices of suppressed operations (kept in history, not evaluated). */
    public final java.util.Set<Integer> suppressedFeatures=new java.util.TreeSet<>();
    /** Hole index → edge-face references of its dimensioned centre (see HoleAssociation). */
    public final java.util.Map<Integer,double[]> holeReferences=new java.util.TreeMap<>();
    /** Rollback marker: operations historyMarker … historyMarkerCount−1 are not evaluated; -1 = end of history.
     *  Operations appended while the marker is set are moved to the marker on commit (FeatureHistory.settle). */
    public int historyMarker=-1,historyMarkerCount=-1;

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
        bodyUuids.put(1,UUID.nameUUIDFromBytes((id+":body:1").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
        this.name = name;
        this.units = units;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.sketchPlane = sketchPlane;
    }

    public JSONObject toJson() throws JSONException {
        try { pl.tabletcad.app.MaterialDensity.validate(materialDensityKgM3); }
        catch (IllegalArgumentException error) { throw new JSONException(error.getMessage()); }
        JSONObject root = new JSONObject();
        root.put("schema", CURRENT_SCHEMA);
        root.put("materialDensityKgM3", materialDensityKgM3);
        if(!bodyDensities.isEmpty()){JSONObject densities=new JSONObject();for(java.util.Map.Entry<Integer,Double> e:bodyDensities.entrySet()){try{pl.tabletcad.app.MaterialDensity.validate(e.getValue());}catch(IllegalArgumentException x){throw new JSONException(x.getMessage());}if(e.getValue()<=0)throw new JSONException("Gęstość bryły musi być dodatnia");densities.put(e.getKey().toString(),e.getValue());}root.put("bodyDensities",densities);}
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
        if(!drawingDimensions.isEmpty()){JSONArray dims=new JSONArray();for(double[] d:drawingDimensions){JSONArray row=new JSONArray();for(double v:d)row.put(v);dims.put(row);}root.put("drawingDimensions",dims);}
        root.put("id", id);
        root.put("name", name);
        root.put("splineDraft",splineDraft);
        root.put("drawingConstruction",drawingConstruction);
        root.put("bodyGraphStart",bodyGraphStart).put("nextBodyIndex",nextBodyIndex).put("selectedBody",selectedBody).put("activeBodyTarget",activeBodyTarget).put("activeBodyOutput",activeBodyOutput);
        root.put("featureBodyTargets",new JSONArray(featureBodyTargets)).put("featureBodyOutputs",new JSONArray(featureBodyOutputs));
        JSONObject bodyIds=new JSONObject(),names=new JSONObject();for(Integer key:bodyUuids.keySet())bodyIds.put(key.toString(),bodyUuids.get(key));for(Integer key:bodyNames.keySet())names.put(key.toString(),bodyNames.get(key));root.put("bodyUuids",bodyIds).put("bodyNames",names);
        JSONObject extents=new JSONObject();for(java.util.Map.Entry<Integer,double[]> e:extrusionExtents.entrySet())extents.put(e.getKey().toString(),new JSONArray(e.getValue()));root.put("extrusionExtents",extents);
        root.put("hiddenBodies",new JSONArray(hiddenBodies)).put("hiddenSketchParts",new JSONArray(hiddenSketchParts));
        root.put("sketchName",sketchName);
        if(!pl.tabletcad.app.Units.valid(units))throw new JSONException("Nieznana jednostka projektu: "+units);
        try{pl.tabletcad.app.Units.validateDecimals(displayDecimals);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
        root.put("units", units);
        root.put("displayDecimals", displayDecimals);
        try{pl.tabletcad.app.PrintExport.validate(exportDeflection,exportAngle);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
        root.put("exportDeflection",exportDeflection).put("exportAngle",exportAngle).put("overhangAngle",overhangAngle);
        if(!parameters.isEmpty())root.put("parameters",pl.tabletcad.app.UserParameters.toJson(this));
        if(!parameterBindings.isEmpty())root.put("parameterBindings",new JSONObject(parameterBindings));
        if(!suppressedFeatures.isEmpty())root.put("suppressed",new JSONArray(suppressedFeatures));
        if(!holeReferences.isEmpty()){JSONObject holes=new JSONObject();for(java.util.Map.Entry<Integer,double[]> e:holeReferences.entrySet()){double[] r=e.getValue();try{pl.tabletcad.app.HoleAssociation.validate(r);}catch(IllegalArgumentException x){throw new JSONException(x.getMessage());}JSONArray a=new JSONArray();for(int i=0;i<(Double.isNaN(r[5])?5:10);i++)a.put(r[i]);holes.put(e.getKey().toString(),a);}root.put("holeReferences",holes);}
        if(historyMarker>=0)root.put("historyMarker",historyMarker).put("historyMarkerCount",historyMarkerCount);
        if(!components.isEmpty()){JSONArray list=new JSONArray();for(Component c:components)list.put(c.toJson());root.put("components",list);}
        if(!datums.isEmpty()){JSONArray construction=new JSONArray();for(Datum d:datums)construction.put(d.toJson());root.put("construction",construction);}
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
        if(!pl.tabletcad.app.Units.valid(project.units))throw new JSONException("Nieznana jednostka projektu: "+project.units);
        project.displayDecimals=root.optInt("displayDecimals",3);
        pl.tabletcad.app.UserParameters.read(project,root);
        JSONArray construction=root.optJSONArray("construction");java.util.Set<String> datumIds=new java.util.HashSet<>();
        if(construction!=null)for(int i=0;i<construction.length();i++){Datum d=Datum.fromJson(construction.getJSONObject(i));if(!datumIds.add(d.id))throw new JSONException("Powtórzony element konstrukcyjny");project.datums.add(d);}
        try{pl.tabletcad.app.Units.validateDecimals(project.displayDecimals);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
        if (root.has("materialDensityKgM3")) {
            Object density = root.get("materialDensityKgM3");
            if (!(density instanceof Number)) throw new JSONException("Invalid material density");
            project.materialDensityKgM3 = ((Number)density).doubleValue();
            try { pl.tabletcad.app.MaterialDensity.validate(project.materialDensityKgM3); }
            catch (IllegalArgumentException error) { throw new JSONException(error.getMessage()); }
        }
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
        JSONArray sheetDimensions=root.optJSONArray("drawingDimensions");
        if(sheetDimensions!=null)for(int i=0;i<sheetDimensions.length()&&i<500;i++){JSONArray row=sheetDimensions.getJSONArray(i);double[] d=new double[row.length()];for(int j=0;j<d.length;j++)d[j]=row.getDouble(j);
            boolean ok=d.length==7;for(double v:d)ok&=Double.isFinite(v);
            if(!ok||d[0]<0||d[0]>3||d[0]!=Math.rint(d[0])||d[5]<0||d[5]>4||d[5]!=Math.rint(d[5])||Math.abs(d[6])>(d[5]>=3?180:100))throw new JSONException("Niepoprawny wymiar arkusza");
            project.drawingDimensions.add(d);}
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
        project.splineDraft=root.optBoolean("splineDraft",false);
        project.drawingConstruction=root.optBoolean("drawingConstruction",false);
        project.sketchName=root.optString("sketchName","Szkic");
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
            for(int i=0;i<project.committedFeatures.size();i++)if(pl.tabletcad.app.FeaturePattern.is(project.committedFeatures.get(i))) {
                try{pl.tabletcad.app.FeaturePattern.validateSources(project.committedFeatures,i);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
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
        project.bodyGraphStart=root.optInt("bodyGraphStart",-1);project.nextBodyIndex=root.optInt("nextBodyIndex",2);project.selectedBody=root.optInt("selectedBody",1);project.activeBodyTarget=root.optInt("activeBodyTarget",0);project.activeBodyOutput=root.optInt("activeBodyOutput",0);
        for(String field:new String[]{"featureBodyTargets","featureBodyOutputs"}){JSONArray values=root.optJSONArray(field);if(values!=null)for(int i=0;i<values.length();i++)(field.equals("featureBodyTargets")?project.featureBodyTargets:project.featureBodyOutputs).add(values.getInt(i));}
        for(String field:new String[]{"bodyUuids","bodyNames"}){JSONObject values=root.optJSONObject(field);if(values!=null){java.util.Iterator<String> keys=values.keys();while(keys.hasNext()){String key=keys.next();try{(field.equals("bodyUuids")?project.bodyUuids:project.bodyNames).put(Integer.parseInt(key),values.getString(key));}catch(NumberFormatException e){throw new JSONException("Niepoprawne ID bryły");}}}}
        JSONArray hidden=root.optJSONArray("hiddenBodies");if(hidden!=null)for(int i=0;i<hidden.length();i++)project.hiddenBodies.add(hidden.getInt(i));hidden=root.optJSONArray("hiddenSketchParts");if(hidden!=null)for(int i=0;i<hidden.length();i++)project.hiddenSketchParts.add(hidden.getString(i));
        JSONObject ranges=root.optJSONObject("extrusionExtents");if(ranges!=null){java.util.Iterator<String> keys=ranges.keys();while(keys.hasNext()){String key=keys.next();try {int index=Integer.parseInt(key);JSONArray range=ranges.getJSONArray(key);if(range.length()!=3||index<-1||index>=project.committedFeatures.size())throw new JSONException("Niepoprawny zakres wyciągania");double[] value={range.getDouble(0),range.getDouble(1),range.getDouble(2)};if(!Double.isFinite(value[0])||Math.abs(value[0])<.1||!Double.isFinite(value[1])||value[1]<0||value[1]>5000||(value[2]!=0&&value[2]!=1))throw new JSONException("Niepoprawny zakres wyciągania");project.extrusionExtents.put(index,value);}catch(NumberFormatException e){throw new JSONException("Niepoprawny indeks wyciągania");}}}
        try{pl.tabletcad.app.BodyGraph.validate(project);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
        project.exportDeflection=root.optDouble("exportDeflection",.02);project.exportAngle=root.optDouble("exportAngle",10);project.overhangAngle=root.optDouble("overhangAngle",45);
        try{pl.tabletcad.app.PrintExport.validate(project.exportDeflection,project.exportAngle);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
        if(!(project.overhangAngle>=10&&project.overhangAngle<=80))throw new JSONException("Kąt nawisu: 10–80°");
        JSONObject densities=root.optJSONObject("bodyDensities");
        if(densities!=null){java.util.Iterator<String> keys=densities.keys();while(keys.hasNext()){String key=keys.next();double value=densities.getDouble(key);int body;
            try{body=Integer.parseInt(key);}catch(NumberFormatException e){throw new JSONException("Niepoprawny identyfikator bryły gęstości");}
            if(!(value>0)||!Double.isFinite(value))throw new JSONException("Gęstość bryły musi być dodatnia");project.bodyDensities.put(body,value);}}
        int marker=root.optInt("historyMarker",-1),markerCount=root.optInt("historyMarkerCount",-1);
        if(marker>=0){if(marker>=markerCount||markerCount>project.committedFeatures.size())throw new JSONException("Niepoprawny znacznik historii");project.historyMarker=marker;project.historyMarkerCount=markerCount;}
        JSONObject holes=root.optJSONObject("holeReferences");
        if(holes!=null){java.util.Iterator<String> keys=holes.keys();while(keys.hasNext()){String key=keys.next();int index;try{index=Integer.parseInt(key);}catch(NumberFormatException e){throw new JSONException("Niepoprawny numer otworu");}
            JSONArray a=holes.getJSONArray(key);if(a.length()!=5&&a.length()!=10)throw new JSONException("Niepoprawne odniesienie otworu");double[] r=new double[10];java.util.Arrays.fill(r,Double.NaN);for(int i=0;i<a.length();i++)r[i]=a.getDouble(i);
            try{pl.tabletcad.app.HoleAssociation.validate(r);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
            if(index<0||index>=project.committedFeatures.size()||!pl.tabletcad.app.HoleFeature.is(project.committedFeatures.get(index)))throw new JSONException("Odniesienie do nieistniejącego otworu");project.holeReferences.put(index,r);}}
        JSONArray componentList=root.optJSONArray("components");java.util.Set<Integer> grouped=new java.util.HashSet<>();java.util.Set<String> componentIds=new java.util.HashSet<>();
        if(componentList!=null)for(int i=0;i<componentList.length();i++){Component c=Component.fromJson(componentList.getJSONObject(i));if(!componentIds.add(c.id))throw new JSONException("Powtórzony komponent");for(int b:c.bodies)if(!grouped.add(b))throw new JSONException("Bryła należy do dwóch komponentów");project.components.add(c);}
        JSONArray suppressed=root.optJSONArray("suppressed");
        if(suppressed!=null)for(int i=0;i<suppressed.length();i++){int index=suppressed.getInt(i);if(index<0||index>=project.committedFeatures.size())throw new JSONException("Wygaszona operacja poza historią");project.suppressedFeatures.add(index);}
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
        snapshot.put("id", sketchId).put("plane", sketchPlane).put("name",sketchName);
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
            try {p=pl.tabletcad.app.ProfileDependencies.restore(this,p);}
            catch(IllegalArgumentException error){throw new JSONException(error.getMessage());}
            restored.add(p);
        }
        List<pl.tabletcad.app.SketchConstraints.Link> restoredLinks=pl.tabletcad.app.SketchConstraints.fromJson(snapshot.optJSONArray("coincidences"),restored);
        sketchId = snapshot.getString("id"); sketchPlane = snapshot.getString("plane");sketchName=snapshot.optString("name","Szkic");
        sketchOriginX=frame.getDouble(0); sketchOriginY=frame.getDouble(1); sketchOriginZ=frame.getDouble(2);
        sketchUx=frame.getDouble(3); sketchUy=frame.getDouble(4); sketchUz=frame.getDouble(5);
        sketchVx=frame.getDouble(6); sketchVy=frame.getDouble(7); sketchVz=frame.getDouble(8);
        sketch.clear(); sketch.addAll(restored);
        coincidences.clear();coincidences.addAll(restoredLinks);
    }

    private static void validateFeatureArray(JSONArray data,int prefix) throws JSONException {
        if(data.length()<prefix) throw new JSONException("Invalid feature length");
        if(prefix==7&&data.getDouble(0)==-10){double[] f=new double[data.length()];for(int i=0;i<f.length;i++)f[i]=data.getDouble(i);try{pl.tabletcad.app.ExtrusionPlan.validateBase(f);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}return;}
        if(prefix==16&&(data.getDouble(0)==pl.tabletcad.app.FeaturePattern.TYPE||data.getDouble(0)==pl.tabletcad.app.ShellFeature.TYPE||data.getDouble(0)==pl.tabletcad.app.HoleFeature.TYPE||data.getDouble(0)==pl.tabletcad.app.BodyCombine.TYPE||data.getDouble(0)==pl.tabletcad.app.DraftFeature.TYPE||data.getDouble(0)==pl.tabletcad.app.BodyTransform.TYPE||data.getDouble(0)==pl.tabletcad.app.BodySplit.TYPE||data.getDouble(0)==-19||data.getDouble(0)==-20||data.getDouble(0)==-21||data.getDouble(0)==pl.tabletcad.app.BodyInstance.TYPE||data.getDouble(0)==pl.tabletcad.app.FaceOffset.TYPE)){double[] f=new double[data.length()];for(int i=0;i<f.length;i++)f[i]=data.getDouble(i);try{if(f[0]==pl.tabletcad.app.FeaturePattern.TYPE)pl.tabletcad.app.FeaturePattern.validate(f);else if(f[0]==pl.tabletcad.app.HoleFeature.TYPE)pl.tabletcad.app.HoleFeature.validate(f);else if(f[0]==pl.tabletcad.app.BodyCombine.TYPE)pl.tabletcad.app.BodyCombine.validate(f);else if(f[0]==pl.tabletcad.app.DraftFeature.TYPE)pl.tabletcad.app.DraftFeature.validate(f);else if(f[0]==pl.tabletcad.app.BodyTransform.TYPE)pl.tabletcad.app.BodyTransform.validate(f);else if(f[0]==pl.tabletcad.app.BodySplit.TYPE)pl.tabletcad.app.BodySplit.validate(f);else if(f[0]==pl.tabletcad.app.BodyInstance.TYPE)pl.tabletcad.app.BodyInstance.validate(f);else if(f[0]==pl.tabletcad.app.FaceOffset.TYPE)pl.tabletcad.app.FaceOffset.validate(f);else if(f[0]<=-19)pl.tabletcad.app.SweepLoft.validate(f);else pl.tabletcad.app.ShellFeature.validate(f);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}return;}
        if(prefix==16&&data.getDouble(0)==pl.tabletcad.app.MateFeature.TYPE){double[] f=new double[data.length()];for(int i=0;i<f.length;i++)f[i]=data.getDouble(i);try{pl.tabletcad.app.MateFeature.validate(f);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}return;}
        if(prefix==16&&data.getDouble(0)==-8){double[] f=new double[data.length()];for(int i=0;i<f.length;i++)f[i]=data.getDouble(i);try{pl.tabletcad.app.HoleThread.validate(f);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}return;}
        if(data.getDouble(0)==-3) {
            double[] f=new double[data.length()];for(int i=0;i<f.length;i++)f[i]=data.getDouble(i);
            try{pl.tabletcad.app.Revolve.validate(f);if(prefix==7&&f[1]!=0)throw new IllegalArgumentException("Bryła bazowa nie może być wycięciem");}
            catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}return;
        }
        if(prefix==16&&(data.getDouble(0)==-1||data.getDouble(0)==-2||data.getDouble(0)==-4||data.getDouble(0)==-5||data.getDouble(0)==-6||data.getDouble(0)==-7)) {
            double[] feature=new double[data.length()];for(int i=0;i<feature.length;i++)feature[i]=data.getDouble(i);
            try{if(feature[0]==-2||feature[0]==-5||feature[0]==-7)pl.tabletcad.app.EdgeChamfer.validate(feature);else pl.tabletcad.app.EdgeFillet.validate(feature);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
            return;
        }
        double type=data.getDouble(0);int tail=0;
        if(type==2000)tail=1;
        else if(type>=1001&&type<=1128&&type==Math.floor(type))tail=((int)type-1000)*7;
        else if(type>=5&&type<=258&&type==Math.floor(type)) tail=((int)type-2)*2;
        else if(type!=0&&type!=1) throw new JSONException("Invalid feature type");
        if(data.length()!=prefix+tail) throw new JSONException("Invalid polygon length");
        for(int i=0;i<data.length();i++) if(!Double.isFinite(data.getDouble(i))) throw new JSONException("Non-finite feature");
        try {
            if(prefix==7)pl.tabletcad.app.FeatureDirection.basePlane(data.getDouble(5));
            else pl.tabletcad.app.FeatureDirection.validateOperation(data.getDouble(1));
        }catch(IllegalArgumentException error){throw new JSONException(error.getMessage());}
        if(type==2000) {
            double[] f=new double[data.length()];for(int i=0;i<f.length;i++)f[i]=data.getDouble(i);
            try{SketchPrimitive.fromFeature("check","Elipsa",f,prefix==7?1:2,prefix);}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
            tail=0;
        }
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
        coincidences.removeIf(link->!ids.contains(link.a)||!ids.contains(link.b)||(!link.axis.isEmpty()&&!link.axis.startsWith("@")&&!ids.contains(link.axis)));
        // A linked copy whose source or copy changed kind (split, converted) no longer has a rigid relation.
        java.util.Map<String,SketchPrimitive> byId=new java.util.HashMap<>();for(SketchPrimitive p:sketch)byId.put(p.id,p);
        coincidences.removeIf(link->link.kind.equals("rigid")&&!rigidCompatible(byId.get(link.a),byId.get(link.b)));
    }
    static boolean rigidCompatible(SketchPrimitive a,SketchPrimitive b) {
        if(a==null||b==null||!a.type.equals(b.type)||a.curves!=null||b.curves!=null)return false;
        return (a.vertices==null)==(b.vertices==null)&&(a.vertices==null||a.vertices.length==b.vertices.length);
    }
}
