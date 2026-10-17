package pl.tabletcad.app;

import java.util.*;
import org.json.*;
import pl.tabletcad.app.model.SketchPrimitive;

/** Exact linear point/axis/fixed constraints. Locked dimensions are rigid offsets. */
public final class SketchConstraints {
    private SketchConstraints() {}
    public static final class Link {
        public final String a,b;
        public final int ai,bi;
        public final String kind;
        public final double x,y;
        public Link(String a,int ai,String b,int bi){this("coincident",a,ai,b,bi,0,0);}
        public Link(String kind,String a,int ai,String b,int bi,double x,double y){this.kind=kind;this.a=a;this.ai=ai;this.b=b;this.bi=bi;this.x=x;this.y=y;}
        public static Link axis(String id,int first,int second,boolean horizontal){return new Link(horizontal?"horizontal":"vertical",id,first,id,second,0,0);}
        public static Link fixed(SketchPrimitive p,int index){double[] a=anchor(p,index);return new Link("fixed",p.id,index,p.id,index,a[0],a[1]);}
        public boolean uses(String id){return a.equals(id)||b.equals(id);}
        JSONObject json()throws JSONException{
            JSONObject result=new JSONObject().put("kind",kind).put("a",a).put("ai",ai).put("b",b).put("bi",bi);
            if(kind.equals("fixed"))result.put("x",x).put("y",y);return result;
        }
    }
    public static JSONArray toJson(List<Link> links)throws JSONException {
        JSONArray result=new JSONArray();for(Link l:links)result.put(l.json());return result;
    }
    public static List<Link> fromJson(JSONArray data,List<SketchPrimitive> geometry)throws JSONException {
        List<Link> result=new ArrayList<>();
        if(data!=null)for(int i=0;i<data.length();i++) {
            JSONObject item=data.getJSONObject(i);
            String kind=item.optString("kind","coincident");
            result.add(new Link(kind,item.getString("a"),item.getInt("ai"),item.getString("b"),item.getInt("bi"),
                kind.equals("fixed")?item.getDouble("x"):0,kind.equals("fixed")?item.getDouble("y"):0));
        }
        try{validate(geometry,result);}catch(IllegalArgumentException error){throw new JSONException(error.getMessage());}
        return result;
    }
    public static int anchorCount(SketchPrimitive p){return p.curves!=null?0:p.vertices!=null?p.vertices.length/2:p.type.equals("Okrąg")?1:p.type.equals("Prostokąt")?4:2;}
    private static int[] indices(SketchPrimitive p,int anchor) {
        if(anchor<0||anchor>=anchorCount(p))throw new IllegalArgumentException("Niepoprawny punkt więzu");
        if(p.vertices!=null)return new int[]{2*anchor,2*anchor+1};
        if(p.type.equals("Prostokąt"))return new int[][]{{0,1},{2,1},{2,3},{0,3}}[anchor];
        return new int[]{anchor*2,anchor*2+1};
    }
    private static double[] coords(SketchPrimitive p){return p.curves!=null?new double[0]:p.vertices!=null?p.vertices.clone():new double[]{p.x1,p.y1,p.x2,p.y2};}
    public static double[] anchor(SketchPrimitive p,int index){int[] i=indices(p,index);double[] c=coords(p);return new double[]{c[i[0]],c[i[1]]};}
    private static SketchPrimitive entity(List<SketchPrimitive> geometry,String id) {
        for(SketchPrimitive p:geometry)if(p.id.equals(id))return p;
        throw new IllegalArgumentException("Więzy odwołują się do brakującego elementu");
    }
    private static String key(Link l) {
        String a=l.a+":"+l.ai,b=l.b+":"+l.bi;
        return l.kind+":"+(a.compareTo(b)<0?a+"/"+b:b+"/"+a);
    }
    private static void structure(Link l) {
        if(!Arrays.asList("coincident","horizontal","vertical","fixed").contains(l.kind))throw new IllegalArgumentException("Nieznany typ więzu");
        if(l.a==null||l.b==null||l.a.isEmpty()||l.b.isEmpty())throw new IllegalArgumentException("Niepoprawny element więzu");
        boolean same=l.a.equals(l.b)&&l.ai==l.bi;
        if(l.kind.equals("fixed")) {
            if(!same||!Double.isFinite(l.x)||!Double.isFinite(l.y)||Math.abs(l.x)>5000||Math.abs(l.y)>5000)throw new IllegalArgumentException("Niepoprawny punkt stały");
        }else if(same)throw new IllegalArgumentException("Wybierz dwa różne punkty");
    }
    public static void validate(List<SketchPrimitive> geometry,List<Link> links) {
        Set<String> seen=new HashSet<>();
        for(Link l:links) {
            structure(l);if(!seen.add(key(l)))throw new IllegalArgumentException("Powtórzony więz punktów");
            double[] a=anchor(entity(geometry,l.a),l.ai),b=anchor(entity(geometry,l.b),l.bi);
            double error=l.kind.equals("fixed")?Math.hypot(a[0]-l.x,a[1]-l.y):l.kind.equals("horizontal")?Math.abs(a[1]-b[1])
                :l.kind.equals("vertical")?Math.abs(a[0]-b[0]):Math.hypot(a[0]-b[0],a[1]-b[1]);
            if(error>1e-6)throw new IllegalArgumentException("Geometria nie spełnia zapisanego więzu");
        }
    }
    public static List<SketchPrimitive> connect(List<SketchPrimitive> geometry,List<Link> links,Link added) {
        structure(added);
        for(Link l:links)if(key(l).equals(key(added)))throw new IllegalArgumentException("Ten więz jest już zapisany");
        if(added.kind.equals("coincident")&&added.a.equals(added.b))throw new IllegalArgumentException("Wybierz punkty dwóch różnych elementów");
        List<Link> all=new ArrayList<>(links);all.add(added);
        List<SketchPrimitive> input=geometry;
        // Give an unconstrained line a useful rotated starting position, even at 90 degrees.
        if((added.kind.equals("horizontal")||added.kind.equals("vertical"))&&added.a.equals(added.b)) {
            SketchPrimitive p=entity(geometry,added.a);
            if(p.vertices==null&&p.driving==null&&(p.type.equals("Linia")||p.type.equals("Pomocnicza"))&&added.ai==0&&added.bi==1) {
                double length=SketchDimensions.length(p);boolean h=added.kind.equals("horizontal");
                SketchPrimitive rotated=new SketchPrimitive(p.id,p.type,p.x1,p.y1,h?p.x1+Math.copySign(length,p.x2-p.x1):p.x1,h?p.y1:p.y1+Math.copySign(length,p.y2-p.y1));
                input=new ArrayList<>(geometry);input.set(input.indexOf(p),rotated);
            }
        }
        return solve(input,all,null,null,added,-1);
    }
    public static List<SketchPrimitive> edit(List<SketchPrimitive> geometry,List<Link> links,String id,SketchPrimitive candidate) {
        return solve(geometry,links,id,candidate,null,-1);
    }
    /** A dragged vertex drives only its coordinates; related vertices follow the solver. */
    public static List<SketchPrimitive> drag(List<SketchPrimitive> geometry,List<Link> links,SketchPrimitive original,SketchPrimitive candidate,int handle) {
        if(original.curves!=null)return solve(geometry,links,original.id,candidate,null,-1);
        int picked=handle==0||candidate==original?-1:original.type.equals("Prostokąt")?new int[]{0,2,1,3}[handle-1]:handle-1;
        return solve(geometry,links,original.id,candidate,null,picked);
    }
    /** Endpoint links merged into one contour vertex become topology, external links survive. */
    public static List<Link> rebindContour(List<SketchPrimitive> geometry,List<Link> links,SketchContours.Result joined) {
        List<Link> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Link original:links) {
            String a=original.a,b=original.b;int ai=original.ai,bi=original.bi;
            if(joined.consumed.contains(a)){ai=contourAnchor(joined.profile,anchor(entity(geometry,a),ai));a=joined.profile.id;}
            if(joined.consumed.contains(b)){bi=contourAnchor(joined.profile,anchor(entity(geometry,b),bi));b=joined.profile.id;}
            if(a.equals(b)&&ai==bi&&!original.kind.equals("fixed"))continue;
            Link remapped=new Link(original.kind,a,ai,b,bi,original.x,original.y);if(seen.add(key(remapped)))result.add(remapped);
        }
        List<SketchPrimitive> next=new ArrayList<>();for(SketchPrimitive p:geometry)if(!joined.consumed.contains(p.id))next.add(p);
        next.add(joined.profile);validate(next,result);return result;
    }
    private static int contourAnchor(SketchPrimitive profile,double[] point) {
        for(int i=0;i<anchorCount(profile);i++) {
            double[] vertex=anchor(profile,i);if(Math.hypot(point[0]-vertex[0],point[1]-vertex[1])<1e-6)return i;
        }
        throw new IllegalArgumentException("Połączenie konturu zmieniłoby punkt więzu — wyrównaj końce linii");
    }
    private static final class Union {
        final int[] parent,rank;final double[] offset;
        Union(int n){parent=new int[n];rank=new int[n];offset=new double[n];for(int i=0;i<n;i++)parent[i]=i;}
        int root(int i){if(parent[i]!=i){int p=parent[i];parent[i]=root(p);offset[i]+=offset[p];}return parent[i];}
        // value(b)-value(a)=delta
        void join(int a,int b,double delta) {
            int ra=root(a),rb=root(b);double difference=delta+offset[a]-offset[b];
            if(ra==rb){if(Math.abs(difference)>1e-6)throw new IllegalArgumentException("Sprzeczne więzy lub zablokowane wymiary");return;}
            if(rank[ra]<rank[rb]){parent[ra]=rb;offset[ra]=-difference;}
            else {parent[rb]=ra;offset[rb]=difference;if(rank[ra]==rank[rb])rank[ra]++;}
        }
    }
    private static List<SketchPrimitive> solve(List<SketchPrimitive> geometry,List<Link> links,String edited,SketchPrimitive candidate,Link added,int draggedAnchor) {
        List<SketchPrimitive> input=new ArrayList<>();Map<String,Integer> starts=new HashMap<>();int count=0;
        for(SketchPrimitive p:geometry) {
            if(starts.put(p.id,count)!=null)throw new IllegalArgumentException("Powtórzony identyfikator geometrii");
            SketchPrimitive shape=p.id.equals(edited)?candidate:p;
            if(shape==null||!shape.id.equals(p.id))throw new IllegalArgumentException("Niepoprawna edycja elementu");
            input.add(shape);count+=coords(shape).length;
        }
        Union union=new Union(count);double[] original=new double[count];
        for(SketchPrimitive p:input) {
            int start=starts.get(p.id);double[] c=coords(p);System.arraycopy(c,0,original,start,c.length);
            if(p.vertices==null&&(p.driving!=null||p.type.equals("Okrąg"))) {
                union.join(start,start+2,p.x2-p.x1);union.join(start+1,start+3,p.y2-p.y1);
            }
        }
        Set<String> seen=new HashSet<>();
        for(Link l:links) {
            structure(l);
            if(!seen.add(key(l)))throw new IllegalArgumentException("Powtórzony więz punktów");
            SketchPrimitive a=entity(input,l.a),b=entity(input,l.b);int[] ai=indices(a,l.ai),bi=indices(b,l.bi);
            if(l.kind.equals("coincident")||l.kind.equals("vertical"))union.join(starts.get(l.a)+ai[0],starts.get(l.b)+bi[0],0);
            if(l.kind.equals("coincident")||l.kind.equals("horizontal"))union.join(starts.get(l.a)+ai[1],starts.get(l.b)+bi[1],0);
        }
        Map<Integer,Double> pinned=new HashMap<>();
        for(Link l:links)if(l.kind.equals("fixed")) {
            int s=starts.get(l.a);int[] i=indices(entity(input,l.a),l.ai);
            pin(union,pinned,s+i[0],l.x);pin(union,pinned,s+i[1],l.y);
        }
        if(edited!=null) {
            Integer start=starts.get(edited);if(start==null)throw new IllegalArgumentException("Brak edytowanego elementu");
            if(draggedAnchor<0)for(int i=0;i<coords(candidate).length;i++)pin(union,pinned,start+i,original[start+i]);
            else for(int i:indices(candidate,draggedAnchor)) {
                // Fixed points are exact; project the pointer onto remaining free directions.
                int variable=start+i;if(!pinned.containsKey(union.root(variable)))pin(union,pinned,variable,original[variable]);
            }
        }
        if(added!=null) {
            SketchPrimitive p=entity(input,added.a);int[] i=indices(p,added.ai);int s=starts.get(p.id);
            pin(union,pinned,s+i[0],original[s+i[0]]);pin(union,pinned,s+i[1],original[s+i[1]]);
        }
        double[] sums=new double[count];int[] totals=new int[count];
        for(int i=0;i<count;i++){int root=union.root(i);sums[root]+=original[i]-union.offset[i];totals[root]++;}
        for(int i=0;i<count;i++){int root=union.root(i);original[i]=pinned.containsKey(root)?pinned.get(root)+union.offset[i]:sums[root]/totals[root]+union.offset[i];}
        List<SketchPrimitive> result=new ArrayList<>();
        for(SketchPrimitive p:input) {
            int s=starts.get(p.id);double[] c=Arrays.copyOfRange(original,s,s+coords(p).length);
            for(double v:c)if(!Double.isFinite(v)||Math.abs(v)>5000)throw new IllegalArgumentException("Więzy wyprowadzają geometrię poza ±5000 mm");
            SketchPrimitive solved;
            if(p.curves!=null)solved=p;
            else if(p.vertices!=null){SketchContours.validate(c);solved=new SketchPrimitive(p.id,c);}
            else {
                if(Math.hypot(c[2]-c[0],c[3]-c[1])<.01||p.type.equals("Prostokąt")&&(Math.abs(c[2]-c[0])<.01||Math.abs(c[3]-c[1])<.01))
                    throw new IllegalArgumentException("Połączenie tworzy element o zerowym rozmiarze");
                solved=new SketchPrimitive(p.id,p.type,c[0],c[1],c[2],c[3],p.driving);
            }
            result.add(solved);
        }
        validate(result,links);return result;
    }
    private static void pin(Union union,Map<Integer,Double> pinned,int i,double value) {
        int root=union.root(i);double base=value-union.offset[i];Double previous=pinned.put(root,base);
        if(previous!=null&&Math.abs(previous-base)>1e-6)throw new IllegalArgumentException("Sprzeczne połączenia: zmiana narusza zablokowane wymiary");
    }
}
