package pl.tabletcad.app.model;

import java.util.*;
import org.json.*;

/**
 * Construction geometry (datum plane, axis or point) defined by explicit numeric inputs.
 *
 * <p>Methods and their {@link #values}:
 * <ul>
 * <li>plane {@code offset}: base origin xyz, base normal xyz, distance (7). The base is a principal
 *     plane, another datum plane or a picked face (copied numerically when created).</li>
 * <li>plane {@code angle}: axis point xyz, axis direction xyz, base normal xyz, angle in degrees (10);
 *     the plane contains the axis and is the base plane turned about it.</li>
 * <li>plane {@code points}: three points (9).</li>
 * <li>axis {@code line}: point xyz, direction xyz (6) — principal axes, cylinder axes, edges.</li>
 * <li>axis {@code points}: two points (6).</li>
 * <li>point {@code coords}: xyz (3).</li>
 * </ul>
 * The resolved {@link #origin}/{@link #direction} are recomputed from the values, never stored alone.</p>
 */
public final class Datum {
    public static final String PLANE="plane",AXIS="axis",POINT="point";
    public final String id,kind,method,reference;public final double[] values;
    public String name;public boolean hidden;
    public final double[] origin,direction;

    public Datum(String id,String kind,String method,String reference,double[] values,String name) {
        if(id==null||id.isEmpty())throw new IllegalArgumentException("Brak identyfikatora elementu konstrukcyjnego");
        this.id=id;this.kind=kind;this.method=method;this.reference=reference==null?"":reference;this.values=values.clone();this.name=name==null||name.trim().isEmpty()?defaultName(kind):name.trim();
        for(double v:values)if(!Double.isFinite(v)||Math.abs(v)>1e6)throw new IllegalArgumentException("Niepoprawna wartość elementu konstrukcyjnego");
        double[][] r=resolve(kind,method,values);origin=r[0];direction=r[1];
    }
    public static String defaultName(String kind){return kind.equals(PLANE)?"Płaszczyzna":kind.equals(AXIS)?"Oś":"Punkt";}
    public boolean plane(){return kind.equals(PLANE);}
    public boolean axis(){return kind.equals(AXIS);}
    public boolean point(){return kind.equals(POINT);}
    public Datum with(double[] newValues){Datum d=new Datum(id,kind,method,reference,newValues,name);d.hidden=hidden;return d;}

    static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    static double[] at(double[] v,int i){return new double[]{v[i],v[i+1],v[i+2]};}
    static double[] unit(double[] a,String what) {
        double l=Math.sqrt(dot(a,a));if(!(l>1e-9))throw new IllegalArgumentException(what);return new double[]{a[0]/l,a[1]/l,a[2]/l};
    }
    /** Rotates v about the unit axis k by the angle (Rodrigues). */
    static double[] rotate(double[] v,double[] k,double degrees) {
        double a=Math.toRadians(degrees),c=Math.cos(a),s=Math.sin(a),d=dot(k,v);double[] x=cross(k,v);
        return new double[]{v[0]*c+x[0]*s+k[0]*d*(1-c),v[1]*c+x[1]*s+k[1]*d*(1-c),v[2]*c+x[2]*s+k[2]*d*(1-c)};
    }
    static double[][] resolve(String kind,String method,double[] v) {
        switch(kind+":"+method) {
            case "plane:offset": {
                need(v,7);double[] n=unit(at(v,3),"Zerowa normalna płaszczyzny bazowej");double[] o=at(v,0);
                return new double[][]{{o[0]+n[0]*v[6],o[1]+n[1]*v[6],o[2]+n[2]*v[6]},n};
            }
            case "plane:angle": {
                need(v,10);double[] k=unit(at(v,3),"Zerowy kierunek osi obrotu"),n=unit(at(v,6),"Zerowa normalna płaszczyzny bazowej");
                if(Math.abs(dot(k,n))>1e-9)throw new IllegalArgumentException("Oś obrotu musi leżeć w płaszczyźnie bazowej (prostopadła do jej normalnej)");
                if(Math.abs(v[9])>360)throw new IllegalArgumentException("Kąt płaszczyzny: −360…360°");
                return new double[][]{at(v,0),unit(rotate(n,k,v[9]),"Zerowa normalna")};
            }
            case "plane:points": {
                need(v,9);double[] a=at(v,0),b=at(v,3),c=at(v,6);
                double[] n=cross(sub(b,a),sub(c,a));double scale=Math.sqrt(dot(sub(b,a),sub(b,a))*dot(sub(c,a),sub(c,a)));
                if(!(Math.sqrt(dot(n,n))>1e-9*Math.max(1,scale)))throw new IllegalArgumentException("Trzy punkty są współliniowe lub się pokrywają");
                return new double[][]{a,unit(n,"Punkty współliniowe")};
            }
            case "axis:line": need(v,6);return new double[][]{at(v,0),unit(at(v,3),"Zerowy kierunek osi")};
            case "axis:points": {
                need(v,6);double[] a=at(v,0),b=at(v,3);return new double[][]{a,unit(sub(b,a),"Punkty osi się pokrywają")};
            }
            case "point:coords": need(v,3);return new double[][]{at(v,0),new double[3]};
            default: throw new IllegalArgumentException("Nieznany element konstrukcyjny: "+kind+"/"+method);
        }
    }
    static void need(double[] v,int n){if(v.length!=n)throw new IllegalArgumentException("Niepoprawna liczba wartości elementu konstrukcyjnego");}

