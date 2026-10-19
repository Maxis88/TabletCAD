package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.CadProject;

/** One authoritative mapping for drawing and activating model-history entries. */
public final class CadTimeline {
    private CadTimeline() {}
    public enum Kind { START, IMPORT, BASE, FEATURE, SKETCH, ACTIVE, FILLET }
    public static final class Item {
        public final Kind kind;
        public final String title;
        public final int featureIndex;
        Item(Kind kind,String title,int index){this.kind=kind;this.title=title;this.featureIndex=index;}
    }
    public static List<Item> entries(CadProject p) {
        List<Item> result=new ArrayList<>();result.add(new Item(Kind.START,"Początek",-1));
        if(p.importedMeshSource!=null)result.add(new Item(Kind.IMPORT,"Import STL",-1));
        else if(p.importedStep)result.add(new Item(Kind.IMPORT,"Import STEP",-1));
        else if(p.hasSolid())result.add(new Item(Kind.BASE,Revolve.is(p.featureBase)?"Obrót profilu":p.featureBase!=null||p.sketchExtruded?"Wyciągnięcie":"Bryła bazowa",-1));
        for(int i=0;i<p.committedFeatures.size();i++)result.add(new Item(Kind.FEATURE,FeaturePattern.is(p.committedFeatures.get(i))?FeaturePattern.label(p.committedFeatures.get(i)):ShellFeature.is(p.committedFeatures.get(i))?"Powłoka":HoleFeature.is(p.committedFeatures.get(i))?"Otwór":BodyCombine.is(p.committedFeatures.get(i))?BodyCombine.label(p.committedFeatures.get(i)):DraftFeature.is(p.committedFeatures.get(i))?"Pochylenie":BodySplit.is(p.committedFeatures.get(i))?"Podział bryły":BodyInstance.is(p.committedFeatures.get(i))?"Wystąpienie":FaceOffset.is(p.committedFeatures.get(i))?"Odsunięcie ściany":SweepLoft.loft(p.committedFeatures.get(i))||SweepLoft.sweep(p.committedFeatures.get(i))||SweepLoft.bodyPattern(p.committedFeatures.get(i))?SweepLoft.label(p.committedFeatures.get(i)):BodyTransform.is(p.committedFeatures.get(i))?BodyTransform.label(p.committedFeatures.get(i)):HoleThread.is(p.committedFeatures.get(i))?"Gwint":Revolve.is(p.committedFeatures.get(i))?(p.committedFeatures.get(i)[1]==1?"Wycięcie obrotowe":"Obrót profilu"):EdgeChamfer.is(p.committedFeatures.get(i))?"Fazowanie":EdgeFillet.is(p.committedFeatures.get(i))?"Zaokrąglenie":FeatureDirection.label(p.committedFeatures.get(i)[1]),i));
        if(!p.sketch.isEmpty()||!p.savedSketches.isEmpty())result.add(new Item(Kind.SKETCH,p.sketch.isEmpty()?"Szkice":"Szkic",-1));
        if(!p.featureOperation.equals("none"))result.add(new Item(Kind.ACTIVE,FeatureDirection.label(FeatureDirection.featureCode(p.featureOperation,p.featureSymmetric)),-1));
        if(p.filletRadius>0)result.add(new Item(Kind.FILLET,"R"+String.format(Locale.US,"%.1f",p.filletRadius),-1));
        return result;
    }
}
