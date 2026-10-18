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
    public static boolean creates(double[] f){return f.length>=16&&f[0]>=0&&(((int)f[1]&4)!=0);}
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
        for(int i=0;i<p.featureBodyOutputs.size();i++)if(p.featureBodyTargets.get(i)==0)ids.add(p.featureBodyOutputs.get(i));
        if(p.featureOperation.equals("new")&&p.activeBodyOutput>0)ids.add(p.activeBodyOutput);return ids;
    }
    public static String name(CadProject p,int id){return p.bodyNames.getOrDefault(id,"Bryła "+id);}
    public static int profileBody(CadProject p,String profile) {
        if(profile==null)return 0;if(profile.equals(p.featureBaseProfileId))return 1;
        for(int i=0;i<p.committedProfileIds.size();i++)if(profile.equals(p.committedProfileIds.get(i)))return i<p.featureBodyOutputs.size()?p.featureBodyOutputs.get(i):1;
        if(profile.equals(p.featureProfileId)&&!p.featureOperation.equals("none"))return p.activeBodyOutput>0?p.activeBodyOutput:1;return 0;
    }
    private static int recordLength(double[] packed,int offset) {
        if(packed.length-offset<16)throw new IllegalArgumentException("Niekompletna operacja");double type=packed[offset];
        if(type==-9)return 16+(int)packed[offset+3];
        if(type==-6||type==-7){int end=offset+16;for(int i=0;i<(int)packed[offset+1];i++){if(end>=packed.length)throw new IllegalArgumentException("Niekompletna grupa");end+=1+(int)packed[end];}return end-offset;}
        if(type==-4||type==-5)return 17+(int)packed[offset+16];
        if(type==-3){double t=packed[offset+20];return 22+(t>=1001?((int)t-1000)*7:t>=5?((int)t-2)*2:0);}
        return 16+(type>=1001?((int)type-1000)*7:type>=5?((int)type-2)*2:0);
    }
    public static double[] pack(CadProject p,double[] raw) {
        if(p.bodyGraphStart<0)return raw.clone();List<double[]> records=new ArrayList<>();int length=0,index=0;
        for(int offset=0;offset<raw.length;index++) {
            int size=recordLength(raw,offset);if(size<16||size>raw.length-offset)throw new IllegalArgumentException("Niepoprawna długość operacji");
            double[] record=Arrays.copyOfRange(raw,offset,offset+size);offset+=size;
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
            if(target==-1){if(p.bodyGraphStart>=0&&i>=p.bodyGraphStart)throw new IllegalArgumentException("Brak celu operacji");continue;}
            if(target==0){if(!creates(p.committedFeatures.get(i))||ids.contains(output)||output<2)throw new IllegalArgumentException("Powtórzona bryła");ids.add(output);max=Math.max(max,output);}
            else if(!ids.contains(target)||target!=output)throw new IllegalArgumentException("Operacja odwołuje się do nieistniejącej bryły");
            if(!p.bodyUuids.containsKey(output))throw new IllegalArgumentException("Brak UUID bryły");
        }
        if(p.activeBodyOutput>0&&p.featureOperation.equals("new")){if(ids.contains(p.activeBodyOutput))throw new IllegalArgumentException("Powtórzone UUID operacji aktywnej");max=Math.max(max,p.activeBodyOutput);}
        if(p.nextBodyIndex<=max)throw new IllegalArgumentException("Niepoprawny licznik brył");
        for(String uuid:p.bodyUuids.values())UUID.fromString(uuid);
    }
}
