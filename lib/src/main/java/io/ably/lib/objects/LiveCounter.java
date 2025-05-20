package io.ably.lib.objects;

import io.ably.lib.types.Callback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Contract;

/**
 * The LiveCounter interface provides methods to interact with a live counter.
 * It allows incrementing, decrementing, and retrieving the current value of the counter,
 * both synchronously and asynchronously.
 */
public interface LiveCounter {

    /**
     * Increments the value of the counter by 1.
     */
    void increment();

    /**
     * Increments the value of the counter by 1 asynchronously.
     *
     * @param callback the callback to be invoked upon completion of the operation.
     */
    void incrementAsync(@NotNull Callback<Void> callback);

    /**
     * Decrements the value of the counter by 1.
     */
    void decrement();

    /**
     * Decrements the value of the counter by 1 asynchronously.
     *
     * @param callback the callback to be invoked upon completion of the operation.
     */
    void decrementAsync(@NotNull Callback<Void> callback);

    /**
     * Retrieves the current value of the counter.
     *
     * @return the current value of the counter as a Long.
     */
    @NotNull
    @Contract(pure = true) // Indicates this method does not modify the state of the object.
    Long value();
}
