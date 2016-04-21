package io.ably.lib.types;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;

import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Crypto.ChannelCipher;
import io.ably.lib.util.Serialisation;

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
	public Object data;

	/**
	 * Generate a String summary of this BaseMessage
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
							data = Serialisation.gsonParser.parse(jsonText);
						}
						catch(JsonParseException e) { throw AblyException.fromThrowable(e); }
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
		if(data instanceof JsonElement) {
			data = Serialisation.gson.toJson((JsonElement)data);
			encoding = ((encoding == null) ? "" : encoding + "/") + "json";
		}
		if(opts != null && opts.encrypted) {
			if(data instanceof String) {
				try { data = ((String)data).getBytes("UTF-8"); } catch(UnsupportedEncodingException e) {}
				encoding = ((encoding == null) ? "" : encoding + "/") + "utf-8";
			}
			if(!(data instanceof byte[])) {
				throw AblyException.fromErrorInfo(new ErrorInfo("Unable to encode message data (incompatible type)", 400, 40000));
			}
			ChannelCipher cipher = opts.getCipher();
			data = cipher.encrypt((byte[])data);
			encoding = ((encoding == null) ? "" : encoding + "/") + "cipher+" + cipher.getAlgorithm();
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

	/* Gson Serializer */
	public static class Serializer {
		public JsonElement serialize(BaseMessage message, Type typeOfMessage, JsonSerializationContext ctx) {
			JsonObject json = new JsonObject();
			Object data = message.data;
			String encoding = message.encoding;
			if(data != null) {
				if(data instanceof byte[]) {
					byte[] dataBytes = (byte[])data;
					json.addProperty("data", new String(Base64Coder.encode(dataBytes)));
					encoding = (encoding == null) ? "base64" : encoding + "/base64";
				} else {
					json.addProperty("data", data.toString());
				}
				if(encoding != null) json.addProperty("encoding", encoding);
			}
			if(message.clientId != null) json.addProperty("clientId", message.clientId);
			if(message.connectionId != null) json.addProperty("connectionId", message.connectionId);
			return json;
		}		
	}

	/* Msgpack processing */
	boolean readField(MessageUnpacker unpacker, String fieldName, MessageFormat fieldType) throws IOException {
		boolean result = true;
		if(fieldName == "timestamp") {
			timestamp = unpacker.unpackLong();
		} else if(fieldName == "id") {
			id = unpacker.unpackString();
		} else if(fieldName == "clientId") {
			clientId = unpacker.unpackString();
		} else if(fieldName == "connectionId") {
			connectionId = unpacker.unpackString();
		} else if(fieldName == "encoding") {
			encoding = unpacker.unpackString();
		} else if(fieldName == "data") {
			if(fieldType.getValueType().isBinaryType()) {
				byte[] byteData = new byte[unpacker.unpackBinaryHeader()];
				unpacker.readPayload(byteData);
				data = byteData;
			} else {
				data = unpacker.unpackString();
			}
		} else {
			result = false;
		}
		return result;
	}

	protected int countFields() {
		int fieldCount = 0;
		if(timestamp > 0) ++fieldCount;
		if(clientId != null) ++fieldCount;
		if(connectionId != null) ++fieldCount;
		if(encoding != null) ++fieldCount;
		if(data != null) ++fieldCount;
		return fieldCount;
	}

	void writeFields(MessagePacker packer) throws IOException {
		if(timestamp > 0) {
			packer.packString("timestamp");
			packer.packLong(timestamp);
		}
		if(clientId != null) {
			packer.packString("clientId");
			packer.packString(clientId);
		}
		if(connectionId != null) {
			packer.packString("connectionId");
			packer.packString(connectionId);
		}
		if(encoding != null) {
			packer.packString("encoding");
			packer.packString(encoding);
		}
		if(data != null) {
			packer.packString("data");
			if(data instanceof byte[]) {
				byte[] byteData = (byte[])data;
				packer.packBinaryHeader(byteData.length);
				packer.writePayload(byteData);
			} else {
				packer.packString(data.toString());
			}
		}
	}
}
