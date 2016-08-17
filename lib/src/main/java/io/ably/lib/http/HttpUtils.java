package io.ably.lib.http;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import io.ably.lib.types.Param;

/**
 * HttpUtils: utility methods for Http operations
 * Internal
 */
public class HttpUtils {
    public static final String DEFAULT_FORMAT = "json";

    public static final String X_ABLY_LIB_HEADER = "X-Ably-Lib";

    public static final String LIB = "java";
    public static final String VERSION_NAME = "0.8.2";//TODO: generate BuildConfig by gradle

    public static Map<String, String> mimeTypes;

    static {
        mimeTypes = new HashMap<String, String>();
        mimeTypes.put("json", "application/json");
        mimeTypes.put("xml", "application/xml");
        mimeTypes.put("html", "text/html");
        mimeTypes.put("msgpack", "application/x-msgpack");
    }

    /**
     * The header X-Ably-Lib: [lib][.optional variant]?-[version]
     * should be included in all REST requests to the Ably endpoint where
     * <p/>
     * -[lib] is the name of the library such as js for ably-js,
     * -[.optional variant] is an optional library variant,
     * -[version] is the full client library version (ex.0.9.2).
     * <p/>
     * For example, Javascript library would use the header X-Ably-Lib: js-0.9.2
     * <p/>
     * <p/>
     * <p>
     * Spec: RSC7b
     * </p>
     */
    public static Param getAblyLibHeader() {
        return new Param(X_ABLY_LIB_HEADER, getAblyLibValue());
    }

    public static String getAblyLibValue() {
        return String.format("%s-%s", LIB, VERSION_NAME);
    }

    public static Param[] defaultAcceptHeaders(boolean binary) {
        Param[] headers;
        if (binary) {
            headers = new Param[]{new Param("Accept", "application/x-msgpack,application/json")};
        } else {
            headers = new Param[]{new Param("Accept", "application/json")};
        }
        return headers;
    }

    public static String encodeParams(String path, Param[] params) {
        StringBuilder builder = new StringBuilder(path);
        if (params != null && params.length > 0) {
            boolean first = true;
            for (Param entry : params) {
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
        } catch (UnsupportedEncodingException e) {
        }
        return null;
    }
}
