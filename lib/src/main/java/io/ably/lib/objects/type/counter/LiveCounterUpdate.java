package io.ably.lib.objects.type.counter;

import org.jetbrains.annotations.NotNull;

/**
 * Spec: RTLC11, RTLC11a
 */
public class LiveCounterUpdate {
    @NotNull
    private final Long amount; // RTLC11b, RTLC11b1

    public LiveCounterUpdate(@NotNull Long amount) {
        this.amount = amount;
    }

    @NotNull
    public Long getUpdate() {
        return amount;
    }
}
