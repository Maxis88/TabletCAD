package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.CadProject;
import pl.tabletcad.app.model.SketchPrimitive;

/**
 * Sketch text: layout of glyph outlines flat (rotated about the start of the baseline) or along a
 * circle, and the extrusion record of the letters. Pure logic; the outlines of the font come from
 * a {@link Font} (Android typeface on the device, a synthetic font in host tests).
 *
 * <p>Parameters: anchor x, anchor y, letter height (capital letter, mm), layout (0 flat, 1 along
 * a circle reading clockwise with letters standing on it, 2 along a circle reading
 * counter-clockwise with letters hanging inside), angle in degrees (flat: rotation of the
 * baseline; circle: direction from the centre to the middle of the text), circle radius,
 * font (0 sans, 1 bold, 2 serif, 3 monospace), extra letter spacing in mm. For a circle the
 * anchor is the centre of the circle, for flat text the start of the baseline.</p>
 *
 * <p>Record -28: opcode (as profile extrusions), payload length, loop count, 0, 0, signed depth,
 * sketch origin xyz, U xyz, V xyz, then the loops [n, x0, y0, ...].</p>
 */
public final class SketchText {
    private SketchText() {}
    public static final int FLAT=0,OUTSIDE=1,INSIDE=2;
    public static final int PARAMS=8;
    public static final double TYPE=-28;
    public static final String[] FONTS={"Bezszeryfowa","Pogrubiona","Szeryfowa","Stała szerokość"};
    public static final String[] LAYOUTS={"Płasko","Po okręgu – na zewnątrz","Po okręgu – wewnątrz"};

    /** One glyph in font units where the capital letter height is 1; y up, baseline at 0, pen at x=0. */
    public static final class Glyph {
        public final double advance;public final List<double[]> loops;
        public Glyph(double advance,List<double[]> loops){this.advance=advance;this.loops=loops;}
    }
    public interface Font { Glyph glyph(String character,int font); }

    public static double[] params(double x,double y,double height,int layout,double angle,double radius,int font,double spacing) {
        double[] p={x,y,height,layout,angle,radius,font,spacing};validateParams(p);return p;
    }
    public static void validateParams(double[] p) {
        if(p==null||p.length!=PARAMS)throw new IllegalArgumentException("Niepoprawne parametry napisu");
        for(double v:p)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry napisu muszą być skończone");
        if(Math.abs(p[0])>5000||Math.abs(p[1])>5000)throw new IllegalArgumentException("Napis poza zakresem ±5000 mm");
        if(p[2]<.2||p[2]>1000)throw new IllegalArgumentException("Wysokość liter od 0,2 do 1000 mm");
        if(p[3]!=FLAT&&p[3]!=OUTSIDE&&p[3]!=INSIDE)throw new IllegalArgumentException("Nieznany układ napisu");
        if(p[3]!=FLAT&&(p[5]<p[2]*.5||p[5]>5000))throw new IllegalArgumentException("Promień okręgu napisu musi wynosić co najmniej połowę wysokości liter");
        if(p[6]!=Math.floor(p[6])||p[6]<0||p[6]>=FONTS.length)throw new IllegalArgumentException("Nieznana czcionka");
        if(p[7]<-p[2]||p[7]>p[2]*5)throw new IllegalArgumentException("Niepoprawny odstęp liter");
    }
    public static void validate(double[] params,double[] loops) {
        validateParams(params);
        if(loops==null||loops.length<7||loops.length>60000)throw new IllegalArgumentException("Napis nie ma konturów liter (albo jest zbyt długi)");
        int at=0,count=0;
        while(at<loops.length){int n=(int)loops[at];if(loops[at]!=n||n<3||n>4096||at+1+2*n>loops.length)throw new IllegalArgumentException("Niepoprawne kontury napisu");
            for(int i=at+1;i<at+1+2*n;i++)if(!Double.isFinite(loops[i])||Math.abs(loops[i])>5000)throw new IllegalArgumentException("Kontury napisu poza zakresem ±5000 mm");
            at+=1+2*n;count++;}
        if(count>2000)throw new IllegalArgumentException("Napis ma zbyt wiele konturów");
    }
    public static int loopCount(double[] loops){int at=0,count=0;while(at<loops.length){at+=1+2*(int)loops[at];count++;}return count;}
    public static List<double[]> loops(double[] packed){List<double[]> out=new ArrayList<>();int at=0;while(at<packed.length){int n=(int)packed[at];out.add(Arrays.copyOfRange(packed,at+1,at+1+2*n));at+=1+2*n;}return out;}
    public static double[] bounds(double[] packed) {
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
        for(double[] l:loops(packed))for(int i=0;i<l.length;i+=2){minX=Math.min(minX,l[i]);maxX=Math.max(maxX,l[i]);minY=Math.min(minY,l[i+1]);maxY=Math.max(maxY,l[i+1]);}
        if(!(minX<=maxX))throw new IllegalArgumentException("Napis nie ma konturów liter");
        return new double[]{minX,minY,maxX,maxY};
    }
    /** Code points of the text as strings (surrogate pairs kept together). */
    static List<String> characters(String text){List<String> out=new ArrayList<>();for(int i=0;i<text.length();){int c=text.codePointAt(i);out.add(new String(Character.toChars(c)));i+=Character.charCount(c);}return out;}

