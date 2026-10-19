package pl.tabletcad.app;

import java.util.*;
import org.json.*;
import pl.tabletcad.app.model.*;

/**
 * Loft (Przejście) and Sweep (Przeciągnięcie) between sketch profiles, plus whole-body patterns.
 *
 * <p>Kernel records carry the placed geometry; the references to the source sketch elements are
 * kept in the operation's profile-id slot ("loft:sketch/entity|…", "sweep:sketch/entity;sketch/entity|…").
 * Before every evaluation the geometry is refreshed from the current sketches, so editing a
 * profile or path updates the solid. A missing element is reported instead of being guessed.</p>
 */
public final class SweepLoft {
    public static final double LOFT=-19,SWEEP=-20,BODY_PATTERN=-21;
    private SweepLoft() {}
    public static boolean loft(double[] f){return f!=null&&f.length>=16&&f[0]==LOFT;}
    public static boolean sweep(double[] f){return f!=null&&f.length>=16&&f[0]==SWEEP;}
    public static boolean bodyPattern(double[] f){return f!=null&&f.length==16&&f[0]==BODY_PATTERN;}
    public static boolean creates(double[] f){return (loft(f)||sweep(f))&&((int)f[1]&4)!=0;}

    /** Sketch element with the frame of its sketch. */
    public static final class Placed {
        public final String sketchId,sketchName;public final SketchPrimitive shape;public final double[] frame;
        Placed(String sketchId,String sketchName,SketchPrimitive shape,double[] frame){this.sketchId=sketchId;this.sketchName=sketchName;this.shape=shape;this.frame=frame;}
        public String ref(){return sketchId+"/"+shape.id;}
    }
    /** All elements of the open sketch and of saved sketches (the open one wins on duplicate ids). */
    public static List<Placed> elements(CadProject p) {
        List<Placed> result=new ArrayList<>();
        double[] current={p.sketchOriginX,p.sketchOriginY,p.sketchOriginZ,p.sketchUx,p.sketchUy,p.sketchUz,p.sketchVx,p.sketchVy,p.sketchVz};
        for(SketchPrimitive s:p.sketch)result.add(new Placed(p.sketchId,p.sketchName,s,current));
        for(JSONObject snapshot:p.savedSketches) {
            String id=snapshot.optString("id");if(id.equals(p.sketchId))continue;
            JSONArray frame=snapshot.optJSONArray("frame"),entities=snapshot.optJSONArray("entities");if(frame==null||frame.length()!=9||entities==null)continue;
            double[] f=new double[9];for(int i=0;i<9;i++)f[i]=frame.optDouble(i);
            for(int i=0;i<entities.length();i++)try{result.add(new Placed(id,snapshot.optString("name","Szkic"),SketchPrimitive.fromJson(entities.getJSONObject(i)),f));}catch(JSONException ignored){}
        }
        return result;
    }
    static Placed resolve(CadProject p,String ref) {
        for(Placed e:elements(p))if(e.ref().equals(ref))return e;
        throw new IllegalArgumentException("Nie odnaleziono elementu szkicu użytego przez operację – przywróć go albo usuń operację");
    }
    /** Section: frame (origin, u, v), profile type, x1, y1, x2, y2, tail. */
    static double[] section(Placed e) {
        if(!e.shape.closedProfile())throw new IllegalArgumentException("Przekrój musi być zamkniętym profilem zwykłym");
        double[] head=new double[14];System.arraycopy(e.frame,0,head,0,9);
        head[9]=e.shape.featureType();head[10]=e.shape.x1;head[11]=e.shape.y1;head[12]=e.shape.x2;head[13]=e.shape.y2;
        return e.shape.appendVertices(head);
    }
    static double[] concat(List<double[]> parts){int n=0;for(double[] a:parts)n+=a.length;double[] r=new double[n];int at=0;for(double[] a:parts){System.arraycopy(a,0,r,at,a.length);at+=a.length;}return r;}

