package io.ably.lib.rest;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.Callback;
import io.ably.lib.types.Param;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.http.PaginatedQuery;
import io.ably.lib.http.AsyncPaginatedQuery;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpUtils;
import com.google.gson.JsonObject;
import android.content.Context;

public class PushChannel {
    protected final Channel channel;
    protected final AblyRest rest;

    PushChannel(Channel channel, AblyRest rest) {
        this.channel = channel;
        this.rest = rest;
    }

    public void subscribeClient(Context context) throws AblyException {
        postSubscription(subscribeClientBody(context));
    }

    public void subscribeClientAsync(Context context, CompletionListener listener) {
        try {
            postSubscriptionAsync(subscribeClientBody(context), listener);
        } catch (AblyException e) {
            listener.onError(e.errorInfo);
        }
    }

    public void unsubscribeClient(Context context) throws AblyException {
        Param[] params = new Param[] { new Param("channel", channel.name), new Param("clientId", getClientId(context)) };
        rest.http.del("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, null, true);
    }

    public void unsubscribeClientAsync(Context context, CompletionListener listener) throws AblyException {
        try {
            Param[] params = new Param[] { new Param("channel", channel.name), new Param("clientId", getClientId(context)) };
            rest.asyncHttp.del("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, null, true, new CompletionListener.ToCallback(listener));
        } catch (AblyException e) {
            listener.onError(e.errorInfo);
        }
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

    public void unsubscribeDevice(Context context) throws AblyException {
        DeviceDetails device = getDevice(context);
        Param[] params = new Param[] { new Param("channel", channel.name), new Param("deviceId", device.id) };
        rest.http.del("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, null, true);
    }

    public void unsubscribeDeviceAsync(Context context, CompletionListener listener) throws AblyException {
        try {
            DeviceDetails device = getDevice(context);
            Param[] params = new Param[] { new Param("channel", channel.name), new Param("deviceId", device.id) };
            rest.asyncHttp.del("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, null, true, new CompletionListener.ToCallback(listener));
        } catch (AblyException e) {
            listener.onError(e.errorInfo);
        }
    }

    public PaginatedResult<Push.ChannelSubscription> listSubscriptions(Context context) throws AblyException {
        return listSubscriptions(context, new Param[] {});
    }

    public PaginatedResult<Push.ChannelSubscription> listSubscriptions(Context context, Param[] params) throws AblyException {
        params = setListSubscriptionsParams(context, params);
        return new PaginatedQuery<Push.ChannelSubscription>(rest.http, "/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, Push.ChannelSubscription.httpBodyHandler).get();
    }

    public void listSubscriptionsAsync(Callback<AsyncPaginatedResult<Push.ChannelSubscription>> callback) throws AblyException {
        listSubscriptionsAsync(new Param[] {}, callback);
    }

    public void listSubscriptionsAsync(Param[] params, Callback<AsyncPaginatedResult<Push.ChannelSubscription>> callback) throws AblyException {
        params = Param.set(params, "channel", channel.name);
        new AsyncPaginatedQuery<Push.ChannelSubscription>(rest.asyncHttp, "/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, Push.ChannelSubscription.httpBodyHandler).get(callback);
    }

    protected Param[] setListSubscriptionsParams(Context context, Param[] params) throws AblyException {
        params = Param.set(params, "channel", channel.name);
        params = Param.set(params, "deviceId", getDevice(context).id);
        String clientId = rest.auth.clientId;
        if (clientId != null) {
            params = Param.set(params, "clientId", clientId);
        }
        params = Param.set(params, "concatFilters", "true");
        return params;
    } 

    protected Http.RequestBody subscribeClientBody(Context context) throws AblyException {
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("clientId", getClientId(context));
        return subscriptionRequestBody(bodyJson);
    }

    protected Http.RequestBody subscriptionRequestBody(JsonObject bodyJson) {
        bodyJson.addProperty("channel", channel.name);
        return rest.http.requestBodyFromGson(bodyJson);
    }

    protected void postSubscription(Http.RequestBody body) throws AblyException {
        rest.http.post("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, null, true);
    }

    protected void postSubscriptionAsync(Http.RequestBody body, final CompletionListener listener) throws AblyException {
        rest.asyncHttp.post("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, null, true, new CompletionListener.ToCallback(listener));
    }

    protected String getClientId(Context context) throws AblyException {
        String clientId = getDevice(context).clientId;
        if (clientId == null) {
            throw AblyException.fromThrowable(new Exception("cannot subscribe with null client ID"));
        }
        return clientId;
    }

    protected Http.RequestBody subscribeDeviceBody(Context context) throws AblyException {
        DeviceDetails device = getDevice(context);
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("deviceId", device.id);
        return subscriptionRequestBody(bodyJson);
    }

    protected DeviceDetails getDevice(Context context) throws AblyException {
        DeviceDetails device = rest.device(context);
        if (device == null || device.updateToken == null) {
            // Alternatively, we could store a queue of pending subscriptions in the
            // device storage. But then, in order to know if this subscription operation
            // succeeded, you would have to add a BroadcastReceiver in AndroidManifest.xml.
            // Arguably that encourages just ignoring any errors, and forcing you to listen
            // to the broadcast after push.activate has finished before subscribing is
            // more robust.
            throw AblyException.fromThrowable(new Exception("cannot use device before AblyRest.push.activate has finished"));
        }
        return device;
    }
}

