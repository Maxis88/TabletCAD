package pl.tabletcad.app.model;

import org.json.JSONException;
import org.json.JSONObject;

/** Sketch coordinates are millimetres in the local work-plane coordinate system. */
public final class SketchPrimitive {
    public final String id;
    public final String type;
    public final double x1, y1, x2, y2;

    public SketchPrimitive(String id, String type, double x1, double y1, double x2, double y2) {
        this.id = id;
        this.type = type;
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("type", type)
            .put("x1", x1).put("y1", y1).put("x2", x2).put("y2", y2);
    }

    public static SketchPrimitive fromJson(JSONObject json) throws JSONException {
        return new SketchPrimitive(json.getString("id"), json.getString("type"),
            json.getDouble("x1"), json.getDouble("y1"),
            json.getDouble("x2"), json.getDouble("y2"));
    }
}
