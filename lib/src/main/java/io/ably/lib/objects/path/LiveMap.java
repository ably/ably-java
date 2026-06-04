package io.ably.lib.objects.path;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Public factory for LiveMap creation tokens used by atomic deep-create
 * {@code PathObject#set(key, LiveMap.create(...))}.
 * <p>
 * Note: this class is intentionally a factory, NOT the instance type.
 * The runtime instance type is {@link LiveMapInstance}, which extends the
 * internal {@link io.ably.lib.objects.type.map.LiveMap}. Naming follows the
 * Ably docs (which expose {@code LiveMap.create(...)}); see PR #1190
 * review for the rationale.
 * <p>
 * The returned {@link LiveValue} is a creation token — it has no object
 * identity until the enclosing {@code set} operation lands.
 */
public final class LiveMap {

    private LiveMap() { /* factory only */ }

    /** Create an empty LiveMap. */
    @NotNull
    public static LiveValue create() {
        return create(Collections.<String, LiveValue>emptyMap());
    }

    /**
     * Create a LiveMap with the given nested initial contents. Values may be
     * {@link LivePrimitive}s, other {@code LiveMap.create(...)} tokens, or
     * {@code LiveCounter.create(...)} tokens.
     */
    @NotNull
    public static LiveValue create(@NotNull Map<String, LiveValue> entries) {
        // Defensive copy; downstream impl consumes lazily during the wire op.
        Map<String, LiveValue> snapshot = new HashMap<String, LiveValue>(entries);
        return LiveCreate.map(snapshot);
    }
}
