package io.ably.lib.objects.path;

import io.ably.lib.objects.ObjectsCallback;
import io.ably.lib.types.AblyException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;

/**
 * Path-based LiveObjects accessor for a channel — obtained via
 * {@code channel.object()}. Provides a single entry point to the channel's
 * root {@link RootPathObject}.
 */
@ApiStatus.NonExtendable
public interface ChannelObject {

    /**
     * Blocking: waits for the initial Objects sync to complete and returns
     * the channel's root path object. Mirrors {@code RealtimeObjects#getRoot()}
     * which is also blocking.
     */
    @Blocking
    @NotNull
    RootPathObject get() throws AblyException;

    /**
     * Non-blocking variant. The callback is invoked once the initial sync
     * completes (success) or fails.
     */
    @NonBlocking
    void getAsync(@NotNull ObjectsCallback<RootPathObject> callback);
}
