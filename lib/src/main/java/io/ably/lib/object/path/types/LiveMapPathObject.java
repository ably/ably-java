package io.ably.lib.object.path.types;

import io.ably.lib.object.path.PathObject;
import io.ably.lib.objects.type.map.LiveMapValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link PathObject} whose underlying value is expected to be a {@code LiveMap}.
 * Provides type-safe access to map-specific operations such as {@link #get(String)},
 * {@link #entries()}, {@link #set(String, LiveMapValue)}, etc.
 *
 * <p>Calling {@code channel.objects.getRoot()}-equivalent navigation methods at the
 * root of the graph always returns a {@code LiveMapPathObject}.
 *
 * <p>Operations on this type are best-effort: they resolve the path against the local
 * LiveObjects graph at call time. Read operations return empty/null when the path does
 * not resolve to a {@code LiveMap}; write operations complete the returned
 * {@link CompletableFuture} exceptionally with an {@code AblyException}
 * (status 400, code 92007) in that case.
 */
public interface LiveMapPathObject extends PathObject {

    /**
     * Returns a new {@link PathObject} representing the child at {@code key} of the
     * {@code LiveMap} at this path. Purely navigational - no resolution occurs.
     *
     * <p>Spec: RTPO5
     *
     * @param key the child key to navigate to
     * @return a {@link PathObject} pointing to {@code this.path + key}
     */
    @NotNull
    PathObject get(@NotNull String key);

    /**
     * Returns the entries (key, child {@link PathObject}) of the {@code LiveMap} at
     * this path. Each child path is produced as if by calling {@link #get(String)} with
     * the corresponding key.
     *
     * <p>Returns an empty iterable when the path does not resolve to a {@code LiveMap}.
     *
     * <p>Spec: RTPO9
     *
     * @return an unmodifiable iterable of map entries; empty when not a LiveMap
     */
    @NotNull
    @Unmodifiable
    Iterable<Map.Entry<String, PathObject>> entries();

    /**
     * Returns the keys of the {@code LiveMap} at this path.
     *
     * <p>Returns an empty iterable when the path does not resolve to a {@code LiveMap}.
     *
     * <p>Spec: RTPO10
     *
     * @return an unmodifiable iterable of keys; empty when not a LiveMap
     */
    @NotNull
    @Unmodifiable
    Iterable<String> keys();

    /**
     * Returns the child {@link PathObject}s for each key in the {@code LiveMap} at this
     * path.
     *
     * <p>Returns an empty iterable when the path does not resolve to a {@code LiveMap}.
     *
     * <p>Spec: RTPO11
     *
     * @return an unmodifiable iterable of child paths; empty when not a LiveMap
     */
    @NotNull
    @Unmodifiable
    Iterable<PathObject> values();

    /**
     * Returns the size of the {@code LiveMap} at this path, or {@code null} when the
     * path does not resolve to a {@code LiveMap}.
     *
     * <p>Spec: RTPO12
     *
     * @return the number of (non-tombstoned) entries, or {@code null}
     */
    @Nullable
    Long size();

    /**
     * Sets a key on the {@code LiveMap} at this path to the provided value.
     *
     * <p>Sends a {@code MAP_SET} operation to the realtime system; the local state is
     * updated when the operation is echoed back. The returned future completes
     * exceptionally with an {@code AblyException} (status 400, code 92005) if the path
     * cannot be resolved, or (status 400, code 92007) if the resolved value is not a
     * {@code LiveMap}.
     *
     * <p>Spec: RTPO15
     *
     * @param key   the key to set
     * @param value the value to associate with {@code key}
     * @return a future that completes when the operation has been acknowledged
     */
    @NotNull
    CompletableFuture<Void> set(@NotNull String key, @NotNull LiveMapValue value);

    /**
     * Removes a key from the {@code LiveMap} at this path.
     *
     * <p>Sends a {@code MAP_REMOVE} operation to the realtime system; the local state
     * is updated when the operation is echoed back. Same error conditions as
     * {@link #set(String, LiveMapValue)} apply.
     *
     * <p>Spec: RTPO16
     *
     * @param key the key to remove
     * @return a future that completes when the operation has been acknowledged
     */
    @NotNull
    CompletableFuture<Void> remove(@NotNull String key);
}
