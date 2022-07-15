package io.ably.core.http;

import io.ably.core.util.CurrentThreadExecutor;

/**
 * A HttpScheduler that runs everything in the current thread.
 */
public class SyncHttpScheduler extends HttpScheduler<CurrentThreadExecutor> {

    public SyncHttpScheduler(HttpCore httpCore) {
        super(httpCore, CurrentThreadExecutor.INSTANCE);
    }
}
