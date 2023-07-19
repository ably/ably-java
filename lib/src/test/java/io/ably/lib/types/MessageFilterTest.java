package io.ably.lib.types;
import org.junit.Test;
import static org.junit.Assert.*;

public class MessageFilterTest {

    /**
     * Spec: MFI1, MFI2
     */
    @Test
    public void it_sets_parameters() {

        final MessageFilter filter = new MessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        assertTrue(filter.isRef);
        assertEquals("message_timeserial", filter.refTimeSerial);
        assertEquals("message_type", filter.refType);
        assertEquals("message_name", filter.name);
        assertEquals("message_client_id", filter.clientId);
    }
}
