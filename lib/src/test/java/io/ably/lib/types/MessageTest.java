package io.ably.lib.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ably.lib.types.Message.Serializer;
import io.ably.lib.util.Serialisation;
import org.junit.Test;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;

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
    public void serialize_message_with_operation() {
        // Given
        Message message = new Message("test-name", "test-data");
        message.clientId = "test-client-id";
        message.connectionKey = "test-key";
        message.refSerial = "test-ref-serial";
        message.refType = "test-ref-type";
        Message.Operation operation = new Message.Operation();
        operation.clientId = "operation-client-id";
        operation.description = "operation-description";
        operation.metadata = new HashMap<>();
        operation.metadata.put("key1", "value1");
        operation.metadata.put("key2", "value2");
        message.operation = operation;

        // When
        JsonElement serializedElement = serializer.serialize(message, null, null);

        // Then
        JsonObject serializedObject = serializedElement.getAsJsonObject();
        assertEquals("test-client-id", serializedObject.get("clientId").getAsString());
        assertEquals("test-key", serializedObject.get("connectionKey").getAsString());
        assertEquals("test-data", serializedObject.get("data").getAsString());
        assertEquals("test-name", serializedObject.get("name").getAsString());
        assertEquals("test-ref-serial", serializedObject.get("refSerial").getAsString());
        assertEquals("test-ref-type", serializedObject.get("refType").getAsString());
        JsonObject operationObject = serializedObject.getAsJsonObject("operation");
        assertEquals("operation-client-id", operationObject.get("clientId").getAsString());
        assertEquals("operation-description", operationObject.get("description").getAsString());
        JsonObject metadataObject = operationObject.getAsJsonObject("metadata");
        assertEquals("value1", metadataObject.get("key1").getAsString());
        assertEquals("value2", metadataObject.get("key2").getAsString());
    }

    @Test
    public void deserialize_message_with_operation() throws Exception {
        // Given
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("clientId", "test-client-id");
        jsonObject.addProperty("data", "test-data");
        jsonObject.addProperty("name", "test-name");
        jsonObject.addProperty("refSerial", "test-ref-serial");
        jsonObject.addProperty("refType", "test-ref-type");
        jsonObject.addProperty("connectionKey", "test-key");
        JsonObject operationObject = new JsonObject();
        operationObject.addProperty("clientId", "operation-client-id");
        operationObject.addProperty("description", "operation-description");
        JsonObject metadataObject = new JsonObject();
        metadataObject.addProperty("key1", "value1");
        metadataObject.addProperty("key2", "value2");
        operationObject.add("metadata", metadataObject);
        jsonObject.add("operation", operationObject);

        // When
        Message message = Message.fromEncoded(jsonObject, new ChannelOptions());

        // Then
        assertEquals("test-client-id", message.clientId);
        assertEquals("test-data", message.data);
        assertEquals("test-name", message.name);
        assertEquals("test-ref-serial", message.refSerial);
        assertEquals("test-ref-type", message.refType);
        assertEquals("test-key", message.connectionKey);
        assertEquals("operation-client-id", message.operation.clientId);
        assertEquals("operation-description", message.operation.description);
        assertEquals("value1", message.operation.metadata.get("key1"));
        assertEquals("value2", message.operation.metadata.get("key2"));
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

    @Test
    public void serialize_and_deserialize_with_msgpack() throws Exception {
        // Given
        Message message = new Message("test-name", "test-data");
        message.clientId = "test-client-id";
        message.connectionKey = "test-key";
        message.refSerial = "test-ref-serial";
        message.refType = "test-ref-type";
        message.action = MessageAction.MESSAGE_CREATE;
        message.serial = "01826232498871-001@abcdefghij:001";
        Message.Operation operation = new Message.Operation();
        operation.clientId = "operation-client-id";
        operation.description = "operation-description";
        operation.metadata = new HashMap<>();
        operation.metadata.put("key1", "value1");
        operation.metadata.put("key2", "value2");
        message.operation = operation;

        // When Encode to MessagePack
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessagePacker packer = Serialisation.msgpackPackerConfig.newPacker(out);
        message.writeMsgpack(packer);
        packer.close();

        // Decode from MessagePack
        MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(out.toByteArray());
        Message unpacked = Message.fromMsgpack(unpacker);
        unpacker.close();

        // Then
        assertEquals("test-client-id", unpacked.clientId);
        assertEquals("test-key", unpacked.connectionKey);
        assertEquals("test-data", unpacked.data);
        assertEquals("test-name", unpacked.name);
        assertEquals("test-ref-serial", unpacked.refSerial);
        assertEquals("test-ref-type", unpacked.refType);
        assertEquals(MessageAction.MESSAGE_CREATE, unpacked.action);
        assertEquals("01826232498871-001@abcdefghij:001", unpacked.serial);
        assertEquals("operation-client-id", unpacked.operation.clientId);
        assertEquals("operation-description", unpacked.operation.description);
        assertEquals("value1", unpacked.operation.metadata.get("key1"));
        assertEquals("value2", unpacked.operation.metadata.get("key2"));
    }
}
