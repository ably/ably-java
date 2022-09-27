package io.ably.lib.realtime;

import java.util.HashMap;
import java.util.Map;

import io.ably.lib.util.Serialisation;

public class ConnectionRecoveryKey {

    public String connectionKey;
    public long msgSerial;
    public Map<String, String> serials = new HashMap<>();

    public String asJson() {
        return Serialisation.gson.toJson(this);
    }

    public static ConnectionRecoveryKey fromJson(String json) {
        return Serialisation.gson.fromJson(json, ConnectionRecoveryKey.class);
    }

}
