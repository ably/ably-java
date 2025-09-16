package io.ably.lib.types;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.ably.lib.util.Log;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;

/**
 * Contains information about annotations associated with a particular message.
 */
public class MessageAnnotations {

    private static final String TAG = MessageAnnotations.class.getName();

    private static final String SUMMARY = "summary";

    /**
     * A summary of all the annotations that have been made to the message. Will always be
     * populated for a message.annotations.summary, and may be populated for any other type (in
     * particular a message retrieved from REST history will have its latest summary
     * included).
     * The keys of the map are the annotation types. The exact structure of the value of
     * each key depends on the aggregation part of the annotation type, e.g. for a type of
     * reaction:distinct.v1, the value will be a DistinctValues object. New aggregation
     * methods might be added serverside, hence the 'unknown' part of the sum type.
     */
    public Summary summary;

    public MessageAnnotations() {
        this.summary = new Summary(new HashMap<>());
    }

    public MessageAnnotations(Summary summary) {
        this.summary = summary != null ? summary : new Summary(new HashMap<>());
    }

    void writeMsgpack(MessagePacker packer) throws IOException {
        int fieldCount = 0;
        if (summary != null) ++fieldCount;

        packer.packMapHeader(fieldCount);

        if (summary != null) {
            packer.packString(SUMMARY);
            summary.write(packer);
        }
    }

    MessageAnnotations readMsgpack(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        for (int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if (fieldFormat.equals(MessageFormat.NIL)) {
                unpacker.unpackNil();
                continue;
            }

            if (fieldName.equals(SUMMARY)) {
                summary = Summary.read(unpacker);
            } else {
                Log.v(TAG, "Unexpected field: " + fieldName);
                unpacker.skipValue();
            }
        }

        return this;
    }

    static MessageAnnotations fromMsgpack(MessageUnpacker unpacker) throws IOException {
        return (new MessageAnnotations()).readMsgpack(unpacker);
    }

    protected void read(final JsonObject map) throws MessageDecodeException {
        final JsonElement summaryElement = map.get(SUMMARY);
        if (summaryElement != null) {
            if (!summaryElement.isJsonObject()) {
                throw MessageDecodeException.fromDescription("MessageAnnotations summary is of type \"" + summaryElement.getClass() + "\" when expected a JSON object.");
            }
            summary = Summary.read(summaryElement.getAsJsonObject());
        }
    }

    static MessageAnnotations read(JsonElement json) throws MessageDecodeException {
        if (!json.isJsonObject()) {
            Log.w(TAG, "Message annotations is of type \"" + json.getClass() + "\" when expected a JSON object.");
        }

        MessageAnnotations annotations = new MessageAnnotations();
        annotations.read(json.getAsJsonObject());
        return annotations;
    }

    JsonElement toJsonTree() {
        JsonObject json = new JsonObject();
        if (summary != null) {
            json.add(SUMMARY, summary.toJsonTree());
        }
        return json;
    }

    public static class Serializer implements JsonSerializer<MessageAnnotations>, JsonDeserializer<MessageAnnotations> {
        @Override
        public JsonElement serialize(MessageAnnotations annotations, Type typeOfMessage, JsonSerializationContext ctx) {
            return annotations.toJsonTree();
        }

        @Override
        public MessageAnnotations deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return read(json);
            } catch (MessageDecodeException e) {
                Log.e(TAG, e.getMessage(), e);
                throw new JsonParseException("Failed to deserialize MessageAnnotations from JSON.", e);
            }
        }
    }

    @Override
    public String toString() {
        return "{MessageAnnotations summary=" + summary + "}";
    }
}
