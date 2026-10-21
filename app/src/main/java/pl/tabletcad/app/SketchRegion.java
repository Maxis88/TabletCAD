package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.*;

/**
 * Sketch region with islands: the area between an outer closed profile and the closed profiles
 * lying inside it (e.g. the frame between two nested squares, a plate with circular holes).
 *
 * <p>Kernel record -30: opcode as profile extrusions, island count k, 0, 0, 0, signed depth,
 * origin xyz, U xyz, V xyz (fields 6..15 exactly as {@link ExtrusionPlan#feature}), then k+1 exact
 * profiles [type, x1, y1, x2, y2, tail]: the outer boundary first, then the islands. Circles,
 * arcs and ellipses stay exact B-Rep curves.</p>
 *
 * <p>The profile-id slot holds "region:outer|island|…" (sketch element ids). Before every
 * evaluation the geometry is refreshed from those elements, so dimension edits update the solid;
 * an element that no longer exists keeps its stored geometry (as for plain profile extrusions).</p>
 */
public final class SketchRegion {
    private SketchRegion() {}
    public static final double TYPE=-30;
    public static final String PREFIX="region:";
    public static boolean is(double[] f){return f!=null&&f.length>=21&&f[0]==TYPE;}
    public static boolean isRef(String ref){return ref!=null&&ref.startsWith(PREFIX);}

    // ---- References ----
    public static String ref(String outer,Collection<String> islands) {
        if(islands.isEmpty())return outer;
        StringBuilder b=new StringBuilder(PREFIX).append(outer);for(String id:islands)b.append('|').append(id);return b.toString();
    }
    /** Element ids of a reference, the outer boundary first (a plain id is returned alone). */
    public static List<String> ids(String ref) {
        if(ref==null||ref.isEmpty())return new ArrayList<>();
        if(!isRef(ref))return new ArrayList<>(Collections.singletonList(ref));
        return new ArrayList<>(Arrays.asList(ref.substring(PREFIX.length()).split("\\|")));
    }
    public static String outer(String ref){List<String> ids=ids(ref);return ids.isEmpty()?null:ids.get(0);}
    public static List<String> islands(String ref){List<String> ids=ids(ref);return ids.isEmpty()?ids:new ArrayList<>(ids.subList(1,ids.size()));}
    /** True when the reference (plain or region) uses the sketch element. */
    public static boolean mentions(String ref,String id){return id!=null&&!id.isEmpty()&&ids(ref).contains(id);}
    public static String rename(String ref,String oldId,String newId) {
        if(!isRef(ref))return oldId.equals(ref)?newId:ref;
        List<String> ids=ids(ref);for(int i=0;i<ids.size();i++)if(ids.get(i).equals(oldId))ids.set(i,newId);
        return ref(ids.get(0),ids.subList(1,ids.size()));
    }

