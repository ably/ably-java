package io.ably.lib.rest;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Callback;
import io.ably.lib.types.Param;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.http.PaginatedQuery;
import io.ably.lib.http.BasePaginatedQuery;
import io.ably.lib.http.AsyncPaginatedQuery;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpScheduler;
import io.ably.lib.http.HttpCore;
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
        subscribeClientImpl(context).sync();
    }

    public void subscribeClientAsync(Context context, CompletionListener listener) {
        subscribeClientImpl(context).async(new CompletionListener.ToCallback(listener));
    }

    protected Http.Request<Void> subscribeClientImpl(Context context) {
        JsonObject bodyJson = new JsonObject();
        try {
            bodyJson.addProperty("clientId", getClientId(context));
        } catch (AblyException e) {
            return rest.http.failedRequest(e);
        }

        return postSubscription(bodyJson);
    }

    public void subscribeDevice(Context context) throws AblyException {
        subscribeDeviceImpl(context).sync();
    }

    public void subscribeDeviceAsync(Context context, CompletionListener listener) {
        subscribeDeviceImpl(context).async(new CompletionListener.ToCallback(listener));
    }

    protected Http.Request<Void> subscribeDeviceImpl(Context context) {
        try {
            DeviceDetails device = getDevice(context);
            JsonObject bodyJson = new JsonObject();
            bodyJson.addProperty("deviceId", device.id);

            return postSubscription(bodyJson);
        } catch(AblyException e) {
            return rest.http.failedRequest(e);
        }
    }

    protected Http.Request<Void> postSubscription(JsonObject bodyJson) {
        bodyJson.addProperty("channel", channel.name);
        final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(bodyJson, rest.options.useBinaryProtocol);

        return rest.http.request(new Http.Execute<Void>() {
            @Override
            public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                Param[] params = null;
                if (rest.options.pushFullWait) {
                    params = Param.push(params, "fullWait", "true");
                }
                http.post("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, body, null, true, callback);
            }
        });
    }

    public void unsubscribeClient(Context context) throws AblyException {
        unsubscribeClientImpl(context).sync();
    }

    public void unsubscribeClientAsync(Context context, CompletionListener listener) {
        unsubscribeClientImpl(context).async(new CompletionListener.ToCallback(listener));
    }

    protected Http.Request<Void> unsubscribeClientImpl(Context context) {
        try {
            Param[] params = new Param[] { new Param("channel", channel.name), new Param("clientId", getClientId(context)) };
            return delSubscription(params);
        } catch(AblyException e) {
            return rest.http.failedRequest(e);
        }
    }

    public void unsubscribeDevice(Context context) throws AblyException {
        unsubscribeDeviceImpl(context).sync();
    }

    public void unsubscribeDeviceAsync(Context context, CompletionListener listener) {
        unsubscribeDeviceImpl(context).async(new CompletionListener.ToCallback(listener));
    }

    protected Http.Request<Void> unsubscribeDeviceImpl(Context context) {
        try {
            DeviceDetails device = getDevice(context);
            Param[] params = new Param[] { new Param("channel", channel.name), new Param("deviceId", device.id) };
            return delSubscription(params);
        } catch(AblyException e) {
            return rest.http.failedRequest(e);
        }
    }

    protected Http.Request<Void> delSubscription(Param[] params) {
        if (rest.options.pushFullWait) {
            params = Param.push(params, "fullWait", "true");
        }
        final Param[] finalParams = params;
        return rest.http.request(new Http.Execute<Void>() {
            @Override
            public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                http.del("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), finalParams, null, true, callback);
            }
        });
    }

    public PaginatedResult<Push.ChannelSubscription> listSubscriptions(Context context) throws AblyException {
        return listSubscriptions(context, new Param[] {});
    }

    public PaginatedResult<Push.ChannelSubscription> listSubscriptions(Context context, Param[] params) throws AblyException {
        return listSubscriptionsImpl(context, params).sync();
    }

    public void listSubscriptionsAsync(Context context, Callback<AsyncPaginatedResult<Push.ChannelSubscription>> callback) {
        listSubscriptionsAsync(context, new Param[] {}, callback);
    }

    public void listSubscriptionsAsync(Context context, Param[] params, Callback<AsyncPaginatedResult<Push.ChannelSubscription>> callback) {
        listSubscriptionsImpl(context, params).async(callback);
    }

    protected BasePaginatedQuery.ResultRequest<Push.ChannelSubscription> listSubscriptionsImpl(Context context, Param[] params) {
        try {
            params = Param.set(params, "deviceId", getDevice(context).id);
        } catch(AblyException e) {
            return new BasePaginatedQuery.ResultRequest.Failed(e);
        }
        params = Param.set(params, "channel", channel.name);
        String clientId = rest.auth.clientId;
        if (clientId != null) {
            params = Param.set(params, "clientId", clientId);
        }
        params = Param.set(params, "concatFilters", "true");

        return new BasePaginatedQuery<Push.ChannelSubscription>(rest.http, "/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, Push.ChannelSubscription.httpBodyHandler).get();
    }

    protected String getClientId(Context context) throws AblyException {
        String clientId = getDevice(context).clientId;
        if (clientId == null) {
            throw AblyException.fromThrowable(new Exception("cannot subscribe with null client ID"));
        }
        return clientId;
    }

    protected DeviceDetails getDevice(Context context) throws AblyException {
        LocalDevice localDevice = rest.device(context);
        if (localDevice == null || localDevice.deviceIdentityToken == null) {
            // Alternatively, we could store a queue of pending subscriptions in the
            // device storage. But then, in order to know if this subscription operation
            // succeeded, you would have to add a BroadcastReceiver in AndroidManifest.xml.
            // Arguably that encourages just ignoring any errors, and forcing you to listen
            // to the broadcast after push.activate has finished before subscribing is
            // more robust.
            throw AblyException.fromThrowable(new Exception("cannot use device before AblyRest.push.activate has finished"));
        }
        return localDevice;
    }
}

