package pl.tabletcad.app;

import java.util.*;
import org.json.*;
import pl.tabletcad.app.model.*;

/**
 * Named user parameters (Parametry) with expressions, and bindings of model values to them.
 *
 * <p>A parameter is a length (value in the project unit, stored exactly in mm after evaluation)
 * or a plain number. Expressions may reference other parameters; cycles and unknown names are
 * reported. Bindings map a driven value to an expression:</p>
 * <ul>
 *   <li>{@code depth:i} – depth of extrusion/cut i (sign and symmetry kept),</li>
 *   <li>{@code dim:sketch/entity/k} – driving dimension k of a sketch element
 *       (0: length/width/radius, 1: height/angle),</li>
 *   <li>{@code hole:i:d}, {@code hole:i:h} – diameter and depth of hole i,</li>
 *   <li>{@code pattern:i:n}, {@code pattern:i:s} – count and spacing/angle of pattern i.</li>
 * </ul>
 * <p>{@link #apply} recomputes every bound value in the given (candidate) document; the caller
 * commits it through the usual transaction, so an invalid result never replaces the model.</p>
 */
public final class UserParameters {
    private UserParameters() {}
    public static final class Parameter {
        public String name,expression;public boolean length;
        public Parameter(String name,String expression,boolean length){this.name=name;this.expression=expression;this.length=length;}
    }
    private static final Set<String> RESERVED=new HashSet<>(Arrays.asList("mm","cm","m","in"));
    public static void validateName(String name) {
        if(name==null||!name.matches("\\p{L}[\\p{L}\\p{N}_]{0,31}"))throw new IllegalArgumentException("Nazwa parametru: litera, potem litery, cyfry lub _ (do 32 znaków)");
        if(RESERVED.contains(name))throw new IllegalArgumentException("Nazwa parametru nie może być jednostką: "+name);
    }
    public static String depthKey(int feature){return "depth:"+feature;}
    public static String dimKey(String sketch,String entity,int index){return "dim:"+sketch+"/"+entity+"/"+index;}

