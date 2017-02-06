package io.ably.lib.rest;

import io.ably.lib.rest.AblyRestBase;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

public class AblyRest extends AblyRestBase {
    public DeviceDetails device;
    public final Push push;

    public AblyRest(ClientOptions options) throws AblyException {
        super(options);
        this.push = new Push(this);
    }

    public AblyRest(String key) throws AblyException {
        super(key);
        this.push = new Push(this);
    }
}
