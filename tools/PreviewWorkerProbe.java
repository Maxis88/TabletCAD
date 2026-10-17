import java.util.*;
import java.util.concurrent.*;
import pl.tabletcad.app.GeometryPreviewWorker;

public final class PreviewWorkerProbe {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    static void await(CountDownLatch latch)throws Exception{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("Worker timeout");}
    public static void main(String[] args)throws Exception {
        List<Integer> executed=Collections.synchronizedList(new ArrayList<>()),delivered=Collections.synchronizedList(new ArrayList<>());
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
        Thread caller=Thread.currentThread();
        try(GeometryPreviewWorker worker=new GeometryPreviewWorker(Runnable::run)) {
            worker.submit(()->{check(Thread.currentThread()!=caller,"geometry runs off caller thread");executed.add(1);started.countDown();await(release);return new double[]{1};},(mesh,error)->delivered.add(1));
            await(started);
            worker.submit(()->{executed.add(2);return new double[]{2};},(mesh,error)->delivered.add(2));
            worker.submit(()->{executed.add(3);return new double[]{3};},(mesh,error)->{delivered.add((int)mesh[0]);done.countDown();});
            release.countDown();await(done);
            check(executed.equals(Arrays.asList(1,3))&&delivered.equals(Arrays.asList(3)),"pending requests coalesce and stale running result is discarded");
            CountDownLatch failure=new CountDownLatch(1);
            worker.submit(()->{throw new IllegalArgumentException("invalid geometry");},(mesh,error)->{if(error instanceof IllegalArgumentException)failure.countDown();});await(failure);
            check(true,"calculation failures are delivered without killing worker");
        }
        BlockingQueue<Runnable> ui=new LinkedBlockingQueue<>();
        try(GeometryPreviewWorker worker=new GeometryPreviewWorker(ui::add)) {
            worker.submit(()->new double[]{1},(mesh,error)->{throw new AssertionError("stale UI callback");});
            Runnable stale=ui.poll(5,TimeUnit.SECONDS);check(stale!=null,"result queued for UI dispatcher");worker.cancel();stale.run();
            check(true,"cancel invalidates an already queued UI result");
            worker.submit(()->new double[]{2},(mesh,error)->{throw new AssertionError("closed UI callback");});
            Runnable closed=ui.poll(5,TimeUnit.SECONDS);check(closed!=null,"second result queued");worker.close();closed.run();
            check(true,"close invalidates queued callbacks");
        }
        System.out.println("ALL PREVIEW WORKER TESTS PASSED");
    }
}
