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
    /**
     * 3MF (3D Manufacturing Format) mesh import: the model part of the ZIP package, objects with
     * meshes or components, build items with optional affine transforms, model unit scaled to mm.
     * Returns a mesh (stride 13); each object gets its own face number. DTDs are refused.
     */
    public static double[] read3mf(InputStream input) throws IOException {
        byte[] model=null;
        try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(input)) {
            java.util.zip.ZipEntry entry;long total=0;
            while((entry=zip.getNextEntry())!=null) {
                String name=entry.getName().replace('\\','/');
                if(!name.toLowerCase(java.util.Locale.ROOT).endsWith(".model")){continue;}
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[16384];int n;
                while((n=zip.read(buffer))>0){total+=n;if(total>96L*1024*1024)throw new IOException("3MF: model przekracza 96 MB");out.write(buffer,0,n);}
                if(name.toLowerCase(java.util.Locale.ROOT).endsWith("3dmodel.model")||model==null)model=out.toByteArray();
            }
        }catch(java.util.zip.ZipException e){throw new IOException("3MF: plik nie jest pakietem ZIP");}
        if(model==null)throw new IOException("3MF: brak części modelu (3D/3dmodel.model)");
        if(new String(model,0,Math.min(model.length,4096),StandardCharsets.UTF_8).contains("<!DOCTYPE"))throw new IOException("3MF: deklaracje DTD nie są obsługiwane");
        org.w3c.dom.Document document;
        try {
            javax.xml.parsers.DocumentBuilderFactory factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
            try{factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}catch(Exception ignored){}
            factory.setExpandEntityReferences(false);
            document=factory.newDocumentBuilder().parse(new ByteArrayInputStream(model));
        }catch(javax.xml.parsers.ParserConfigurationException|org.xml.sax.SAXException e){throw new IOException("3MF: niepoprawny XML modelu ("+e.getMessage()+")");}
        org.w3c.dom.Element root=document.getDocumentElement();
        String unit=root.getAttribute("unit");
        double scale=unit.isEmpty()||unit.equals("millimeter")?1:unit.equals("micron")?.001:unit.equals("centimeter")?10:unit.equals("meter")?1000:unit.equals("inch")?25.4:unit.equals("foot")?304.8:Double.NaN;
        if(Double.isNaN(scale))throw new IOException("3MF: nieznana jednostka "+unit);
        Map<String,org.w3c.dom.Element> objects=new HashMap<>();
        org.w3c.dom.NodeList list=root.getElementsByTagNameNS("*","object");
        for(int i=0;i<list.getLength();i++){org.w3c.dom.Element o=(org.w3c.dom.Element)list.item(i);objects.put(o.getAttribute("id"),o);}
        List<double[]> triangles=new ArrayList<>();List<Integer> faces=new ArrayList<>();int[] face={0};
        org.w3c.dom.NodeList items=root.getElementsByTagNameNS("*","item");
        if(items.getLength()==0)throw new IOException("3MF: brak elementów do zbudowania (build/item)");
        for(int i=0;i<items.getLength();i++) {
            org.w3c.dom.Element item=(org.w3c.dom.Element)items.item(i);
            emit3mf(objects,item.getAttribute("objectid"),matrix(item.getAttribute("transform")),scale,triangles,faces,face,0);
        }
        if(triangles.isEmpty())throw new IOException("3MF nie zawiera trójkątów");
        double[] mesh=new double[triangles.size()*13];
        for(int i=0;i<triangles.size();i++)triangle(mesh,i*13,triangles.get(i),faces.get(i));
        return mesh;
    }
    private static double[] matrix(String text) throws IOException {
        if(text==null||text.trim().isEmpty())return new double[]{1,0,0,0,1,0,0,0,1,0,0,0};
        String[] parts=text.trim().split("\\s+");if(parts.length!=12)throw new IOException("3MF: przekształcenie wymaga 12 liczb");
        double[] m=new double[12];for(int i=0;i<12;i++)m[i]=number(parts[i]);return m;
    }
    /** 3MF row-vector transform: p' = p·M (M given as m00 m01 m02 m10 m11 m12 m20 m21 m22 m30 m31 m32). */
    private static double[] multiply(double[] a,double[] b) {
        double[] r=new double[12];
        for(int row=0;row<4;row++)for(int col=0;col<3;col++){double v=row==3?b[9+col]:0;for(int k=0;k<3;k++)v+=a[row*3+k]*b[k*3+col];r[row*3+col]=v;}
        return r;
    }
    private static void emit3mf(Map<String,org.w3c.dom.Element> objects,String id,double[] m,double scale,List<double[]> triangles,List<Integer> faces,int[] face,int depth) throws IOException {
        if(depth>8)throw new IOException("3MF: zbyt głęboko zagnieżdżone komponenty");
        org.w3c.dom.Element object=objects.get(id);if(object==null)throw new IOException("3MF: brak obiektu "+id);
        org.w3c.dom.NodeList components=object.getElementsByTagNameNS("*","component");
        for(int i=0;i<components.getLength();i++){org.w3c.dom.Element c=(org.w3c.dom.Element)components.item(i);emit3mf(objects,c.getAttribute("objectid"),multiply(matrix(c.getAttribute("transform")),m),scale,triangles,faces,face,depth+1);}
        org.w3c.dom.NodeList vertexNodes=object.getElementsByTagNameNS("*","vertex");if(vertexNodes.getLength()==0)return;
        if(vertexNodes.getLength()>1000000)throw new IOException("3MF: limit 1000000 wierzchołków");
        double[] v=new double[vertexNodes.getLength()*3];
        for(int i=0;i<vertexNodes.getLength();i++){org.w3c.dom.Element e=(org.w3c.dom.Element)vertexNodes.item(i);
            double x=number(e.getAttribute("x")),y=number(e.getAttribute("y")),z=number(e.getAttribute("z"));
            v[i*3]=(x*m[0]+y*m[3]+z*m[6]+m[9])*scale;v[i*3+1]=(x*m[1]+y*m[4]+z*m[7]+m[10])*scale;v[i*3+2]=(x*m[2]+y*m[5]+z*m[8]+m[11])*scale;}
        org.w3c.dom.NodeList tris=object.getElementsByTagNameNS("*","triangle");face[0]++;
        for(int i=0;i<tris.getLength();i++) {
            org.w3c.dom.Element t=(org.w3c.dom.Element)tris.item(i);int[] k=new int[3];String[] names={"v1","v2","v3"};
            for(int j=0;j<3;j++){try{k[j]=Integer.parseInt(t.getAttribute(names[j]));}catch(NumberFormatException e){throw new IOException("3MF: niepoprawny indeks trójkąta");}
                if(k[j]<0||k[j]*3>=v.length)throw new IOException("3MF: indeks trójkąta poza listą wierzchołków");}
            double[] p={v[k[0]*3],v[k[0]*3+1],v[k[0]*3+2],v[k[1]*3],v[k[1]*3+1],v[k[1]*3+2],v[k[2]*3],v[k[2]*3+1],v[k[2]*3+2]};
            double ax=p[3]-p[0],ay=p[4]-p[1],az=p[5]-p[2],bx=p[6]-p[0],by=p[7]-p[1],bz=p[8]-p[2];
            double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;if(!(Math.sqrt(nx*nx+ny*ny+nz*nz)>1e-15))continue;
            if(triangles.size()>=250000)throw new IOException("3MF: limit 250000 trójkątów");
            triangles.add(p);faces.add(face[0]);
        }
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
                // Vertices with bulges (code 42 belongs to the vertex before it): bulge = tan(sweep/4), positive counter-clockwise.
                List<double[]> points=new ArrayList<>(); double[] point=null;
                for(int j=i+1;j<end;j++) {
                    String[] v=pairs.get(j);
                    if(v[0].equals("38")&&Math.abs(number(v[1]))>1e-8) throw new IOException("DXF musi być płaski");
                    if(v[0].equals("10")) {point=new double[]{number(v[1])*scale,0,0};points.add(point);}
                    if(v[0].equals("20")&&point!=null) point[1]=number(v[1])*scale;
                    if(v[0].equals("42")&&point!=null) point[2]=number(v[1]);
                }
                if(Math.abs(number(values.getOrDefault("230","1"))-1)>1e-8)throw new IOException("LWPOLYLINE: obsługiwany jest układ XY bez odwróconej osi Z");
                boolean closed=(((int)number(values.getOrDefault("70","0")))&1)!=0,bulged=false;
                for(int j=0;j<points.size()-(closed?0:1);j++)if(Math.abs(points.get(j)[2])>1e-10)bulged=true;
                if(closed&&points.size()==4&&!bulged&&axisRectangle(points)) {
                    result.add(new SketchPrimitive(UUID.randomUUID().toString(),"Prostokąt",
                        points.get(0)[0],points.get(0)[1],points.get(2)[0],points.get(2)[1]));
                } else for(int j=1;j<points.size()+(closed?1:0);j++) {
                    double[] a=points.get(j-1),b=points.get(j%points.size());
                    if(Math.abs(a[2])<=1e-10){result.add(new SketchPrimitive(UUID.randomUUID().toString(),"Linia",a[0],a[1],b[0],b[1]));continue;}
                    double cx=b[0]-a[0],cy=b[1]-a[1],c=Math.hypot(cx,cy);if(c<1e-9)continue;
                    double sag=a[2]*c/2,mx=(a[0]+b[0])/2+cy/c*sag,my=(a[1]+b[1])/2-cx/c*sag;
                    try{result.add(new SketchPrimitive(UUID.randomUUID().toString(),SketchCurves.arc(a[0],a[1],mx,my,b[0],b[1]),false));}
                    catch(IllegalArgumentException error){throw new IOException("LWPOLYLINE: "+error.getMessage(),error);}
                }
            } else if(type.equals("POINT")) {
                if(Math.abs(number(values.getOrDefault("30","0")))>1e-8)throw new IOException("DXF musi zawierać szkic 2D w płaszczyźnie XY");
                result.add(SketchPrimitive.point(UUID.randomUUID().toString(),number(values.getOrDefault("10","0"))*scale,number(values.getOrDefault("20","0"))*scale));
            } else if(type.equals("ELLIPSE")) {
                if(Math.abs(number(values.getOrDefault("230","1"))-1)>1e-8||Math.abs(number(values.getOrDefault("30","0")))>1e-8||Math.abs(number(values.getOrDefault("31","0")))>1e-8)throw new IOException("ELLIPSE: obsługiwany jest układ XY");
                double x=number(values.getOrDefault("10","0"))*scale,y=number(values.getOrDefault("20","0"))*scale,ax=number(values.getOrDefault("11","0"))*scale,ay=number(values.getOrDefault("21","0"))*scale;
                double ratio=number(values.getOrDefault("40","1")),t0=number(values.getOrDefault("41","0")),t1=number(values.getOrDefault("42",String.valueOf(2*Math.PI))),a=Math.hypot(ax,ay);
                try {
                    double sweep=t1-t0;while(sweep<=0)sweep+=2*Math.PI;
                    if(Math.abs(sweep-2*Math.PI)<1e-9)result.add(SketchPrimitive.ellipse(UUID.randomUUID().toString(),x,y,x+ax,y+ay,a*ratio));
                    else result.add(SketchPrimitive.ellipseArc(UUID.randomUUID().toString(),x,y,x+ax,y+ay,a*ratio,t0,t0+sweep));
                }catch(IllegalArgumentException error){throw new IOException("ELLIPSE: "+error.getMessage(),error);}
            } else if(type.equals("SPLINE")) {
                result.add(dxfSpline(pairs,i+1,end,scale));
            } else if(!type.equals("SEQEND")) throw new IOException("Nieobsługiwana encja DXF: "+type);
            if(result.size()>50000) throw new IOException("DXF: limit 50000 elementów");
            i=end;
        }
        if(result.isEmpty()) throw new IOException("Brak obsługiwanych elementów DXF");
        return result;
    }
    /**
     * SPLINE: fit points (11/21) when present; otherwise the B-spline (degree, knots 40, control
     * points 10/20) is evaluated. A cubic in Bezier form (TabletCAD export) gives back its
     * interpolation points exactly; other splines are re-interpolated through up to 128 points.
     */
    static SketchPrimitive dxfSpline(List<String[]> pairs,int from,int to,double scale) throws IOException {
        List<double[]> fit=new ArrayList<>(),control=new ArrayList<>();List<Double> knots=new ArrayList<>();int degree=3,flags=0;double[] f=null,c=null;
        for(int j=from;j<to;j++){String[] v=pairs.get(j);
            switch(v[0]){case "70":flags=(int)number(v[1]);break;case "71":degree=(int)number(v[1]);break;case "40":knots.add(number(v[1]));break;
                case "10":c=new double[]{number(v[1])*scale,0};control.add(c);break;case "20":if(c!=null)c[1]=number(v[1])*scale;break;
                case "11":f=new double[]{number(v[1])*scale,0};fit.add(f);break;case "21":if(f!=null)f[1]=number(v[1])*scale;break;
                case "30":case "31":if(Math.abs(number(v[1]))>1e-8)throw new IOException("SPLINE musi leżeć w płaszczyźnie XY");break;default:}}
        if((flags&4)!=0)throw new IOException("SPLINE wymierny (NURBS z wagami) nie jest obsługiwany");
        List<double[]> through=new ArrayList<>();
        if(fit.size()>=3)through.addAll(fit);
        else {
            int n=control.size();if(n<degree+1||knots.size()!=n+degree+1||degree<1||degree>5)throw new IOException("Niepoprawny SPLINE w DXF");
            boolean bezier=degree==3&&(n-1)%3==0;
            int spans=(n-1)/3;if(bezier)for(int k=0;k+1<spans;k++){int at=4+3*k;if(Math.abs(knots.get(at)-knots.get(at+1))>1e-12||Math.abs(knots.get(at+1)-knots.get(at+2))>1e-12){bezier=false;break;}}
            if(bezier){for(int k=0;k<n;k+=3)through.add(control.get(k));}
            else {double lo=knots.get(degree),hi=knots.get(n);int m=Math.min(128,Math.max(8,n*4));
                for(int k=0;k<m;k++)through.add(deBoor(degree,knots,control,lo+(hi-lo)*k/(m-1)));}
        }
        boolean closed=(flags&1)!=0||Math.hypot(through.get(0)[0]-through.get(through.size()-1)[0],through.get(0)[1]-through.get(through.size()-1)[1])<1e-6;
        List<double[]> clean=new ArrayList<>();for(double[] p:through)if(clean.isEmpty()||Math.hypot(p[0]-clean.get(clean.size()-1)[0],p[1]-clean.get(clean.size()-1)[1])>=.01)clean.add(p);
        if(closed&&Math.hypot(clean.get(0)[0]-clean.get(clean.size()-1)[0],clean.get(0)[1]-clean.get(clean.size()-1)[1])>1e-9)clean.add(clean.get(0).clone());
        if(closed){double[] l=clean.get(clean.size()-1);l[0]=clean.get(0)[0];l[1]=clean.get(0)[1];}
        while(clean.size()>128){List<double[]> half=new ArrayList<>();for(int k=0;k<clean.size();k+=2)half.add(clean.get(k));if(!Arrays.equals(half.get(half.size()-1),clean.get(clean.size()-1)))half.add(clean.get(clean.size()-1));clean=half;}
        double[] pts=new double[clean.size()*2];for(int k=0;k<clean.size();k++){pts[2*k]=clean.get(k)[0];pts[2*k+1]=clean.get(k)[1];}
        try{return new SketchPrimitive(UUID.randomUUID().toString(),pts,"Splajn");}catch(IllegalArgumentException e){throw new IOException("SPLINE: "+e.getMessage(),e);}
    }
    private static double[] deBoor(int p,List<Double> u,List<double[]> c,double t) {
        int n=c.size(),k=p;while(k<n-1&&t>=u.get(k+1))k++;
        double[][] d=new double[p+1][];for(int j=0;j<=p;j++)d[j]=c.get(j+k-p).clone();
        for(int r=1;r<=p;r++)for(int j=p;j>=r;j--){double den=u.get(j+1+k-r)-u.get(j+k-p);double a=den<1e-15?0:(t-u.get(j+k-p))/den;d[j][0]=(1-a)*d[j-1][0]+a*d[j][0];d[j][1]=(1-a)*d[j-1][1]+a*d[j][1];}
        return d[p];
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
