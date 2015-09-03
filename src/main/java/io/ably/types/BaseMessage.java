package io.ably.types;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.ably.util.Base64Coder;
import io.ably.util.Crypto.ChannelCipher;

public class BaseMessage implements Cloneable {
	/**
	 * A unique id for this message
	 */
	public String id;

	/**
	 * The timestamp for this message
	 */
	public long timestamp;

	/**
	 * The id of the publisher of this message
	 */
	public String clientId;

	/**
	 * The connection id of the publisher of this message
	 */
	public String connectionId;

	/**
	 * Any transformation applied to the data for this message
	 */
	public String encoding;

	/**
	 * The message payload.
	 */
	@JsonSerialize(using = DataSerializer.class)
	@JsonDeserialize(using = DataDeserializer.class)
	public Object data;

	/**
	 * Construct a message from a JSON-encoded response body.
	 * @param json: a JSONObject obtained by parsing the response text
	 * @return
	 */
	protected void readJSON(JSONObject json) {
		if(json != null) {
			timestamp = json.optLong("timestamp");
			encoding = (String)json.opt("encoding");
			data = (String)json.opt("data");
			id = (String)json.opt("id");
			clientId = (String)json.opt("clientId");
			connectionId = (String)json.opt("connectionId");
		}
	}

	/**
	 * Internal: obtain a JSONObject from a Message
	 * @return
	 * @throws AblyException
	 */
	JSONObject toJSON() throws AblyException {
		JSONObject json = new JSONObject();
		try {
			if(timestamp > 0) json.put("timestamp", timestamp);
			if(clientId != null) json.put("clientId", clientId);
			if(connectionId != null) json.put("connectionId", clientId);
			if(data != null) {
				if(data instanceof byte[]) {
					data = new String(Base64Coder.encode((byte[])data));
					encoding = (encoding == null) ? "base64" : encoding + "/base64";
				}
				json.put("data", data);
			}
			if(encoding != null) json.put("encoding", encoding);
			return json;
		} catch(JSONException e) {
			throw new AblyException("Unexpected exception encoding message; err = " + e, 400, 40000);
		}
	}

	/**
	 * Generate a String summary of this BaseMessage
	 * @return string
	 */
	public void getDetails(StringBuilder builder) {
		if(clientId != null)
			builder.append(" clientId=").append(clientId);
		if(connectionId != null)
			builder.append(" connectionId=").append(connectionId);
		if(data != null)
			builder.append(" data=").append(data);
		if(encoding != null)
			builder.append(" encoding=").append(encoding);
		if(id != null)
			builder.append(" id=").append(id);
	}

	public void decode(ChannelOptions opts) throws AblyException {
		if(encoding != null) {
			String[] xforms = encoding.split("\\/");
			int i = 0, j = xforms.length;
			try {
				while((i = j) > 0) {
					Matcher match = xformPattern.matcher(xforms[--j]);
					if(!match.matches()) break;
					String xform = match.group(1).intern();
					if(xform == "base64") {
						data = Base64Coder.decode((String)data);
						continue;
					}
					if(xform == "utf-8") {
						try { data = new String((byte[])data, "UTF-8"); } catch(UnsupportedEncodingException e) {}
						continue;
					}
					if(xform == "json") {
						try {
							String jsonText = ((String)data).trim();
							if(jsonText.charAt(0) == '[')
								data = new JSONArray(jsonText);
							else
								data = new JSONObject(jsonText);
						}
						catch(JSONException e) { throw AblyException.fromThrowable(e); }
						continue;
					}
					if(xform == "cipher" && opts != null && opts.encrypted) {
						data = opts.getCipher().decrypt((byte[])data);
						continue;
					}
					break;
				}
			} finally {
				encoding = (i <= 0) ? null : join(xforms, '/', 0, i);
			}
		}
	}

	public void encode(ChannelOptions opts) throws AblyException {
		if(data instanceof JSONObject || data instanceof JSONArray) {
			data = data.toString();
			encoding = ((encoding == null) ? "" : encoding + "/") + "json";
		}
		if(opts != null && opts.encrypted) {
			if(data instanceof String) {
				try { data = ((String)data).getBytes("UTF-8"); } catch(UnsupportedEncodingException e) {}
				encoding = ((encoding == null) ? "" : encoding + "/") + "utf-8";
			}
			if(!(data instanceof byte[])) {
				throw new AblyException("Unable to encode message data (incompatible type)", 400, 40000);
			}
			ChannelCipher cipher = opts.getCipher();
			data = cipher.encrypt((byte[])data);
			encoding = ((encoding == null) ? "" : encoding + "/") + "cipher+" + cipher.getAlgorithm();
		}
	}

	public static class DataSerializer extends JsonSerializer<Object> {
		@Override
		public void serialize(Object data, JsonGenerator generator, SerializerProvider arg2)
				throws IOException, JsonProcessingException {

			if(data instanceof byte[]) {
				byte[] dataBytes = (byte[])data;
				generator.writeBinary(dataBytes);
			} else {
				generator.writeString(data.toString());
			}
		}
	}

	public static class DataDeserializer extends JsonDeserializer<Object> {
		@Override
		public Object deserialize(JsonParser parser, DeserializationContext deserContext)
				throws IOException, JsonProcessingException {

			return (parser.getCurrentToken() == JsonToken.VALUE_STRING) ? parser.getText() : parser.getBinaryValue();
		}
	}

	/* trivial utilities for processing encoding string */
	private static Pattern xformPattern = Pattern.compile("([\\-\\w]+)(\\+([\\-\\w]+))?");
	private String join(String[] elements, char separator, int start, int end) {
		StringBuilder result = new StringBuilder(elements[start++]);
		for(int i = start; i < end; i++)
			result.append(separator).append(elements[i]);
		return result.toString();
	}

	static final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
}
