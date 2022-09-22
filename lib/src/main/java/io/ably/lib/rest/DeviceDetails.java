package io.ably.lib.rest;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import io.ably.lib.types.ErrorInfo;
import io.ably.lib.http.HttpCore;
import io.ably.lib.util.JsonUtils;
import io.ably.lib.util.Serialisation;

/**
 * Contains the properties of a device registered for push notifications.
 */
public class DeviceDetails {
    /**
     * A unique ID generated by the device.
     */
    public String id;
    /**
     * The platform associated with the device.
     * Describes the platform the device uses, such as android or java.
     */
    public String platform;
    /**
     * The device form factor associated with the device.
     * Describes the type of the device, such as phone or tablet.
     */
    public String formFactor;
    /**
     * The client ID the device is connected to Ably with.
     */
    public String clientId;
    /**
     * A JSON object of key-value pairs that contains metadata for the device.
     */
    public JsonObject metadata;

    /**
     * The {@link Push} object associated with the device.
     * Describes the details of the push registration of the device.
     */
    public Push push;

    /**
     * Contains the details of the push registration of a device.
     */
    public static class Push {
        /**
         * A JSON object of key-value pairs that contains of the push transport and address.
         */
        public JsonObject recipient;
        /**
         * The current state of the push registration.
         */
        public State state;
        /**
         * An {@link ErrorInfo} object describing the most recent error when the state is Failing or Failed.
         */
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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DeviceDetails)) {
            return false;
        }
        DeviceDetails other = (DeviceDetails) o;
        JsonObject thisJson = this.toJsonObject();
        JsonObject otherJson = other.toJsonObject();

        // Disregard device token
        thisJson.remove("deviceSecret");
        otherJson.remove("deviceSecret");

        if ((this.metadata == null || this.metadata.entrySet().isEmpty()) && (other.metadata == null || other.metadata.entrySet().isEmpty())) {
            // Empty metadata == null metadata.
            thisJson.remove("metadata");
            otherJson.remove("metadata");
        }

        return thisJson.equals(otherJson);
    }

    @Override
    public String toString() {
        return this.toJsonObject().toString();
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

    public static HttpCore.ResponseHandler<DeviceDetails> httpResponseHandler = new Serialisation.HttpResponseHandler<DeviceDetails>(DeviceDetails.class, fromJsonElement);

    public static HttpCore.BodyHandler<DeviceDetails> httpBodyHandler = new Serialisation.HttpBodyHandler<DeviceDetails>(DeviceDetails[].class, fromJsonElement);
}