    /** Evaluates all parameters in dependency order; returns name → {value (mm or number), power}. */
    public static Map<String,double[]> evaluate(CadProject p) {
        Map<String,Parameter> byName=new LinkedHashMap<>();
        for(Parameter parameter:p.parameters){validateName(parameter.name);if(byName.put(parameter.name,parameter)!=null)throw new IllegalArgumentException("Powtórzona nazwa parametru: "+parameter.name);}
        Map<String,double[]> values=new HashMap<>();Set<String> visiting=new HashSet<>();
        for(String name:byName.keySet())resolve(name,byName,values,visiting,p.units,new ArrayDeque<>());
        return values;
    }
    private static void resolve(String name,Map<String,Parameter> all,Map<String,double[]> values,Set<String> visiting,String unit,Deque<String> path) {
        if(values.containsKey(name))return;
        Parameter parameter=all.get(name);if(parameter==null)throw new IllegalArgumentException("Nieznany parametr „"+name+"”");
        path.addLast(name);
        if(!visiting.add(name))throw new IllegalArgumentException("Cykl zależności parametrów: "+String.join(" → ",path));
        for(String used:names(parameter.expression))if(!values.containsKey(used))resolve(used,all,values,visiting,unit,path);
        double value=parameter.length?Units.parseLength(parameter.expression,unit,values):Units.parseNumber(parameter.expression,values);
        if(!Double.isFinite(value))throw new IllegalArgumentException("Parametr „"+name+"” nie ma skończonej wartości");
        values.put(name,new double[]{value,parameter.length?1:0});visiting.remove(name);path.removeLast();
    }
    /** Identifiers used by an expression (candidate parameter names). */
    public static Set<String> names(String expression) {
        Set<String> result=new LinkedHashSet<>();if(expression==null)return result;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}_.])\\p{L}[\\p{L}\\p{N}_]*").matcher(expression);
        while(m.find()){String n=m.group();if(!RESERVED.contains(n))result.add(n);}
        // A unit directly after a number ("10mm") is not a name; the regex excludes it by the look-behind on digits only when attached.
        return result;
    }
    public static boolean isExpression(String text){return !names(text).isEmpty();}

    public static void bind(CadProject p,String key,String expression){if(expression==null||expression.trim().isEmpty())p.parameterBindings.remove(key);else p.parameterBindings.put(key,expression.trim());}

    /** Recomputes all bound values of the document from the parameters. */
    public static void apply(CadProject p) {
        if(p.parameterBindings.isEmpty())return;
        Map<String,double[]> values=evaluate(p);
        Map<String,Map<String,double[]>> dims=new LinkedHashMap<>();
        for(Map.Entry<String,String> binding:p.parameterBindings.entrySet()) {
            String key=binding.getKey(),expression=binding.getValue();
            try {
                if(key.startsWith("depth:")) {
                    int i=Integer.parseInt(key.substring(6));double[] f=feature(p,i);
                    if(f[0]<0||f.length<16)throw new IllegalArgumentException("Operacja nie ma głębokości");
                    double depth=Units.parseLength(expression,p.units,values);if(depth<.1||depth>5000)throw new IllegalArgumentException("Głębokość poza zakresem 0,1–5000 mm");
                    f[6]=Math.copySign(depth,f[6]);
                } else if(key.startsWith("hole:")) {
                    String[] part=key.split(":");int i=Integer.parseInt(part[1]);double[] f=feature(p,i).clone();if(!HoleFeature.is(f))throw new IllegalArgumentException("Operacja nie jest otworem");
                    double v=Units.parseLength(expression,p.units,values);if(part[2].equals("d"))f[9]=v;else{if(f[1]==1)throw new IllegalArgumentException("Otwór na wylot nie ma głębokości");f[10]=v;}
                    HoleFeature.validate(f);p.committedFeatures.set(i,f);
                } else if(key.startsWith("pattern:")) {
                    String[] part=key.split(":");int i=Integer.parseInt(part[1]);double[] f=feature(p,i);if(!FeaturePattern.is(f))throw new IllegalArgumentException("Operacja nie jest szykiem");
                    double v=part[2].equals("n")?Units.parseNumber(expression,values):(f[1]==FeaturePattern.LINEAR?Units.parseLength(expression,p.units,values):Units.parseNumber(expression,values));
                    if(part[2].equals("n")){if(v!=Math.rint(v))throw new IllegalArgumentException("Liczba wystąpień musi być całkowita");f=f.clone();f[9]=v;}else{f=f.clone();f[10]=v;}
                    FeaturePattern.validate(f);p.committedFeatures.set(i,f);
                } else if(key.startsWith("dim:")) {
                    String[] part=key.substring(4).split("/");if(part.length!=3)throw new IllegalArgumentException("Niepoprawne powiązanie wymiaru");
                    int k=Integer.parseInt(part[2]);double v=k==1&&isAngle(p,part[0],part[1])?Units.parseNumber(expression,values):Units.parseLength(expression,p.units,values);
                    dims.computeIfAbsent(part[0],x->new LinkedHashMap<>()).computeIfAbsent(part[1],x->new double[]{Double.NaN,Double.NaN})[k]=v;
                } else throw new IllegalArgumentException("Nieznane powiązanie");
            }catch(IllegalArgumentException|IndexOutOfBoundsException error){
                throw new IllegalArgumentException("Parametr „"+expression+"” ("+describe(key)+"): "+error.getMessage());
            }
        }
        for(Map.Entry<String,Map<String,double[]>> sketch:dims.entrySet())applyDimensions(p,sketch.getKey(),sketch.getValue());
    }
    private static double[] feature(CadProject p,int i){if(i<0||i>=p.committedFeatures.size())throw new IllegalArgumentException("Operacja nie istnieje");return p.committedFeatures.get(i);}
    private static boolean isAngle(CadProject p,String sketch,String entity) {
        for(SweepLoft.Placed e:SweepLoft.elements(p))if(e.sketchId.equals(sketch)&&e.shape.id.equals(entity))return !e.shape.type.equals("Prostokąt");
        return false;
    }
    /** Sets driving dimensions of one sketch, re-solves its constraints and propagates profiles. */
    private static void applyDimensions(CadProject p,String sketchId,Map<String,double[]> values) {
        boolean open=sketchId.equals(p.sketchId);JSONObject snapshot=null;
        List<SketchPrimitive> geometry;List<SketchConstraints.Link> links;
        try {
            if(open){geometry=new ArrayList<>(p.sketch);links=new ArrayList<>(p.coincidences);}
            else {
                for(JSONObject s:p.savedSketches)if(sketchId.equals(s.optString("id")))snapshot=s;
                if(snapshot==null)throw new IllegalArgumentException("Szkic powiązany z parametrem nie istnieje");
                geometry=new ArrayList<>();JSONArray entities=snapshot.getJSONArray("entities");for(int i=0;i<entities.length();i++)geometry.add(SketchPrimitive.fromJson(entities.getJSONObject(i)));
                links=SketchConstraints.fromJson(snapshot.optJSONArray("coincidences"),geometry);
            }
        }catch(JSONException error){throw new IllegalArgumentException(error.getMessage());}
        List<SketchPrimitive> before=new ArrayList<>(geometry);
        for(Map.Entry<String,double[]> e:values.entrySet()) {
            int index=-1;for(int i=0;i<geometry.size();i++)if(geometry.get(i).id.equals(e.getKey()))index=i;
            if(index<0)throw new IllegalArgumentException("Element powiązany z parametrem nie istnieje");
            SketchPrimitive s=geometry.get(index);double[] v=e.getValue();
            double first,second;
            if(s.type.equals("Prostokąt")){first=Math.abs(s.x2-s.x1);second=Math.abs(s.y2-s.y1);}
            else if(s.type.equals("Okrąg")){first=SketchDimensions.length(s);second=0;}
            else if(s.type.equals("Linia")||s.type.equals("Pomocnicza")){first=SketchDimensions.length(s);second=SketchDimensions.angle(s);}
            else throw new IllegalArgumentException("Parametrem można sterować wymiarem linii, prostokąta lub okręgu");
            if(!Double.isNaN(v[0]))first=v[0];if(!Double.isNaN(v[1]))second=v[1];
            geometry.set(index,SketchDimensions.apply(s,first,second,true));
        }
        if(!links.isEmpty())geometry=SketchConstraints.resolve(geometry,links);
        if(open){p.sketch.clear();p.sketch.addAll(geometry);}
        else try {
            JSONArray entities=new JSONArray();for(SketchPrimitive s:geometry)entities.put(s.toJson());snapshot.put("entities",entities);
        }catch(JSONException error){throw new IllegalArgumentException(error.getMessage());}
        for(int i=0;i<geometry.size();i++){SketchPrimitive old=before.get(i),now=geometry.get(i);if(now.closedProfile()&&!same(old,now))ProfileDependencies.propagate(p,old.id,now);}
    }
    private static boolean same(SketchPrimitive a,SketchPrimitive b){return a.x1==b.x1&&a.y1==b.y1&&a.x2==b.x2&&a.y2==b.y2&&Arrays.equals(a.vertices,b.vertices)&&Arrays.equals(a.curves,b.curves);}

    public static String describe(String key) {
        if(key.startsWith("depth:"))return "głębokość operacji nr "+(Integer.parseInt(key.substring(6))+1);
        if(key.startsWith("hole:"))return (key.endsWith(":d")?"średnica":"głębokość")+" otworu nr "+(Integer.parseInt(key.split(":")[1])+1);
        if(key.startsWith("pattern:"))return (key.endsWith(":n")?"liczba wystąpień":"odstęp")+" szyku nr "+(Integer.parseInt(key.split(":")[1])+1);
        if(key.startsWith("dim:"))return "wymiar szkicu";
        return key;
    }
    /** Renumbers operation-index bindings after removal of operation {@code removed}. */
    public static void removed(CadProject p,int removed) {
        Map<String,String> next=new LinkedHashMap<>();
        for(Map.Entry<String,String> e:p.parameterBindings.entrySet()) {
            String key=e.getKey();java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(depth|hole|pattern):(\\d+)(.*)$").matcher(key);
            if(!m.matches()){next.put(key,e.getValue());continue;}
            int i=Integer.parseInt(m.group(2));if(i==removed)continue;
            next.put(m.group(1)+":"+(i>removed?i-1:i)+m.group(3),e.getValue());
        }
        p.parameterBindings.clear();p.parameterBindings.putAll(next);
    }

    public static JSONArray toJson(CadProject p)throws JSONException {
        JSONArray list=new JSONArray();for(Parameter x:p.parameters)list.put(new JSONObject().put("name",x.name).put("expression",x.expression).put("length",x.length));return list;
    }
    public static void read(CadProject p,JSONObject root)throws JSONException {
        JSONArray list=root.optJSONArray("parameters");
        if(list!=null)for(int i=0;i<list.length();i++){JSONObject o=list.getJSONObject(i);p.parameters.add(new Parameter(o.getString("name"),o.getString("expression"),o.optBoolean("length",true)));}
        JSONObject bindings=root.optJSONObject("parameterBindings");
        if(bindings!=null){Iterator<String> keys=bindings.keys();while(keys.hasNext()){String k=keys.next();p.parameterBindings.put(k,bindings.getString(k));}}
        try{evaluate(p);}catch(IllegalArgumentException error){throw new JSONException(error.getMessage());}
    }
}
