package io.ably.lib.realtime;

import com.google.gson.JsonSyntaxException;

import java.util.HashMap;
import java.util.Map;

import io.ably.lib.util.Serialisation;

public class ConnectionRecoveryKey {

    public String connectionKey;
    public long msgSerial;
    /**
     * Key - channel name
     * <p>
     * Value - channelSerial
     */
    public Map<String, String> serials = new HashMap<>();

    public String asJson() {
        return Serialisation.gson.toJson(this);
    }

    public static ConnectionRecoveryKey fromJson(String json) {
        try {
            return Serialisation.gson.fromJson(json, ConnectionRecoveryKey.class);
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

}
