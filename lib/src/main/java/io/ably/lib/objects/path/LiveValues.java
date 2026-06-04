package io.ably.lib.objects.path;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.ably.lib.objects.type.counter.LiveCounter;
import io.ably.lib.objects.type.map.LiveMap;
import io.ably.lib.objects.type.map.LiveMapValue;
import org.jetbrains.annotations.NotNull;

/**
 * Package-private factory / bridge between the public {@link LiveValue}
 * hierarchy and the spec-internal {@link LiveMapValue} union. Centralising
 * the conversion here keeps the public types small and avoids duplicating
 * the union in two places.
 */
final class LiveValues {

    private LiveValues() { /* no instances */ }

    // ---- LivePrimitive factories ------------------------------------------

    @NotNull static LivePrimitive primitive(@NotNull String  v) { return new PrimitiveImpl(LiveMapValue.of(v)); }
    @NotNull static LivePrimitive primitive(@NotNull Number  v) { return new PrimitiveImpl(LiveMapValue.of(v)); }
    @NotNull static LivePrimitive primitive(boolean          v) { return new PrimitiveImpl(LiveMapValue.of(Boolean.valueOf(v))); }
    @NotNull static LivePrimitive primitive(byte @NotNull [] v) { return new PrimitiveImpl(LiveMapValue.of(v)); }
    @NotNull static LivePrimitive primitive(@NotNull JsonArray  v) { return new PrimitiveImpl(LiveMapValue.of(v)); }
    @NotNull static LivePrimitive primitive(@NotNull JsonObject v) { return new PrimitiveImpl(LiveMapValue.of(v)); }

    /**
     * Wrap an internal {@link LiveMapValue} as the appropriate {@link LiveValue}.
     * <ul>
     *   <li>{@code LiveCounter}-typed values become a {@link LiveCounterInstance}
     *       (the wrapped value is already an instance of the internal
     *       {@link LiveCounter}, which the path-API instance extends).</li>
     *   <li>{@code LiveMap}-typed values become a {@link LiveMapInstance} (same idea).</li>
     *   <li>Everything else becomes a {@link LivePrimitive}.</li>
     * </ul>
     */
    @NotNull
    static LiveValue from(@NotNull LiveMapValue value) {
        if (value.isLiveCounter()) {
            LiveCounter c = value.getAsLiveCounter();
            if (c instanceof LiveCounterInstance) {
                return (LiveCounterInstance) c;
            }
            // Internal LiveCounter that doesn't yet implement LiveCounterInstance
            // — wrap in a thin adapter. Implemented in the liveobjects module;
            // for now this branch should not be hit because DefaultLiveCounter
            // will be updated to implement LiveCounterInstance.
            throw new IllegalStateException(
                "Internal LiveCounter does not implement LiveCounterInstance: " + c.getClass());
        }
        if (value.isLiveMap()) {
            LiveMap m = value.getAsLiveMap();
            if (m instanceof LiveMapInstance) {
                return (LiveMapInstance) m;
            }
            throw new IllegalStateException(
                "Internal LiveMap does not implement LiveMapInstance: " + m.getClass());
        }
        return new PrimitiveImpl(value);
    }

    // ---- PrimitiveImpl -----------------------------------------------------

    /**
     * Trivial {@link LivePrimitive} delegating to an underlying {@link LiveMapValue}.
     * Keeps the union in one place: {@code LiveMapValue} already knows how to
     * box / type-check every primitive variant.
     */
    static final class PrimitiveImpl implements LivePrimitive {
        private final LiveMapValue delegate;

        PrimitiveImpl(@NotNull LiveMapValue delegate) {
            this.delegate = delegate;
        }

        @Override public @NotNull LiveMapValue toMapValue() { return delegate; }

        @Override public @NotNull Object raw() { return delegate.getValue(); }

        @Override public boolean isString()     { return delegate.isString(); }
        @Override public boolean isNumber()     { return delegate.isNumber(); }
        @Override public boolean isBoolean()    { return delegate.isBoolean(); }
        @Override public boolean isBinary()     { return delegate.isBinary(); }
        @Override public boolean isJsonArray()  { return delegate.isJsonArray(); }
        @Override public boolean isJsonObject() { return delegate.isJsonObject(); }

        @Override public @NotNull String     asString()     { return delegate.getAsString(); }
        @Override public @NotNull Number     asNumber()     { return delegate.getAsNumber(); }
        @Override public boolean             asBoolean()    { return delegate.getAsBoolean().booleanValue(); }
        @Override public byte @NotNull []    asBinary()     { return delegate.getAsBinary(); }
        @Override public @NotNull JsonArray  asJsonArray()  { return delegate.getAsJsonArray(); }
        @Override public @NotNull JsonObject asJsonObject() { return delegate.getAsJsonObject(); }

        @Override public String toString() {
            return "LivePrimitive{" + delegate.getValue() + "}";
        }
    }
}
