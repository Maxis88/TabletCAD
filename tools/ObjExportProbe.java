import pl.tabletcad.app.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ObjExportProbe {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void verify(double[] mesh, double expectedVolume, File destination)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Locale previous=Locale.getDefault();Locale.setDefault(Locale.forLanguageTag("pl-PL"));
        try{ObjExporter.write(bytes,mesh);}finally{Locale.setDefault(previous);}
        String text=bytes.toString(StandardCharsets.US_ASCII);
        check(text.contains("# units: millimetres"),"units comment");
        List<double[]> vertices=new ArrayList<>(),normals=new ArrayList<>();
        int faces=0;double volume=0;
        for(String line:text.split("\n")) {
            String[] tokens=line.split(" +");
            if(tokens[0].equals("v")||tokens[0].equals("vn")) {
                double[] v={Double.parseDouble(tokens[1]),Double.parseDouble(tokens[2]),Double.parseDouble(tokens[3])};
                (tokens[0].equals("v")?vertices:normals).add(v);
            } else if(tokens[0].equals("f")) {
                check(tokens.length==4,"triangle face");double[][] p=new double[3][];
                for(int j=0;j<3;j++) {
                    String[] index=tokens[j+1].split("//");check(index.length==2,"vertex//normal syntax");
                    int vi=Integer.parseInt(index[0]),ni=Integer.parseInt(index[1]);
                    check(vi>0&&vi<=vertices.size()&&ni==faces+1&&ni<=normals.size(),"valid independent one-based indices");
                    p[j]=vertices.get(vi-1);
                    for(int k=0;k<3;k++)check(p[j][k]==mesh[faces*13+j*3+k],"exact original coordinates and winding");
                }
                double[] a=p[0],b=p[1],c=p[2],n=normals.get(faces);
                double nx=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1]);
                double ny=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2]);
                double nz=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
                check(nx*n[0]+ny*n[1]+nz*n[2]>0,"normal agrees with winding");
                check(Math.abs(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]-1)<1e-12,"unit normal");
                volume+=a[0]*(b[1]*c[2]-b[2]*c[1])+a[1]*(b[2]*c[0]-b[0]*c[2])+a[2]*(b[0]*c[1]-b[1]*c[0]);
                faces++;
            }
        }
        check(faces==mesh.length/13&&normals.size()==faces,"all triangles preserved");
        check(vertices.size()<faces*3,"shared vertices reused");
        check(Math.abs(Math.abs(volume/6)-expectedVolume)<.001,"exported signed volume");
        try(OutputStream output=new FileOutputStream(destination)){bytes.writeTo(output);}
    }
    public static void main(String[] args)throws Exception {
        double[] box=NativeCadEngine.evaluateFeatures(80,48,32,0,null,new double[0],null);
        verify(box,122880,new File(args[0]+".obj"));
        double[] cut={0,1,12,12,17,17,5,0,0,32,1,0,0,0,1,0};
        verify(NativeCadEngine.evaluateFeatures(80,48,32,0,null,cut,null),122755,new File(args[0]+"-cut.obj"));
        for(double[] invalid:new double[][]{null,new double[0],new double[12],new double[13]}) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try{ObjExporter.write(bytes,invalid);throw new AssertionError("invalid mesh accepted");}
            catch(IOException expected){check(bytes.size()==0,"invalid input writes no partial header");}
        }
        double[] malformed=box.clone();malformed[malformed.length-4]=Double.NaN;
        try{ObjExporter.write(new ByteArrayOutputStream(),malformed);throw new AssertionError("NaN accepted");}catch(IOException expected){}
        System.out.println("PASS OBJ: exact coordinates/winding, shared vertices, independent normal indices, unit normals, box/cut volumes, Polish locale and invalid input rejection");
    }
}
