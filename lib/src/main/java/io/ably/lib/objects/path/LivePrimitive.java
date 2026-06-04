package io.ably.lib.objects.path;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Wrapper for primitive leaf values stored at a path. Mirrors the existing
 * {@link io.ably.lib.objects.type.map.LiveMapValue} union, minus the
 * live-object variants (those are represented by {@link LiveInstance}).
 * <p>
 * The supported underlying types are {@code String}, {@code Number},
 * {@code Boolean}, {@code byte[]}, {@link JsonArray} and {@link JsonObject}
 * (spec RTO11a1).
 * <p>
 * Logically sealed.
 */
@ApiStatus.NonExtendable
public interface LivePrimitive extends LiveValue {

    // ---- Static factories --------------------------------------------------

    @NotNull
    static LivePrimitive of(@NotNull String value) {
        return LiveValues.primitive(value);
    }

    @NotNull
    static LivePrimitive of(@NotNull Number value) {
        return LiveValues.primitive(value);
    }

    @NotNull
    static LivePrimitive of(boolean value) {
        return LiveValues.primitive(value);
    }

    @NotNull
    static LivePrimitive of(byte @NotNull [] value) {
        return LiveValues.primitive(value);
    }

    @NotNull
    static LivePrimitive of(@NotNull JsonArray value) {
        return LiveValues.primitive(value);
    }

    @NotNull
    static LivePrimitive of(@NotNull JsonObject value) {
        return LiveValues.primitive(value);
    }

    // ---- Raw access --------------------------------------------------------

    /** Boxed underlying value, same shape as {@link io.ably.lib.objects.type.map.LiveMapValue#getValue()}. */
    @NotNull
    Object raw();

    // ---- Type checks (mirror LiveMapValue.isXxx) ---------------------------

    boolean isString();
    boolean isNumber();
    boolean isBoolean();
    boolean isBinary();
    boolean isJsonArray();
    boolean isJsonObject();

    // ---- Typed accessors (throw IllegalStateException on mismatch) ---------

    @NotNull String     asString();
    @NotNull Number     asNumber();
    boolean             asBoolean();
    byte @NotNull []    asBinary();
    @NotNull JsonArray  asJsonArray();
    @NotNull JsonObject asJsonObject();
}
