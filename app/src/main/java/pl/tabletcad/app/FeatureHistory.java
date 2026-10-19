package pl.tabletcad.app;

import java.util.*;
import java.util.function.IntUnaryOperator;
import pl.tabletcad.app.model.*;

/**
 * History management beyond editing and removal: suppression, reordering and the rollback marker.
 *
 * <p>A suppressed operation (and every operation past the rollback marker) is sent to the kernel as a
 * 16-value placeholder record ({@link #PLACEHOLDER}); it keeps its history index, so face creators,
 * pattern sources and hole faces of other operations keep their meaning. A pattern whose source is
 * inactive is inactive too. Reordering swaps neighbours and renumbers every index reference; the
 * candidate is validated (body graph, pattern order, face creators before users) and then evaluated by
 * the kernel like any other change.</p>
 */
public final class FeatureHistory {
    private FeatureHistory() {}
    public static final double PLACEHOLDER=-22;
    public static double[] placeholder(){double[] r=new double[16];r[0]=PLACEHOLDER;return r;}

    /** True when the operation is not evaluated (suppressed, after the marker or patterning an inactive source). */
    public static boolean inactive(CadProject p,int index) {
        if(p.historyMarker>=0&&index>=p.historyMarker&&index<p.historyMarkerCount)return true;
        if(p.suppressedFeatures.contains(index))return true;
        if(index<p.committedFeatures.size()&&FeaturePattern.is(p.committedFeatures.get(index)))
            for(int s:FeaturePattern.sources(p.committedFeatures.get(index)))if(s!=index&&s>=0&&s<index&&inactive(p,s))return true;
        return false;
    }

    /** Operations that use a body created by {@code index}; suppressing it would leave them without input. */
    public static List<Integer> bodyUsers(CadProject p,int index) {
        List<Integer> users=new ArrayList<>();if(index>=p.featureBodyTargets.size())return users;
        double[] f=p.committedFeatures.get(index);int body;
        if(BodyGraph.createdBody(f)>0)body=BodyGraph.createdBody(f);else if(p.featureBodyTargets.get(index)==0)body=p.featureBodyOutputs.get(index);else return users;
        for(int j=index+1;j<p.featureBodyTargets.size();j++)
            if(p.featureBodyTargets.get(j)==body||(BodyCombine.is(p.committedFeatures.get(j))&&BodyCombine.tool(p.committedFeatures.get(j))==body))users.add(j);
        return users;
    }
    public static void setSuppressed(CadProject p,int index,boolean suppressed) {
        if(index<0||index>=p.committedFeatures.size())throw new IllegalArgumentException("Brak operacji");
        if(suppressed) {
            for(int user:bodyUsers(p,index))if(!p.suppressedFeatures.contains(user))
                throw new IllegalArgumentException("Operacja tworzy bryłę używaną przez operację nr "+(user+1)+". Najpierw wygaś tamte operacje.");
            p.suppressedFeatures.add(index);
        } else p.suppressedFeatures.remove(index);
    }

    /** Applies an index mapping to every index reference inside a record. */
    static double[] remap(double[] f,IntUnaryOperator map) {
        double[] r=f.clone();
        if(FeaturePattern.is(f)){for(int i=0;i<(int)f[2];i++)r[FeaturePattern.PREFIX+i]=map.applyAsInt((int)f[FeaturePattern.PREFIX+i]);return r;}
        if(HoleFeature.is(f)){if(f[8]>=0&&f[8]<Integer.MAX_VALUE/4)r[8]=map.applyAsInt((int)f[8]);return r;}
        if(ShellFeature.is(f)||DraftFeature.is(f)) {
            for(int i=0;i<(int)f[1];i++){int at=ShellFeature.PREFIX+i*ShellFeature.FACE+6;if(f[at]>=0&&f[at]<Integer.MAX_VALUE/4)r[at]=map.applyAsInt((int)f[at]);}
            return r;
        }
        return r;
    }
    /** Index references held by a record (pattern sources, face creators). */
    static List<Integer> references(double[] f) {
        List<Integer> r=new ArrayList<>();
        if(FeaturePattern.is(f))for(int s:FeaturePattern.sources(f))r.add(s);
        else if(HoleFeature.is(f)){if(f[8]>=0&&f[8]<Integer.MAX_VALUE/4)r.add((int)f[8]);}
        else if(ShellFeature.is(f)||DraftFeature.is(f))for(int i=0;i<(int)f[1];i++){double o=f[ShellFeature.PREFIX+i*ShellFeature.FACE+6];if(o>=0&&o<Integer.MAX_VALUE/4)r.add((int)o);}
        return r;
    }

