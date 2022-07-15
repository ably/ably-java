package io.ably.core.test.util;

import io.ably.core.realtime.AblyRealtimeBase;
import io.ably.core.realtime.AblyRealtime;
import io.ably.core.rest.AblyBase;
import io.ably.core.rest.AblyRest;
import io.ably.core.types.AblyException;
import io.ably.core.types.ClientOptions;

public class AndroidAblyInstanceCreator implements AblyInstanceCreator {
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
