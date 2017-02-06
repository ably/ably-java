package io.ably.lib.rest;

import io.ably.lib.rest.AblyRestBase;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

public class AblyRest extends AblyRestBase {
    public AblyRest(String key) throws AblyException {
        super(key);
    }

    public AblyRest(ClientOptions options) throws AblyException {
        super(options);
    }
}
