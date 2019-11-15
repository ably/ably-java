package io.ably.lib.http;

import io.ably.lib.util.CurrentThreadExecutor;

/**
 * A HttpScheduler that runs everything in the current thread.
 */
class SyncHttpScheduler extends HttpScheduler<CurrentThreadExecutor> {

    SyncHttpScheduler(HttpCore httpCore) {
        super(httpCore, CurrentThreadExecutor.INSTANCE);
    }
}
