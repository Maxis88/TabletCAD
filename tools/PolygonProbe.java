import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class PolygonProbe {
    static void require(boolean yes,String label){if(!yes)throw new AssertionError(label);System.out.println("PASS "+label);}
    static double volume(double[] m) {
        require(m.length>0,"nonempty OCCT mesh");double sum=0;
        for(int i=0;i<m.length;i+=13) sum+=m[i]*(m[i+4]*m[i+8]-m[i+5]*m[i+7])
            +m[i+1]*(m[i+5]*m[i+6]-m[i+3]*m[i+8])+m[i+2]*(m[i+3]*m[i+7]-m[i+4]*m[i+6]);
        return Math.abs(sum/6);
    }
    static void close(double a,double b){require(Math.abs(a-b)<.01,"volume "+a+" expected "+b);}
    static SketchPrimitive line(String id,double x,double y,double u,double v){return new SketchPrimitive(id,"Linia",x,y,u,v);}
    static void invalid(List<SketchPrimitive> lines,String label) {
        try{SketchContours.join(lines,null);throw new AssertionError(label);}catch(IllegalArgumentException good){System.out.println("PASS reject "+label);}
    }
    public static void main(String[] args)throws Exception {
        List<SketchPrimitive> triangle=Arrays.asList(line("a",10,10,30,10),line("c",10,30,10,10),line("b",10,30,30,10));
        SketchContours.Result result=SketchContours.join(triangle,"b");SketchPrimitive p=result.profile;
        require(result.consumed.size()==3&&p.vertices.length==6,"unordered reversed lines form triangle");
        invalid(triangle.subList(0,2),"open chain");
        List<SketchPrimitive> branched=new ArrayList<>(triangle);branched.add(line("d",10,10,0,0));invalid(branched,"branch");
        invalid(Arrays.asList(line("a",0,0,20,20),line("b",20,20,0,20),line("c",0,20,20,0),line("d",20,0,0,0)),"self intersection");
        List<SketchPrimitive> helpers=new ArrayList<>(triangle);helpers.add(new SketchPrimitive("guide","Pomocnicza",10,10,80,80));
        require(SketchContours.join(helpers,null).consumed.size()==3,"construction ignored");
        CadProject doc=new CadProject();doc.sketch.add(p);doc.sketchExtruded=true;doc.featureBaseProfileId=p.id;
        doc.featureBase=p.appendVertices(new double[]{p.featureType(),p.x1,p.y1,p.x2,p.y2,0,10});
        CadProject restored=CadProject.fromJson(doc.toJson());
        require(restored.sketch.get(0).vertices.length==6&&restored.featureBase.length==13,"schema15 polygon base round trip");
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,doc.featureBase,new double[0],args[0])),2000);
        close(volume(NativeCadEngine.tessellateStep(args[0])),2000);
        for(int plane=1;plane<=2;plane++) {
            double[] base=doc.featureBase.clone();base[5]=plane;
            close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,base,new double[0],null)),2000);
        }
        double[] add=p.appendVertices(new double[]{p.featureType(),0,p.x1,p.y1,p.x2,p.y2,10,0,0,32,1,0,0,0,1,0});
        double[] cut=p.appendVertices(new double[]{p.featureType(),1,p.x1,p.y1,p.x2,p.y2,5,0,0,42,1,0,0,0,1,0});
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,add,null)),124880);
        double[] side=add.clone();side[7]=80;side[8]=0;side[9]=0;
        side[10]=0;side[11]=1;side[12]=0;side[13]=0;side[14]=0;side[15]=1;
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,side,null)),124880);
        double[] chain=new double[add.length+cut.length];System.arraycopy(add,0,chain,0,add.length);System.arraycopy(cut,0,chain,add.length,cut.length);
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,null,chain,null)),123880);
        doc.committedFeatures.add(add);doc.committedProfileIds.add(p.id);
        require(CadProject.fromJson(doc.toJson()).committedFeatures.get(0).length==22,"variable feature history round trip");
        double[] concave={0,0,30,0,30,10,10,10,10,30,0,30};SketchContours.validate(concave);
        SketchPrimitive l=new SketchPrimitive("L",concave);
        close(volume(NativeCadEngine.evaluateFeatures(80,48,32,0,l.appendVertices(new double[]{l.featureType(),0,0,30,30,0,10}),new double[0],null)),5000);
        double[] bad=Arrays.copyOf(add,add.length-1);
        require(NativeCadEngine.evaluateFeatures(80,48,32,0,null,bad,null).length==0,"truncated native polygon rejected");
        System.out.println("ALL POLYGON TESTS PASSED");
    }
}
