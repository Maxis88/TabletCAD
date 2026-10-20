import java.io.*;
import java.util.*;
import java.util.zip.*;
import pl.tabletcad.app.CadExchange;
import pl.tabletcad.app.PrintExport;

/** 0.97 3MF import: round trip with the 3MF export, transforms, components, units, malformed packages. */
public final class ThreeMfHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static double[] cube(double size,double dx,int face) {
        double[][] v={{0,0,0},{size,0,0},{size,size,0},{0,size,0},{0,0,size},{size,0,size},{size,size,size},{0,size,size}};
        int[][] t={{0,2,1},{0,3,2},{4,5,6},{4,6,7},{0,1,5},{0,5,4},{1,2,6},{1,6,5},{2,3,7},{2,7,6},{3,0,4},{3,4,7}};
        double[] m=new double[t.length*13];
        for(int i=0;i<t.length;i++){for(int k=0;k<3;k++){m[i*13+k*3]=v[t[i][k]][0]+dx;m[i*13+k*3+1]=v[t[i][k]][1];m[i*13+k*3+2]=v[t[i][k]][2];}m[i*13+11]=1;m[i*13+12]=face;}
        return m;
    }
    static byte[] zip(String model)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();ZipOutputStream z=new ZipOutputStream(out);z.putNextEntry(new ZipEntry("3D/3dmodel.model"));z.write(model.getBytes("UTF-8"));z.closeEntry();z.finish();return out.toByteArray();}
    static double[] bounds(double[] m){double[] b={1e9,1e9,1e9,-1e9,-1e9,-1e9};for(int i=0;i<m.length;i+=13)for(int k=0;k<9;k++){b[k%3]=Math.min(b[k%3],m[i+k]);b[3+k%3]=Math.max(b[3+k%3],m[i+k]);}return b;}
    static boolean refused(byte[] data){try{CadExchange.read3mf(new ByteArrayInputStream(data));return false;}catch(IOException e){System.out.println("  ("+e.getMessage()+")");return true;}}
    public static void main(String[] args)throws Exception {
        Map<Integer,double[]> bodies=new LinkedHashMap<>();bodies.put(1,cube(10,0,1));bodies.put(2,cube(5,20,2));
        ByteArrayOutputStream out=new ByteArrayOutputStream();PrintExport.write3mf(bodies,new HashMap<>(),out);
        double[] mesh=CadExchange.read3mf(new ByteArrayInputStream(out.toByteArray()));
        check(mesh.length/13==24,"round trip: 24 triangles of two cubes");
        double[] b=bounds(mesh);check(Math.abs(b[0])<1e-6&&Math.abs(b[3]-25)<1e-6&&Math.abs(b[5]-10)<1e-6,"round trip keeps the geometry (bounds 0…25 × 0…10)");
        Set<Integer> faces=new HashSet<>();for(int i=12;i<mesh.length;i+=13)faces.add((int)mesh[i]);check(faces.size()==2,"each object is its own face group");
        String tri="<object id=\"1\" type=\"model\"><mesh><vertices><vertex x=\"0\" y=\"0\" z=\"0\"/><vertex x=\"1\" y=\"0\" z=\"0\"/><vertex x=\"0\" y=\"1\" z=\"0\"/></vertices><triangles><triangle v1=\"0\" v2=\"1\" v3=\"2\"/></triangles></mesh></object>";
        String head="<?xml version=\"1.0\"?><model unit=\"%s\" xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\"><resources>";
        double[] inch=CadExchange.read3mf(new ByteArrayInputStream(zip(String.format(head,"inch")+tri+"</resources><build><item objectid=\"1\" transform=\"1 0 0 0 1 0 0 0 1 10 0 0\"/></build></model>")));
        check(Math.abs(inch[3]-11*25.4)<1e-9&&Math.abs(inch[0]-254)<1e-9,"inch unit and build transform (translation 10 in) → mm");
        String comp="<object id=\"2\" type=\"model\"><components><component objectid=\"1\" transform=\"0 1 0 -1 0 0 0 0 1 0 0 5\"/></components></object>";
        double[] turned=CadExchange.read3mf(new ByteArrayInputStream(zip(String.format(head,"millimeter")+tri+comp+"</resources><build><item objectid=\"2\"/></build></model>")));
        check(Math.abs(turned[3])<1e-9&&Math.abs(turned[4]-1)<1e-9&&Math.abs(turned[5]-5)<1e-9,"component transform: rotated 90° about Z and lifted 5 mm");
        check(refused("not a zip".getBytes()),"a non-ZIP file is refused");
        check(refused(zip("<?xml version=\"1.0\"?><!DOCTYPE m [<!ENTITY x \"y\">]><model>&x;</model>")),"a DTD is refused");
        check(refused(zip(String.format(head,"millimeter")+tri+"</resources><build/></model>")),"a model without build items is refused");
        check(refused(zip(String.format(head,"furlong")+tri+"</resources><build><item objectid=\"1\"/></build></model>")),"an unknown unit is refused");
        check(refused(zip(String.format(head,"millimeter")+tri.replace("v3=\"2\"","v3=\"9\"")+"</resources><build><item objectid=\"1\"/></build></model>")),"an index outside the vertex list is refused");
        System.out.println("PASS ThreeMfHostTest: "+checks+" checks");
    }
}