    /** In-plane frame (origin, U, V) used for a sketch on this plane; same convention as a face sketch. */
    public double[] sketchFrame() {
        if(!plane())throw new IllegalArgumentException("Szkic wymaga płaszczyzny");
        double[] n=direction;double[] r=Math.abs(n[2])<.9?new double[]{0,0,1}:new double[]{0,1,0};
        double[] u=unit(cross(r,n),"Zerowa oś U"),w=cross(n,u);double offset=dot(origin,n);
        return new double[]{n[0]*offset,n[1]*offset,n[2]*offset,u[0],u[1],u[2],w[0],w[1],w[2]};
    }
    /** Point and normal of the plane offset; for patterns and mirrors: {origin, direction}. */
    public double[] frame(){return new double[]{origin[0],origin[1],origin[2],direction[0],direction[1],direction[2]};}

    public String describe() {
        switch(kind+":"+method) {
            case "plane:offset":return "Odsunięta"+(reference.isEmpty()?"":" od "+reference)+" o "+fmt(values[6])+" mm";
            case "plane:angle":return "Pod kątem "+fmt(values[9])+"°"+(reference.isEmpty()?"":" względem "+reference);
            case "plane:points":return "Przez 3 punkty";
            case "axis:line":return reference.isEmpty()?"Oś przez punkt":reference;
            case "axis:points":return "Oś przez 2 punkty";
            default:return "Punkt ("+fmt(values[0])+"; "+fmt(values[1])+"; "+fmt(values[2])+")";
        }
    }
    static String fmt(double v){return String.format(Locale.US,"%.3f",v).replaceAll("0+$","").replaceAll("\\.$","");}

    public JSONObject toJson()throws JSONException {
        JSONArray v=new JSONArray();for(double d:values)v.put(d);
        return new JSONObject().put("id",id).put("kind",kind).put("method",method).put("reference",reference).put("values",v).put("name",name).put("hidden",hidden);
    }
    public static Datum fromJson(JSONObject o)throws JSONException {
        JSONArray a=o.getJSONArray("values");double[] v=new double[a.length()];for(int i=0;i<v.length;i++)v[i]=a.getDouble(i);
        try{Datum d=new Datum(o.getString("id"),o.getString("kind"),o.getString("method"),o.optString("reference",""),v,o.optString("name",""));d.hidden=o.optBoolean("hidden",false);return d;}
        catch(IllegalArgumentException e){throw new JSONException("Element konstrukcyjny: "+e.getMessage());}
    }
    public static Datum create(String kind,String method,String reference,double[] values,String name){return new Datum(UUID.randomUUID().toString(),kind,method,reference,values,name);}
    public static String nextName(List<Datum> existing,String kind) {
        int n=1;for(Datum d:existing)if(d.kind.equals(kind))n++;return defaultName(kind)+" "+n;
    }
}
