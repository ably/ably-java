package io.ably.lib.objects.path;

import org.jetbrains.annotations.NotNull;

/**
 * Listener invoked when a {@link PathObject} subscription fires.
 * <p>
 * Listeners are called from the Objects worker thread; keep handlers
 * fast and dispatch heavy work to a different executor if needed.
 */
@FunctionalInterface
public interface PathChangeListener {
    void onChange(@NotNull PathChangeEvent event);
}
