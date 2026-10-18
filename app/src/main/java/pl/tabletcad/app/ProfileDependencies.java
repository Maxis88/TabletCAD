package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.*;

/** Explicit profile UUID links. Never infers a profile from position or a nearby face. */
public final class ProfileDependencies {
    private ProfileDependencies() {}
    public static double[] replace(double[] original,SketchPrimitive shape,boolean base) {
        if(!shape.closedProfile())throw new IllegalArgumentException("Operacja bryłowa wymaga zamkniętego profilu zwykłego");
        if(ExtrusionPlan.placedBase(original))return ExtrusionPlan.withProfile(original,shape);
        if(Revolve.is(original))return Revolve.withProfile(original,shape);
        int prefix=base?7:16,offset=base?1:2;
        if(original==null||original.length<prefix||original[0]<0)throw new IllegalArgumentException("Operacja nie ma edytowalnego profilu");
        double[] result=Arrays.copyOf(original,prefix);result[0]=shape.featureType();
        double[] coordinates={shape.x1,shape.y1,shape.x2,shape.y2};System.arraycopy(coordinates,0,result,offset,4);
        return shape.appendVertices(result);
    }
    public static boolean uses(CadProject p,String id) {
        return id!=null&&!id.isEmpty()&&(id.equals(p.featureBaseProfileId)||id.equals(p.featureProfileId)||p.committedProfileIds.contains(id));
    }
    /** Updates every consumer while preserving depth, direction, frame, axis and body routing. */
    public static void propagate(CadProject p,String oldId,SketchPrimitive shape) {
        if(!uses(p,oldId))return;
        if(!shape.closedProfile())throw new IllegalArgumentException("Używany profil musi pozostać zamkniętą geometrią zwykłą");
        if(oldId.equals(p.featureBaseProfileId)&&p.featureBase!=null){p.featureBase=replace(p.featureBase,shape,true);p.featureBaseProfileId=shape.id;}
        for(int i=0;i<p.committedProfileIds.size();i++)if(oldId.equals(p.committedProfileIds.get(i))) {
            p.committedFeatures.set(i,replace(p.committedFeatures.get(i),shape,false));p.committedProfileIds.set(i,shape.id);
        }
        if(oldId.equals(p.featureProfileId))p.featureProfileId=shape.id;
    }
    public static List<String> consumers(CadProject p,String id) {
        List<String> result=new ArrayList<>();
        if(id==null||id.isEmpty())return result;// operations without a source profile share nothing
        if(id!=null&&id.equals(p.featureBaseProfileId)&&p.featureBase!=null)result.add("Bryła bazowa · "+(Revolve.is(p.featureBase)?"obrót":"wyciągnięcie"));
        for(int i=0;i<p.committedProfileIds.size();i++)if(id!=null&&id.equals(p.committedProfileIds.get(i)))
            result.add("Operacja "+(i+1)+" · "+(Revolve.is(p.committedFeatures.get(i))?"obrót":FeatureDirection.isCut(p.committedFeatures.get(i)[1])?"wycięcie":"wyciągnięcie"));
        if(id!=null&&id.equals(p.featureProfileId)&&!p.featureOperation.equals("none"))result.add("Aktywna operacja · "+p.featureOperation);
        return result;
    }
    private static boolean same(double[] a,double[] b) {
        if(a==null||b==null)return a==b;if(a.length!=b.length)return false;
        for(int i=0;i<a.length;i++)if(Math.abs(a[i]-b[i])>1e-7)return false;return true;
    }
    private static boolean same(SketchPrimitive a,SketchPrimitive b) {
        if(!a.type.equals(b.type)||!same(a.vertices,b.vertices)||!same(a.curves,b.curves))return false;
        if(a.type.equals("Okrąg"))return Math.hypot(a.x1-b.x1,a.y1-b.y1)<1e-7&&Math.abs(SketchDimensions.length(a)-SketchDimensions.length(b))<1e-7;
        return same(new double[]{a.x1,a.y1,a.x2,a.y2},new double[]{b.x1,b.y1,b.x2,b.y2});
    }
    /** Timeline geometry may be authoritative, but contradictory consumers must not be guessed. */
    public static SketchPrimitive restore(CadProject p,SketchPrimitive source) {
        SketchPrimitive selected=null;
        if(source.id.equals(p.featureBaseProfileId)&&p.featureBase!=null)selected=source.restoreFeature(p.featureBase,1,7);
        for(int i=0;i<p.committedProfileIds.size();i++)if(source.id.equals(p.committedProfileIds.get(i))) {
            SketchPrimitive next=source.restoreFeature(p.committedFeatures.get(i),2,16);
            if(selected!=null&&!same(selected,next))throw new IllegalArgumentException("Operacje mają niezgodny wspólny profil "+source.id+". Nie można jednoznacznie odtworzyć szkicu.");
            selected=next;
        }
        return selected==null?source:selected.withConstruction(source.construction);
    }
}
