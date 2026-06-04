package io.ably.lib.objects.path;

import io.ably.lib.objects.type.map.LiveMapValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Root marker for anything that can sit at a path in the LiveObjects tree.
 * <p>
 * Logically sealed: the only valid implementations are bundled with the SDK
 * ({@link LivePrimitive}, {@link LiveMapInstance}, {@link LiveCounterInstance},
 * and the internal {@code MapCreate} / {@code CounterCreate} creation tokens).
 * The interface is annotated {@link ApiStatus.NonExtendable} — third parties
 * must not implement it.
 * <p>
 * Spec: see <a href="https://sdk.ably.com/builds/ably/specification/main/objects-features/">Objects feature spec</a>.
 */
@ApiStatus.NonExtendable
public interface LiveValue {

    /**
     * Bridge to the spec-aligned {@link LiveMapValue} union used by the
     * underlying internal LiveMap representation. Never null.
     */
    @NotNull
    LiveMapValue toMapValue();

    /**
     * Inverse bridge — wraps a {@link LiveMapValue} as a path-API {@link LiveValue}.
     */
    @NotNull
    static LiveValue fromMapValue(@NotNull LiveMapValue value) {
        return LiveValues.from(value);
    }
}