    public static String loftRef(List<Placed> sections){StringBuilder b=new StringBuilder("loft:");for(int i=0;i<sections.size();i++){if(i>0)b.append('|');b.append(sections.get(i).ref());}return b.toString();}
    public static double[] loftRecord(List<Placed> sections,int flags,boolean ruled) {
        if(sections.size()<2||sections.size()>16)throw new IllegalArgumentException("Przejście wymaga od 2 do 16 profili");
        Set<String> sketches=new HashSet<>();for(Placed s:sections)if(!sketches.add(s.sketchId))throw new IllegalArgumentException("Każdy profil przejścia musi leżeć w innym szkicu (innej płaszczyźnie)");
        if(flags!=0&&flags!=1&&flags!=4)throw new IllegalArgumentException("Niepoprawny rodzaj operacji");
        List<double[]> parts=new ArrayList<>();double[] head=new double[16];head[0]=LOFT;head[1]=flags;head[2]=sections.size();head[3]=ruled?1:0;parts.add(head);
        for(Placed s:sections)parts.add(section(s));return concat(parts);
    }
    public static String sweepRef(Placed profile,List<Placed> path){StringBuilder b=new StringBuilder("sweep:").append(profile.ref()).append(';');for(int i=0;i<path.size();i++){if(i>0)b.append('|');b.append(path.get(i).ref());}return b.toString();}
    /** Path elements chained end to end in the order given; each may be reversed. */
    static double[] chain(List<Placed> path) {
        if(path.isEmpty())throw new IllegalArgumentException("Wskaż ścieżkę przeciągnięcia");
        String sketch=path.get(0).sketchId;List<double[]> segments=new ArrayList<>();
        for(Placed e:path) {
            if(!e.sketchId.equals(sketch))throw new IllegalArgumentException("Elementy ścieżki muszą leżeć w jednym szkicu");
            SketchPrimitive s=e.shape;
            if(s.curves!=null)for(int i=0;i<s.curves.length;i+=7)segments.add(Arrays.copyOfRange(s.curves,i,i+7));
            else if(s.vertices!=null&&!SketchSpline.is(s)){for(int i=0;i<s.vertices.length;i+=2){int j=(i+2)%s.vertices.length;segments.add(new double[]{0,s.vertices[i],s.vertices[i+1],0,0,s.vertices[j],s.vertices[j+1]});}}
            else if(s.type.equals("Linia")||s.type.equals("Pomocnicza"))segments.add(new double[]{0,s.x1,s.y1,0,0,s.x2,s.y2});
            else throw new IllegalArgumentException("Ścieżka może składać się z linii, łuków i konturów");
        }
        // Orient every segment so that it starts where the previous one ends.
        List<double[]> ordered=new ArrayList<>();ordered.add(segments.get(0));
        if(segments.size()>1&&!touches(segments.get(0),5,segments.get(1))&&touches(segments.get(0),1,segments.get(1)))ordered.set(0,reversed(segments.get(0)));
        for(int i=1;i<segments.size();i++) {
            double[] last=ordered.get(i-1),next=segments.get(i);
            if(near(last[5],last[6],next[1],next[2]))ordered.add(next);
            else if(near(last[5],last[6],next[5],next[6]))ordered.add(reversed(next));
            else throw new IllegalArgumentException("Elementy ścieżki nie łączą się końcami – wskaż je po kolei");
        }
        return concat(ordered);
    }
    private static boolean touches(double[] a,int end,double[] b){return near(a[end],a[end+1],b[1],b[2])||near(a[end],a[end+1],b[5],b[6]);}
    private static boolean near(double ax,double ay,double bx,double by){return Math.hypot(ax-bx,ay-by)<1e-6;}
    private static double[] reversed(double[] s){return new double[]{s[0],s[5],s[6],s[3],s[4],s[1],s[2]};}
    public static double[] sweepRecord(Placed profile,List<Placed> path,int flags) {
        if(flags!=0&&flags!=1&&flags!=4)throw new IllegalArgumentException("Niepoprawny rodzaj operacji");
        double[] segments=chain(path);
        double[] head=new double[16];head[0]=SWEEP;head[1]=flags;head[2]=segments.length/7;
        if(head[2]>128)throw new IllegalArgumentException("Ścieżka może mieć maksymalnie 128 odcinków");
        return concat(Arrays.asList(head,section(profile),path.get(0).frame,segments));
    }
    /** Current geometry of a loft/sweep from its references. */
    public static double[] refresh(CadProject p,double[] record,String ref) {
        if(ref==null)throw new IllegalArgumentException("Brak odwołań do szkiców operacji");
        if(loft(record)&&ref.startsWith("loft:")) {
            List<Placed> sections=new ArrayList<>();for(String r:ref.substring(5).split("\\|"))sections.add(resolve(p,r));
            return loftRecord(sections,(int)record[1],record[3]==1);
        }
        if(sweep(record)&&ref.startsWith("sweep:")) {
            String[] parts=ref.substring(6).split(";",2);if(parts.length!=2)throw new IllegalArgumentException("Niepoprawne odwołania przeciągnięcia");
            List<Placed> path=new ArrayList<>();for(String r:parts[1].split("\\|"))path.add(resolve(p,r));
            return sweepRecord(resolve(p,parts[0]),path,(int)record[1]);
        }
        throw new IllegalArgumentException("Niepoprawne odwołania operacji");
    }
    public static void validate(double[] f) {
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry operacji muszą być skończone");
        if(bodyPattern(f)) {
            int kind=(int)f[1];if(f[1]!=kind||kind<1||kind>3||f[2]!=0)throw new IllegalArgumentException("Niepoprawny szyk bryły");
            if(Math.abs(f[6]*f[6]+f[7]*f[7]+f[8]*f[8]-1)>1e-6)throw new IllegalArgumentException("Kierunek szyku bryły musi być jednostkowy");
            for(int i=11;i<16;i++)if(f[i]!=0)throw new IllegalArgumentException("Niepoprawne pola zarezerwowane");
            if(kind==1?f[9]!=2||f[10]!=0:f[9]<2||f[9]>100||f[9]!=Math.floor(f[9])||(kind==2?Math.abs(f[10])<.001:f[10]<.01||f[10]>360))throw new IllegalArgumentException("Niepoprawne parametry szyku bryły");
            return;
        }
        if(!loft(f)&&!sweep(f))throw new IllegalArgumentException("Niepoprawny zapis operacji");
        int flags=(int)f[1];if(f[1]!=flags||(flags!=0&&flags!=1&&flags!=4))throw new IllegalArgumentException("Niepoprawny rodzaj operacji");
        for(int i=4;i<16;i++)if(f[i]!=0)throw new IllegalArgumentException("Niepoprawne pola zarezerwowane");
        int at=16;
        if(loft(f)){int k=(int)f[2];if(f[2]!=k||k<2||k>16||(f[3]!=0&&f[3]!=1))throw new IllegalArgumentException("Niepoprawna liczba profili przejścia");for(int i=0;i<k;i++)at+=sectionLength(f,at);}
        else {if(f[3]!=0||f[2]<1||f[2]>128||f[2]!=Math.floor(f[2]))throw new IllegalArgumentException("Niepoprawna ścieżka przeciągnięcia");at+=sectionLength(f,at)+9+(int)f[2]*7;}
        if(at!=f.length)throw new IllegalArgumentException("Niepoprawna długość zapisu operacji");
    }
    static int sectionLength(double[] f,int at) {
        if(at+14>f.length)throw new IllegalArgumentException("Niekompletny przekrój");double t=f[at+9];
        int tail=t==0||t==1?0:t==2000?1:t>=1002&&t<=1128&&t==Math.floor(t)?((int)t-1000)*7:t>=5&&t<=258&&t==Math.floor(t)?((int)t-2)*2:-1;
        if(tail<0||at+14+tail>f.length)throw new IllegalArgumentException("Niepoprawny profil przekroju");return 14+tail;
    }
    /** Record length for packing. */
    public static int length(double[] packed,int offset) {
        double[] view=Arrays.copyOfRange(packed,offset,packed.length);
        if(view[0]==BODY_PATTERN)return 16;
        int at=16;
        if(view[0]==LOFT){for(int i=0;i<(int)view[2];i++)at+=sectionLength(view,at);return at;}
        return at+sectionLength(view,at)+9+(int)view[2]*7;
    }
    public static double[] bodyPatternRecord(int kind,double[] origin,double[] direction,int count,double value) {
        double l=Math.sqrt(direction[0]*direction[0]+direction[1]*direction[1]+direction[2]*direction[2]);if(!(l>1e-9))throw new IllegalArgumentException("Niepoprawny kierunek");
        double[] f=new double[16];f[0]=BODY_PATTERN;f[1]=kind;for(int i=0;i<3;i++){f[3+i]=origin[i];f[6+i]=direction[i]/l;}f[9]=kind==1?2:count;f[10]=kind==1?0:value;validate(f);return f;
    }
    public static String label(double[] f) {
        if(loft(f))return "Przejście";if(sweep(f))return "Przeciągnięcie";
        return f[1]==1?"Lustro bryły":f[1]==2?"Szyk liniowy bryły":"Szyk kołowy bryły";
    }
}
