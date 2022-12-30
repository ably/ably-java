package io.ably.lib.util;

public class HttpCode {

    //Informational responses (100 – 199)
    public static final int CONTINUE = 100;
    public static final int SWITCHING_PROTOCOLS = 101;
    public static final int PROCESSING = 102;
    public static final int EARLY_HINTS = 103;
    public static final int NO_CODE = 104;
    public static final int NO_MESSAGE = 105;

    //Successful responses (200 – 299)
    public static final int OK = 200;
    public static final int CREATED = 201;
    public static final int ACCEPTED = 202;
    public static final int NON_AUTHORITATIVE_INFORMATION = 203;
    public static final int NO_CONTENT = 204;
    public static final int RESET_CONTENT = 205;
    public static final int PARTIAL_CONTENT = 206;

    //Redirection messages (300 – 399)
    public static final int MULTIPLE_CHOICES = 300;
    public static final int MOVED_PERMANENTLY = 301;
    public static final int FOUND = 302;
    public static final int SEE_OTHER = 303;
    public static final int NOT_MODIFIED = 304;
    public static final int USE_PROXY = 305;
    public static final int TEMPORARY_REDIRECT = 307;
    public static final int PERMANENT_REDIRECT = 308;

    //Client error responses (400 – 499)
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int PAYMENT_REQUIRED = 402;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int NOT_ACCEPTABLE = 406;
    public static final int PROXY_AUTHENTICATION_REQUIRED = 407;
    public static final int REQUEST_TIMEOUT = 408;
    public static final int CONFLICT = 409;
    public static final int GONE = 410;
    public static final int LENGTH_REQUIRED = 411;
    public static final int PRECONDITION_FAILED = 412;
    public static final int REQUEST_ENTITY_TOO_LARGE = 413;
    public static final int REQUEST_URI_TOO_LONG = 414;
    public static final int UNSUPPORTED_MEDIA_TYPE = 415;
    public static final int REQUESTED_RANGE_NOT_SATISFIABLE = 416;
    public static final int EXPECTATION_FAILED = 417;
    public static final int I_AM_A_TEAPOT = 418;
    public static final int MISDIRECTED_REQUEST = 421;
    public static final int UNPROCESSABLE_ENTITY = 422;
    public static final int LOCKED = 423;
    public static final int FAILED_DEPENDENCY = 424;
    public static final int UPGRADE_REQUIRED = 426;
    public static final int PRECONDITION_REQUIRED = 428;
    public static final int TOO_MANY_REQUESTS = 429;
    public static final int REQUEST_HEADER_FIELDS_TOO_LARGE = 431;
    public static final int UNAVAILABLE_FOR_LEGAL_REASONS = 451;

    //Server error responses (500 – 599)
    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int NOT_IMPLEMENTED = 501;
    public static final int BAD_GATEWAY = 502;
    public static final int SERVICE_UNAVAILABLE = 503;
    public static final int GATEWAY_TIMEOUT = 504;
    public static final int HTTP_VERSION_NOT_SUPPORTED = 505;
    public static final int VARIANTS_ALSO_NEGOTIATES = 506;
    public static final int INSUFFICIENT_STORAGE = 507;
    public static final int NOT_EXTENDED = 510;
    public static final int NETWORK_AUTHENTICATION_REQUIRED = 511;

    public static String describe(int errorCode) {
        switch (errorCode) {
            case CONTINUE:
                return "Continue";
            case SWITCHING_PROTOCOLS:
                return "Switching Protocols";
            case PROCESSING:
                return "Processing";
            case EARLY_HINTS:
                return "Early Hints";
            case NO_CODE:
                return "No Code";
            case NO_MESSAGE:
                return "No Message";
            case OK:
                return "OK";
            case CREATED:
                return "Created";
            case ACCEPTED:
                return "Accepted";
            case NON_AUTHORITATIVE_INFORMATION:
                return "Non-Authoritative Information";
            case NO_CONTENT:
                return "No Content";
            case RESET_CONTENT:
                return "Reset Content";
            case PARTIAL_CONTENT:
                return "Partial Content";
            case MULTIPLE_CHOICES:
                return "Multiple Choices";
            case MOVED_PERMANENTLY:
                return "Moved Permanently";
            case FOUND:
                return "Found";
            case SEE_OTHER:
                return "See Other";
            case NOT_MODIFIED:
                return "Not Modified";
            case USE_PROXY:
                return "Use Proxy";
            case TEMPORARY_REDIRECT:
                return "Temporary Redirect";
            case PERMANENT_REDIRECT:
                return "Permanent Redirect";
            case BAD_REQUEST:
                return "Bad Request";
            case UNAUTHORIZED:
                return "Unauthorized";
            case PAYMENT_REQUIRED:
                return "Payment Required";
            case FORBIDDEN:
                return "Forbidden";
            case NOT_FOUND:
                return "Not Found";
            case METHOD_NOT_ALLOWED:
                return "Method Not Allowed";
            case NOT_ACCEPTABLE:
                return "Not Acceptable";
            case PROXY_AUTHENTICATION_REQUIRED:
                return "Proxy Authentication Required";
            case REQUEST_TIMEOUT:
                return "Request Timeout";
            case CONFLICT:
                return "Conflict";
            case GONE:
                return "Gone";
            case LENGTH_REQUIRED:
                return "Length Required";
            case PRECONDITION_FAILED:
                return "Precondition Failed";
            case REQUEST_ENTITY_TOO_LARGE:
                return "Request Entity Too Large";
            case REQUEST_URI_TOO_LONG:
                return "Request-URI Too Long";
            case UNSUPPORTED_MEDIA_TYPE:
                return "Unsupported Media Type";
            case REQUESTED_RANGE_NOT_SATISFIABLE:
                return "Requested Range Not Satisfiable";
            case EXPECTATION_FAILED:
                return "Expectation Failed";
            case I_AM_A_TEAPOT:
                return "I'm a teapot";
            case MISDIRECTED_REQUEST:
                return "Misdirected Request";
            case UNPROCESSABLE_ENTITY:
                return "Unprocessable Entity";
            case LOCKED:
                return "Locked";
            case FAILED_DEPENDENCY:
                return "Failed Dependency";
            case UPGRADE_REQUIRED:
                return "Upgrade Required";
            case PRECONDITION_REQUIRED:
                return "Precondition Required";
            case TOO_MANY_REQUESTS:
                return "Too Many Requests";
            case REQUEST_HEADER_FIELDS_TOO_LARGE:
                return "Request Header Fields Too Large";
            case UNAVAILABLE_FOR_LEGAL_REASONS:
                return "Unavailable For Legal Reasons";
            case INTERNAL_SERVER_ERROR:
                return "Internal Server Error";
            case NOT_IMPLEMENTED:
                return "Not Implemented";
            case BAD_GATEWAY:
                return "Bad Gateway";
            case SERVICE_UNAVAILABLE:
                return "Service Unavailable";
            case GATEWAY_TIMEOUT:
                return "Gateway Timeout";
            case HTTP_VERSION_NOT_SUPPORTED:
                return "HTTP Version Not Supported";
            case VARIANTS_ALSO_NEGOTIATES:
                return "Variant Also Negotiates";
            case INSUFFICIENT_STORAGE:
                return "Insufficient Storage";
            case NOT_EXTENDED:
                return "Not Extended";
            case NETWORK_AUTHENTICATION_REQUIRED:
                return "Network Authentication Required";
            default:
                return "Unknown error";
        }
    }

}
