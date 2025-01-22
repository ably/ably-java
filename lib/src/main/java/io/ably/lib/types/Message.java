package io.ably.lib.types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.ably.lib.util.Serialisation;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import io.ably.lib.util.Log;

/**
 * Contains an individual message that is sent to, or received from, Ably.
 */
public class Message extends BaseMessage {

    /**
     * The event name.
     * <p>
     * Spec: TM2g
     */
    public String name;

    /**
     * A MessageExtras object of arbitrary key-value pairs that may contain metadata, and/or ancillary payloads.
     * Valid payloads include {@link DeltaExtras}, {@link JsonObject}.
     * <p>
     * Spec: TM2i
     */
    public MessageExtras extras;

    /**
     * Key needed only in case one client is publishing this message on behalf of another client.
     * The connectionKey will never be populated for messages received.
     * <p>
     * Spec: TM2h
     */
    public String connectionKey;

    /**
     * (TM2k) serial string – an opaque string that uniquely identifies the message. If a message received from Ably
     * (whether over realtime or REST, eg history) with an action of MESSAGE_CREATE does not contain a serial,
     * the SDK must set it equal to its version.
     */
    public String serial;

    /**
     * (TM2p) version string – an opaque string that uniquely identifies the message, and is different for different versions.
     * If a message received from Ably over a realtime transport does not contain a version,
     * the SDK must set it to <channelSerial>:<padded_index> from the channelSerial field of the enclosing ProtocolMessage,
     * and padded_index is the index of the message inside the messages array of the ProtocolMessage,
     * left-padded with 0s to three digits (for example, the second entry might be foo:001)
     */
    public String version;

    /**
     * (TM2j) action enum
     */
    public MessageAction action;

    /**
     * (TM2o) createdAt time in milliseconds since epoch. If a message received from Ably
     * (whether over realtime or REST, eg history) with an action of MESSAGE_CREATE does not contain a createdAt,
     * the SDK must set it equal to the TM2f timestamp.
     */
    public Long createdAt;

    /**
     * (TM2l) ref string – an opaque string that uniquely identifies some referenced message.
     */
    public String refSerial;

    /**
     * (TM2m) refType string – an opaque string that identifies the type of this reference.
     */
    public String refType;

    /**
     * (TM2n) operation object – data object that may contain the `optional` attributes.
     */
    public Operation operation;

    public static class Operation {
        public String clientId;
        public String description;
        public Map<String, String> metadata;

        void write(MessagePacker packer) throws IOException {
            int fieldCount = 0;
            if (clientId != null) fieldCount++;
            if (description != null) fieldCount++;
            if (metadata != null) fieldCount++;

            packer.packMapHeader(fieldCount);

            if (clientId != null) {
                packer.packString("clientId");
                packer.packString(clientId);
            }
            if (description != null) {
                packer.packString("description");
                packer.packString(description);
            }
            if (metadata != null) {
                packer.packString("metadata");
                packer.packMapHeader(metadata.size());
                for (Map.Entry<String, String> entry : metadata.entrySet()) {
                    packer.packString(entry.getKey());
                    packer.packString(entry.getValue());
                }
            }
        }

        protected static Operation read(final MessageUnpacker unpacker) throws IOException {
            Operation operation = new Operation();
            int fieldCount = unpacker.unpackMapHeader();
            for (int i = 0; i < fieldCount; i++) {
                String fieldName = unpacker.unpackString().intern();
                switch (fieldName) {
                    case "clientId":
                        operation.clientId = unpacker.unpackString();
                        break;
                    case "description":
                        operation.description = unpacker.unpackString();
                        break;
                    case "metadata":
                        int mapSize = unpacker.unpackMapHeader();
                        operation.metadata = new HashMap<>(mapSize);
                        for (int j = 0; j < mapSize; j++) {
                            String key = unpacker.unpackString();
                            String value = unpacker.unpackString();
                            operation.metadata.put(key, value);
                        }
                        break;
                    default:
                        unpacker.skipValue();
                        break;
                }
            }
            return operation;
        }

        protected static Operation read(final JsonObject jsonObject) throws MessageDecodeException {
            Operation operation = new Operation();
            if (jsonObject.has("clientId")) {
                operation.clientId = jsonObject.get("clientId").getAsString();
            }
            if (jsonObject.has("description")) {
                operation.description = jsonObject.get("description").getAsString();
            }
            if (jsonObject.has("metadata")) {
                JsonObject metadataObject = jsonObject.getAsJsonObject("metadata");
                operation.metadata = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : metadataObject.entrySet()) {
                    operation.metadata.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            return operation;
        }
    }

