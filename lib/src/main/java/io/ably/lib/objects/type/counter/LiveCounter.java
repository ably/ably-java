package io.ably.lib.objects.type.counter;

import io.ably.lib.objects.ObjectsCallback;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Contract;

/**
 * The LiveCounter interface provides methods to interact with a live counter.
 * It allows incrementing, decrementing, and retrieving the current value of the counter,
 * both synchronously and asynchronously.
 */
public interface LiveCounter extends LiveCounterChange {

    /**
     * Increments the value of the counter by 1.
     * Send a COUNTER_INC operation to the realtime system to increment a value on this LiveCounter object.
     * This does not modify the underlying data of this LiveCounter object. Instead, the change will be applied when
     * the published COUNTER_INC operation is echoed back to the client and applied to the object following the regular
     * operation application procedure.
     */
    @Blocking
    void increment();

    /**
     * Increments the value of the counter by 1 asynchronously.
     * Send a COUNTER_INC operation to the realtime system to increment a value on this LiveCounter object.
     * This does not modify the underlying data of this LiveCounter object. Instead, the change will be applied when
     * the published COUNTER_INC operation is echoed back to the client and applied to the object following the regular
     * operation application procedure.
     *
     * @param callback the callback to be invoked upon completion of the operation.
     */
    @NonBlocking
    void incrementAsync(@NotNull ObjectsCallback<Void> callback);

    /**
     * Decrements the value of the counter by 1.
     * An alias for calling {@link LiveCounter#increment()} with a negative amount.
     */
    @Blocking
    void decrement();

    /**
     * Decrements the value of the counter by 1 asynchronously.
     * An alias for calling {@link LiveCounter#increment()} with a negative amount.
     *
     * @param callback the callback to be invoked upon completion of the operation.
     */
    @NonBlocking
    void decrementAsync(@NotNull ObjectsCallback<Void> callback);

    /**
     * Retrieves the current value of the counter.
     *
     * @return the current value of the counter as a Long.
     */
    @NotNull
    @Contract(pure = true) // Indicates this method does not modify the state of the object.
    Double value();
}
