package io.ably.lib.util;

import com.google.gson.JsonElement;
import io.ably.lib.http.HttpCore;

public class StringUtils {
    public static Serialisation.FromJsonElement<String> fromJsonElement = new Serialisation.FromJsonElement<String>() {
        @Override
        public String fromJsonElement(JsonElement e) {
            return e.getAsJsonPrimitive().getAsString();
        }
    };

    public static HttpCore.ResponseHandler<String> httpResponseHandler = new Serialisation.HttpResponseHandler<String>(String.class, fromJsonElement);

    public static HttpCore.BodyHandler<String> httpBodyHandler = new Serialisation.HttpBodyHandler<String>(String[].class, fromJsonElement);
}
