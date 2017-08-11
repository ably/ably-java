package io.ably.lib.http;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.Param;

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

}
