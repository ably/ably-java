package io.ably.lib.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Callback;
import io.ably.lib.types.Param;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.http.PaginatedQuery;
import io.ably.lib.http.AsyncPaginatedQuery;
import io.ably.lib.util.Serialisation;
import io.ably.lib.util.StringUtils;

import java.util.Map;

/**
 * Created by tcard on 3/2/17.
 */
public class PushBase {
    public PushBase(AblyRest rest) {
        this.rest = rest;
        this.admin = new Admin(rest);
    }

    public void publish(Param[] recipient, JsonObject payload) throws AblyException {
        rest.http.post("/push/publish", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, publishBody(recipient, payload), null);
    }

    public void publishAsync(Param[] recipient, JsonObject payload, final CompletionListener listener) {
        rest.asyncHttp.post("/push/publish", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, publishBody(recipient, payload), null, new CompletionListener.ToCallback(listener));
    }

    private Http.RequestBody publishBody(Param[] recipient, JsonObject payload) {
        JsonObject recipientJson = new JsonObject();
        for (Param param : recipient) {
            recipientJson.addProperty(param.key, param.value);
        }
        JsonObject bodyJson = new JsonObject();
        bodyJson.add("recipient", recipientJson);
        for (Map.Entry<String, JsonElement> entry : payload.entrySet()) {
            bodyJson.add(entry.getKey(), entry.getValue());
        }
        return rest.http.requestBodyFromGson(bodyJson);
    }

    public static class Admin {
        public final DeviceRegistrations deviceRegistrations;
        public final ChannelSubscriptions channelSubscriptions;

        Admin(AblyRest rest) {
            this.deviceRegistrations = new DeviceRegistrations(rest);
            this.channelSubscriptions = new ChannelSubscriptions(rest);
        }
    }

    public static class DeviceRegistrations {
        public DeviceDetails save(DeviceDetails device) throws AblyException {
            Http.RequestBody body = rest.http.requestBodyFromGson(device.toJsonObject());
            return rest.http.put("/push/deviceRegistrations/" + device.id, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, DeviceDetails.httpResponseHandler);
        }

        public void saveAsync(DeviceDetails device, final Callback<DeviceDetails> callback) {
            Http.RequestBody body = rest.http.requestBodyFromGson(device.toJsonObject());
            rest.asyncHttp.put("/push/deviceRegistrations/" + device.id, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, DeviceDetails.httpResponseHandler, callback);
        }

        public PaginatedResult<DeviceDetails> get(Param[] params) throws AblyException {
            return new PaginatedQuery<DeviceDetails>(rest.http, "/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, DeviceDetails.httpBodyHandler).get();
        }

        public void getAsync(Param[] params, Callback<AsyncPaginatedResult<DeviceDetails>> callback) throws AblyException {
            new AsyncPaginatedQuery<DeviceDetails>(rest.asyncHttp, "/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, DeviceDetails.httpBodyHandler).get(callback);
        }

        public void remove(Param[] params) throws AblyException {
            rest.http.del("/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, null);
        }

        public void removeAsync(Param[] params, CompletionListener listener) throws AblyException {
            rest.asyncHttp.del("/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, null, new CompletionListener.ToCallback(listener));
        }

        DeviceRegistrations(AblyRest rest) {
            this.rest = rest;
        }

        private final AblyRest rest;
    }

    public static class ChannelSubscriptions {
        public void save(ChannelSubscription subscription) throws AblyException {
            Http.RequestBody body = rest.http.requestBodyFromGson(subscription.toJsonObject());
            rest.http.post("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, ChannelSubscription.httpResponseHandler);
        }

        public void saveAsync(ChannelSubscription subscription, final Callback<ChannelSubscription> callback) {
            Http.RequestBody body = rest.http.requestBodyFromGson(subscription.toJsonObject());
            rest.asyncHttp.put("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, ChannelSubscription.httpResponseHandler, callback);
        }

        public PaginatedResult<ChannelSubscription> get(Param[] params) throws AblyException {
            return new PaginatedQuery<ChannelSubscription>(rest.http, "/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, ChannelSubscription.httpBodyHandler).get();
        }

        public void getAsync(Param[] params, Callback<AsyncPaginatedResult<ChannelSubscription>> callback) throws AblyException {
            new AsyncPaginatedQuery<ChannelSubscription>(rest.asyncHttp, "/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, ChannelSubscription.httpBodyHandler).get(callback);
        }

        public void remove(Param[] params) throws AblyException {
            rest.http.del("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, null);
        }

        public void removeAsync(Param[] params, CompletionListener listener) throws AblyException {
            rest.asyncHttp.del("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, null, new CompletionListener.ToCallback(listener));
        }

        public PaginatedResult<String> listChannels(Param[] params) throws AblyException {
            return new PaginatedQuery<String>(rest.http, "/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, StringUtils.httpBodyHandler).get();
        }

        public void listChannelsAsync(Param[] params, Callback<AsyncPaginatedResult<String>> callback) throws AblyException {
            new AsyncPaginatedQuery<String>(rest.asyncHttp, "/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, StringUtils.httpBodyHandler).get(callback);
        }

        ChannelSubscriptions(AblyRest rest) {
            this.rest = rest;
        }

        private final AblyRest rest;
    }

    public static class ChannelSubscription {
        public final String channel;
        public final String deviceId;
        public final String clientId;

        public static ChannelSubscription forDevice(String channel, String deviceId) {
            return new ChannelSubscription(channel, deviceId, null);
        }

        public static ChannelSubscription forClientId(String channel, String clientId) {
            return new ChannelSubscription(channel, null, clientId);
        }

        private ChannelSubscription(String channel, String deviceId, String clientId) {
            this.channel = channel;
            this.deviceId = deviceId;
            this.clientId = clientId;
        }

        public JsonObject toJsonObject() {
            JsonObject o = new JsonObject();

            o.addProperty("channel", channel);
            if (clientId != null) {
                o.addProperty("clientId", clientId);
            }
            if (deviceId != null) {
                o.addProperty("deviceId", deviceId);
            }

            return o;
        }

        public static ChannelSubscription fromJsonObject(JsonObject o) {
            return Serialisation.gson.fromJson(o, ChannelSubscription.class);
        }

        private static Serialisation.FromJsonElement<ChannelSubscription> fromJsonElement = new Serialisation.FromJsonElement<ChannelSubscription>() {
            @Override
            public ChannelSubscription fromJsonElement(JsonElement e) {
                return fromJsonObject((JsonObject) e);
            }
        };

        protected static Http.ResponseHandler<ChannelSubscription> httpResponseHandler = new Serialisation.HttpResponseHandler<ChannelSubscription>(ChannelSubscription.class, fromJsonElement);

        protected static Http.BodyHandler<ChannelSubscription> httpBodyHandler = new Serialisation.HttpBodyHandler<ChannelSubscription>(ChannelSubscription[].class, fromJsonElement);
    }

    protected final AblyRest rest;
    protected final Admin admin;
}
