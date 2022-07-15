package io.ably.core.test.util;

import io.ably.core.platform.Platform;
import io.ably.core.push.PushBase;
import io.ably.core.realtime.AblyRealtimeBase;
import io.ably.core.realtime.RealtimeChannelBase;
import io.ably.core.rest.AblyBase;
import io.ably.core.rest.RestChannelBase;
import io.ably.core.types.AblyException;
import io.ably.core.types.ClientOptions;

public interface AblyInstanceCreator {
    AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> createAblyRealtime(String key) throws AblyException;
    AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> createAblyRealtime(ClientOptions options) throws AblyException;

    AblyBase<PushBase, Platform, RestChannelBase> createAblyRest(String key) throws AblyException;
    AblyBase<PushBase, Platform, RestChannelBase> createAblyRest(ClientOptions options) throws AblyException;
    AblyBase<PushBase, Platform, RestChannelBase> createAblyRest(ClientOptions options, long mockedTime) throws AblyException;
}
