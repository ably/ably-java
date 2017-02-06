package io.ably.lib.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.ably.lib.http.Http;
import io.ably.lib.http.HttpScheduler;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Callback;
import io.ably.lib.types.Param;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.AsyncPaginatedResult;
import io.ably.lib.http.PaginatedQuery;
import io.ably.lib.http.BasePaginatedQuery;
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
            publishImpl(recipient, payload).sync();
        }

        public void publishAsync(Param[] recipient, JsonObject payload, final CompletionListener listener) {
            publishImpl(recipient, payload).async(new CompletionListener.ToCallback(listener));
        }

        private Http.Request<Void> publishImpl(final Param[] recipient, final JsonObject payload)  {
            return rest.http.request(new Http.Execute<Void>() {
                @Override
                public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                    if (recipient == null || recipient.length == 0) {
                        throw AblyException.fromThrowable(new Exception("recipient cannot be empty"));
                    }
                    if (payload == null || payload.entrySet().isEmpty()) {
                        throw AblyException.fromThrowable(new Exception("payload cannot be empty"));
                    }

                    JsonObject recipientJson = new JsonObject();
                    for (Param param : recipient) {
                        recipientJson.addProperty(param.key, param.value);
                    }
                    JsonObject bodyJson = new JsonObject();
                    bodyJson.add("recipient", recipientJson);
                    for (Map.Entry<String, JsonElement> entry : payload.entrySet()) {
                        bodyJson.add(entry.getKey(), entry.getValue());
                    }
                    HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(bodyJson, rest.options.useBinaryProtocol);

                    http.post("/push/publish", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, null, true, callback);
                }
            });
        }

        private final AblyRest rest;
    }

    public static class DeviceRegistrations {
        public DeviceDetails save(DeviceDetails device) throws AblyException {
            return saveImpl(device).sync();
        }

        public void saveAsync(DeviceDetails device, final Callback<DeviceDetails> callback) {
            saveImpl(device).async(callback);
        }

        protected Http.Request<DeviceDetails> saveImpl(final DeviceDetails device) {
            final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(device.toJsonObject(), rest.options.useBinaryProtocol);
            return rest.http.request(new Http.Execute<DeviceDetails>() {
                @Override
                public void execute(HttpScheduler http, Callback<DeviceDetails> callback) throws AblyException {
                    http.put("/push/deviceRegistrations/" + device.id, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, DeviceDetails.httpResponseHandler, true, callback);
                }
            });
        }

        public DeviceDetails get(String deviceId) throws AblyException {
            return getImpl(deviceId).sync();
        }

        public void getAsync(String deviceId, final Callback<DeviceDetails> callback) {
            getImpl(deviceId).async(callback);
        }

        protected Http.Request<DeviceDetails> getImpl(final String deviceId) {
            return rest.http.request(new Http.Execute<DeviceDetails>() {
                @Override
                public void execute(HttpScheduler http, Callback<DeviceDetails> callback) throws AblyException {
                    http.get("/push/deviceRegistrations/" + deviceId, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, DeviceDetails.httpResponseHandler, true, callback);
                }
            });
        }

        public PaginatedResult<DeviceDetails> list(Param[] params) throws AblyException {
            return listImpl(params).sync();
        }

        public void listAsync(Param[] params, Callback<AsyncPaginatedResult<DeviceDetails>> callback) {
            listImpl(params).async(callback);
        }

        protected BasePaginatedQuery.ResultRequest<DeviceDetails> listImpl(Param[] params) {
            return new BasePaginatedQuery<DeviceDetails>(rest.http, "/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, DeviceDetails.httpBodyHandler).get();
        }

        public void remove(DeviceDetails device) throws AblyException {
            remove(device.id);
        }

        public void removeAsync(DeviceDetails device, CompletionListener listener) {
            removeAsync(device.id, listener);
        }

        public void remove(String deviceId) throws AblyException {
            removeImpl(deviceId).sync();
        }

        public void removeAsync(String deviceId, CompletionListener listener) {
            removeImpl(deviceId).async(new CompletionListener.ToCallback(listener));
        }

        protected Http.Request<Void> removeImpl(final String deviceId) {
            return rest.http.request(new Http.Execute<Void>() {
                @Override
                public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                    http.del("/push/deviceRegistrations/" + deviceId, HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, null, true, callback);
                }
            });
        }

        public void removeWhere(Param[] params) throws AblyException {
            removeWhereImpl(params).sync();
        }

        public void removeWhereAsync(Param[] params, CompletionListener listener) {
            removeWhereImpl(params).async(new CompletionListener.ToCallback(listener));
        }

        protected Http.Request<Void> removeWhereImpl(final Param[] params) {
            return rest.http.request(new Http.Execute<Void>() {
                @Override
                public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                    http.del("/push/deviceRegistrations", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, null, true, callback);
                }
            });
        }

        DeviceRegistrations(AblyRest rest) {
            this.rest = rest;
        }

        private final AblyRest rest;
    }

    public static class ChannelSubscriptions {
        public ChannelSubscription save(ChannelSubscription subscription) throws AblyException {
            return saveImpl(subscription).sync();
        }

        public void saveAsync(ChannelSubscription subscription, final Callback<ChannelSubscription> callback) {
            saveImpl(subscription).async(callback);
        }

        protected Http.Request<ChannelSubscription> saveImpl(final ChannelSubscription subscription) {
            final HttpCore.RequestBody body = HttpUtils.requestBodyFromGson(subscription.toJsonObject(), rest.options.useBinaryProtocol);
            return rest.http.request(new Http.Execute<ChannelSubscription>() {
                @Override
                public void execute(HttpScheduler http, Callback<ChannelSubscription> callback) throws AblyException {
                    http.post("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), null, body, ChannelSubscription.httpResponseHandler, true, callback);
                }
            });
        }

        public PaginatedResult<ChannelSubscription> list(Param[] params) throws AblyException {
            return listImpl(params).sync();
        }

        public void listAsync(Param[] params, Callback<AsyncPaginatedResult<ChannelSubscription>> callback) {
            listImpl(params).async(callback);
        }

        protected BasePaginatedQuery.ResultRequest<ChannelSubscription> listImpl(Param[] params) {
            return new BasePaginatedQuery<Push.ChannelSubscription>(rest.http, "/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, ChannelSubscription.httpBodyHandler).get();
        }

        public void remove(ChannelSubscription subscription) throws AblyException {
            removeImpl(subscription).sync();
        }

        public void removeAsync(ChannelSubscription subscription, CompletionListener listener) {
            removeImpl(subscription).async(new CompletionListener.ToCallback(listener));
        }

        protected Http.Request<Void> removeImpl(ChannelSubscription subscription) {
            Param[] params = new Param[] { new Param("channel", subscription.channel) };
            if (subscription.deviceId != null) {
                params = Param.push(params, "deviceId", subscription.deviceId);
            } else if (subscription.clientId != null) {
                params = Param.push(params, "clientId", subscription.clientId);
            } else {
                return rest.http.failedRequest(AblyException.fromThrowable(new Exception("ChannelSubscription cannot be for both a deviceId and a clientId")));
            }
            
            return removeWhereImpl(params);
        }


        public void removeWhere(Param[] params) throws AblyException {
            removeWhereImpl(params).sync();
        }

        public void removeWhereAsync(Param[] params, CompletionListener listener) {
            removeWhereImpl(params).async(new CompletionListener.ToCallback(listener));
        }

        protected Http.Request<Void> removeWhereImpl(final Param[] params) {
            return rest.http.request(new Http.Execute<Void>() {
                @Override
                public void execute(HttpScheduler http, Callback<Void> callback) throws AblyException {
                    http.del("/push/channelSubscriptions", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, null, true, callback);
                }
            });
        }

        public PaginatedResult<String> listChannels(Param[] params) throws AblyException {
            return listChannelsImpl(params).sync();
        }

        public void listChannelsAsync(Param[] params, Callback<AsyncPaginatedResult<String>> callback) {
            listChannelsImpl(params).async(callback);
        }

        protected BasePaginatedQuery.ResultRequest<String> listChannelsImpl(Param[] params) {
            return new BasePaginatedQuery<String>(rest.http, "/push/channels", HttpUtils.defaultAcceptHeaders(rest.options.useBinaryProtocol), params, StringUtils.httpBodyHandler).get();
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

        protected static HttpCore.ResponseHandler<ChannelSubscription> httpResponseHandler = new Serialisation.HttpResponseHandler<ChannelSubscription>(ChannelSubscription.class, fromJsonElement);

        protected static HttpCore.BodyHandler<ChannelSubscription> httpBodyHandler = new Serialisation.HttpBodyHandler<ChannelSubscription>(ChannelSubscription[].class, fromJsonElement);
    }

    protected final AblyRest rest;
    public final Admin admin;
}
