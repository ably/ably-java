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
}
