package io.ably.lib.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.ably.lib.http.AsyncHttp;
import io.ably.lib.http.Http;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
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

    public static class Admin {
        public final DeviceRegistrations deviceRegistrations;
        public final ChannelSubscriptions channelSubscriptions;

        Admin(AblyRest rest) {
            this.rest = rest;
            this.deviceRegistrations = new DeviceRegistrations(rest);
            this.channelSubscriptions = new ChannelSubscriptions(rest);
        }

        public void publish(Param[] recipient, JsonObject payload) throws AblyException {
            if (recipient == null || recipient.length == 0) {
                throw AblyException.fromThrowable(new Exception("recipient cannot be empty"));
            }
            if (payload == null || payload.entrySet().isEmpty()) {
                throw AblyException.fromThrowable(new Exception("payload cannot be empty"));
            }
            rest.http.post("/push/publish", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, publishBody(recipient, payload), null, true);
        }

        public void publishAsync(Param[] recipient, JsonObject payload, final CompletionListener listener) {
            Callback<Void> callback = new CompletionListener.ToCallback(listener);
            if (recipient == null || recipient.length == 0) {
                callback.onError(ErrorInfo.fromThrowable(new Exception("recipient cannot be empty")));
                return;
            }
            if (payload == null || payload.entrySet().isEmpty()) {
                callback.onError(ErrorInfo.fromThrowable(new Exception("payload cannot be empty")));
                return;
            }
            rest.asyncHttp.post("/push/publish", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, publishBody(recipient, payload), null, true, callback);
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

        private final AblyRest rest;
    }

    public static class DeviceRegistrations {
        public DeviceDetails save(DeviceDetails device) throws AblyException {
            Http.RequestBody body = rest.http.requestBodyFromGson(device.toJsonObject());
            return rest.http.put("/push/deviceRegistrations/" + device.id, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, DeviceDetails.httpResponseHandler, true);
        }

        public void saveAsync(DeviceDetails device, final Callback<DeviceDetails> callback) {
            Http.RequestBody body = rest.http.requestBodyFromGson(device.toJsonObject());
            rest.asyncHttp.put("/push/deviceRegistrations/" + device.id, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, DeviceDetails.httpResponseHandler, true, callback);
        }

        public DeviceDetails get(String deviceId) throws AblyException {
            return rest.http.get("/push/deviceRegistrations/" + deviceId, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, DeviceDetails.httpResponseHandler, true);
        }

        public void getAsync(String deviceId, final Callback<DeviceDetails> callback) {
            rest.asyncHttp.get("/push/deviceRegistrations/" + deviceId, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, DeviceDetails.httpResponseHandler, true, callback);
        }

        public PaginatedResult<DeviceDetails> list(Param[] params) throws AblyException {
            return new PaginatedQuery<DeviceDetails>(rest.http, "/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, DeviceDetails.httpBodyHandler).get();
        }

        public void listAsync(Param[] params, Callback<AsyncPaginatedResult<DeviceDetails>> callback) throws AblyException {
            new AsyncPaginatedQuery<DeviceDetails>(rest.asyncHttp, "/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, DeviceDetails.httpBodyHandler).get(callback);
        }

        public void remove(DeviceDetails device) throws AblyException {
            remove(device.id);
        }

        public void removeAsync(DeviceDetails device, CompletionListener listener) throws AblyException {
            removeAsync(device.id, listener);
        }

        public void remove(String deviceId) throws AblyException {
            rest.http.del("/push/deviceRegistrations/" + deviceId, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, null, true);
        }

        public void removeAsync(String deviceId, CompletionListener listener) throws AblyException {
            rest.asyncHttp.del("/push/deviceRegistrations/" + deviceId, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, null, true, new CompletionListener.ToCallback(listener));
        }

        public void removeWhere(Param[] params) throws AblyException {
            rest.http.del("/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, null, true);
        }

        public void removeWhereAsync(Param[] params, CompletionListener listener) throws AblyException {
            rest.asyncHttp.del("/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, null, true, new CompletionListener.ToCallback(listener));
        }

        DeviceRegistrations(AblyRest rest) {
            this.rest = rest;
        }

        private final AblyRest rest;
    }

    public static class ChannelSubscriptions {
        public void save(ChannelSubscription subscription) throws AblyException {
            Http.RequestBody body = rest.http.requestBodyFromGson(subscription.toJsonObject());
            rest.http.post("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, ChannelSubscription.httpResponseHandler, true);
        }

        public void saveAsync(ChannelSubscription subscription, final Callback<ChannelSubscription> callback) {
            Http.RequestBody body = rest.http.requestBodyFromGson(subscription.toJsonObject());
            rest.asyncHttp.put("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, ChannelSubscription.httpResponseHandler, true, callback);
        }

        public PaginatedResult<ChannelSubscription> list(Param[] params) throws AblyException {
            return new PaginatedQuery<ChannelSubscription>(rest.http, "/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, ChannelSubscription.httpBodyHandler).get();
        }

        public void listAsync(Param[] params, Callback<AsyncPaginatedResult<ChannelSubscription>> callback) throws AblyException {
            new AsyncPaginatedQuery<ChannelSubscription>(rest.asyncHttp, "/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, ChannelSubscription.httpBodyHandler).get(callback);
        }

        public void remove(ChannelSubscription subscription) throws AblyException {
            Param[] params = removeParams(subscription);
            removeWhere(params);
        }

        public void removeAsync(ChannelSubscription subscription, CompletionListener listener) throws AblyException {
            Param[] params = removeParams(subscription);
            removeWhereAsync(params, listener);
        }

        public void removeWhere(Param[] params) throws AblyException {
            rest.http.del("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, null, true);
        }

        public void removeWhereAsync(Param[] params, CompletionListener listener) throws AblyException {
            rest.asyncHttp.del("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, null, true, new CompletionListener.ToCallback(listener));
        }

        public PaginatedResult<String> listChannels(Param[] params) throws AblyException {
            return new PaginatedQuery<String>(rest.http, "/push/channels", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, StringUtils.httpBodyHandler).get();
        }

        public void listChannelsAsync(Param[] params, Callback<AsyncPaginatedResult<String>> callback) throws AblyException {
            new AsyncPaginatedQuery<String>(rest.asyncHttp, "/push/channels", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, StringUtils.httpBodyHandler).get(callback);
        }

        ChannelSubscriptions(AblyRest rest) {
            this.rest = rest;
        }

        protected Param[] removeParams(ChannelSubscription subscription) throws AblyException {
            Param[] params = new Param[] { new Param("channel", subscription.channel) };
            if (subscription.deviceId != null) {
                params = Param.push(params, "deviceId", subscription.deviceId);
            } else if (subscription.clientId != null) {
                params = Param.push(params, "clientId", subscription.clientId);
            } else {
                throw AblyException.fromThrowable(new Exception("ChannelSubscription cannot be for both a deviceId and a clientId"));
            }
            return params;
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
    public final Admin admin;
}
