package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.*;

/**
 * Hole centre dimensioned from face edges, kept associative through rebuilds.
 *
 * <p>A dimension is stored as the planar face that meets the hole face along the reference edge:
 * creating operation (face lineage tag) + outward normal, and the signed distance of the centre from
 * that plane. Before a rebuild the model up to the hole is evaluated, the face with the same creator
 * and normal is found again and the centre is moved inside the hole face so both distances hold.
 * A face that cannot be found unambiguously is reported and the centre is left unchanged.</p>
 *
 * <p>Value layout per hole: creator A, normal A xyz, distance A, creator B, normal B xyz, distance B
 * (B entries NaN when only one edge was available).</p>
 */
public final class HoleAssociation {
    private HoleAssociation() {}
    public static final int LENGTH=10;

    static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    /** Planar face (row of the face analysis) through both end points of the edge, not parallel to the hole face. */
    static double[] faceOfEdge(double[] faces,double[] edgeStart,double[] edgeEnd,double[] holeNormal) {
        double[] best=null;double bestPerp=-1;
        for(int i=0;i+GeometryMeasure.FACE_STRIDE<=faces.length;i+=GeometryMeasure.FACE_STRIDE) {
            if(faces[i+3]!=0)continue;double[] p={faces[i+4],faces[i+5],faces[i+6]},n={faces[i+7],faces[i+8],faces[i+9]};
            if(Math.abs(dot(n,holeNormal))>1-1e-9)continue;
            double s=dot(new double[]{edgeStart[0]-p[0],edgeStart[1]-p[1],edgeStart[2]-p[2]},n),e=dot(new double[]{edgeEnd[0]-p[0],edgeEnd[1]-p[1],edgeEnd[2]-p[2]},n);
            if(Math.abs(s)>1e-6||Math.abs(e)>1e-6)continue;
            double perp=1-Math.abs(dot(n,holeNormal));if(perp>bestPerp){bestPerp=perp;best=Arrays.copyOfRange(faces,i,i+GeometryMeasure.FACE_STRIDE);}
        }
        return best;
    }
    /** Reference values for a placement, or null when no adjacent face could be identified. */
    public static double[] references(HolePlacement placement,double[] centre,double[] faces) {
        if(placement==null)return null;
        double[] a=faceOfEdge(faces,placement.aOrigin,new double[]{placement.aOrigin[0]+placement.aDirection[0],placement.aOrigin[1]+placement.aDirection[1],placement.aOrigin[2]+placement.aDirection[2]},placement.normal);
        if(a==null||a[11]<-2)return null;
        double[] r=new double[LENGTH];Arrays.fill(r,Double.NaN);
        fill(r,0,a,centre);
        if(placement.hasB()){double[] b=faceOfEdge(faces,placement.bOrigin,new double[]{placement.bOrigin[0]+placement.bDirection[0],placement.bOrigin[1]+placement.bDirection[1],placement.bOrigin[2]+placement.bDirection[2]},placement.normal);if(b!=null&&b[11]>=-2)fill(r,5,b,centre);}
        return r;
    }
    static void fill(double[] r,int at,double[] face,double[] c) {
        double[] p={face[4],face[5],face[6]},n={face[7],face[8],face[9]};
        r[at]=face[11];r[at+1]=n[0];r[at+2]=n[1];r[at+3]=n[2];r[at+4]=dot(new double[]{c[0]-p[0],c[1]-p[1],c[2]-p[2]},n);
    }
    public static void validate(double[] r) {
        if(r==null||r.length!=LENGTH)throw new IllegalArgumentException("Niepoprawne odniesienie otworu");
        for(int at:new int[]{0,5}) {
            if(at==5&&Double.isNaN(r[5])){for(int k=5;k<LENGTH;k++)if(!Double.isNaN(r[k]))throw new IllegalArgumentException("Niepoprawne odniesienie otworu");continue;}
            for(int k=at;k<at+5;k++)if(!Double.isFinite(r[k]))throw new IllegalArgumentException("Niepoprawne odniesienie otworu");
            if(r[at]!=Math.floor(r[at])||r[at]<-2||Math.abs(r[at+1]*r[at+1]+r[at+2]*r[at+2]+r[at+3]*r[at+3]-1)>1e-6)throw new IllegalArgumentException("Niepoprawne odniesienie otworu");
        }
    }
    /** Result of an update: names of holes whose reference face was not found. */
    public static List<String> update(CadProject p,String sourceStep) {
        List<String> lost=new ArrayList<>();if(p.holeReferences.isEmpty())return lost;
        if(p.featureBase==null||p.bodyGraphStart<0)return lost;
        for(Map.Entry<Integer,double[]> e:new TreeMap<>(p.holeReferences).entrySet()) {
            int index=e.getKey();if(index>=p.committedFeatures.size()||!HoleFeature.is(p.committedFeatures.get(index))){p.holeReferences.remove(index);continue;}
            if(FeatureHistory.inactive(p,index))continue;
            double[] faces=facesBefore(p,index,sourceStep);if(faces.length==0){lost.add("otwór nr "+(index+1));continue;}
            double[] hole=p.committedFeatures.get(index).clone(),c0={hole[2],hole[3],hole[4]},r=e.getValue();
            double[][] planes=new double[2][];boolean ok=true;
            for(int k=0;k<2;k++){int at=k*5;if(Double.isNaN(r[at]))continue;planes[k]=find(faces,(int)r[at],new double[]{r[at+1],r[at+2],r[at+3]},c0,r[at+4]);if(planes[k]==null)ok=false;}
            if(!ok){lost.add("otwór nr "+(index+1));continue;}
            // Solve c = c0 + α nA + β nB with (c − pA)·nA = dA and (c − pB)·nB = dB.
            double[] nA={r[1],r[2],r[3]},pA=planes[0];double gA=r[4]-dot(new double[]{c0[0]-pA[0],c0[1]-pA[1],c0[2]-pA[2]},nA);
            double[] c=c0.clone();
            if(planes[1]==null){for(int i=0;i<3;i++)c[i]+=nA[i]*gA;}
            else {
                double[] nB={r[6],r[7],r[8]},pB=planes[1];double gB=r[9]-dot(new double[]{c0[0]-pB[0],c0[1]-pB[1],c0[2]-pB[2]},nB);
                double ab=dot(nA,nB),det=1-ab*ab;if(Math.abs(det)<1e-9){lost.add("otwór nr "+(index+1)+" (krawędzie równoległe)");continue;}
                double alpha=(gA-ab*gB)/det,beta=(gB-ab*gA)/det;for(int i=0;i<3;i++)c[i]+=alpha*nA[i]+beta*nB[i];
            }
            if(Math.abs(c[0]-c0[0])+Math.abs(c[1]-c0[1])+Math.abs(c[2]-c0[2])<1e-12)continue;
            hole[2]=c[0];hole[3]=c[1];hole[4]=c[2];HoleFeature.validate(hole);p.committedFeatures.set(index,hole);
        }
        return lost;
    }
    /** Plane point of the face with this creator and normal; with several candidates the one keeping the distance closest. */
    static double[] find(double[] faces,int creator,double[] normal,double[] centre,double distance) {
        double[] best=null;double bestError=Double.POSITIVE_INFINITY;
        for(int i=0;i+GeometryMeasure.FACE_STRIDE<=faces.length;i+=GeometryMeasure.FACE_STRIDE) {
            if(faces[i+3]!=0||(int)faces[i+11]!=creator)continue;double[] n={faces[i+7],faces[i+8],faces[i+9]};if(dot(n,normal)<1-1e-9)continue;
            double[] p={faces[i+4],faces[i+5],faces[i+6]};double error=Math.abs(dot(new double[]{centre[0]-p[0],centre[1]-p[1],centre[2]-p[2]},n)-distance);
            if(error<bestError){bestError=error;best=p;}
        }
        return best;
    }
    static double[] facesBefore(CadProject p,int index,String sourceStep) {
        try {
            CadProject view=CadProject.fromJson(new org.json.JSONObject(p.toJson().toString()));
            FeatureHistory.setMarker(view,index);// operations from the hole on are placeholders
            int n=0;for(double[] f:view.committedFeatures)n+=f.length;double[] raw=new double[n];int at=0;for(double[] f:view.committedFeatures){System.arraycopy(f,0,raw,at,f.length);at+=f.length;}
            return NativeCadEngine.faceAnalysis(view.width,view.height,view.depth,view.importedStep?0:view.filletRadius,view.featureBase,BodyGraph.pack(view,raw),sourceStep);
        }catch(org.json.JSONException|IllegalArgumentException error){return new double[0];}
    }
    /** Keys and creator tags after a removal at {@code removed}. */
    public static void removed(CadProject p,int removed) {
        Map<Integer,double[]> next=new TreeMap<>();
        for(Map.Entry<Integer,double[]> e:p.holeReferences.entrySet()){int k=e.getKey();if(k==removed)continue;double[] r=e.getValue().clone();boolean lostFace=false;
            for(int at:new int[]{0,5})if(!Double.isNaN(r[at])&&r[at]>=0){if(r[at]==removed)lostFace=true;else if(r[at]>removed)r[at]--;}
            if(!lostFace)next.put(k>removed?k-1:k,r);}
        p.holeReferences.clear();p.holeReferences.putAll(next);
    }
    public static void remap(CadProject p,java.util.function.IntUnaryOperator map) {
        Map<Integer,double[]> next=new TreeMap<>();
        for(Map.Entry<Integer,double[]> e:p.holeReferences.entrySet()){double[] r=e.getValue().clone();for(int at:new int[]{0,5})if(!Double.isNaN(r[at])&&r[at]>=0)r[at]=map.applyAsInt((int)r[at]);next.put(map.applyAsInt(e.getKey()),r);}
        p.holeReferences.clear();p.holeReferences.putAll(next);
    }
}
