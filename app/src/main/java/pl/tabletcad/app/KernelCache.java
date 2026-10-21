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
        // Second level: results saved on disk by earlier sessions (opening a project with threads
        // no longer re-runs every helical cut). Keyed by a SHA-256 of all inputs and the app build.
        File file=diskFile(key);
        if(file!=null&&file.isFile()){double[] loaded=read(file);if(loaded!=null&&loaded.length>0){file.setLastModified(System.currentTimeMillis());remember(key,loaded);return loaded.clone();}}
        double[] result=query.run();
        if(result==null||result.length==0||result.length>LIMIT/2)return result;
        if(file!=null)write(file,result);
        remember(key,result);
        return result;
    }
    private static void remember(Key key,double[] result) {
        synchronized(MEMO) {
            double[] previous=MEMO.put(key,result.clone());
            if(previous!=null)stored-=previous.length;stored+=result.length;
            Iterator<Map.Entry<Key,double[]>> oldest=MEMO.entrySet().iterator();
            while(stored>LIMIT&&oldest.hasNext()){Map.Entry<Key,double[]> e=oldest.next();stored-=e.getValue().length;oldest.remove();}
        }
    }
    private static volatile File disk;private static volatile String build="";
    /** Enables the disk level (app cache folder); build identifies the kernel version. */
    public static void setDisk(File folder,String buildId){disk=folder;build=buildId==null?"":buildId;if(folder!=null)folder.mkdirs();}
    static final long DISK_LIMIT=300L*1024*1024;
    private static File diskFile(Key k) {
        File folder=disk;if(folder==null)return null;
        try {
            java.security.MessageDigest sha=java.security.MessageDigest.getInstance("SHA-256");java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(8);
            java.util.function.DoubleConsumer put=v->{b.clear();b.putLong(Double.doubleToLongBits(v));sha.update(b.array());};
            sha.update(build.getBytes(java.nio.charset.StandardCharsets.UTF_8));put.accept(k.mode);put.accept(k.w);put.accept(k.h);put.accept(k.d);put.accept(k.r);
            put.accept(k.base==null?-1:k.base.length);if(k.base!=null)for(double v:k.base)put.accept(v);
            put.accept(k.features==null?-1:k.features.length);if(k.features!=null)for(double v:k.features)put.accept(v);
            if(k.source!=null){sha.update(k.source.getBytes(java.nio.charset.StandardCharsets.UTF_8));put.accept(k.sourceLength);put.accept(k.sourceTime);}
            StringBuilder name=new StringBuilder();for(byte x:sha.digest())name.append(String.format("%02x",x));return new File(folder,name+".k");
        }catch(Exception error){return null;}
    }
    private static double[] read(File f) {
        try(java.io.DataInputStream in=new java.io.DataInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(f),1<<16))) {
            int n=in.readInt();if(n<=0||n>LIMIT)return null;double[] r=new double[n];for(int i=0;i<n;i++)r[i]=in.readDouble();return r;
        }catch(Exception error){f.delete();return null;}
    }
    private static void write(File f,double[] values) {
        File folder=f.getParentFile();File temp=new File(folder,f.getName()+".tmp");
        try(java.io.DataOutputStream out=new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(temp),1<<16))) {
            out.writeInt(values.length);for(double v:values)out.writeDouble(v);
        }catch(Exception error){temp.delete();return;}
        if(!temp.renameTo(f)){temp.delete();return;}
        // Oldest results go first when the folder exceeds its limit.
        File[] all=folder.listFiles((d,n)->n.endsWith(".k"));if(all==null)return;long total=0;for(File x:all)total+=x.length();
        if(total<=DISK_LIMIT)return;Arrays.sort(all,(a,b)->Long.compare(a.lastModified(),b.lastModified()));
        for(File x:all){if(total<=DISK_LIMIT*.8)break;total-=x.length();x.delete();}
    }
    public static void clear(){synchronized(MEMO){MEMO.clear();stored=0;}}
    public static long hits(){synchronized(MEMO){return hits;}}
    public static long misses(){synchronized(MEMO){return misses;}}
    public static int size(){synchronized(MEMO){return MEMO.size();}}
}
