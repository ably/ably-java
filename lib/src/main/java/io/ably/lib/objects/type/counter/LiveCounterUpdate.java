package io.ably.lib.objects.type.counter;

import io.ably.lib.objects.type.LiveObjectUpdate;
import org.jetbrains.annotations.NotNull;

/**
 * Spec: RTLC11, RTLC11a
 */
public class LiveCounterUpdate extends LiveObjectUpdate {

    public LiveCounterUpdate() {
        super(null);
    }

    public LiveCounterUpdate(@NotNull Long amount) {
        super(new Update(amount));
    }

    @NotNull
    public LiveCounterUpdate.Update getUpdate() {
        return (Update) update;
    }

    /**
     * Spec: RTLC11b, RTLC11b1
     */
    public static class Update {
        @NotNull
        private final Long amount;

        public Update(@NotNull Long amount) {
            this.amount = amount;
        }

        public @NotNull Long getAmount() {
            return amount;
        }
    }
}
