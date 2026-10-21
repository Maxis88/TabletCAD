package pl.tabletcad.app;

import pl.tabletcad.app.model.SketchPrimitive;

/**
 * Exact sketch offset. Positive distance grows closed profiles (outward), moves a line to its
 * left and enlarges arc radius; negative distance does the opposite. Lines stay lines and arcs
 * stay concentric arcs; sharp corners keep sharp (mitred) corners. Collapsing results are rejected.
 */
public final class SketchOffset {
    private SketchOffset() {}
    private static final double EPS=1e-7;

    public static SketchPrimitive offset(SketchPrimitive source,String id,double distance) {
        if(source==null||id==null||id.isEmpty()||id.equals(source.id))throw new IllegalArgumentException("Odsunięcie wymaga nowej tożsamości");
        if(!Double.isFinite(distance)||Math.abs(distance)<.001||Math.abs(distance)>5000)throw new IllegalArgumentException("Odległość odsunięcia: 0,001–5000 mm");
        SketchPrimitive result;
        if(source.isText())throw new IllegalArgumentException("Napis: zmień wysokość liter albo pogrubienie czcionki zamiast odsunięcia");
        if(source.isEllipse())result=ellipse(source,id,distance);
        else if(SketchSpline.is(source))result=spline(source,id,distance);
        else if(source.curves!=null)result=source.closedGeometry()?curved(source.curves,id,distance):arc(source.curves,id,distance);
        else if(source.vertices!=null)result=polygon(source.vertices,id,distance);
        else switch(source.type) {
            case "Okrąg": {
                double r=Math.hypot(source.x2-source.x1,source.y2-source.y1),next=r+distance;
                if(next<.01)throw new IllegalArgumentException("Odsunięcie do wewnątrz większe niż promień okręgu");
                double ux=(source.x2-source.x1)/r,uy=(source.y2-source.y1)/r;
                result=new SketchPrimitive(id,"Okrąg",source.x1,source.y1,source.x1+ux*next,source.y1+uy*next);break;
            }
            case "Prostokąt": {
                double x1=Math.min(source.x1,source.x2)-distance,x2=Math.max(source.x1,source.x2)+distance;
                double y1=Math.min(source.y1,source.y2)-distance,y2=Math.max(source.y1,source.y2)+distance;
                if(x2-x1<.01||y2-y1<.01)throw new IllegalArgumentException("Odsunięcie do wewnątrz większe niż połowa boku prostokąta");
                result=new SketchPrimitive(id,"Prostokąt",x1,y1,x2,y2);break;
            }
            case "Linia": case "Pomocnicza": {
                double length=Math.hypot(source.x2-source.x1,source.y2-source.y1);if(length<.01)throw new IllegalArgumentException("Zerowa linia");
                double nx=-(source.y2-source.y1)/length*distance,ny=(source.x2-source.x1)/length*distance;
                result=new SketchPrimitive(id,"Linia",source.x1+nx,source.y1+ny,source.x2+nx,source.y2+ny);break;
            }
            default: throw new IllegalArgumentException("Nieobsługiwany typ geometrii odsunięcia: "+source.type);
        }
        check(result);
        return result.withConstruction(source.construction);
    }

