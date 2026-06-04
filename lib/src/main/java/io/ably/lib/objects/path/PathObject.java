package io.ably.lib.objects.path;

import com.google.gson.JsonElement;
import io.ably.lib.objects.ObjectsCallback;
import io.ably.lib.objects.ObjectsSubscription;
import io.ably.lib.types.AblyException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A handle to a location within the LiveObjects tree.
 * <p>
 * PathObject is deliberately untyped: the value at this path can change type
 * over the channel's lifetime (it may be a primitive today, a LiveMap
 * tomorrow). Terminal methods ({@code value}, {@code set}, {@code remove},
 * {@code increment}, {@code instance}, {@code compact}, {@code as*}) resolve
 * the path against the in-memory objects pool at call time, and may throw if
 * the resolved type does not match the operation.
 * <p>
 * <b>Threading / blocking:</b>
 * <ul>
 *   <li>Reads ({@code value}, {@code instance}, {@code compact},
 *   {@code compactJson}, {@code as*}, {@code entries}, {@code keys},
 *   {@code values}, {@code size}) are non-blocking — pool lookups, no IO.</li>
 *   <li>Writes ({@code set}, {@code remove}, {@code increment},
 *   {@code decrement}) are {@link Blocking @Blocking} — they publish an
 *   operation and wait for the ACK. Use {@code *Async} variants to avoid
 *   blocking the caller.</li>
 *   <li>{@code subscribe} is {@link NonBlocking @NonBlocking} — registry
 *   insert only.</li>
 * </ul>
 * <p>
 * Spec: see <a href="https://ably.com/docs/liveobjects/concepts/path-object">Path object concept</a>.
 */
@ApiStatus.NonExtendable
public interface PathObject {

    // ---- Identity / navigation --------------------------------------------

    /** Dotted path from the channel root, e.g. {@code "game.players.alice"}. */
    @NotNull
    String path();

    /** Navigate one key deeper. The returned PathObject is dynamic. */
    @NotNull
    PathObject get(@NotNull String key);

    /** Navigate by dotted path; equivalent to repeatedly calling {@link #get(String)}. */
    @NotNull
    PathObject at(@NotNull String dottedPath);

    // ---- Reads (non-blocking — local pool lookups) ------------------------

    /**
     * Resolve the path against the local objects pool.
     *
     * @return {@code null} if nothing is at this path; a {@link LivePrimitive}
     *         for primitives; a {@link LiveMapInstance} or
     *         {@link LiveCounterInstance} for live objects.
     */
    @Nullable
    @Contract(pure = true)
    LiveValue value();

    /**
     * Resolved live-object instance, or {@code null} if the path is
     * unresolved or resolves to a primitive.
     */
    @Nullable
    @Contract(pure = true)
    LiveInstance instance();

    /**
     * Snapshot the subtree rooted at this path. The returned map references
     * the live-object instances themselves, so cyclic graphs are preserved.
     */
    @NotNull
    @Contract(pure = true)
    Map<String, LiveValue> compact();

    /**
     * Snapshot the subtree as JSON-safe Gson {@link JsonElement}. Cycles are
     * broken via {@code {"objectId":"…"}} placeholders; binary values are
     * base64-encoded.
     */
    @NotNull
    @Contract(pure = true)
    JsonElement compactJson();

    // ---- Typed assertions (non-blocking) ----------------------------------

    /**
     * Assert that this path resolves to a LiveMap. Throws if missing or of
     * a different type.
     */
    @NotNull
    LiveMapInstance asLiveMap() throws AblyException;

    @NotNull
    LiveCounterInstance asLiveCounter() throws AblyException;

    @NotNull
    LivePrimitive asPrimitive() throws AblyException;

    // ---- Map-like enumeration (non-blocking) ------------------------------

    @NotNull
    Iterable<Map.Entry<String, PathObject>> entries() throws AblyException;

    @NotNull
    Iterable<String> keys() throws AblyException;

    @NotNull
    Iterable<PathObject> values() throws AblyException;

    /** Size of the LiveMap at this path; {@code null} if the path is not a map. */
    @Nullable
    @Contract(pure = true)
    Long size();

    // ---- Writes (Blocking — publish + ACK) --------------------------------

    @Blocking
    void set(@NotNull String key, @NotNull LiveValue value) throws AblyException;

    @Blocking
    void remove(@NotNull String key) throws AblyException;

    @Blocking
    void increment(@NotNull Number amount) throws AblyException;

    @Blocking
    void decrement(@NotNull Number amount) throws AblyException;

    // ---- Subscriptions ----------------------------------------------------

    @NonBlocking
    @NotNull
    ObjectsSubscription subscribe(@NotNull PathChangeListener listener);

    @NonBlocking
    @NotNull
    ObjectsSubscription subscribe(@NotNull PathChangeListener listener,
                                  @NotNull PathSubscriptionOptions options);

    // ---- Async write variants ---------------------------------------------
    // No async variants for reads: reads are already non-blocking.

    @NonBlocking
    void setAsync(@NotNull String key, @NotNull LiveValue value,
                  @NotNull ObjectsCallback<Void> callback);

    @NonBlocking
    void removeAsync(@NotNull String key, @NotNull ObjectsCallback<Void> callback);

    @NonBlocking
    void incrementAsync(@NotNull Number amount, @NotNull ObjectsCallback<Void> callback);

    @NonBlocking
    void decrementAsync(@NotNull Number amount, @NotNull ObjectsCallback<Void> callback);
}
