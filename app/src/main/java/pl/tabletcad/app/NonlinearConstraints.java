package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.SketchPrimitive;
import pl.tabletcad.app.SketchConstraints.Link;

/** Bounded connected-component nonlinear sketch solver. Never publishes an unconverged result. */
public final class NonlinearConstraints {
    private NonlinearConstraints() {}
    public static boolean relation(String kind){return Arrays.asList("parallel","perpendicular","equal","concentric","tangent","pointOn","symmetric","collinear","rigid","angle","distance").contains(kind);}
    public static boolean present(List<Link> links){for(Link l:links)if(relation(l.kind))return true;return false;}
    public static String symbol(String kind){return kind.equals("parallel")?"∥":kind.equals("perpendicular")?"⊥":kind.equals("equal")?"=":kind.equals("concentric")?"◎":kind.equals("tangent")?"T":kind.equals("pointOn")?"P∈":kind.equals("collinear")?"⫽":kind.equals("rigid")?"⧉":kind.equals("symmetric")?"↔︎":kind.equals("angle")?"∠":kind.equals("distance")?"↦":kind;}
    static final class Block {
        final SketchPrimitive source;final int start,size;
        Block(SketchPrimitive p,int offset){source=p;start=offset;size=p.isPoint()?2:p.curves!=null?0:p.vertices!=null?p.vertices.length:4;}
        double[] data(double[] all){return Arrays.copyOfRange(all,start,start+size);}
        double[] point(double[] all,int index) {
            if(index<0||index>=SketchConstraints.anchorCount(source))throw new IllegalArgumentException("Niepoprawny punkt więzu");
            double[] p=data(all);if(source.type.equals("Prostokąt")){int[] i=new int[][]{{0,1},{2,1},{2,3},{0,3}}[index];return new double[]{p[i[0]],p[i[1]]};}
            return new double[]{p[index*2],p[index*2+1]};
        }
        int[] indices(int index){if(source.type.equals("Prostokąt"))return new int[][]{{0,1},{2,1},{2,3},{0,3}}[index];return new int[]{2*index,2*index+1};}
        boolean circle(){return source.type.equals("Okrąg");}
        boolean line(){return source.type.equals("Linia")||source.type.equals("Pomocnicza");}
    }
    private static Block block(Map<String,Block> blocks,String id){Block b=blocks.get(id);if(b==null)throw new IllegalArgumentException("Brak geometrii więzu");return b;}
    private static double length(double[] p){return Math.hypot(p[2]-p[0],p[3]-p[1]);}
    private static void line(Block b){if(!b.line())throw new IllegalArgumentException("Ten więz wymaga dwóch pojedynczych linii");}
    private static double[] axis(Link l,Map<String,Block> blocks,double[] all) {
        if(l.axis.equals("@X"))return new double[]{0,0,1,0};if(l.axis.equals("@Y"))return new double[]{0,0,0,1};
        Block b=block(blocks,l.axis);line(b);return b.data(all);
    }
    private static double[] reflected(double x,double y,double[] axis) {
        double dx=axis[2]-axis[0],dy=axis[3]-axis[1],length=dx*dx+dy*dy;if(length<1e-10)throw new IllegalArgumentException("Zerowa oś symetrii");
        double t=((x-axis[0])*dx+(y-axis[1])*dy)/length;return new double[]{2*(axis[0]+t*dx)-x,2*(axis[1]+t*dy)-y};
    }
    /** Rigid transform of a "rigid" relation: "@T:cx,cy,degrees,dx,dy" (rotate about c, then translate) or "@M:ax,ay,bx,by" (mirror). */
    static double[] transformOf(String axis) {
        if(axis==null||!(axis.startsWith("@T:")||axis.startsWith("@M:")))throw new IllegalArgumentException("Niepoprawne przekształcenie powiązanej kopii");
        String[] parts=axis.split(";",2)[0].substring(3).split(",");double[] v=new double[parts.length];
        try{for(int i=0;i<v.length;i++){v[i]=Double.parseDouble(parts[i]);if(!Double.isFinite(v[i]))throw new NumberFormatException();}}catch(NumberFormatException e){throw new IllegalArgumentException("Niepoprawne przekształcenie powiązanej kopii");}
        if(axis.split(";",2)[0].startsWith("@T:")&&v.length!=5||axis.startsWith("@M:")&&(v.length!=4||Math.hypot(v[2]-v[0],v[3]-v[1])<1e-9))throw new IllegalArgumentException("Niepoprawne przekształcenie powiązanej kopii");
        return v;
    }
    public static String translation(double dx,double dy){return "@T:0,0,0,"+dx+","+dy;}
    /** Pattern metadata appended to a transform: ";g=<group>;p=<kind,params>" (group shared by all copies of one pattern). */
    public static String withGroup(String transform,String group,String parameters){return transform+";g="+group+";p="+parameters;}
    public static String meta(String axis,String key) {
        if(axis==null)return null;for(String part:axis.split(";"))if(part.startsWith(key+"="))return part.substring(key.length()+1);return null;
    }
    public static String rotation(double cx,double cy,double degrees){return "@T:"+cx+","+cy+","+degrees+",0,0";}
    public static String mirror(double ax,double ay,double bx,double by){return "@M:"+ax+","+ay+","+bx+","+by;}
    static double[] mapped(String axis,double x,double y) {
        double[] t=transformOf(axis);
        if(axis.startsWith("@T:")){double a=Math.toRadians(t[2]),c=Math.cos(a),s=Math.sin(a);return new double[]{t[0]+(x-t[0])*c-(y-t[1])*s+t[3],t[1]+(x-t[0])*s+(y-t[1])*c+t[4]};}
        return reflected(x,y,t);
    }
    /** Data of the source block mapped by the relation, in the copy's storage convention. */
    static double[] mappedData(Block a,double[] p,String axis) {
        double[] r=new double[p.length];for(int i=0;i+1<p.length;i+=2){double[] q=mapped(axis,p[i],p[i+1]);r[i]=q[0];r[i+1]=q[1];}
        if(a.source.type.equals("Prostokąt"))return new double[]{Math.min(r[0],r[2]),Math.min(r[1],r[3]),Math.max(r[0],r[2]),Math.max(r[1],r[3])};
        return r;
    }
    private static void residual(Link l,Map<String,Block> blocks,double[] all,List<Double> out) {
        Block a=block(blocks,l.a),b=block(blocks,l.b);double[] p=a.data(all),q=b.data(all);
        if(!relation(l.kind)) {
            double[] pa=a.point(all,l.ai),pb=b.point(all,l.bi);
            if(l.kind.equals("fixed")){out.add(pa[0]-l.x);out.add(pa[1]-l.y);}
            else if(SketchConstraints.position(l.kind)){int k=l.kind.equals("dimX")?0:1;out.add((SketchConstraints.fromOrigin(l)?pa[k]:pb[k]-pa[k])-l.x);}
            else {if(l.kind.equals("coincident")||l.kind.equals("vertical"))out.add(pa[0]-pb[0]);if(l.kind.equals("coincident")||l.kind.equals("horizontal"))out.add(pa[1]-pb[1]);}return;
        }
        switch(l.kind) {
            case "parallel":case "perpendicular":
                line(a);line(b);double la=length(p),lb=length(q);if(la<.001||lb<.001)throw new IllegalArgumentException("Zerowa linia więzu");
                double ax=p[2]-p[0],ay=p[3]-p[1],bx=q[2]-q[0],by=q[3]-q[1];
                out.add(10*(l.kind.equals("parallel")?ax*by-ay*bx:ax*bx+ay*by)/(la*lb));break;
            case "distance": {
                // Driving distance from a point of a to b: to another point (l.y==1) or to the line b (signed, keeps the side).
                double[] point=a.point(all,l.ai);
                if(l.y==1){double[] other=b.point(all,l.bi);out.add(Math.hypot(point[0]-other[0],point[1]-other[1])-l.x);break;}
                line(b);double d=length(q);if(d<.001)throw new IllegalArgumentException("Zerowa linia");
                out.add(((point[0]-q[0])*(q[3]-q[1])-(point[1]-q[1])*(q[2]-q[0]))/d-l.x);break;
            }
            case "angle": {
                // Oriented angle from line a to line b equals l.x degrees (modulo 180°: a line has no direction).
                line(a);line(b);double la2=length(p),lb2=length(q);if(la2<.001||lb2<.001)throw new IllegalArgumentException("Zerowa linia więzu");
                double ux=p[2]-p[0],uy=p[3]-p[1],vx=q[2]-q[0],vy=q[3]-q[1],t=Math.toRadians(l.x);
                out.add(10*((ux*vy-uy*vx)*Math.cos(t)-(ux*vx+uy*vy)*Math.sin(t))/(la2*lb2));break;
            }
            case "equal":
                if(!((a.line()&&b.line())||(a.circle()&&b.circle())))throw new IllegalArgumentException("Równość wymaga dwóch linii lub dwóch okręgów");out.add(length(p)-length(q));break;
            case "concentric":
                if(!a.circle()||!b.circle())throw new IllegalArgumentException("Współśrodkowość wymaga okręgów");out.add(p[0]-q[0]);out.add(p[1]-q[1]);break;
            case "pointOn": {
                double[] point=a.point(all,l.ai);if(b.line()) {double d=length(q);if(d<.001)throw new IllegalArgumentException("Zerowa linia");out.add(((point[0]-q[0])*(q[3]-q[1])-(point[1]-q[1])*(q[2]-q[0]))/d);}
                else if(b.circle())out.add(Math.hypot(point[0]-q[0],point[1]-q[1])-length(q));else throw new IllegalArgumentException("Punkt na obiekcie: wybierz linię albo okrąg");break;
            }
            case "tangent": {
                if(a.circle()&&b.circle()){out.add(Math.hypot(p[0]-q[0],p[1]-q[1])-(l.x<0?Math.abs(length(p)-length(q)):length(p)+length(q)));break;}
                Block circle=a.circle()?a:b,edge=a.circle()?b:a;if(!circle.circle()||!edge.line())throw new IllegalArgumentException("Styczność: okrąg i linia lub dwa okręgi");
                double[] c=circle.data(all),e=edge.data(all);double len=length(e);if(len<.001)throw new IllegalArgumentException("Zerowa linia");
                double distance=((c[0]-e[0])*(e[3]-e[1])-(c[1]-e[1])*(e[2]-e[0]))/len;out.add(distance-(l.x<0?-1:1)*length(c));break;
            }
            case "rigid": {
                if(!a.source.type.equals(b.source.type)||a.size!=b.size||a.size==0)throw new IllegalArgumentException("Powiązana kopia musi mieć ten sam typ geometrii co źródło");
                double[] m=mappedData(a,p,l.axis);double[] c=b.source.type.equals("Prostokąt")?new double[]{Math.min(q[0],q[2]),Math.min(q[1],q[3]),Math.max(q[0],q[2]),Math.max(q[1],q[3])}:q;
                for(int i=0;i<m.length;i++)out.add(m[i]-c[i]);break;
            }
            case "collinear": {
                line(a);line(b);double d=length(p);if(d<.001||length(q)<.001)throw new IllegalArgumentException("Zerowa linia więzu");
                for(int i=0;i<4;i+=2)out.add(((q[i]-p[0])*(p[3]-p[1])-(q[i+1]-p[1])*(p[2]-p[0]))/d);break;
            }
            case "symmetric":
                if(a.circle()&&b.circle()){double[] mirrorAxis=axis(l,blocks,all),r=reflected(p[0],p[1],mirrorAxis);out.add(r[0]-q[0]);out.add(r[1]-q[1]);out.add(length(p)-length(q));break;}
                line(a);line(b);double[] axis=axis(l,blocks,all);
                for(int i=0;i<2;i++){double[] r=reflected(p[2*i],p[2*i+1],axis);int j=l.bi==1?1-i:i;out.add(r[0]-q[2*j]);out.add(r[1]-q[2*j+1]);}break;
            default:throw new IllegalArgumentException("Nieznany więz");
        }
    }
    private static double[] residuals(Map<String,Block> blocks,double[] all,List<Link> links) {
        List<Double> result=new ArrayList<>();for(Link l:links)if(blocks.containsKey(l.a))residual(l,blocks,all,result);
        for(Block b:blocks.values())if(b.source.driving!=null) {
            double[] p=b.data(all),d=b.source.driving;
            if(b.circle())result.add(length(p)-d[0]);
            else if(b.source.type.equals("Prostokąt")){result.add((p[2]-p[0])-Math.copySign(d[0],b.source.x2-b.source.x1));result.add((p[3]-p[1])-Math.copySign(d[1],b.source.y2-b.source.y1));}
            else {result.add(p[2]-p[0]-d[0]*Math.cos(Math.toRadians(d[1])));result.add(p[3]-p[1]-d[0]*Math.sin(Math.toRadians(d[1])));}
        }
        double[] r=new double[result.size()];for(int i=0;i<r.length;i++)r[i]=result.get(i);return r;
    }
    /** Degrees-of-freedom analysis of a whole sketch (numerical Jacobian rank of all stored constraints and driving dimensions). */
    public static final class Status {
        public final int freedoms,redundant;public final boolean contradictory;public final int unanalysed;
        Status(int freedoms,int redundant,boolean contradictory,int unanalysed){this.freedoms=freedoms;this.redundant=redundant;this.contradictory=contradictory;this.unanalysed=unanalysed;}
        public boolean full(){return !contradictory&&redundant==0&&freedoms==0&&unanalysed==0;}
        public String label() {
            if(contradictory)return "Szkic sprzeczny geometrycznie";
            if(redundant>0)return "Szkic nadmiernie związany ("+redundant+(redundant==1?" zbędny więz)":" zbędne więzy)");
            if(freedoms>0)return "Szkic niedowiązany: "+freedoms+" st. swobody"+(unanalysed>0?" (w tym kontury z łukami: "+unanalysed+" – ustalane wymiarami w edycji, bez więzów)":"");
            return "Szkic w pełni związany";
        }
    }
    public static Status status(List<SketchPrimitive> geometry,List<Link> links) {
        Map<String,Block> blocks=new LinkedHashMap<>();int size=0,circles=0,unanalysed=0;
        int curveFreedoms=0;
        for(SketchPrimitive p:geometry){Block b=new Block(p,size);if(b.size==0){if(p.curves!=null){unanalysed++;curveFreedoms+=curveFreedoms(p.curves);}continue;}blocks.put(p.id,b);size+=b.size;if(b.circle())circles++;}
        double[] all=coordinates(blocks,size);List<Link> used=new ArrayList<>();for(Link l:links)if(blocks.containsKey(l.a)&&blocks.containsKey(l.b)&&(l.axis.isEmpty()||l.axis.startsWith("@")||blocks.containsKey(l.axis)))used.add(l);
        double[] r;try{r=residuals(blocks,all,used);}catch(IllegalArgumentException e){return new Status(curveFreedoms,0,true,unanalysed);}
        boolean contradictory=maximum(r)>1e-6;int rows=r.length;
        double[][] jac=new double[rows][size];
        for(int j=0;j<size;j++){double old=all[j],h=1e-6*Math.max(1,Math.abs(old));all[j]=old+h;double[] plus=residuals(blocks,all,used);all[j]=old-h;double[] minus=residuals(blocks,all,used);all[j]=old;for(int i=0;i<rows;i++)jac[i][j]=(plus[i]-minus[i])/(2*h);}
        int rank=rank(jac,size);
        // A circle stores its radius as a second point: turning that point about the centre is not a real freedom.
        // Such gauge motions are subtracted only where the constraints leave them free: k − rank(J·G).
        List<double[]> gauges=new ArrayList<>();
        for(Block b:blocks.values())if(b.circle()){double[] g=new double[size];g[b.start+2]=-(all[b.start+3]-all[b.start+1]);g[b.start+3]=all[b.start+2]-all[b.start];gauges.add(g);}
        double[][] jg=new double[rows][gauges.size()];
        for(int i=0;i<rows;i++)for(int k=0;k<gauges.size();k++){double sum=0;double[] g=gauges.get(k);for(int j=0;j<size;j++)sum+=jac[i][j]*g[j];jg[i][k]=sum;}
        int gaugeFree=gauges.size()-(rows==0?0:rank(jg,gauges.size()));
        int freedoms=Math.max(0,size-gaugeFree-rank);
        return new Status(freedoms+curveFreedoms,rows-rank,contradictory,unanalysed);
    }
    /** Degrees of freedom of a line/arc contour (no stored constraints can reference it): 2 per distinct end point, 1 per arc (curvature). */
    static int curveFreedoms(double[] curves) {
        List<double[]> ends=new ArrayList<>();int arcs=0;
        for(int i=0;i+6<curves.length;i+=7){if(curves[i]==1)arcs++;for(int j:new int[]{1,5}){double x=curves[i+j],y=curves[i+j+1];boolean known=false;for(double[] e:ends)if(Math.hypot(e[0]-x,e[1]-y)<1e-7){known=true;break;}if(!known)ends.add(new double[]{x,y});}}
        return 2*ends.size()+arcs;
    }
    static int rank(double[][] m,int columns) {
        int rows=m.length,rank=0;double[][] a=new double[rows][];for(int i=0;i<rows;i++){a[i]=m[i].clone();double norm=0;for(double v:a[i])norm=Math.max(norm,Math.abs(v));if(norm>0)for(int j=0;j<columns;j++)a[i][j]/=norm;}
        boolean[] used=new boolean[rows];
        for(int c=0;c<columns&&rank<rows;c++) {
            int pivot=-1;double best=1e-7;for(int i=0;i<rows;i++)if(!used[i]&&Math.abs(a[i][c])>best){best=Math.abs(a[i][c]);pivot=i;}
            if(pivot<0)continue;used[pivot]=true;rank++;
            for(int i=0;i<rows;i++)if(i!=pivot&&a[i][c]!=0){double f=a[i][c]/a[pivot][c];for(int j=c;j<columns;j++)a[i][j]-=f*a[pivot][j];}
        }
        return rank;
    }
    /** Current distance of a distance relation: point–point, or signed point–line. */
    public static double distance(List<SketchPrimitive> geometry,Link l) {
        SketchPrimitive a=null,b=null;for(SketchPrimitive p:geometry){if(p.id.equals(l.a))a=p;if(p.id.equals(l.b))b=p;}
        if(a==null||b==null)throw new IllegalArgumentException("Brak geometrii wymiaru");
        double[] point=SketchConstraints.anchor(a,l.ai);
        if(l.y==1){double[] other=SketchConstraints.anchor(b,l.bi);return Math.hypot(point[0]-other[0],point[1]-other[1]);}
        double d=Math.hypot(b.x2-b.x1,b.y2-b.y1);return ((point[0]-b.x1)*(b.y2-b.y1)-(point[1]-b.y1)*(b.x2-b.x1))/d;
    }
    /** Current oriented angle from line a to line b in degrees (−180, 180]. */
    public static double angle(SketchPrimitive a,SketchPrimitive b) {
        double ux=a.x2-a.x1,uy=a.y2-a.y1,vx=b.x2-b.x1,vy=b.y2-b.y1;return Math.toDegrees(Math.atan2(ux*vy-uy*vx,ux*vx+uy*vy));
    }
    /** Stored value for a requested angle between lines (0–180°), keeping the side the lines are on now. */
    public static double angleValue(SketchPrimitive a,SketchPrimitive b,double degrees) {
        if(!Double.isFinite(degrees)||degrees<=0||degrees>=180)throw new IllegalArgumentException("Kąt musi być w zakresie 0–180°");
        return Math.copySign(degrees,angle(a,b)==0?1:angle(a,b));
    }
    public static void validate(List<SketchPrimitive> geometry,Link link) {
        Map<String,Block> blocks=new LinkedHashMap<>();int size=0;for(SketchPrimitive p:geometry){Block b=new Block(p,size);blocks.put(p.id,b);size+=b.size;}
        double[] all=coordinates(blocks,size);List<Double> residual=new ArrayList<>();residual(link,blocks,all,residual);
        for(double v:residual)if(!Double.isFinite(v)||Math.abs(v)>1e-6)throw new IllegalArgumentException("Geometria nie spełnia więzu "+symbol(link.kind));
    }
    private static boolean centred(Block b){return b.size==4&&(b.circle()||b.source.isEllipse());}
    static double[] relative(Map<String,Block> blocks,double[] all){double[] r=all.clone();for(Block b:blocks.values())if(centred(b)){r[b.start+2]-=r[b.start];r[b.start+3]-=r[b.start+1];}return r;}
    static double[] absolute(Map<String,Block> blocks,double[] rel){double[] r=rel.clone();for(Block b:blocks.values())if(centred(b)){r[b.start+2]+=r[b.start];r[b.start+3]+=r[b.start+1];}return r;}
    private static double[] coordinates(Map<String,Block> blocks,int size) {
        double[] all=new double[size];for(Block b:blocks.values())if(b.size>0){double[] p=b.source.vertices!=null?b.source.vertices:new double[]{b.source.x1,b.source.y1,b.source.x2,b.source.y2};System.arraycopy(p,0,all,b.start,b.size);}return all;
    }
    private static double error(double[] r){double value=0;for(double v:r)value+=v*v;return value;}
    private static double maximum(double[] r){double value=0;for(double v:r)value=Math.max(value,Math.abs(v));return value;}
    private static void put(Block block,double[] all,double[] values){System.arraycopy(values,0,all,block.start,values.length);}
    private static void seed(Link l,Map<String,Block> blocks,double[] all) {
        Block a=block(blocks,l.a),b=block(blocks,l.b);double[] p=a.data(all),q=b.data(all);
        switch(l.kind) {
            case "parallel":case "perpendicular": {
                line(a);line(b);double angle=Math.atan2(p[3]-p[1],p[2]-p[0])+(l.kind.equals("perpendicular")?Math.PI/2:0),current=Math.atan2(q[3]-q[1],q[2]-q[0]);
                if(Math.abs(Math.IEEEremainder(angle-current,Math.PI*2))>Math.PI/2)angle+=Math.PI;
                double length=length(q);q[2]=q[0]+length*Math.cos(angle);q[3]=q[1]+length*Math.sin(angle);put(b,all,q);break;
            }
            case "angle": {
                line(a);line(b);double target=Math.atan2(p[3]-p[1],p[2]-p[0])+Math.toRadians(l.x),current=Math.atan2(q[3]-q[1],q[2]-q[0]);
                if(Math.abs(Math.IEEEremainder(target-current,Math.PI*2))>Math.PI/2)target+=Math.PI;
                double length=length(q);q[2]=q[0]+length*Math.cos(target);q[3]=q[1]+length*Math.sin(target);put(b,all,q);break;
            }
            case "equal":if((a.line()&&b.line())||(a.circle()&&b.circle())) {double factor=length(p)/length(q);q[2]=q[0]+(q[2]-q[0])*factor;q[3]=q[1]+(q[3]-q[1])*factor;put(b,all,q);}break;
            case "rigid":{double[] m=mappedData(a,p,l.axis);if(m.length==q.length){System.arraycopy(m,0,q,0,m.length);put(b,all,q);}break;}
            case "collinear": {
                line(a);line(b);double dx=p[2]-p[0],dy=p[3]-p[1],dd=dx*dx+dy*dy;
                for(int i=0;i<4;i+=2){double t=((q[i]-p[0])*dx+(q[i+1]-p[1])*dy)/dd;q[i]=p[0]+t*dx;q[i+1]=p[1]+t*dy;}
                if(length(q)<.01)throw new IllegalArgumentException("Współliniowość zredukowałaby linię do punktu");put(b,all,q);break;
            }
            case "concentric":if(a.circle()&&b.circle()){double dx=p[0]-q[0],dy=p[1]-q[1];for(int i=0;i<4;i+=2){q[i]+=dx;q[i+1]+=dy;}put(b,all,q);}break;
            case "symmetric": {
                if(a.circle()&&b.circle()){double[] mirrorAxis=axis(l,blocks,all),r=reflected(p[0],p[1],mirrorAxis);double radius=length(p);q[0]=r[0];q[1]=r[1];q[2]=r[0]+radius;q[3]=r[1];put(b,all,q);break;}
                line(a);line(b);double[] axis=axis(l,blocks,all);for(int i=0;i<2;i++){double[] r=reflected(p[2*i],p[2*i+1],axis);int j=l.bi==1?1-i:i;q[2*j]=r[0];q[2*j+1]=r[1];}put(b,all,q);break;
            }
            case "pointOn": {
                double[] point=a.point(all,l.ai);double x=point[0],y=point[1];
                if(b.line()){double dx=q[2]-q[0],dy=q[3]-q[1],t=((x-q[0])*dx+(y-q[1])*dy)/(dx*dx+dy*dy);x=q[0]+t*dx;y=q[1]+t*dy;}
                else if(b.circle()){double angle=Math.atan2(y-q[1],x-q[0]);x=q[0]+length(q)*Math.cos(angle);y=q[1]+length(q)*Math.sin(angle);}
                int[] index=a.indices(l.ai);all[a.start+index[0]]=x;all[a.start+index[1]]=y;break;
            }
            case "tangent": {
                if(a.circle()&&b.circle()) {
                    double distance=l.x<0?Math.abs(length(p)-length(q)):length(p)+length(q),angle=Math.atan2(q[1]-p[1],q[0]-p[0]),dx=p[0]+distance*Math.cos(angle)-q[0],dy=p[1]+distance*Math.sin(angle)-q[1];
                    q[0]+=dx;q[1]+=dy;q[2]+=dx;q[3]+=dy;put(b,all,q);break;
                }
                Block circle=a.circle()?a:b,edge=a.circle()?b:a;if(!circle.circle()||!edge.line())break;
                double[] c=circle.data(all),e=edge.data(all);double len=length(e),nx=(e[3]-e[1])/len,ny=-(e[2]-e[0])/len;
                double d=(c[0]-e[0])*nx+(c[1]-e[1])*ny,target=(l.x<0?-1:1)*length(c),shift=target-d;
                if(circle==b){for(int i=0;i<4;i+=2){c[i]+=nx*shift;c[i+1]+=ny*shift;}put(circle,all,c);}
                else {for(int i=0;i<4;i+=2){e[i]-=nx*shift;e[i+1]-=ny*shift;}put(edge,all,e);}break;
            }
            default:break;
        }
    }
    private static double[] linear(double[][] a,double[] b) {
        int n=b.length;for(int k=0;k<n;k++) {
            int pivot=k;for(int i=k+1;i<n;i++)if(Math.abs(a[i][k])>Math.abs(a[pivot][k]))pivot=i;
            if(Math.abs(a[pivot][k])<1e-18)throw new IllegalArgumentException("Osobliwy układ więzów");double[] row=a[k];a[k]=a[pivot];a[pivot]=row;double t=b[k];b[k]=b[pivot];b[pivot]=t;
            for(int i=k+1;i<n;i++){double f=a[i][k]/a[k][k];for(int j=k+1;j<n;j++)a[i][j]-=f*a[k][j];b[i]-=f*b[k];}
        }
        double[] x=new double[n];for(int i=n-1;i>=0;i--){double value=b[i];for(int j=i+1;j<n;j++)value-=a[i][j]*x[j];x[i]=value/a[i][i];}return x;
    }
    public static List<SketchPrimitive> solve(List<SketchPrimitive> geometry,List<Link> links,String edited,SketchPrimitive candidate,Link added,int draggedAnchor) {
        Set<String> component=new HashSet<>();if(edited!=null)component.add(edited);if(added!=null){component.add(added.a);component.add(added.b);if(!added.axis.isEmpty()&&!added.axis.startsWith("@"))component.add(added.axis);}
        if(component.isEmpty())for(Link l:links){component.add(l.a);component.add(l.b);}
        boolean changed;do {changed=false;for(Link l:links)if(component.contains(l.a)||component.contains(l.b)||component.contains(l.axis)){changed|=component.add(l.a);changed|=component.add(l.b);if(!l.axis.isEmpty()&&!l.axis.startsWith("@"))changed|=component.add(l.axis);}}while(changed);
        Map<String,Block> blocks=new LinkedHashMap<>();int size=0;
        for(SketchPrimitive p:geometry)if(component.contains(p.id)){SketchPrimitive source=p.id.equals(edited)?candidate:p;Block block=new Block(source,size);if(blocks.put(p.id,block)!=null)throw new IllegalArgumentException("Powtórzony identyfikator");size+=block.size;}
        if(size>128)throw new IllegalArgumentException("Połączona grupa przekracza limit 128 współrzędnych solvera");
        double[] all=coordinates(blocks,size);boolean[] pinned=new boolean[size];
        if(added!=null&&relation(added.kind))seed(added,blocks,all);
        for(Link l:links)if(l.kind.equals("fixed")&&blocks.containsKey(l.a)){Block b=block(blocks,l.a);int[] i=b.indices(l.ai);all[b.start+i[0]]=l.x;all[b.start+i[1]]=l.y;pinned[b.start+i[0]]=pinned[b.start+i[1]]=true;}
        if(edited!=null){Block b=block(blocks,edited);if(draggedAnchor<0){for(int i=0;i<b.size;i++)pinned[b.start+i]=true;}else {for(int i:b.indices(draggedAnchor))pinned[b.start+i]=true;}}
        if(added!=null){String held=added.kind.equals("pointOn")||added.kind.equals("distance")?added.b:added.a;Block b=block(blocks,held);for(int i=0;i<b.size;i++)pinned[b.start+i]=true;}
        if(added!=null&&!added.axis.isEmpty()&&!added.axis.startsWith("@")){Block b=block(blocks,added.axis);for(int i=0;i<b.size;i++)pinned[b.start+i]=true;}
        if(edited!=null)for(Link fixed:links)if(fixed.kind.equals("fixed")&&fixed.a.equals(edited)&&(draggedAnchor<0||draggedAnchor==fixed.ai)) {
            Block editedBlock=block(blocks,edited);double[] point=editedBlock.point(coordinates(blocks,size),fixed.ai);
            if(Math.abs(point[0]-fixed.x)>1e-7||Math.abs(point[1]-fixed.y)>1e-7)throw new IllegalArgumentException("Przesunięcie narusza unieruchomiony punkt");
        }
        if(added==null)for(Link relation:links)if((relation.kind.equals("parallel")||relation.kind.equals("perpendicular")||relation.kind.equals("angle"))&&blocks.containsKey(relation.a)) {
            List<Double> r0=new ArrayList<>();residual(relation,blocks,all,r0);
            if(Math.abs(r0.get(0))>9.999999) {
                Link oriented=relation;Block follower=block(blocks,relation.b);boolean movable=false;
                for(int j=0;j<follower.size;j++)movable|=!pinned[follower.start+j];
                if(!movable)oriented=new Link(relation.kind,relation.b,relation.bi,relation.a,relation.ai,relation.x,relation.y,relation.axis);
                double[] before=all.clone();seed(oriented,blocks,all);for(int j=0;j<all.length;j++)if(pinned[j])all[j]=before[j];
            }
        }
        List<Integer> free=new ArrayList<>();for(int i=0;i<size;i++)if(!pinned[i])free.add(i);
        // Circles and ellipses are solved with their second point relative to the centre, so a moved
        // centre carries the radius/axis point along instead of silently changing the size.
        all=relative(blocks,all);
        double damping=1e-4;double[] r=residuals(blocks,absolute(blocks,all),links);
        for(int iteration=0;iteration<80&&maximum(r)>1e-8;iteration++) {
            int n=free.size();if(n==0)break;double[][] jac=new double[n][r.length];
            for(int j=0;j<n;j++){int variable=free.get(j);double old=all[variable],h=1e-5*Math.max(1,Math.abs(old)*.001);all[variable]=old+h;double[] plus=residuals(blocks,absolute(blocks,all),links);all[variable]=old-h;double[] minus=residuals(blocks,absolute(blocks,all),links);all[variable]=old;for(int i=0;i<r.length;i++)jac[j][i]=(plus[i]-minus[i])/(2*h);}
            double[][] matrix=new double[n][n];double[] rhs=new double[n];for(int j=0;j<n;j++){for(int i=0;i<r.length;i++)rhs[j]-=jac[j][i]*r[i];for(int k=0;k<=j;k++){double sum=0;for(int i=0;i<r.length;i++)sum+=jac[j][i]*jac[k][i];matrix[j][k]=matrix[k][j]=sum;}matrix[j][j]+=damping;}
            double[] delta=linear(matrix,rhs),proposal=all.clone();for(int i=0;i<n;i++)proposal[free.get(i)]+=delta[i];
            double[] proposed;try{proposed=residuals(blocks,absolute(blocks,proposal),links);}catch(IllegalArgumentException e){damping*=10;continue;}
            if(error(proposed)<error(r)){all=proposal;r=proposed;damping=Math.max(1e-12,damping*.25);}else damping*=10;
            if(damping>1e12)break;
        }
        all=absolute(blocks,all);
        if(maximum(r)>5e-7||!Double.isFinite(error(r)))throw new IllegalArgumentException("Sprzeczne więzy lub brak zbieżności — szkic zachowano");
        List<SketchPrimitive> result=new ArrayList<>();
        for(SketchPrimitive original:geometry) {
            Block b=blocks.get(original.id);if(b==null||b.size==0){result.add(original.id.equals(edited)?candidate:original);continue;}
            double[] p=b.data(all);for(double v:p)if(!Double.isFinite(v)||Math.abs(v)>5000)throw new IllegalArgumentException("Więzy wyprowadzają geometrię poza ±5000 mm");
            SketchPrimitive rebuilt;
            if(b.source.vertices!=null){if(SketchSpline.is(b.source))rebuilt=new SketchPrimitive(original.id,p,"Splajn");else {SketchContours.validate(p);rebuilt=new SketchPrimitive(original.id,p);}}
            else if(b.source.isPoint())rebuilt=SketchPrimitive.point(original.id,p[0],p[1]);
            else {if(length(p)<.01)throw new IllegalArgumentException("Więz tworzy zerową geometrię");rebuilt=b.source.withPoints(p[0],p[1],p[2],p[3],b.source.driving);}
            result.add(rebuilt.withConstruction(b.source.construction));
        }
        SketchConstraints.validate(result,links);return result;
    }
}