    /** Glyph outlines placed by the parameters, packed as loops in sketch coordinates. */
    public static double[] layout(String text,double[] p,Font font) {
        validateParams(p);if(text==null||text.trim().isEmpty())throw new IllegalArgumentException("Wpisz treść napisu");
        if(text.indexOf('\n')>=0)throw new IllegalArgumentException("Napis ma jedną linię");
        double h=p[2],spacing=p[7]/h;int layout=(int)p[3];
        // Glyphs in text units (height 1), pen positions along the baseline.
        List<double[]> local=new ArrayList<>();double pen=0;List<String> chars=characters(text);
        for(int k=0;k<chars.size();k++){Glyph g=font.glyph(chars.get(k),(int)p[6]);if(g==null)throw new IllegalArgumentException("Czcionka nie zawiera znaku „"+chars.get(k)+"”");
            for(double[] l:g.loops){double[] m=new double[l.length];for(int i=0;i<l.length;i+=2){m[i]=l[i]+pen;m[i+1]=l[i+1];}local.add(m);}
            pen+=g.advance+(k<chars.size()-1?spacing:0);}
        if(local.isEmpty())throw new IllegalArgumentException("Napis nie zawiera widocznych znaków");
        double width=pen;List<double[]> placed=new ArrayList<>();
        if(layout==FLAT) {
            double a=Math.toRadians(p[4]),c=Math.cos(a),s=Math.sin(a);
            for(double[] l:local){double[] m=new double[l.length];for(int i=0;i<l.length;i+=2){double x=l[i]*h,y=l[i+1]*h;m[i]=p[0]+x*c-y*s;m[i+1]=p[1]+x*s+y*c;}placed.add(m);}
        } else {
            double r=p[5],centre=Math.toRadians(p[4]);
            if(width*h>2*Math.PI*r*.98)throw new IllegalArgumentException("Napis nie mieści się na okręgu – zwiększ promień albo zmniejsz litery");
            double step=Math.max(.02,Math.min(.25,r/(h*40)));// max segment length in text units before bending
            for(double[] l:local){double[] d=densify(l,step);double[] m=new double[d.length];
                for(int i=0;i<d.length;i+=2){double x=(d[i]-width/2)*h,y=d[i+1]*h,t,rr;
                    if(layout==OUTSIDE){t=centre-x/r;rr=r+y;}else{t=centre+x/r;rr=r-y;}
                    m[i]=p[0]+rr*Math.cos(t);m[i+1]=p[1]+rr*Math.sin(t);}
                placed.add(m);}
        }
        int total=0;for(double[] l:placed)total+=1+l.length;
        double[] out=new double[total];int at=0;
        for(double[] l:placed){out[at++]=l.length/2;System.arraycopy(l,0,out,at,l.length);at+=l.length;}
        validate(p,out);return out;
    }
    /** Closed loop with no edge longer than step. */
    static double[] densify(double[] l,double step) {
        int n=l.length/2;List<Double> out=new ArrayList<>();
        for(int i=0;i<n;i++){int j=(i+1)%n;double ax=l[2*i],ay=l[2*i+1],bx=l[2*j],by=l[2*j+1];int parts=Math.max(1,(int)Math.ceil(Math.hypot(bx-ax,by-ay)/step));
            for(int k=0;k<parts;k++){double t=(double)k/parts;out.add(ax+(bx-ax)*t);out.add(ay+(by-ay)*t);}}
        if(out.size()>8192)return l;
        double[] r=new double[out.size()];for(int i=0;i<r.length;i++)r[i]=out.get(i);return r;
    }
    public static SketchPrimitive create(String id,String text,double[] params,Font font){return SketchPrimitive.text(id,text,params,layout(text,params,font));}
    /** Same text at new parameters (move, rotate, other layout). */
    public static SketchPrimitive with(SketchPrimitive t,String text,double[] params,Font font){return create(t.id,text,params,font);}
    /** Moved by (dx, dy) without the font: the outlines are translated exactly. */
    public static SketchPrimitive moved(SketchPrimitive t,double dx,double dy) {
        double[] p=t.textParams(),l=t.textLoops();p[0]+=dx;p[1]+=dy;
        int at=0;while(at<l.length){int n=(int)l[at];for(int i=0;i<n;i++){l[at+1+2*i]+=dx;l[at+2+2*i]+=dy;}at+=1+2*n;}
        return SketchPrimitive.text(t.id,t.text(),p,l);
    }

