package pl.tabletcad.app;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import pl.tabletcad.app.model.SketchPrimitive;

/** Bounded offline readers. STL stays explicitly a triangle mesh. */
public final class CadExchange {
    private CadExchange() {}
    private static byte[] bytes(InputStream input) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int n;
        while ((n=input.read(buffer))!=-1) {
            if (data.size()+n>32*1024*1024) throw new IOException("Limit pliku: 32 MB");
            data.write(buffer,0,n);
        }
        return data.toByteArray();
    }
    private static double number(String text) throws IOException {
        try {
            double value=Double.parseDouble(text.trim());
            if (!Double.isFinite(value) || Math.abs(value)>1e7) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) { throw new IOException("Niepoprawna współrzędna"); }
    }
    private static void triangle(double[] mesh,int offset,double[] p,int face) throws IOException {
        double ax=p[3]-p[0], ay=p[4]-p[1], az=p[5]-p[2];
        double bx=p[6]-p[0], by=p[7]-p[1], bz=p[8]-p[2];
        double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
        double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if (length<1e-15 || !Double.isFinite(length)) throw new IOException("STL zawiera zerowy trójkąt");
        System.arraycopy(p,0,mesh,offset,9);
        mesh[offset+9]=nx/length;mesh[offset+10]=ny/length;mesh[offset+11]=nz/length;
        mesh[offset+12]=face;
    }
    public static double[] readStl(InputStream input) throws IOException {
        byte[] data=bytes(input);
        if (data.length>=84) {
            ByteBuffer binary=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            long count=Integer.toUnsignedLong(binary.getInt(80));
            if (84L+50L*count==data.length) {
                if (count==0 || count>250000) throw new IOException("STL: limit 250000 trójkątów");
                double[] mesh=new double[(int)count*13],p=new double[9];
                for(int i=0;i<count;i++) {
                    binary.position(84+i*50+12);
                    for(int j=0;j<9;j++) {
                        p[j]=binary.getFloat();
                        if (!Double.isFinite(p[j]) || Math.abs(p[j])>1e7) throw new IOException("Niepoprawny STL");
                    }
                    triangle(mesh,i*13,p,i+1);
                }
                return mesh;
            }
        }
        String ascii=new String(data,StandardCharsets.US_ASCII);
        if (!ascii.trim().toLowerCase(Locale.ROOT).startsWith("solid")
                || !ascii.toLowerCase(Locale.ROOT).contains("endsolid")) throw new IOException("Niepoprawny STL");
        Matcher vertices=Pattern.compile("(?i)\\bvertex\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)").matcher(ascii);
        ArrayList<double[]> triangles=new ArrayList<>();
        double[] p=new double[9]; int count=0;
        while(vertices.find()) {
            for(int j=0;j<3;j++) p[count*3+j]=number(vertices.group(j+1));
            if (++count==3) {
                if(triangles.size()>=250000) throw new IOException("STL: limit 250000 trójkątów");
                triangles.add(p); p=new double[9]; count=0;
            }
        }
        if(count!=0 || triangles.isEmpty()) throw new IOException("Niekompletne trójkąty STL");
        int facets=0; Matcher facet=Pattern.compile("(?i)\\bfacet\\s+normal\\b").matcher(ascii);
        while(facet.find()) facets++;
        if(facets!=triangles.size()) throw new IOException("Niepoprawna struktura STL ASCII");
        double[] mesh=new double[triangles.size()*13];
        for(int i=0;i<triangles.size();i++) triangle(mesh,i*13,triangles.get(i),i+1);
        return mesh;
    }
    /**
     * Wavefront OBJ geometry (v / f; polygons fan-triangulated, v/vt/vn and negative indices).
     * Materials, textures, normals and groups are ignored; the result is a triangle mesh like STL.
     */
    public static double[] readObj(InputStream input) throws IOException {
        String text=new String(bytes(input),StandardCharsets.UTF_8);
        List<double[]> vertices=new ArrayList<>();List<double[]> triangles=new ArrayList<>();int degenerate=0;
        int lineNumber=0;
        for(String raw:text.split("\\r?\\n|\\r")) {
            lineNumber++;String line=raw.trim();int hash=line.indexOf('#');if(hash>=0)line=line.substring(0,hash).trim();if(line.isEmpty())continue;
            String[] parts=line.split("\\s+");
            if(parts[0].equals("v")) {
                if(parts.length<4)throw new IOException("OBJ: niepełny wierzchołek w wierszu "+lineNumber);
                if(vertices.size()>=1000000)throw new IOException("OBJ: limit 1000000 wierzchołków");
                vertices.add(new double[]{number(parts[1]),number(parts[2]),number(parts[3])});
            } else if(parts[0].equals("f")) {
                if(parts.length<4)throw new IOException("OBJ: ściana z mniej niż 3 wierzchołkami w wierszu "+lineNumber);
                int[] index=new int[parts.length-1];
                for(int i=1;i<parts.length;i++) {
                    String head=parts[i].split("/")[0];int value;
                    try{value=Integer.parseInt(head);}catch(NumberFormatException e){throw new IOException("OBJ: niepoprawny indeks w wierszu "+lineNumber);}
                    int resolved=value>0?value-1:vertices.size()+value;
                    if(value==0||resolved<0||resolved>=vertices.size())throw new IOException("OBJ: indeks poza listą wierzchołków w wierszu "+lineNumber);
                    index[i-1]=resolved;
                }
                for(int i=1;i+1<index.length;i++) {
                    double[] a=vertices.get(index[0]),b=vertices.get(index[i]),c=vertices.get(index[i+1]);
                    double[] p={a[0],a[1],a[2],b[0],b[1],b[2],c[0],c[1],c[2]};
                    double ax=p[3]-p[0],ay=p[4]-p[1],az=p[5]-p[2],bx=p[6]-p[0],by=p[7]-p[1],bz=p[8]-p[2];
                    double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
                    if(!(Math.sqrt(nx*nx+ny*ny+nz*nz)>1e-15)){degenerate++;continue;}
                    if(triangles.size()>=250000)throw new IOException("OBJ: limit 250000 trójkątów");
                    triangles.add(p);
                }
            }
        }
        if(triangles.isEmpty())throw new IOException(degenerate>0?"OBJ zawiera tylko zerowe trójkąty":"OBJ nie zawiera ścian (f)");
        double[] mesh=new double[triangles.size()*13];
        for(int i=0;i<triangles.size();i++)triangle(mesh,i*13,triangles.get(i),i+1);
        return mesh;
    }
    /** Binary STL of a mesh (stride 13: three vertices, normal, face). */
    public static void writeBinaryStl(double[] mesh,OutputStream output)throws IOException {
        int count=mesh.length/13;ByteBuffer buffer=ByteBuffer.allocate(84+50*count).order(ByteOrder.LITTLE_ENDIAN);
        byte[] title="TabletCAD binary STL".getBytes(StandardCharsets.US_ASCII);buffer.put(title);buffer.position(80);buffer.putInt(count);
        for(int i=0;i<count;i++){int o=i*13;buffer.putFloat((float)mesh[o+9]).putFloat((float)mesh[o+10]).putFloat((float)mesh[o+11]);for(int j=0;j<9;j++)buffer.putFloat((float)mesh[o+j]);buffer.putShort((short)0);}
        output.write(buffer.array());
    }
    public static List<SketchPrimitive> readDxf(InputStream input) throws IOException {
        String[] lines=new String(bytes(input),StandardCharsets.UTF_8).split("\\r?\\n");
        List<String[]> pairs=new ArrayList<>();
        for(int i=0;i+1<lines.length;i+=2) pairs.add(new String[]{lines[i].trim(),lines[i+1].trim()});
        double scale=1;
        for(int i=0;i+1<pairs.size();i++) if(pairs.get(i)[1].equals("$INSUNITS")) {
            String units=pairs.get(i+1)[1];
            if(units.equals("1")) scale=25.4;
            else if(units.equals("5")) scale=10;
            else if(units.equals("6")) scale=1000;
            else if(!units.equals("0")&&!units.equals("4")) throw new IOException("Nieobsługiwane jednostki DXF");
        }
        List<SketchPrimitive> result=new ArrayList<>(); boolean entities=false;
        for(int i=0;i<pairs.size();) {
            String[] pair=pairs.get(i);
            if(pair[0].equals("2")&&pair[1].equals("ENTITIES")) {entities=true;i++;continue;}
            if(pair[0].equals("0")&&pair[1].equals("ENDSEC")) entities=false;
            if(!entities||!pair[0].equals("0")) {i++;continue;}
            String type=pair[1]; int end=i+1;
            while(end<pairs.size()&&!pairs.get(end)[0].equals("0")) end++;
            Map<String,String> values=new HashMap<>();
            for(int j=i+1;j<end;j++) values.put(pairs.get(j)[0],pairs.get(j)[1]);
            if(type.equals("LINE")||type.equals("CIRCLE")||type.equals("ARC")) {
                double x=number(values.getOrDefault("10","0"))*scale;
                double y=number(values.getOrDefault("20","0"))*scale;
                for(String z:new String[]{"30","31"}) if(Math.abs(number(values.getOrDefault(z,"0")))>1e-8)
                    throw new IOException("DXF musi zawierać szkic 2D w płaszczyźnie XY");
                if(type.equals("ARC")) {
                    double radius=number(values.getOrDefault("40","0"))*scale,start=Math.toRadians(number(values.getOrDefault("50","0"))),endAngle=Math.toRadians(number(values.getOrDefault("51","0")));
                    if(Math.abs(number(values.getOrDefault("210","0")))>1e-8||Math.abs(number(values.getOrDefault("220","0")))>1e-8||Math.abs(number(values.getOrDefault("230","1"))-1)>1e-8)throw new IOException("ARC: obsługiwany jest układ XY bez obróconej osi OCS");
                    double sweep=(endAngle-start)%(Math.PI*2);if(sweep<0)sweep+=Math.PI*2;
                    if(radius<=0||sweep<1e-8)throw new IOException("Niepoprawny promień lub zakres łuku DXF");
                    try {result.add(new SketchPrimitive(UUID.randomUUID().toString(),SketchCurves.arc(x+radius*Math.cos(start),y+radius*Math.sin(start),x+radius*Math.cos(start+sweep/2),y+radius*Math.sin(start+sweep/2),x+radius*Math.cos(start+sweep),y+radius*Math.sin(start+sweep)),false));}
                    catch(IllegalArgumentException error){throw new IOException("ARC: "+error.getMessage(),error);}
                }else if(type.equals("CIRCLE")) {
                    double radius=number(values.getOrDefault("40","0"))*scale;
                    if(radius<=0) throw new IOException("Niepoprawny promień DXF");
                    result.add(new SketchPrimitive(UUID.randomUUID().toString(),"Okrąg",x,y,x+radius,y));
                } else result.add(new SketchPrimitive(UUID.randomUUID().toString(),"Linia",x,y,
                    number(values.getOrDefault("11","0"))*scale,number(values.getOrDefault("21","0"))*scale));
            } else if(type.equals("LWPOLYLINE")) {
                List<double[]> points=new ArrayList<>(); double[] point=null;
                for(int j=i+1;j<end;j++) {
                    String[] v=pairs.get(j);
                    if(v[0].equals("42")&&Math.abs(number(v[1]))>1e-10) throw new IOException("Łukowe polilinie DXF nie są jeszcze obsługiwane");
                    if(v[0].equals("38")&&Math.abs(number(v[1]))>1e-8) throw new IOException("DXF musi być płaski");
                    if(v[0].equals("10")) {point=new double[]{number(v[1])*scale,0};points.add(point);}
                    if(v[0].equals("20")&&point!=null) point[1]=number(v[1])*scale;
                }
                boolean closed=(((int)number(values.getOrDefault("70","0")))&1)!=0;
                if(closed&&points.size()==4&&axisRectangle(points)) {
                    result.add(new SketchPrimitive(UUID.randomUUID().toString(),"Prostokąt",
                        points.get(0)[0],points.get(0)[1],points.get(2)[0],points.get(2)[1]));
                } else for(int j=1;j<points.size()+(closed?1:0);j++) {
                    double[] a=points.get(j-1),b=points.get(j%points.size());
                    result.add(new SketchPrimitive(UUID.randomUUID().toString(),"Linia",a[0],a[1],b[0],b[1]));
                }
            } else if(!type.equals("SEQEND")) throw new IOException("Nieobsługiwana encja DXF: "+type);
            if(result.size()>50000) throw new IOException("DXF: limit 50000 elementów");
            i=end;
        }
        if(result.isEmpty()) throw new IOException("Brak obsługiwanych elementów DXF");
        return result;
    }
    private static boolean axisRectangle(List<double[]> p) {
        for(int i=0;i<4;i++) {
            double[] a=p.get(i),b=p.get((i+1)%4);
            boolean horizontal=Math.abs(a[1]-b[1])<1e-8,vertical=Math.abs(a[0]-b[0])<1e-8;
            if(horizontal==vertical) return false;
        }
        double[] a=p.get(0),b=p.get(1),c=p.get(2),d=p.get(3);
        boolean orderA=Math.abs(a[1]-b[1])<1e-8&&Math.abs(b[0]-c[0])<1e-8
            &&Math.abs(c[1]-d[1])<1e-8&&Math.abs(d[0]-a[0])<1e-8;
        boolean orderB=Math.abs(a[0]-b[0])<1e-8&&Math.abs(b[1]-c[1])<1e-8
            &&Math.abs(c[0]-d[0])<1e-8&&Math.abs(d[1]-a[1])<1e-8;
        return (orderA||orderB)&&Math.abs(a[0]-c[0])>1e-8&&Math.abs(a[1]-c[1])>1e-8;
    }
}
