package io.ably.lib.types;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RecoveryKeyContextTest {

    @Test
    public void should_encode_recovery_key_context_object() {
        String expectedRecoveryKey =
            "{\"connectionKey\":\"uniqueKey\",\"msgSerial\":1,\"channelSerials\":{\"channel1\":\"1\",\"channel2\":\"2\",\"channel3\":\"3\"}}";
        RecoveryKeyContext recoveryKey = new RecoveryKeyContext("uniqueKey", 1);
        Map<String, String> keys = new HashMap<>();
        keys.put("channel1", "1");
        keys.put("channel2", "2");
        keys.put("channel3", "3");
        recoveryKey.setChannelSerials(keys);
        String encodedRecoveryKey = recoveryKey.encode();
        assertEquals("should be equal", expectedRecoveryKey, encodedRecoveryKey);
    }


}
