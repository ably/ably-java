package io.ably.lib.objects;

import io.ably.lib.objects.batch.BatchContextBuilder;
import io.ably.lib.types.Callback;

import java.util.Map;

/**
 * The LiveObjects interface provides methods to interact with live data objects,
 * such as maps and counters, in a real-time environment. It supports both synchronous
 * and asynchronous operations for retrieving and creating live objects.
 */
public interface LiveObjects {

    /**
     * Retrieves the root LiveMap object.
     *
     * @return the root LiveMap instance.
     */
    LiveMap getRoot();

    /**
     * Initiates a batch operation and provides a BatchContext through a callback.
     *
     * @param batchContextCallback the callback to handle the BatchContext or error.
     */
    void batch(BatchContextBuilder batchContextCallback);

    /**
     * Creates a new LiveMap based on an existing LiveMap.
     *
     * @param liveMap the existing LiveMap to base the new LiveMap on.
     * @return the newly created LiveMap instance.
     */
    LiveMap createMap(LiveMap liveMap);

    /**
     * Creates a new LiveMap based on a LiveCounter.
     *
     * @param liveCounter the LiveCounter to base the new LiveMap on.
     * @return the newly created LiveMap instance.
     */
    LiveMap createMap(LiveCounter liveCounter);

    /**
     * Creates a new LiveMap based on a standard Java Map.
     *
     * @param map the Java Map to base the new LiveMap on.
     * @return the newly created LiveMap instance.
     */
    LiveMap createMap(Map<String, Object> map);

    /**
     * Creates a new LiveCounter with an initial value.
     *
     * @param initialValue the initial value of the LiveCounter.
     * @return the newly created LiveCounter instance.
     */
    LiveCounter createCounter(Long initialValue);

    /**
     * Asynchronously retrieves the root LiveMap object.
     *
     * @param callback the callback to handle the result or error.
     */
    void getRootAsync(Callback<LiveMap> callback);

    /**
     * Initiates a batch operation asynchronously.
     *
     * @param batchContextCallback the BatchContextBuilder to build the BatchContext.
     * @param callback the Callback to handle the completion or error of the batch operation.
     */
    void batchAsync(BatchContextBuilder batchContextCallback, Callback<Void> callback);

    /**
     * Asynchronously creates a new LiveMap based on an existing LiveMap.
     *
     * @param liveMap the existing LiveMap to base the new LiveMap on.
     * @param callback the callback to handle the result or error.
     */
    void createMapAsync(LiveMap liveMap, Callback<LiveMap> callback);

    /**
     * Asynchronously creates a new LiveMap based on a LiveCounter.
     *
     * @param liveCounter the LiveCounter to base the new LiveMap on.
     * @param callback the callback to handle the result or error.
     */
    void createMapAsync(LiveCounter liveCounter, Callback<LiveMap> callback);

    /**
     * Asynchronously creates a new LiveMap based on a standard Java Map.
     *
     * @param map the Java Map to base the new LiveMap on.
     * @param callback the callback to handle the result or error.
     */
    void createMapAsync(Map<String, Object> map, Callback<LiveMap> callback);

    /**
     * Asynchronously creates a new LiveCounter with an initial value.
     *
     * @param initialValue the initial value of the LiveCounter.
     * @param callback the callback to handle the result or error.
     */
    void createCounterAsync(Long initialValue, Callback<LiveCounter> callback);
}
