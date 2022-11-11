package io.ably.lib.rest;

import android.content.Context;
import io.ably.lib.platform.AndroidPlatform;
import io.ably.lib.push.LocalDevice;
import io.ably.lib.push.Push;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.util.AndroidPlatformAgentProvider;
import io.ably.lib.util.Log;

/**
 * A client that offers a simple stateless API to interact directly with Ably's REST API.
 *
 * This class implements {@link AutoCloseable} so you can use it in
 * try-with-resources constructs and have the JDK close it for you.
 */
public class AblyRest extends AblyBase<Push, AndroidPlatform, Channel> {

    /**
     * Constructs a client object using an Ably API key or token string.
     * <p>
     * Spec: RSC1
     * @param key The Ably API key or token string used to validate the client.
     * @throws AblyException
     */
    public AblyRest(String key) throws AblyException {
        super(key, new AndroidPlatformAgentProvider());
    }

    /**
     * Construct a client object using an Ably {@link ClientOptions} object.
     * <p>
     * Spec: RSC1
     * @param options A {@link ClientOptions} object to configure the client connection to Ably.
     * @throws AblyException
     */
    public AblyRest(ClientOptions options) throws AblyException {
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
    protected RestChannelBase createChannel(AblyBase<Push, AndroidPlatform, Channel> ablyBase, String channelName, ChannelOptions channelOptions) throws AblyException {
        return new Channel(ablyBase, channelName, channelOptions);
    }

    /**
     * Retrieves a {@link LocalDevice} object that represents the current state of the device as a target for push notifications.
     * <p>
     * Spec: RSH8
     * @return A {@link LocalDevice} object.
     * @throws AblyException
     */
    public LocalDevice device() throws AblyException {
        return this.push.getLocalDevice();
    }

    /**
     * Set the Android Context for this instance
     */
    public void setAndroidContext(Context context) throws AblyException {
        Log.v(TAG, "setAndroidContext(): context=" + context);
        this.platform.setAndroidContext(context);
        this.push.tryRequestRegistrationToken();
    }

    /**
     * clientId set by late initialisation
     */
    protected void onClientIdSet(String clientId) {
        Log.v(TAG, "onClientIdSet(): clientId=" + clientId);
        /* we only need to propagate any update to clientId if this is a late init */
        if(push != null && platform.hasApplicationContext()) {
            try {
                push.getActivationContext().setClientId(clientId, true);
            } catch(AblyException ae) {
                Log.e(TAG, "unable to update local device state");
            }
        }
    }

    private static final String TAG = AblyRest.class.getName();
}
