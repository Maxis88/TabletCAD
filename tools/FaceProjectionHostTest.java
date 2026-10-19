import java.util.*;
import pl.tabletcad.app.EdgeProjection;
import pl.tabletcad.app.SketchPosition;
import pl.tabletcad.app.model.SketchPrimitive;

/** 0.87 face projection: the edges bounding a tapped face are found from the display mesh, alignment to the projected outline. */
public final class FaceProjectionHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static void tri(List<Double> mesh,double[] a,double[] b,double[] c,int face) {
        for(double[] p:new double[][]{a,b,c})for(double v:p)mesh.add(v);mesh.add(0.0);mesh.add(0.0);mesh.add(1.0);mesh.add((double)face);
    }
    static void edge(List<Double> edges,int id,double[] a,double[] b) {
        int n=4;for(int i=0;i<n;i++){double t0=i/(double)n,t1=(i+1)/(double)n;
            for(int k=0;k<9;k++)edges.add(0.0);
            for(int k=0;k<3;k++)edges.add(a[k]+(b[k]-a[k])*t0);for(int k=0;k<3;k++)edges.add(a[k]+(b[k]-a[k])*t1);edges.add((double)id);}
    }
    static double[] arr(List<Double> l){double[] r=new double[l.size()];for(int i=0;i<r.length;i++)r[i]=l.get(i);return r;}
    public static void main(String[] args) {
        // Box 40 x 30 x 10: top face (id 1) at z=10, front face (id 2) at y=0.
        double[] p000={0,0,0},p400={40,0,0},p430={40,30,0},p030={0,30,0},p001={0,0,10},p401={40,0,10},p431={40,30,10},p031={0,30,10};
        List<Double> mesh=new ArrayList<>();
        tri(mesh,p001,p401,p431,1);tri(mesh,p001,p431,p031,1);
        tri(mesh,p000,p400,p401,2);tri(mesh,p000,p401,p001,2);
        List<Double> edges=new ArrayList<>();
        edge(edges,1,p001,p401);edge(edges,2,p401,p431);edge(edges,3,p431,p031);edge(edges,4,p031,p001);
        edge(edges,5,p000,p400);edge(edges,6,p400,p401);edge(edges,7,p000,p001);
        List<Integer> top=EdgeProjection.faceEdges(arr(mesh),0,arr(edges));
        check(new HashSet<>(top).equals(new HashSet<>(Arrays.asList(1,2,3,4))),"top face: its four boundary edges ("+top+")");
        List<Integer> front=EdgeProjection.faceEdges(arr(mesh),1,arr(edges));
        check(new HashSet<>(front).equals(new HashSet<>(Arrays.asList(1,5,6,7))),"front face: shared top edge plus its own three ("+front+")");
        check(EdgeProjection.faceEdges(arr(mesh),5,arr(edges)).isEmpty(),"unknown face: no edges");
        // A circle aligned to the outline of the projected top face (four lines).
        List<SketchPrimitive> sketch=new ArrayList<>(Arrays.asList(
            new SketchPrimitive("a","Linia",0,0,40,0),new SketchPrimitive("b","Linia",40,0,40,30),new SketchPrimitive("c","Linia",40,30,0,30),new SketchPrimitive("d","Linia",0,30,0,0),
            new SketchPrimitive("hole","Okrąg",5,5,8,5)));
        List<String> outline=Arrays.asList("a","b","c","d");
        SketchPosition.Result centred=SketchPosition.align(sketch,new ArrayList<>(),"hole",outline,0,true);
        SketchPrimitive h=null;for(SketchPrimitive p:centred.sketch)if(p.id.equals("hole"))h=p;
        check(Math.abs(h.x1-20)<1e-6&&Math.abs(h.y1-15)<1e-6,"circle centred on the projected face outline");
        check(centred.links.size()==2,"centring locked with X and Y dimensions");
        SketchPosition.Result right=SketchPosition.align(sketch,new ArrayList<>(),"hole",outline,4,false);
        for(SketchPrimitive p:right.sketch)if(p.id.equals("hole"))h=p;
        check(Math.abs(h.x1+3-40)<1e-6,"circle touching the right edge of the outline");
        System.out.println("PASS FaceProjectionHostTest: "+checks+" checks");
    }
}
