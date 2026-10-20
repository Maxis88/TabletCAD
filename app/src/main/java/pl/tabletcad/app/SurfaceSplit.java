package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.CadProject;
import pl.tabletcad.app.model.SketchPrimitive;

/**
 * Split a body by a surface that need not be flat: an open sketch path (line, arc, open spline,
 * chained lines and arcs) extruded symmetrically along its sketch normal through the body.
 *
 * <p>Record: -26, id of the new body, surface depth, sketch origin xyz, u xyz, v xyz, n,
 * flip, 0, 0, then n path segments of 7 values (0 line, 1 arc, 2 cubic Bezier whose end is the
 * start of the next slot) and a terminal slot (9, end x, end y, 0, 0, 0, 0). The part left of the
 * path direction keeps the target body (flip swaps the sides). The path is refreshed from the
 * referenced sketch elements before each evaluation, so editing the curve updates the split.</p>
 */
public final class SurfaceSplit {
    public static final double TYPE=-26;
    public static final String REF="split:";
    private SurfaceSplit() {}
    public static boolean is(double[] f){return f!=null&&f.length>=30&&f[0]==TYPE;}
    public static int created(double[] f){return (int)f[1];}
    public static boolean flipped(double[] f){return f[13]==1;}
    public static int length(double[] packed,int offset){return 16+7*((int)packed[offset+12]+1);}

