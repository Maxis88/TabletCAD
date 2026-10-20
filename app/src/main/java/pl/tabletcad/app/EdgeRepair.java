package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.CadProject;

/**
 * Keeps fillets and chamfers on "the same" edge after an earlier operation changed.
 *
 * <p>An edge operation stores the exact signature (start, middle, end) of its edge. When an
 * earlier sketch or operation is edited, the edge moves and the signature no longer matches. The
 * edge is then identified by what it is: the edge between two faces created by known operations
 * (face creators of the display mesh) with the same orientation. Before the change these faces
 * are read from the previous model; after the change the unique edge between faces with the same
 * creators and normals is taken (the nearest one when several remain). Nothing is guessed when
 * there is no such edge — the kernel then reports the lost edge as before.</p>
 */
public final class EdgeRepair {
    private EdgeRepair() {}

    /** Model of the first operations: display mesh (stride 13), face → creating operation, B-Rep edges (stride 16). */
    public static final class Model {
        public final double[] mesh,edges;public final Map<Integer,Integer> creators;public final List<HoleThread.Cylinder> holes;
        public Model(double[] mesh,Map<Integer,Integer> creators,double[] edges){this(mesh,creators,edges,Collections.emptyList());}
        public Model(double[] mesh,Map<Integer,Integer> creators,double[] edges,List<HoleThread.Cylinder> holes){this.mesh=mesh;this.creators=creators;this.edges=edges;this.holes=holes;}
    }
    public interface Evaluator {Model at(CadProject p,int operations);}

