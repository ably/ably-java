package io.ably.lib.objects;

import io.ably.lib.objects.state.ObjectsStateChange;
import io.ably.lib.objects.type.counter.LiveCounter;
import io.ably.lib.objects.type.map.LiveMap;
import io.ably.lib.objects.type.map.LiveMapValue;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The RealtimeObjects interface provides methods to interact with live data objects,
 * such as maps and counters, in a real-time environment. It supports both synchronous
 * and asynchronous operations for retrieving and creating objects.
 *
 * <p>Implementations of this interface must be thread-safe as they may be accessed
 * from multiple threads concurrently.
 */
public interface RealtimeObjects extends ObjectsStateChange {

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
     * Creates a new empty LiveMap with no entries.
     * Send a MAP_CREATE operation to the realtime system to create a new map object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed MAP_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * and returns it.
     *
     * @return the newly created empty LiveMap instance.
     */
    @Blocking
    @NotNull
    LiveMap createMap();

    /**
     * Creates a new LiveMap with type-safe entries that can be Boolean, Binary, Number, String, JsonArray, JsonObject, LiveCounter, or LiveMap.
     * Implements spec RTO11 : createMap(Dict<String, Boolean | Binary | Number | String | JsonArray | JsonObject | LiveCounter | LiveMap> entries?)
     * Send a MAP_CREATE operation to the realtime system to create a new map object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed MAP_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * using the provided data and returns it.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * Map<String, LiveMapValue> entries = Map.of(
     *     "string", LiveMapValue.of("Hello"),
     *     "number", LiveMapValue.of(42),
     *     "boolean", LiveMapValue.of(true),
     *     "binary", LiveMapValue.of(new byte[]{1, 2, 3}),
     *     "array", LiveMapValue.of(new JsonArray()),
     *     "object", LiveMapValue.of(new JsonObject()),
     *     "counter", LiveMapValue.of(realtimeObjects.createCounter()),
     *     "nested", LiveMapValue.of(realtimeObjects.createMap())
     * );
     * LiveMap map = realtimeObjects.createMap(entries);
     * }</pre>
     *
     * @param entries the type-safe map entries with values that can be Boolean, Binary, Number, String, JsonArray, JsonObject, LiveCounter, or LiveMap.
     * @return the newly created LiveMap instance.
     */
    @Blocking
    @NotNull
    LiveMap createMap(@NotNull Map<String, LiveMapValue> entries);

    /**
     * Creates a new LiveCounter with an initial value of 0.
     * Send a COUNTER_CREATE operation to the realtime system to create a new counter object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed COUNTER_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * using the provided data and returns it.
     *
     * @return the newly created LiveCounter instance with initial value of 0.
     */
    @Blocking
    @NotNull
    LiveCounter createCounter();

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
    LiveCounter createCounter(@NotNull Number initialValue);

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
     * Asynchronously creates a new empty LiveMap with no entries.
     * Send a MAP_CREATE operation to the realtime system to create a new map object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed MAP_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * and returns it.
     *
     * @param callback the callback to handle the result or error.
     */
    @NonBlocking
    void createMapAsync(@NotNull ObjectsCallback<@NotNull LiveMap> callback);

    /**
     * Asynchronously creates a new LiveMap with type-safe entries that can be Boolean, Binary, Number, String, JsonArray, JsonObject, LiveCounter, or LiveMap.
     * This method implements the spec RTO11 signature: createMap(Dict<String, Boolean | Binary | Number | String | JsonArray | JsonObject | LiveCounter | LiveMap> entries?)
     * Send a MAP_CREATE operation to the realtime system to create a new map object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed MAP_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * using the provided data and returns it.
     *
     * @param entries the type-safe map entries with values that can be Boolean, Binary, Number, String, JsonArray, JsonObject, LiveCounter, or LiveMap.
     * @param callback the callback to handle the result or error.
     */
    @NonBlocking
    void createMapAsync(@NotNull Map<String, LiveMapValue> entries, @NotNull ObjectsCallback<@NotNull LiveMap> callback);

    /**
     * Asynchronously creates a new LiveCounter with an initial value of 0.
     * Send a COUNTER_CREATE operation to the realtime system to create a new counter object in the pool.
     * Once the ACK message is received, the method returns the object from the local pool if it got created due to
     * the echoed COUNTER_CREATE operation, or if it wasn't received yet, the method creates a new object locally
     * using the provided data and returns it.
     *
     * @param callback the callback to handle the result or error.
     */
    @NonBlocking
    void createCounterAsync(@NotNull ObjectsCallback<@NotNull LiveCounter> callback);

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
    void createCounterAsync(@NotNull Number initialValue, @NotNull ObjectsCallback<@NotNull LiveCounter> callback);
}
