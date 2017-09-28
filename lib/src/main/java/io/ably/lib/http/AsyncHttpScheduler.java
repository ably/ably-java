package io.ably.lib.http;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A HttpScheduler that uses a thread pool to run HTTP operations.
 */
public class AsyncHttpScheduler extends HttpScheduler<ThreadPoolExecutor> {
	public AsyncHttpScheduler(HttpCore httpCore) {
		super(httpCore, new ThreadPoolExecutor(DEFAULT_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>()));
	}

	public void setThreadPoolSize(int size) {
		executor.setCorePoolSize(size);
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

	private static final int DEFAULT_POOL_SIZE = 0;
	private static final int MAX_POOL_SIZE = 64;
	private static final long KEEP_ALIVE_TIME = 2000L;
	private static final long SHUTDOWN_TIME = 5000L;

	protected static final String TAG = AsyncHttpScheduler.class.getName();
}
