package io.ably.lib.objects.batch;

/**
 * A functional interface for building and handling a BatchContext.*
 */
@FunctionalInterface
public interface BatchContextBuilder {
    /**
     * Builds and handles the provided BatchContext.
     *
     * @param batchContext the BatchContext to handle.
     */
    void build(BatchContext batchContext);
}