    /**
     * Offset of an ellipse (not an ellipse itself): an interpolating closed spline through 64 exact
     * offset points; deviation from the true offset curve far below 0.01 mm at drawing sizes.
     */
    private static SketchPrimitive ellipse(SketchPrimitive e,String id,double d) {
        double a=e.major(),b=e.minor,ux=(e.x2-e.x1)/a,uy=(e.y2-e.y1)/a;
        if(d<0&&-d>=b*b/a-1e-6)throw new IllegalArgumentException("Odsunięcie do wewnątrz większe niż najmniejszy promień krzywizny elipsy ("+String.format(java.util.Locale.US,"%.3f",b*b/a)+" mm)");
        int n=64;double[] pts=new double[2*n+2];
        for(int i=0;i<n;i++){double t=2*Math.PI*i/n,lx=a*Math.cos(t),ly=b*Math.sin(t),nx=b*Math.cos(t),ny=a*Math.sin(t),l=Math.hypot(nx,ny);lx+=nx/l*d;ly+=ny/l*d;
            pts[2*i]=e.x1+lx*ux-ly*uy;pts[2*i+1]=e.y1+lx*uy+ly*ux;}
        pts[2*n]=pts[0];pts[2*n+1]=pts[1];return new SketchPrimitive(id,pts,"Splajn");
    }
    /**
     * Offset of a spline: the curve is sampled densely, each sample moved along its normal
     * (closed: outward for a positive distance; open: to the left of the direction), and a new
     * interpolating spline goes through the moved points. An inward offset larger than the
     * smallest curvature radius would loop over itself and is rejected.
     */
    private static SketchPrimitive spline(SketchPrimitive sp,String id,double d) {
        double[] p=sp.vertices;boolean closed=SketchSpline.closed(p);int spans=p.length/2-1;
        double[] dense=SketchSpline.sample(p,Math.max(4,Math.min(64,4096/Math.max(1,spans))));int m=dense.length/2;
        if(closed){double area=0;for(int i=0;i<m-1;i++)area+=dense[2*i]*dense[2*i+3]-dense[2*i+2]*dense[2*i+1];if(area<0)d=-d;}
        // Outward normal of a counter-clockwise loop is the right side; for open curves the left side.
        double side=closed?-1:1;
        double[] moved=new double[dense.length];
        for(int i=0;i<m;i++){int a=closed?(i==0?m-2:i-1):Math.max(0,i-1),b=closed?(i==m-1?1:i+1):Math.min(m-1,i+1);
            double tx=dense[2*b]-dense[2*a],ty=dense[2*b+1]-dense[2*a+1],l=Math.hypot(tx,ty);if(l<1e-12)throw new IllegalArgumentException("Splajn ma zerowy odcinek");
            moved[2*i]=dense[2*i]-ty/l*d*side;moved[2*i+1]=dense[2*i+1]+tx/l*d*side;}
        for(int i=0;i<m-1;i++){double ox=dense[2*i+2]-dense[2*i],oy=dense[2*i+3]-dense[2*i+1],nx=moved[2*i+2]-moved[2*i],ny=moved[2*i+3]-moved[2*i+1];
            if(ox*nx+oy*ny<=0)throw new IllegalArgumentException("Odsunięcie większe niż promień krzywizny splajnu – krzywa zawinęłaby się");}
        int k=Math.max(closed?8:4,Math.min(120,spans*4));double[] out=new double[2*(k+1)];
        for(int j=0;j<=k;j++){int i=(int)Math.round((double)j*(m-1)/k);out[2*j]=moved[2*i];out[2*j+1]=moved[2*i+1];}
        if(closed){out[2*k]=out[0];out[2*k+1]=out[1];}
        return new SketchPrimitive(id,out,"Splajn");
    }

    private static void check(SketchPrimitive p) {
        double[] values={p.x1,p.y1,p.x2,p.y2};
        for(double v:values)if(!Double.isFinite(v)||Math.abs(v)>5000)throw new IllegalArgumentException("Odsunięcie wykracza poza zakres szkicu ±5000 mm");
    }

    private static SketchPrimitive arc(double[] s,String id,double d) {
        SketchCurves.Arc a=SketchCurves.arc(s,0);double next=a.r+d;
        if(next<.01)throw new IllegalArgumentException("Odsunięcie do wewnątrz większe niż promień łuku");
        double[] r=s.clone();double k=next/a.r;
        for(int j:new int[]{1,3,5}){r[j]=a.x+(s[j]-a.x)*k;r[j+1]=a.y+(s[j+1]-a.y)*k;}
        return new SketchPrimitive(id,r,false);
    }

