package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.*;

/**
 * Sketch areas bounded by any combination of closed profiles and open lines or arcs, as in
 * Fusion: two overlapping circles give three areas, a line across a rectangle splits it in two.
 * The kernel splits the sketch plane by all elements (OCCT General Fuse) and keeps the faces
 * that contain the tapped seed points, so the result stays an exact B-Rep region.
 *
 * <p>Kernel record -31: opcode as profile extrusions, element count n, seed count m, 0, 0,
 * signed depth, origin xyz, U xyz, V xyz (fields 6..15 as {@link ExtrusionPlan#feature}),
 * then m seed points (u, v) and n elements: closed profiles [type, x1, y1, x2, y2, tail] or open
 * chains [3000+k, 0, 0, 0, 0, k segments (kind 0 line / 1 arc: x1, y1, xm, ym, x2, y2)].</p>
 *
 * <p>The profile-id slot holds "area:id|id|…" (record element order). Editing an element
 * replaces it inside the record; a seed that no longer lies in any area is reported, never guessed.</p>
 */
public final class SketchArea {
    private SketchArea() {}
    public static final double TYPE=-31;
    public static final String PREFIX="area:";
    public static boolean is(double[] f){return f!=null&&f.length>=21&&f[0]==TYPE;}
    public static boolean isRef(String ref){return ref!=null&&ref.startsWith(PREFIX);}
    /** Selection entry of a tapped area: its seed point in sketch coordinates. */
    public static String seedRef(double u,double v){return "seed:"+u+","+v;}
    public static boolean isSeed(String ref){return ref!=null&&ref.startsWith("seed:");}
    public static double[] seed(String ref){String[] p=ref.substring(5).split(",");return new double[]{Double.parseDouble(p[0]),Double.parseDouble(p[1])};}

    /** Sketch elements that bound areas: closed profiles and open lines / line-arc chains. */
    public static boolean element(SketchPrimitive p) {
        if(p.construction||p.isText()||p.isPoint()||p.isEllipseArc())return false;
        if(p.closedProfile())return true;
        return p.type.equals("Linia")||p.curves!=null&&!p.closedGeometry();
    }
    static double[] encode(SketchPrimitive p) {
        if(p.closedProfile())return SketchRegion.profile(p);
        double[] segments=p.curves!=null?p.curves:new double[]{0,p.x1,p.y1,0,0,p.x2,p.y2};
        for(int i=0;i<segments.length;i+=7)if(segments[i]!=0&&segments[i]!=1)throw new IllegalArgumentException("Krzywa Béziera nie ogranicza obszaru – użyj linii i łuków");
        double[] r=new double[5+segments.length];r[0]=3000+segments.length/7;System.arraycopy(segments,0,r,5,segments.length);return r;
    }
    static int elementLength(double[] f,int at) {
        if(at+5>f.length)throw new IllegalArgumentException("Niekompletny element obszaru");double t=f[at];
        if(t>=3001&&t<=3128&&t==Math.floor(t)){int len=5+((int)t-3000)*7;if(at+len>f.length)throw new IllegalArgumentException("Niekompletny element obszaru");return len;}
        return SketchRegion.profileLength(f,at);
    }
    static SketchPrimitive decode(String id,double[] f,int at) {
        int len=elementLength(f,at);double t=f[at];
        if(t>=3001){double[] s=Arrays.copyOfRange(f,at+5,at+len);if(s.length==7&&s[0]==0)return new SketchPrimitive(id,"Linia",s[1],s[2],s[5],s[6]);return new SketchPrimitive(id,s,false);}
        double[] d=new double[16+len-5];d[0]=t;System.arraycopy(f,at+1,d,2,4);System.arraycopy(f,at+5,d,16,len-5);
        return SketchPrimitive.fromFeature(id,t==1?"Okrąg":"Prostokąt",d,2,16);
    }
    /** All area elements of a sketch packed for {@link NativeCadEngine#sketchAreas}; ids in the same order. */
    public static double[] pack(List<SketchPrimitive> elements) {
        List<double[]> parts=new ArrayList<>();for(SketchPrimitive p:elements)parts.add(encode(p));return SweepLoft.concat(parts);
    }
    public static List<SketchPrimitive> elements(List<SketchPrimitive> sketch){List<SketchPrimitive> r=new ArrayList<>();for(SketchPrimitive p:sketch)try{if(element(p)){encode(p);r.add(p);}}catch(IllegalArgumentException ignored){}return r;}
    public static String ref(List<SketchPrimitive> elements){StringBuilder b=new StringBuilder(PREFIX);for(int i=0;i<elements.size();i++){if(i>0)b.append('|');b.append(elements.get(i).id);}return b.toString();}

