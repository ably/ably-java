package io.ably.lib.objects;

import io.ably.lib.objects.batch.BatchContextBuilder;
import io.ably.lib.types.Callback;
import org.jetbrains.annotations.NotNull;


import java.util.Map;

/**
 * The LiveObjects interface provides methods to interact with live data objects,
 * such as maps and counters, in a real-time environment. It supports both synchronous
 * and asynchronous operations for retrieving and creating live objects.
 *
 * <p>Implementations of this interface must be thread-safe as they may be accessed
 * from multiple threads concurrently.
 */
public interface LiveObjects {

    /**
     * Retrieves the root LiveMap object.
     *
     * @return the root LiveMap instance.
     */
    @NotNull
    LiveMap getRoot();

    /**
     * Initiates a batch operation and provides a BatchContext through a callback.
     *
     * @param batchContextCallback the builder to configure the batch operation.
     */
    void batch(@NotNull BatchContextBuilder batchContextCallback);

    /**
     * Creates a new LiveMap based on an existing LiveMap.
     *
     * @param liveMap the existing LiveMap to base the new LiveMap on.
     * @return the newly created LiveMap instance.
     */
    @NotNull
    LiveMap createMap(@NotNull LiveMap liveMap);

    /**
     * Creates a new LiveMap based on a LiveCounter.
     *
     * @param liveCounter the LiveCounter to base the new LiveMap on.
     * @return the newly created LiveMap instance.
     */
    @NotNull
    LiveMap createMap(@NotNull LiveCounter liveCounter);

    /**
     * Creates a new LiveMap based on a standard Java Map.
     *
     * @param map the Java Map to base the new LiveMap on.
     * @return the newly created LiveMap instance.
     */
    @NotNull
    LiveMap createMap(@NotNull Map<String, Object> map);

    /**
     * Creates a new LiveCounter with an initial value.
     *
     * @param initialValue the initial value of the LiveCounter.
     * @return the newly created LiveCounter instance.
     */
    @NotNull
    LiveCounter createCounter(@NotNull Long initialValue);

    /**
     * Asynchronously retrieves the root LiveMap object.
     *
     * @param callback the callback to handle the result or error.
     */
    void getRootAsync(@NotNull Callback<@NotNull LiveMap> callback);

    /**
     * Initiates a batch operation asynchronously.
     *
     * @param batchContextCallback the builder to configure the batch operation.
     * @param callback the Callback to handle the completion or error of the batch operation.
     */
    void batchAsync(@NotNull BatchContextBuilder batchContextCallback, @NotNull Callback<Void> callback);

    /**
     * Asynchronously creates a new LiveMap based on an existing LiveMap.
     *
     * @param liveMap the existing LiveMap to base the new LiveMap on.
     * @param callback the callback to handle the result or error.
     */
    void createMapAsync(@NotNull LiveMap liveMap, @NotNull Callback<@NotNull LiveMap> callback);

    /**
     * Asynchronously creates a new LiveMap based on a LiveCounter.
     *
     * @param liveCounter the LiveCounter to base the new LiveMap on.
     * @param callback the callback to handle the result or error.
     */
    void createMapAsync(@NotNull LiveCounter liveCounter, @NotNull Callback<@NotNull LiveMap> callback);

    /**
     * Asynchronously creates a new LiveMap based on a standard Java Map.
     *
     * @param map the Java Map to base the new LiveMap on.
     * @param callback the callback to handle the result or error.
     */
    void createMapAsync(@NotNull Map<String, Object> map, @NotNull Callback<@NotNull LiveMap> callback);

    /**
     * Asynchronously creates a new LiveCounter with an initial value.
     *
     * @param initialValue the initial value of the LiveCounter.
     * @param callback the callback to handle the result or error.
     */
    void createCounterAsync(@NotNull Long initialValue, @NotNull Callback<@NotNull LiveCounter> callback);
}
