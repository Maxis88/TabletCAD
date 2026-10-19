package pl.tabletcad.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/**
 * Mesh export for 3D printing: export-quality tessellation (kernel query 13), body grouping and 3MF.
 * The display mesh is never used for B-Rep models, so the chordal deviation is the one chosen in the project.
 */
public final class PrintExport {
    private PrintExport() {}
    /** Presets: name, chordal deviation [mm], angular deviation [°]. */
    public static final String[] PRESETS={"Zgrubna","Standardowa","Dokładna","Bardzo dokładna"};
    public static final double[][] VALUES={{.1,20},{.02,10},{.005,5},{.001,2}};
    public static void validate(double deflection,double angle) {
        if(!(deflection>=.0005&&deflection<=5))throw new IllegalArgumentException("Odchyłka cięciwy: 0,0005–5 mm");
        if(!(angle>=.5&&angle<=45))throw new IllegalArgumentException("Odchyłka kątowa: 0,5–45°");
    }
    public static final class Mesh {
        public final double[] triangles;public final Map<Integer,Integer> faceBodies=new HashMap<>();
        Mesh(double[] triangles){this.triangles=triangles;}
        public int triangleCount(){return triangles.length/13;}
        /** Triangles (stride 13) of each body, in body order. */
        public Map<Integer,double[]> byBody() {
            Map<Integer,List<Integer>> index=new TreeMap<>();
            for(int i=0;i+13<=triangles.length;i+=13){int face=(int)triangles[i+12];int body=faceBodies.getOrDefault(face,1);index.computeIfAbsent(body,k->new ArrayList<>()).add(i);}
            Map<Integer,double[]> result=new LinkedHashMap<>();
            for(Map.Entry<Integer,List<Integer>> e:index.entrySet()){double[] m=new double[e.getValue().size()*13];int at=0;for(int o:e.getValue()){System.arraycopy(triangles,o,m,at,13);at+=13;}result.put(e.getKey(),m);}
            return result;
        }
    }
    /** Parses the kernel result [n, mesh(n), face, body, face, body …]. */
    public static Mesh parse(double[] packed) {
        if(packed.length<1)throw new IllegalArgumentException("Brak siatki eksportu");
        int n=(int)packed[0];if(n<13||n%13!=0||1+n>packed.length||(packed.length-1-n)%2!=0)throw new IllegalArgumentException("Niepoprawna siatka eksportu");
        Mesh mesh=new Mesh(Arrays.copyOfRange(packed,1,1+n));
        for(int i=1+n;i+1<packed.length;i+=2)mesh.faceBodies.put((int)packed[i],(int)packed[i+1]);
        return mesh;
    }
    /** 3MF package (millimetres): one object per body, shared vertices per object. */
    public static void write3mf(Map<Integer,double[]> bodies,Map<Integer,String> names,OutputStream output)throws IOException {
        if(bodies.isEmpty())throw new IOException("Brak brył do zapisu");
        ZipOutputStream zip=new ZipOutputStream(output);
        put(zip,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            +"<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            +"<Default Extension=\"model\" ContentType=\"application/vnd.ms-package.3dmanufacturing-3dmodel+xml\"/></Types>");
        put(zip,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            +"<Relationship Target=\"/3D/3dmodel.model\" Id=\"rel0\" Type=\"http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel\"/></Relationships>");
        StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<model unit=\"millimeter\" xml:lang=\"pl-PL\" xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">\n");
        xml.append("<metadata name=\"Application\">TabletCAD</metadata>\n<resources>\n");
        int id=1;List<Integer> ids=new ArrayList<>();
        for(Map.Entry<Integer,double[]> body:bodies.entrySet()) {
            String name=names.getOrDefault(body.getKey(),"Bryła "+body.getKey());
            xml.append("<object id=\"").append(id).append("\" type=\"model\" name=\"").append(escape(name)).append("\"><mesh><vertices>\n");
            Map<String,Integer> vertexIndex=new HashMap<>();List<float[]> vertices=new ArrayList<>();double[] m=body.getValue();int[] tri=new int[m.length/13*3];
            for(int t=0;t*13<m.length;t++)for(int k=0;k<3;k++){float x=(float)m[t*13+k*3],y=(float)m[t*13+k*3+1],z=(float)m[t*13+k*3+2];String key=x+","+y+","+z;
                Integer index=vertexIndex.get(key);if(index==null){index=vertices.size();vertexIndex.put(key,index);vertices.add(new float[]{x,y,z});}tri[t*3+k]=index;}
            for(float[] v:vertices)xml.append("<vertex x=\"").append(v[0]).append("\" y=\"").append(v[1]).append("\" z=\"").append(v[2]).append("\"/>\n");
            xml.append("</vertices><triangles>\n");
            for(int t=0;t<tri.length;t+=3)if(tri[t]!=tri[t+1]&&tri[t+1]!=tri[t+2]&&tri[t]!=tri[t+2])xml.append("<triangle v1=\"").append(tri[t]).append("\" v2=\"").append(tri[t+1]).append("\" v3=\"").append(tri[t+2]).append("\"/>\n");
            xml.append("</triangles></mesh></object>\n");ids.add(id++);
        }
        xml.append("</resources>\n<build>\n");for(int i:ids)xml.append("<item objectid=\"").append(i).append("\"/>\n");xml.append("</build>\n</model>\n");
        put(zip,"3D/3dmodel.model",xml.toString());
        zip.finish();zip.flush();
    }
    static void put(ZipOutputStream zip,String name,String text)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
    static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    /**
     * "Lay flat": records turning the body so the face (point, outward normal) looks down (−Z) and moving it onto Z = 0.
     * Returns one or two body-transform records (no rotation when the face already looks down).
     */
    public static java.util.List<double[]> layFlat(double[] point,double[] normal) {
        double l=Math.sqrt(normal[0]*normal[0]+normal[1]*normal[1]+normal[2]*normal[2]);if(!(l>1e-9))throw new IllegalArgumentException("Zerowa normalna ściany");
        double nx=normal[0]/l,ny=normal[1]/l,nz=normal[2]/l;java.util.List<double[]> records=new java.util.ArrayList<>();
        double angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,-nz))));
        if(angle>1e-9) {
            double[] axis={-ny,nx,0};/* n × (0,0,−1) */ if(Math.hypot(axis[0],axis[1])<1e-12)axis=new double[]{1,0,0};
            records.add(BodyTransform.rotate(point,axis,angle));
        }
        if(Math.abs(point[2])>1e-9)records.add(BodyTransform.move(0,0,-point[2]));
        if(records.isEmpty())throw new IllegalArgumentException("Bryła już leży tą ścianą na stole");
        return records;
    }
    /** True when the triangle faces down more steeply than the allowed overhang (angle from the vertical build direction). */
    public static boolean overhang(double nz,double maxOverhangDegrees){return nz< -Math.cos(Math.toRadians(90-maxOverhangDegrees))-1e-12;}
}
