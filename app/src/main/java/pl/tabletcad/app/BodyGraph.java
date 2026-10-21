package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.*;

/** Stable document bodies and explicit operation targets; legacy compounds remain one named root. */
public final class BodyGraph {
    private BodyGraph() {}
    public static void begin(CadProject p) {
        if(p.bodyGraphStart>=0)return;
        p.bodyGraphStart=p.committedFeatures.size();
        while(p.featureBodyTargets.size()<p.committedFeatures.size()){p.featureBodyTargets.add(-1);p.featureBodyOutputs.add(1);}
        reconcile(p);
    }
    public static boolean creates(double[] f){return f.length>=16&&((f[0]>=0&&(((int)f[1]&4)!=0))||SweepLoft.creates(f));}
    public static int allocate(CadProject p) {int id=p.nextBodyIndex++;p.bodyUuids.put(id,UUID.randomUUID().toString());return id;}
    public static void reconcile(CadProject p) {
        while(p.featureBodyTargets.size()>p.committedFeatures.size()){p.featureBodyTargets.remove(p.featureBodyTargets.size()-1);p.featureBodyOutputs.remove(p.featureBodyOutputs.size()-1);}
        while(p.featureBodyTargets.size()<p.committedFeatures.size()) {
            int i=p.featureBodyTargets.size();boolean indexed=p.bodyGraphStart>=0&&i>=p.bodyGraphStart;
            boolean create=indexed&&creates(p.committedFeatures.get(i));int output=create?allocate(p):p.selectedBody;
            p.featureBodyTargets.add(indexed?(create?0:p.selectedBody):-1);p.featureBodyOutputs.add(indexed?output:1);
        }
        if(p.bodyGraphStart>=0&&!p.featureOperation.equals("none")) {
            if(p.activeBodyTarget<1)p.activeBodyTarget=p.selectedBody;
            if(p.featureOperation.equals("new")&&p.activeBodyOutput<1)p.activeBodyOutput=allocate(p);
            if(!p.featureOperation.equals("new"))p.activeBodyOutput=p.activeBodyTarget;
        }
    }
    public static void removeRoute(CadProject p,int index){Map<Integer,double[]> shifted=new LinkedHashMap<>();for(Map.Entry<Integer,double[]> e:p.extrusionExtents.entrySet())if(e.getKey()!=index)shifted.put(e.getKey()>index?e.getKey()-1:e.getKey(),e.getValue());p.extrusionExtents.clear();p.extrusionExtents.putAll(shifted);if(index<p.featureBodyTargets.size()){p.featureBodyTargets.remove(index);p.featureBodyOutputs.remove(index);}if(p.bodyGraphStart>index)p.bodyGraphStart--;}
    public static LinkedHashSet<Integer> bodies(CadProject p) {
        LinkedHashSet<Integer> ids=new LinkedHashSet<>();if(p.hasSolid())ids.add(1);
        for(int i=0;i<p.featureBodyOutputs.size();i++){if(p.featureBodyTargets.get(i)==0)ids.add(p.featureBodyOutputs.get(i));if(i<p.committedFeatures.size()&&BodyCombine.consumes(p.committedFeatures.get(i)))ids.remove(BodyCombine.tool(p.committedFeatures.get(i)));if(i<p.committedFeatures.size()&&createdBody(p.committedFeatures.get(i))>0)ids.add(createdBody(p.committedFeatures.get(i)));}
        if(p.featureOperation.equals("new")&&p.activeBodyOutput>0)ids.add(p.activeBodyOutput);return ids;
    }
    /** Body created by a routed record (split part, instance), or 0. */
    public static int createdBody(double[] f){return BodySplit.is(f)?BodySplit.created(f):SurfaceSplit.is(f)?SurfaceSplit.created(f):SurfaceSplit.isLoft(f)?(int)f[1]:BodyInstance.is(f)?BodyInstance.created(f):0;}
    public static String name(CadProject p,int id){return p.bodyNames.getOrDefault(id,"Bryła "+id);}
    public static int profileBody(CadProject p,String profile) {
        if(profile==null)return 0;if(profile.equals(p.featureBaseProfileId))return 1;
        for(int i=0;i<p.committedProfileIds.size();i++)if(profile.equals(p.committedProfileIds.get(i)))return i<p.featureBodyOutputs.size()?p.featureBodyOutputs.get(i):1;
        if(profile.equals(p.featureProfileId)&&!p.featureOperation.equals("none"))return p.activeBodyOutput>0?p.activeBodyOutput:1;return 0;
    }
    private static int recordLength(double[] packed,int offset) {
        if(packed.length-offset<16)throw new IllegalArgumentException("Niekompletna operacja");double type=packed[offset];
        if(type==-9)return 16+(int)packed[offset+3];
        if(type==FeatureHistory.PLACEHOLDER)return 16;
        if(type==-6||type==-7){int end=offset+16;for(int i=0;i<(int)packed[offset+1];i++){if(end>=packed.length)throw new IllegalArgumentException("Niekompletna grupa");end+=1+(int)packed[end];}return end-offset;}
        if(type==-4||type==-5)return 17+(int)packed[offset+16];
        if(type==FeaturePattern.TYPE)return 16+(int)packed[offset+2];
        if(type==ShellFeature.TYPE)return 16+(int)packed[offset+1]*ShellFeature.FACE;
        if(type==DraftFeature.TYPE)return 16+(int)packed[offset+1]*DraftFeature.FACE;
        if(type==-3){double t=packed[offset+20];return 22+(t>=1001?((int)t-1000)*7:t>=5?((int)t-2)*2:0);}
        if(type==SweepLoft.LOFT||type==SweepLoft.SWEEP||type==SweepLoft.BODY_PATTERN)return SweepLoft.length(packed,offset);
        if(type==MateFeature.TYPE)return MateFeature.LENGTH;
        if(type==SurfaceSplit.TYPE)return SurfaceSplit.length(packed,offset);
        if(type==SketchText.TYPE)return SketchText.length(packed,offset);
        if(type==SurfaceSplit.LOFT_TYPE)return SurfaceSplit.loftLength(packed,offset);
        if(type==2000)return 17;
        return 16+(type>=1001?((int)type-1000)*7:type>=5?((int)type-2)*2:0);
    }
    public static double[] pack(CadProject p,double[] raw) {
        if(p.bodyGraphStart<0){for(double[] f:p.committedFeatures)if(FeaturePattern.is(f)||ShellFeature.is(f)||SweepLoft.loft(f)||SweepLoft.sweep(f)||SweepLoft.bodyPattern(f)||MateFeature.is(f))throw new IllegalArgumentException("Szyk i powłoka wymagają grafu brył");return raw.clone();}
        List<double[]> records=new ArrayList<>();int length=0,index=0;
        for(int offset=0;offset<raw.length;index++) {
            int size=recordLength(raw,offset);if(size<16||size>raw.length-offset)throw new IllegalArgumentException("Niepoprawna długość operacji");
            double[] record=Arrays.copyOfRange(raw,offset,offset+size);offset+=size;
            // Suppressed operations, operations past the rollback marker and patterns of inactive sources keep their index as placeholders.
            if(index<p.committedFeatures.size()&&FeatureHistory.inactive(p,index)){records.add(FeatureHistory.placeholder());length+=16;continue;}
            // Loft/sweep geometry is refreshed from the referenced sketch elements before every evaluation.
            if(SurfaceSplit.isLoft(record)&&index<p.committedFeatures.size())record=SurfaceSplit.refreshLoft(p,record,index<p.committedProfileIds.size()?p.committedProfileIds.get(index):null);
            if(SketchText.is(record)&&index<p.committedFeatures.size())record=SketchText.refresh(p,record,index<p.committedProfileIds.size()?p.committedProfileIds.get(index):null);
            if(SurfaceSplit.is(record)&&index<p.committedFeatures.size())record=SurfaceSplit.refresh(p,record,index<p.committedProfileIds.size()?p.committedProfileIds.get(index):null);
            if((SweepLoft.loft(record)||SweepLoft.sweep(record))&&index<p.committedFeatures.size())record=SweepLoft.refresh(p,record,index<p.committedProfileIds.size()?p.committedProfileIds.get(index):null);
            // Patterns are expanded here, from the current source operations, into one routed record.
            if(FeaturePattern.is(record)) {
                if(index>=p.committedFeatures.size()||index<p.bodyGraphStart)throw new IllegalArgumentException("Szyk musi być zatwierdzoną operacją grafu brył");
                record=FeaturePattern.expand(p.committedFeatures,index);
                double[] wrapped=new double[16+record.length];wrapped[0]=-9;wrapped[1]=p.featureBodyTargets.get(index);wrapped[2]=p.featureBodyOutputs.get(index);wrapped[3]=record.length;System.arraycopy(record,0,wrapped,16,record.length);
                records.add(wrapped);length+=wrapped.length;continue;
            }
            if(record[0]!=-9&&index>=p.bodyGraphStart) {
                int target,output;
                if(index<p.featureBodyTargets.size()){target=p.featureBodyTargets.get(index);output=p.featureBodyOutputs.get(index);}
                else {target=creates(record)?0:p.activeBodyTarget>0?p.activeBodyTarget:p.selectedBody;output=target==0?(p.activeBodyOutput>0?p.activeBodyOutput:p.nextBodyIndex):target;}
                double[] wrapped=new double[16+record.length];wrapped[0]=-9;wrapped[1]=target;wrapped[2]=output;wrapped[3]=record.length;System.arraycopy(record,0,wrapped,16,record.length);record=wrapped;
            }
            records.add(record);length+=record.length;
        }
        double[] result=new double[length];int at=0;for(double[] record:records){System.arraycopy(record,0,result,at,record.length);at+=record.length;}return result;
    }
    public static void validate(CadProject p) {
        if(p.bodyGraphStart<-1||p.bodyGraphStart>p.committedFeatures.size()||p.featureBodyTargets.size()!=p.featureBodyOutputs.size()||p.featureBodyTargets.size()>p.committedFeatures.size())throw new IllegalArgumentException("Niepoprawny graf brył");
        Set<Integer> ids=new HashSet<>();ids.add(1);int max=1;
        for(int i=0;i<p.featureBodyTargets.size();i++) {
            int target=p.featureBodyTargets.get(i),output=p.featureBodyOutputs.get(i);
            if(target==-1){if(p.bodyGraphStart>=0&&i>=p.bodyGraphStart)throw new IllegalArgumentException("Brak celu operacji");if(FeaturePattern.is(p.committedFeatures.get(i))||ShellFeature.is(p.committedFeatures.get(i))||BodyCombine.is(p.committedFeatures.get(i))||createdBody(p.committedFeatures.get(i))>0)throw new IllegalArgumentException("Szyk i powłoka wymagają celu w grafie brył");continue;}
            if((FeaturePattern.is(p.committedFeatures.get(i))||ShellFeature.is(p.committedFeatures.get(i)))&&target<1)throw new IllegalArgumentException("Szyk i powłoka nie tworzą nowej bryły");
            if(target==0){if(!creates(p.committedFeatures.get(i))||ids.contains(output)||output<2)throw new IllegalArgumentException("Powtórzona bryła");ids.add(output);max=Math.max(max,output);}
            else if(!ids.contains(target)||target!=output)throw new IllegalArgumentException("Operacja odwołuje się do nieistniejącej bryły");
            double[] combine=p.committedFeatures.get(i);
            if(BodyCombine.is(combine)) {
                int tool=BodyCombine.tool(combine);
                if(target<1||tool==target||!ids.contains(tool))throw new IllegalArgumentException("Operacja na bryłach odwołuje się do nieistniejącej bryły");
                if(BodyCombine.consumes(combine))ids.remove(tool);
            }
            if(MateFeature.is(combine)){int other=MateFeature.other(combine);if(target<1||other==target||!ids.contains(other))throw new IllegalArgumentException("Dosunięcie odwołuje się do nieistniejącej bryły");}
            if(createdBody(combine)>0) {
                int created=createdBody(combine);
                if(target<1||ids.contains(created)||created<2||!p.bodyUuids.containsKey(created))throw new IllegalArgumentException("Podział tworzy bryłę o powtórzonym identyfikatorze");
                for(int j=0;j<i;j++)if(p.featureBodyOutputs.get(j)==created)throw new IllegalArgumentException("Podział tworzy bryłę o powtórzonym identyfikatorze");
                ids.add(created);max=Math.max(max,created);
            }
            if(!p.bodyUuids.containsKey(output))throw new IllegalArgumentException("Brak UUID bryły");
        }
        if(p.activeBodyOutput>0&&p.featureOperation.equals("new")){if(ids.contains(p.activeBodyOutput))throw new IllegalArgumentException("Powtórzone UUID operacji aktywnej");max=Math.max(max,p.activeBodyOutput);}
        if(p.nextBodyIndex<=max)throw new IllegalArgumentException("Niepoprawny licznik brył");
        for(String uuid:p.bodyUuids.values())UUID.fromString(uuid);
    }
}
