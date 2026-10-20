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
    public final boolean construction;
    /** Semi-minor axis of an ellipse ("Elipsa": x1/y1 centre, x2/y2 end of the semi-major axis); 0 otherwise. */
    public final double minor;
    public static final String ELLIPSE="Elipsa";
    /** Sketch point: a reference position (always construction), e.g. a hole centre. */
    public static final String POINT="Punkt";
    public static SketchPrimitive point(String id,double x,double y){return new SketchPrimitive(id,POINT,x,y,x,y);}
    public boolean isPoint(){return POINT.equals(type);}

    public SketchPrimitive(String id, String type, double x1, double y1, double x2, double y2) {
        this(id,type,x1,y1,x2,y2,null);
    }

    public SketchPrimitive(String id,String type,double x1,double y1,double x2,double y2,double[] driving) {
        if(type.equals("Łuk")||type.equals("Kontur krzywy"))throw new IllegalArgumentException("Krzywa wymaga danych analitycznych");
        if(type.equals(ELLIPSE))throw new IllegalArgumentException("Elipsa wymaga półosi małej");
        this.minor=0;
        this.id = id;
        this.type = type;
        this.construction=type.equals("Pomocnicza")||type.equals(POINT);
        if(type.equals(POINT)&&(x1!=x2||y1!=y2))throw new IllegalArgumentException("Punkt ma jedno położenie");
        this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        this.vertices = null;
        this.curves=null;
        this.driving=driving==null?null:driving.clone();
        if(this.driving!=null) pl.tabletcad.app.SketchDimensions.validate(this);
    }

    public SketchPrimitive(String id, double[] points, String kind) {
        if(!kind.equals("Splajn"))throw new IllegalArgumentException("Niepoprawny rodzaj krzywej");
        pl.tabletcad.app.SketchSpline.spans(points);
        this.id=id;this.type=kind;this.vertices=points.clone();this.curves=null;this.driving=null;
        this.construction=false;this.minor=0;
        x1=points[0];y1=points[1];x2=points[points.length-2];y2=points[points.length-1];
    }

    public SketchPrimitive(String id, double[] points) {
        if(points==null||points.length<6||points.length>512||points.length%2!=0)
            throw new IllegalArgumentException("Kontur wymaga 3–256 wierzchołków");
        this.id=id;this.type="Kontur";this.vertices=points.clone();this.driving=null;this.curves=null;
        this.construction=false;this.minor=0;
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
        this.construction=false;this.minor=0;
        if(!closed&&segments[0]!=1)throw new IllegalArgumentException("Łuk wymaga trzech punktów");
        if(!closed){x1=segments[1];y1=segments[2];x2=segments[5];y2=segments[6];}
        else {
            double[] bounds=pl.tabletcad.app.SketchCurves.bounds(segments);x1=bounds[0];y1=bounds[1];x2=bounds[2];y2=bounds[3];
        }
    }
    private SketchPrimitive(SketchPrimitive source,boolean auxiliary) {
        id=source.id;type=source.type.equals("Pomocnicza")?"Linia":source.type;
        x1=source.x1;y1=source.y1;x2=source.x2;y2=source.y2;
        vertices=source.vertices==null?null:source.vertices.clone();curves=source.curves==null?null:source.curves.clone();
        driving=source.driving==null?null:source.driving.clone();construction=auxiliary||POINT.equals(source.type);minor=source.minor;
    }
    private SketchPrimitive(String id,double cx,double cy,double ax,double ay,double minor) {
        this.id=id;type=ELLIPSE;x1=cx;y1=cy;x2=ax;y2=ay;this.minor=minor;vertices=null;curves=null;driving=null;construction=false;
        double major=Math.hypot(ax-cx,ay-cy);
        for(double v:new double[]{cx,cy,ax,ay,minor})if(!Double.isFinite(v))throw new IllegalArgumentException("Niepoprawna elipsa");
        if(major<.01||minor<.01)throw new IllegalArgumentException("Półosie elipsy muszą wynosić co najmniej 0,01 mm");
        if(minor>major+1e-9)throw new IllegalArgumentException("Półoś mała nie może być większa od wielkiej");
        if(Math.abs(cx)+major>5000||Math.abs(cy)+major>5000)throw new IllegalArgumentException("Elipsa poza zakresem ±5000 mm");
    }
    /** Exact ellipse: centre, end of the semi-major axis and semi-minor length. */
    public static SketchPrimitive ellipse(String id,double cx,double cy,double ax,double ay,double minor){return new SketchPrimitive(id,cx,cy,ax,ay,minor);}
    public boolean isEllipse(){return ELLIPSE.equals(type);}
    /** Same kind of two-point element at new points (an ellipse keeps its semi-minor axis). */
    public SketchPrimitive withPoints(double ax,double ay,double bx,double by,double[] lock) {
        if(isPoint())return point(id,ax,ay);
        if(isEllipse())return ellipse(id,ax,ay,bx,by,minor).withConstruction(construction);
        return new SketchPrimitive(id,type,ax,ay,bx,by,lock);
    }
    public double major(){return Math.hypot(x2-x1,y2-y1);}
    public SketchPrimitive withConstruction(boolean value){return construction==value?this:new SketchPrimitive(this,value);}
    public boolean closedGeometry() {return (vertices!=null&&!type.equals("Splajn"))||type.equals("Kontur krzywy")||type.equals("Prostokąt")||type.equals("Okrąg")||isEllipse();}
    public boolean closedProfile(){return !construction&&closedGeometry();}
    public double featureType() {if(construction||type.equals("Splajn"))throw new IllegalArgumentException("Wyciąganie wymaga zamkniętej geometrii zwykłej");return isEllipse()?2000:curves!=null?1000+curves.length/7:vertices==null?(type.equals("Okrąg")?1:0):2+vertices.length/2;}
    public double[] appendVertices(double[] prefix) {
        double[] tail=isEllipse()?new double[]{minor}:curves!=null?curves:vertices;
        double[] result=java.util.Arrays.copyOf(prefix,prefix.length+(tail==null?0:tail.length));
        if(tail!=null) System.arraycopy(tail,0,result,prefix.length,tail.length);
        return result;
    }
    public static SketchPrimitive fromFeature(String id,String type,double[] data,int offset,int prefixLength) {
        if(pl.tabletcad.app.ExtrusionPlan.placedBase(data))return pl.tabletcad.app.ExtrusionPlan.baseProfile(id,data);
        if(pl.tabletcad.app.Revolve.is(data))return pl.tabletcad.app.Revolve.profile(id,data);
        if(data[0]==2000)return ellipse(id,data[offset],data[offset+1],data[offset+2],data[offset+3],data[prefixLength]);
        return data[0]>=1001 ? new SketchPrimitive(id,java.util.Arrays.copyOfRange(data,prefixLength,data.length),true):data[0]>=5 ? new SketchPrimitive(id,java.util.Arrays.copyOfRange(data,prefixLength,data.length))
            : new SketchPrimitive(id,type,data[offset],data[offset+1],data[offset+2],data[offset+3]);
    }
    public SketchPrimitive restoreFeature(double[] data,int offset,int prefixLength) {
        SketchPrimitive shape=fromFeature(id,type,data,offset,prefixLength);
        return driving==null||shape.isEllipse()?shape:new SketchPrimitive(id,type,shape.x1,shape.y1,shape.x2,shape.y2,driving);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject result=new JSONObject().put("id", id).put("type", type)
            .put("x1", x1).put("y1", y1).put("x2", x2).put("y2", y2);
        result.put("construction",construction);
        if(isEllipse())result.put("minor",minor);
        if(vertices!=null) {org.json.JSONArray points=new org.json.JSONArray();for(double v:vertices) points.put(v);result.put("vertices",points);}
        if(curves!=null){org.json.JSONArray segments=new org.json.JSONArray();for(double v:curves)segments.put(v);result.put("curves",segments);}
        if(driving!=null) {org.json.JSONArray values=new org.json.JSONArray();for(double v:driving)values.put(v);result.put("driving",values);}
        return result;
    }

    public static SketchPrimitive fromJson(JSONObject json) throws JSONException {
        return readGeometry(json).withConstruction(json.optBoolean("construction",json.optString("type").equals("Pomocnicza")));
    }
    private static SketchPrimitive readGeometry(JSONObject json) throws JSONException {
        if(ELLIPSE.equals(json.optString("type"))) {
            try{return ellipse(json.getString("id"),json.getDouble("x1"),json.getDouble("y1"),json.getDouble("x2"),json.getDouble("y2"),json.getDouble("minor"));}
            catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
        }
        if(json.has("curves")) {
            if(json.has("vertices")||json.has("driving"))throw new JSONException("Niejednoznaczne dane krzywej");
            String type=json.getString("type");if(!type.equals("Łuk")&&!type.equals("Kontur krzywy"))throw new JSONException("Niepoprawny typ krzywej");
            org.json.JSONArray segments=json.getJSONArray("curves");double[] values=new double[segments.length()];for(int i=0;i<values.length;i++)values[i]=segments.getDouble(i);
            try{return new SketchPrimitive(json.getString("id"),values,type.equals("Kontur krzywy"));}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}
        }
        if(json.has("vertices")) {
            org.json.JSONArray points=json.getJSONArray("vertices");double[] values=new double[points.length()];
            for(int i=0;i<values.length;i++) values[i]=points.getDouble(i);
            try{if(json.getString("type").equals("Splajn"))return new SketchPrimitive(json.getString("id"),values,"Splajn");pl.tabletcad.app.SketchContours.validate(values);return new SketchPrimitive(json.getString("id"),values);}
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