    private static final String NAME = "name";
    private static final String EXTRAS = "extras";
    private static final String CONNECTION_KEY = "connectionKey";
    private static final String SERIAL = "serial";
    private static final String VERSION = "version";
    private static final String ACTION = "action";
    private static final String CREATED_AT = "createdAt";
    private static final String REF_SERIAL = "refSerial";
    private static final String REF_TYPE = "refType";
    private static final String OPERATION = "operation";

    /**
     * Default constructor
     */
    public Message() {
    }

    /**
     * Construct a Message object with an event name and payload.
     * <p>
     * Spec: TM2
     *
     * @param name The event name.
     * @param data The message payload.
     */
    public Message(String name, Object data) {
        this(name, data, null, null);
    }

    /**
     * Construct a Message object with an event name, payload, and a unique client ID.
     * <p>
     * Spec: TM2
     *
     * @param name The event name.
     * @param data The message payload.
     * @param clientId The client ID of the publisher of this message.
     */
    public Message(String name, Object data, String clientId) {
        this(name, data, clientId, null);
    }

    /**
     * Construct a Message object with an event name, payload, and a extras.
     * <p>
     * Spec: TM2
     *
     * @param name The event name.
     * @param data The message payload.
     * @param extras Extra information to be sent with this message.
     */
    public Message(String name, Object data, MessageExtras extras) {
        this(name, data, null, extras);
    }

    /**
     * Construct a Message object with an event name, payload, extras, and a unique client ID.
     * <p>
     * Spec: TM2
     *
     * @param name The event name.
     * @param data The message payload.
     * @param clientId The client ID of the publisher of this message.
     * @param extras Extra information to be sent with this message.
     */
    public Message(String name, Object data, String clientId, MessageExtras extras) {
        this.name = name;
        this.clientId = clientId;
        this.data = data;
        this.extras = extras;
    }

    /**
     * Generate a String summary of this Message
     * @return string
     */
    public String toString() {
        StringBuilder result = new StringBuilder("{Message");
        super.getDetails(result);
        if(name != null)
            result.append(" name=").append(name);
        result.append('}');
        return result.toString();
    }

    void writeMsgpack(MessagePacker packer) throws IOException {
        int fieldCount = super.countFields();
        if(name != null) ++fieldCount;
        if(extras != null) ++fieldCount;
        if(connectionKey != null) ++fieldCount;
        if(serial != null) ++fieldCount;
        if(version != null) ++fieldCount;
        if(action != null) ++fieldCount;
        if(createdAt != null) ++fieldCount;
        if(refSerial != null) ++fieldCount;
        if(refType != null) ++fieldCount;
        if(operation != null) ++fieldCount;

        packer.packMapHeader(fieldCount);
        super.writeFields(packer);
        if(name != null) {
            packer.packString(NAME);
            packer.packString(name);
        }
        if(extras != null) {
            packer.packString(EXTRAS);
            extras.write(packer);
        }
        if(connectionKey != null) {
            packer.packString(CONNECTION_KEY);
            packer.packString(connectionKey);
        }
        if(serial != null) {
            packer.packString(SERIAL);
            packer.packString(serial);
        }
        if(version != null) {
            packer.packString(VERSION);
            packer.packString(version);
        }
        if(action != null) {
            packer.packString(ACTION);
            packer.packInt(action.ordinal());
        }
        if(createdAt != null) {
            packer.packString(CREATED_AT);
            packer.packLong(createdAt);
        }
        if(refSerial != null) {
            packer.packString(REF_SERIAL);
            packer.packString(refSerial);
        }
        if(refType != null) {
            packer.packString(REF_TYPE);
            packer.packString(refType);
        }
        if(operation != null) {
            packer.packString(OPERATION);
            operation.write(packer);
        }
    }

