package io.ably.lib.util;

import com.google.gson.JsonElement;
import io.ably.lib.http.Http;

public class StringUtils {
	public static Serialisation.FromJsonElement<String> fromJsonElement = new Serialisation.FromJsonElement<String>() {
        @Override
        public String fromJsonElement(JsonElement e) {
            return e.getAsJsonPrimitive().getAsString();
        }
    };

    public static Http.ResponseHandler<String> httpResponseHandler = new Serialisation.HttpResponseHandler<String>(String.class, fromJsonElement);

    public static Http.BodyHandler<String> httpBodyHandler = new Serialisation.HttpBodyHandler<String>(String[].class, fromJsonElement);
}
