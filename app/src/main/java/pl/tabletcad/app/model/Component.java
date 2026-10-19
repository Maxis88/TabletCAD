package pl.tabletcad.app.model;

import java.util.*;
import org.json.*;

/** Named group of bodies (assembly component). An instance component records the component it copies. */
public final class Component {
    public final String id;public String name;public final LinkedHashSet<Integer> bodies=new LinkedHashSet<>();
    /** Source component of an instance (its bodies are BodyInstance copies), or empty. */
    public String instanceOf="";
    public Component(String id,String name){if(id==null||id.isEmpty())throw new IllegalArgumentException("Brak identyfikatora komponentu");this.id=id;this.name=name==null||name.trim().isEmpty()?"Komponent":name.trim();}
    public static Component create(String name){return new Component(UUID.randomUUID().toString(),name);}
    public boolean instance(){return !instanceOf.isEmpty();}
    public JSONObject toJson()throws JSONException{return new JSONObject().put("id",id).put("name",name).put("bodies",new JSONArray(bodies)).put("instanceOf",instanceOf);}
    public static Component fromJson(JSONObject o)throws JSONException {
        Component c=new Component(o.getString("id"),o.optString("name","Komponent"));JSONArray b=o.getJSONArray("bodies");
        for(int i=0;i<b.length();i++){int id=b.getInt(i);if(id<1)throw new JSONException("Niepoprawna bryła komponentu");c.bodies.add(id);}
        c.instanceOf=o.optString("instanceOf","");return c;
    }
    /** Component containing the body, or null. */
    public static Component of(List<Component> components,int body){for(Component c:components)if(c.bodies.contains(body))return c;return null;}
    public static String nextName(List<Component> components){return "Komponent "+(components.size()+1);}
}