    Message readMsgpack(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        for(int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if(fieldFormat.equals(MessageFormat.NIL)) {
                unpacker.unpackNil();
                continue;
            }

            if(super.readField(unpacker, fieldName, fieldFormat)) {
                continue;
            }
            if(fieldName.equals(NAME)) {
                name = unpacker.unpackString();
            } else if (fieldName.equals(EXTRAS)) {
                extras = MessageExtras.read(unpacker);
            } else if (fieldName.equals(CONNECTION_KEY)) {
                connectionKey = unpacker.unpackString();
            } else if (fieldName.equals(SERIAL)) {
                serial = unpacker.unpackString();
            } else if (fieldName.equals(VERSION)) {
                version = unpacker.unpackString();
            } else if (fieldName.equals(ACTION)) {
                action = MessageAction.tryFindByOrdinal(unpacker.unpackInt());
            } else if (fieldName.equals(CREATED_AT)) {
                createdAt = unpacker.unpackLong();
            }  else if (fieldName.equals(REF_SERIAL)) {
                refSerial = unpacker.unpackString();
            } else if (fieldName.equals(REF_TYPE)) {
                refType = unpacker.unpackString();
            } else if (fieldName.equals(OPERATION)) {
                operation = Operation.read(unpacker);
            }
            else {
                Log.v(TAG, "Unexpected field: " + fieldName);
                unpacker.skipValue();
            }
        }
        return this;
    }

    /**
     * Sets the channel names and message contents to {@link io.ably.lib.realtime.AblyRealtime#publishBatch}.
     */
    public static class Batch {
        /**
         * An array of channel names to publish messages to.
         */
        public String[] channels;
        /**
         * An array of {@link Message} objects to publish.
         */
        public Message[] messages;

        public Batch(String channel, Message[] messages) {
            if(channel == null || channel.isEmpty()) throw new IllegalArgumentException("A Batch spec cannot have an empty set of channels");
            if(messages == null || messages.length == 0) throw new IllegalArgumentException("A Batch spec cannot have an empty set of messages");
            this.channels = new String[]{channel};
            this.messages = messages;
        }

        public Batch(String[] channels, Message[] messages) {
            if(channels == null || channels.length == 0) throw new IllegalArgumentException("A Batch spec cannot have an empty set of channels");
            if(messages == null || messages.length == 0) throw new IllegalArgumentException("A Batch spec cannot have an empty set of messages");
            this.channels = channels;
            this.messages = messages;
        }

        public Batch(Collection<String> channels, Collection<Message> messages) {
            this(channels.toArray(new String[0]), messages.toArray(new Message[0]));
        }

        public void writeMsgpack(MessagePacker packer) throws IOException {
            packer.packMapHeader(2);
            packer.packString("channels");
            packer.packArrayHeader(channels.length);
            for(String ch : channels) packer.packString(ch);
            packer.packString("messages");
            MessageSerializer.writeMsgpackArray(messages, packer);
        }
    }

    static Message fromMsgpack(MessageUnpacker unpacker) throws IOException {
        return (new Message()).readMsgpack(unpacker);
    }