    /** Open path of one or more chained elements of one sketch, with the terminal slot. */
    static double[] path(List<SweepLoft.Placed> elements) {
        if(elements.isEmpty())throw new IllegalArgumentException("Wskaż krzywą podziału");
        if(elements.size()==1&&SketchSpline.is(elements.get(0).shape)) {
            double[] p=elements.get(0).shape.vertices;
            if(SketchSpline.closed(p))throw new IllegalArgumentException("Krzywa podziału musi być otwarta (zamknięty splajn użyj do wycięcia)");
            double[] spans=SketchSpline.spans(p);int n=spans.length/8;double[] out=new double[(n+1)*7];
            for(int i=0;i<n;i++){out[7*i]=2;System.arraycopy(spans,8*i,out,7*i+1,6);}
            out[7*n]=9;out[7*n+1]=p[p.length-2];out[7*n+2]=p[p.length-1];return out;
        }
        for(SweepLoft.Placed e:elements)if(SketchSpline.is(e.shape))throw new IllegalArgumentException("Splajn może być tylko jedynym elementem krzywej podziału");
        double[] chain=SweepLoft.chain(elements);int n=chain.length/7;
        if(Math.hypot(chain[1]-chain[chain.length-2],chain[2]-chain[chain.length-1])<1e-6)throw new IllegalArgumentException("Krzywa podziału musi być otwarta");
        double[] out=Arrays.copyOf(chain,chain.length+7);out[7*n]=9;out[7*n+1]=chain[chain.length-2];out[7*n+2]=chain[chain.length-1];return out;
    }
    public static double[] create(int created,List<SweepLoft.Placed> elements,double depth,boolean flip) {
        double[] path=path(elements);int n=path.length/7-1;
        if(n>128)throw new IllegalArgumentException("Krzywa podziału może mieć najwyżej 128 odcinków");
        double[] f=new double[16+path.length];f[0]=TYPE;f[1]=created;f[2]=depth;
        System.arraycopy(elements.get(0).frame,0,f,3,9);f[12]=n;f[13]=flip?1:0;
        System.arraycopy(path,0,f,16,path.length);validate(f);return f;
    }
    public static String ref(List<SweepLoft.Placed> elements){StringBuilder b=new StringBuilder(REF);for(int i=0;i<elements.size();i++){if(i>0)b.append('|');b.append(elements.get(i).ref());}return b.toString();}
    public static List<SweepLoft.Placed> elements(CadProject p,String ref) {
        if(ref==null||!ref.startsWith(REF))throw new IllegalArgumentException("Brak odwołania do krzywej podziału");
        List<SweepLoft.Placed> list=new ArrayList<>();for(String r:ref.substring(REF.length()).split("\\|"))list.add(SweepLoft.resolve(p,r));return list;
    }
    /** Current geometry from the referenced curve; keeps the new body id, depth and side. */
    public static double[] refresh(CadProject p,double[] record,String ref) {
        return create(created(record),elements(p,ref),record[2],flipped(record));
    }
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis podziału powierzchnią");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry podziału muszą być skończone");
        int n=(int)f[12];
        if(f[1]!=Math.floor(f[1])||f[1]<2)throw new IllegalArgumentException("Niepoprawny identyfikator nowej bryły");
        if(!(f[2]>0)||f[2]>20000)throw new IllegalArgumentException("Niepoprawna wysokość powierzchni podziału");
        if(f[12]!=n||n<1||n>128||f.length!=16+7*(n+1))throw new IllegalArgumentException("Niepoprawna długość krzywej podziału");
        if((f[13]!=0&&f[13]!=1)||f[14]!=0||f[15]!=0)throw new IllegalArgumentException("Niepoprawne pola zarezerwowane podziału");
        double[] u={f[6],f[7],f[8]},v={f[9],f[10],f[11]};
        if(Math.abs(u[0]*u[0]+u[1]*u[1]+u[2]*u[2]-1)>1e-6||Math.abs(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]-1)>1e-6||Math.abs(u[0]*v[0]+u[1]*v[1]+u[2]*v[2])>1e-6)throw new IllegalArgumentException("Niepoprawny układ szkicu podziału");
        for(int i=0;i<n;i++){double k=f[16+7*i];if(k!=0&&k!=1&&k!=2)throw new IllegalArgumentException("Nieznany typ odcinka krzywej podziału");}
        if(f[16+7*n]!=9)throw new IllegalArgumentException("Brak zakończenia krzywej podziału");
        for(int i=16;i<f.length;i++)if(Math.abs(f[i])>5000)throw new IllegalArgumentException("Krzywa podziału poza zakresem ±5000 mm");
    }
    public static String describe(double[] f){return "Podział powierzchnią ("+(int)f[12]+" odc.)"+(flipped(f)?" · strony odwrócone":"");}
    /** Elements usable as a split curve: open lines, arcs, open splines. */
    public static boolean usable(SketchPrimitive s) {
        if(s.isPoint())return false;
        if(SketchSpline.is(s))return !SketchSpline.closed(s.vertices);
        return (s.type.equals("Linia")||s.type.equals("Pomocnicza")||s.type.equals("Łuk"));
    }

    // ---- Split by a surface through several curves (loft of open curves, record -27) ----
    public static final double LOFT_TYPE=-27;
    public static final String LOFT_REF="loftsplit:";
    public static boolean isLoft(double[] f){return f!=null&&f.length>=16&&f[0]==LOFT_TYPE;}
    public static int loftLength(double[] packed,int offset) {
        int k=(int)packed[offset+3],at=offset+16;
        for(int s=0;s<k;s++){if(at+10>packed.length)throw new IllegalArgumentException("Niekompletny podział powierzchnią");at+=10+7*((int)packed[at+9]+1);}
        return at-offset;
    }
    /** Surface through the curves in the given order (each from a different sketch); left of the first curve keeps the body. */
    public static double[] createLoft(int created,List<SweepLoft.Placed> curves,boolean flip) {
        if(curves.size()<2||curves.size()>16)throw new IllegalArgumentException("Powierzchnia przez krzywe wymaga od 2 do 16 krzywych");
        Set<String> sketches=new HashSet<>();for(SweepLoft.Placed c:curves)if(!sketches.add(c.sketchId))throw new IllegalArgumentException("Każda krzywa musi leżeć w innym szkicu");
        List<double[]> parts=new ArrayList<>();double[] head=new double[16];head[0]=LOFT_TYPE;head[1]=created;head[3]=curves.size();head[4]=flip?1:0;parts.add(head);
        for(SweepLoft.Placed c:curves){double[] path=path(Collections.singletonList(c));double[] sec=new double[10+path.length];System.arraycopy(c.frame,0,sec,0,9);sec[9]=path.length/7-1;System.arraycopy(path,0,sec,10,path.length);parts.add(sec);}
        double[] f=SweepLoft.concat(parts);validateLoft(f);return f;
    }
    public static String loftRef(List<SweepLoft.Placed> curves){StringBuilder b=new StringBuilder(LOFT_REF);for(int i=0;i<curves.size();i++){if(i>0)b.append('|');b.append(curves.get(i).ref());}return b.toString();}
    public static double[] refreshLoft(CadProject p,double[] record,String ref) {
        if(ref==null||!ref.startsWith(LOFT_REF))throw new IllegalArgumentException("Brak odwołań do krzywych podziału");
        List<SweepLoft.Placed> list=new ArrayList<>();for(String r:ref.substring(LOFT_REF.length()).split("\\|"))list.add(SweepLoft.resolve(p,r));
        return createLoft((int)record[1],list,record[4]==1);
    }
    public static void validateLoft(double[] f) {
        if(!isLoft(f))throw new IllegalArgumentException("Niepoprawny zapis podziału powierzchnią");
        for(double v:f)if(!Double.isFinite(v)||Math.abs(v)>10000)throw new IllegalArgumentException("Parametry podziału poza zakresem");
        int k=(int)f[3];if(f[1]!=Math.floor(f[1])||f[1]<2||f[3]!=k||k<2||k>16||(f[4]!=0&&f[4]!=1)||f[2]!=0)throw new IllegalArgumentException("Niepoprawne parametry podziału powierzchnią");
        for(int i=5;i<16;i++)if(f[i]!=0)throw new IllegalArgumentException("Niepoprawne pola zarezerwowane podziału");
        int at=16;for(int s=0;s<k;s++){if(at+10>f.length)throw new IllegalArgumentException("Niekompletny przekrój podziału");int n=(int)f[at+9];if(n<1||n>128)throw new IllegalArgumentException("Niepoprawna krzywa podziału");
            if(f[at+10+7*n]!=9)throw new IllegalArgumentException("Brak zakończenia krzywej podziału");at+=10+7*(n+1);}
        if(at!=f.length)throw new IllegalArgumentException("Niepoprawna długość zapisu podziału");
    }
}
