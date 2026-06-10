package io.ably.lib.object.message;

import org.jetbrains.annotations.NotNull;

/**
 * Payload of a {@link ObjectOperationAction#COUNTER_CREATE} operation, describing the
 * initial state of the created {@code LiveCounter} object.
 *
 * <p>Spec: CCR*
 */
public interface CounterCreate {

    /**
     * Returns the initial value of the created counter object.
     *
     * <p>Spec: CCR2a
     *
     * @return the initial counter value
     */
    @NotNull Double getCount();
}
