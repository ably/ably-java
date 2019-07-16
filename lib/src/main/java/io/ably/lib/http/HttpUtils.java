package io.ably.lib.http;

import com.google.gson.JsonElement;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.Param;
import io.ably.lib.util.Serialisation;

/**
 * HttpUtils: utility methods for Http operations
 * Internal
 *
 */
public class HttpUtils {
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

	public static URL parseUrl(String url) throws AblyException {
		try {
			return new URL(url);
		} catch (MalformedURLException e) {
			throw AblyException.fromThrowable(e);
		}
	}

	public static Map<String, Param> decodeParams(String query) {
		Map<String, Param> params = new HashMap<String, Param>();
		String[] pairs = query.split("&");
		try {
			for (String pair : pairs) {
				int idx = pair.indexOf('=');
				String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
						value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
				params.put(key, new Param(key, value));
			}
		} catch (UnsupportedEncodingException e) {}
		return params;
	}

	public static Map<String, Param> indexParams(Param[] paramArray) {
		Map<String, Param> params = new HashMap<String, Param>();
		for (Param param : paramArray) {
			params.put(param.key, param);
		}
		return params;
	}

	public static Map<String, Param> mergeParams(Map<String, Param> target, Map<String, Param> src) {
		for(Param p : src.values()) {
			target.put(p.key, p);
		}
		return target;
	}

	public static Param[] flattenParams(Map<String, Param> map) {
		Param[] result = null;
		if(map != null) {
			result = map.values().toArray(new Param[map.size()]);
		}
		return result;
	}

	public static Param[] toParamArray(Map<String, List<String>> indexedParams) {
		List<Param> params = new ArrayList<Param>();
		for(Entry<String, List<String>> entry : indexedParams.entrySet()) {
			for(String value : entry.getValue()) {
				params.add(new Param(entry.getKey(), value));
			}
		}
		return params.toArray(new Param[params.size()]);
	}

	public static String getParam(Param[] params, String key) {
		String result = null;
		if(params != null) {
			for(Param p : params) {
				if(key.equals(p.key)) {
					result = p.value;
					break;
				}
			}
		}
		return result;
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

	private static void appendParams(StringBuilder uri, Param[] params) {
		if(params != null && params.length > 0) {
			uri.append('?').append(params[0].key).append('=').append(params[0].value);
			for(int i = 1; i < params.length; i++) {
				uri.append('&').append(params[i].key).append('=').append(params[i].value);
			}
		}
	}

	static URL buildURL(String scheme, String host, int port, String path, Param[] params) {
		StringBuilder builder = new StringBuilder(scheme).append(host).append(':').append(port).append(path);
		appendParams(builder, params);

		URL result = null;
		try {
			result = new URL(builder.toString());
		} catch (MalformedURLException e) {}
		return result;
	}

	static URL buildURL(String uri, Param[] params) {
		StringBuilder builder = new StringBuilder(uri);
		appendParams(builder, params);

		URL result = null;
		try {
			result = new URL(builder.toString());
		} catch (MalformedURLException e) {}
		return result;
	}

	/**
	 * A RequestBody wrapping a byte array
	 */
	public static class ByteArrayRequestBody implements HttpCore.RequestBody {
		public ByteArrayRequestBody(byte[] bytes, String contentType) { this.bytes = bytes; this.contentType = contentType; }

		@Override
		public byte[] getEncoded() { return bytes; }
		@Override
		public String getContentType() { return contentType; }

		private final byte[] bytes;
		private final String contentType;
	}

	public static class FormRequestBody implements HttpCore.RequestBody {
		public FormRequestBody(Param[] formData) {
			this.formData = formData;
		}

		@Override
		public byte[] getEncoded() {
			try {
				StringBuilder body = new StringBuilder();
				for (int i = 0; i < formData.length; i++) {
					if (i != 0)
						body.append('&');
					body.append(URLEncoder.encode(formData[i].key, "UTF-8"));
					body.append('=');
					body.append(URLEncoder.encode(formData[i].value, "UTF-8"));
				}
				return body.toString().getBytes("UTF-8");
			} catch (UnsupportedEncodingException e) {
				return new byte[]{};
			}
		}

		@Override
		public String getContentType() {
			return HttpConstants.ContentTypes.FORM_ENCODING;
		}

		private Param[] formData;
	}

	/**
	 * A RequestBody wrapping a JSON-serialisable object
	 */
	public static class JsonRequestBody implements HttpCore.RequestBody {
		public JsonRequestBody(String jsonText) { this.jsonText = jsonText; }
		public JsonRequestBody(Object ob) { this(Serialisation.gson.toJson(ob)); }

		@Override
		public byte[] getEncoded() { return (bytes != null) ? bytes : (bytes = jsonText.getBytes(Charset.forName("UTF-8"))); }
		@Override
		public String getContentType() { return HttpConstants.ContentTypes.JSON; }

		private final String jsonText;
		private byte[] bytes;
	}

	public static HttpCore.RequestBody requestBodyFromGson(JsonElement json, boolean useBinaryProtocol) {
		if (!useBinaryProtocol) {
			return new JsonRequestBody(json);
		}

		return new ByteArrayRequestBody(Serialisation.gsonToMsgpack(json), "application/x-msgpack");
	}
}
