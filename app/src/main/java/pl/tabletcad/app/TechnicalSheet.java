package pl.tabletcad.app;

import android.graphics.*;
import java.util.*;
import pl.tabletcad.app.model.CadProject;

/** A4 landscape drawing in millimetres; same vector renderer for screen and PDF. */
public final class TechnicalSheet {
    /** Created on first draw, so the geometry queries (snapping, circles) work without a display. */
    private Paint paint;
    private final double[] segments;
    private final double[][] bounds=new double[4][4];
    private final double scale;
    private final String scaleLabel;
    private final double sectionFraction;
    public TechnicalSheet(double[] segments,double sectionFraction) {
        this.segments=segments;
        this.sectionFraction=sectionFraction;
        for(double[] b:bounds) {b[0]=b[1]=Double.POSITIVE_INFINITY;b[2]=b[3]=Double.NEGATIVE_INFINITY;}
        for(int i=0;i+5<segments.length;i+=6) {
            double[] b=bounds[(int)segments[i]];
            for(int j=2;j<=4;j+=2) {b[0]=Math.min(b[0],segments[i+j]);b[1]=Math.min(b[1],segments[i+j+1]);
                b[2]=Math.max(b[2],segments[i+j]);b[3]=Math.max(b[3],segments[i+j+1]);}
        }
        double fit=Double.POSITIVE_INFINITY;
        for(double[] b:bounds) if(Double.isFinite(b[0]))
            fit=Math.min(fit,Math.min(100/Math.max(.001,b[2]-b[0]),54/Math.max(.001,b[3]-b[1])));
        double chosen=Math.min(.001,fit);
        for(double s:new double[]{2,1,.5,.2,.1,.05,.02,.01,.005,.002,.001}) if(s<=fit) {chosen=s;break;}
        scale=chosen;
        scaleLabel=scale>=1 ? String.format(Locale.US,"%.0f:1",scale)
            : String.format(Locale.US,"1:%.0f",1/scale);
    }
    private void line(Canvas c,float x1,float y1,float x2,float y2,float width) {
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setColor(Color.BLACK);
        c.drawLine(x1,y1,x2,y2,paint);
    }
    private void text(Canvas c,String value,float x,float y,float size) {
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.BLACK);paint.setTextSize(size);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL));
        c.drawText(value,x,y,paint);
    }
    private float px(int v,double x) {return (float)((v==0||v==1?78:218)+(x-(bounds[v][0]+bounds[v][2])/2)*scale);}
    private float py(int v,double y) {return (float)((v==0||v==2?57:133)-(y-(bounds[v][1]+bounds[v][3])/2)*scale);}
    private void fittedText(Canvas c,String value,float x,float y,float size,float width) {
        paint.setTextSize(size);
        String fitted=value;
        while(fitted.length()>0&&paint.measureText(fitted)>width) fitted=fitted.substring(0,fitted.length()-1);
        if(!fitted.equals(value)&&fitted.length()>3) fitted=fitted.substring(0,fitted.length()-3)+"...";
        text(c,fitted,x,y,size);
    }
    public void draw(Canvas canvas,RectF area,CadProject project) {
        if(paint==null)paint=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
        canvas.save();
        float factor=Math.min(area.width()/297,area.height()/210);
        canvas.translate(area.centerX()-297*factor/2,area.centerY()-210*factor/2);
        canvas.scale(factor,factor);
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);canvas.drawRect(0,0,297,210,paint);
        paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.35f);
        canvas.drawRect(10,10,287,200,paint);
        text(canvas,"TabletCAD - RYSUNEK TECHNICZNY",15,18,3.5f);
        text(canvas,"Rzutowanie europejskie · metoda pierwszego kąta",15,24,2.5f);
        String[] titles={"PRZÓD","GÓRA","BOK LEWY",String.format(Locale.US,"PRZEKRÓJ A-A · X %.1f%%",sectionFraction*100)};
        for(int view=0;view<4;view++) {
            if(!Double.isFinite(bounds[view][0])) continue;
            if(view==3) hatch(canvas,view);
            for(int hidden=1;hidden>=0;hidden--) {
                paint.setPathEffect(hidden==1?new DashPathEffect(new float[]{1.7f,1},0):null);
                for(int i=0;i+5<segments.length;i+=6) if((int)segments[i]==view&&(int)segments[i+1]==hidden)
                    line(canvas,px(view,segments[i+2]),py(view,segments[i+3]),
                        px(view,segments[i+4]),py(view,segments[i+5]),hidden==1?.15f:.3f);
            }
            paint.setPathEffect(new DashPathEffect(new float[]{4,1,0.5f,1},0));
            float x0=px(view,bounds[view][0])-3,x1=px(view,bounds[view][2])+3;
            float y0=py(view,bounds[view][3])-3,y1=py(view,bounds[view][1])+3;
            float cx=(x0+x1)/2,cy=(y0+y1)/2;
            line(canvas,x0,cy,x1,cy,.13f);line(canvas,cx,y0,cx,y1,.13f);
            paint.setPathEffect(null);
            text(canvas,titles[view],view==0||view==1?40:180,view==0||view==2?28:172,2.8f);
            if(view==0||view==2) dimension(canvas,x0+3,y1+6,x1-3,y1+6,
                bounds[view][2]-bounds[view][0]);
            if(view==0) {
                float cutX=(float)(x0+3+(x1-x0-6)*sectionFraction);
                paint.setPathEffect(new DashPathEffect(new float[]{4,1,.5f,1},0));
                line(canvas,cutX,y0-2,cutX,y1+2,.3f);paint.setPathEffect(null);
                text(canvas,"A",cutX+2,y0-3,3);text(canvas,"A",cutX+2,y1+11,3);
                float dx=x0-7;
                line(canvas,dx,y0+3,dx,y1-3,.15f);
                line(canvas,dx,y0+3,dx-1,y0+5,.2f);line(canvas,dx,y0+3,dx+1,y0+5,.2f);
                line(canvas,dx,y1-3,dx-1,y1-5,.2f);line(canvas,dx,y1-3,dx+1,y1-5,.2f);
                line(canvas,dx-2,y0+3,x0,y0+3,.15f);line(canvas,dx-2,y1-3,x0,y1-3,.15f);
                canvas.save();canvas.rotate(-90,dx-1,cy);
                text(canvas,String.format(Locale.US,"%.2f",bounds[view][3]-bounds[view][1]),dx-5,cy,2.5f);
                canvas.restore();
            }
        }
        if(project.drawingDimensions!=null)for(double[] d:project.drawingDimensions)userDimension(canvas,d,false);
        if(pending!=null)userDimension(canvas,pending,true);
        line(canvas,10,180,287,180,.3f);
        line(canvas,170,180,170,200,.3f);line(canvas,235,180,235,200,.3f);
        line(canvas,170,190,287,190,.2f);
        fittedText(canvas,"Nazwa: "+project.name,14,185,3.2f,151);
        fittedText(canvas,"Nr rysunku: "+project.drawingNumber+"   Rewizja: "+project.drawingRevision,14,191,2.5f,151);
        fittedText(canvas,"Autor: "+project.drawingAuthor+"   Materiał: "+project.drawingMaterial,14,197,2.5f,151);
        text(canvas,"Skala "+scaleLabel,174,187,3);text(canvas,"A4 · 297 × 210",239,187,2.5f);
        text(canvas,"Jednostka: mm",174,197,2.5f);text(canvas,"Arkusz 1 / 1",239,197,2.5f);
        canvas.restore();
    }
    private void dimension(Canvas c,float x0,float y0,float x1,float y1,double value) {
        line(c,x0,y0-4,x0,y0+2,.15f);line(c,x1,y1-4,x1,y1+2,.15f);
        line(c,x0,y0,x1,y1,.15f);
        line(c,x0,y0,x0+2,y0-1,.2f);line(c,x0,y0,x0+2,y0+1,.2f);
        line(c,x1,y1,x1-2,y1-1,.2f);line(c,x1,y1,x1-2,y1+1,.2f);
        text(c,String.format(Locale.US,"%.2f",value),(x0+x1)/2-4,y0-1,2.5f);
    }
    /** Dimension being placed (preview), drawn in the accent colour. */
    public double[] pending;
    /** Kinds of user dimensions on the sheet. */
    public static final int ALIGNED=0,HORIZONTAL=1,VERTICAL=2,DIAMETER=3,RADIUS=4,ANGLE=5;
    /** Record length: view, x1, y1, x2, y2, kind, offset, x3, y3, tolerance +, tolerance −, anchors u1 v1 u2 v2. */
    public static final int LENGTH=15;
    /** Anchor value meaning "unknown" (records from 0.90/0.91). */
    public static final double NO_ANCHOR=-9;
    /** Record of a sheet dimension (view coordinates in mm, offset in paper mm). */
    public static double[] dimension(int view,double x1,double y1,double x2,double y2,int kind,double offset) {
        return new double[]{view,x1,y1,x2,y2,kind,offset,0,0,0,0,NO_ANCHOR,NO_ANCHOR,NO_ANCHOR,NO_ANCHOR};
    }
    /** Angle at apex (x1,y1) between the arms to (x2,y2) and (x3,y3); offset = arc radius on paper. */
    public static double[] angle(int view,double ax,double ay,double bx,double by,double cx,double cy) {
        double[] d=dimension(view,ax,ay,bx,by,ANGLE,12);d[7]=cx;d[8]=cy;return d;
    }
    /** Pads a 7-value record of 0.90/0.91 to the current layout. */
    public static double[] upgrade(double[] d) {
        if(d!=null&&d.length==7){double[] r=dimension((int)d[0],d[1],d[2],d[3],d[4],(int)d[5],d[6]);return r;}
        return d;
    }
    public static void validate(double[] d) {
        if(d==null||d.length!=LENGTH)throw new IllegalArgumentException("Niepoprawny wymiar arkusza");
        for(double v:d)if(!Double.isFinite(v))throw new IllegalArgumentException("Niepoprawny wymiar arkusza");
        if(d[0]<0||d[0]>3||d[0]!=Math.rint(d[0])||d[5]<0||d[5]>5||d[5]!=Math.rint(d[5])||Math.abs(d[6])>(d[5]==DIAMETER||d[5]==RADIUS?180:100)
            ||d[9]<0||d[9]>100||d[10]<0||d[10]>100)throw new IllegalArgumentException("Niepoprawny wymiar arkusza");
    }
    /** Records where each point lies within the view's extents, so the dimension can follow a changed model. */
    public double[] withAnchors(double[] d) {
        double[] r=d.clone();int v=(int)d[0];if(!hasView(v))return r;double[] b=bounds[v];double w=Math.max(1e-9,b[2]-b[0]),h=Math.max(1e-9,b[3]-b[1]);
        r[11]=(d[1]-b[0])/w;r[12]=(d[2]-b[1])/h;
        if((int)d[5]==ANGLE){r[13]=(d[3]-b[0])/w;r[14]=(d[4]-b[1])/h;}// apex and first arm; the second arm follows the apex shift
        else if((int)d[5]==DIAMETER||(int)d[5]==RADIUS){r[13]=Math.hypot(d[3]-d[1],d[4]-d[2])/Math.hypot(w,h);r[14]=0;}
        else{r[13]=(d[3]-b[0])/w;r[14]=(d[4]-b[1])/h;}
        return r;
    }
    /**
     * The record as it applies to the current model: unchanged while its points are still view
     * vertices; otherwise each point is looked up near the place its anchor maps to (the views
     * stretch with a changed size) and the nearest vertex or circle centre is taken. Returns null
     * when the geometry cannot be found again — then the dimension is shown as stale.
     */
    public double[] resolve(double[] d) {
        int v=(int)d[0],kind=(int)d[5];if(!hasView(v))return null;
        if(current(d))return d;
        if(d[11]==NO_ANCHOR)return null;
        double[] b=bounds[v];double w=b[2]-b[0],h=b[3]-b[1],reach=.08*Math.hypot(w,h)+1e-6;
        double[] r=d.clone();
        if(kind==DIAMETER||kind==RADIUS) {
            double ex=b[0]+d[11]*w,ey=b[1]+d[12]*h,best=reach;double[] hit=null;
            for(double[] c:circles())if((int)c[0]==v){double dist=Math.hypot(c[1]-ex,c[2]-ey);if(dist<best){best=dist;hit=c;}}
            if(hit==null)return null;double a=Math.toRadians(d[6]);
            r[1]=hit[1];r[2]=hit[2];r[3]=hit[1]+hit[3]*Math.cos(a);r[4]=hit[2]+hit[3]*Math.sin(a);return r;
        }
        double[] p1=nearestPoint(v,b[0]+d[11]*w,b[1]+d[12]*h,reach),p2=nearestPoint(v,b[0]+d[13]*w,b[1]+d[14]*h,reach);
        if(p1==null||p2==null)return null;
        if(kind==ANGLE){r[7]=d[7]+(p1[0]-d[1]);r[8]=d[8]+(p1[1]-d[2]);double[] p3=nearestPoint(v,r[7],r[8],reach);if(p3==null)return null;r[7]=p3[0];r[8]=p3[1];}
        r[1]=p1[0];r[2]=p1[1];r[3]=p2[0];r[4]=p2[1];
        return current(r)?r:null;
    }
    private boolean current(double[] d) {
        int v=(int)d[0],kind=(int)d[5];
        if(kind==DIAMETER||kind==RADIUS)return onCircle(v,d[1],d[2],Math.hypot(d[3]-d[1],d[4]-d[2]));
        boolean ok=onVertex(v,d[1],d[2])&&onVertex(v,d[3],d[4]);if(kind==ANGLE)ok&=onVertex(v,d[7],d[8]);return ok;
    }
    private double[] nearestPoint(int view,double x,double y,double reach) {
        double best=reach;double[] hit=null;
        for(int i=0;i+5<segments.length;i+=6)if((int)segments[i]==view)for(int j=2;j<=4;j+=2){double d=Math.hypot(segments[i+j]-x,segments[i+j+1]-y);if(d<best){best=d;hit=new double[]{segments[i+j],segments[i+j+1]};}}
        for(double[] c:circles())if((int)c[0]==view){double d=Math.hypot(c[1]-x,c[2]-y);if(d<best){best=d;hit=new double[]{c[1],c[2]};}}
        return hit;
    }
    /** Value with its tolerance, as printed. */
    public static String label(double[] d) {
        int kind=(int)d[5];double value=value(d);
        String base=kind==ANGLE?String.format(Locale.US,"%.1f°",value):(kind==DIAMETER?"Ø":kind==RADIUS?"R":"")+String.format(Locale.US,"%.2f",value);
        double plus=d.length>10?d[9]:0,minus=d.length>10?d[10]:0;
        if(plus==0&&minus==0)return base;
        if(Math.abs(plus-minus)<1e-12)return base+" ±"+trim(plus);
        return base+" +"+trim(plus)+"/−"+trim(minus);
    }
    private static String trim(double v){String t=String.format(Locale.US,"%.3f",v);while(t.endsWith("0"))t=t.substring(0,t.length()-1);if(t.endsWith("."))t=t.substring(0,t.length()-1);return t;}
    /** Automatic kind: nearly horizontal or vertical pairs get a horizontal/vertical dimension. */
    public static int autoKind(double x1,double y1,double x2,double y2) {
        double dx=Math.abs(x2-x1),dy=Math.abs(y2-y1);
        if(dy<=1e-6*Math.max(1,dx)||dy<dx*.02)return HORIZONTAL;
        if(dx<=1e-6*Math.max(1,dy)||dx<dy*.02)return VERTICAL;
        return ALIGNED;
    }
    /** True length of a sheet dimension in model millimetres (orthographic views are true size). */
    public static double value(double[] d) {
        double dx=d[3]-d[1],dy=d[4]-d[2];int kind=(int)d[5];
        if(kind==DIAMETER)return 2*Math.hypot(dx,dy);
        if(kind==RADIUS)return Math.hypot(dx,dy);
        if(kind==ANGLE){double ux=d[3]-d[1],uy=d[4]-d[2],vx=d[7]-d[1],vy=d[8]-d[2];return Math.toDegrees(Math.atan2(Math.abs(ux*vy-uy*vx),ux*vx+uy*vy));}
        return kind==HORIZONTAL?Math.abs(dx):kind==VERTICAL?Math.abs(dy):Math.hypot(dx,dy);
    }
    /** Paper coordinates (mm) of a screen point for the area the sheet is drawn into. */
    public static float[] paper(RectF area,float x,float y){return paper(area.left,area.top,area.width(),area.height(),x,y);}
    public static float[] paper(float left,float top,float width,float height,float x,float y) {
        float factor=Math.min(width/297,height/210),cx=left+width/2,cy=top+height/2;
        return new float[]{(x-(cx-297*factor/2))/factor,(y-(cy-210*factor/2))/factor};
    }
    /** Paper position (mm) of a point given in a view's coordinates. */
    /** Paper scale (paper mm per model mm). */
    public double scale(){return scale;}
    public float[] paperOf(int view,double x,double y){return hasView(view)?new float[]{px(view,x),py(view,y)}:null;}
    public boolean hasView(int view){return view>=0&&view<4&&Double.isFinite(bounds[view][0]);}
    /** Nearest segment end point within tolerance (paper mm): {view, x, y} in view coordinates, or null. */
    public double[] snap(float paperX,float paperY,double tolerance) {
        double best=tolerance;double[] hit=null;
        for(int i=0;i+5<segments.length;i+=6) {
            int view=(int)segments[i];
            for(int j=2;j<=4;j+=2) {
                double d=Math.hypot(px(view,segments[i+j])-paperX,py(view,segments[i+j+1])-paperY);
                if(d<best){best=d;hit=new double[]{view,segments[i+j],segments[i+j+1]};}
            }
        }
        return hit;
    }
    /** Whether a point is still an end point of a segment of the view (1 µm). */
    public boolean onVertex(int view,double x,double y) {
        for(double[] c:circles())if((int)c[0]==view&&Math.abs(c[1]-x)<1e-3&&Math.abs(c[2]-y)<1e-3)return true;
        for(int i=0;i+5<segments.length;i+=6)if((int)segments[i]==view)for(int j=2;j<=4;j+=2)if(Math.abs(segments[i+j]-x)<1e-3&&Math.abs(segments[i+j+1]-y)<1e-3)return true;
        return false;
    }
    /** Whether the view still has a vertex on the given circle (the dimensioned hole or arc exists). */
    public boolean onCircle(int view,double cx,double cy,double r) {
        int hits=0;for(int i=0;i+5<segments.length;i+=6)if((int)segments[i]==view&&Math.abs(Math.hypot(segments[i+2]-cx,segments[i+3]-cy)-r)<Math.max(1e-3,r*1e-3))if(++hits>=3)return true;
        return false;
    }
    /** Index of the user dimension whose value label is under the paper point, or −1. */
    public int dimensionAt(List<double[]> dims,float paperX,float paperY) {
        if(dims==null)return -1;
        for(int i=dims.size()-1;i>=0;i--){float[] label=labelPoint(dims.get(i));if(label!=null&&Math.abs(label[0]-paperX)<8&&Math.abs(label[1]-paperY)<4)return i;}
        return -1;
    }
    private float[][] dimensionLine(double[] d) {
        int view=(int)d[0];if(!hasView(view))return null;
        float ax=px(view,d[1]),ay=py(view,d[2]),bx=px(view,d[3]),by=py(view,d[4]);int kind=(int)d[5];float off=(float)d[6];
        float la,lb,lc,ld;// dimension line end points
        if(kind==HORIZONTAL){float y=Math.min(ay,by)-off;return new float[][]{{ax,ay,bx,by},{ax,y,bx,y}};}
        if(kind==VERTICAL){float x=Math.min(ax,bx)-off;return new float[][]{{ax,ay,bx,by},{x,ay,x,by}};}
        float dx=bx-ax,dy=by-ay,l=(float)Math.hypot(dx,dy);if(l<1e-4)return null;float nx=dy/l,ny=-dx/l;
        if(ny>0){nx=-nx;ny=-ny;}// offset upward on the sheet
        la=ax+nx*off;lb=ay+ny*off;lc=bx+nx*off;ld=by+ny*off;
        return new float[][]{{ax,ay,bx,by},{la,lb,lc,ld}};
    }
    private List<double[]> circles;
    /** Circles and arcs of all views (connected chains of visible segments on one circle): {view, cx, cy, r, closed}. */
    public List<double[]> circles() {
        if(circles!=null)return circles;
        int n=segments.length/6;int[] parent=new int[n];for(int i=0;i<n;i++)parent[i]=i;
        Map<String,Integer> owner=new HashMap<>();
        for(int i=0;i<n;i++){if(segments[6*i+1]!=0)continue;for(int j=2;j<=4;j+=2){String key=(int)segments[6*i]+":"+Math.round(segments[6*i+j]*1e5)+":"+Math.round(segments[6*i+j+1]*1e5);Integer o=owner.putIfAbsent(key,i);if(o!=null)union(parent,o,i);}}
        Map<Integer,List<Integer>> groups=new HashMap<>();
        for(int i=0;i<n;i++)if(segments[6*i+1]==0)groups.computeIfAbsent(find(parent,i),k->new ArrayList<>()).add(i);
        List<double[]> out=new ArrayList<>();
        for(List<Integer> g:groups.values()) {
            if(g.size()<6)continue;List<double[]> pts=new ArrayList<>();Map<String,Integer> degree=new HashMap<>();
            for(int i:g)for(int j=2;j<=4;j+=2){pts.add(new double[]{segments[6*i+j],segments[6*i+j+1]});degree.merge(Math.round(segments[6*i+j]*1e5)+":"+Math.round(segments[6*i+j+1]*1e5),1,Integer::sum);}
            double[] c=circle(pts.get(0),pts.get(pts.size()/3|1),pts.get((2*pts.size()/3)|1));if(c==null||c[2]<1e-3)continue;
            boolean round=true;for(double[] p:pts)if(Math.abs(Math.hypot(p[0]-c[0],p[1]-c[1])-c[2])>Math.max(1e-3,c[2]*.005)){round=false;break;}
            if(!round)continue;boolean closed=true;for(int d:degree.values())if(d!=2)closed=false;
            out.add(new double[]{segments[6*g.get(0)],c[0],c[1],c[2],closed?1:0});
        }
        circles=out;return out;
    }
    private static int find(int[] p,int i){while(p[i]!=i){p[i]=p[p[i]];i=p[i];}return i;}
    private static void union(int[] p,int a,int b){p[find(p,a)]=find(p,b);}
    /** Circle or arc whose outline passes under the paper point, or null. */
    public double[] circleAt(float paperX,float paperY,double tolerance) {
        double best=tolerance;double[] hit=null;
        for(double[] c:circles()){int v=(int)c[0];double d=Math.abs(Math.hypot(px(v,c[1])-paperX,py(v,c[2])-paperY)-c[3]*scale);if(d<best){best=d;hit=c;}}
        return hit;
    }
    /** Centre of a circle near the paper point: {view, cx, cy}, or null. */
    public double[] centreAt(float paperX,float paperY,double tolerance) {
        double best=tolerance;double[] hit=null;
        for(double[] c:circles()){int v=(int)c[0];double d=Math.hypot(px(v,c[1])-paperX,py(v,c[2])-paperY);if(d<best){best=d;hit=new double[]{v,c[1],c[2]};}}
        return hit;
    }
    private static double[] circle(double[] a,double[] b,double[] c) {
        double d=2*(a[0]*(b[1]-c[1])+b[0]*(c[1]-a[1])+c[0]*(a[1]-b[1]));if(Math.abs(d)<1e-12)return null;
        double a2=a[0]*a[0]+a[1]*a[1],b2=b[0]*b[0]+b[1]*b[1],c2=c[0]*c[0]+c[1]*c[1];
        double x=(a2*(b[1]-c[1])+b2*(c[1]-a[1])+c2*(a[1]-b[1]))/d,y=(a2*(c[0]-b[0])+b2*(a[0]-c[0])+c2*(b[0]-a[0]))/d;
        return new double[]{x,y,Math.hypot(a[0]-x,a[1]-y)};
    }
    /** Record of a diameter/radius dimension: centre, a point on the circle at the leader angle. */
    public static double[] circleDimension(int view,double cx,double cy,double r,boolean diameter,double angleDegrees) {
        double a=Math.toRadians(angleDegrees);return dimension(view,cx,cy,cx+r*Math.cos(a),cy+r*Math.sin(a),diameter?DIAMETER:RADIUS,angleDegrees);
    }
    private void circleDimensionDraw(Canvas c,double[] d,int colour) {
        int view=(int)d[0];float cx=px(view,d[1]),cy=py(view,d[2]),ex=px(view,d[3]),ey=py(view,d[4]);
        float dx=ex-cx,dy=ey-cy,r=(float)Math.hypot(dx,dy);if(r<.05)return;float ux=dx/r,uy=dy/r;
        boolean diameter=(int)d[5]==DIAMETER;
        float sx=diameter?cx-ux*r:cx,sy=diameter?cy-uy*r:cy,tx=ex+ux*7,ty=ey+uy*7;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.15f);paint.setColor(colour);c.drawLine(sx,sy,tx,ty,paint);
        String label=label(d);
        paint.setTextSize(2.5f);float w=paint.measureText(label);
        float hx=tx+(ux>=0?w+2:-(w+2));c.drawLine(tx,ty,hx,ty,paint);
        arrow(c,ex,ey,cx,cy,colour);if(diameter)arrow(c,sx,sy,cx,cy,colour);
        paint.setStyle(Paint.Style.FILL);paint.setColor(colour);c.drawText(label,Math.min(tx,hx)+1,ty-.8f,paint);
    }
    private float[] labelPoint(double[] stored){
        double[] found=resolve(stored);double[] d=found==null?stored:found;
        if((int)d[5]==ANGLE){float[] g=angleGeometry(d);if(g==null)return null;double mid=g[2]+g[3]/2;return new float[]{g[0]+(float)Math.cos(mid)*(g[4]+3),g[1]+(float)Math.sin(mid)*(g[4]+3)};}
        if((int)d[5]>=DIAMETER){int view=(int)d[0];if(!hasView(view))return null;float cx=px(view,d[1]),cy=py(view,d[2]),ex=px(view,d[3]),ey=py(view,d[4]);float dx=ex-cx,dy=ey-cy,r=(float)Math.hypot(dx,dy);if(r<.05)return null;return new float[]{ex+dx/r*7+(dx>=0?5:-5),ey+dy/r*7-1.2f};}float[][] g=dimensionLine(d);if(g==null)return null;return new float[]{(g[1][0]+g[1][2])/2,(g[1][1]+g[1][3])/2-1.2f};}
    private void userDimension(Canvas c,double[] stored,boolean preview) {
        // The dimension follows the model when its points can be found again; otherwise it is shown in red.
        double[] resolved=preview?stored:resolve(stored);boolean stale=resolved==null;double[] d=stale?stored:resolved;
        if((int)d[5]==ANGLE){angleDraw(c,d,preview?Color.rgb(13,148,136):stale?Color.rgb(196,64,52):Color.BLACK);return;}
        if((int)d[5]>=DIAMETER) {
            circleDimensionDraw(c,d,preview?Color.rgb(13,148,136):stale?Color.rgb(196,64,52):Color.BLACK);return;
        }
        float[][] g=dimensionLine(d);if(g==null)return;
        int colour=preview?Color.rgb(13,148,136):stale?Color.rgb(196,64,52):Color.BLACK;
        float[] p=g[0],q=g[1];
        paint.setPathEffect(null);
        // Extension lines from the geometry to just beyond the dimension line.
        ext(c,p[0],p[1],q[0],q[1],colour);ext(c,p[2],p[3],q[2],q[3],colour);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.15f);paint.setColor(colour);c.drawLine(q[0],q[1],q[2],q[3],paint);
        arrow(c,q[0],q[1],q[2],q[3],colour);arrow(c,q[2],q[3],q[0],q[1],colour);
        String label=label(d);
        float angle=(float)Math.toDegrees(Math.atan2(q[3]-q[1],q[2]-q[0]));if(angle>90||angle<-90)angle+=180;
        c.save();c.translate((q[0]+q[2])/2,(q[1]+q[3])/2);c.rotate(angle);
        paint.setStyle(Paint.Style.FILL);paint.setColor(colour);paint.setTextSize(2.5f);float w=paint.measureText(label);c.drawText(label,-w/2,-.8f,paint);c.restore();
    }
    private float[] angleGeometry(double[] d) {
        int view=(int)d[0];if(!hasView(view))return null;
        float ax=px(view,d[1]),ay=py(view,d[2]),bx=px(view,d[3]),by=py(view,d[4]),cx=px(view,d[7]),cy=py(view,d[8]);
        double a1=Math.atan2(by-ay,bx-ax),a2=Math.atan2(cy-ay,cx-ax),sweep=Math.IEEEremainder(a2-a1,2*Math.PI);
        return new float[]{ax,ay,(float)a1,(float)sweep,(float)Math.max(3,d[6])};
    }
    private void angleDraw(Canvas c,double[] d,int colour) {
        float[] g=angleGeometry(d);if(g==null)return;float ax=g[0],ay=g[1],r=g[4];double a1=g[2],sweep=g[3];
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.15f);paint.setColor(colour);
        c.drawArc(new RectF(ax-r,ay-r,ax+r,ay+r),(float)Math.toDegrees(a1),(float)Math.toDegrees(sweep),false,paint);
        // Arms extended to the arc where they are shorter than its radius.
        for(double a:new double[]{a1,a1+sweep})c.drawLine(ax,ay,ax+(float)Math.cos(a)*(r+1.5f),ay+(float)Math.sin(a)*(r+1.5f),paint);
        double mid=a1+sweep/2;String label=label(d);paint.setTextSize(2.5f);float w=paint.measureText(label);
        float lx=ax+(float)Math.cos(mid)*(r+3)-w/2,ly=ay+(float)Math.sin(mid)*(r+3)+1;
        paint.setStyle(Paint.Style.FILL);paint.setColor(colour);c.drawText(label,lx,ly,paint);
    }
    private void ext(Canvas c,float x0,float y0,float x1,float y1,int colour) {
        float dx=x1-x0,dy=y1-y0,l=(float)Math.hypot(dx,dy);if(l<.01)return;float ux=dx/l,uy=dy/l;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.13f);paint.setColor(colour);c.drawLine(x0+ux*1f,y0+uy*1f,x1+ux*1.5f,y1+uy*1.5f,paint);
    }
    private void arrow(Canvas c,float x,float y,float towardX,float towardY,int colour) {
        float dx=towardX-x,dy=towardY-y,l=(float)Math.hypot(dx,dy);if(l<.01)return;float ux=dx/l,uy=dy/l;
        Path head=new Path();head.moveTo(x,y);head.lineTo(x+ux*2-uy*.6f,y+uy*2+ux*.6f);head.lineTo(x+ux*2+uy*.6f,y+uy*2-ux*.6f);head.close();
        paint.setStyle(Paint.Style.FILL);paint.setColor(colour);c.drawPath(head,paint);
    }
    private void hatch(Canvas c,int view) {
        double[] b=bounds[view];
        double spacing=2.5/scale;
        for(double level=b[0]+b[1];level<b[2]+b[3];level+=spacing) {
            ArrayList<Double> crossings=new ArrayList<>();
            for(int i=0;i+5<segments.length;i+=6) if((int)segments[i]==view) {
                double q0=segments[i+2]+segments[i+3],q1=segments[i+4]+segments[i+5];
                if((q0<=level&&level<q1)||(q1<=level&&level<q0)) {
                    double t=(level-q0)/(q1-q0);
                    crossings.add(segments[i+2]+t*(segments[i+4]-segments[i+2]));
                }
            }
            Collections.sort(crossings);
            for(int i=1;i<crossings.size();i+=2) {
                double x0=crossings.get(i-1),x1=crossings.get(i);
                line(c,px(view,x0),py(view,level-x0),px(view,x1),py(view,level-x1),.13f);
            }
        }
    }
}