    // ---- Planar outlines for picking ----
    /** Closed polyline approximating the profile (for containment only; never used as geometry). */
    static double[] outline(SketchPrimitive p) {
        if(p.type.equals("Okrąg")){double r=Math.hypot(p.x2-p.x1,p.y2-p.y1);double[] o=new double[128];for(int i=0;i<64;i++){double a=2*Math.PI*i/64;o[2*i]=p.x1+r*Math.cos(a);o[2*i+1]=p.y1+r*Math.sin(a);}return o;}
        if(p.isEllipse()){double a=p.major(),ux=(p.x2-p.x1)/a,uy=(p.y2-p.y1)/a;double[] o=new double[128];for(int i=0;i<64;i++){double t=2*Math.PI*i/64,c=a*Math.cos(t),s=p.minor*Math.sin(t);o[2*i]=p.x1+c*ux-s*uy;o[2*i+1]=p.y1+c*uy+s*ux;}return o;}
        if(p.vertices!=null)return SketchSpline.is(p)?SketchSpline.sample(p.vertices,24):p.vertices;
        if(p.curves!=null)return SketchCurves.samples(p.curves,96);
        return new double[]{Math.min(p.x1,p.x2),Math.min(p.y1,p.y2),Math.max(p.x1,p.x2),Math.min(p.y1,p.y2),Math.max(p.x1,p.x2),Math.max(p.y1,p.y2),Math.min(p.x1,p.x2),Math.max(p.y1,p.y2)};
    }
    static boolean contains(double[] poly,double x,double y) {
        boolean inside=false;int n=poly.length/2;
        for(int i=0,j=n-1;i<n;j=i++){double ax=poly[2*i],ay=poly[2*i+1],bx=poly[2*j],by=poly[2*j+1];if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)inside=!inside;}
        return inside;
    }
    static double area(double[] poly){return Math.abs(SketchContours.area(poly));}
    /** Every outline point of the island lies inside the outer outline and the outer is not inside the island. */
    static boolean inside(double[] island,double[] outer) {
        if(area(island)>=area(outer))return false;
        for(int i=0;i<island.length;i+=2)if(!contains(outer,island[i],island[i+1]))return false;
        for(int i=0;i<outer.length;i+=2)if(contains(island,outer[i],outer[i+1]))return false;
        return true;
    }
    static boolean usable(SketchPrimitive p){return p.closedProfile()&&!p.isText()&&!p.isEllipseArc()&&!p.isPoint();}

    /**
     * Region tapped at (x, y): the smallest profile around the point minus the outermost profiles
     * lying inside it that do not contain the point. Returns a plain id when there are no islands,
     * a region reference otherwise, or null when no profile surrounds the point.
     */
    public static String pick(List<SketchPrimitive> sketch,double x,double y) {
        SketchPrimitive outer=null;double best=Double.POSITIVE_INFINITY;Map<String,double[]> outlines=new HashMap<>();
        for(SketchPrimitive p:sketch)if(usable(p)){double[] o=outline(p);outlines.put(p.id,o);if(contains(o,x,y)&&area(o)<best){best=area(o);outer=p;}}
        if(outer==null)return null;
        double[] boundary=outlines.get(outer.id);List<SketchPrimitive> inner=new ArrayList<>();
        for(SketchPrimitive p:sketch)if(usable(p)&&p!=outer&&inside(outlines.get(p.id),boundary)&&!contains(outlines.get(p.id),x,y))inner.add(p);
        List<String> islands=new ArrayList<>();
        for(SketchPrimitive p:inner){boolean nested=false;for(SketchPrimitive q:inner)if(q!=p&&inside(outlines.get(p.id),outlines.get(q.id))){nested=true;break;}if(!nested)islands.add(p.id);}
        return ref(outer.id,islands);
    }

    /**
     * Selections tapped one by one become the material to extrude: a region whose island is also
     * selected absorbs it (the island is filled, its own islands become holes of the merged region).
     */
    public static List<String> merge(List<String> selections) {
        List<String> result=new ArrayList<>(selections);boolean changed=true;
        while(changed) {
            changed=false;
            outer:for(int a=0;a<result.size();a++)for(int b=0;b<result.size();b++) {
                if(a==b)continue;List<String> holes=islands(result.get(a));String filled=outer(result.get(b));
                if(!holes.contains(filled))continue;
                holes.remove(filled);holes.addAll(islands(result.get(b)));
                String merged=ref(outer(result.get(a)),holes);
                int keep=Math.min(a,b);result.remove(Math.max(a,b));result.set(keep,merged);changed=true;break outer;
            }
        }
        return result;
    }
    /** Sketch elements of a region in the open sketch, the outer first; null when one is missing. */
    public static List<SketchPrimitive> resolve(List<SketchPrimitive> sketch,String ref) {
        List<SketchPrimitive> result=new ArrayList<>();
        for(String id:ids(ref)){SketchPrimitive found=null;for(SketchPrimitive p:sketch)if(p.id.equals(id))found=p;if(found==null||!usable(found))return null;result.add(found);}
        return result;
    }

    // ---- Kernel record ----
    static double[] profile(SketchPrimitive s) {
        double[] head={s.featureType(),s.x1,s.y1,s.x2,s.y2};return s.appendVertices(head);
    }
    static int profileLength(double[] f,int at) {
        if(at+5>f.length)throw new IllegalArgumentException("Niekompletny profil obszaru");double t=f[at];
        int tail=t==0||t==1?0:t==2000?1:t>=1002&&t<=1128&&t==Math.floor(t)?((int)t-1000)*7:t>=5&&t<=258&&t==Math.floor(t)?((int)t-2)*2:-1;
        if(tail<0||at+5+tail>f.length)throw new IllegalArgumentException("Niepoprawny profil obszaru");return 5+tail;
    }
    public static double[] record(CadProject p,List<SketchPrimitive> shapes,String operation,double first,double second,boolean reverse,boolean symmetric) {
        if(shapes.size()<2)throw new IllegalArgumentException("Obszar wymaga konturu zewnętrznego i co najmniej jednej wyspy");
        if(shapes.size()>257)throw new IllegalArgumentException("Obszar ma zbyt wiele wysp");
        double[] head=Arrays.copyOf(ExtrusionPlan.feature(p,shapes.get(0),operation,first,second,reverse,symmetric),16);
        head[0]=TYPE;head[2]=shapes.size()-1;head[3]=head[4]=head[5]=0;
        return withProfiles(head,shapes);
    }
    static double[] withProfiles(double[] head,List<SketchPrimitive> shapes) {
        List<double[]> parts=new ArrayList<>();parts.add(Arrays.copyOf(head,16));for(SketchPrimitive s:shapes)parts.add(profile(s));
        double[] r=SweepLoft.concat(parts);r[2]=shapes.size()-1;validate(r);return r;
    }
    public static int length(double[] packed,int offset) {
        double[] view=Arrays.copyOfRange(packed,offset,packed.length);int k=(int)view[2],at=16;
        for(int i=0;i<=k;i++)at+=profileLength(view,at);return at;
    }
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis obszaru");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry obszaru muszą być skończone");
        int k=(int)f[2];if(f[2]!=k||k<1||k>256||f[3]!=0||f[4]!=0||f[5]!=0)throw new IllegalArgumentException("Niepoprawna liczba wysp obszaru");
        FeatureDirection.validateOperation(f[1]);
        if(Math.abs(f[6])<1e-6||Math.abs(f[6])>10000)throw new IllegalArgumentException("Niepoprawna głębokość obszaru");
        int at=16;for(int i=0;i<=k;i++)at+=profileLength(f,at);
        if(at!=f.length)throw new IllegalArgumentException("Niepoprawna długość zapisu obszaru");
    }
    /** Stored profiles of a record (outer first) as sketch elements named by the reference ids. */
    static List<SketchPrimitive> stored(double[] f,String ref) {
        List<String> ids=ids(ref);int k=(int)f[2];if(ids.size()!=k+1)throw new IllegalArgumentException("Odwołania obszaru nie odpowiadają zapisowi operacji");
        List<SketchPrimitive> result=new ArrayList<>();int at=16;
        for(int i=0;i<=k;i++){int len=profileLength(f,at);double[] d=new double[16+len-5];d[0]=f[at];System.arraycopy(f,at+1,d,2,4);System.arraycopy(f,at+5,d,16,len-5);
            result.add(SketchPrimitive.fromFeature(ids.get(i),d[0]==1?"Okrąg":"Prostokąt",d,2,16));at+=len;}
        return result;
    }
    /**
     * Record with one referenced element replaced by its edited geometry (depth, direction, frame
     * and body routing preserved), the same rule as {@link ProfileDependencies#replace} for one profile.
     */
    public static double[] replace(double[] f,String ref,String oldId,SketchPrimitive shape) {
        if(!usable(shape))throw new IllegalArgumentException("Kontur obszaru musi pozostać zamkniętą geometrią zwykłą");
        if(ExtrusionPlan.placedBase(f)){double[] inner=replace(Arrays.copyOfRange(f,7,f.length),ref,oldId,shape);double[] r=Arrays.copyOf(f,7+inner.length);System.arraycopy(inner,0,r,7,inner.length);ExtrusionPlan.validateBase(r);return r;}
        List<SketchPrimitive> shapes=stored(f,ref);List<String> ids=ids(ref);
        for(int i=0;i<ids.size();i++)if(ids.get(i).equals(oldId))shapes.set(i,shape);
        return withProfiles(f,shapes);
    }
    /** First solid of an empty document: placed base (-10) embedding the region record. */
    public static double[] base(CadProject p,List<SketchPrimitive> shapes,double first,double second,boolean reverse,boolean symmetric) {
        int plane=p.sketchPlane.equals("XZ")?1:p.sketchPlane.equals("YZ")?2:0;
        double[] feature=record(p,shapes,"new",first,second,plane==1?!reverse:reverse,symmetric),result=new double[7+feature.length];
        result[0]=-10;result[1]=FeatureDirection.signed(first,reverse,symmetric);result[2]=second;result[6]=Math.copySign(first+second,result[1]);
        System.arraycopy(feature,0,result,7,feature.length);ExtrusionPlan.validateBase(result);return result;
    }
    /** Outer boundary as a sketch element (arrow placement, profile editing). */
    public static SketchPrimitive outerProfile(String id,double[] f) {
        int len=profileLength(f,16);double[] d=new double[16+len-5];d[0]=f[16];System.arraycopy(f,17,d,2,4);System.arraycopy(f,21,d,16,len-5);
        return SketchPrimitive.fromFeature(id,d[0]==1?"Okrąg":"Prostokąt",d,2,16);
    }
    /** Closed outlines (outer first) of a stored record, for drawing the selected region. */
    public static List<double[]> outlines(List<SketchPrimitive> sketch,String ref) {
        List<double[]> result=new ArrayList<>();List<SketchPrimitive> shapes=resolve(sketch,ref);if(shapes==null)return result;
        for(SketchPrimitive s:shapes)result.add(outline(s));return result;
    }
}
