package io.ably.lib.rest;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.AblyException;
import io.ably.lib.http.Http;
import io.ably.lib.util.JsonUtils;
import io.ably.lib.util.Serialisation;

/**
 * Created by tcard on 3/2/17.
 */

public abstract class DeviceDetails {
    public String id;
    public String platform;
    public String formFactor;
    public String clientId;
    public JsonObject metadata;
    public String updateToken;

    public Push push;

    public static class Push {
        public JsonObject recipient;
        public State state;
        public ErrorInfo errorReason;

        public JsonObject toJsonObject() {
            JsonObject o = new JsonObject();

            o.add("recipient", recipient);

            return o;
        }

        public enum State {
            ACTIVE("ACTIVE"),
            FAILING("FAILING"),
            FAILED("FAILED");

            public String code;
            State(String code) {
                this.code = code;
            }

            public int toInt() {
                State[] values = State.values();
                for (int i = 0; i < values.length; i++) {
                    if (this == values[i]) {
                        return i;
                    }
                }
                return -1;
            }

            public static State fromInt(int i) {
                State[] values = State.values();
                if (i < 0 || i >= values.length) {
                    return null;
                }
                return values[i];
            }

            public static State fromCode(String code) {
                State[] values = State.values();
                for (State t : values) {
                    if (t.code.equals(code)) {
                        return t;
                    }
                }
                return null;
            }
        }
    }

    public JsonObject toJsonObject() {
        JsonObject o = new JsonObject();

        o.addProperty("id", id);
        o.addProperty("platform", platform);
        o.addProperty("formFactor", formFactor);
        o.addProperty("clientId", clientId);
        if (metadata != null) {
            o.add("metadata", metadata);
        }
        if (updateToken != null) {
            o.addProperty("updateToken", updateToken);
        }
        if (push != null) {
            o.add("push", push.toJsonObject());
        }

        return o;
    }

    public JsonObject pushRecipientJsonObject() {
        return JsonUtils.object()
                .add("push", JsonUtils.object()
                    .add("recipient", this.push.recipient)).toJson();
    }

    public static DeviceDetails fromJsonObject(JsonObject o) {
        return Serialisation.gson.fromJson(o, DeviceDetails.class);
    }

    private static Serialisation.FromJsonElement<DeviceDetails> fromJsonElement = new Serialisation.FromJsonElement<DeviceDetails>() {
        @Override
        public DeviceDetails fromJsonElement(JsonElement e) {
            return fromJsonObject((JsonObject) e);
        }
    };

    protected static Http.ResponseHandler<DeviceDetails> httpResponseHandler = new Serialisation.HttpResponseHandler<DeviceDetails>(DeviceDetails.class, fromJsonElement);

    protected static Http.BodyHandler<DeviceDetails> httpBodyHandler = new Serialisation.HttpBodyHandler<DeviceDetails>(DeviceDetails[].class, fromJsonElement);
}
