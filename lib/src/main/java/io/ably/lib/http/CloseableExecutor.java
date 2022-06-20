package io.ably.lib.http;

import java.util.concurrent.Executor;

public interface CloseableExecutor extends Executor, AutoCloseable {
}
