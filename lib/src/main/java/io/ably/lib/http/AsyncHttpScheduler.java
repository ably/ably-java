package io.ably.lib.http;

import io.ably.lib.types.ClientOptions;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A HttpScheduler that uses a thread pool to run HTTP operations.
 */
public class AsyncHttpScheduler extends HttpScheduler<ThreadPoolExecutor> {
    public AsyncHttpScheduler(HttpCore httpCore, ClientOptions options) {
        super(httpCore, new ThreadPoolExecutor(options.asyncHttpThreadpoolSize, options.asyncHttpThreadpoolSize, KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>()));
    }

    private static final long KEEP_ALIVE_TIME = 2000L;

    protected static final String TAG = AsyncHttpScheduler.class.getName();
}