    public static double[] record(CadProject p,List<SketchPrimitive> elements,List<double[]> seeds,String operation,double first,double second,boolean reverse,boolean symmetric) {
        if(elements.isEmpty()||seeds.isEmpty())throw new IllegalArgumentException("Wskaż obszar szkicu");
        double[] head=Arrays.copyOf(ExtrusionPlan.feature(p,new SketchPrimitive("anchor","Prostokąt",0,0,1,1),operation,first,second,reverse,symmetric),16);
        head[0]=TYPE;head[3]=seeds.size();head[4]=head[5]=0;return with(head,seeds,elements);
    }
    static double[] with(double[] head,List<double[]> seeds,List<SketchPrimitive> elements) {
        if(elements.size()>512||seeds.size()>64)throw new IllegalArgumentException("Zbyt wiele elementów lub obszarów");
        List<double[]> parts=new ArrayList<>();double[] h=Arrays.copyOf(head,16);h[2]=elements.size();h[3]=seeds.size();parts.add(h);
        double[] s=new double[seeds.size()*2];for(int i=0;i<seeds.size();i++){s[2*i]=seeds.get(i)[0];s[2*i+1]=seeds.get(i)[1];}parts.add(s);
        for(SketchPrimitive e:elements)parts.add(encode(e));
        double[] r=SweepLoft.concat(parts);validate(r);return r;
    }
    public static int length(double[] packed,int offset) {
        double[] view=Arrays.copyOfRange(packed,offset,packed.length);int n=(int)view[2],m=(int)view[3],at=16+2*m;
        for(int i=0;i<n;i++)at+=elementLength(view,at);return at;
    }
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis obszaru");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry obszaru muszą być skończone");
        int n=(int)f[2],m=(int)f[3];if(f[2]!=n||f[3]!=m||n<1||n>512||m<1||m>64||f[4]!=0||f[5]!=0)throw new IllegalArgumentException("Niepoprawny zapis obszaru");
        FeatureDirection.validateOperation(f[1]);if(Math.abs(f[6])<1e-6||Math.abs(f[6])>10000)throw new IllegalArgumentException("Niepoprawna głębokość obszaru");
        int at=16+2*m;for(int i=0;i<n;i++)at+=elementLength(f,at);if(at!=f.length)throw new IllegalArgumentException("Niepoprawna długość zapisu obszaru");
    }
    static List<double[]> seeds(double[] f){List<double[]> r=new ArrayList<>();for(int i=0;i<(int)f[3];i++)r.add(new double[]{f[16+2*i],f[17+2*i]});return r;}
    static List<SketchPrimitive> stored(double[] f,String ref) {
        List<String> ids=SketchRegion.ids(ref);int n=(int)f[2];if(ids.size()!=n)throw new IllegalArgumentException("Odwołania obszaru nie odpowiadają zapisowi operacji");
        List<SketchPrimitive> r=new ArrayList<>();int at=16+2*(int)f[3];for(int i=0;i<n;i++){r.add(decode(ids.get(i),f,at));at+=elementLength(f,at);}return r;
    }
    public static double[] replace(double[] f,String ref,String oldId,SketchPrimitive shape) {
        if(!element(shape))throw new IllegalArgumentException("Element ograniczający obszar musi pozostać linią, łukiem albo zamkniętym konturem");
        List<SketchPrimitive> elements=stored(f,ref);List<String> ids=SketchRegion.ids(ref);
        for(int i=0;i<ids.size();i++)if(ids.get(i).equals(oldId))elements.set(i,shape);
        return with(f,seeds(f),elements);
    }
    public static double[] base(CadProject p,List<SketchPrimitive> elements,List<double[]> seeds,double first,double second,boolean reverse,boolean symmetric) {
        int plane=p.sketchPlane.equals("XZ")?1:p.sketchPlane.equals("YZ")?2:0;
        double[] feature=record(p,elements,seeds,"new",first,second,plane==1?!reverse:reverse,symmetric),result=new double[7+feature.length];
        result[0]=-10;result[1]=FeatureDirection.signed(first,reverse,symmetric);result[2]=second;result[6]=Math.copySign(first+second,result[1]);
        System.arraycopy(feature,0,result,7,feature.length);ExtrusionPlan.validateBase(result);return result;
    }
    /** Small square around the first seed: the arrow of the extent editor sits in the area. */
    public static SketchPrimitive anchor(String id,double[] f){double u=f[16],v=f[17];return new SketchPrimitive(id,"Prostokąt",u-.5,v-.5,u+.5,v+.5);}

    /** Areas of a packed element list as computed by the kernel: area and closed loops (u, v). */
    public static final class Area {
        public final double area;public final List<double[]> loops;
        Area(double area,List<double[]> loops){this.area=area;this.loops=loops;}
        public boolean contains(double x,double y){boolean in=false;for(double[] l:loops)if(SketchRegion.contains(l,x,y))in=!in;return in;}
    }
    public static List<Area> areas(double[] packed) {
        List<Area> r=new ArrayList<>();if(packed==null||packed.length<1)return r;int count=(int)packed[0],at=1;
        for(int i=0;i<count;i++){double a=packed[at++];int loops=(int)packed[at++];List<double[]> l=new ArrayList<>();
            for(int k=0;k<loops;k++){int n=(int)packed[at++];l.add(Arrays.copyOfRange(packed,at,at+2*n));at+=2*n;}r.add(new Area(a,l));}
        return r;
    }
    /** Smallest area containing the point, or null. */
    public static Area at(List<Area> areas,double x,double y){Area best=null;for(Area a:areas)if(a.contains(x,y)&&(best==null||a.area<best.area))best=a;return best;}
    /** Exact area of a nested region (outer minus islands) for comparison with the kernel's area. */
    public static double regionArea(List<SketchPrimitive> sketch,String ref) {
        List<SketchPrimitive> shapes=SketchRegion.resolve(sketch,ref);if(shapes==null)return Double.NaN;
        double a=exactArea(shapes.get(0));for(int i=1;i<shapes.size();i++)a-=exactArea(shapes.get(i));return a;
    }
    static double exactArea(SketchPrimitive p) {
        if(p.type.equals("Okrąg"))return Math.PI*Math.pow(Math.hypot(p.x2-p.x1,p.y2-p.y1),2);
        if(p.isEllipse())return Math.PI*p.major()*p.minor;
        if(p.vertices!=null&&!SketchSpline.is(p))return Math.abs(SketchContours.area(p.vertices));
        if(p.vertices!=null)return Math.abs(SketchContours.area(SketchSpline.sample(p.vertices,256)));
        if(p.curves!=null)return Math.abs(SketchContours.area(SketchCurves.samples(p.curves,2048)));
        return Math.abs((p.x2-p.x1)*(p.y2-p.y1));
    }
}
