package io.ably.lib.liveobjects.message;

import org.jetbrains.annotations.NotNull;

/**
 * Payload of a {@link ObjectOperationAction#COUNTER_INC} operation, describing an amount
 * by which a {@code LiveCounter} object is incremented. The amount may be negative,
 * representing a decrement.
 *
 * <p>Spec: CIN*
 */
public interface CounterInc {

    /**
     * Returns the amount by which the counter is incremented.
     *
     * <p>Spec: CIN2a
     *
     * @return the increment amount (may be negative)
     */
    @NotNull Double getNumber();
}
