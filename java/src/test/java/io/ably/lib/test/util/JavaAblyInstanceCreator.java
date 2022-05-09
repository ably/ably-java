package io.ably.lib.test.util;

import io.ably.lib.realtime.AblyRealtimeBase;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

public class JavaAblyInstanceCreator implements AblyInstanceCreator {
    @Override
    public AblyRealtimeBase createAblyRealtime(String key) throws AblyException {
        return new AblyRealtime(key);
    }

    @Override
    public AblyRealtimeBase createAblyRealtime(ClientOptions options) throws AblyException {
        return new AblyRealtime(options);
    }

    @Override
    public AblyBase createAblyRest(String key) throws AblyException {
        return new AblyRest(key);
    }

    @Override
    public AblyBase createAblyRest(ClientOptions options) throws AblyException {
        return new AblyRest(options);
    }

    @Override
    public AblyBase createAblyRest(ClientOptions options, final long mockedTime) throws AblyException {
        return new AblyRest(options) {
            @Override
            public long time() throws AblyException {
                return mockedTime;
            }
        };
    }
}
