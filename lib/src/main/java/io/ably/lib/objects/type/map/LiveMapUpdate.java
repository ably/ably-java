package io.ably.lib.objects.type.map;

import io.ably.lib.objects.type.LiveObjectUpdate;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Spec: RTLM18, RTLM18a
 */
public class LiveMapUpdate extends LiveObjectUpdate {

    public LiveMapUpdate() {
        super(null);
    }

    /**
     * Constructor for LiveMapUpdate
     * @param update The map of updates
     */
    public LiveMapUpdate(@NotNull Map<String, Change> update) {
        super(update);
    }

    /**
     * Get the map of updates
     * @return The update map
     */
    @NotNull
    public Map<String, Change> getUpdate() {
        return (Map<String, Change>) update;
    }

    /**
     * Spec: RTLM18b
     */
    public enum Change {
        UPDATED,
        REMOVED
    }
}
