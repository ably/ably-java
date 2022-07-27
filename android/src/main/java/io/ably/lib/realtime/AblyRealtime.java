package io.ably.lib.realtime;

import android.content.Context;
import io.ably.lib.platform.AndroidPlatform;
import io.ably.lib.push.LocalDevice;
import io.ably.lib.push.Push;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.util.AndroidPlatformAgentProvider;
import io.ably.lib.util.Log;

public class AblyRealtime extends AblyRealtimeBase<Push, AndroidPlatform, Channel> {
    public AblyRealtime(String key) throws AblyException {
        super(key, new AndroidPlatformAgentProvider());
    }

    public AblyRealtime(ClientOptions options) throws AblyException {
        super(options, new AndroidPlatformAgentProvider());
    }

    @Override
    protected AndroidPlatform createPlatform() {
        return new AndroidPlatform();
    }

    @Override
    protected Push createPush() {
        return new Push((AblyBase) this);
    }

    @Override
    protected RealtimeChannelBase createChannel(AblyRealtimeBase ablyRealtime, String channelName, ChannelOptions channelOptions) throws AblyException {
        return new Channel(ablyRealtime, channelName, channelOptions);
    }

    /**
     * Get the local device, if any
     *
     * @return an instance of LocalDevice, or null if this device is not capable of activation as a push target
     * @throws AblyException
     */
    public LocalDevice device() throws AblyException {
        return push.getLocalDevice();
    }

    /**
     * Set the Android Context for this instance
     */
    public void setAndroidContext(Context context) throws AblyException {
        Log.v(TAG, "setAndroidContext(): context=" + context);
        platform.setAndroidContext(context);
        push.tryRequestRegistrationToken();
    }

    /**
     * clientId set by late initialisation
     */
    protected void onClientIdSet(String clientId) {
        Log.v(TAG, "onClientIdSet(): clientId=" + clientId);
        /* we only need to propagate any update to clientId if this is a late init */
        if (push != null && platform.hasApplicationContext()) {
            try {
                push.getActivationContext().setClientId(clientId, true);
            } catch (AblyException ae) {
                Log.e(TAG, "unable to update local device state");
            }
        }
    }

    private static final String TAG = AblyRealtime.class.getName();
}