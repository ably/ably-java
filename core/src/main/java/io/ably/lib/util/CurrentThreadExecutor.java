package io.ably.lib.util;

import io.ably.lib.http.CloseableExecutor;

public class CurrentThreadExecutor implements CloseableExecutor {
    public static CurrentThreadExecutor INSTANCE = new CurrentThreadExecutor();

    @Override
    public void execute(Runnable runnable) {
        runnable.run();
    }

    @Override
    public void close() throws Exception {
        // nothing to do
    }
}
