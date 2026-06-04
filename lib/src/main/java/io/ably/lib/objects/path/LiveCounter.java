package io.ably.lib.objects.path;

import org.jetbrains.annotations.NotNull;

/**
 * Public factory for LiveCounter creation tokens used by atomic deep-create
 * {@code PathObject#set(key, LiveCounter.create(...))}.
 * <p>
 * The runtime instance type is {@link LiveCounterInstance}.
 */
public final class LiveCounter {

    private LiveCounter() { /* factory only */ }

    /** Create a LiveCounter initialised to zero. */
    @NotNull
    public static LiveValue create() {
        return create(0);
    }

    /** Create a LiveCounter initialised to {@code initialValue}. */
    @NotNull
    public static LiveValue create(@NotNull Number initialValue) {
        return LiveCreate.counter(initialValue);
    }
}