    private static SketchPrimitive polygon(double[] v,String id,double d) {
        int n=v.length/2;double area=0;
        for(int i=0;i<n;i++){int j=(i+1)%n;area+=v[2*i]*v[2*j+1]-v[2*j]*v[2*i+1];}
        double side=Math.signum(area);if(side==0)throw new IllegalArgumentException("Kontur ma zerowe pole");
        double[] nx=new double[n],ny=new double[n];
        for(int i=0;i<n;i++){int j=(i+1)%n;double dx=v[2*j]-v[2*i],dy=v[2*j+1]-v[2*i+1],l=Math.hypot(dx,dy);nx[i]=side*dy/l;ny[i]=-side*dx/l;}
        double[] r=new double[v.length];
        for(int i=0;i<n;i++) {
            int p=(i+n-1)%n;
            double[] q=lineIntersection(v[2*p]+nx[p]*d,v[2*p+1]+ny[p]*d,v[2*i]-v[2*p],v[2*i+1]-v[2*p+1],
                v[2*i]+nx[i]*d,v[2*i+1]+ny[i]*d,v[2*((i+1)%n)]-v[2*i],v[2*((i+1)%n)+1]-v[2*i+1]);
            if(q==null){q=new double[]{v[2*i]+nx[i]*d,v[2*i+1]+ny[i]*d};}
            r[2*i]=q[0];r[2*i+1]=q[1];
        }
        for(int i=0;i<n;i++){int j=(i+1)%n;
            double ox=v[2*j]-v[2*i],oy=v[2*j+1]-v[2*i+1],mx=r[2*j]-r[2*i],my=r[2*j+1]-r[2*i+1];
            if(ox*mx+oy*my<=EPS)throw new IllegalArgumentException("Odsunięcie zbyt duże — kontur zapadłby się (bok "+(i+1)+")");}
        SketchContours.validate(r);
        return new SketchPrimitive(id,r);
    }

    /** Intersection of p+t*u and q+s*w, or null for (near) parallel lines. */
    static double[] lineIntersection(double px,double py,double ux,double uy,double qx,double qy,double wx,double wy) {
        double det=ux*wy-uy*wx,scale=Math.hypot(ux,uy)*Math.hypot(wx,wy);
        if(Math.abs(det)<=1e-9*scale)return null;
        double t=((qx-px)*wy-(qy-py)*wx)/det;return new double[]{px+ux*t,py+uy*t};
    }

    /** One offset segment: a line (point + direction) or a circle (centre + radius). */
    private static final class Piece {
        boolean arc;double ax,ay,bx,by;double cx,cy,r,sweep;double oax,oay,obx,oby;
    }

    private static SketchPrimitive curved(double[] s,String id,double d) {
        int n=s.length/7;
        double[] samples=SketchCurves.samples(s,24);double area=0;
        for(int i=0;i+3<samples.length;i+=2)area+=samples[i]*samples[i+3]-samples[i+2]*samples[i+1];
        double side=Math.signum(area);if(side==0)throw new IllegalArgumentException("Kontur ma zerowe pole");
        Piece[] pieces=new Piece[n];
        for(int k=0;k<n;k++) {
            int o=k*7;Piece p=new Piece();p.oax=s[o+1];p.oay=s[o+2];p.obx=s[o+5];p.oby=s[o+6];
            if(s[o]==1) {
                SketchCurves.Arc a=SketchCurves.arc(s,o);p.arc=true;p.cx=a.x;p.cy=a.y;p.sweep=a.sweep;
                p.r=a.r+d*side*Math.signum(a.sweep);
                if(p.r<.01)throw new IllegalArgumentException("Odsunięcie większe niż promień łuku "+(k+1));
                double k1=p.r/a.r;p.ax=a.x+(s[o+1]-a.x)*k1;p.ay=a.y+(s[o+2]-a.y)*k1;p.bx=a.x+(s[o+5]-a.x)*k1;p.by=a.y+(s[o+6]-a.y)*k1;
            }else {
                double dx=s[o+5]-s[o+1],dy=s[o+6]-s[o+2],l=Math.hypot(dx,dy);
                double nx=side*dy/l*d,ny=-side*dx/l*d;
                p.ax=s[o+1]+nx;p.ay=s[o+2]+ny;p.bx=s[o+5]+nx;p.by=s[o+6]+ny;
            }
            pieces[k]=p;
        }
        double[] jx=new double[n],jy=new double[n];
        for(int k=0;k<n;k++) {
            Piece a=pieces[(k+n-1)%n],b=pieces[k];
            if(Math.hypot(a.bx-b.ax,a.by-b.ay)<1e-6){jx[k]=(a.bx+b.ax)/2;jy[k]=(a.by+b.ay)/2;continue;}
            double[] q=join(a,b,(a.bx+b.ax)/2,(a.by+b.ay)/2);
            if(q==null)throw new IllegalArgumentException("Nie można odsunąć narożnika "+(k+1)+" (styk łuku z linią bez przecięcia)");
            jx[k]=q[0];jy[k]=q[1];
        }
        double[] r=new double[s.length];
        for(int k=0;k<n;k++) {
            int o=k*7,next=(k+1)%n;Piece p=pieces[k];
            double ax=jx[k],ay=jy[k],bx=jx[next],by=jy[next];
            r[o]=s[o];r[o+1]=ax;r[o+2]=ay;r[o+5]=bx;r[o+6]=by;
            if(p.arc) {
                double start=Math.atan2(ay-p.cy,ax-p.cx),end=Math.atan2(by-p.cy,bx-p.cx);
                double sweep=p.sweep>0?positive(end-start):-positive(start-end);
                if(Math.abs(sweep)<1e-6||Math.abs(sweep-p.sweep)>Math.PI)throw new IllegalArgumentException("Odsunięcie zbyt duże — łuk "+(k+1)+" zanikłby");
                r[o+3]=p.cx+p.r*Math.cos(start+sweep/2);r[o+4]=p.cy+p.r*Math.sin(start+sweep/2);
            }else {
                if((bx-ax)*(p.obx-p.oax)+(by-ay)*(p.oby-p.oay)<=EPS)throw new IllegalArgumentException("Odsunięcie zbyt duże — odcinek "+(k+1)+" zanikłby");
                r[o+3]=(ax+bx)/2;r[o+4]=(ay+by)/2;
            }
        }
        SketchPrimitive result=new SketchPrimitive(id,r,true);
        double[] poly=SketchCurves.samples(r,16);SketchContours.validate(java.util.Arrays.copyOf(poly,poly.length-2));
        return result;
    }
    private static double positive(double a){a%=Math.PI*2;return a<0?a+Math.PI*2:a;}