    /**
     * A static factory method to create a Message object from a deserialized Message-like object encoded using Ably's wire protocol.
     * <p>
     * Spec: TM3
     * @param messageJson A Message-like deserialized object.
     * @param channelOptions A {@link ChannelOptions} object.
     *                       If you have an encrypted channel, use this to allow the library to decrypt the data.
     * @return A Message object.
     * @throws MessageDecodeException
     */
    public static Message fromEncoded(JsonObject messageJson, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            Message message = Serialisation.gson.fromJson(messageJson, Message.class);
            message.decode(channelOptions);
            return message;
        } catch(Exception e) {
            Log.e(Message.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    /**
     * A static factory method to create a Message object from a deserialized Message-like object encoded using Ably's wire protocol.
     * <p>
     * Spec: TM3
     * @param messageJson A Message-like deserialized object.
     * @param channelOptions A {@link ChannelOptions} object.
     *                       If you have an encrypted channel, use this to allow the library to decrypt the data.
     * @return A Message object.
     * @throws MessageDecodeException
     */
    public static Message fromEncoded(String messageJson, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            JsonObject jsonObject = Serialisation.gson.fromJson(messageJson, JsonObject.class);
            return fromEncoded(jsonObject.getAsJsonObject(), channelOptions);
        } catch(Exception e) {
            Log.e(Message.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    /**
     * A static factory method to create an array of Message objects from an array of deserialized
     * Message-like object encoded using Ably's wire protocol.
     * <p>
     * Spec: TM3
     * @param messageArray An array of Message-like deserialized objects.
     * @param channelOptions A {@link ChannelOptions} object.
     *                       If you have an encrypted channel, use this to allow the library to decrypt the data.
     * @return An array of {@link Message} objects.
     * @throws MessageDecodeException
     */
    public static Message[] fromEncodedArray(JsonArray messageArray, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            Message[] messages = new Message[messageArray.size()];
            for(int index = 0; index < messageArray.size(); index++) {
                JsonElement jsonElement = messageArray.get(index);
                if(!jsonElement.isJsonObject()) {
                    throw new JsonParseException("Not all JSON elements are of type JSON Object.");
                }
                messages[index] = fromEncoded(jsonElement.getAsJsonObject(), channelOptions);
            }
            return messages;
        } catch(Exception e) {
            Log.e(Message.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    /**
     * A static factory method to create an array of Message objects from an array of deserialized
     * Message-like object encoded using Ably's wire protocol.
     * <p>
     * Spec: TM3
     * @param messagesArray An array of Message-like deserialized objects.
     * @param channelOptions A {@link ChannelOptions} object.
     *                       If you have an encrypted channel, use this to allow the library to decrypt the data.
     * @return An array of {@link Message} objects.
     * @throws MessageDecodeException
     */
    public static Message[] fromEncodedArray(String messagesArray, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            JsonArray jsonArray = Serialisation.gson.fromJson(messagesArray, JsonArray.class);
            return fromEncodedArray(jsonArray, channelOptions);
        } catch(Exception e) {
            Log.e(Message.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    @Override
    protected void read(final JsonObject map) throws MessageDecodeException {
        super.read(map);

        name = readString(map, NAME);

        final JsonElement extrasElement = map.get(EXTRAS);
        if (null != extrasElement) {
            if (!(extrasElement instanceof JsonObject)) {
                throw MessageDecodeException.fromDescription("Message extras is of type \"" + extrasElement.getClass() + "\" when expected a JSON object.");
            }
            extras = MessageExtras.read((JsonObject) extrasElement);
        }
        connectionKey = readString(map, CONNECTION_KEY);

        serial = readString(map, SERIAL);
        version = readString(map, VERSION);
        Integer actionOrdinal = readInt(map, ACTION);
        action = actionOrdinal == null ? null : MessageAction.tryFindByOrdinal(actionOrdinal);
        createdAt = readLong(map, CREATED_AT);
        refSerial = readString(map, REF_SERIAL);
        refType = readString(map, REF_TYPE);

        final JsonElement operationElement = map.get(OPERATION);
        if (null != operationElement) {
            if (!(operationElement instanceof JsonObject)) {
                throw MessageDecodeException.fromDescription("Message operation is of type \"" + operationElement.getClass() + "\" when expected a JSON object.");
            }
            operation = Operation.read((JsonObject) operationElement);
        }
    }

    public static class Serializer implements JsonSerializer<Message>, JsonDeserializer<Message> {
        @Override
        public JsonElement serialize(Message message, Type typeOfMessage, JsonSerializationContext ctx) {
            final JsonObject json = BaseMessage.toJsonObject(message);
            if (message.name != null) {
                json.addProperty(NAME, message.name);
            }
            if (message.extras != null) {
                json.add(EXTRAS, Serialisation.gson.toJsonTree(message.extras));
            }
            if (message.connectionKey != null) {
                json.addProperty(CONNECTION_KEY, message.connectionKey);
            }
            if (message.serial != null) {
                json.addProperty(SERIAL, message.serial);
            }
            if (message.version != null) {
                json.addProperty(VERSION, message.version);
            }
            if (message.action != null) {
                json.addProperty(ACTION, message.action.ordinal());
            }
            if (message.createdAt != null) {
                json.addProperty(CREATED_AT, message.createdAt);
            }
            if (message.refSerial != null) {
                json.addProperty(REF_SERIAL, message.refSerial);
            }
            if (message.refType != null) {
                json.addProperty(REF_TYPE, message.refType);
            }
            if (message.operation != null) {
                json.add(OPERATION, Serialisation.gson.toJsonTree(message.operation));
            }
            return json;
        }

        @Override
        public Message deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!(json instanceof JsonObject)) {
                throw new JsonParseException("Expected an object but got \"" + json.getClass() + "\".");
            }

            final Message message = new Message();
            try {
                message.read((JsonObject)json);
            } catch (MessageDecodeException e) {
                Log.e(Message.class.getName(), e.getMessage(), e);
                throw new JsonParseException("Failed to deserialize Message from JSON.", e);
            }
            return message;
        }
    }

    private static final String TAG = Message.class.getName();
}
