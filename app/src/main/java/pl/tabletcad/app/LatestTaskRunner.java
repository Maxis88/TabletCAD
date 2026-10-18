package pl.tabletcad.app;

import java.util.concurrent.*;
import java.util.function.*;

/** Serial read tasks with generation checks both before work and before delivering a result. */
public final class LatestTaskRunner implements AutoCloseable {
    private final ExecutorService worker;
    private final Executor delivery;
    private long generation;
    private boolean closed;

    public LatestTaskRunner(Executor delivery) {
        this.delivery=delivery;
        worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"TabletCAD-library-read");t.setDaemon(true);return t;});
    }
    public synchronized <T> void submit(Callable<T> action,Consumer<T> success,Consumer<Exception> failure) {
        if(closed)return;
        long token=++generation;
        worker.execute(()->{
            if(!current(token))return;
            T value;
            try {value=action.call();}
            catch(Exception error){delivery.execute(()->{if(current(token))failure.accept(error);});return;}
            delivery.execute(()->{if(current(token))success.accept(value);});
        });
    }
    private synchronized boolean current(long token){return !closed&&generation==token;}
    /** Does not interrupt a read already in progress; its result is discarded. */
    @Override public synchronized void close(){closed=true;generation++;worker.shutdown();}
}
