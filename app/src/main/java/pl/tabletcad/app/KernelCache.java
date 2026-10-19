package pl.tabletcad.app;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Memo of pure kernel queries. The OCCT evaluation is deterministic in its inputs (base box,
 * history records, imported STEP file), so a repeated query — rebuilding the unchanged solid
 * after each edit of a sketch draft, cancelling a sketch, reopening the same edge list —
 * returns the stored result instead of rebuilding the model. Failed (empty) results are never
 * stored, so failure diagnostics still come from a real evaluation. Results are copied on the
 * way in and out so callers cannot alter the memo.
 */
public final class KernelCache {
    private KernelCache() {}
    /** Upper bound of stored doubles (~32 MB). */
    static final long LIMIT=4_000_000;
    private static final class Key {
        final int mode;final double w,h,d,r;final double[] base,features;final String source;final long sourceLength,sourceTime;final int hash;
        Key(int mode,double w,double h,double d,double r,double[] base,double[] features,String source) {
            this.mode=mode;this.w=w;this.h=h;this.d=d;this.r=r;this.base=base==null?null:base.clone();this.features=features==null?null:features.clone();this.source=source;
            File f=source==null?null:new File(source);sourceLength=f==null?0:f.length();sourceTime=f==null?0:f.lastModified();
            hash=java.util.Objects.hash(mode,w,h,d,r,Arrays.hashCode(this.base),Arrays.hashCode(this.features),source,sourceLength,sourceTime);
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object o) {
            if(!(o instanceof Key))return false;Key k=(Key)o;
            return mode==k.mode&&Double.compare(w,k.w)==0&&Double.compare(h,k.h)==0&&Double.compare(d,k.d)==0&&Double.compare(r,k.r)==0
                &&Arrays.equals(base,k.base)&&Arrays.equals(features,k.features)&&java.util.Objects.equals(source,k.source)&&sourceLength==k.sourceLength&&sourceTime==k.sourceTime;
        }
    }
    private static final LinkedHashMap<Key,double[]> MEMO=new LinkedHashMap<>(16,.75f,true);
    private static long stored;
    private static long hits,misses;

    /** Kernel call for the given inputs. */
    public interface Query {double[] run();}

    public static double[] get(int mode,double w,double h,double d,double r,double[] base,double[] features,String source,Query query) {
        Key key=new Key(mode,w,h,d,r,base,features,source);
        synchronized(MEMO) {
            double[] found=MEMO.get(key);
            if(found!=null){hits++;return found.clone();}
            misses++;
        }
        double[] result=query.run();
        if(result==null||result.length==0||result.length>LIMIT/2)return result;
        synchronized(MEMO) {
            double[] previous=MEMO.put(key,result.clone());
            if(previous!=null)stored-=previous.length;stored+=result.length;
            Iterator<Map.Entry<Key,double[]>> oldest=MEMO.entrySet().iterator();
            while(stored>LIMIT&&oldest.hasNext()){Map.Entry<Key,double[]> e=oldest.next();stored-=e.getValue().length;oldest.remove();}
        }
        return result;
    }
    public static void clear(){synchronized(MEMO){MEMO.clear();stored=0;}}
    public static long hits(){synchronized(MEMO){return hits;}}
    public static long misses(){synchronized(MEMO){return misses;}}
    public static int size(){synchronized(MEMO){return MEMO.size();}}
}
