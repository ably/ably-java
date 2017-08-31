package io.ably.lib.http;

import io.ably.lib.util.CurrentThreadExecutor;

/**
 * Created by tcard on 31/8/17.
 */

public class SyncHttp extends CallbackfulHttp<CurrentThreadExecutor> {

    public SyncHttp(Http http) {
        super(http, CurrentThreadExecutor.INSTANCE);
    }
}
