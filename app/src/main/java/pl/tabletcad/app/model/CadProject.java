package pl.tabletcad.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Versioned, geometry-independent source of truth for a TabletCAD document. */
public final class CadProject {
    public static final int CURRENT_SCHEMA = 5;

    public final String id;
    public String name;
    public String units;
    public double width;
    public double height;
    public double depth;
    public String sketchPlane;
    public boolean sketchExtruded;
    public boolean importedStep;
    public double filletRadius;
    public double sketchOriginX, sketchOriginY, sketchOriginZ;
    public double sketchUx = 1.0, sketchUy, sketchUz;
    public double sketchVx, sketchVy = 1.0, sketchVz;
    public final List<SketchPrimitive> sketch = new ArrayList<>();

    public CadProject() {
        this(UUID.randomUUID().toString(), "Bez nazwy", "mm", 80.0, 48.0, 32.0, "XY");
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
        root.put("id", id);
        root.put("name", name);
        root.put("units", units);
        root.put("sketchPlane", sketchPlane);
        root.put("sketchExtruded", sketchExtruded);
        root.put("importedStep", importedStep);
        root.put("filletRadius", filletRadius);
        JSONObject planeFrame = new JSONObject();
        planeFrame.put("ox", sketchOriginX).put("oy", sketchOriginY).put("oz", sketchOriginZ);
        planeFrame.put("ux", sketchUx).put("uy", sketchUy).put("uz", sketchUz);
        planeFrame.put("vx", sketchVx).put("vy", sketchVy).put("vz", sketchVz);
        root.put("sketchFrame", planeFrame);
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
        project.sketchExtruded = schema >= 2 && root.optBoolean("sketchExtruded", false);
        project.importedStep = schema >= 3 && root.optBoolean("importedStep", false);
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
        JSONArray entities = root.optJSONArray("sketch");
        if (entities != null) {
            for (int i = 0; i < entities.length(); i++) {
                project.sketch.add(SketchPrimitive.fromJson(entities.getJSONObject(i)));
            }
        }
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
}
