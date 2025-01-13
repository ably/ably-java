package io.ably.lib.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ably.lib.types.Message.Serializer;
import org.junit.Test;

public class MessageTest {

    private final Serializer serializer = new Serializer();

    @Test
    public void serialize_message() {
        // Given
        Message message = new Message("test-name", "test-data");
        message.clientId = "test-client-id";
        message.connectionKey = "test-key";

        // When
        JsonElement serializedElement = serializer.serialize(message, null, null);

        // Then
        JsonObject serializedObject = serializedElement.getAsJsonObject();
        assertEquals("test-client-id", serializedObject.get("clientId").getAsString());
        assertEquals("test-key", serializedObject.get("connectionKey").getAsString());
        assertEquals("test-data", serializedObject.get("data").getAsString());
        assertEquals("test-name", serializedObject.get("name").getAsString());
    }

    @Test
    public void serialize_message_with_name_and_data() {
        // Given
        Message message = new Message("test-name", "test-data");

        // When
        JsonElement serializedElement = serializer.serialize(message, null, null);

        // Then
        JsonObject serializedObject = serializedElement.getAsJsonObject();
        assertNull(serializedObject.get("clientId"));
        assertNull(serializedObject.get("connectionKey"));
        assertNull(serializedObject.get("extras"));
        assertEquals("test-data", serializedObject.get("data").getAsString());
        assertEquals("test-name", serializedObject.get("name").getAsString());
    }

    @Test
    public void serialize_message_with_serial() {
        // Given
        Message message = new Message("test-name", "test-data");
        message.clientId = "test-client-id";
        message.connectionKey = "test-key";
        message.action = MessageAction.MESSAGE_CREATE;
        message.serial = "01826232498871-001@abcdefghij:001";

        // When
        JsonElement serializedElement = serializer.serialize(message, null, null);

        // Then
        JsonObject serializedObject = serializedElement.getAsJsonObject();
        assertEquals("test-client-id", serializedObject.get("clientId").getAsString());
        assertEquals("test-key", serializedObject.get("connectionKey").getAsString());
        assertEquals("test-data", serializedObject.get("data").getAsString());
        assertEquals("test-name", serializedObject.get("name").getAsString());
        assertEquals(0, serializedObject.get("action").getAsInt());
        assertEquals("01826232498871-001@abcdefghij:001", serializedObject.get("serial").getAsString());
    }

    @Test
    public void deserialize_message_with_serial() throws Exception {
        // Given
       JsonObject jsonObject = new JsonObject();
       jsonObject.addProperty("clientId", "test-client-id");
       jsonObject.addProperty("data", "test-data");
       jsonObject.addProperty("name", "test-name");
       jsonObject.addProperty("action", 0);
       jsonObject.addProperty("serial", "01826232498871-001@abcdefghij:001");

        // When
        Message message = Message.fromEncoded(jsonObject, new ChannelOptions());

        // Then
        assertEquals("test-client-id", message.clientId);
        assertEquals("test-data", message.data);
        assertEquals("test-name", message.name);
        assertEquals(MessageAction.MESSAGE_CREATE, message.action);
        assertEquals("01826232498871-001@abcdefghij:001", message.serial);
    }


    @Test
    public void deserialize_message_with_unknown_action() throws Exception {
        // Given
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("clientId", "test-client-id");
        jsonObject.addProperty("data", "test-data");
        jsonObject.addProperty("name", "test-name");
        jsonObject.addProperty("action", 10);
        jsonObject.addProperty("serial", "01826232498871-001@abcdefghij:001");

        // When
        Message message = Message.fromEncoded(jsonObject, new ChannelOptions());

        // Then
        assertEquals("test-client-id", message.clientId);
        assertEquals("test-data", message.data);
        assertEquals("test-name", message.name);
        assertNull(message.action);
        assertEquals("01826232498871-001@abcdefghij:001", message.serial);
    }
}
