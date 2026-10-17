package pl.tabletcad.app.model;

import org.json.JSONException;
import org.json.JSONObject;

/** Sketch coordinates are millimetres in the local work-plane coordinate system. */
public final class SketchPrimitive {
    public final String id;
    public final String type;
    public final double x1, y1, x2, y2;
    public final double[] vertices;
    public final double[] curves;
    /** Locked primitive dimensions: L/angle, width/height, or radius. */
    public final double[] driving;

    public SketchPrimitive(String id, String type, double x1, double y1, double x2, double y2) {
        this(id,type,x1,y1,x2,y2,null);
    }

    public SketchPrimitive(String id,String type,double x1,double y1,double x2,double y2,double[] driving) {
        if(type.equals("Łuk")||type.equals("Kontur krzywy"))throw new IllegalArgumentException("Krzywa wymaga danych analitycznych");
        this.id = id;
        this.type = type;
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        this.vertices = null;
        this.curves=null;
        this.driving=driving==null?null:driving.clone();
        if(this.driving!=null) pl.tabletcad.app.SketchDimensions.validate(this);
    }

    public SketchPrimitive(String id, double[] points) {
        if(points==null||points.length<6||points.length>512||points.length%2!=0)
            throw new IllegalArgumentException("Kontur wymaga 3–256 wierzchołków");
        this.id=id;this.type="Kontur";this.vertices=points.clone();this.driving=null;this.curves=null;
        double xmin=Double.POSITIVE_INFINITY,ymin=xmin,xmax=-xmin,ymax=-xmin;
        for(int i=0;i<points.length;i+=2) {
            if(!Double.isFinite(points[i])||!Double.isFinite(points[i+1])
                ||Math.abs(points[i])>5000||Math.abs(points[i+1])>5000) throw new IllegalArgumentException("Niepoprawny kontur");
            xmin=Math.min(xmin,points[i]);xmax=Math.max(xmax,points[i]);
            ymin=Math.min(ymin,points[i+1]);ymax=Math.max(ymax,points[i+1]);
        }
        x1=xmin;y1=ymin;x2=xmax;y2=ymax;
    }

    public SketchPrimitive(String id,double[] segments,boolean closed) {
        pl.tabletcad.app.SketchCurves.validate(segments,closed);
        this.id=id;this.type=closed?"Kontur krzywy":"Łuk";this.curves=segments.clone();this.vertices=null;this.driving=null;
        if(!closed&&segments[0]!=1)throw new IllegalArgumentException("Łuk wymaga trzech punktów");
        if(!closed){x1=segments[1];y1=segments[2];x2=segments[5];y2=segments[6];}
        else {
            double[] bounds=pl.tabletcad.app.SketchCurves.bounds(segments);x1=bounds[0];y1=bounds[1];x2=bounds[2];y2=bounds[3];
        }
    }
    public boolean closedProfile() {return vertices!=null||type.equals("Kontur krzywy")||type.equals("Prostokąt")||type.equals("Okrąg");}
    public double featureType() {return curves!=null?1000+curves.length/7:vertices==null?(type.equals("Okrąg")?1:0):2+vertices.length/2;}
    public double[] appendVertices(double[] prefix) {
        double[] tail=curves!=null?curves:vertices;
        double[] result=java.util.Arrays.copyOf(prefix,prefix.length+(tail==null?0:tail.length));
        if(tail!=null) System.arraycopy(tail,0,result,prefix.length,tail.length);
        return result;
    }
    public static SketchPrimitive fromFeature(String id,String type,double[] data,int offset,int prefixLength) {
        if(pl.tabletcad.app.Revolve.is(data))return pl.tabletcad.app.Revolve.profile(id,data);
        return data[0]>=1001 ? new SketchPrimitive(id,java.util.Arrays.copyOfRange(data,prefixLength,data.length),true):data[0]>=5 ? new SketchPrimitive(id,java.util.Arrays.copyOfRange(data,prefixLength,data.length))
            : new SketchPrimitive(id,type,data[offset],data[offset+1],data[offset+2],data[offset+3]);
    }
    public SketchPrimitive restoreFeature(double[] data,int offset,int prefixLength) {
        SketchPrimitive shape=fromFeature(id,type,data,offset,prefixLength);
        return driving==null?shape:new SketchPrimitive(id,type,shape.x1,shape.y1,shape.x2,shape.y2,driving);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject result=new JSONObject().put("id", id).put("type", type)
            .put("x1", x1).put("y1", y1).put("x2", x2).put("y2", y2);
        if(vertices!=null) {org.json.JSONArray points=new org.json.JSONArray();for(double v:vertices) points.put(v);result.put("vertices",points);}
        if(curves!=null){org.json.JSONArray segments=new org.json.JSONArray();for(double v:curves)segments.put(v);result.put("curves",segments);}
        if(driving!=null) {org.json.JSONArray values=new org.json.JSONArray();for(double v:driving)values.put(v);result.put("driving",values);}
        return result;
    }

    public static SketchPrimitive fromJson(JSONObject json) throws JSONException {
        if(json.has("curves")) {
            if(json.has("vertices")||json.has("driving"))throw new JSONException("Niejednoznaczne dane krzywej");
            String type=json.getString("type");if(!type.equals("Łuk")&&!type.equals("Kontur krzywy"))throw new JSONException("Niepoprawny typ krzywej");
            org.json.JSONArray segments=json.getJSONArray("curves");double[] values=new double[segments.length()];for(int i=0;i<values.length;i++)values[i]=segments.getDouble(i);
            try{return new SketchPrimitive(json.getString("id"),values,type.equals("Kontur krzywy"));}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
        }
        if(json.has("vertices")) {
            org.json.JSONArray points=json.getJSONArray("vertices");double[] values=new double[points.length()];
            for(int i=0;i<values.length;i++) values[i]=points.getDouble(i);
            try{pl.tabletcad.app.SketchContours.validate(values);return new SketchPrimitive(json.getString("id"),values);}
            catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
        }
        org.json.JSONArray locked=json.optJSONArray("driving");double[] driving=null;
        if(locked!=null) {driving=new double[locked.length()];for(int i=0;i<driving.length;i++)driving[i]=locked.getDouble(i);}
        try{return new SketchPrimitive(json.getString("id"), json.getString("type"),
            json.getDouble("x1"), json.getDouble("y1"),
            json.getDouble("x2"), json.getDouble("y2"),driving);}
        catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
    }
}
