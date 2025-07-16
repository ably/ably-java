package io.ably.lib.objects;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.ably.lib.util.Log;

import java.lang.reflect.Type;

public class LiveObjectsJsonSerializer implements JsonSerializer<Object[]>, JsonDeserializer<Object[]> {
    private static final String TAG = LiveObjectsJsonSerializer.class.getName();

    @Override
    public Object[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        LiveObjectSerializer serializer = LiveObjectsHelper.getLiveObjectSerializer();
        if (serializer == null) {
            Log.w(TAG, "Skipping 'state' field json deserialization because LiveObjectsSerializer not found.");
            return null;
        }
        if (!json.isJsonArray()) {
            throw new JsonParseException("Expected a JSON array for 'state' field, but got: " + json);
        }
        return serializer.readFromJsonArray(json.getAsJsonArray());
    }

    @Override
    public JsonElement serialize(Object[] src, Type typeOfSrc, JsonSerializationContext context) {
        LiveObjectSerializer serializer = LiveObjectsHelper.getLiveObjectSerializer();
        if (serializer == null) {
            Log.w(TAG, "Skipping 'state' field json serialization because LiveObjectsSerializer not found.");
            return JsonNull.INSTANCE;
        }
        return serializer.asJsonArray(src);
    }
}
