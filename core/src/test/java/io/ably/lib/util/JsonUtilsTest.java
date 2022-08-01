package io.ably.lib.util;

import static com.google.gson.JsonNull.INSTANCE;
import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class JsonUtilsTest {

    @Test
    public void add_returns_json_object() {
        JsonObject jsonObject = JsonUtils.object()
            .add("null", null)
            .add("jsonElement", new Gson().toJson("jsonElement"))
            .add("string", "Joe")
            .add("boolean", true)
            .add("character", 'a')
            .add("number", 12.3)
            .add("jsonUtilsObject", new JsonUtils.JsonUtilsObject())
            .toJson();

        assertEquals(INSTANCE, jsonObject.get("null"));
        assertEquals(new JsonPrimitive("\"jsonElement\""), jsonObject.get("jsonElement"));
        assertEquals(new JsonPrimitive("Joe"), jsonObject.get("string"));
        assertEquals(new JsonPrimitive(true), jsonObject.get("boolean"));
        assertEquals(new JsonPrimitive('a'), jsonObject.get("character"));
        assertEquals(new JsonPrimitive(12.3), jsonObject.get("number"));
        assertEquals(new JsonObject(), jsonObject.get("jsonUtilsObject"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void add_throw_exception_on_unsupported_type() {
        Map<String, String> simpleMap = new HashMap<String, String>() {
            {
                put("key1", "value1");
                put("key2", "value2");
            }
        };

        JsonUtils.object().add("key", simpleMap);
    }

}
