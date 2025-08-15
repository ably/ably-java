package io.ably.lib.objects.type.map;

import io.ably.lib.objects.type.ObjectUpdate;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Represents an update that occurred on a LiveMap object.
 * Contains information about which keys were modified and whether they were updated or removed.
 *
 * @spec RTLM18, RTLM18a - LiveMap update structure and behavior
 */
public class LiveMapUpdate extends ObjectUpdate {

    /**
     * Creates a no-op LiveMapUpdate representing no actual change.
     */
    public LiveMapUpdate() {
        super(null);
    }

    /**
     * Creates a LiveMapUpdate with the specified key changes.
     *
     * @param update map of key names to their change types (UPDATED or REMOVED)
     */
    public LiveMapUpdate(@NotNull Map<String, Change> update) {
        super(update);
    }

    /**
     * Gets the map of key changes that occurred in this update.
     *
     * @return map of key names to their change types
     */
    @NotNull
    public Map<String, Change> getUpdate() {
        return (Map<String, Change>) update;
    }

    /**
     * Returns a string representation of this LiveMapUpdate.
     *
     * @return a string showing the map key changes in this update
     */
    @Override
    public String toString() {
        if (update == null) {
            return "LiveMapUpdate{no change}";
        }
        return "LiveMapUpdate{changes=" + getUpdate() + "}";
    }

    /**
     * Indicates the type of change that occurred to a map key.
     *
     * @spec RTLM18b - Map change types
     */
    public enum Change {
        /** The key was added or its value was modified */
        UPDATED,
        /** The key was removed from the map */
        REMOVED
    }
}
