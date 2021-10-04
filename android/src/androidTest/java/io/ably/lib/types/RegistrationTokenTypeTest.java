package io.ably.lib.types;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RegistrationTokenTypeTest {

    @Test
    public void fromNameParseCorrectly() {
        assertEquals(RegistrationToken.Type.FCM, RegistrationToken.Type.fromName("FCM"));
        assertEquals(RegistrationToken.Type.GCM, RegistrationToken.Type.fromName("GCM"));
    }

    @Test
    public void fromNameParseFailure() {
        assertNull(RegistrationToken.Type.fromName("FĆM"));
        assertNull(RegistrationToken.Type.fromName("GÇM"));
        assertNull(RegistrationToken.Type.fromName(null));
    }

    @Test
    public void toNameProducesNameCorrectly() {
        assertEquals("fcm", new RegistrationToken(RegistrationToken.Type.FCM, "token").type.toName());
        assertEquals("gcm", new RegistrationToken(RegistrationToken.Type.GCM, "token").type.toName());
    }

}
