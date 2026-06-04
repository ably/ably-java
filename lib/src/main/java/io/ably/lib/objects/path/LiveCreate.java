package io.ably.lib.objects.path;

import io.ably.lib.objects.type.map.LiveMapValue;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Package-private factory for the two creation-token kinds — kept here so
 * the public {@link LiveMap} / {@link LiveCounter} factories don't have to
 * expose any implementation classes.
 * <p>
 * Both tokens implement {@link LiveValue} so they can be passed straight to
 * {@code PathObject#set(...)}. They have no object identity until the
 * enclosing wire operation lands.
 */
final class LiveCreate {

    private LiveCreate() { /* no instances */ }

    @NotNull
    static LiveValue map(@NotNull Map<String, LiveValue> entries) {
        return new MapCreateToken(entries);
    }

    @NotNull
    static LiveValue counter(@NotNull Number initialValue) {
        return new CounterCreateToken(initialValue);
    }

    /** Tag interface for the two token kinds — read by the impl module. */
    interface CreationToken extends LiveValue {}

    static final class MapCreateToken implements CreationToken {
        final Map<String, LiveValue> entries;

        MapCreateToken(@NotNull Map<String, LiveValue> entries) {
            this.entries = entries;
        }

        @Override
        public @NotNull LiveMapValue toMapValue() {
            throw new UnsupportedOperationException(
                "MapCreateToken is a creation token; it has no LiveMapValue until the operation lands");
        }

        @Override public String toString() { return "MapCreate" + entries.keySet(); }
    }

    static final class CounterCreateToken implements CreationToken {
        final Number initialValue;

        CounterCreateToken(@NotNull Number initialValue) {
            this.initialValue = initialValue;
        }

        @Override
        public @NotNull LiveMapValue toMapValue() {
            throw new UnsupportedOperationException(
                "CounterCreateToken is a creation token; it has no LiveMapValue until the operation lands");
        }

        @Override public String toString() { return "CounterCreate(" + initialValue + ")"; }
    }
}
