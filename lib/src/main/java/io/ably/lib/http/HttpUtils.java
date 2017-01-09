package io.ably.lib.http;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import io.ably.lib.BuildConfig;
import io.ably.lib.http.Http.BodyHandler;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Param;
import io.ably.lib.util.Serialisation;

/**
 * HttpUtils: utility methods for Http operations
 * Internal
 *
 */
public class HttpUtils {
	/* Headers */
	public static final String X_ABLY_VERSION_HEADER = "X-Ably-Version";
	public static final String X_ABLY_VERSION_VALUE = "0.9";
	public static final String X_ABLY_LIB_HEADER = "X-Ably-Lib";
	public static final String X_ABLY_LIB_VALUE = String.format("%s-%s", BuildConfig.LIBRARY_NAME, BuildConfig.VERSION);

	public static final String DEFAULT_FORMAT = "json";
	public static Map<String, String> mimeTypes;
	
	static {
		mimeTypes = new HashMap<String, String>();
		mimeTypes.put("json", "application/json");
		mimeTypes.put("xml", "application/xml");
		mimeTypes.put("html", "text/html");
		mimeTypes.put("msgpack", "application/x-msgpack");
	}

	public static Param[] defaultAcceptHeaders(boolean binary) {
		Param[] headers;
		if(binary) {
			headers = new Param[]{ new Param("Accept", "application/x-msgpack,application/json") };
		} else {
			headers = new Param[]{ new Param("Accept", "application/json") };
		}
		return headers;
	}

	public static Param[] mergeHeaders(Param[] target, Param[] src) {
		Map<String, Param> merged = new HashMap<String, Param>();
		if(target != null) {
			for(Param param : target) { merged.put(param.key, param); }
		}
		if(src != null) {
			for(Param param : src) { merged.put(param.key, param); }
		}
		return merged.values().toArray(new Param[merged.size()]);
	}

	public static String encodeParams(String path, Param[] params) {
		StringBuilder builder = new StringBuilder(path);
		if(params != null && params.length > 0) {
			boolean first = true;
			for(Param entry : params) {
				builder.append(first ? '?' : '&');
				first = false;
				builder.append(entry.key);
				builder.append('=');
				builder.append(encodeURIComponent(entry.value));
			}
		}
		return builder.toString();
	}

	public static String encodeURIComponent(String input) {
		try {
			return URLEncoder.encode(input, "UTF-8")
				.replaceAll(" ", "%20")
				.replaceAll("!", "%21")
				.replaceAll("'", "%27")
				.replaceAll("\\(", "%28")
				.replaceAll("\\)", "%29")
				.replaceAll("\\+", "%2B")
				.replaceAll("\\:", "%3A")
				.replaceAll("~", "%7E");
		} catch (UnsupportedEncodingException e) {}
		return null;
	}

	public static BodyHandler<JsonElement> jsonArrayResponseHandler = new BodyHandler<JsonElement>() {
		@Override
		public JsonElement[] handleResponseBody(String contentType, byte[] body) throws AblyException {
			if(!"application/json".equals(contentType)) {
				return null;
			}
			JsonElement jsonBody = Serialisation.gsonParser.parse(new String(body, StandardCharsets.UTF_8));
			if(!jsonBody.isJsonArray()) {
				return new JsonElement[] { jsonBody };
			}
			JsonArray jsonArray = jsonBody.getAsJsonArray();
			JsonElement[] items = new JsonElement[jsonArray.size()];
			for(int i = 0; i < items.length; i++) {
				items[i] = jsonArray.get(i);
			}
			return items;
		}
	};

}
