import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** 0.75: construction geometry, angle/area helpers, OBJ import — without OCCT. */
public final class ConstructionHostTest {
    interface Work{void run()throws Exception;}
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static void near(double a,double b,String label){check(Math.abs(a-b)<1e-9,label+" ("+a+")");}
    static void rejects(Work w,String label)throws Exception{try{w.run();}catch(IllegalArgumentException|JSONException|IOException e){check(true,label+" → "+e.getMessage());return;}throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        Datum offset=Datum.create(Datum.PLANE,"offset","Płaszczyzna XY",new double[]{0,0,0,0,0,2,15},null);
        near(offset.origin[2],15,"offset plane lies 15 mm above XY");near(offset.direction[2],1,"normal is normalised");check(offset.name.equals("Płaszczyzna"),"default name");
        double[] f=offset.sketchFrame();near(f[2],15,"sketch frame origin on the plane");near(f[3]*f[6]+f[4]*f[7]+f[5]*f[8],0,"sketch U ⟂ V");
        Datum angled=Datum.create(Datum.PLANE,"angle","Płaszczyzna XY",new double[]{0,0,0,1,0,0,0,0,1,30},"Skos");
        near(angled.direction[1],-Math.sin(Math.toRadians(30)),"XY turned 30° about X: normal Y");near(angled.direction[2],Math.cos(Math.toRadians(30)),"normal Z");
        rejects(()->Datum.create(Datum.PLANE,"angle","",new double[]{0,0,0,0,0,1,0,0,1,30},null),"axis normal to the base plane is refused");
        Datum three=Datum.create(Datum.PLANE,"points","",new double[]{0,0,5,10,0,5,0,10,5},null);near(Math.abs(three.direction[2]),1,"3-point plane normal");near(three.origin[2],5,"3-point plane origin");
        rejects(()->Datum.create(Datum.PLANE,"points","",new double[]{0,0,0,1,1,1,2,2,2},null),"collinear points refused");
        Datum axis=Datum.create(Datum.AXIS,"points","",new double[]{0,0,0,0,0,4},null);near(axis.direction[2],1,"axis through two points");
        rejects(()->Datum.create(Datum.AXIS,"points","",new double[]{1,1,1,1,1,1},null),"axis with coincident points refused");
        Datum point=Datum.create(Datum.POINT,"coords","",new double[]{1,2,3},"P");check(point.describe().contains("1; 2; 3"),"point description");
        rejects(()->Datum.create(Datum.POINT,"coords","",new double[]{Double.NaN,0,0},null),"non-finite coordinates refused");
        rejects(()->Datum.create("plane","bogus","",new double[3],null),"unknown method refused");
        CadProject p=new CadProject();p.datums.addAll(Arrays.asList(offset,angled,axis,point));angled.hidden=true;
        CadProject back=CadProject.fromJson(new JSONObject(p.toJson().toString()));
        check(back.datums.size()==4&&back.datums.get(1).name.equals("Skos")&&back.datums.get(1).hidden,"construction geometry persists with names and visibility");
        near(back.datums.get(1).direction[1],angled.direction[1],"resolved geometry is recomputed identically after load");
        JSONObject dup=new JSONObject(p.toJson().toString());dup.getJSONArray("construction").put(dup.getJSONArray("construction").get(0));rejects(()->CadProject.fromJson(dup),"duplicate datum ids refused");
        JSONObject bad=new JSONObject(p.toJson().toString());bad.getJSONArray("construction").getJSONObject(2).put("values",new JSONArray(new double[]{0,0,0,0,0,0}));rejects(()->CadProject.fromJson(bad),"degenerate stored datum refused on load");
        check(Datum.nextName(p.datums,Datum.PLANE).equals("Płaszczyzna 3"),"next default name counts the kind");
        List<ModelTree.Row> rows=ModelTree.rows(back,new HashSet<>());int datumRows=0;boolean header=false;for(ModelTree.Row r:rows){if(r.datum())datumRows++;if(r.body==ModelTree.CONSTRUCTION&&!r.datum())header=true;}
        check(header&&datumRows==4,"model tree lists a Konstrukcja group with four items");
        check(ModelTree.rows(back,new HashSet<>(Collections.singleton(ModelTree.CONSTRUCTION))).stream().noneMatch(ModelTree.Row::datum),"collapsed construction group hides its items");

        // Angles.
        GeometryMeasure.Direction top=new GeometryMeasure.Direction(new double[]{0,0,1},false,"g"),side=new GeometryMeasure.Direction(new double[]{1,0,0},false,"s"),bottom=new GeometryMeasure.Direction(new double[]{0,0,-1},false,"d");
        near(GeometryMeasure.angle(top,side),90,"perpendicular faces 90°");near(GeometryMeasure.angle(top,bottom),0,"opposite faces are parallel");near(GeometryMeasure.normalsAngle(top,bottom),180,"opposite outward normals 180°");
        GeometryMeasure.Direction edge=new GeometryMeasure.Direction(new double[]{1,1,0},true,"e"),edge2=new GeometryMeasure.Direction(new double[]{-1,0,0},true,"e2");
        near(GeometryMeasure.angle(edge,edge2),45,"edges at 45° (direction sign ignored)");near(GeometryMeasure.angle(edge,top),0,"edge lying in a plane has 0°");
        near(GeometryMeasure.angle(new GeometryMeasure.Direction(new double[]{0,1,1},true,"x"),top),45,"edge rising 45° from the plane");
        check(GeometryMeasure.describeAngle(top,side).contains("prostopadłe"),"perpendicular is stated");
        double[] row={3,1,250,1,0,0,0,0,0,1,5,-1};check(GeometryMeasure.face(row,2)!=null&&GeometryMeasure.face(row,0)==null,"face rows are looked up by 0-based mesh face");
        check(GeometryMeasure.faceDirection(row).line,"cylinder face measures by its axis");
        check(ToolbarCatalog.allows(false,"Płaszczyzna odsunięta")&&ToolbarCatalog.allows(false,"Zmierz kąt")&&ToolbarCatalog.allows(false,"Pole ściany"),"construction and measurement commands in the solid toolbar");

        // OBJ.
        String obj="# cube\nv 0 0 0\nv 10 0 0\nv 10 10 0\nv 0 10 0\nv 0 0 10\nv 10 0 10\nv 10 10 10\nv 0 10 10\nvt 0 0\nf 1/1 4 3 2\nf 5 6 7 8\nf 1 2 6 5\nf 2//1 3//1 7//1 6//1\nf -5 -1 -2 -6\nf 4 1 5 8\n";
        double[] mesh=CadExchange.readObj(new ByteArrayInputStream(obj.getBytes(StandardCharsets.UTF_8)));
        check(mesh.length==12*13,"six quads become 12 triangles");
        double volume=0;for(int i=0;i<mesh.length;i+=13)volume+=mesh[i]*(mesh[i+4]*mesh[i+8]-mesh[i+5]*mesh[i+7])+mesh[i+1]*(mesh[i+5]*mesh[i+6]-mesh[i+3]*mesh[i+8])+mesh[i+2]*(mesh[i+3]*mesh[i+7]-mesh[i+4]*mesh[i+6]);
        near(Math.abs(volume/6),1000,"OBJ cube encloses 1000 mm³");
        ByteArrayOutputStream stl=new ByteArrayOutputStream();CadExchange.writeBinaryStl(mesh,stl);double[] again=CadExchange.readStl(new ByteArrayInputStream(stl.toByteArray()));
        check(again.length==mesh.length,"OBJ mesh is retained as binary STL");near(again[3],mesh[3],"STL keeps vertex coordinates");
        ByteArrayOutputStream exported=new ByteArrayOutputStream();ObjExporter.write(exported,mesh);double[] round=CadExchange.readObj(new ByteArrayInputStream(exported.toByteArray()));check(round.length==mesh.length,"OBJ export → import round trip");
        rejects(()->CadExchange.readObj(new ByteArrayInputStream("v 0 0 0\nf 1 2 3\n".getBytes())),"index beyond the vertex list refused");
        rejects(()->CadExchange.readObj(new ByteArrayInputStream("v 0 0 0\n".getBytes())),"OBJ without faces refused");
        rejects(()->CadExchange.readObj(new ByteArrayInputStream("v 0 0\nf 1 1 1\n".getBytes())),"incomplete vertex refused");
        System.out.println("PASS ConstructionHostTest: "+checks+" checks");
    }
}
