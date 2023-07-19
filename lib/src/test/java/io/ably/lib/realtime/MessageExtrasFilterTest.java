package io.ably.lib.realtime;

import com.google.gson.JsonParser;
import io.ably.lib.types.Message;
import io.ably.lib.types.MessageExtras;
import io.ably.lib.types.MessageFilter;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageExtrasFilterTest {
    private Message createMessage(String extrasJson, String name, String clientId)
    {
        return new Message(
            name,
            new Object(), // Fake data
            clientId,
            extrasJson == null ? null : new MessageExtras(JsonParser.parseString(extrasJson).getAsJsonObject())
        );
    }

    private MessageExtrasFilter createFilter(Boolean isRef, String refTimeSerial, String refType, String name, String clientId)
    {
        return new MessageExtrasFilter(new MessageFilter(isRef, refTimeSerial, refType, name, clientId));
    }

    @Test
    public void it_passes_on_all_fields_matching()
    {
        MessageExtrasFilter filter = createFilter(true, "refTimeserial", "refType", "name", "clientId");
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_passes_with_no_clientId_filter()
    {
        MessageExtrasFilter filter = createFilter(true, "refTimeserial", "refType", "name", null);
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_passes_with_no_name_filter()
    {
        MessageExtrasFilter filter = createFilter(true, "refTimeserial", "refType", null, "clientId");
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_passes_with_no_isRef_filter()
    {
        MessageExtrasFilter filter = createFilter(null, "refTimeserial", "refType", "name", "clientId");
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_passes_with_no_refType_filter()
    {
        MessageExtrasFilter filter = createFilter(true, "refTimeserial", null, "name", "clientId");
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_passes_with_no_refTimeserial_filter()
    {
        MessageExtrasFilter filter = createFilter(true, null, "refType", "name", "clientId");
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_passes_on_clientId_match()
    {
        MessageExtrasFilter filter = createFilter(null, null, null, null, "clientId");
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_passes_on_clientId_no_extras()
    {
        MessageExtrasFilter filter = createFilter(null, null, null, null, "clientId");
        Message message = createMessage(
            null,
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_fails_on_clientId_mismatch()
    {
        MessageExtrasFilter filter = createFilter(null, null, null, null, "clientId");
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId2"
        );

        assertFalse(filter.onMessage(message));
    }

    @Test
    public void it_passes_on_name_match()
    {
        MessageExtrasFilter filter = createFilter(null, null, null, "name", null);
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_passes_on_name_match_no_extras()
    {
        MessageExtrasFilter filter = createFilter(null, null, null, "name", null);
        Message message = createMessage(
            null,
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_fails_on_name_mismatch()
    {
        MessageExtrasFilter filter = createFilter(null, null, null, "name", null);
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name2",
            "clientId"
        );

        assertFalse(filter.onMessage(message));
    }

    @Test
    public void it_passes_on_is_ref_match_with_timeserial()
    {
        MessageExtrasFilter filter = createFilter(true, null, null, null, null);
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_passes_on_is_ref_match_without_timeserial()
    {
        MessageExtrasFilter filter = createFilter(false, null, null, null, null);
        Message message = createMessage(
            "{}",
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_fails_on_is_ref_match_with_timeserial()
    {
        MessageExtrasFilter filter = createFilter(true, null, null, null, null);
        Message message = createMessage(
            "{}",
            "name",
            "clientId"
        );

        assertFalse(filter.onMessage(message));
    }

    @Test
    public void it_fails_on_is_ref_match_no_extras()
    {
        MessageExtrasFilter filter = createFilter(true, null, null, null, null);
        Message message = createMessage(
            null,
            "name",
            "clientId"
        );

        assertFalse(filter.onMessage(message));
    }

    @Test
    public void it_fails_on_is_ref_mismatch()
    {
        MessageExtrasFilter filter = createFilter(false, null, null, null, null);
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId"
        );

        assertFalse(filter.onMessage(message));
    }

    @Test
    public void it_passes_on_refTimeserial_match()
    {
        MessageExtrasFilter filter = createFilter(null, "refTimeserial", null, null, null);
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_fails_on_refTimeserial_mismatch()
    {
        MessageExtrasFilter filter = createFilter(null, "refTimeserial", null, null, null);
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial2\"}}",
            "name",
            "clientId"
        );

        assertFalse(filter.onMessage(message));
    }

    @Test
    public void it_fails_on_refTimeserial_mismatch_no_timeserial()
    {
        MessageExtrasFilter filter = createFilter(null, "refTimeserial", null, null, null);
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\"}}",
            "name",
            "clientId"
        );

        assertFalse(filter.onMessage(message));
    }

    @Test
    public void it_fails_on_is_refTimeserial_no_ref()
    {
        MessageExtrasFilter filter = createFilter(null, "refTimeserial", null, null, null);
        Message message = createMessage(
            "{}",
            "name",
            "clientId"
        );

        assertFalse(filter.onMessage(message));
    }

    @Test
    public void it_fails_on_is_refTimeserial_no_extras()
    {
        MessageExtrasFilter filter = createFilter(null, "refTimeserial", null, null, null);
        Message message = createMessage(
            null,
            "name",
            "clientId"
        );

        assertFalse(filter.onMessage(message));
    }

    @Test
    public void it_passes_on_refType_match()
    {
        MessageExtrasFilter filter = createFilter(null, null, "refType", null, null);
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }

    @Test
    public void it_fails_on_refType_mismatch()
    {
        MessageExtrasFilter filter = createFilter(null, null, "refType2", null, null);
        Message message = createMessage(
            "{\"ref\": {\"type\": \"refType\", \"timeserial\": \"refTimeserial\"}}",
            "name",
            "clientId"
        );

        assertFalse(filter.onMessage(message));
    }

    @Test
    public void it_fails_on_refType_mismatch_no_refType()
    {
        MessageExtrasFilter filter = createFilter(null, null, "refType", null, null);
        Message message = createMessage(
            "{\"timeserial\": \"refTimeserial\"}",
            "name",
            "clientId"
        );

        assertFalse(filter.onMessage(message));
    }

    @Test
    public void it_fails_on_is_refType_no_ref()
    {
        MessageExtrasFilter filter = createFilter(null, null, "refType", null, null);
        Message message = createMessage(
            "{}",
            "name",
            "clientId"
        );

        assertFalse(filter.onMessage(message));
    }

    @Test
    public void it_fails_on_is_refType_no_extras()
    {
        MessageExtrasFilter filter = createFilter(null, null, "refType", null, null);
        Message message = createMessage(
            null,
            "name",
            "clientId"
        );

        assertFalse(filter.onMessage(message));
    }

    @Test
    public void it_passes_on_empty_filter()
    {
        MessageExtrasFilter filter = createFilter(null, null, null, null, null);
        Message message = createMessage(
            "{}",
            "name",
            "clientId"
        );

        assertTrue(filter.onMessage(message));
    }
}
