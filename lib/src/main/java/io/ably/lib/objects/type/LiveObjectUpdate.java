package io.ably.lib.objects.type;

import org.jetbrains.annotations.Nullable;

/**
 * Abstract base class for all LiveObject update notifications.
 * Provides common structure for updates that occur on LiveMap and LiveCounter objects.
 * Contains the update data that describes what changed in the live object.
 * Spec: RTLO4b4
 */
public abstract class LiveObjectUpdate {
    /**
     * The update data containing details about the change that occurred
     * Spec: RTLO4b4a
     */
    @Nullable
    protected final Object update;

    /**
     * Creates a LiveObjectUpdate with the specified update data.
     *
     * @param update the data describing the change, or null for no-op updates
     */
    protected LiveObjectUpdate(@Nullable Object update) {
        this.update = update;
    }
}
