package io.ably.lib.objects;

import io.ably.lib.types.Callback;

public interface LiveObjects {
    Long getRoot();
    void getRootAsync(Callback<Long> callback);
}
