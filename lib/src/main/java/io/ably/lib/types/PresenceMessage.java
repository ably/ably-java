package io.ably.lib.types;

import java.io.IOException;
import java.lang.reflect.Type;
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
 * A class representing an individual presence update to be sent or received
 * via the Ably Realtime service.
 */
public class PresenceMessage extends BaseMessage implements Cloneable {

    /**
     * Describes the possible actions members in the presence set can emit.
     */
    public enum Action {
        /**
         * A member is not present in the channel.
         * <p>
         * Spec: TP2
         */
        absent,
        /**
         * When subscribing to presence events on a channel that already has members present,
         * this event is emitted for every member already present on the channel before the subscribe listener was registered.
         * <p>
         * Spec: TP2
         */
        present,
        /**
         * A new member has entered the channel.
         * <p>
         * Spec: TP2
         */
        enter,
        /**
         * A member who was present has now left the channel.
         * This may be a result of an explicit request to leave or implicitly when detaching from the channel.
         * Alternatively, if a member's connection is abruptly disconnected and they do not resume their connection within a minute,
         * Ably treats this as a leave event as the client is no longer present.
         * <p>
         * Spec: TP2
         */
        leave,
        /**
         * An already present member has updated their member data.
         * Being notified of member data updates can be very useful, for example,
         * it can be used to update the status of a user when they are typing a message.
         * <p>
         * Spec: TP2
         */
        update;

        public int getValue() { return ordinal(); }
        public static Action findByValue(int value) { return values()[value]; }
    }

    public Action action;

    /**
     * Default constructor
     */
    public PresenceMessage() {}

    /**
     * Construct a PresenceMessage from an Action and clientId
     * @param action
     * @param clientId
     */
    public PresenceMessage(Action action, String clientId) {
        this(action, clientId, null);
    }

    /**
     * Generic constructor
     * @param action
     * @param clientId
     * @param data
     */
    public PresenceMessage(Action action, String clientId, Object data) {
        this.action = action;
        this.clientId = clientId;
        this.data = data;
    }

    /**
     * Generate a String summary of this PresenceMessage
     * @return string
     */
    public String toString() {
        StringBuilder result = new StringBuilder("{PresenceMessage");
        super.getDetails(result);
        result.append(" action=").append(action.name());
        result.append('}');
        return result.toString();
    }

    @Override
    public Object clone() {
        PresenceMessage result = new PresenceMessage();
        result.id = id;
        result.timestamp = timestamp;
        result.clientId = clientId;
        result.connectionId = connectionId;
        result.encoding = encoding;
        result.data = data;
        result.action = action;
        return result;
    }

    void writeMsgpack(MessagePacker packer) throws IOException {
        int fieldCount = super.countFields();
        ++fieldCount;
        packer.packMapHeader(fieldCount);
        super.writeFields(packer);
        packer.packString("action");
        packer.packInt(action.getValue());
    }

    PresenceMessage readMsgpack(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        for(int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

            if(super.readField(unpacker, fieldName, fieldFormat)) { continue; }
            if(fieldName.equals("action")) {
                action = Action.findByValue(unpacker.unpackInt());
            } else {
                Log.v(TAG, "Unexpected field: " + fieldName);
                unpacker.skipValue();
            }
        }
        return this;
    }

    static PresenceMessage fromMsgpack(MessageUnpacker unpacker) throws IOException {
        return (new PresenceMessage()).readMsgpack(unpacker);
    }

    /**
     * Refer Spec TP4 <br>
     * An alternative constructor that take an PresenceMessage-JSON object and a channelOptions (optional), and return a PresenceMessage
     * @param messageJsonObject
     * @param channelOptions
     * @return
     * @throws MessageDecodeException
     */
    public static PresenceMessage fromEncoded(JsonObject messageJsonObject, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            PresenceMessage presenceMessage = Serialisation.gson.fromJson(messageJsonObject, PresenceMessage.class);
            presenceMessage.decode(channelOptions);
            if(presenceMessage.action == null){
                throw MessageDecodeException.fromDescription("Action cannot be null/empty");
            }
            return presenceMessage;
        } catch(Exception e) {
            Log.e(PresenceMessage.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    /**
     * Refer Spec TP4 <br>
     * An alternative constructor that takes a Stringified PresenceMessage-JSON and a channelOptions (optional), and return a PresenceMessage
     * @param messageJson
     * @param channelOptions
     * @return
     * @throws MessageDecodeException
     */
    public static PresenceMessage fromEncoded(String messageJson, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            JsonObject jsonObject = Serialisation.gson.fromJson(messageJson, JsonObject.class);
            return fromEncoded(jsonObject, channelOptions);
        } catch(Exception e) {
            Log.e(PresenceMessage.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    /**
     * Refer Spec TP4 <br>
     * An alternative constructor that takes a PresenceMessage JsonArray and a channelOptions (optional), and return array of PresenceMessages.
     * @param presenceMsgArray
     * @param channelOptions
     * @return
     * @throws MessageDecodeException
     */
    public static PresenceMessage[] fromEncodedArray(JsonArray presenceMsgArray, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            PresenceMessage[] messages = new PresenceMessage[presenceMsgArray.size()];
            for(int index = 0; index < presenceMsgArray.size(); index++) {
                JsonElement jsonElement = presenceMsgArray.get(index);
                if(!jsonElement.isJsonObject()) {
                    throw new JsonParseException("Not all JSON elements are of type JSON Object.");
                }
                messages[index] = fromEncoded(jsonElement.getAsJsonObject(), channelOptions);
            }
            return messages;
        } catch(Exception e) {
            Log.e(PresenceMessage.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    /**
     * Refer Spec TP4 <br>
     * An alternative constructor that takes a Stringified PresenceMessages Array and a channelOptions (optional), and return array of PresenceMessages.
     * @param presenceMsgArray
     * @param channelOptions
     * @return
     * @throws MessageDecodeException
     */
    public static PresenceMessage[] fromEncodedArray(String presenceMsgArray, ChannelOptions channelOptions) throws MessageDecodeException {
        try {
            JsonArray jsonArray = Serialisation.gson.fromJson(presenceMsgArray, JsonArray.class);
            return fromEncodedArray(jsonArray, channelOptions);
        } catch(Exception e) {
            Log.e(PresenceMessage.class.getName(), e.getMessage(), e);
            throw MessageDecodeException.fromDescription(e.getMessage());
        }
    }

    public static class ActionSerializer implements JsonDeserializer<Action> {
        @Override
        public Action deserialize(JsonElement json, Type t, JsonDeserializationContext ctx)
                throws JsonParseException {
            return Action.findByValue(json.getAsInt());
        }
    }

    public static class Serializer implements JsonSerializer<PresenceMessage> {
        @Override
        public JsonElement serialize(PresenceMessage message, Type typeOfMessage, JsonSerializationContext ctx) {
            final JsonObject json = BaseMessage.toJsonObject(message);
            if(message.action != null) json.addProperty("action", message.action.getValue());
            return json;
        }
    }

    /**
     * Get the member key for the PresenceMessage.
     * @return
     */
    public String memberKey() {
        return connectionId + ':' + clientId;
    }

    private static final String TAG = PresenceMessage.class.getName();
}
