package io.ably.lib.rest;

import android.content.Context;
import io.ably.lib.rest.AblyRestBase;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

public class AblyRest extends AblyRestBase {
    public final Push push;

    public AblyRest(ClientOptions options) throws AblyException {
        super(options);
        this.push = new Push(this);
    }

    public AblyRest(String key) throws AblyException {
        super(key);
        this.push = new Push(this);
    }

    private LocalDevice _device;

    public LocalDevice device(Context context) {
        if (_device == null) {
            _device = LocalDevice.load(context, this);
        }
        return _device;
    }
}
