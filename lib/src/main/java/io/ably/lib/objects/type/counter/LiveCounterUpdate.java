package io.ably.lib.objects.type.counter;

import io.ably.lib.objects.type.LiveObjectUpdate;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an update that occurred on a LiveCounter object.
 * Contains information about counter value changes from increment/decrement operations.
 * Updates can represent positive changes (increments) or negative changes (decrements).
 *
 * @spec RTLC11, RTLC11a - LiveCounter update structure and behavior
 */
public class LiveCounterUpdate extends LiveObjectUpdate {

    /**
     * Creates a no-op LiveCounterUpdate representing no actual change.
     */
    public LiveCounterUpdate() {
        super(null);
    }

    /**
     * Creates a LiveCounterUpdate with the specified amount change.
     *
     * @param amount the amount by which the counter changed (positive = increment, negative = decrement)
     */
    public LiveCounterUpdate(@NotNull Double amount) {
        super(new Update(amount));
    }

    /**
     * Gets the update information containing the amount of change.
     *
     * @return the Update object with the counter modification amount
     */
    @NotNull
    public LiveCounterUpdate.Update getUpdate() {
        return (Update) update;
    }

    /**
     * Returns a string representation of this LiveCounterUpdate.
     *
     * @return a string showing the amount of change to the counter
     */
    @Override
    public String toString() {
        if (update == null) {
            return "LiveCounterUpdate{no change}";
        }
        return "LiveCounterUpdate{amount=" + getUpdate().getAmount() + "}";
    }

    /**
     * Contains the specific details of a counter update operation.
     *
     * @spec RTLC11b, RTLC11b1 - Counter update data structure
     */
    public static class Update {
        private final @NotNull Double amount;

        /**
         * Creates an Update with the specified amount.
         *
         * @param amount the counter change amount (positive = increment, negative = decrement)
         */
        public Update(@NotNull Double amount) {
            this.amount = amount;
        }

        /**
         * Gets the amount by which the counter value was modified.
         *
         * @return the change amount (positive for increments, negative for decrements)
         */
        public @NotNull Double getAmount() {
            return amount;
        }
    }
}
