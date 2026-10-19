package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.*;

/**
 * Projection of exact B-Rep edges onto the active sketch plane ("Rzutuj krawędź").
 *
 * <p>Input are the kernel arrays {@link NativeCadEngine#modelEdges} (tessellated segments, stride 16:
 * signature start/mid/end xyz, segment endpoints xyz, edge index) and
 * {@link NativeCadEngine#edgeMeasurements} (stride 16: signature, exact length, radius, centre xyz,
 * kind 0 line / 1 circle / 2 other, edge index). Lines project to lines, circles whose plane is
 * parallel to the sketch to circles or three-point arcs. Edges that would become a point or an
 * ellipse/free curve are reported as skipped instead of being approximated. Projected lines and
 * circle centres are fixed so that the solver never moves the reference geometry.</p>
 */
public final class EdgeProjection {
    private EdgeProjection() {}
    public static final class Result {
        public final List<SketchPrimitive> created=new ArrayList<>();public final List<SketchConstraints.Link> links=new ArrayList<>();
        public int skipped;public String reason="";
    }
    static double[] frame(CadProject p){return new double[]{p.sketchOriginX,p.sketchOriginY,p.sketchOriginZ,p.sketchUx,p.sketchUy,p.sketchUz,p.sketchVx,p.sketchVy,p.sketchVz};}
    static double[] local(double[] f,double x,double y,double z) {
        double dx=x-f[0],dy=y-f[1],dz=z-f[2];
        return new double[]{dx*f[3]+dy*f[4]+dz*f[5],dx*f[6]+dy*f[7]+dz*f[8]};
    }
    static double height(double[] f,double x,double y,double z) {
        double nx=f[4]*f[8]-f[5]*f[7],ny=f[5]*f[6]-f[3]*f[8],nz=f[3]*f[7]-f[4]*f[6];
        return (x-f[0])*nx+(y-f[1])*ny+(z-f[2])*nz;
    }
    /** Indices of all edges whose tessellation lies in the sketch plane (within 1 µm). */
    public static List<Integer> inPlane(CadProject p,double[] edges) {
        double[] f=frame(p);Map<Integer,Boolean> on=new LinkedHashMap<>();
        for(int i=0;i+15<edges.length;i+=16) {
            int id=(int)edges[i+15];boolean flat=Math.abs(height(f,edges[i+9],edges[i+10],edges[i+11]))<1e-3&&Math.abs(height(f,edges[i+12],edges[i+13],edges[i+14]))<1e-3;
            on.put(id,on.getOrDefault(id,true)&&flat);
        }
        List<Integer> result=new ArrayList<>();for(Map.Entry<Integer,Boolean> e:on.entrySet())if(e.getValue())result.add(e.getKey());return result;
    }
    public static Result project(CadProject p,double[] edges,double[] properties,Collection<Integer> ids) {
        double[] f=frame(p);Result result=new Result();
        for(int id:ids) {
            double[] m;try{m=CadMeasurements.edge(properties,id);}catch(IllegalArgumentException e){m=null;}
            if(m==null){result.skipped++;result.reason="brak danych krawędzi";continue;}
            double[] a=local(f,m[0],m[1],m[2]),mid=local(f,m[3],m[4],m[5]),b=local(f,m[6],m[7],m[8]);
            String uid=UUID.randomUUID().toString();
            if(m[14]==0) {
                if(Math.hypot(b[0]-a[0],b[1]-a[1])<.01){result.skipped++;result.reason="krawędź prostopadła do płaszczyzny rzutuje się w punkt";continue;}
                SketchPrimitive line=new SketchPrimitive(uid,"Linia",a[0],a[1],b[0],b[1]);
                if(duplicate(p,result,line))continue;
                result.created.add(line);result.links.add(SketchConstraints.Link.fixed(line,0));result.links.add(SketchConstraints.Link.fixed(line,1));continue;
            }
            if(m[14]==1) {
                double[] c=local(f,m[11],m[12],m[13]);double r=m[10];boolean round=r>.005;
                for(int i=0;round&&i+15<edges.length;i+=16)if((int)edges[i+15]==id)for(int k=9;k<=12;k+=3){double[] q=local(f,edges[i+k],edges[i+k+1],edges[i+k+2]);if(Math.abs(Math.hypot(q[0]-c[0],q[1]-c[1])-r)>1e-3*Math.max(1,r))round=false;}
                if(!round){result.skipped++;result.reason="okrąg nierównoległy do szkicu dałby elipsę";continue;}
                boolean closed=Math.hypot(b[0]-a[0],b[1]-a[1])<1e-6&&Math.abs(m[9]-2*Math.PI*r)<1e-6*Math.max(1,r);
                SketchPrimitive created;
                if(closed){created=new SketchPrimitive(uid,"Okrąg",c[0],c[1],c[0]+r,c[1],new double[]{r});if(duplicate(p,result,created))continue;result.links.add(SketchConstraints.Link.fixed(created,0));}
                else {created=new SketchPrimitive(uid,SketchCurves.arc(a[0],a[1],mid[0],mid[1],b[0],b[1]),false);if(duplicate(p,result,created))continue;}
                result.created.add(created);continue;
            }
            result.skipped++;result.reason="krzywe swobodne nie są rzutowane (bez przybliżeń)";
        }
        return result;
    }
    /**
     * B-Rep edges bounding one face of the display mesh (stride 13, 1-based face id at 12):
     * every segment of the edge lies on a triangle of that face. Used by "Rzutuj ścianę".
     */
    public static List<Integer> faceEdges(double[] mesh,int face,double[] edges) {
        List<double[]> triangles=new ArrayList<>();
        for(int i=0;i+12<mesh.length;i+=13)if((int)Math.round(mesh[i+12])-1==face)triangles.add(Arrays.copyOfRange(mesh,i,i+9));
        List<Integer> result=new ArrayList<>();if(triangles.isEmpty())return result;
        double[] first=triangles.get(0);double[] n=normal(first);boolean planar=n!=null;
        if(planar)for(double[] t:triangles)for(int k=0;k<9&&planar;k+=3)if(Math.abs((t[k]-first[0])*n[0]+(t[k+1]-first[1])*n[1]+(t[k+2]-first[2])*n[2])>1e-3)planar=false;
        double tolerance=planar?1e-3:0.05;
        Map<Integer,Boolean> on=new LinkedHashMap<>();
        for(int i=0;i+15<edges.length;i+=16) {
            int id=(int)edges[i+15];if(Boolean.FALSE.equals(on.get(id)))continue;
            double[] a={edges[i+9],edges[i+10],edges[i+11]},b={edges[i+12],edges[i+13],edges[i+14]},m={(a[0]+b[0])/2,(a[1]+b[1])/2,(a[2]+b[2])/2};
            on.put(id,near(triangles,a,tolerance)&&near(triangles,b,tolerance)&&near(triangles,m,tolerance));
        }
        for(Map.Entry<Integer,Boolean> e:on.entrySet())if(e.getValue())result.add(e.getKey());
        return result;
    }
    private static double[] normal(double[] t) {
        double ux=t[3]-t[0],uy=t[4]-t[1],uz=t[5]-t[2],vx=t[6]-t[0],vy=t[7]-t[1],vz=t[8]-t[2];
        double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,l=Math.sqrt(nx*nx+ny*ny+nz*nz);
        return l<1e-12?null:new double[]{nx/l,ny/l,nz/l};
    }
    private static boolean near(List<double[]> triangles,double[] p,double tolerance) {
        for(double[] t:triangles)if(distance(t,p)<=tolerance)return true;return false;
    }
    /** Distance from a point to a triangle (Ericson, closest point on triangle). */
    static double distance(double[] t,double[] p) {
        double[] a={t[0],t[1],t[2]},b={t[3],t[4],t[5]},c={t[6],t[7],t[8]};
        double[] ab=sub(b,a),ac=sub(c,a),ap=sub(p,a);double d1=dot(ab,ap),d2=dot(ac,ap);
        if(d1<=0&&d2<=0)return len(sub(p,a));
        double[] bp=sub(p,b);double d3=dot(ab,bp),d4=dot(ac,bp);if(d3>=0&&d4<=d3)return len(bp);
        double vc=d1*d4-d3*d2;if(vc<=0&&d1>=0&&d3<=0){double v=d1/(d1-d3);return len(sub(p,add(a,mul(ab,v))));}
        double[] cp=sub(p,c);double d5=dot(ab,cp),d6=dot(ac,cp);if(d6>=0&&d5<=d6)return len(cp);
        double vb=d5*d2-d1*d6;if(vb<=0&&d2>=0&&d6<=0){double w=d2/(d2-d6);return len(sub(p,add(a,mul(ac,w))));}
        double va=d3*d6-d5*d4;if(va<=0&&(d4-d3)>=0&&(d5-d6)>=0){double w=(d4-d3)/((d4-d3)+(d5-d6));return len(sub(p,add(b,mul(sub(c,b),w))));}
        double denom=1/(va+vb+vc),v=vb*denom,w=vc*denom;return len(sub(p,add(a,add(mul(ab,v),mul(ac,w)))));
    }
    private static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static double[] add(double[] a,double[] b){return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    private static double[] mul(double[] a,double k){return new double[]{a[0]*k,a[1]*k,a[2]*k};}
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static double len(double[] a){return Math.sqrt(dot(a,a));}

    static boolean duplicate(CadProject p,Result r,SketchPrimitive candidate) {
        List<SketchPrimitive> existing=new ArrayList<>(p.sketch);existing.addAll(r.created);
        for(SketchPrimitive e:existing) {
            if(candidate.curves!=null||e.curves!=null){if(candidate.curves!=null&&e.curves!=null&&same(candidate.curves,e.curves))return true;continue;}
            if(!e.type.equals(candidate.type)||e.vertices!=null)continue;
            if(near(e.x1,candidate.x1)&&near(e.y1,candidate.y1)&&near(e.x2,candidate.x2)&&near(e.y2,candidate.y2))return true;
            if(e.type.equals("Linia")&&near(e.x1,candidate.x2)&&near(e.y1,candidate.y2)&&near(e.x2,candidate.x1)&&near(e.y2,candidate.y1))return true;
            if(e.type.equals("Okrąg")&&near(e.x1,candidate.x1)&&near(e.y1,candidate.y1)&&near(Math.hypot(e.x2-e.x1,e.y2-e.y1),Math.hypot(candidate.x2-candidate.x1,candidate.y2-candidate.y1)))return true;
        }
        return false;
    }
    static boolean same(double[] a,double[] b){if(a.length!=b.length)return false;for(int i=0;i<a.length;i++)if(!near(a[i],b[i]))return false;return true;}
    static boolean near(double a,double b){return Math.abs(a-b)<1e-6;}
}
