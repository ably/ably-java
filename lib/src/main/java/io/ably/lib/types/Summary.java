package io.ably.lib.types;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A summary of all the annotations that have been made to the message. Will always be
 * populated for a message.summary, and may be populated for any other type (in
 * particular a message retrieved from REST history will have its latest summary
 * included).
 * The keys of the map are the annotation types. The exact structure of the value of
 * each key depends on the aggregation part of the annotation type, e.g. for a type of
 * reaction:distinct.v1, the value will be a DistinctValues object. New aggregation
 * methods might be added serverside, hence the 'unknown' part of the sum type.
 */
public class Summary {

    private static final String TAG = Summary.class.getName();

    /**
     * (TM2q1) The sdk MUST be able to cope with structures and aggregation types that have it does not yet know about
     * or have explicit support for, hence the loose (JsonObject) type.
     */
    private final Map<String, JsonObject> typeToSummaryJson;

    public Summary(Map<String, JsonObject> typeToSummaryJson) {
        this.typeToSummaryJson = typeToSummaryJson;
    }

    public static Map<String, SummaryClientIdList> asSummaryDistinctV1(JsonObject jsonObject) {
        Map<String, SummaryClientIdList> summary = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            summary.put(key, asSummaryFlagV1(entry.getValue().getAsJsonObject()));
        }
        return summary;
    }

    public static Map<String, SummaryClientIdList> asSummaryUniqueV1(JsonObject jsonObject) {
        return asSummaryDistinctV1(jsonObject);
    }

    public static Map<String, SummaryClientIdCounts> asSummaryMultipleV1(JsonObject jsonObject) {
        Map<String, SummaryClientIdCounts> summary = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            JsonObject value = entry.getValue().getAsJsonObject();
            int total = value.get("total").getAsInt();
            Map<String, Integer> clientIds = new HashMap<>();
            for (Map.Entry<String, JsonElement> clientEntry: value.get("clientIds").getAsJsonObject().entrySet()) {
                clientIds.put(clientEntry.getKey(), clientEntry.getValue().getAsInt());
            }
            summary.put(key, new SummaryClientIdCounts(total, clientIds));
        }
        return summary;
    }

    public static SummaryClientIdList asSummaryFlagV1(JsonObject jsonObject) {
        int total = jsonObject.get("total").getAsInt();
        List<String> clientIds = Serialisation.gson.fromJson(jsonObject.get("clientIds"), List.class);
        return new SummaryClientIdList(total, clientIds);
    }

    public static SummaryTotal asSummaryTotalV1(JsonObject jsonObject) {
        int total = jsonObject.get("total").getAsInt();
        return new SummaryTotal(total);
    }

    static Summary read(MessageUnpacker unpacker) {
        try {
            return read(Serialisation.msgpackToGson(unpacker.unpackValue()));
        } catch (Exception e) {
            Log.e(TAG, "Failed to read summary from MessagePack", e);
            return null;
        }
    }

    static Summary read(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new JsonParseException("Expected an object but got \"" + json.getClass() + "\".");
        }
        Map<String, JsonObject> typeToSummaryJson = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                throw new JsonParseException("Expected an object but got \"" + json.getClass() + "\".");
            }
            typeToSummaryJson.put(entry.getKey(), entry.getValue().getAsJsonObject());
        }
        return new Summary(typeToSummaryJson);
    }

    /**
     * Retrieves the JSON representation associated with a specified annotation type.
     *
     * @param annotationType the type of annotation to retrieve its JSON representation
     * @return a JsonObject containing the JSON representation of the specified annotation type,
     *         or null if no representation exists for the given type
     */
    public JsonObject get(String annotationType) {
        return typeToSummaryJson.get(annotationType);
    }

    void write(MessagePacker packer) {
        Serialisation.gsonToMsgpack(toJsonTree(), packer);
    }

    JsonElement toJsonTree() {
        return Serialisation.gson.toJsonTree(this);
    }

    public static class Serializer implements JsonSerializer<Summary>, JsonDeserializer<Summary> {

        @Override
        public JsonElement serialize(Summary summary, Type typeOfMessage, JsonSerializationContext ctx) {
            JsonObject json = new JsonObject();
            for (Map.Entry<String, JsonObject> entry : summary.typeToSummaryJson.entrySet()) {
                json.add(entry.getKey(), entry.getValue());
            }
            return json;
        }

        @Override
        public Summary deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return read(json);
        }

    }
}
