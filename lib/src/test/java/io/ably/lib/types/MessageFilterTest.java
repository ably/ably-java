package io.ably.lib.types;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

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

    public void it_is_equal_to_itself() {

        final MessageFilter filter1 = new MessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        assertEquals(filter1, filter1);
    }

    public void it_is_equal() {

        final MessageFilter filter1 = new MessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        final MessageFilter filter2 = new MessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        assertEquals(filter1, filter2);
    }

    public void it_is_not_equal_different_is_ref() {

        final MessageFilter filter1 = new MessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        final MessageFilter filter2 = new MessageFilter(
            false,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        assertNotEquals(filter1, filter2);
    }

    public void it_is_not_equal_different_timeserial() {

        final MessageFilter filter1 = new MessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        final MessageFilter filter2 = new MessageFilter(
            true,
            "message_timeserial_2",
            "message_type",
            "message_name",
            "message_client_id"
        );

        assertNotEquals(filter1, filter2);
    }

    public void it_is_not_equal_different_type() {

        final MessageFilter filter1 = new MessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        final MessageFilter filter2 = new MessageFilter(
            true,
            "message_timeserial",
            "message_type_2",
            "message_name",
            "message_client_id"
        );

        assertNotEquals(filter1, filter2);
    }

    public void it_is_not_equal_different_name() {

        final MessageFilter filter1 = new MessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        final MessageFilter filter2 = new MessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name_2",
            "message_client_id"
        );

        assertNotEquals(filter1, filter2);
    }

    public void it_is_not_equal_different_client_id() {

        final MessageFilter filter1 = new MessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        final MessageFilter filter2 = new MessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id_2"
        );

        assertNotEquals(filter1, filter2);
    }
}
