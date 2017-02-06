package io.ably.lib.http;

/**
 * Created by tcard on 04/09/2017.
 */
public class HttpConstants {
    static class ContentTypes {
        static final String JSON                = "application/json";
        static final String FORM_ENCODING       = "application/x-www-form-urlencoded";
    }

    public static class Headers {
        static final String CONTENT_LENGTH      = "Content-Length";
        static final String ACCEPT              = "Accept";
        static final String CONTENT_TYPE        = "Content-Type";
        static final String WWW_AUTHENTICATE    = "WWW-Authenticate";
        static final String PROXY_AUTHENTICATE  = "Proxy-Authenticate";
        static final String AUTHORIZATION       = "Authorization";
        static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
        static final String LINK                = "Link";
    }

    public static class Methods {
        public static final String GET    = "GET";
        public static final String PATCH  = "PATCH";
        public static final String PUT    = "PUT";
        public static final String POST   = "POST";
        public static final String DELETE = "DELETE";
        public static final String PATCH  = "PATCH";
    }
}
