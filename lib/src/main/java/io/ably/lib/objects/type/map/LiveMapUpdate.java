package io.ably.lib.objects.type.map;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Spec: RTLM18, RTLM18a
 */
public class LiveMapUpdate {

    @NotNull
    private final Map<String, Change> update;

    /**
     * Constructor for LiveMapUpdate
     * @param update The map of updates
     */
    public LiveMapUpdate(@NotNull Map<String, Change> update) {
        this.update = update;
    }

    /**
     * Get the map of updates
     * @return The update map
     */
    @NotNull
    public Map<String, Change> getUpdate() {
        return update;
    }

    /**
     * Spec: RTLM18b
     */
    public enum Change {
        UPDATED,
        REMOVED
    }
}
