package io.ably.lib.http;

import io.ably.lib.types.ClientOptions;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.ably.lib.util.Log;

/**
 * A HttpScheduler that uses a thread pool to run HTTP operations.
 */
public class AsyncHttpScheduler extends HttpScheduler {
    public AsyncHttpScheduler(HttpCore httpCore, ClientOptions options) {
        super(httpCore, new WrappedExecutor(options));
    }

    private static final long KEEP_ALIVE_TIME = 2000L;

    protected static final String TAG = AsyncHttpScheduler.class.getName();

    private static class WrappedExecutor implements Executor {
        private final ThreadPoolExecutor executor;

        WrappedExecutor(final ClientOptions options) {
            executor = new ThreadPoolExecutor(
                options.asyncHttpThreadpoolSize,
                options.asyncHttpThreadpoolSize,
                KEEP_ALIVE_TIME,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>()
            );
        }

        @Override
        public void execute(final Runnable command) {
            executor.execute(command);
        }

        @Override
        protected void finalize() throws Throwable {
            final int drainedCount = executor.shutdownNow().size();
            if (drainedCount > 0) {
                Log.w(TAG, "finalize() drained (cancelled) task count: " + drainedCount);
            }
        }
    }
}
