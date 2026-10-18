package pl.tabletcad.app;

import java.util.*;

/** Disposable display adjacency. Never used as a document/topological reference. */
final class DisplayMeshTopology {
    final double[] mesh;
    final int[] neighbours;
    final boolean[] visible;
    DisplayMeshTopology(double[] mesh) {
        this.mesh=mesh;int count=mesh.length/13;
        neighbours=new int[count*3];Arrays.fill(neighbours,-1);visible=new boolean[count];
        Map<Vertex,Integer> vertices=new HashMap<>();Map<Edge,Integer> edges=new HashMap<>();
        for(int t=0;t<count;t++) {
            int[] ids=new int[3];int offset=t*13;
            for(int v=0;v<3;v++){Vertex point=new Vertex(mesh,offset+v*3);Integer id=vertices.get(point);if(id==null){id=vertices.size();vertices.put(point,id);}ids[v]=id;}
            for(int e=0;e<3;e++) {
                Edge key=new Edge((int)Math.round(mesh[offset+12]),ids[e],ids[(e+1)%3]);
                int index=t*3+e;Integer other=edges.putIfAbsent(key,index);
                if(other!=null){neighbours[index]=other/3;neighbours[other]=t;}
            }
        }
    }
    private static final class Vertex {
        final long x,y,z;
        Vertex(double[] m,int i){x=bits(m[i]);y=bits(m[i+1]);z=bits(m[i+2]);}
        static long bits(double v){return Double.doubleToLongBits(v==0?0:v);}
        @Override public int hashCode(){return Long.hashCode(x)*961+Long.hashCode(y)*31+Long.hashCode(z);}
        @Override public boolean equals(Object o){if(!(o instanceof Vertex))return false;Vertex v=(Vertex)o;return x==v.x&&y==v.y&&z==v.z;}
    }
    private static final class Edge {
        final int face,a,b;
        Edge(int face,int a,int b){this.face=face;this.a=Math.min(a,b);this.b=Math.max(a,b);}
        @Override public int hashCode(){return (face*31+a)*31+b;}
        @Override public boolean equals(Object o){if(!(o instanceof Edge))return false;Edge e=(Edge)o;return face==e.face&&a==e.a&&b==e.b;}
    }
}
