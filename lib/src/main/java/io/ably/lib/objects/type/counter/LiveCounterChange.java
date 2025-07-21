package io.ably.lib.objects.type.counter;

import io.ably.lib.objects.ObjectsSubscription;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;

public interface LiveCounterChange {

    @NonBlocking
    @NotNull ObjectsSubscription subscribe(@NotNull Listener listener);

    @NonBlocking
    void unsubscribe(@NotNull Listener listener);

    @NonBlocking
    void unsubscribeAll();

    interface Listener {
        void onUpdated(@NotNull LiveCounterUpdate update);
    }
}
