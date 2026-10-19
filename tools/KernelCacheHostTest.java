import pl.tabletcad.app.KernelCache;

/** 0.86 kernel memo: identical inputs reuse the result, any changed input or failure evaluates again. */
public final class KernelCacheHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    public static void main(String[] args) {
        KernelCache.clear();int[] calls={0};
        double[] base={1,2,3},features={-9,1,1,5,7};
        KernelCache.Query q=()->{calls[0]++;return new double[]{42,calls[0]};};
        double[] a=KernelCache.get(8,40,30,10,0,base,features,null,q);
        double[] b=KernelCache.get(8,40,30,10,0,base.clone(),features.clone(),null,q);
        check(calls[0]==1&&a[1]==b[1],"identical inputs evaluate the kernel once");
        b[0]=-1;check(KernelCache.get(8,40,30,10,0,base,features,null,q)[0]==42,"callers cannot alter the stored result");
        KernelCache.get(8,40,30,10,0,base,new double[]{-9,1,1,5,8},null,q);check(calls[0]==2,"changed history record evaluates again");
        KernelCache.get(1,40,30,10,0,base,features,null,q);check(calls[0]==3,"different query mode evaluates again");
        KernelCache.get(8,40,30,11,0,base,features,null,q);check(calls[0]==4,"changed size evaluates again");
        base[0]=9;KernelCache.get(8,40,30,10,0,new double[]{1,2,3},features,null,q);check(calls[0]==4,"inputs are copied: later mutation of the caller's array does not corrupt the key");
        int[] fails={0};KernelCache.Query failing=()->{fails[0]++;return new double[0];};
        KernelCache.get(3,1,1,1,0,null,null,null,failing);KernelCache.get(3,1,1,1,0,null,null,null,failing);
        check(fails[0]==2,"failed (empty) results are never stored");
        System.out.println("PASS KernelCacheHostTest: "+checks+" checks");
    }
}
