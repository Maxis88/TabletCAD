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
        public final double[] mesh,edges;public final Map<Integer,Integer> creators;
        public Model(double[] mesh,Map<Integer,Integer> creators,double[] edges){this.mesh=mesh;this.creators=creators;this.edges=edges;}
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
            if(!(EdgeFillet.is(f)||EdgeChamfer.is(f))||!Arrays.equals(f,before.committedFeatures.get(i)))continue;
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