    /** Faces meeting at the edge: creator and outward normal of each (by the edge middle point). */
    static List<double[]> faces(Model m,double[] signature) {
        double[] mid={signature[3],signature[4],signature[5]};
        double scale=Math.max(1,Math.abs(mid[0])+Math.abs(mid[1])+Math.abs(mid[2]));
        Map<Integer,double[]> found=new LinkedHashMap<>();Map<Integer,Double> best=new HashMap<>();
        for(int i=0;i+12<m.mesh.length;i+=13) {
            int face=(int)Math.round(m.mesh[i+12])-1;
            double d=EdgeProjection.distance(Arrays.copyOfRange(m.mesh,i,i+9),mid);
            if(d>1e-4*scale)continue;
            if(d<best.getOrDefault(face,Double.POSITIVE_INFINITY)){best.put(face,d);found.put(face,new double[]{m.creators.getOrDefault(face,Integer.MIN_VALUE),m.mesh[i+9],m.mesh[i+10],m.mesh[i+11]});}
        }
        List<double[]> out=new ArrayList<>(found.values());
        out.sort((a,b)->a[0]!=b[0]?Double.compare(a[0],b[0]):Double.compare(a[1]*7+a[2]*3+a[3],b[1]*7+b[2]*3+b[3]));
        return out;
    }
    static boolean sameFaces(List<double[]> a,List<double[]> b) {
        if(a.size()!=2||b.size()!=2)return false;
        boolean straight=match(a.get(0),b.get(0))&&match(a.get(1),b.get(1)),crossed=match(a.get(0),b.get(1))&&match(a.get(1),b.get(0));
        return straight||crossed;
    }
    private static boolean match(double[] a,double[] b){return a[0]==b[0]&&a[1]*b[1]+a[2]*b[2]+a[3]*b[3]>0.995;}
    static boolean exists(double[] edges,double[] signature) {
        for(int i=0;i+15<edges.length;i+=16) {
            boolean forward=true,backward=true;
            for(int j=0;j<9;j++){forward&=Math.abs(edges[i+j]-signature[j])<=1e-5;backward&=Math.abs(edges[i+(2-j/3)*3+j%3]-signature[j])<=1e-5;}
            if(forward||backward)return true;
        }
        return false;
    }
    /** The new signature of the edge, or null when it cannot be identified. */
    static double[] relocate(Model before,Model after,double[] signature) {
        List<double[]> wanted=faces(before,signature);if(wanted.size()!=2)return null;
        double[] best=null;double distance=Double.POSITIVE_INFINITY;Set<Double> seen=new HashSet<>();
        for(int i=0;i+15<after.edges.length;i+=16) {
            if(!seen.add(after.edges[i+15]))continue;
            double[] candidate=Arrays.copyOfRange(after.edges,i,i+9);
            if(!sameFaces(wanted,faces(after,candidate)))continue;
            double d=Math.hypot(Math.hypot(candidate[3]-signature[3],candidate[4]-signature[4]),candidate[5]-signature[5]);
            if(d<distance){distance=d;best=candidate;}
        }
        return best;
    }
    /**
     * A thread cuts the cylinder it was made for: found in the previous model by its axis and
     * radius, then the cylinder created by the same operation is found in the new model and the
     * thread starts again at the same end of it.
     */
    static double[] followThread(CadProject before,CadProject after,int index,double[] f,Evaluator evaluator) {
        Model now=evaluator.at(after,index);if(now==null)return null;
        double[] origin={f[2],f[3],f[4]},direction={f[5],f[6],f[7]};double radius=f[8];
        for(HoleThread.Cylinder c:now.holes)if(Math.abs(c.radius-radius)<1e-6&&axisDistance(c,origin)<1e-5&&Math.abs(Math.abs(dot(c.axis,direction))-1)<1e-9)return null;// still in place
        Model then=evaluator.at(before,index);if(then==null)return null;
        HoleThread.Cylinder old=null;for(HoleThread.Cylinder c:then.holes)if(Math.abs(c.radius-radius)<1e-6&&axisDistance(c,origin)<1e-5&&Math.abs(Math.abs(dot(c.axis,direction))-1)<1e-9)old=c;
        if(old==null)return null;
        int creator=then.creators.getOrDefault(old.face,Integer.MIN_VALUE);
        boolean fromMax=dist(origin,old.end(true))<dist(origin,old.end(false));
        HoleThread.Cylinder best=null;double bestDistance=Double.POSITIVE_INFINITY;
        for(HoleThread.Cylinder c:now.holes) {
            if(now.creators.getOrDefault(c.face,Integer.MIN_VALUE)!=creator||Math.abs(c.radius-radius)>1e-6||Math.abs(Math.abs(dot(c.axis,old.axis))-1)>1e-6)continue;
            double d=dist(c.origin,old.origin);if(d<bestDistance){bestDistance=d;best=c;}
        }
        if(best==null)return null;
        boolean flip=dot(best.axis,old.axis)<0;boolean end=flip?!fromMax:fromMax;
        double[] start=best.end(end);double sign=dot(best.axis,direction)>=0?1:-1;
        double[] r=f.clone();r[2]=start[0];r[3]=start[1];r[4]=start[2];r[5]=best.axis[0]*sign;r[6]=best.axis[1]*sign;r[7]=best.axis[2]*sign;r[12]=best.max-best.min;
        if(r[11]>r[12]+1e-6)r[11]=r[12];
        try{HoleThread.validate(r);}catch(IllegalArgumentException e){return null;}
        return r;
    }
    /**
     * A hole that is not dimensioned from edges keeps its distance from the nearest corner of the
     * planar face it was placed on when that face moves or changes size (e.g. a moved boss).
     */
    static double[] followHole(CadProject before,CadProject after,int index,double[] f,Evaluator evaluator) {
        double[] centre={f[2],f[3],f[4]},normal={f[5],f[6],f[7]};int creator=(int)f[8];
        Model then=evaluator.at(before,index);if(then==null)return null;
        int face=-1;
        for(int i=0;i+12<then.mesh.length;i+=13){int id=(int)Math.round(then.mesh[i+12])-1;
            if(then.creators.getOrDefault(id,Integer.MIN_VALUE)!=creator||then.mesh[i+9]*normal[0]+then.mesh[i+10]*normal[1]+then.mesh[i+11]*normal[2]<0.999)continue;
            if(EdgeProjection.distance(Arrays.copyOfRange(then.mesh,i,i+9),centre)<1e-4){face=id;break;}}
        if(face<0)return null;
        double[][] basis=basis(normal);double[] oldBox=box(then,face,basis);
        Model now=evaluator.at(after,index);if(now==null)return null;
        Map<Integer,double[]> boxes=new HashMap<>();
        for(int i=0;i+12<now.mesh.length;i+=13){int id=(int)Math.round(now.mesh[i+12])-1;
            if(now.creators.getOrDefault(id,Integer.MIN_VALUE)!=creator||now.mesh[i+9]*normal[0]+now.mesh[i+10]*normal[1]+now.mesh[i+11]*normal[2]<0.999)continue;
            if(!boxes.containsKey(id))boxes.put(id,box(now,id,basis));}
        double[] best=null;double score=Double.POSITIVE_INFINITY;
        for(double[] b:boxes.values()){double d=Math.hypot((b[0]+b[2])/2-(oldBox[0]+oldBox[2])/2,(b[1]+b[3])/2-(oldBox[1]+oldBox[3])/2)+Math.abs((b[2]-b[0])-(oldBox[2]-oldBox[0]))+Math.abs((b[3]-b[1])-(oldBox[3]-oldBox[1]));if(d<score){score=d;best=b;}}
        if(best==null)return null;
        double u=dot(centre,basis[0]),v=dot(centre,basis[1]);
        // Keep the offset from the nearest corner of the face.
        boolean useMaxU=Math.abs(u-oldBox[2])<Math.abs(u-oldBox[0]),useMaxV=Math.abs(v-oldBox[3])<Math.abs(v-oldBox[1]);
        double nu=(useMaxU?best[2]:best[0])+(u-(useMaxU?oldBox[2]:oldBox[0])),nv=(useMaxV?best[3]:best[1])+(v-(useMaxV?oldBox[3]:oldBox[1]));
        if(Math.abs(nu-u)+Math.abs(nv-v)<1e-9)return null;
        double w=best[4];// plane offset along the normal
        double[] r=f.clone();
        for(int k=0;k<3;k++)r[2+k]=basis[0][k]*nu+basis[1][k]*nv+normal[k]*w;
        try{HoleFeature.validate(r);}catch(IllegalArgumentException e){return null;}
        return r;
    }
    static double[][] basis(double[] n) {
        double[] a=Math.abs(n[0])<.9?new double[]{1,0,0}:new double[]{0,1,0};
        double[] u={n[1]*a[2]-n[2]*a[1],n[2]*a[0]-n[0]*a[2],n[0]*a[1]-n[1]*a[0]};double l=Math.sqrt(dot(u,u));for(int i=0;i<3;i++)u[i]/=l;
        double[] v={n[1]*u[2]-n[2]*u[1],n[2]*u[0]-n[0]*u[2],n[0]*u[1]-n[1]*u[0]};
        return new double[][]{u,v};
    }
    /** In-plane bounding box of one face: minU, minV, maxU, maxV, offset along the normal. */
    static double[] box(Model m,int face,double[][] basis) {
        double[] b={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,0};
        double[] n={basis[0][1]*basis[1][2]-basis[0][2]*basis[1][1],basis[0][2]*basis[1][0]-basis[0][0]*basis[1][2],basis[0][0]*basis[1][1]-basis[0][1]*basis[1][0]};
        for(int i=0;i+12<m.mesh.length;i+=13){if((int)Math.round(m.mesh[i+12])-1!=face)continue;
            for(int k=0;k<9;k+=3){double[] p={m.mesh[i+k],m.mesh[i+k+1],m.mesh[i+k+2]};double u=dot(p,basis[0]),v=dot(p,basis[1]);
                b[0]=Math.min(b[0],u);b[1]=Math.min(b[1],v);b[2]=Math.max(b[2],u);b[3]=Math.max(b[3],v);b[4]=dot(p,n);}}
        return b;
    }
    static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static double dist(double[] a,double[] b){return Math.sqrt((a[0]-b[0])*(a[0]-b[0])+(a[1]-b[1])*(a[1]-b[1])+(a[2]-b[2])*(a[2]-b[2]));}
    static double axisDistance(HoleThread.Cylinder c,double[] p) {
        double[] d={p[0]-c.origin[0],p[1]-c.origin[1],p[2]-c.origin[2]};double t=dot(d,c.axis);
        return Math.sqrt(Math.max(0,dot(d,d)-t*t));
    }
    /** Operations after the first difference between the documents may need repair. */
    static int firstDifference(CadProject before,CadProject after) {
        if(!Arrays.equals(before.featureBase,after.featureBase)||before.width!=after.width||before.height!=after.height||before.depth!=after.depth)return 0;
        int n=Math.min(before.committedFeatures.size(),after.committedFeatures.size());
        for(int i=0;i<n;i++)if(!Arrays.equals(before.committedFeatures.get(i),after.committedFeatures.get(i)))return i;
        return n;
    }

