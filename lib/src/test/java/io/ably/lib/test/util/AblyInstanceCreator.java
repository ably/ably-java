package io.ably.lib.test.util;

import io.ably.lib.platform.PlatformBase;
import io.ably.lib.push.PushBase;
import io.ably.lib.realtime.AblyRealtimeBase;
import io.ably.lib.realtime.RealtimeChannelBase;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.rest.RestChannelBase;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

public interface AblyInstanceCreator {
    AblyRealtimeBase<PushBase, PlatformBase, RealtimeChannelBase> createAblyRealtime(String key) throws AblyException;
    AblyRealtimeBase<PushBase, PlatformBase, RealtimeChannelBase> createAblyRealtime(ClientOptions options) throws AblyException;

    AblyBase<PushBase, PlatformBase, RestChannelBase> createAblyRest(String key) throws AblyException;
    AblyBase<PushBase, PlatformBase, RestChannelBase> createAblyRest(ClientOptions options) throws AblyException;
    AblyBase<PushBase, PlatformBase, RestChannelBase> createAblyRest(ClientOptions options, long mockedTime) throws AblyException;
}
