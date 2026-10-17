package pl.tabletcad.app;

import java.util.concurrent.*;
import java.util.function.Consumer;

/** One running computation and one replaceable pending request. UI delivery is revision-checked. */
public final class GeometryPreviewWorker implements AutoCloseable {
    public interface Callback { void completed(double[] mesh,Exception error); }
    private static final class Request {
        final long revision;final Callable<double[]> task;final Callback callback;
        Request(long revision,Callable<double[]> task,Callback callback){this.revision=revision;this.task=task;this.callback=callback;}
    }
    private final ExecutorService executor=Executors.newSingleThreadExecutor(task -> {
        Thread thread=new Thread(task,"TabletCAD-geometry-preview");thread.setDaemon(true);return thread;
    });
    private final Consumer<Runnable> dispatcher;
    private Request pending;
    private long revision;
    private boolean running,closed;
    public GeometryPreviewWorker(Consumer<Runnable> dispatcher){this.dispatcher=dispatcher;}
    public synchronized void submit(Callable<double[]> task,Callback callback) {
        if(closed)throw new IllegalStateException("Preview worker closed");
        pending=new Request(++revision,task,callback);
        if(!running){running=true;executor.execute(this::drain);}
    }
    public synchronized void cancel(){revision++;pending=null;}
    private void drain() {
        for(;;) {
            Request request;
            synchronized(this){request=pending;pending=null;if(closed||request==null){running=false;return;}}
            double[] mesh=null;Exception failure=null;
            try{mesh=request.task.call();}catch(Exception error){failure=error;}
            final double[] result=mesh;final Exception error=failure;
            synchronized(this){if(closed||revision!=request.revision)continue;}
            dispatcher.accept(() -> {
                synchronized(GeometryPreviewWorker.this) {
                    if(!closed&&revision==request.revision)request.callback.completed(result,error);
                }
            });
        }
    }
    @Override public synchronized void close(){closed=true;cancel();executor.shutdownNow();}
}