    /** Repairs edge references of {@code after} in place; returns how many edges were re-attached. */
    public static int repair(CadProject before,CadProject after,Evaluator evaluator) {
        if(before==null||after==null||before.committedFeatures.size()!=after.committedFeatures.size())return 0;
        int first=firstDifference(before,after),repaired=0;
        for(int i=first;i<after.committedFeatures.size();i++) {
            double[] f=after.committedFeatures.get(i);
            if(!Arrays.equals(f,before.committedFeatures.get(i)))continue;
            if(HoleThread.is(f)){double[] moved=followThread(before,after,i,f,evaluator);if(moved!=null){after.committedFeatures.set(i,moved);repaired++;}continue;}
            if(HoleFeature.is(f)&&!after.holeReferences.containsKey(i)){double[] moved=followHole(before,after,i,f,evaluator);if(moved!=null){after.committedFeatures.set(i,moved);repaired++;}continue;}
            if(!(EdgeFillet.is(f)||EdgeChamfer.is(f)))continue;
            List<double[]> members=EdgeGroup.is(f)?EdgeGroup.members(f):Collections.singletonList(f);
            boolean simple=true;for(double[] m:members)if(m.length!=16)simple=false;
            if(!simple)continue;// root references are mapped by the kernel from the generator parameters
            Model now=null,then=null;List<double[]> updated=new ArrayList<>();boolean changed=false;
            for(double[] m:members) {
                double[] signature=Arrays.copyOfRange(m,2,11);
                if(now==null)now=evaluator.at(after,i);
                if(now==null)return repaired;
                if(exists(now.edges,signature)){updated.add(m);continue;}
                if(then==null)then=evaluator.at(before,i);
                if(then==null)return repaired;
                double[] moved=relocate(then,now,signature);
                if(moved==null){updated.add(m);continue;}
                double[] copy=m.clone();System.arraycopy(moved,0,copy,2,9);updated.add(copy);changed=true;repaired++;
            }
            if(!changed)continue;
            after.committedFeatures.set(i,EdgeGroup.is(f)?EdgeGroup.create(updated,f[11],EdgeGroup.chamfer(f)):updated.get(0));
        }
        return repaired;
    }
}
