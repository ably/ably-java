package io.ably.core.types;

import io.ably.core.util.Serialisation;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessageUnpacker;

import io.ably.core.util.Log;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;

/**
 * An exception type encapsulating error information containing
 * an Ably-specific error code and generic status code.
 */
public class ErrorInfo {

    /**
     * Ably error code (see ably-common/protocol/errors.json)
     */
    public int code;

    /**
     * HTTP Status Code corresponding to this error, where applicable
     */
    public int statusCode;

    /**
     * Additional message information, where available
     */
    public String message;

    /**
     * Link to specification detail for this error code, where available. Spec TI4.
     */
    public String href;

    /**
     * Public no-argument constructor for msgpack
     */
    public ErrorInfo() {}

    /**
     * Construct an ErrorInfo from message and code
     * @param message
     * @param code
     */
    public ErrorInfo(String message, int code) {
        this.code = code;
        this.message = message;
    }

    /**
     * Generic constructor
     * @param message
     * @param statusCode
     * @param code
     */
    public ErrorInfo(String message, int statusCode, int code) {
        this(message, code);
        this.statusCode = statusCode;
        if(code > 0) {
            this.href = href(code);
        }
    }

    public String toString() {
        StringBuilder result = new StringBuilder("{ErrorInfo");
        result.append(" message=").append(logMessage());
        if(code > 0) {
            result.append(" code=").append(code);
        }
        if(statusCode > 0) {
            result.append(" statusCode=").append(statusCode);
        }
        if(href != null) {
            result.append(" href=").append(href);
        }
        result.append('}');
        return result.toString();
    }

    ErrorInfo readMsgpack(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        for(int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

            switch(fieldName) {
                case "message":
                    message = unpacker.unpackString();
                    break;
                case "code":
                    code = unpacker.unpackInt();
                    break;
                case "statusCode":
                    statusCode = unpacker.unpackInt();
                    break;
                case "href":
                    href = unpacker.unpackString();
                    break;
                default:
                    Log.v(TAG, "Unexpected field: " + fieldName);
                    unpacker.skipValue();
            }
        }
        return this;
    }

    public static ErrorInfo fromMsgpackBody(byte[] msgpack) throws IOException {
        MessageUnpacker unpacker = Serialisation.msgpackUnpackerConfig.newUnpacker(msgpack);
        return fromMsgpackBody(unpacker);
    }

    private static ErrorInfo fromMsgpackBody(MessageUnpacker unpacker) throws IOException {
        int fieldCount = unpacker.unpackMapHeader();
        ErrorInfo error = null;
        for(int i = 0; i < fieldCount; i++) {
            String fieldName = unpacker.unpackString().intern();
            MessageFormat fieldFormat = unpacker.getNextFormat();
            if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

            switch(fieldName) {
                case "error":
                    error = ErrorInfo.fromMsgpack(unpacker);
                    break;
                default:
                    Log.v(TAG, "Unexpected field: " + fieldName);
                    unpacker.skipValue();
            }
        }
        return error;
    }

    static ErrorInfo fromMsgpack(MessageUnpacker unpacker) throws IOException {
        return (new ErrorInfo()).readMsgpack(unpacker);
    }

    public static ErrorInfo fromThrowable(Throwable throwable) {
        ErrorInfo errorInfo;
        if(throwable instanceof UnknownHostException
                || throwable instanceof NoRouteToHostException) {
            errorInfo = new ErrorInfo(throwable.getLocalizedMessage(), 500, 50002);
        }
        else if(throwable instanceof IOException) {
            errorInfo = new ErrorInfo(throwable.getLocalizedMessage(), 500, 50000);
        }
        else {
            errorInfo = new ErrorInfo("Unexpected exception: " + throwable.getLocalizedMessage(), 50000, 500);
        }

        return errorInfo;
    }

    public static ErrorInfo fromResponseStatus(String statusLine, int statusCode) {
        return new ErrorInfo(statusLine, statusCode, statusCode * 100);
    }

    /* Spec: TI5 */
    private String logMessage() {
        String errHref = null, logMessage = message == null ? "" : message;
        if(href != null) {
            errHref = href;
        } else if(code > 0) {
            errHref = href(code);
        }
        if(errHref != null && !logMessage.contains(errHref)) {
            logMessage += " (See " + errHref + ")";
        }
        return logMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ErrorInfo)) {
            return false;
        }
        ErrorInfo other = (ErrorInfo) o;
        return code == other.code &&
                statusCode == other.statusCode &&
                (message == other.message || (message != null && message.equals(other.message)));
    }

    private static String href(int code) { return HREF_BASE + code; }
    private static final String HREF_BASE = "https://help.ably.io/error/";
    private static final String TAG = ErrorInfo.class.getName();
}
