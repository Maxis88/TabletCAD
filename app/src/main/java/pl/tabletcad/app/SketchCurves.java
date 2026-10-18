package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.SketchPrimitive;

/** Analytic line/three-point arc paths. Seven values per segment: kind,start,mid,end. */
public final class SketchCurves {
    private SketchCurves() {}
    public static final class Arc {
        public final double x,y,r,start,sweep;
        Arc(double[] s,int o) {
            double ax=s[o+1],ay=s[o+2],bx=s[o+3]-ax,by=s[o+4]-ay,cx=s[o+5]-ax,cy=s[o+6]-ay;
            double det=2*(bx*cy-by*cx);
            if(Math.abs(det)<1e-8)throw new IllegalArgumentException("Punkty łuku są współliniowe lub zbyt blisko");
            double bb=bx*bx+by*by,cc=cx*cx+cy*cy;
            x=ax+(cy*bb-by*cc)/det;y=ay+(bx*cc-cx*bb)/det;r=Math.hypot(ax-x,ay-y);
            if(!Double.isFinite(r)||r<.01||r>5000)throw new IllegalArgumentException("Promień łuku: 0,01–5000 mm");
            start=Math.atan2(ay-y,ax-x);double end=positive(Math.atan2(s[o+6]-y,s[o+5]-x)-start);
            double mid=positive(Math.atan2(s[o+4]-y,s[o+3]-x)-start);
            sweep=mid<end?end:end-Math.PI*2;
            if(Math.abs(sweep)<1e-6||Math.abs(sweep)>=Math.PI*2-1e-6)throw new IllegalArgumentException("Łuk wymaga różnych punktów");
            for(int i=0;i<4;i++){double angle=i*Math.PI/2,px=x+r*Math.cos(angle),py=y+r*Math.sin(angle);
                if(contains(px,py)&&(Math.abs(px)>5000+1e-6||Math.abs(py)>5000+1e-6))throw new IllegalArgumentException("Łuk wychodzi poza zakres ±5000 mm");}
        }
        public double[] point(double t){double a=start+sweep*t;return new double[]{x+r*Math.cos(a),y+r*Math.sin(a)};}
        public boolean contains(double px,double py) {
            if(Math.abs(Math.hypot(px-x,py-y)-r)>1e-5)return false;
            double a=Math.atan2(py-y,px-x);
            double delta=sweep>0?positive(a-start):positive(start-a);if(delta>Math.PI*2-1e-7)delta=0;
            return delta<=Math.abs(sweep)+1e-7;
        }
    }
    static double positive(double a){a%=Math.PI*2;return a<0?a+Math.PI*2:a;}
    public static Arc arc(double[] s,int o){return new Arc(s,o);}
    public static SketchPrimitive withRadius(SketchPrimitive source,int segment,double radius) {
        int o=segment*7;double[] s=source.curves.clone();Arc old=arc(s,o);
        double ax=s[o+1],ay=s[o+2],bx=s[o+5],by=s[o+6],chord=Math.hypot(bx-ax,by-ay);
        if(!Double.isFinite(radius)||radius<chord/2||radius>5000)throw new IllegalArgumentException("Promień musi być co najmniej połową cięciwy i nie większy niż 5000 mm");
        double nx=-(by-ay)/chord,ny=(bx-ax)/chord,mx=(ax+bx)/2,my=(ay+by)/2;
        double side=Math.copySign(1,(old.x-mx)*nx+(old.y-my)*ny),height=Math.sqrt(Math.max(0,radius*radius-chord*chord/4));
        double cx=mx+nx*side*height,cy=my+ny*side*height,start=Math.atan2(ay-cy,ax-cx),sweep=positive(Math.atan2(by-cy,bx-cx)-start);
        if(old.sweep<0)sweep-=Math.PI*2;
        s[o+3]=cx+radius*Math.cos(start+sweep/2);s[o+4]=cy+radius*Math.sin(start+sweep/2);
        return new SketchPrimitive(source.id,s,source.closedGeometry()).withConstruction(source.construction);
    }
    public static double[] arc(double ax,double ay,double mx,double my,double bx,double by){double[] s={1,ax,ay,mx,my,bx,by};validate(s,false);return s;}
    public static void writeDxf(java.io.Writer writer,double[] curves) throws java.io.IOException {
        for(int i=0;i<curves.length;i+=7) {
            if(curves[i]==0)writer.write("0\nLINE\n8\nSKETCH\n10\n"+curves[i+1]+"\n20\n"+curves[i+2]+"\n30\n0\n11\n"+curves[i+5]+"\n21\n"+curves[i+6]+"\n31\n0\n");
            else {Arc a=arc(curves,i);double start=a.sweep>0?a.start:a.start+a.sweep,end=a.sweep>0?a.start+a.sweep:a.start;
                writer.write("0\nARC\n8\nSKETCH\n10\n"+a.x+"\n20\n"+a.y+"\n30\n0\n40\n"+a.r+"\n50\n"+Math.toDegrees(positive(start))+"\n51\n"+Math.toDegrees(positive(end))+"\n");}
        }
    }
    public static double[] reverse(double[] s){double[] r=s.clone();for(int i=0;i<s.length;i+=7){int o=s.length-7-i;r[i]=s[o];r[i+1]=s[o+5];r[i+2]=s[o+6];r[i+3]=s[o+3];r[i+4]=s[o+4];r[i+5]=s[o+1];r[i+6]=s[o+2];}return r;}
    public static double[] samples(double[] segments,int steps) {
        if(steps<1)throw new IllegalArgumentException("Niepoprawna dokładność podglądu");
        List<Double> points=new ArrayList<>();
        for(int i=0;i<segments.length;i+=7) {
            Arc a=segments[i]==1?arc(segments,i):null;int count=a==null?1:steps;
            if(i==0){points.add(segments[i+1]);points.add(segments[i+2]);}
            for(int j=1;j<=count;j++){double[] p=a==null?new double[]{segments[i+5],segments[i+6]}:a.point((double)j/count);points.add(p[0]);points.add(p[1]);}
        }
        double[] result=new double[points.size()];for(int i=0;i<result.length;i++)result[i]=points.get(i);return result;
    }
    public static double[] bounds(double[] segments) {
        double[] result={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(int i=0;i<segments.length;i+=7) {
            expand(result,segments[i+1],segments[i+2]);expand(result,segments[i+5],segments[i+6]);
            if(segments[i]==1){Arc a=arc(segments,i);for(int j=0;j<4;j++){double angle=j*Math.PI/2,x=a.x+a.r*Math.cos(angle),y=a.y+a.r*Math.sin(angle);if(a.contains(x,y))expand(result,x,y);}}
        }
        return result;
    }
    private static void expand(double[] bounds,double x,double y){bounds[0]=Math.min(bounds[0],x);bounds[1]=Math.min(bounds[1],y);bounds[2]=Math.max(bounds[2],x);bounds[3]=Math.max(bounds[3],y);}
    public static void validate(double[] s,boolean closed) {
        if(s==null||s.length<7||s.length>128*7||s.length%7!=0||(!closed&&s.length!=7))throw new IllegalArgumentException("Ścieżka wymaga 1–128 odcinków");
        double area=0;
        for(int i=0;i<s.length;i+=7) {
            if(s[i]!=0&&s[i]!=1)throw new IllegalArgumentException("Nieznany typ segmentu");
            for(int j=1;j<7;j++)if(!Double.isFinite(s[i+j])||Math.abs(s[i+j])>5000)throw new IllegalArgumentException("Punkty łuku poza zakresem ±5000 mm");
            if(Math.hypot(s[i+1]-s[i+5],s[i+2]-s[i+6])<.001)throw new IllegalArgumentException("Zerowy segment");
            if(s[i]==1){Arc a=arc(s,i);area+=a.x*(s[i+6]-s[i+2])-a.y*(s[i+5]-s[i+1])+a.r*a.r*a.sweep;}
            else area+=s[i+1]*s[i+6]-s[i+5]*s[i+2];
            if(closed) {
                int next=(i+7)%s.length;
                if(Math.hypot(s[i+5]-s[next+1],s[i+6]-s[next+2])>1e-6)throw new IllegalArgumentException("Kontur krzywy nie jest zamknięty");
            }
        }
        if(!closed)return;
        if(s.length<14||Math.abs(area)<1e-5)throw new IllegalArgumentException("Kontur ma zerowe pole");
        for(int i=0;i<s.length;i+=7)for(int j=i+7;j<s.length;j+=7) {
            List<double[]> intersections=intersections(s,i,j);
            for(double[] p:intersections) {
                boolean shared=(j==i+7&&near(p[0],p[1],s[i+5],s[i+6]))||(i==0&&j==s.length-7&&near(p[0],p[1],s[1],s[2]));
                if(!shared)throw new IllegalArgumentException("Kontur krzywy przecina lub dotyka sam siebie");
            }
        }
    }
    private static boolean near(double x,double y,double u,double v){return Math.hypot(x-u,y-v)<1e-5;}
    public static List<double[]> lineHits(double[] line,double[] curves) {
        List<double[]> result=new ArrayList<>();
        for(int i=0;i<curves.length;i+=7) {
            double[] pair={0,line[0],line[1],0,0,line[2],line[3],curves[i],curves[i+1],curves[i+2],curves[i+3],curves[i+4],curves[i+5],curves[i+6]};
            result.addAll(intersections(pair,0,7));
        }
        return result;
    }
    public static List<double[]> segmentHits(double[] a,double[] b) {
        if(a.length!=7||b.length!=7)throw new IllegalArgumentException("Niepoprawny segment przecięcia");
        double[] pair=Arrays.copyOf(a,14);System.arraycopy(b,0,pair,7,7);return intersections(pair,0,7);
    }
    public static SketchPrimitive drag(SketchPrimitive p,int handle,double dx,double dy) {
        double[] s=p.curves.clone();boolean closed=p.closedGeometry();
        if(handle==0)for(int i=0;i<s.length;i+=7)for(int j=1;j<7;j+=2){s[i+j]+=dx;s[i+j+1]+=dy;}
        else if(!closed) {int index=handle==1?1:handle==2?5:3;s[index]+=dx;s[index+1]+=dy;}
        else if(handle>=1000){int i=(handle-1000)*7;s[i+3]+=dx;s[i+4]+=dy;}
        else {int i=(handle-1)*7,previous=(i-7+s.length)%s.length;s[i+1]+=dx;s[i+2]+=dy;s[previous+5]=s[i+1];s[previous+6]=s[i+2];}
        return new SketchPrimitive(p.id,s,closed).withConstruction(p.construction);
    }
    private static boolean onLine(double[] s,int i,double x,double y) {
        double dx=s[i+5]-s[i+1],dy=s[i+6]-s[i+2],t=((x-s[i+1])*dx+(y-s[i+2])*dy)/(dx*dx+dy*dy);
        return t>=-1e-8&&t<=1+1e-8&&Math.abs((x-s[i+1])*dy-(y-s[i+2])*dx)<1e-5;
    }
    private static List<double[]> intersections(double[] s,int i,int j) {
        List<double[]> out=new ArrayList<>();Arc a=s[i]==1?arc(s,i):null,b=s[j]==1?arc(s,j):null;
        if(a==null&&b==null) {
            double dx=s[i+5]-s[i+1],dy=s[i+6]-s[i+2],ex=s[j+5]-s[j+1],ey=s[j+6]-s[j+2],det=dx*ey-dy*ex;
            if(Math.abs(det)<1e-10) {for(int k:new int[]{i+1,i+5,j+1,j+5})if(onLine(s,i,s[k],s[k+1])&&onLine(s,j,s[k],s[k+1]))out.add(new double[]{s[k],s[k+1]});}
            else {double t=((s[j+1]-s[i+1])*ey-(s[j+2]-s[i+2])*ex)/det;double x=s[i+1]+t*dx,y=s[i+2]+t*dy;if(onLine(s,i,x,y)&&onLine(s,j,x,y))out.add(new double[]{x,y});}
        }else if(a==null||b==null) {
            Arc c=a==null?b:a;int line=a==null?i:j;
            double dx=s[line+5]-s[line+1],dy=s[line+6]-s[line+2],x=s[line+1]-c.x,y=s[line+2]-c.y;
            double aa=dx*dx+dy*dy,bb=2*(x*dx+y*dy),cc=x*x+y*y-c.r*c.r,disc=bb*bb-4*aa*cc;
            if(disc>=-1e-7)for(double sign:new double[]{-1,1}) {double t=(-bb+sign*Math.sqrt(Math.max(0,disc)))/(2*aa),px=s[line+1]+t*dx,py=s[line+2]+t*dy;if(t>=-1e-8&&t<=1+1e-8&&c.contains(px,py))out.add(new double[]{px,py});}
        }else {
            double dx=b.x-a.x,dy=b.y-a.y,d=Math.hypot(dx,dy);
            if(d<1e-7&&Math.abs(a.r-b.r)<1e-7) {
                for(int k:new int[]{i+1,i+5,j+1,j+5})if(a.contains(s[k],s[k+1])&&b.contains(s[k],s[k+1]))out.add(new double[]{s[k],s[k+1]});
                double[] ma=a.point(.5),mb=b.point(.5);
                if(b.contains(ma[0],ma[1])||a.contains(mb[0],mb[1]))throw new IllegalArgumentException("Nakładające się łuki");
            }else if(d>1e-7&&d<=a.r+b.r+1e-7&&d>=Math.abs(a.r-b.r)-1e-7) {
                double t=(a.r*a.r-b.r*b.r+d*d)/(2*d),h=Math.sqrt(Math.max(0,a.r*a.r-t*t));
                for(double sign:new double[]{-1,1}) {double x=a.x+t*dx/d-sign*h*dy/d,y=a.y+t*dy/d+sign*h*dx/d;if(a.contains(x,y)&&b.contains(x,y))out.add(new double[]{x,y});}
            }
        }
        return out;
    }
    public static SketchContours.Result join(List<SketchPrimitive> geometry,String selected) {
        List<SketchPrimitive> edges=new ArrayList<>();boolean curved=false;
        for(SketchPrimitive p:geometry)if(!p.construction&&(p.type.equals("Linia")||p.type.equals("Łuk"))){edges.add(p);curved|=p.curves!=null;}
        if(!curved)return null;
        SketchPrimitive first=edges.get(0);for(SketchPrimitive p:edges)if(p.id.equals(selected))first=p;
        List<double[]> chain=new ArrayList<>();Set<String> consumed=new HashSet<>();
        double[] current=segment(first);double sx=current[1],sy=current[2];
        int degreeAtStart=0;for(SketchPrimitive p:edges)if(Math.hypot(sx-p.x1,sy-p.y1)<=.01||Math.hypot(sx-p.x2,sy-p.y2)<=.01)degreeAtStart++;
        if(degreeAtStart!=2)throw new IllegalArgumentException("Początek konturu nie jest zamknięty lub ma rozgałęzienie");
        while(true) {
            if(chain.size()>=128)throw new IllegalArgumentException("Kontur przekracza 128 segmentów");
            chain.add(current);consumed.add(first.id);double x=current[5],y=current[6];
            if(Math.hypot(x-sx,y-sy)<=.01){current[5]=sx;current[6]=sy;break;}
            SketchPrimitive next=null;int degree=0;
            for(SketchPrimitive p:edges)if(Math.hypot(x-p.x1,y-p.y1)<=.01||Math.hypot(x-p.x2,y-p.y2)<=.01) {
                degree++;if(!consumed.contains(p.id)){if(next!=null)throw new IllegalArgumentException("Rozgałęziony kontur");next=p;}
            }
            if(next==null||degree!=2)throw new IllegalArgumentException("Połącz końce linii i łuków w zamknięty kontur");
            first=next;current=segment(next);if(Math.hypot(x-current[1],y-current[2])>.01)current=reverse(current);
            current[1]=x;current[2]=y;
        }
        double[] values=new double[chain.size()*7];for(int i=0;i<chain.size();i++)System.arraycopy(chain.get(i),0,values,i*7,7);
        for(SketchPrimitive p:edges)if(consumed.contains(p.id)&&p.driving!=null)throw new IllegalArgumentException("Odblokuj wymiary przed połączeniem");
        validate(values,true);return new SketchContours.Result(new SketchPrimitive(chainId(geometry,consumed,selected),values,true),consumed);
    }
    private static String chainId(List<SketchPrimitive> geometry,Set<String> ids,String selected){if(selected!=null&&ids.contains(selected))return selected;for(SketchPrimitive p:geometry)if(ids.contains(p.id))return p.id;throw new IllegalArgumentException("Pusty kontur");}
    private static double[] segment(SketchPrimitive p){return p.curves!=null?p.curves.clone():new double[]{0,p.x1,p.y1,(p.x1+p.x2)/2,(p.y1+p.y2)/2,p.x2,p.y2};}
}
