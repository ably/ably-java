package io.ably.lib.rest;

import com.google.gson.JsonObject;

import io.ably.lib.types.ErrorInfo;

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
        public String transportType;
        public String state;
        public ErrorInfo errorReason;
        public JsonObject metadata;

        public JsonObject toJsonObject() {
            JsonObject o = new JsonObject();

            o.addProperty("transportType", transportType);
            if (metadata != null) {
                o.add("metadata", metadata);
            }

            return o;
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

    public JsonObject pushMetadataJsonObject() {
        JsonObject json = new JsonObject();
        JsonObject push = new JsonObject();
        json.add("push", push);
        push.add("metadata", this.push.metadata);
        return json;
    }
}
