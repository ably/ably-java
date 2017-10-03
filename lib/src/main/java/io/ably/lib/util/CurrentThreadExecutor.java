package io.ably.lib.util;

import java.util.concurrent.Executor;



public class CurrentThreadExecutor implements Executor {
    public static CurrentThreadExecutor INSTANCE = new CurrentThreadExecutor();

    @Override
    public void execute(Runnable runnable) {
        runnable.run();
    }
}
