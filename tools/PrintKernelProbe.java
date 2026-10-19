import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** 0.79 printing: export-quality tessellation, 3MF with bodies, overhang test, lay flat. */
public final class PrintKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static void close(String what,double actual,double expected,double tolerance){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(what+": "+actual+" != "+expected);checks++;System.out.println("PASS "+what+" = "+actual);}
    static CadProject plate(){CadProject p=new CadProject();p.emptyDocument=false;p.width=100;p.height=60;p.depth=20;p.featureBase=new double[]{0,0,0,100,60,0,20};BodyGraph.begin(p);return p;}
    static double[] packed(CadProject p){int n=0;for(double[] f:p.committedFeatures)n+=f.length;double[] raw=new double[n];int at=0;for(double[] f:p.committedFeatures){System.arraycopy(f,0,raw,at,f.length);at+=f.length;}BodyGraph.validate(p);return BodyGraph.pack(p,raw);}
    static double volume(double[] m){double v=0;for(int i=0;i+12<m.length;i+=13)v+=m[i]*(m[i+4]*m[i+8]-m[i+5]*m[i+7])+m[i+1]*(m[i+5]*m[i+6]-m[i+3]*m[i+8])+m[i+2]*(m[i+3]*m[i+7]-m[i+4]*m[i+6]);return v/6;}
    static CadProject at(double z){CadProject p=new CadProject();p.sketchOriginZ=z;return p;}
    public static void main(String[] args)throws Exception {
        CadProject p=plate();double r=10;
        p.committedFeatures.add(ExtrusionPlan.feature(at(20),new SketchPrimitive("h","Okrąg",50,30,60,30),"cut",20,0,false,false));p.committedProfileIds.add("h");p.featureBodyTargets.add(1);p.featureBodyOutputs.add(1);
        double exact=120000-Math.PI*r*r*20;
        PrintExport.Mesh coarse=PrintExport.parse(NativeCadEngine.exportMesh(100,60,20,0,p.featureBase,packed(p),null,.1,20));
        PrintExport.Mesh fine=PrintExport.parse(NativeCadEngine.exportMesh(100,60,20,0,p.featureBase,packed(p),null,.001,2));
        check(fine.triangleCount()>coarse.triangleCount(),"finer deviation gives more triangles ("+coarse.triangleCount()+" → "+fine.triangleCount()+")");
        double ec=Math.abs(volume(coarse.triangles)-exact),ef=Math.abs(volume(fine.triangles)-exact);
        check(ef<ec&&ef/exact<2e-5,"finer mesh volume closer to the exact B-Rep ("+ec+" → "+ef+" mm³)");
        double[] display=NativeCadEngine.evaluateFeatures(100,60,20,0,p.featureBase,packed(p),null);
        check(display.length/13<fine.triangleCount(),"export tessellation does not refine the display mesh ("+display.length/13+")");
        boolean rejected=NativeCadEngine.exportMesh(100,60,20,0,p.featureBase,packed(p),null,0,10).length==0;check(rejected,"zero deviation refused");
        // Second body → two 3MF objects.
        p.committedFeatures.add(ExtrusionPlan.feature(at(30),new SketchPrimitive("b","Prostokąt",0,0,10,10),"new",5,0,false,false));p.committedProfileIds.add("b");p.featureBodyTargets.add(0);int second=BodyGraph.allocate(p);p.featureBodyOutputs.add(second);
        PrintExport.Mesh two=PrintExport.parse(NativeCadEngine.exportMesh(100,60,20,0,p.featureBase,packed(p),null,.02,10));
        Map<Integer,double[]> bodies=two.byBody();check(bodies.size()==2,"export mesh is grouped into two bodies");close("second body mesh volume",volume(bodies.get(second)),500,1e-6);
        ByteArrayOutputStream out=new ByteArrayOutputStream();Map<Integer,String> names=new HashMap<>();names.put(1,"Płyta & <A>");names.put(second,"Kostka");
        PrintExport.write3mf(bodies,names,out);
        Map<String,String> entries=new HashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))){ZipEntry e;while((e=zip.getNextEntry())!=null)entries.put(e.getName(),new String(zip.readAllBytes(),"UTF-8"));}
        check(entries.containsKey("[Content_Types].xml")&&entries.containsKey("_rels/.rels")&&entries.containsKey("3D/3dmodel.model"),"3MF package parts");
        String model=entries.get("3D/3dmodel.model");
        check(model.contains("unit=\"millimeter\"")&&model.split("<object ").length-1==2&&model.split("<item ").length-1==2,"3MF model: millimetres, two objects, two build items");
        check(model.contains("name=\"Płyta &amp; &lt;A&gt;\""),"object names are XML-escaped");
        // Parse the 3MF mesh of the box back and check its volume.
        String box=model.substring(model.indexOf("name=\"Kostka\""));box=box.substring(0,box.indexOf("</object>"));
        List<double[]> verts=new ArrayList<>();java.util.regex.Matcher vm=java.util.regex.Pattern.compile("<vertex x=\"([^\"]+)\" y=\"([^\"]+)\" z=\"([^\"]+)\"").matcher(box);while(vm.find())verts.add(new double[]{Double.parseDouble(vm.group(1)),Double.parseDouble(vm.group(2)),Double.parseDouble(vm.group(3))});
        java.util.regex.Matcher tm=java.util.regex.Pattern.compile("<triangle v1=\"(\\d+)\" v2=\"(\\d+)\" v3=\"(\\d+)\"").matcher(box);double v=0;int t=0;
        while(tm.find()){double[] a=verts.get(Integer.parseInt(tm.group(1))),b=verts.get(Integer.parseInt(tm.group(2))),c=verts.get(Integer.parseInt(tm.group(3)));v+=a[0]*(b[1]*c[2]-b[2]*c[1])+a[1]*(b[2]*c[0]-b[0]*c[2])+a[2]*(b[0]*c[1]-b[1]*c[0]);t++;}
        check(verts.size()==8&&t==12,"box object has shared 8 vertices and 12 triangles");close("3MF box volume (outward winding)",v/6,500,1e-3);
        // Overhang rule.
        check(PrintExport.overhang(-1,45)&&!PrintExport.overhang(-.5,45)&&PrintExport.overhang(-.8,45)&&!PrintExport.overhang(0,45)&&!PrintExport.overhang(1,45),"overhang test at 45°");
        check(!PrintExport.overhang(-.8,60)&&PrintExport.overhang(-.9,60),"overhang test at 60°");
        // Lay flat on the side face x = 100 (normal +X): the plate ends up on Z = 0 with the face down.
        CadProject lf=plate();for(double[] rec:PrintExport.layFlat(new double[]{100,30,10},new double[]{1,0,0})){lf.committedFeatures.add(rec);lf.committedProfileIds.add("");lf.featureBodyTargets.add(1);lf.featureBodyOutputs.add(1);}
        double[] mesh=NativeCadEngine.evaluateFeatures(100,60,20,0,lf.featureBase,packed(lf),null);double zmin=1e9,zmax=-1e9;for(int i=0;i+12<mesh.length;i+=13)for(int k=2;k<9;k+=3){zmin=Math.min(zmin,mesh[i+k]);zmax=Math.max(zmax,mesh[i+k]);}
        close("laid flat: bottom at Z = 0",zmin,0,1e-9);close("laid flat: height = former X length 100",zmax,100,1e-9);
        check(PrintExport.layFlat(new double[]{0,0,5},new double[]{0,0,-1}).size()==1,"a face already looking down needs only the move to Z = 0");
        refusedFlat:{try{PrintExport.layFlat(new double[]{0,0,0},new double[]{0,0,-1});}catch(IllegalArgumentException e){check(true,"a body already lying on that face is reported");break refusedFlat;}throw new AssertionError("no-op lay flat accepted");}
        check(PrintExport.layFlat(new double[]{0,0,20},new double[]{0,0,1}).size()==2,"the top face is turned over");
        CadProject q=CadProject.fromJson(new JSONObject(p.toJson().toString()));check(q.exportDeflection==.02&&q.exportAngle==10&&q.overhangAngle==45,"export settings persist with defaults");
        JSONObject bad=new JSONObject(p.toJson().toString());bad.put("exportDeflection",0);boolean refused=false;try{CadProject.fromJson(bad);}catch(JSONException e){refused=true;}check(refused,"invalid export deviation refused on load");
        System.out.println("PASS PrintKernelProbe: "+checks+" checks");
    }
}
