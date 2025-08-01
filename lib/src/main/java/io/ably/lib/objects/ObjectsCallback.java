package io.ably.lib.objects;

import io.ably.lib.types.AblyException;

/**
 * Callback interface for handling results of asynchronous LiveObjects operations.
 * Used for operations like creating LiveMaps/LiveCounters, modifying entries, and retrieving objects.
 * Callbacks are executed on background threads managed by the LiveObjects system.
 *
 * @param <T> the type of the result returned by the asynchronous operation
 */
public interface ObjectsCallback<T> {

    /**
     * Called when the asynchronous operation completes successfully.
     * For modification operations (set, remove, increment), result is typically Void.
     * For creation/retrieval operations, result contains the created/retrieved object.
     *
     * @param result the result of the operation, may be null for modification operations
     */
    void onSuccess(T result);

    /**
     * Called when the asynchronous operation fails.
     * The exception contains detailed error information including error codes and messages.
     * Common errors include network issues, authentication failures, and validation errors.
     *
     * @param exception the exception that occurred during the operation
     */
    void onError(AblyException exception);
}
