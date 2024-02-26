package io.ably.lib.types;

import com.google.gson.JsonSyntaxException;

import java.util.HashMap;
import java.util.Map;

import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;

public class RecoveryKeyContext {
    private static final String TAG = "RecoveryKeyContext";

    private final String connectionKey;
    private final long msgSerial;
    private final Map<String, String> channelSerials = new HashMap<>();

    public RecoveryKeyContext(String connectionKey, long msgSerial, Map<String, String> channelSerials) {
        this.connectionKey = connectionKey;
        this.msgSerial = msgSerial;
        this.channelSerials.putAll(channelSerials);
    }

    public String getConnectionKey() {
        return connectionKey;
    }

    public long getMsgSerial() {
        return msgSerial;
    }

    public Map<String, String> getChannelSerials() {
        return channelSerials;
    }

    public String encode() {
        return Serialisation.gson.toJson(this);
    }

    public static RecoveryKeyContext decode(String json) {
        try {
            return Serialisation.gson.fromJson(json, RecoveryKeyContext.class);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Cannot create recovery key from json: " + e.getMessage());
            return null;
        }
    }
}
