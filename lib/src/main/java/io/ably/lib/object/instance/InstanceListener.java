package io.ably.lib.object.instance;

import io.ably.lib.object.instance.types.LiveCounterInstance;
import io.ably.lib.object.instance.types.LiveMapInstance;
import org.jetbrains.annotations.NotNull;

/**
 * Listener interface for instance subscriptions created via
 * {@link LiveMapInstance#subscribe(InstanceListener)} or
 * {@link LiveCounterInstance#subscribe(InstanceListener)}.
 *
 * <p>Spec: RTINS16a1
 */
public interface InstanceListener {

    /**
     * Invoked when the wrapped LiveObject is modified.
     *
     * @param event the event describing the change
     */
    void onUpdated(@NotNull InstanceSubscriptionEvent event);
}
