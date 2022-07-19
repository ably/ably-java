package io.ably.lib.test.util;

import io.ably.lib.platform.Platform;
import io.ably.lib.push.PushBase;
import io.ably.lib.realtime.AblyRealtimeBase;
import io.ably.lib.realtime.RealtimeChannelBase;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.rest.RestChannelBase;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

public interface AblyInstanceCreator {
    AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> createAblyRealtime(String key) throws AblyException;
    AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> createAblyRealtime(ClientOptions options) throws AblyException;

    AblyBase<PushBase, Platform, RestChannelBase> createAblyRest(String key) throws AblyException;
    AblyBase<PushBase, Platform, RestChannelBase> createAblyRest(ClientOptions options) throws AblyException;
    AblyBase<PushBase, Platform, RestChannelBase> createAblyRest(ClientOptions options, long mockedTime) throws AblyException;
}
