package io.ably.lib.objects.batch;

import org.jetbrains.annotations.NotNull;

/**
 * The BatchContext interface represents the context for batch operations
 * on live data objects. It provides access to the root LiveMap, which serves
 * as the entry point for interacting with the batch context.
 */
public interface BatchContext {

    /**
     * Retrieves the root LiveMap associated with this batch context.
     *
     * @return the root LiveMap instance.
     */
    @NotNull
    BatchContextLiveMap getRoot();
}
