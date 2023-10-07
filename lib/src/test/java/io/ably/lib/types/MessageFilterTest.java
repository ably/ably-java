package io.ably.lib.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class MessageFilterTest {

    private MessageFilter createMessageFilter(boolean isRef, String refTimeserial, String refType, String name, String clientId) {
        MessageFilter filter = new MessageFilter();
        filter.isRef = isRef;
        filter.refTimeserial = refTimeserial;
        filter.refType = refType;
        filter.name = name;
        filter.clientId = clientId;

        return filter;
    }

    public void it_is_equal_to_itself() {

        final MessageFilter filter1 = createMessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        assertEquals(filter1, filter1);
    }

    public void it_is_equal() {

        final MessageFilter filter1 = createMessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        final MessageFilter filter2 = createMessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        assertEquals(filter1, filter2);
    }

    public void it_is_not_equal_different_is_ref() {

        final MessageFilter filter1 = createMessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        final MessageFilter filter2 = createMessageFilter(
            false,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        assertNotEquals(filter1, filter2);
    }

    public void it_is_not_equal_different_timeserial() {

        final MessageFilter filter1 = createMessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        final MessageFilter filter2 = createMessageFilter(
            true,
            "message_timeserial_2",
            "message_type",
            "message_name",
            "message_client_id"
        );

        assertNotEquals(filter1, filter2);
    }

    public void it_is_not_equal_different_type() {

        final MessageFilter filter1 = createMessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        final MessageFilter filter2 = createMessageFilter(
            true,
            "message_timeserial",
            "message_type_2",
            "message_name",
            "message_client_id"
        );

        assertNotEquals(filter1, filter2);
    }

    public void it_is_not_equal_different_name() {

        final MessageFilter filter1 = createMessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        final MessageFilter filter2 = createMessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name_2",
            "message_client_id"
        );

        assertNotEquals(filter1, filter2);
    }

    public void it_is_not_equal_different_client_id() {

        final MessageFilter filter1 = createMessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id"
        );

        final MessageFilter filter2 = createMessageFilter(
            true,
            "message_timeserial",
            "message_type",
            "message_name",
            "message_client_id_2"
        );

        assertNotEquals(filter1, filter2);
    }
}
