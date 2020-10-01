package io.ably.lib.http;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A HttpScheduler that uses a thread pool to run HTTP operations.
 */
public class AsyncHttpScheduler extends HttpScheduler<ThreadPoolExecutor> {
    public AsyncHttpScheduler(HttpCore httpCore, ClientOptions options) {
        super(httpCore, new ThreadPoolExecutor(options.asyncHttpThreadpoolSize, options.asyncHttpThreadpoolSize, KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>()));
        executor.allowsCoreThreadTimeOut();
    }

    public void dispose() {
        ThreadPoolExecutor threadPoolExecutor = executor;
        threadPoolExecutor.shutdown();
        try {
            threadPoolExecutor.awaitTermination(SHUTDOWN_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            threadPoolExecutor.shutdownNow();
        }
    }

    private static final long KEEP_ALIVE_TIME = 2000L;
    private static final long SHUTDOWN_TIME = 5000L;

    protected static final String TAG = AsyncHttpScheduler.class.getName();
}