    /** Extrusion of the letters: "new", "join", "cut" or "intersect" like profile extrusions. */
    public static double[] feature(CadProject p,SketchPrimitive t,String operation,double first,boolean reverse,boolean symmetric) {
        if(!t.isText())throw new IllegalArgumentException("To nie jest napis");
        double signed=FeatureDirection.signed(first,reverse,symmetric);
        double[] loops=t.textLoops();double[] f=new double[16+loops.length];
        f[0]=TYPE;f[1]=FeatureDirection.featureCode(operation,symmetric);f[2]=loops.length;f[3]=loopCount(loops);f[6]=signed;
        f[7]=p.sketchOriginX;f[8]=p.sketchOriginY;f[9]=p.sketchOriginZ;f[10]=p.sketchUx;f[11]=p.sketchUy;f[12]=p.sketchUz;f[13]=p.sketchVx;f[14]=p.sketchVy;f[15]=p.sketchVz;
        System.arraycopy(loops,0,f,16,loops.length);validateFeature(f);return f;
    }
    public static boolean is(double[] f){return f!=null&&f.length>=23&&f[0]==TYPE;}
    public static int length(double[] packed,int offset){return 16+(int)packed[offset+2];}
    public static void validateFeature(double[] f) {
        if(!is(f)||f[2]!=Math.floor(f[2])||f.length!=16+(int)f[2]||f[4]!=0||f[5]!=0)throw new IllegalArgumentException("Niepoprawny zapis wyciągnięcia napisu");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry napisu muszą być skończone");
        if(!(Math.abs(f[6])>=1e-3)||Math.abs(f[6])>5000)throw new IllegalArgumentException("Niepoprawna głębokość napisu");
        double[] loops=Arrays.copyOfRange(f,16,f.length);if(loopCount(loops)!=(int)f[3])throw new IllegalArgumentException("Niepoprawna liczba konturów napisu");
        validate(new double[]{0,0,1,0,0,0,0,0},loops);
    }
    public static final String REF="text:";
    public static String ref(SweepLoft.Placed e){return REF+e.ref();}
    /**
     * The extrusion follows the referenced sketch text (edited content, size, position or a moved
     * sketch plane). A text that no longer exists keeps the stored letters.
     */
    public static double[] refresh(CadProject p,double[] f,String ref) {
        if(ref==null||!ref.startsWith(REF))return f;
        SweepLoft.Placed e;try{e=SweepLoft.resolve(p,ref.substring(REF.length()));}catch(IllegalArgumentException missing){return f;}
        if(!e.shape.isText())return f;
        double[] r=refresh(f,e.shape);System.arraycopy(e.frame,0,r,7,9);return r;
    }
    /** Same extrusion with the current outlines of the sketch text (after the text was edited). */
    public static double[] refresh(double[] f,SketchPrimitive t) {
        double[] loops=t.textLoops();double[] r=Arrays.copyOf(f,16+loops.length);r[2]=loops.length;r[3]=loopCount(loops);System.arraycopy(loops,0,r,16,loops.length);validateFeature(r);return r;
    }
    public static String describe(double[] f){return "Wyciągnięcie napisu ("+(int)f[3]+" konturów)";}
}
