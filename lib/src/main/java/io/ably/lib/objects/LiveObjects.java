package io.ably.lib.objects;

import io.ably.lib.objects.state.ObjectsStateChange;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
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
public interface LiveObjects extends ObjectsStateChange {

    /**
     * Retrieves the root LiveMap object.
     * When called without a type variable, we return a default root type which is based on globally defined interface for Objects feature.
     * A user can provide an explicit type for the getRoot method to explicitly set the type structure on this particular channel.
     * This is useful when working with multiple channels with different underlying data structure.
     *
     * @return the root LiveMap instance.
     */
    @Blocking
    @NotNull
    LiveMap getRoot();

    /**
     * Creates a new LiveMap based on an existing LiveMap.
     * Send a MAP_CREATE operation to the realtime system to create a new map object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed MAP_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * using the provided data and returns it.
     *
     * @param liveMap the existing LiveMap to base the new LiveMap on.
     * @return the newly created LiveMap instance.
     */
    @Blocking
    @NotNull
    LiveMap createMap(@NotNull LiveMap liveMap);

    /**
     * Creates a new LiveMap based on a LiveCounter.
     * Send a MAP_CREATE operation to the realtime system to create a new map object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed MAP_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * using the provided data and returns it.
     *
     * @param liveCounter the LiveCounter to base the new LiveMap on.
     * @return the newly created LiveMap instance.
     */
    @Blocking
    @NotNull
    LiveMap createMap(@NotNull LiveCounter liveCounter);

    /**
     * Creates a new LiveMap based on a standard Java Map.
     * Send a MAP_CREATE operation to the realtime system to create a new map object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed MAP_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * using the provided data and returns it.
     *
     * @param map the Java Map to base the new LiveMap on.
     * @return the newly created LiveMap instance.
     */
    @Blocking
    @NotNull
    LiveMap createMap(@NotNull Map<String, Object> map);

    /**
     * Creates a new LiveCounter with an initial value.
     * Send a COUNTER_CREATE operation to the realtime system to create a new counter object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed COUNTER_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * using the provided data and returns it.
     *
     * @param initialValue the initial value of the LiveCounter.
     * @return the newly created LiveCounter instance.
     */
    @Blocking
    @NotNull
    LiveCounter createCounter(@NotNull Long initialValue);

    /**
     * Asynchronously retrieves the root LiveMap object.
     * When called without a type variable, we return a default root type which is based on globally defined interface for Objects feature.
     * A user can provide an explicit type for the getRoot method to explicitly set the type structure on this particular channel.
     * This is useful when working with multiple channels with different underlying data structure.
     *
     * @param callback the callback to handle the result or error.
     */
    @NonBlocking
    void getRootAsync(@NotNull ObjectsCallback<@NotNull LiveMap> callback);

    /**
     * Asynchronously creates a new LiveMap based on an existing LiveMap.
     * Send a MAP_CREATE operation to the realtime system to create a new map object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed MAP_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * using the provided data and returns it.
     *
     * @param liveMap the existing LiveMap to base the new LiveMap on.
     * @param callback the callback to handle the result or error.
     */
    @NonBlocking
    void createMapAsync(@NotNull LiveMap liveMap, @NotNull ObjectsCallback<@NotNull LiveMap> callback);

    /**
     * Asynchronously creates a new LiveMap based on a LiveCounter.
     * Send a MAP_CREATE operation to the realtime system to create a new map object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed MAP_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * using the provided data and returns it.
     *
     * @param liveCounter the LiveCounter to base the new LiveMap on.
     * @param callback the callback to handle the result or error.
     */
    @NonBlocking
    void createMapAsync(@NotNull LiveCounter liveCounter, @NotNull ObjectsCallback<@NotNull LiveMap> callback);

    /**
     * Asynchronously creates a new LiveMap based on a standard Java Map.
     * Send a MAP_CREATE operation to the realtime system to create a new map object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed MAP_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * using the provided data and returns it.
     *
     * @param map the Java Map to base the new LiveMap on.
     * @param callback the callback to handle the result or error.
     */
    @NonBlocking
    void createMapAsync(@NotNull Map<String, Object> map, @NotNull ObjectsCallback<@NotNull LiveMap> callback);

    /**
     * Asynchronously creates a new LiveCounter with an initial value.
     * Send a COUNTER_CREATE operation to the realtime system to create a new counter object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed COUNTER_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * using the provided data and returns it.
     *
     * @param initialValue the initial value of the LiveCounter.
     * @param callback the callback to handle the result or error.
     */
    @NonBlocking
    void createCounterAsync(@NotNull Long initialValue, @NotNull ObjectsCallback<@NotNull LiveCounter> callback);
}