    /** Swaps operations {@code index} and {@code index + 1} in place, with every reference renumbered. */
    public static void swap(CadProject p,int index) {
        int a=index,b=index+1;
        if(a<0||b>=p.committedFeatures.size())throw new IllegalArgumentException("Brak sąsiedniej operacji");
        if(p.bodyGraphStart<0||a<p.bodyGraphStart)throw new IllegalArgumentException("Operacje sprzed grafu brył (starsze dokumenty) mają stałą kolejność");
        double[] first=p.committedFeatures.get(a),second=p.committedFeatures.get(b);
        if(references(second).contains(a))throw new IllegalArgumentException("Operacja nr "+(b+1)+" odwołuje się do operacji nr "+(a+1)+" (źródło szyku albo ściana) i musi zostać po niej");
        IntUnaryOperator map=i->i==a?b:i==b?a:i;
        for(int i=0;i<p.committedFeatures.size();i++)p.committedFeatures.set(i,remap(p.committedFeatures.get(i),map));
        Collections.swap(p.committedFeatures,a,b);
        if(b<p.committedProfileIds.size())Collections.swap(p.committedProfileIds,a,b);
        if(b<p.featureBodyTargets.size()){Collections.swap(p.featureBodyTargets,a,b);Collections.swap(p.featureBodyOutputs,a,b);}
        Set<Integer> suppressed=new LinkedHashSet<>();for(int s:p.suppressedFeatures)suppressed.add(map.applyAsInt(s));p.suppressedFeatures.clear();p.suppressedFeatures.addAll(suppressed);
        Map<Integer,double[]> extents=new LinkedHashMap<>();for(Map.Entry<Integer,double[]> e:p.extrusionExtents.entrySet())extents.put(e.getKey()<0?e.getKey():map.applyAsInt(e.getKey()),e.getValue());p.extrusionExtents.clear();p.extrusionExtents.putAll(extents);
        Map<String,String> bindings=new LinkedHashMap<>();
        for(Map.Entry<String,String> e:p.parameterBindings.entrySet()) {
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(depth|hole|pattern):(\\d+)(.*)$").matcher(e.getKey());
            bindings.put(m.matches()?m.group(1)+":"+map.applyAsInt(Integer.parseInt(m.group(2)))+m.group(3):e.getKey(),e.getValue());
        }
        p.parameterBindings.clear();p.parameterBindings.putAll(bindings);
        HoleAssociation.remap(p,map);
        // The moved operations must still see their references before them and their bodies must exist.
        for(int i=0;i<p.committedFeatures.size();i++)for(int r:references(p.committedFeatures.get(i)))if(r>=i)throw new IllegalArgumentException("Nowa kolejność odwraca zależność operacji nr "+(i+1));
        BodyGraph.validate(p);
    }
    /** Keeps the suppressed set consistent after a removal at {@code removed}. */
    public static void removed(CadProject p,int removed) {
        Set<Integer> next=new LinkedHashSet<>();for(int s:p.suppressedFeatures)if(s!=removed)next.add(s>removed?s-1:s);
        p.suppressedFeatures.clear();p.suppressedFeatures.addAll(next);
        if(p.historyMarker>=0){if(removed<p.historyMarker)p.historyMarker--;if(removed<p.historyMarkerCount)p.historyMarkerCount--;if(p.historyMarker>=p.historyMarkerCount){p.historyMarker=-1;p.historyMarkerCount=-1;}}
    }
    /** Places the rollback marker after {@code count} operations (count ≥ size: end of history). */
    public static void setMarker(CadProject p,int count) {
        if(count<0)throw new IllegalArgumentException("Niepoprawne położenie znacznika");
        if(count>=p.committedFeatures.size()){p.historyMarker=-1;p.historyMarkerCount=-1;}else{p.historyMarker=count;p.historyMarkerCount=p.committedFeatures.size();}
    }
    /**
     * Operations appended while the marker is set are evaluated right after the operations before the marker
     * (the ones in between are placeholders), so moving them to the marker does not change the geometry; this
     * makes the inserted position permanent and advances the marker behind them.
     */
    public static void settle(CadProject p) {
        if(p.historyMarker<0)return;
        if(p.historyMarkerCount>p.committedFeatures.size())throw new IllegalArgumentException("Historia jest krótsza niż znacznik");
        while(p.historyMarkerCount<p.committedFeatures.size()) {
            for(int k=p.historyMarkerCount-1;k>=p.historyMarker;k--)swap(p,k);
            p.historyMarker++;p.historyMarkerCount++;
        }
        if(p.historyMarker>=p.historyMarkerCount){p.historyMarker=-1;p.historyMarkerCount=-1;}
    }
}
