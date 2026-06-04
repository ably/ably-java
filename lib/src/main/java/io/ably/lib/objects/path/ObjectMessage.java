package io.ably.lib.objects.path;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only public view of an Objects operation message as observed by a
 * path-level subscriber. Adapts the internal Kotlin {@code ObjectMessage}
 * (in the {@code liveobjects} module, not exposed) to a stable Java surface.
 */
@ApiStatus.NonExtendable
public interface ObjectMessage {

    @Nullable String clientId();
    @Nullable String connectionId();

    /** Server-assigned operation serial (used for ordering / dedup). */
    @Nullable String serial();

    @Nullable Long   timestamp(); // millis since epoch
    @Nullable JsonObject extras();

    /** The operation that caused this change. */
    @NotNull Operation operation();

    /** Kinds of operations dispatched to path subscribers. */
    enum Action {
        MAP_CREATE,
        MAP_SET,
        MAP_REMOVE,
        COUNTER_CREATE,
        COUNTER_INC,
        OBJECT_DELETE
    }

    @ApiStatus.NonExtendable
    interface Operation {
        @NotNull Action action();

        /** Set when {@link #action()} is {@link Action#MAP_SET}. */
        @Nullable String     mapSetKey();
        @Nullable LiveValue  mapSetValue();

        /** Set when {@link #action()} is {@link Action#MAP_REMOVE}. */
        @Nullable String     mapRemoveKey();

        /** Set when {@link #action()} is {@link Action#COUNTER_INC}. */
        @Nullable Number     counterIncNumber();

        /** Object ID for create / delete operations. */
        @Nullable String     objectId();
    }
}
