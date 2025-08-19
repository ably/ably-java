package io.ably.lib.objects.type.counter;

import io.ably.lib.objects.ObjectsCallback;
import io.ably.lib.objects.type.ObjectLifecycleChange;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Contract;

/**
 * The LiveCounter interface provides methods to interact with a live counter.
 * It allows incrementing, decrementing, and retrieving the current value of the counter,
 * both synchronously and asynchronously.
 */
public interface LiveCounter extends LiveCounterChange, ObjectLifecycleChange {

    /**
     * Increments the value of the counter by the specified amount.
     * Send a COUNTER_INC operation to the realtime system to increment a value on this LiveCounter object.
     * This does not modify the underlying data of this LiveCounter object. Instead, the change will be applied when
     * the published COUNTER_INC operation is echoed back to the client and applied to the object following the regular
     * operation application procedure.
     * Spec: RTLC12
     *
     * @param amount the amount by which to increment the counter
     */
    @Blocking
    void increment(@NotNull Number amount);

    /**
     * Decrements the value of the counter by the specified amount.
     * An alias for calling {@link LiveCounter#increment(Number)} with a negative amount.
     * Spec: RTLC13
     *
     * @param amount the amount by which to decrement the counter
     */
    @Blocking
    void decrement(@NotNull Number amount);

    /**
     * Increments the value of the counter by the specified amount asynchronously.
     * Send a COUNTER_INC operation to the realtime system to increment a value on this LiveCounter object.
     * This does not modify the underlying data of this LiveCounter object. Instead, the change will be applied when
     * the published COUNTER_INC operation is echoed back to the client and applied to the object following the regular
     * operation application procedure.
     * Spec: RTLC12
     *
     * @param amount the amount by which to increment the counter
     * @param callback the callback to be invoked upon completion of the operation.
     */
    @NonBlocking
    void incrementAsync(@NotNull Number amount, @NotNull ObjectsCallback<Void> callback);

    /**
     * Decrements the value of the counter by the specified amount asynchronously.
     * An alias for calling {@link LiveCounter#incrementAsync(Number, ObjectsCallback)} with a negative amount.
     * Spec: RTLC13
     *
     * @param amount the amount by which to decrement the counter
     * @param callback the callback to be invoked upon completion of the operation.
     */
    @NonBlocking
    void decrementAsync(@NotNull Number amount, @NotNull ObjectsCallback<Void> callback);

    /**
     * Retrieves the current value of the counter.
     *
     * @return the current value of the counter as a Double.
     */
    @NotNull
    @Contract(pure = true) // Indicates this method does not modify the state of the object.
    Double value();
}
