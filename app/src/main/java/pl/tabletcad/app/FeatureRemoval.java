package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.*;

/**
 * Removal of any committed history operation, with explicit consequences.
 *
 * <p>Index-based references held by later operations are renumbered: pattern sources
 * and shell face creators. A pattern whose only source is removed is removed with it;
 * a shell face created by the removed operation becomes unresolved and the kernel
 * reports it on rebuild instead of guessing a replacement. Removing an operation that
 * created a body still used by later operations is refused.</p>
 */
public final class FeatureRemoval {
    private FeatureRemoval() {}
    public static final class Plan {
        public final int index;public final List<Integer> removed=new ArrayList<>();public final List<String> consequences=new ArrayList<>();
        Plan(int index){this.index=index;}
    }

    public static Plan plan(CadProject p,int index) {
        if(index<0||index>=p.committedFeatures.size())throw new IllegalArgumentException("Brak operacji do usunięcia");
        Plan plan=new Plan(index);Set<Integer> gone=new TreeSet<>();gone.add(index);
        boolean changed=true;
        while(changed) {
            changed=false;
            for(int i=0;i<p.committedFeatures.size();i++) {
                double[] f=p.committedFeatures.get(i);if(gone.contains(i)||!FeaturePattern.is(f))continue;
                boolean all=true;for(int s:FeaturePattern.sources(f))if(!gone.contains(s))all=false;
                if(all){gone.add(i);changed=true;}
            }
        }
        plan.removed.addAll(gone);
        for(int i:gone)if(i!=index)plan.consequences.add("Zostanie usunięta zależna operacja nr "+(i+1)+" ("+FeaturePattern.label(p.committedFeatures.get(i))+")");
        for(int i=index+1;i<p.committedFeatures.size();i++) {
            double[] f=p.committedFeatures.get(i);if(gone.contains(i))continue;
            if(FeaturePattern.is(f)){for(int s:FeaturePattern.sources(f))if(gone.contains(s)){plan.consequences.add("Operacja nr "+(i+1)+" ("+FeaturePattern.label(f)+") przestanie powielać usuwane operacje");break;}}
            if(ShellFeature.is(f))for(ShellFeature.Face face:ShellFeature.faces(f))if(gone.contains(face.origin)){plan.consequences.add("Powłoka nr "+(i+1)+" utraci wskazaną ścianę – trzeba ją wskazać ponownie");break;}
            if(DraftFeature.is(f))for(int k=0;k<(int)f[1];k++)if(gone.contains((int)f[DraftFeature.PREFIX+k*DraftFeature.FACE+6])){plan.consequences.add("Pochylenie nr "+(i+1)+" utraci wskazaną ścianę – trzeba ją wskazać ponownie");break;}
            if(HoleFeature.is(f)&&gone.contains((int)f[8]))plan.consequences.add("Otwór nr "+(i+1)+" leży na ścianie usuwanej operacji – zostanie sprawdzony przy przeliczeniu");
            if(EdgeFillet.is(f)||EdgeChamfer.is(f))plan.consequences.add("Operacja nr "+(i+1)+" odnosi się do krawędzi; zostanie sprawdzona przy przeliczeniu");
        }
        // A removed body creator must not leave later operations without their target.
        for(int i:gone)if(i<p.featureBodyTargets.size()&&(p.featureBodyTargets.get(i)==0||BodySplit.is(p.committedFeatures.get(i)))) {
            int body=BodySplit.is(p.committedFeatures.get(i))?BodySplit.created(p.committedFeatures.get(i)):p.featureBodyOutputs.get(i);
            for(int j=0;j<p.featureBodyTargets.size();j++)if(!gone.contains(j)&&j!=i&&(p.featureBodyTargets.get(j)==body||(BodyCombine.is(p.committedFeatures.get(j))&&BodyCombine.tool(p.committedFeatures.get(j))==body)))
                throw new IllegalArgumentException("Operacja tworzy bryłę „"+BodyGraph.name(p,body)+"”, której używa operacja nr "+(j+1)+". Usuń najpierw operacje tej bryły.");
        }
        return plan;
    }

    /** Keeps only the operations before {@code index} (for listing the bodies that exist there). */
    public static void truncate(CadProject p,int index) {
        while(p.committedFeatures.size()>index){int last=p.committedFeatures.size()-1;BodyGraph.removeRoute(p,last);p.committedFeatures.remove(last);if(last<p.committedProfileIds.size())p.committedProfileIds.remove(last);}
        p.featureOperation="none";
    }

    /** Applies a plan to the given (candidate) document. */
    public static void apply(CadProject p,Plan plan) {
        List<Integer> descending=new ArrayList<>(plan.removed);descending.sort(Collections.reverseOrder());
        for(int index:descending) {
            for(int i=index+1;i<p.committedFeatures.size();i++)p.committedFeatures.set(i,renumber(p.committedFeatures.get(i),index));
            BodyGraph.removeRoute(p,index);p.committedFeatures.remove(index);
            if(index<p.committedProfileIds.size())p.committedProfileIds.remove(index);
            UserParameters.removed(p,index);
        }
    }

    /** Shifts index references above {@code removed}; drops pattern sources equal to it. */
    static double[] renumber(double[] f,int removed) {
        if(FeaturePattern.is(f)) {
            List<Integer> kept=new ArrayList<>();for(int s:FeaturePattern.sources(f))if(s!=removed)kept.add(s>removed?s-1:s);
            if(kept.isEmpty())return f;// removed together with its last source by the plan
            double[] r=Arrays.copyOf(f,FeaturePattern.PREFIX+kept.size());r[2]=kept.size();for(int i=0;i<kept.size();i++)r[FeaturePattern.PREFIX+i]=kept.get(i);return r;
        }
        if(HoleFeature.is(f)) {
            double[] r=f.clone();r[8]=f[8]==removed?Integer.MAX_VALUE/2:f[8]>removed?f[8]-1:f[8];return r;
        }
        if(ShellFeature.is(f)||DraftFeature.is(f)) {
            double[] r=f.clone();
            for(int i=0;i<(int)f[1];i++){int at=ShellFeature.PREFIX+i*ShellFeature.FACE+6;double o=f[at];
                // An unresolved creator becomes an index no operation can have; the kernel reports it.
                r[at]=o==removed?Integer.MAX_VALUE/2:o>removed?o-1:o;}
            return r;
        }
        return f;
    }
}
