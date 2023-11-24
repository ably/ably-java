package io.ably.lib.types;

import com.google.gson.JsonSyntaxException;

import java.util.HashMap;
import java.util.Map;

import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;

public class ConnectionRecoveryKey {
    private static final String TAG = "RecoveryKey";

    private final String connectionKey;
    private final long msgSerial;
    /**
     * Key - channel name
     * <p>
     * Value - channelSerial
     */
    private final Map<String, String> serials = new HashMap<>();

    public ConnectionRecoveryKey(String connectionKey, long msgSerial) {
        this.connectionKey = connectionKey;
        this.msgSerial = msgSerial;
    }

    public String getConnectionKey() {
        return connectionKey;
    }

    public long getMsgSerial() {
        return msgSerial;
    }

    public Map<String, String> getSerials() {
        return serials;
    }

    public void setSerials(Map<String, String> serials) {
        this.serials.clear();
        this.serials.putAll(serials);
    }

    public void addSerial(String channelName, String channelSerial) {
        this.serials.put(channelName, channelSerial);
    }

    public String asJson() {
        return Serialisation.gson.toJson(this);
    }

    public static ConnectionRecoveryKey fromJson(String json) {
        try {
            return Serialisation.gson.fromJson(json, ConnectionRecoveryKey.class);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Cannot create recovery key from json: " + e.getMessage());
            return null;
        }
    }

}
