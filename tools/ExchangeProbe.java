import pl.tabletcad.app.CadExchange;
import pl.tabletcad.app.model.SketchPrimitive;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ExchangeProbe {
    private static InputStream input(String text) {return new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII));}
    private static void check(boolean value) {if(!value) throw new AssertionError("Exchange check failed");}
    private static void reject(InputStream input,boolean stl) throws Exception {
        try {if(stl) CadExchange.readStl(input); else CadExchange.readDxf(input);}
        catch(IOException expected) {return;}
        throw new AssertionError("Malformed file accepted");
    }
    public static void main(String[] args) throws Exception {
        try{run(args);}catch(Throwable error){error.printStackTrace(System.out);System.exit(1);}
    }
    private static void run(String[] args) throws Exception {
        String ascii="solid test\nfacet normal 0 0 1\nouter loop\nvertex 0 0 0\nvertex 80 0 0\nvertex 0 40 0\nendloop\nendfacet\nendsolid test\n";
        double[] textMesh=CadExchange.readStl(input(ascii));
        check(textMesh.length==13&&textMesh[3]==80&&textMesh[7]==40&&textMesh[11]==1);
        ByteBuffer binary=ByteBuffer.allocate(134).order(ByteOrder.LITTLE_ENDIAN);
        binary.put("solid binary ambiguity".getBytes(StandardCharsets.US_ASCII));
        binary.position(80);binary.putInt(1);binary.putFloat(0).putFloat(0).putFloat(1);
        for(float value:new float[]{0,0,0,80,0,0,0,40,0}) binary.putFloat(value);
        binary.putShort((short)0);
        check(Arrays.equals(textMesh,CadExchange.readStl(new ByteArrayInputStream(binary.array()))));
        reject(input(ascii.replace("80 0 0","NaN 0 0")),true);
        reject(new ByteArrayInputStream(Arrays.copyOf(binary.array(),120)),true);
        String dxf="0\nSECTION\n2\nHEADER\n9\n$INSUNITS\n70\n4\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n"
            +"0\nLWPOLYLINE\n90\n4\n70\n1\n10\n0\n20\n0\n10\n40\n20\n0\n10\n40\n20\n20\n10\n0\n20\n20\n"
            +"0\nCIRCLE\n10\n60\n20\n10\n40\n5\n0\nENDSEC\n0\nEOF\n";
        List<SketchPrimitive> sketch=CadExchange.readDxf(input(dxf));
        check(sketch.size()==2&&sketch.get(0).type.equals("Prostokąt")&&sketch.get(0).x2==40);
        check(sketch.get(1).type.equals("Okrąg")&&sketch.get(1).x2==65);
        check(CadExchange.readDxf(input(dxf.replace("70\n4\n","70\n1\n"))).get(0).x2==1016);
        reject(input(dxf.replace("CIRCLE","ARC")),false);
        try(FileOutputStream f=new FileOutputStream(args[0]+".ascii.stl")) {f.write(ascii.getBytes(StandardCharsets.US_ASCII));}
        try(FileOutputStream f=new FileOutputStream(args[0]+".binary.stl")) {f.write(binary.array());}
        try(FileOutputStream f=new FileOutputStream(args[0]+".dxf")) {f.write(dxf.getBytes(StandardCharsets.US_ASCII));}
        System.out.println("PASS STL ASCII/binary incl. solid header; malformed/NaN rejection; DXF rectangle/circle/units/unsupported rejection");
    }
}
