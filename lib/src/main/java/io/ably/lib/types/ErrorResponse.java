package io.ably.lib.types;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.ably.lib.util.Serialisation;

@JsonInclude(Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown=true)
public class ErrorResponse {
	public ErrorInfo error;

	/**
	 * Get an ErrorInfo from a response body with error details
	 * @param jsonText
	 * @return
	 * @throws AblyException
	 */
	public static ErrorResponse fromJSON(String jsonText) throws AblyException {
		try {
			ErrorResponse errorResponse = (ErrorResponse)Serialisation.jsonObjectMapper.readValue(jsonText, ErrorResponse.class);
			return errorResponse;
		} catch (IOException e) {
			throw new AblyException("Unexpected exception decoding server response: " + e, 500, 50000);
		}
	}
}
