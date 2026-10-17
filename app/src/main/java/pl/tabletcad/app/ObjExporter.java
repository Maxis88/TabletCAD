package pl.tabletcad.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Wavefront polygonal OBJ; coordinates remain in document millimetres. */
public final class ObjExporter {
    private ObjExporter() {}
    private record Vertex(double x, double y, double z) {
        static Vertex at(double[] mesh, int i) {
            return new Vertex(zero(mesh[i]), zero(mesh[i+1]), zero(mesh[i+2]));
        }
        private static double zero(double v) { return v == 0 ? 0 : v; }
    }
    private static double[] normal(double[] m, int i) throws IOException {
        double ax=m[i+3]-m[i], ay=m[i+4]-m[i+1], az=m[i+5]-m[i+2];
        double bx=m[i+6]-m[i], by=m[i+7]-m[i+1], bz=m[i+8]-m[i+2];
        double x=ay*bz-az*by, y=az*bx-ax*bz, z=ax*by-ay*bx;
        double length=Math.hypot(Math.hypot(x,y),z);
        if (!(length>0) || !Double.isFinite(length))
            throw new IOException("OBJ: niepoprawny lub zerowy trójkąt");
        return new double[]{x/length,y/length,z/length};
    }
    public static void write(OutputStream output, double[] mesh) throws IOException {
        if (!CadMesh.isValid(mesh,false)) throw new IOException("OBJ: brak poprawnej siatki");
        Map<Vertex,Integer> vertices=new LinkedHashMap<>();
        // Validate the complete mesh before emitting even the header.
        for(int i=0;i<mesh.length;i+=13) {
            normal(mesh,i);
            for(int j=0;j<9;j+=3) {
                Vertex v=Vertex.at(mesh,i+j);
                if(!vertices.containsKey(v)) vertices.put(v,vertices.size()+1);
            }
        }
        Writer writer=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.US_ASCII));
        writer.write("# TabletCAD Wavefront OBJ\n# units: millimetres (mm); no axis conversion\no TabletCAD\n");
        for(Vertex v:vertices.keySet()) writer.write("v "+v.x+" "+v.y+" "+v.z+"\n");
        for(int i=0;i<mesh.length;i+=13) {
            double[] n=normal(mesh,i);
            writer.write("vn "+n[0]+" "+n[1]+" "+n[2]+"\n");
        }
        writer.write("s off\n");
        int previousFace=-1;
        for(int i=0;i<mesh.length;i+=13) {
            int face=(int)mesh[i+12];
            if(face!=previousFace) {writer.write("g face_"+face+"\n");previousFace=face;}
            int normal=i/13+1;
            writer.write("f");
            for(int j=0;j<9;j+=3) writer.write(" "+vertices.get(Vertex.at(mesh,i+j))+"//"+normal);
            writer.write("\n");
        }
        writer.flush(); // Caller owns the stream, including SAF destinations.
    }
}