    private static double[] join(Piece a,Piece b,double hx,double hy) {
        if(!a.arc&&!b.arc)return lineIntersection(a.ax,a.ay,a.bx-a.ax,a.by-a.ay,b.ax,b.ay,b.bx-b.ax,b.by-b.ay);
        double[][] candidates;
        if(a.arc&&b.arc)candidates=circles(a.cx,a.cy,a.r,b.cx,b.cy,b.r);
        else {Piece line=a.arc?b:a,circle=a.arc?a:b;candidates=lineCircle(line.ax,line.ay,line.bx-line.ax,line.by-line.ay,circle.cx,circle.cy,circle.r);}
        double[] best=null;double bestDistance=Double.POSITIVE_INFINITY;
        for(double[] c:candidates){double dist=Math.hypot(c[0]-hx,c[1]-hy);if(dist<bestDistance){bestDistance=dist;best=c;}}
        return best;
    }
    private static double[][] lineCircle(double px,double py,double ux,double uy,double cx,double cy,double r) {
        double a=ux*ux+uy*uy,b=2*(ux*(px-cx)+uy*(py-cy)),c=(px-cx)*(px-cx)+(py-cy)*(py-cy)-r*r,disc=b*b-4*a*c;
        if(disc<-1e-12*Math.max(1,b*b))return new double[0][];
        double root=Math.sqrt(Math.max(0,disc));double t1=(-b-root)/(2*a),t2=(-b+root)/(2*a);
        return new double[][]{{px+ux*t1,py+uy*t1},{px+ux*t2,py+uy*t2}};
    }
    private static double[][] circles(double x1,double y1,double r1,double x2,double y2,double r2) {
        double d=Math.hypot(x2-x1,y2-y1);if(d<1e-12||d>r1+r2+1e-9||d<Math.abs(r1-r2)-1e-9)return new double[0][];
        double a=(r1*r1-r2*r2+d*d)/(2*d),h=Math.sqrt(Math.max(0,r1*r1-a*a));
        double mx=x1+a*(x2-x1)/d,my=y1+a*(y2-y1)/d;
        return new double[][]{{mx+h*(y2-y1)/d,my-h*(x2-x1)/d},{mx-h*(y2-y1)/d,my+h*(x2-x1)/d}};
    }
}
