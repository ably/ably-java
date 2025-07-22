package io.ably.lib.objects.type;

public abstract class LiveObjectUpdate {
    protected final Object update;

    protected LiveObjectUpdate(Object update) {
        this.update = update;
    }
}
