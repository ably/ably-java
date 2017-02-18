package io.ably.lib.rest;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpUtils;
import com.google.gson.JsonObject;
import android.content.Context;

public class Channel extends ChannelBase {
    Channel(AblyRest ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
        push = new PushChannel(this, ably);
    }

    public final PushChannel push;

    public static class PushChannel {
        private final Channel channel;
        private final AblyRest rest;

        PushChannel(Channel channel, AblyRest rest) {
            this.channel = channel;
            this.rest = rest;
        }

        public void subscribeDevice(Context context) throws AblyException {
            postSubscription(subscribeDeviceBody(context));
        }

        public void subscribeDeviceAsync(Context context, CompletionListener listener) {
            try {
                postSubscriptionAsync(subscribeDeviceBody(context), listener);
            } catch (AblyException e) {
                listener.onError(e.errorInfo);
            }
        }

        public void subscribeClient() throws AblyException {
            postSubscription(subscribeClientBody());
        }

        public void subscribeClientAsync(CompletionListener listener) {
            try {
                postSubscriptionAsync(subscribeClientBody(), listener);
            } catch (AblyException e) {
                listener.onError(e.errorInfo);
            }
        }

        public void unsubscribeDevice(Context context) throws AblyException {
            DeviceDetails device = getDevice(context);
            rest.http.del("/push/channelSubscriptions?channel=" + channel.name + "&deviceId=" + device.id, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, null);
        }

        public void unsubscribeDeviceAsync(Context context, CompletionListener listener) throws AblyException {
            try {
                DeviceDetails device = getDevice(context);
                rest.asyncHttp.del("/push/channelSubscriptions?channel=" + channel.name + "&deviceId=" + device.id, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, null, new CompletionListener.ToCallback(listener));
            } catch (AblyException e) {
                listener.onError(e.errorInfo);
            }
        }

        public void unsubscribeClient() throws AblyException {
            String clientId = getClientId();
            rest.http.del("/push/channelSubscriptions?channel=" + channel.name + "&clientId=" + clientId, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, null);
        }

        public void unsubscribeClientAsync(CompletionListener listener) throws AblyException {
            try {
                String clientId = getClientId();
                rest.asyncHttp.del("/push/channelSubscriptions?channel=" + channel.name + "&clientId=" + clientId, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, null, new CompletionListener.ToCallback(listener));
            } catch (AblyException e) {
                listener.onError(e.errorInfo);
            }
        }

        private Http.RequestBody subscribeDeviceBody(Context context) throws AblyException {
            DeviceDetails device = getDevice(context);
            JsonObject bodyJson = new JsonObject();
            bodyJson.addProperty("deviceId", device.id);
            return subscriptionRequestBody(bodyJson);
        }

        private Http.RequestBody subscribeClientBody() throws AblyException {
            JsonObject bodyJson = new JsonObject();
            bodyJson.addProperty("clientId", getClientId());
            return subscriptionRequestBody(bodyJson);
        }

        private Http.RequestBody subscriptionRequestBody(JsonObject bodyJson) {
            bodyJson.addProperty("channel", channel.name);
            return rest.http.requestBodyFromGson(bodyJson);
        }

        private void postSubscription(Http.RequestBody body) throws AblyException {
            rest.http.post("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, null);
        }

        private void postSubscriptionAsync(Http.RequestBody body, final CompletionListener listener) throws AblyException {
            rest.asyncHttp.post("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, null, new CompletionListener.ToCallback(listener));
        }

        private DeviceDetails getDevice(Context context) throws AblyException {
            DeviceDetails device = rest.device(context);
            if (device == null || device.updateToken == null) {
                // Alternatively, we could store a queue of pending subscriptions in the
                // device storage. But then, in order to know if this subscription operation
                // succeeded, you would have to add a BroadcastReceiver in AndroidManifest.xml.
                // Arguably that encourages just ignoring any errors, and forcing you to listen
                // to the broadcast after push.activate has finished before subscribing is
                // more robust.
                throw AblyException.fromThrowable(new Exception("cannot subscribe device before AblyRest.push.activate has finished"));
            }
            return device;
        }

        private String getClientId() throws AblyException {
            String clientId = rest.auth.clientId;
            if (clientId == null) {
                throw AblyException.fromThrowable(new Exception("cannot subscribe from REST client with null client ID"));
            }
            return clientId;
        }
    }
}

