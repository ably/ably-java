package io.ably.lib.types;

import io.ably.lib.util.Serialisation;

public class ErrorResponse {
    public ErrorInfo error;

    /**
     * Get an ErrorInfo from a response body with error details
     * @param jsonText
     * @return
     * @throws AblyException
     */
    public static ErrorResponse fromJSON(String jsonText) throws AblyException {
        ErrorResponse errorResponse = (ErrorResponse)Serialisation.gson.fromJson(jsonText, ErrorResponse.class);
        return errorResponse;
    }
}
